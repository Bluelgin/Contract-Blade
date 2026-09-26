package com.maidweapon.forge.compat.tacz;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.compat.TaczCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.reflect.Method;
import java.util.UUID;

/** Loaded only when TACZ is present. */
public final class TaczLoadedCompat {
    static final String LAST_REPORTED_TAG = "MaidWeaponTaczLastReported";
    private static final String LINK_OWNER_TAG = "MaidWeaponTaczLinkOwner";
    private static final String LINK_MAID_TAG = "MaidWeaponTaczLinkMaid";
    private static final String LINK_BINDING_TAG = "MaidWeaponTaczLinkBinding";
    private static final String ORIGINAL_HAND_TAG = "MaidWeaponTaczOriginalHand";
    private static final String LINK_FAILURE_TAG = "MaidInfusionTaczAmmoLinkFailure";
    private static final int MAX_REPORTED_AMMO = 1_000_000;
    private static final double MAX_LINK_DISTANCE_SQR = 64.0 * 64.0;

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MaidWeaponConstants.MOD_ID);
    private static final RegistryObject<Item> AMMO_LINK = ITEMS.register(
            "contract_ammo_link", () -> new ContractAmmoLinkItem(
                    new Item.Properties().stacksTo(1).fireResistant()));

    public record LinkContext(ServerPlayer owner, LivingEntity maid,
                              ItemStack source, ItemStack gun) {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    public static boolean isGun(ItemStack stack) {
        return !stack.isEmpty() && IGun.getIGunOrNull(stack) != null;
    }

    public static boolean maintain(Player owner, LivingEntity maid, ItemStack source) {
        if (!isGun(source) || owner.level().isClientSide) return false;
        String binding = MaidWeaponItem.getBindingId(source);
        if (binding == null || binding.isEmpty()) return false;

        maid.getPersistentData().putUUID(TaczCompat.ENTITY_OWNER_TAG, owner.getUUID());
        maid.getPersistentData().putString(TaczCompat.ENTITY_BINDING_TAG, binding);

        ItemStack current = maid.getMainHandItem();
        if (!isMatchingProjection(current, owner.getUUID(), binding)) {
            // TLM temporarily swaps food into the hand while feeding. Do not
            // overwrite that stack; it restores the projected gun afterwards.
            if (!current.isEmpty() && maid.isUsingItem()) {
                return ensureLink(owner, maid, source, binding);
            }
            rememberOriginalHand(maid, current);
            ItemStack projection = createProjection(source, owner.getUUID(), binding);
            maid.setItemSlot(EquipmentSlot.MAINHAND, projection);
            if (maid instanceof Mob mob) {
                mob.setDropChance(EquipmentSlot.MAINHAND, 0);
            }
        }

        boolean linked = ensureLink(owner, maid, source, binding);
        if (linked) {
            source.getOrCreateTag().remove(LINK_FAILURE_TAG);
        } else if (!source.getOrCreateTag().getBoolean(LINK_FAILURE_TAG)) {
            source.getOrCreateTag().putBoolean(LINK_FAILURE_TAG, true);
            owner.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "maid_weapon.message.tacz_link_full"), true);
        }
        return linked;
    }

    public static void clear(Player owner, LivingEntity maid, ItemStack source) {
        if (owner.level().isClientSide) return;
        restoreTlmEatingHand(maid);
        ItemStack projection = maid.getMainHandItem();
        if (TaczCompat.isProjection(projection)) {
            refundProjectionAmmo(owner, projection);
            restoreOriginalHand(maid);
        }
        removeLinks(maid);
        maid.getPersistentData().remove(TaczCompat.ENTITY_OWNER_TAG);
        maid.getPersistentData().remove(TaczCompat.ENTITY_BINDING_TAG);
        if (!source.isEmpty()) source.getOrCreateTag().remove(LINK_FAILURE_TAG);
    }

    public static void purgeLeakedLinks(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isLink(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (isLink(player.getOffhandItem())) {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        if (isLink(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    static LinkContext resolve(ItemStack link) {
        if (!isLink(link) || link.getTag() == null
                || !link.getTag().hasUUID(LINK_OWNER_TAG)
                || !link.getTag().hasUUID(LINK_MAID_TAG)) return null;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        UUID ownerId = link.getTag().getUUID(LINK_OWNER_TAG);
        UUID maidId = link.getTag().getUUID(LINK_MAID_TAG);
        String binding = link.getTag().getString(LINK_BINDING_TAG);
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null || binding.isEmpty()) return null;

        Entity found = null;
        for (var level : server.getAllLevels()) {
            found = level.getEntity(maidId);
            if (found != null) break;
        }
        if (!(found instanceof LivingEntity maid)
                || maid.level() != owner.level()
                || maid.distanceToSqr(owner) > MAX_LINK_DISTANCE_SQR
                || !maid.getPersistentData().hasUUID(TaczCompat.ENTITY_OWNER_TAG)
                || !ownerId.equals(maid.getPersistentData().getUUID(
                        TaczCompat.ENTITY_OWNER_TAG))
                || !binding.equals(maid.getPersistentData().getString(
                        TaczCompat.ENTITY_BINDING_TAG))
                || !binding.equals(maid.getPersistentData().getString(
                        TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID))) return null;

        ItemStack source = InfusedMaidDeploymentSystem.findBoundWeapon(
                owner, maid.getStringUUID());
        ItemStack gun = maid.getMainHandItem();
        if (source.isEmpty() || !MaidWeaponItem.isOwner(source, owner)
                || !binding.equals(MaidWeaponItem.getBindingId(source))
                || !isGun(source) || !isMatchingProjection(gun, ownerId, binding)) {
            return null;
        }
        return new LinkContext(owner, maid, source, gun);
    }

    static net.minecraft.resources.ResourceLocation ammoId(ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return DefaultAssets.EMPTY_AMMO_ID;
        return TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> index.getGunData().getAmmoId())
                .orElse(DefaultAssets.EMPTY_AMMO_ID);
    }

    static boolean sameGun(ItemStack expected, ItemStack actual) {
        IGun left = IGun.getIGunOrNull(expected);
        IGun right = IGun.getIGunOrNull(actual);
        return left != null && right != null
                && left.getGunId(expected).equals(right.getGunId(actual));
    }

    static int countOwnerAmmo(LinkContext context) {
        if (context.owner().isCreative()) return MAX_REPORTED_AMMO;
        return context.owner().getCapability(ForgeCapabilities.ITEM_HANDLER)
                .map(handler -> countAmmo(handler, context.gun()))
                .orElse(0);
    }

    static int consumeOwnerAmmo(LinkContext context, int requested) {
        if (requested <= 0) return 0;
        if (context.owner().isCreative()) return requested;
        int consumed = context.owner().getCapability(ForgeCapabilities.ITEM_HANDLER)
                .map(handler -> consumeAmmo(handler, context.gun(), requested))
                .orElse(0);
        if (consumed > 0) context.owner().getInventory().setChanged();
        return consumed;
    }

    private static int countAmmo(IItemHandler handler, ItemStack gun) {
        long total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack candidate = handler.getStackInSlot(i);
            if (candidate.isEmpty() || isLink(candidate)) continue;
            if (candidate.getItem() instanceof IAmmo ammo
                    && ammo.isAmmoOfGun(gun, candidate)) {
                total += candidate.getCount();
            } else if (candidate.getItem() instanceof IAmmoBox box
                    && box.isAmmoBoxOfGun(gun, candidate)) {
                if (box.isCreative(candidate)) return MAX_REPORTED_AMMO;
                total += Math.max(0, box.getAmmoCount(candidate));
            }
            if (total >= MAX_REPORTED_AMMO) return MAX_REPORTED_AMMO;
        }
        return (int) total;
    }

    private static int consumeAmmo(IItemHandler handler, ItemStack gun, int requested) {
        // A creative ammo box makes the whole linked supply infinite. Detect it
        // before extracting loose rounds from any earlier inventory slot.
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack candidate = handler.getStackInSlot(i);
            if (candidate.isEmpty() || isLink(candidate)) continue;
            if (candidate.getItem() instanceof IAmmoBox box
                    && box.isAmmoBoxOfGun(gun, candidate)
                    && box.isCreative(candidate)) {
                return requested;
            }
        }

        int remaining = requested;
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack candidate = handler.getStackInSlot(i);
            if (candidate.isEmpty() || isLink(candidate)) continue;
            if (candidate.getItem() instanceof IAmmo ammo
                    && ammo.isAmmoOfGun(gun, candidate)) {
                ItemStack extracted = handler.extractItem(i, remaining, false);
                remaining -= extracted.getCount();
                continue;
            }
            if (candidate.getItem() instanceof IAmmoBox box
                    && box.isAmmoBoxOfGun(gun, candidate)) {
                int available = Math.max(0, box.getAmmoCount(candidate));
                int take = Math.min(available, remaining);
                if (take <= 0) continue;
                int left = available - take;
                box.setAmmoCount(candidate, left);
                if (left <= 0) box.setAmmoId(candidate, DefaultAssets.EMPTY_AMMO_ID);
                if (handler instanceof IItemHandlerModifiable modifiable) {
                    modifiable.setStackInSlot(i, candidate);
                }
                remaining -= take;
            }
        }
        return requested - remaining;
    }

    private static boolean ensureLink(Player owner, LivingEntity maid,
                                      ItemStack source, String binding) {
        IItemHandlerModifiable inventory = maidInventory(maid);
        if (inventory == null) return false;
        int emptySlot = -1;
        for (int i = inventory.getSlots() - 1; i >= 0; i--) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (isLink(stack)) {
                if (linkMatches(stack, owner.getUUID(), maid.getUUID(), binding)) return true;
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            } else if (stack.isEmpty() && emptySlot < 0) {
                emptySlot = i;
            }
        }
        if (emptySlot < 0) return false;

        ItemStack link = new ItemStack(AMMO_LINK.get());
        CompoundTag tag = link.getOrCreateTag();
        tag.putUUID(LINK_OWNER_TAG, owner.getUUID());
        tag.putUUID(LINK_MAID_TAG, maid.getUUID());
        tag.putString(LINK_BINDING_TAG, binding);
        inventory.setStackInSlot(emptySlot, link);
        return true;
    }

    private static void removeLinks(LivingEntity maid) {
        IItemHandlerModifiable inventory = maidInventory(maid);
        if (inventory == null) return;
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (isLink(inventory.getStackInSlot(i))) {
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    private static IItemHandlerModifiable maidInventory(LivingEntity maid) {
        try {
            Method method = maid.getClass().getMethod("getMaidInv");
            Object result = method.invoke(maid);
            return result instanceof IItemHandlerModifiable handler ? handler : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static ItemStack createProjection(ItemStack source, UUID owner, String binding) {
        ItemStack copy = source.copy();
        copy.setCount(1);
        MaidWeaponItem.clearMaidContract(copy);
        CompoundTag tag = copy.getOrCreateTag();
        tag.putBoolean(TaczCompat.PROJECTION_TAG, true);
        tag.putUUID("MaidWeaponPhantomOwner", owner);
        tag.putString("MaidWeaponPhantomBinding", binding);

        IGun gun = IGun.getIGunOrNull(copy);
        if (gun != null) {
            gun.setCurrentAmmoCount(copy, 0);
            gun.setBulletInBarrel(copy, false);
            gun.setDummyAmmoAmount(copy, 0);
        }
        return copy;
    }

    private static void refundProjectionAmmo(Player owner, ItemStack projection) {
        IGun gun = IGun.getIGunOrNull(projection);
        if (gun == null) return;
        if (owner.isCreative()) {
            gun.setCurrentAmmoCount(projection, 0);
            gun.setBulletInBarrel(projection, false);
            return;
        }

        boolean chambered = gun.hasBulletInBarrel(projection);
        gun.dropAllAmmo(owner, projection);
        if (chambered) {
            var id = ammoId(projection);
            if (!DefaultAssets.EMPTY_AMMO_ID.equals(id)) {
                ItemStack round = AmmoItemBuilder.create().setId(id).setCount(1).build();
                ItemHandlerHelper.giveItemToPlayer(owner, round);
            }
            gun.setBulletInBarrel(projection, false);
        }
    }

    private static void rememberOriginalHand(LivingEntity maid, ItemStack current) {
        if (maid.getPersistentData().contains(ORIGINAL_HAND_TAG)) return;
        maid.getPersistentData().put(ORIGINAL_HAND_TAG, current.save(new CompoundTag()));
    }

    private static void restoreOriginalHand(LivingEntity maid) {
        CompoundTag data = maid.getPersistentData();
        ItemStack original = data.contains(ORIGINAL_HAND_TAG)
                ? ItemStack.of(data.getCompound(ORIGINAL_HAND_TAG))
                : ItemStack.EMPTY;
        maid.setItemSlot(EquipmentSlot.MAINHAND, original);
        data.remove(ORIGINAL_HAND_TAG);
    }

    private static void restoreTlmEatingHand(LivingEntity maid) {
        if (!maid.isUsingItem() || !maid.getUseItem().isEdible()
                || !maid.getClass().getName().equals(
                "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid")) return;
        try {
            maid.stopUsingItem();
            Method method = maid.getClass().getDeclaredMethod("backCurrentHandItemStack");
            method.setAccessible(true);
            method.invoke(maid);
        } catch (ReflectiveOperationException ignored) {
            // If TLM changes this private helper, retaining the hand is safer
            // than deleting an item that may belong to the real maid inventory.
        }
    }

    private static boolean isMatchingProjection(ItemStack stack, UUID owner, String binding) {
        if (!TaczCompat.isProjection(stack) || stack.getTag() == null
                || !stack.getTag().hasUUID("MaidWeaponPhantomOwner")) return false;
        return owner.equals(stack.getTag().getUUID("MaidWeaponPhantomOwner"))
                && binding.equals(stack.getTag().getString("MaidWeaponPhantomBinding"))
                && isGun(stack);
    }

    private static boolean linkMatches(ItemStack stack, UUID owner, UUID maid,
                                       String binding) {
        return stack.getTag() != null
                && stack.getTag().hasUUID(LINK_OWNER_TAG)
                && owner.equals(stack.getTag().getUUID(LINK_OWNER_TAG))
                && stack.getTag().hasUUID(LINK_MAID_TAG)
                && maid.equals(stack.getTag().getUUID(LINK_MAID_TAG))
                && binding.equals(stack.getTag().getString(LINK_BINDING_TAG));
    }

    private static boolean isLink(ItemStack stack) {
        return !stack.isEmpty() && AMMO_LINK.isPresent()
                && stack.getItem() == AMMO_LINK.get();
    }

    private TaczLoadedCompat() {
    }
}

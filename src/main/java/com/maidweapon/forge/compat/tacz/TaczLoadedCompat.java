package com.maidweapon.forge.compat.tacz;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.compat.TaczCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
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
    private static final String LINK_OWNER_TAG = "MaidWeaponTaczLinkOwner";
    private static final String LINK_MAID_TAG = "MaidWeaponTaczLinkMaid";
    private static final String LINK_BINDING_TAG = "MaidWeaponTaczLinkBinding";
    private static final String ORIGINAL_HAND_TAG = "MaidWeaponTaczOriginalHand";
    private static final String LINK_FAILURE_TAG = "MaidInfusionTaczAmmoLinkFailure";
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
        String binding = ContractCarrierData.getBindingId(source);
        if (binding == null || binding.isEmpty()) return false;

        maid.getPersistentData().putUUID(TaczCompat.ENTITY_OWNER_TAG, owner.getUUID());
        maid.getPersistentData().putString(TaczCompat.ENTITY_BINDING_TAG, binding);

        if (!maid.getPersistentData().getBoolean("MaidWeaponTaczLinksRetired")) {
            removeLinks(maid);
            maid.getPersistentData().putBoolean("MaidWeaponTaczLinksRetired", true);
        }
        migrateNestedSnapshot(maid);
        ItemStack current = maid.getMainHandItem();
        if (!isMatchingProjection(current, owner.getUUID(), binding)) {
            // A real item supplied during projection belongs to the player, not to this adapter.
            if (maid.getPersistentData().contains(ORIGINAL_HAND_TAG)
                    && !current.isEmpty()
                    && !com.maidweapon.forge.compat.ContractProjectionIdentity.isProjection(current)) return false;
            // TLM temporarily swaps food into the hand while feeding. Do not
            // overwrite that stack; it restores the projected gun afterwards.
            if (!current.isEmpty() && maid.isUsingItem()) {
                return true;
            }
            rememberOriginalHand(maid, current);
            ItemStack projection = createProjection(source, owner.getUUID(), binding);
            maid.setItemSlot(EquipmentSlot.MAINHAND, projection);
            if (maid instanceof Mob mob) {
                mob.setDropChance(EquipmentSlot.MAINHAND, 0);
            }
        }

        source.getOrCreateTag().remove(LINK_FAILURE_TAG);
        return true;
    }

    public static void clear(Player owner, LivingEntity maid, ItemStack source) {
        if (owner.level().isClientSide) return;
        if (maid.isUsingItem() && maid.getUseItem().isEdible()) return;
        migrateNestedSnapshot(maid);
        ItemStack projection = maid.getMainHandItem();
        if (TaczCompat.isProjection(projection)) {
            refundProjectionAmmo(owner, projection);
        }
        restoreOriginalHand(maid);
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

    /** Fresh server-side authority check; never retain an inventory count or link token. */
    static LinkContext resolve(LivingEntity maid) {
        if (maid.level().isClientSide || maid.getServer() == null
                || !maid.getServer().isSameThread()
                || !com.maidweapon.forge.compat.tlm.TlmEntityAdapter.isMaidEntity(maid)
                || !maid.isAlive()) return null;
        CompoundTag data = maid.getPersistentData();
        if (!data.hasUUID(TaczCompat.ENTITY_OWNER_TAG)) return null;
        UUID ownerId = data.getUUID(TaczCompat.ENTITY_OWNER_TAG);
        String binding = data.getString(TaczCompat.ENTITY_BINDING_TAG);
        ServerPlayer owner = maid.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || binding.isEmpty() || owner.level() != maid.level()
                || maid.distanceToSqr(owner) > MAX_LINK_DISTANCE_SQR
                || !com.maidweapon.forge.compat.tlm.TlmEntityAdapter.isOwnedMaid(maid, owner)
                || !com.maidweapon.forge.compat.tlm.TlmProjectionBaubles.mode(maid).weapon()
                || com.maidweapon.forge.compat.tlm.TlmResidenceAdapter.isResident(maid)
                || maid.level().dimension().equals(
                    com.maidweapon.forge.system.interior.ContractInteriorService.INTERIOR_LEVEL)
                || !binding.equals(data.getString(TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID))) return null;
        ItemStack source = InfusedMaidDeploymentSystem.findBoundWeapon(owner, maid.getStringUUID());
        ItemStack gun = maid.getMainHandItem();
        if (source.isEmpty() || !ContractCarrierData.isOwner(source, owner)
                || !binding.equals(ContractCarrierData.getBindingId(source))
                || !isGun(source) || !isMatchingProjection(gun, ownerId, binding)
                || !sameGun(source, gun)) return null;
        return new LinkContext(owner, maid, source, gun);
    }

    public static net.minecraftforge.common.util.LazyOptional<IItemHandler> ammoView(
            LivingEntity shooter, net.minecraftforge.common.util.LazyOptional<IItemHandler> nativeInventory) {
        LinkContext context = resolve(shooter);
        if (context == null) return nativeInventory;
        IItemHandler own = context.owner().getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        IItemHandler fallback = nativeInventory.orElse(null);
        if (own == null || fallback == null) return nativeInventory;
        return net.minecraftforge.common.util.LazyOptional.of(
                () -> new TaczPriorityAmmoHandler(own, fallback, shooter));
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
        ContractCarrierData.clearMaidContract(copy);
        CompoundTag tag = copy.getOrCreateTag();
        tag.putBoolean(TaczCompat.PROJECTION_TAG, true);
        tag.putUUID("MaidWeaponPhantomOwner", owner);
        tag.putString("MaidWeaponPhantomBinding", binding);

        tag.putBoolean(TaczAmmoAccounting.TRACKED, true);
        tag.putInt(TaczAmmoAccounting.PAID, 0);
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
        var index = TimelessAPI.getCommonGunIndex(gun.getGunId(projection)).orElse(null);
        int loaded = gun.getCurrentAmmoCount(projection) + (gun.hasBulletInBarrel(projection) ? 1 : 0);
        int paid = Math.max(0, projection.getOrCreateTag().getInt(TaczAmmoAccounting.PAID));
        // Unknown legacy loaded guns retain the old refund behavior. New projections are ledgered.
        if (!projection.getOrCreateTag().getBoolean(TaczAmmoAccounting.TRACKED)) paid = loaded;
        boolean refundable = index != null && !owner.isCreative() && !gun.useDummyAmmo(projection)
                && !index.getGunData().getReloadData().isInfinite()
                && index.getGunData().getReloadData().getType()
                    != com.tacz.guns.resource.pojo.data.gun.FeedType.FUEL;
        gun.setCurrentAmmoCount(projection, 0);
        gun.setBulletInBarrel(projection, false);
        projection.getOrCreateTag().putInt(TaczAmmoAccounting.PAID, 0);
        int remaining = refundable ? Math.min(loaded, paid) : 0;
        var id = ammoId(projection);
        var ammo = TimelessAPI.getCommonAmmoIndex(id).orElse(null);
        if (ammo == null || DefaultAssets.EMPTY_AMMO_ID.equals(id)) return;
        while (remaining > 0) {
            int count = Math.min(remaining, Math.max(1, ammo.getStackSize()));
            ItemHandlerHelper.giveItemToPlayer(owner,
                    AmmoItemBuilder.create().setId(id).setCount(count).build());
            remaining -= count;
        }
    }

    private static void migrateNestedSnapshot(LivingEntity maid) {
        CompoundTag data = maid.getPersistentData();
        if (!data.contains(ORIGINAL_HAND_TAG)) return;
        ItemStack old = ItemStack.of(data.getCompound(ORIGINAL_HAND_TAG));
        if (!com.maidweapon.forge.compat.ContractProjectionIdentity.isProjection(old)) return;
        CompoundTag gear = data.getCompound("MaidWeaponProjectionGear");
        if (gear.contains("mainhand")) {
            data.put(ORIGINAL_HAND_TAG, gear.getCompound("mainhand").copy());
            gear.remove("mainhand");
            if (gear.isEmpty()) data.remove("MaidWeaponProjectionGear");
            else data.put("MaidWeaponProjectionGear", gear);
        } else {
            data.put(ORIGINAL_HAND_TAG, ItemStack.EMPTY.save(new CompoundTag()));
        }
    }

    private static void rememberOriginalHand(LivingEntity maid, ItemStack current) {
        if (maid.getPersistentData().contains(ORIGINAL_HAND_TAG)) return;
        maid.getPersistentData().put(ORIGINAL_HAND_TAG, current.save(new CompoundTag()));
    }

    private static void restoreOriginalHand(LivingEntity maid) {
        CompoundTag data = maid.getPersistentData();
        if (!data.contains(ORIGINAL_HAND_TAG)) {
            if (TaczCompat.isProjection(maid.getMainHandItem())) maid.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            return;
        }
        ItemStack original = ItemStack.of(data.getCompound(ORIGINAL_HAND_TAG));
        ItemStack current = maid.getMainHandItem();
        if (current.isEmpty() || TaczCompat.isProjection(current)) {
            maid.setItemSlot(EquipmentSlot.MAINHAND, original);
        } else if (!com.maidweapon.forge.compat.TripleMagicCompat.isPhantom(original)
                && !TaczCompat.isProjection(original)) {
            com.maidweapon.forge.compat.tlm.TlmEquipmentReturns.returnOriginal(maid, original);
        }
        data.remove(ORIGINAL_HAND_TAG);
    }

    private static boolean isMatchingProjection(ItemStack stack, UUID owner, String binding) {
        if (!TaczCompat.isProjection(stack) || stack.getTag() == null
                || !stack.getTag().hasUUID("MaidWeaponPhantomOwner")) return false;
        return owner.equals(stack.getTag().getUUID("MaidWeaponPhantomOwner"))
                && binding.equals(stack.getTag().getString("MaidWeaponPhantomBinding"))
                && isGun(stack);
    }

    private static boolean isLink(ItemStack stack) {
        return !stack.isEmpty() && AMMO_LINK.isPresent()
                && stack.getItem() == AMMO_LINK.get();
    }

    private TaczLoadedCompat() {
    }
}

package com.maidweapon.forge.compat;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.compat.TlmReflection;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.api.event.ContractMaidCapturedEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * TLM 业务逻辑层 — 女仆捕获/释放的核心操作。
 * 所有 TLM 反射调用委托给 {@link TlmReflection}。
 */
public final class TouhouLittleMaidHelper {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TlmReflection TLM = TlmReflection.INSTANCE;
    private static final Set<java.util.UUID> SHOWED_CAPTURE_MEMORY = new java.util.HashSet<>();

    private static final String TAG_MAID_ENTITY_DATA = "MaidEntityData";
    private static final String TAG_FILM_PROGRESS = "MaidWeaponProgress";
    public static final String TAG_ENTITY_BINDING_ID = "MaidWeaponBindingId";

    /** 检查物品是否为女仆武器（普通版或拔刀剑版） */
    private static boolean isMaidWeaponItem(ItemStack stack) {
        return MaidInfusion.isWeapon(stack);
    }

    // ==================== 女仆检查（委托给 TlmReflection） ====================

    public static boolean isMaidEntity(Entity entity) {
        return TLM.isMaidEntity(entity);
    }

    private static Class<?> findMaidEntityClass() {
        return TLM.getMaidEntityClass();
    }

    private static int getMaidFavorability(Entity maid) {
        return TLM.getMaidFavorability(maid);
    }

    private static void setMaidFavorability(Entity maid, int value) {
        TLM.setMaidFavorability(maid, value);
    }

    // ==================== 直接 API 调用（无需反射） ====================

    private static boolean isOwnedByPlayer(Entity entity, Player player) {
        if (!(entity instanceof TamableAnimal tamable)) return false;
        java.util.UUID ownerUUID = tamable.getOwnerUUID();
        if (ownerUUID == null) return false;
        return ownerUUID.equals(player.getUUID());
    }

    public static boolean isOwnedMaid(Entity entity, Player player) {
        return isMaidEntity(entity) && isOwnedByPlayer(entity, player);
    }

    public static int maidFavorability(Entity entity) {
        return isMaidEntity(entity) ? getMaidFavorability(entity) : 0;
    }

    public static CompoundTag captureMaidAppearance(Entity entity) {
        return TLM.captureAppearance(entity);
    }

    public static CompoundTag captureStoredMaidAppearance(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null) return new CompoundTag();
        if (root.contains(TAG_MAID_ENTITY_DATA)) {
            return TLM.extractAppearance(root.getCompound(TAG_MAID_ENTITY_DATA));
        }
        if (root.contains("MaidInfo")) {
            return TLM.extractAppearance(root.getCompound("MaidInfo"));
        }
        return new CompoundTag();
    }

    public static Entity createMaidAppearanceProxy(net.minecraft.world.level.Level level,
                                                   CompoundTag appearance) {
        return TLM.createAppearanceProxy(level, appearance);
    }

    public static String getMaidTaskId(Entity entity) {
        return TLM.getTaskId(entity);
    }

    public static boolean switchMaidTask(Entity entity, String taskId) {
        return TLM.switchTask(entity, taskId);
    }

    /** Keeps an auto-deployed combat maid working during both day and night. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean setAllDaySchedule(Entity entity) {
        try {
            Class<?> schedule = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule");
            Object all = Enum.valueOf((Class<? extends Enum>) schedule.asSubclass(Enum.class), "ALL");
            entity.getClass().getMethod("setSchedule", schedule).invoke(entity, all);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static boolean isMaidFilm(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemId.equals("touhou_little_maid:smart_slab_has_maid")
                || itemId.equals("touhou_little_maid:film");
    }

    public static boolean isFilledMaidFilm(ItemStack stack) {
        return isMaidFilm(stack) && stack.getTag() != null
                && stack.getTag().contains("MaidInfo");
    }

    public static boolean isEmptyMaidFilm(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemId.equals("touhou_little_maid:smart_slab_empty")
                || (itemId.equals("touhou_little_maid:film")
                && (stack.getTag() == null || !stack.getTag().contains("MaidInfo")));
    }

    public static ItemStack createEmptyMaidStoreItem(ItemStack filled) {
        String itemId = BuiltInRegistries.ITEM.getKey(filled.getItem()).toString();
        if (itemId.equals("touhou_little_maid:smart_slab_has_maid")) {
            return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(
                    "touhou_little_maid", "smart_slab_empty")));
        }
        ItemStack empty = filled.copy();
        empty.setCount(1);
        empty.setTag(null);
        return empty;
    }

    /** Transfers a maid stored by TLM's film item into a weapon without spawning her. */
    public static boolean infuseFromFilm(Player player, ItemStack film, ItemStack weaponStack) {
        if (!isFilledMaidFilm(film) || !MaidInfusion.isWeapon(weaponStack)
                || MaidInfusion.isInfused(weaponStack)) return false;
        try {
            Class<?> maidClass = findMaidEntityClass();
            if (maidClass == null) return false;
            Entity maid = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());
            loadEntityNBT(maid, film.getTag().getCompound("MaidInfo").copy());
            if (!isOwnedByPlayer(maid, player)) {
                player.displayClientMessage(
                        Component.translatable("maid_weapon.message.film_not_owner"), true);
                return false;
            }

            retirePreviousContracts(player, maid.getStringUUID(), weaponStack);

            String bindingId = MaidWeaponItem.ensureBindingId(weaponStack);
            MaidWeaponItem.setContractSuperseded(weaponStack, false);
            maid.getPersistentData().putString(TAG_ENTITY_BINDING_ID, bindingId);
            weaponStack.getOrCreateTag().put(TAG_MAID_ENTITY_DATA, saveEntityNBT(maid));
            MaidWeaponItem.setOwner(weaponStack, player);
            MaidWeaponItem.setBoundMaidUUID(weaponStack, maid.getStringUUID());
            if (film.getTag().contains(TAG_FILM_PROGRESS)) {
                weaponStack.getOrCreateTag().put("MaidData",
                        film.getTag().getCompound(TAG_FILM_PROGRESS).copy());
            } else {
                MaidWeaponData data = new MaidWeaponData(maid.getName().getString());
                data.setFavorability(Math.min(MaidWeaponData.MAX_FAVORABILITY,
                        Math.max(0, getMaidFavorability(maid))));
                MaidWeaponItem.setMaidData(weaponStack, data);
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("[MaidWeapon] Failed to infuse maid film", e);
            return false;
        }
    }

    /** Moves a stored maid and this mod's progression from an infused weapon back into an empty film. */
    public static ItemStack extractMaidToFilm(Player player, ItemStack weapon, ItemStack emptyFilm) {
        if (!MaidInfusion.containsMaid(weapon) || !isEmptyMaidFilm(emptyFilm)
                || !MaidWeaponItem.isOwner(weapon, player)) return ItemStack.EMPTY;
        try {
            CompoundTag weaponTag = weapon.getTag();
            if (weaponTag == null) return ItemStack.EMPTY;
            CompoundTag maidTag = weaponTag.getCompound(TAG_MAID_ENTITY_DATA).copy();
            Class<?> maidClass = findMaidEntityClass();
            if (maidClass == null) return ItemStack.EMPTY;
            Entity maid = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());
            loadEntityNBT(maid, maidTag);
            if (!isOwnedByPlayer(maid, player)) return ItemStack.EMPTY;
            maid.getPersistentData().remove(TAG_ENTITY_BINDING_ID);

            String emptyId = BuiltInRegistries.ITEM.getKey(emptyFilm.getItem()).toString();
            ItemStack filled = emptyId.equals("touhou_little_maid:smart_slab_empty")
                    ? new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(
                    "touhou_little_maid", "smart_slab_has_maid")))
                    : emptyFilm.copy();
            filled.setCount(1);
            CompoundTag filledTag = filled.getOrCreateTag();
            filledTag.put("MaidInfo", saveEntityNBT(maid));
            filledTag.put(TAG_FILM_PROGRESS, weaponTag.getCompound("MaidData").copy());
            return filled;
        } catch (Exception e) {
            LOGGER.error("[MaidWeapon] Failed to extract maid into film", e);
            return ItemStack.EMPTY;
        }
    }

    private static CompoundTag saveEntityNBT(Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        return tag;
    }

    private static void retirePreviousContracts(Player player, String maidId, ItemStack target) {
        boolean retiredAny = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack != target && maidId.equals(MaidWeaponItem.getBoundMaidUUID(stack))) {
                MaidWeaponItem.setContractSuperseded(stack, true);
                retiredAny = true;
            }
        }
        if (retiredAny) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.contract_transferred"), true);
        }
    }

    private static void loadEntityNBT(Entity entity, CompoundTag tag) {
        entity.load(tag);
    }

    private static void tameMaid(Entity maid, Player player) {
        if (maid instanceof TamableAnimal tamable) {
            tamable.tame(player);
        }
    }

    // ==================== 核心业务逻辑 ====================

    public static boolean convertMaidToWeapon(Player player, Entity entity, ItemStack weaponStack) {
        return convertMaidToWeapon(player, entity, weaponStack, true);
    }

    public static boolean convertMaidToWeapon(Player player, Entity entity, ItemStack weaponStack,
                                              boolean notifyPlayer) {
        if (!isMaidEntity(entity)) return false;
        if (weaponStack.isEmpty()) return false;
        if (!isMaidWeaponItem(weaponStack)) return false;
        if (MaidWeaponItem.isContractSuperseded(weaponStack)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.superseded_contract"), true);
            return false;
        }

        CompoundTag rootTag = weaponStack.getTag();
        if (rootTag != null && rootTag.contains(TAG_MAID_ENTITY_DATA)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.already_bound"), true);
            return false;
        }

        if (!isOwnedByPlayer(entity, player)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.maid_not_tamed"), true);
            return false;
        }

        if (!MaidWeaponItem.isBoundMaid(weaponStack, entity)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.wrong_maid"), true);
            return false;
        }

        String entityBinding = entity.getPersistentData().getString(TAG_ENTITY_BINDING_ID);
        String weaponBinding = MaidWeaponItem.getBindingId(weaponStack);
        if (!entityBinding.isEmpty() && (weaponBinding == null || !entityBinding.equals(weaponBinding))) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.bound_to_other_weapon"), true);
            return false;
        }

        weaponBinding = MaidWeaponItem.ensureBindingId(weaponStack);
        entity.getPersistentData().putString(TAG_ENTITY_BINDING_ID, weaponBinding);

        CompoundTag maidEntityTag = saveEntityNBT(entity);
        weaponStack.getOrCreateTag().put(TAG_MAID_ENTITY_DATA, maidEntityTag);

        boolean isFirstCapture = !MaidWeaponItem.hasMaidData(weaponStack);
        MaidWeaponData data;
        if (isFirstCapture) {
            data = new MaidWeaponData("");
            MaidWeaponItem.setOwner(weaponStack, player);
            MaidWeaponItem.setBoundMaidUUID(weaponStack, entity.getStringUUID());
        } else {
            data = MaidWeaponItem.getMaidData(weaponStack);
        }
        data.setMaidName(entity.getName().getString());
        data.setFavorability(Math.min(MaidWeaponData.MAX_FAVORABILITY,
                Math.max(0, getMaidFavorability(entity))));
        MaidWeaponItem.setMaidData(weaponStack, data);

        entity.discard();

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new ContractMaidCapturedEvent(player, entity, weaponStack, isFirstCapture));

        if (notifyPlayer) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.maid_bound", entity.getName().getString()), true);
        }
        return true;
    }

    public static boolean convertWeaponToMaid(Player player, ItemStack weaponStack) {
        return convertWeaponToMaid(player, weaponStack, true);
    }

    public static boolean convertWeaponToMaid(Player player, ItemStack weaponStack,
                                              boolean notifyPlayer) {
        if (weaponStack.isEmpty()) return false;
        if (!isMaidWeaponItem(weaponStack)) return false;
        if (!MaidWeaponItem.hasMaidData(weaponStack)) return false;
        if (MaidWeaponItem.isContractSuperseded(weaponStack)) return false;

        CompoundTag rootTag = weaponStack.getTag();
        if (rootTag == null || !rootTag.contains(TAG_MAID_ENTITY_DATA)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.no_maid_data"), true);
            return false;
        }

        CompoundTag maidEntityTag = rootTag.getCompound(TAG_MAID_ENTITY_DATA);

        try {
            Class<?> maidClass = findMaidEntityClass();
            if (maidClass == null) {
                return createNewMaidFromWeapon(player, weaponStack, notifyPlayer);
            }

            Entity maid = (Entity) maidClass
                    .getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());

            loadEntityNBT(maid, maidEntityTag);
            String bindingId = MaidWeaponItem.ensureBindingId(weaponStack);
            maid.getPersistentData().putString(TAG_ENTITY_BINDING_ID, bindingId);
            maid.setPos(player.getX(), player.getY(), player.getZ());
            tameMaid(maid, player);

            if (!player.level().isClientSide && !player.level().addFreshEntity(maid)) {
                LOGGER.warn("[MaidWeapon] Maid entity {} could not be added; keeping weapon data for retry",
                        maid.getUUID());
                return false;
            }

            rootTag.remove(TAG_MAID_ENTITY_DATA);
            weaponStack.setTag(rootTag);

            if (notifyPlayer) {
                player.displayClientMessage(
                        Component.translatable("maid_weapon.message.maid_released",
                                maid.getName().getString()), true);
            }
            return true;

        } catch (Exception e) {
            System.out.println("[MaidWeapon] Failed to restore maid from NBT: " + e.getMessage());
            e.printStackTrace();
            return createNewMaidFromWeapon(player, weaponStack, notifyPlayer);
        }
    }

    private static boolean createNewMaidFromWeapon(Player player, ItemStack weaponStack) {
        return createNewMaidFromWeapon(player, weaponStack, true);
    }

    private static boolean createNewMaidFromWeapon(Player player, ItemStack weaponStack,
                                                   boolean notifyPlayer) {
        MaidWeaponData data = MaidWeaponItem.getMaidData(weaponStack);
        if (data == null) return false;
        try {
            Class<?> maidClass = findMaidEntityClass();
            if (maidClass == null) return false;
            Entity maid = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());
            maid.getPersistentData().putString(TAG_ENTITY_BINDING_ID,
                    MaidWeaponItem.ensureBindingId(weaponStack));
            maid.setPos(player.position());
            maid.setCustomNameVisible(true);
            tameMaid(maid, player);
            setMaidFavorability(maid, data.getFavorability());

            if (!player.level().isClientSide && !player.level().addFreshEntity(maid)) return false;

            CompoundTag rootTag = weaponStack.getTag();
            if (rootTag != null) {
                rootTag.remove(TAG_MAID_ENTITY_DATA);
                weaponStack.setTag(rootTag);
            }

            if (notifyPlayer) {
                player.displayClientMessage(
                        Component.translatable("maid_weapon.message.maid_released", data.getMaidName()), true);
            }
            return true;
        } catch (Exception e) {
            System.out.println("[MaidWeapon] Failed to create new maid: " + e.getMessage());
            return false;
        }
    }

    /**
     * While manifested, TLM's maid entity is the sole authority for favorability.
     * The weapon only mirrors that value for tooltips, damage and stored-state use.
     */
    public static void syncFavorabilityFromMaid(Entity maid, ItemStack weaponStack) {
        if (!isMaidEntity(maid) || !MaidWeaponItem.hasMaidData(weaponStack)) return;
        MaidWeaponData data = MaidWeaponItem.getMaidData(weaponStack);
        int actual = Math.min(MaidWeaponData.MAX_FAVORABILITY,
                Math.max(MaidWeaponData.MIN_FAVORABILITY, getMaidFavorability(maid)));
        if (data.getFavorability() == actual) return;
        data.setFavorability(actual);
        MaidWeaponItem.setMaidData(weaponStack, data);
    }

    // ==================== 事件入口 ====================

    public static InteractionResult onPlayerInteractWithMaid(Player player, Entity entity, InteractionHand hand) {
        if (!isMaidEntity(entity)) return InteractionResult.PASS;
        ItemStack heldItem = player.getItemInHand(hand);
        if (!isMaidWeaponItem(heldItem)) return InteractionResult.PASS;
        boolean success = convertMaidToWeapon(player, entity, heldItem);
        return success ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    public static InteractionResult onPlayerShiftRightClick(Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);
        LOGGER.info("[MaidWeapon] onPlayerShiftRightClick called, item={} hasMaidData={} isShift={}",
                heldItem.getItem(), MaidWeaponItem.hasMaidData(heldItem), player.isShiftKeyDown());

        if (!isMaidWeaponItem(heldItem)) return InteractionResult.PASS;
        if (!MaidWeaponItem.hasMaidData(heldItem)) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        if (!MaidWeaponItem.isOwner(heldItem, player)) {
            LOGGER.info("[MaidWeapon] Release blocked: not owner");
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.not_owner"), true);
            return InteractionResult.FAIL;
        }

        CompoundTag tag = heldItem.getTag();
        if (tag == null || !tag.contains(TAG_MAID_ENTITY_DATA)) {
            LOGGER.info("[MaidWeapon] Release blocked: no MaidEntityData tag. Tags: {}",
                    tag != null ? tag.getAllKeys() : "null");
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.no_maid_data"), true);
            return InteractionResult.PASS;
        }

        LOGGER.info("[MaidWeapon] Calling convertWeaponToMaid...");
        boolean success = convertWeaponToMaid(player, heldItem);
        LOGGER.info("[MaidWeapon] convertWeaponToMaid result: {}", success);
        return success ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private TouhouLittleMaidHelper() {}
}

package com.maidweapon.forge.compat;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.compat.TlmReflection;
import com.maidweapon.forge.item.MaidWeaponItem;
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

/**
 * TLM 业务逻辑层 — 女仆捕获/释放的核心操作。
 * 所有 TLM 反射调用委托给 {@link TlmReflection}。
 */
public final class TouhouLittleMaidHelper {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TlmReflection TLM = TlmReflection.INSTANCE;
    private static final Set<java.util.UUID> SHOWED_CAPTURE_MEMORY = new java.util.HashSet<>();

    private static final String TAG_MAID_ENTITY_DATA = "MaidEntityData";

    /** 检查物品是否为女仆武器（普通版或拔刀剑版） */
    private static boolean isMaidWeaponItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof MaidWeaponItem) return true;
        return stack.getTag() != null && stack.getTag().contains(MaidWeaponConstants.TAG_SLASHBLADE_MODE);
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

    private static CompoundTag saveEntityNBT(Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        return tag;
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
        if (!isMaidEntity(entity)) return false;
        if (weaponStack.isEmpty()) return false;
        if (!isMaidWeaponItem(weaponStack)) return false;

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

        if (isFirstCapture && !SHOWED_CAPTURE_MEMORY.contains(player.getUUID())) {
            SHOWED_CAPTURE_MEMORY.add(player.getUUID());
            player.displayClientMessage(
                    Component.translatable("maid_weapon.memory.capture"), false);
        }

        player.displayClientMessage(
                Component.translatable("maid_weapon.message.maid_bound", entity.getName().getString()), true);
        return true;
    }

    public static boolean convertWeaponToMaid(Player player, ItemStack weaponStack) {
        if (weaponStack.isEmpty()) return false;
        if (!isMaidWeaponItem(weaponStack)) return false;
        if (!MaidWeaponItem.hasMaidData(weaponStack)) return false;

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
                return createNewMaidFromWeapon(player, weaponStack);
            }

            Entity maid = (Entity) maidClass
                    .getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());

            loadEntityNBT(maid, maidEntityTag);
            maid.setPos(player.getX(), player.getY(), player.getZ());
            tameMaid(maid, player);

            MaidWeaponData maidData = MaidWeaponItem.getMaidData(weaponStack);
            if (maidData != null) {
                setMaidFavorability(maid, maidData.getFavorability());
            }

            if (!player.level().isClientSide) {
                player.level().addFreshEntity(maid);
            }

            rootTag.remove(TAG_MAID_ENTITY_DATA);
            weaponStack.setTag(rootTag);

            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.maid_released",
                            maid.getName().getString()), true);
            return true;

        } catch (Exception e) {
            System.out.println("[MaidWeapon] Failed to restore maid from NBT: " + e.getMessage());
            e.printStackTrace();
            return createNewMaidFromWeapon(player, weaponStack);
        }
    }

    private static boolean createNewMaidFromWeapon(Player player, ItemStack weaponStack) {
        MaidWeaponData data = MaidWeaponItem.getMaidData(weaponStack);
        if (data == null) return false;
        try {
            Class<?> maidClass = findMaidEntityClass();
            if (maidClass == null) return false;
            Entity maid = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());
            maid.setPos(player.position());
            maid.setCustomNameVisible(true);
            tameMaid(maid, player);
            setMaidFavorability(maid, data.getFavorability());

            if (!player.level().isClientSide) {
                player.level().addFreshEntity(maid);
            }

            CompoundTag rootTag = weaponStack.getTag();
            if (rootTag != null) {
                rootTag.remove(TAG_MAID_ENTITY_DATA);
                weaponStack.setTag(rootTag);
            }

            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.maid_released", data.getMaidName()), true);
            return true;
        } catch (Exception e) {
            System.out.println("[MaidWeapon] Failed to create new maid: " + e.getMessage());
            return false;
        }
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

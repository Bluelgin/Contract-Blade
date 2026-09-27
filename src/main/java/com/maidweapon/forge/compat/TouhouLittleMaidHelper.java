package com.maidweapon.forge.compat;

import com.maidweapon.forge.compat.tlm.ContractMaidKeys;
import com.maidweapon.forge.compat.tlm.ContractMaidLifecycleService;
import com.maidweapon.forge.compat.tlm.ContractMaidStorage;
import com.maidweapon.forge.compat.tlm.TlmEntityAdapter;
import com.maidweapon.forge.compat.tlm.TlmFilmService;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Backwards-compatible facade for the TLM integration.
 *
 * <p>New code should prefer the focused services in {@code forge.compat.tlm}.
 * This class intentionally keeps the beta-era public surface stable for
 * addons and the rest of Contract Blade while no longer owning the logic.</p>
 */
public final class TouhouLittleMaidHelper {
    public static final String TAG_ENTITY_BINDING_ID = ContractMaidKeys.ENTITY_BINDING_ID;

    public static boolean isMaidEntity(Entity entity) {
        return TlmEntityAdapter.isMaidEntity(entity);
    }

    public static boolean isOwnedMaid(Entity entity, Player player) {
        return TlmEntityAdapter.isOwnedMaid(entity, player);
    }

    public static int maidFavorability(Entity entity) {
        return isMaidEntity(entity) ? TlmEntityAdapter.favorability(entity) : 0;
    }

    public static CompoundTag captureMaidAppearance(Entity entity) {
        return TlmEntityAdapter.captureAppearance(entity);
    }

    public static CompoundTag captureStoredMaidAppearance(ItemStack stack) {
        return TlmEntityAdapter.captureStoredAppearance(stack);
    }

    public static Entity createMaidAppearanceProxy(Level level, CompoundTag appearance) {
        return TlmEntityAdapter.createAppearanceProxy(level, appearance);
    }

    public static String getMaidTaskId(Entity entity) {
        return TlmEntityAdapter.taskId(entity);
    }

    public static boolean switchMaidTask(Entity entity, String taskId) {
        return TlmEntityAdapter.switchTask(entity, taskId);
    }

    public static boolean playMaidIdleVoice(Entity entity) {
        return TlmEntityAdapter.playIdleVoice(entity);
    }

    public static boolean setAllDaySchedule(Entity entity) {
        return TlmEntityAdapter.setAllDaySchedule(entity);
    }

    public static boolean isMaidFilm(ItemStack stack) {
        return TlmFilmService.isMaidFilm(stack);
    }

    public static boolean isFilledMaidFilm(ItemStack stack) {
        return TlmFilmService.isFilledMaidFilm(stack);
    }

    public static boolean isEmptyMaidFilm(ItemStack stack) {
        return TlmFilmService.isEmptyMaidFilm(stack);
    }

    public static ItemStack createEmptyMaidStoreItem(ItemStack filled) {
        return TlmFilmService.createEmptyMaidStoreItem(filled);
    }

    public static boolean infuseFromFilm(Player player, ItemStack film, ItemStack weaponStack) {
        return TlmFilmService.infuseFromFilm(player, film, weaponStack);
    }

    public static ItemStack extractMaidToFilm(
            Player player, ItemStack weapon, ItemStack emptyFilm) {
        return TlmFilmService.extractMaidToFilm(player, weapon, emptyFilm);
    }

    public static ItemStack createEmergencyResurrectionFilm(
            Player player, ItemStack contract, Entity liveMaid) {
        return TlmFilmService.createEmergencyResurrectionFilm(player, contract, liveMaid);
    }

    public static boolean setStoredMaidHealth(Player player, ItemStack weapon, float health) {
        return ContractMaidStorage.setHealth(weapon, health);
    }

    public static boolean createPresetSpiritContract(
            Player player, ItemStack weaponStack, String spiritId, String displayName) {
        return ContractMaidLifecycleService.createPresetSpiritContract(
                player, weaponStack, spiritId, displayName);
    }

    public static boolean convertMaidToWeapon(
            Player player, Entity entity, ItemStack weaponStack) {
        return convertMaidToWeapon(player, entity, weaponStack, true);
    }

    public static boolean convertMaidToWeapon(
            Player player, Entity entity, ItemStack weaponStack, boolean notifyPlayer) {
        return ContractMaidLifecycleService.capture(
                player, entity, weaponStack, notifyPlayer);
    }

    public static boolean convertWeaponToMaid(Player player, ItemStack weaponStack) {
        return convertWeaponToMaid(player, weaponStack, true);
    }

    public static boolean convertWeaponToMaid(
            Player player, ItemStack weaponStack, boolean notifyPlayer) {
        return ContractMaidLifecycleService.manifest(
                player, weaponStack, notifyPlayer);
    }

    public static void prepareManifestedMaid(
            Player player, ItemStack weaponStack, Entity maid) {
        ContractMaidLifecycleService.prepareManifestedMaid(
                player, weaponStack, maid);
    }

    public static void syncFavorabilityFromMaid(Entity maid, ItemStack weaponStack) {
        ContractMaidLifecycleService.syncFavorabilityFromMaid(maid, weaponStack);
    }

    /** Legacy facade retained for callers compiled against beta builds. */
    public static InteractionResult onPlayerInteractWithMaid(
            Player player, Entity entity, InteractionHand hand) {
        if (!isMaidEntity(entity)) return InteractionResult.PASS;
        ItemStack heldItem = player.getItemInHand(hand);
        if (!MaidInfusion.isWeapon(heldItem)) return InteractionResult.PASS;
        return convertMaidToWeapon(player, entity, heldItem)
                ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** Legacy facade retained for callers compiled against beta builds. */
    public static InteractionResult onPlayerShiftRightClick(
            Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (!MaidInfusion.isWeapon(heldItem)
                || !MaidWeaponItem.hasMaidData(heldItem)
                || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!MaidWeaponItem.isOwner(heldItem, player)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.not_owner"), true);
            return InteractionResult.FAIL;
        }
        if (!MaidWeaponItem.hasMaidEntityData(heldItem)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.no_maid_data"), true);
            return InteractionResult.PASS;
        }
        return convertWeaponToMaid(player, heldItem)
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private TouhouLittleMaidHelper() {}
}

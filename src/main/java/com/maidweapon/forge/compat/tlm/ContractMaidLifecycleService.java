package com.maidweapon.forge.compat.tlm;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.api.event.ContractMaidCapturedEvent;
import com.maidweapon.forge.compat.TaczCompat;
import com.maidweapon.forge.compat.TripleMagicCompat;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.MaidAttentionSystem;
import com.maidweapon.forge.system.MaidCareTaskSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Capture/manifest lifecycle for one contract maid.
 *
 * <p>This is the only service that is allowed to turn a live TLM maid into
 * serialized contract state or restore that state back into a live maid.</p>
 */
public final class ContractMaidLifecycleService {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean createPresetSpiritContract(
            Player player, ItemStack weaponStack, String spiritId, String displayName) {
        if (player == null || player.level().isClientSide || weaponStack.isEmpty()
                || !MaidInfusion.isWeapon(weaponStack)
                || MaidWeaponItem.hasMaidData(weaponStack)) {
            return false;
        }
        CompoundTag originalWeaponTag = weaponStack.getTag() == null
                ? null : weaponStack.getTag().copy();
        try {
            Class<?> maidClass = TlmEntityAdapter.maidClass();
            if (maidClass == null) return false;
            Entity maid = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());
            TlmEntityAdapter.tame(maid, player);
            maid.setCustomName(Component.literal(displayName));
            maid.setCustomNameVisible(false);
            maid.moveTo(player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());

            String bindingId = MaidWeaponItem.ensureBindingId(weaponStack);
            maid.getPersistentData().putString(ContractMaidKeys.ENTITY_BINDING_ID, bindingId);
            maid.getPersistentData().putString("MaidWeaponEmbeddedSpirit", spiritId);
            MaidWeaponItem.setOwner(weaponStack, player);
            MaidWeaponItem.setBoundMaidUUID(weaponStack, maid.getStringUUID());
            MaidWeaponItem.setContractSuperseded(weaponStack, false);

            MaidWeaponData data = new MaidWeaponData(displayName);
            data.setFavorability(MaidWeaponData.MAX_FAVORABILITY / 2);
            data.setResonance(MaidWeaponData.MAX_RESONANCE);
            MaidWeaponItem.setMaidData(weaponStack, data);
            if (!ContractMaidStorage.commit(player, weaponStack, TlmEntityAdapter.save(maid))) {
                weaponStack.setTag(originalWeaponTag);
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            weaponStack.setTag(originalWeaponTag);
            LOGGER.error("[MaidWeapon] Failed to create preset spirit {}", spiritId, exception);
            return false;
        }
    }

    public static boolean capture(Player player, Entity entity, ItemStack weaponStack,
                                  boolean notifyPlayer) {
        if (!TlmEntityAdapter.isMaidEntity(entity)
                || weaponStack.isEmpty()
                || !MaidInfusion.isWeapon(weaponStack)) {
            return false;
        }
        if (MaidWeaponItem.isContractSuperseded(weaponStack)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.superseded_contract"), true);
            return false;
        }
        if (MaidWeaponItem.hasMaidEntityData(weaponStack)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.already_bound"), true);
            return false;
        }
        if (!TlmEntityAdapter.isOwnedByPlayer(entity, player)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.maid_not_tamed"), true);
            return false;
        }
        if (!MaidWeaponItem.isBoundMaid(weaponStack, entity)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.wrong_maid"), true);
            return false;
        }

        String entityBinding = entity.getPersistentData().getString(
                ContractMaidKeys.ENTITY_BINDING_ID);
        com.maidweapon.forge.system.interior.ContractResidentPositionService.remember(entity);
        String originalEntityBinding = entityBinding;
        String weaponBinding = MaidWeaponItem.getBindingId(weaponStack);
        if (!entityBinding.isEmpty()
                && (weaponBinding == null || !entityBinding.equals(weaponBinding))) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.bound_to_other_weapon"), true);
            return false;
        }

        CompoundTag rootTag = weaponStack.getTag();
        CompoundTag originalWeaponTag = rootTag == null ? null : rootTag.copy();
        weaponBinding = MaidWeaponItem.ensureBindingId(weaponStack);
        entity.getPersistentData().putString(ContractMaidKeys.ENTITY_BINDING_ID, weaponBinding);

        MaidCareTaskSystem.restoreOriginalTask(weaponStack, entity);
        if (entity instanceof LivingEntity living) {
            TripleMagicCompat.clearPhantoms(living, weaponStack);
            TaczCompat.clear(player, living, weaponStack);
        }

        CompoundTag emergencyProgress = entity.getPersistentData().getCompound(
                ContractMaidKeys.EMERGENCY_FILM_PROGRESS).copy();
        CompoundTag maidEntityTag = TlmEntityAdapter.save(entity);
        if (!emergencyProgress.isEmpty()) {
            CompoundTag storedForgeData = maidEntityTag.getCompound("ForgeData");
            storedForgeData.remove(ContractMaidKeys.EMERGENCY_FILM_PROGRESS);
            maidEntityTag.put("ForgeData", storedForgeData);
        }

        boolean firstCapture = !MaidWeaponItem.hasMaidData(weaponStack);
        MaidWeaponData data;
        if (firstCapture) {
            if (emergencyProgress.isEmpty()) {
                data = new MaidWeaponData("");
            } else {
                ItemStack progressCarrier = new ItemStack(net.minecraft.world.item.Items.STICK);
                progressCarrier.getOrCreateTag().put("MaidData", emergencyProgress.copy());
                data = MaidWeaponItem.getMaidData(progressCarrier);
            }
            MaidWeaponItem.setOwner(weaponStack, player);
            MaidWeaponItem.setBoundMaidUUID(weaponStack, entity.getStringUUID());
        } else {
            data = MaidWeaponItem.getMaidData(weaponStack);
        }
        data.setMaidName(entity.getName().getString());
        data.setFavorability(Math.min(MaidWeaponData.MAX_FAVORABILITY,
                Math.max(0, TlmEntityAdapter.favorability(entity))));
        MaidWeaponItem.setMaidData(weaponStack, data);

        if (!ContractMaidStorage.commit(player, weaponStack, maidEntityTag)) {
            weaponStack.setTag(originalWeaponTag);
            if (originalEntityBinding.isEmpty()) {
                entity.getPersistentData().remove(ContractMaidKeys.ENTITY_BINDING_ID);
            } else {
                entity.getPersistentData().putString(
                        ContractMaidKeys.ENTITY_BINDING_ID, originalEntityBinding);
            }
            return false;
        }

        entity.getPersistentData().remove(ContractMaidKeys.EMERGENCY_FILM_PROGRESS);
        entity.discard();
        MaidCareTaskSystem.clearOriginalTask(weaponStack);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new ContractMaidCapturedEvent(player, entity, weaponStack, firstCapture));

        if (notifyPlayer) {
            player.displayClientMessage(Component.translatable(
                    "maid_weapon.message.maid_bound", entity.getName().getString()), true);
        }
        return true;
    }

    public static boolean manifest(Player player, ItemStack weaponStack, boolean notifyPlayer) {
        if (weaponStack.isEmpty()
                || !MaidInfusion.isWeapon(weaponStack)
                || !MaidWeaponItem.hasMaidData(weaponStack)
                || MaidWeaponItem.isContractSuperseded(weaponStack)) {
            return false;
        }
        if (!MaidWeaponItem.hasMaidEntityData(weaponStack)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.no_maid_data"), true);
            return false;
        }

        CompoundTag maidEntityTag = ContractMaidStorage.read(player, weaponStack);
        if (maidEntityTag == null) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.contract_nbt_decode_failed"), false);
            return false;
        }

        try {
            Class<?> maidClass = TlmEntityAdapter.maidClass();
            if (maidClass == null) return false;
            Entity maid = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());

            TlmEntityAdapter.load(maid, maidEntityTag);
            String bindingId = MaidWeaponItem.ensureBindingId(weaponStack);
            maid.getPersistentData().putString(ContractMaidKeys.ENTITY_BINDING_ID, bindingId);
            maid.setPos(player.getX(), player.getY(), player.getZ());
            TlmEntityAdapter.tame(maid, player);

            if (!player.level().isClientSide && !player.level().addFreshEntity(maid)) {
                LOGGER.warn("[MaidWeapon] Maid entity {} could not be added; keeping weapon data for retry",
                        maid.getUUID());
                return false;
            }

            prepareManifestedMaid(player, weaponStack, maid);
            ContractMaidStorage.remove(weaponStack);

            if (notifyPlayer) {
                player.displayClientMessage(Component.translatable(
                        "maid_weapon.message.maid_released", maid.getName().getString()), true);
            }
            MaidAttentionSystem.onManifested(player, weaponStack, maid);
            return true;
        } catch (Exception exception) {
            LOGGER.error("[MaidWeapon] Failed to restore maid from stored contract; data was retained",
                    exception);
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.contract_nbt_restore_failed"), false);
            return false;
        }
    }

    public static void prepareManifestedMaid(Player player, ItemStack weaponStack, Entity maid) {
        if (!(maid instanceof LivingEntity living)) return;
        if (maid.level().dimension().equals(
                com.maidweapon.forge.system.interior.ContractInteriorService.INTERIOR_LEVEL)) return;
        MaidCareTaskSystem.rememberOriginalTask(weaponStack, living);
        TlmEntityAdapter.setAllDaySchedule(living);
        TripleMagicCompat.equipPhantoms(player, living, weaponStack);
        TripleMagicCompat.syncMaidSpellLoadout(player, living, weaponStack);
        TaczCompat.maintain(player, living, weaponStack);
    }

    public static void syncFavorabilityFromMaid(Entity maid, ItemStack weaponStack) {
        if (!TlmEntityAdapter.isMaidEntity(maid)
                || !MaidWeaponItem.hasMaidData(weaponStack)) return;
        MaidWeaponData data = MaidWeaponItem.getMaidData(weaponStack);
        int actual = Math.min(MaidWeaponData.MAX_FAVORABILITY,
                Math.max(MaidWeaponData.MIN_FAVORABILITY,
                        TlmEntityAdapter.favorability(maid)));
        if (data.getFavorability() == actual) return;
        data.setFavorability(actual);
        MaidWeaponItem.setMaidData(weaponStack, data);
    }

    private ContractMaidLifecycleService() {}
}

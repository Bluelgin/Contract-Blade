package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.maidweapon.forge.system.fox.FoxSpiritLedger;
import com.maidweapon.forge.api.IntrinsicSpiritApi;
import com.maidweapon.forge.api.EmbeddedSpiritApi;
import com.maidweapon.forge.api.ContractAuthorization;
import com.maidweapon.forge.system.interior.ContractResidentPositionService;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import com.maidweapon.forge.system.contract.ContractAnomalyRecoveryService;
import com.maidweapon.forge.system.contract.ContractRecoveryJournal;
import com.maidweapon.forge.system.deployment.ContractCompanionState;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import com.maidweapon.forge.system.interior.ContractInteriorService;
import com.maidweapon.forge.system.interior.ContractInteriorSavedData;
import com.maidweapon.forge.system.deployment.ContractMaidRuntimeService;
import com.maidweapon.forge.api.event.ContractMaidManifestedEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import java.util.function.Consumer;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.api.event.ContractMaidCapturedEvent;
import com.maidweapon.forge.item.MaidInfusion;
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
                || ContractCarrierData.hasMaidData(weaponStack)) {
            return false;
        }
        var fox = FoxSpiritState.resident(weaponStack);
        if (FoxSpiritState.valid(fox)
                && !FoxSpiritLedger.get(player.getServer()).maid(fox).isEmpty()) return false;
        CompoundTag originalWeaponTag = weaponStack.getTag() == null
                ? null : weaponStack.getTag().copy();
        try {
            Class<?> maidClass = TlmEntityAdapter.maidClass();
            if (maidClass == null) return false;
            Entity maid = (Entity) maidClass.getConstructor(Level.class)
                    .newInstance(player.level());
            TlmEntityAdapter.tame(maid, player);
            PresetSpiritModels.initialize(maid, spiritId);
            maid.setCustomName(Component.literal(displayName));
            maid.setCustomNameVisible(false);
            maid.moveTo(player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());

            String bindingId = ContractCarrierData.ensureBindingId(weaponStack);
            maid.getPersistentData().putString(ContractMaidKeys.ENTITY_BINDING_ID, bindingId);
            maid.getPersistentData().putString(ContractMaidKeys.EMBEDDED_SPIRIT, spiritId);
            ContractCarrierData.setOwner(weaponStack, player);
            ContractCarrierData.setBoundMaidUUID(weaponStack, maid.getStringUUID());
            ContractCarrierData.setContractSuperseded(weaponStack, false);

            MaidWeaponData data = new MaidWeaponData(displayName);
            data.setFavorability(MaidWeaponData.MAX_FAVORABILITY / 2);
            data.setResonance(MaidWeaponData.maximumResonance());
            ContractCarrierData.setMaidData(weaponStack, data);
            if (!ContractMaidStorage.commit(player, weaponStack, TlmEntityAdapter.save(maid))) {
                weaponStack.setTag(originalWeaponTag);
                return false;
            }
            afterCommit("intrinsic origin", () -> com.maidweapon.forge.system.contract.IntrinsicSpiritOriginData.observed(player, maid, weaponStack));
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            weaponStack.setTag(originalWeaponTag);
            LOGGER.error("[MaidWeapon] Failed to create preset spirit {}", spiritId, exception);
            return false;
        }
    }

    /** Rehouse a released, ledger-owned fox through the ordinary full-data capture transaction. */
    public static boolean capture(Player player, Entity entity, ItemStack weaponStack, boolean notifyPlayer) {
        if (player == null || player.level().isClientSide || entity == null || weaponStack.isEmpty()) return false;
        var free = entity.getPersistentData().getCompound(FoxSpiritState.ROOT);
        boolean adopt = !FoxSpiritState.hasResident(weaponStack)
                && FoxSpiritState.valid(free)
                && free.getUUID("OwnerUUID").equals(player.getUUID())
                && FoxSpiritLedger.get(player.getServer()).owns(free, "FREE_MAID")
                && (FoxSpiritLedger.get(player.getServer()).maid(free).isEmpty()
                    || entity.getStringUUID().equals(FoxSpiritLedger.get(player.getServer()).maid(free)))
                && !ContractCarrierData.hasMaidData(weaponStack)
                && !IntrinsicSpiritApi.hasActiveProjection(weaponStack)
                && !FoxSpiritState.isProtected(weaponStack);
        var previous = weaponStack.getTag() == null ? null : weaponStack.getTag().copy();
        if (adopt) {
            weaponStack.getOrCreateTag().put(FoxSpiritState.ROOT, free.copy());
            weaponStack.getOrCreateTag().putString(EmbeddedSpiritApi.TAG_SPIRIT_ID, free.getString("SpiritId"));
        }
        boolean captured = false;
        try {
            captured = captureBound(player, entity, weaponStack, notifyPlayer);
            if (captured && adopt) FoxSpiritLedger.get(player.getServer()).record(free, "WEAPON");
            return captured;
        } finally {
            if (adopt && !captured) weaponStack.setTag(previous);
        }
    }

    private static boolean captureBound(Player player, Entity entity, ItemStack weaponStack,
                                  boolean notifyPlayer) {
        if (player == null || player.level().isClientSide || entity == null || entity.isRemoved() || !entity.isAlive()
                || !ContractAuthorization.allows(player, weaponStack)
                || !TlmEntityAdapter.isMaidEntity(entity)
                || weaponStack.isEmpty()
                || !MaidInfusion.isWeapon(weaponStack)) {
            return false;
        }
        String embedded = entity.getPersistentData().getString(ContractMaidKeys.EMBEDDED_SPIRIT);
        if ((FoxSpiritState.WHITE.equals(embedded)
                || FoxSpiritState.BLACK.equals(embedded)
                || FoxSpiritState.valid(entity.getPersistentData().getCompound(
                        FoxSpiritState.ROOT)))
                && !FoxSpiritState.hasResident(weaponStack)) return false;
        if (ContractCarrierData.isContractSuperseded(weaponStack)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.superseded_contract"), true);
            return false;
        }
        if (ContractCarrierData.hasMaidEntityData(weaponStack)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.already_bound"), true);
            return false;
        }
        if (!TlmEntityAdapter.isOwnedByPlayer(entity, player)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.maid_not_tamed"), true);
            return false;
        }
        if (!ContractCarrierData.isBoundMaid(weaponStack, entity)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.wrong_maid"), true);
            return false;
        }

        String entityBinding = entity.getPersistentData().getString(
                ContractMaidKeys.ENTITY_BINDING_ID);
        String originalEntityBinding = entityBinding;
        String weaponBinding = ContractCarrierData.getBindingId(weaponStack);
        if (!entityBinding.isEmpty()
                && (weaponBinding == null || !entityBinding.equals(weaponBinding))) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.bound_to_other_weapon"), true);
            return false;
        }

        CompoundTag rootTag = weaponStack.getTag();
        CompoundTag originalWeaponTag = rootTag == null ? null : rootTag.copy();
        CompoundTag previousDestination = ContractRecoveryJournal.get(player.getServer()).transfer(entity.getStringUUID());
        String originalTask = TlmEntityAdapter.taskId(entity);
        String originalSchedule = TlmEntityAdapter.scheduleName(entity);
        CompoundTag emergencyProgress = entity.getPersistentData().getCompound(
                ContractMaidKeys.EMERGENCY_FILM_PROGRESS).copy();
        boolean firstCapture = !ContractCarrierData.hasMaidData(weaponStack);

        // Preparation is reversible for identity/task state. Optional projections are cleaned
        // deliberately even on rejection; live runtime maintenance can recreate them next tick.
        try {
            ContractResidentPositionService.remember(entity);
            weaponBinding = ContractCarrierData.ensureBindingId(weaponStack);
            entity.getPersistentData().putString(ContractMaidKeys.ENTITY_BINDING_ID, weaponBinding);
            MaidCareTaskSystem.restoreOriginalTask(weaponStack, entity);
            if (entity instanceof LivingEntity living) {
                TlmMealLifecycle.prepareForStorage(living);
                com.maidweapon.forge.compat.ContractEquipmentCleanup.clear(player, living, weaponStack);
            }
            CompoundTag maidEntityTag = TlmEntityAdapter.save(entity);
            if (!maidEntityTag.hasUUID("Owner") || !player.getUUID().equals(maidEntityTag.getUUID("Owner"))
                    || !maidEntityTag.hasUUID("UUID") || !entity.getUUID().equals(maidEntityTag.getUUID("UUID"))
                    || !weaponBinding.equals(maidEntityTag.getCompound("ForgeData").getString(ContractMaidKeys.ENTITY_BINDING_ID))) {
                rollbackCapture(weaponStack, originalWeaponTag, entity, originalEntityBinding,
                        originalTask, originalSchedule);
                return false;
            }
            if (!emergencyProgress.isEmpty()) {
                CompoundTag storedForgeData = maidEntityTag.getCompound("ForgeData");
                storedForgeData.remove(ContractMaidKeys.EMERGENCY_FILM_PROGRESS);
                maidEntityTag.put("ForgeData", storedForgeData);
            }

            MaidWeaponData data;
            if (firstCapture) {
                if (emergencyProgress.isEmpty()) {
                    data = new MaidWeaponData("");
                } else {
                    ItemStack progressCarrier = new ItemStack(net.minecraft.world.item.Items.STICK);
                    progressCarrier.getOrCreateTag().put("MaidData", emergencyProgress.copy());
                    data = ContractCarrierData.getMaidData(progressCarrier);
                }
                ContractCarrierData.setOwner(weaponStack, player);
                ContractCarrierData.setBoundMaidUUID(weaponStack, entity.getStringUUID());
            } else {
                data = ContractCarrierData.getMaidData(weaponStack);
            }
            // Preserve an old emergency-film growth archive until the player authority imports it.
            if (firstCapture && !emergencyProgress.isEmpty()) weaponStack.getOrCreateTag().put("MaidData", emergencyProgress.copy());
            data.setMaidName(entity.getName().getString());
            data.setFavorability(Math.min(MaidWeaponData.MAX_FAVORABILITY,
                    Math.max(0, TlmEntityAdapter.favorability(entity))));
            ContractCarrierData.setMaidData(weaponStack, data);

            if (!ContractMaidStorage.commit(player, weaponStack, maidEntityTag)) {
                rollbackCapture(weaponStack, originalWeaponTag, entity, originalEntityBinding,
                        originalTask, originalSchedule);
                return false;
            }
        } catch (RuntimeException failure) {
            rollbackCapture(weaponStack, originalWeaponTag, entity, originalEntityBinding,
                    originalTask, originalSchedule);
            LOGGER.error("[Contract Blade] Capture preparation failed; live maid retained", failure);
            return false;
        }

        // Commit succeeded: never report a failed capture because a notification listener failed.
        SoulTalismanService.captured(player, weaponStack);
        afterCommit("capture mastery", () -> ContractMasteryService.observe(player, weaponStack));
        afterCommit("capture reconciliation", () -> ContractAnomalyRecoveryService.stored(player, weaponStack));
        entity.getPersistentData().remove(ContractMaidKeys.EMERGENCY_FILM_PROGRESS);
        entity.getPersistentData().putBoolean(ContractMaidKeys.CORE_STORAGE_TRANSFER, true);
        try { entity.discard(); }
        catch (RuntimeException failure) {
            if (!entity.isRemoved()) {
                // An unusual override may fail before Entity.remove. Storage must not claim
                // the still-live maid; restore the pre-operation destination as well.
                rollbackCapture(weaponStack, originalWeaponTag, entity, originalEntityBinding,
                        originalTask, originalSchedule);
                entity.getPersistentData().remove(ContractMaidKeys.CORE_STORAGE_TRANSFER);
                if (!emergencyProgress.isEmpty()) entity.getPersistentData().put(
                        ContractMaidKeys.EMERGENCY_FILM_PROGRESS, emergencyProgress);
                var journal = ContractRecoveryJournal.get(player.getServer());
                if (journal.knownSchema()) journal.transfer(entity.getStringUUID(), previousDestination);
                LOGGER.error("[Contract Blade] Entity removal failed before commit; live maid retained", failure);
                return false;
            }
            LOGGER.error("[Contract Blade] Removal observer failed after capture; stored snapshot remains authoritative", failure);
        }
        ContractCompanionState.clear(weaponStack);
        MaidCareTaskSystem.clearOriginalTask(weaponStack);
        afterCommit("capture event", () -> MinecraftForge.EVENT_BUS.post(
                new ContractMaidCapturedEvent(player, entity, weaponStack, firstCapture)));

        if (notifyPlayer) {
            afterCommit("capture message", () -> player.displayClientMessage(Component.translatable(
                    "maid_weapon.message.maid_bound", entity.getName().getString()), true));
        }
        return true;
    }

    public static boolean manifest(Player player, ItemStack weaponStack, boolean notifyPlayer) {
        return manifest(player, weaponStack, notifyPlayer, maid -> {});
    }

    public static boolean manifest(Player player, ItemStack weaponStack, boolean notifyPlayer,
                                   Consumer<Entity> beforeSpawn) {
        if (player == null || player.level().isClientSide
                || !ContractAuthorization.allows(player, weaponStack)
                || !ContractWeaponLocator.mayManifest(player, ContractCarrierData.getBoundMaidUUID(weaponStack))
                || weaponStack.isEmpty()
                || !MaidInfusion.isWeapon(weaponStack)
                || !ContractCarrierData.hasMaidData(weaponStack)
                || ContractCarrierData.isContractSuperseded(weaponStack)) {
            return false;
        }
        if (com.maidweapon.forge.system.oath.OathRest.deny(player,
                ContractCarrierData.getBoundMaidUUID(weaponStack), notifyPlayer)) return false;
        if (!ContractCarrierData.hasMaidEntityData(weaponStack)) {
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

        if (!maidEntityTag.hasUUID("Owner") || !player.getUUID().equals(maidEntityTag.getUUID("Owner"))
                || !maidEntityTag.hasUUID("UUID") || !maidEntityTag.getUUID("UUID").toString()
                    .equals(ContractCarrierData.getBoundMaidUUID(weaponStack))) return false;
        String storedBinding = maidEntityTag.getCompound("ForgeData").getString(ContractMaidKeys.ENTITY_BINDING_ID);
        if (!storedBinding.isEmpty() && !storedBinding.equals(ContractCarrierData.getBindingId(weaponStack))) return false;

        Entity maid = null;
        CompoundTag originalWeaponTag = weaponStack.getTag().copy();
        try {
            Class<?> maidClass = TlmEntityAdapter.maidClass();
            if (maidClass == null) return false;
            maid = (Entity) maidClass.getConstructor(Level.class)
                    .newInstance(player.level());

            TlmEntityAdapter.load(maid, maidEntityTag);
            maid.getPersistentData().remove(ContractMaidKeys.NATIVE_STORAGE_TRANSFER);
            maid.getPersistentData().remove(ContractMaidKeys.CORE_STORAGE_TRANSFER);
            String bindingId = ContractCarrierData.ensureBindingId(weaponStack);
            maid.getPersistentData().putString(ContractMaidKeys.ENTITY_BINDING_ID, bindingId);
            TlmEntityAdapter.tame(maid, player);
            if (player.level().dimension().equals(
                    ContractInteriorService.INTERIOR_LEVEL)
                    && player.level() instanceof ServerLevel level) {
                var saved = ContractInteriorSavedData.get(level.getServer());
                var plot = saved.find(bindingId);
                // Decide the resident location before publishing a spawn packet.
                // Never expose the ordinary combat manifestation at the player's feet.
                if (plot == null || !plot.hasTerrainTheme()) { weaponStack.setTag(originalWeaponTag); return false; }
                ContractResidentPositionService.restore(maid, plot);
            } else {
                maid.setPos(player.getX(), player.getY(), player.getZ());
            }
            beforeSpawn.accept(maid);
            // The callback can mutate the candidate or world. Revalidate at this commit
            // boundary; entry checks cannot protect a later caller-controlled mutation.
            if (!maid.getStringUUID().equals(ContractCarrierData.getBoundMaidUUID(weaponStack))
                    || !TlmEntityAdapter.isOwnedByPlayer(maid, player)
                    || !bindingId.equals(maid.getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID))
                    || !ContractAuthorization.allows(player, weaponStack)
                    || !ContractWeaponLocator.mayManifest(player, maid.getStringUUID())) {
                weaponStack.setTag(originalWeaponTag);
                return false;
            }

            if (!player.level().isClientSide && !player.level().addFreshEntity(maid)) {
                weaponStack.setTag(originalWeaponTag);
                LOGGER.warn("[MaidWeapon] Maid entity {} could not be added; keeping weapon data for retry",
                        maid.getUUID());
                return false;
            }

        } catch (Exception exception) {
            // A join listener may throw during addFreshEntity. Inspect publication, not just
            // the return value: an already-published real entity must become authoritative.
            if (!(player.level() instanceof ServerLevel level)
                    || maid == null || level.getEntity(maid.getUUID()) != maid) {
                weaponStack.setTag(originalWeaponTag);
                LOGGER.error("[Contract Blade] Restore failed before publication; snapshot retained", exception);
                player.displayClientMessage(Component.translatable(
                        "maid_weapon.message.contract_nbt_restore_failed"), false);
                return false;
            }
            LOGGER.error("[Contract Blade] Spawn callback failed after publication; completing manifestation", exception);
        }

        Entity manifested = maid;
        // Keep successful-path event order: runtime preparation, destination, payload removal,
        // manifested event, message, attention. A failed optional step cannot undo publication.
        afterCommit("manifest runtime", () -> prepareManifestedMaid(player, weaponStack, manifested));
        SoulTalismanService.manifested(player, weaponStack);
        ContractMaidStorage.remove(weaponStack);
        afterCommit("manifest event", () -> MinecraftForge.EVENT_BUS.post(
                new ContractMaidManifestedEvent(player, manifested, weaponStack)));
        if (notifyPlayer) {
            afterCommit("manifest message", () -> player.displayClientMessage(Component.translatable(
                    "maid_weapon.message.maid_released", manifested.getName().getString()), true));
        }
        afterCommit("manifest attention", () -> MaidAttentionSystem.onManifested(player, weaponStack, manifested));
        return true;
    }

    private static void rollbackCapture(ItemStack weapon, CompoundTag previous, Entity maid,
                                        String binding, String task, String schedule) {
        weapon.setTag(previous);
        if (binding.isEmpty()) maid.getPersistentData().remove(ContractMaidKeys.ENTITY_BINDING_ID);
        else maid.getPersistentData().putString(ContractMaidKeys.ENTITY_BINDING_ID, binding);
        // Best effort applies only to native task APIs; the sole live maid is never discarded.
        if (!task.isEmpty() && !task.equals(TlmEntityAdapter.taskId(maid))
                && !TlmEntityAdapter.switchTask(maid, task))
            LOGGER.warn("[Contract Blade] Retained live maid {}; native task rollback to {} refused", maid.getUUID(), task);
        if (!schedule.isEmpty() && !schedule.equals(TlmEntityAdapter.scheduleName(maid))
                && !TlmEntityAdapter.setSchedule(maid, schedule))
            LOGGER.warn("[Contract Blade] Retained live maid {}; native schedule rollback to {} refused", maid.getUUID(), schedule);
    }

    private static void afterCommit(String phase, Runnable action) {
        try { action.run(); }
        catch (RuntimeException failure) {
            LOGGER.error("[Contract Blade] {} failed after authority commit; transition remains successful", phase, failure);
        }
    }

    public static void prepareManifestedMaid(Player player, ItemStack weaponStack, Entity maid) {
        if (!(maid instanceof LivingEntity living)) return;
        if (maid.level().dimension().equals(
                ContractInteriorService.INTERIOR_LEVEL)) return;
        ContractMaidRuntimeService.prepare(player, weaponStack, living);
    }

    public static void syncFavorabilityFromMaid(Entity maid, ItemStack weaponStack) {
        if (!TlmEntityAdapter.isMaidEntity(maid)
                || !ContractCarrierData.hasMaidData(weaponStack)) return;
        MaidWeaponData data = ContractCarrierData.getMaidData(weaponStack);
        int actual = Math.min(MaidWeaponData.MAX_FAVORABILITY,
                Math.max(MaidWeaponData.MIN_FAVORABILITY,
                        TlmEntityAdapter.favorability(maid)));
        if (data.getFavorability() == actual) return;
        data.setFavorability(actual);
        ContractCarrierData.setMaidData(weaponStack, data);
    }

    private ContractMaidLifecycleService() {}
}

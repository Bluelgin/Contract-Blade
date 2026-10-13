package com.maidweapon.forge.system.contract;

import com.maidweapon.forge.api.ContractAuthorization;
import com.maidweapon.forge.compat.tlm.*;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.maidweapon.forge.system.deployment.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Reconciles current facts. Historical incidents never override current storage or authorize resurrection. */
public final class ContractAnomalyRecoveryService {
    /** STORED/MANIFESTED are facts; TRANSITIONING is session-only; missing/pending are diagnostics. */
    public enum State { STORED, MANIFESTED, TRANSITIONING, SUSPECT_MISSING, RECOVERY_PENDING, UNBOUND }
    private static final Map<UUID, Integer> CURSORS = new HashMap<>();
    private static final Set<ItemStack> TRANSITIONS = Collections.newSetFromMap(new IdentityHashMap<>());
    public static boolean transitioning(ItemStack item) { return TRANSITIONS.contains(item); }
    public static void begin(ItemStack item) { TRANSITIONS.add(item); }
    public static void end(ItemStack item) { TRANSITIONS.remove(item); }
    public static State state(Player owner, ItemStack carrier) {
        if (!MaidInfusion.isInfused(carrier) || ContractCarrierData.isContractSuperseded(carrier)) return State.UNBOUND;
        if (transitioning(carrier)) return State.TRANSITIONING;
        if (MaidInfusion.containsMaid(carrier)) return State.STORED;
        Entity entity = ContractWeaponLocator.findManifestedMaid(owner, ContractCarrierData.getBoundMaidUUID(carrier));
        if (entity != null && entity.isAlive()) return State.MANIFESTED;
        var record = incident(owner, carrier);
        return record.getBoolean("Confirmed") && matches(owner, carrier, record) ? State.RECOVERY_PENDING : State.SUSPECT_MISSING;
    }
    public static CompoundTag incident(Player owner, ItemStack carrier) {
        String binding = ContractCarrierData.getBindingId(carrier);
        return owner == null || owner.getServer() == null || binding == null ? new CompoundTag()
                : ContractRecoveryJournal.get(owner.getServer()).incident(binding);
    }
    public static boolean releasesSlot(Player owner, ItemStack carrier) {
        // Unknown/unloaded maids keep their identity, but do not reserve a follower slot forever.
        return MaidInfusion.containsMaid(carrier)
                || ContractWeaponLocator.findManifestedMaid(owner, ContractCarrierData.getBoundMaidUUID(carrier)) == null;
    }
    /** Preserve the newest real data after a confirmed removal, never an older backup. */
    public static boolean preserveRemoved(Player owner, ItemStack carrier, LivingEntity maid) {
        var reason = maid.getRemovalReason();
        if (reason != Entity.RemovalReason.DISCARDED && reason != Entity.RemovalReason.KILLED
                || maid.getPersistentData().getBoolean(ContractMaidKeys.NATIVE_STORAGE_TRANSFER)
                || maid.getPersistentData().getBoolean(ContractMaidKeys.CORE_STORAGE_TRANSFER)
                || carrier.isEmpty() || !TlmEntityAdapter.isOwnedMaid(maid, owner)
                || !ContractCarrierData.isOwner(carrier, owner) || !ContractCarrierData.isBoundMaid(carrier, maid)
                || !ContractAuthorization.allows(owner, carrier)
                || !Objects.equals(ContractCarrierData.getBindingId(carrier),
                    maid.getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID))) return false;
        try {
            var data = TlmEntityAdapter.save(maid);
            if (!data.hasUUID("Owner") || !owner.getUUID().equals(data.getUUID("Owner"))
                    || !data.hasUUID("UUID") || !maid.getUUID().equals(data.getUUID("UUID"))) return false;
            if (data.getFloat("Health") <= 0) {
                data.putFloat("Health", Math.max(1, maid.getMaxHealth() * .25f));
                data.remove("DeathTime"); data.remove("HurtTime");
            }
            if (!ContractMaidStorage.commit(owner, carrier, data)) {
                // Only retain a last real snapshot if the carrier encoder failed. Legacy format stays readable.
                var retained = new CompoundTag(); retained.putUUID("Owner", owner.getUUID());
                retained.putString("Binding", ContractCarrierData.getBindingId(carrier));
                retained.putString("Maid", maid.getStringUUID()); retained.putBoolean("Confirmed", true);
                retained.putString("Source", "CURRENT_REMOVED_SNAPSHOT");
                MaidEntityDataCodec.write(retained, data);
                var journal = ContractRecoveryJournal.get(owner.getServer());
                if (journal.knownSchema()) journal.incident(ContractCarrierData.getBindingId(carrier), retained);
                return false;
            }
            SoulTalismanService.captured(owner, carrier);
            clearRuntime(owner, carrier, maid.getStringUUID()); stored(owner, carrier);
            return true;
        } catch (java.io.IOException | RuntimeException failed) { return false; }
    }
    public static void stored(Player owner, ItemStack carrier) {
        if (owner.getServer() != null && MaidInfusion.containsMaid(carrier))
            ContractRecoveryJournal.get(owner.getServer()).clearIncident(ContractCarrierData.getBindingId(carrier));
        if (carrier.hasTag() && carrier.getTag().getBoolean("MaidWeaponClientSuppliedContract")) {
            for (var channel : com.maidweapon.forge.system.mastery.ContractMasteryMigration.channels(carrier))
                if (channel.getCompound("MaidData").contains("Level")) channel.getCompound("MaidData").putBoolean("ClientSuppliedGrowth", true);
            carrier.getTag().remove("MaidWeaponClientSuppliedContract");
        }
    }
    /** Legacy addon entry: the reason is diagnostic-only and never affects authority. */
    @Deprecated
    public static void record(Player owner, ItemStack carrier, String reason, boolean confirmed) {
        if (confirmed) confirmedRemoved(owner, carrier);
    }
    public static void confirmedRemoved(Player owner, ItemStack carrier) {
        if (owner == null || owner.getServer() == null) return;
        ContractRecoveryJournal.get(owner.getServer()).markMaidAbsent(ContractCarrierData.getBoundMaidUUID(carrier));
        clearRuntime(owner, carrier, ContractCarrierData.getBoundMaidUUID(carrier));
        owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("maid_weapon.recovery.native_backup"), false);
    }
    public static boolean matches(Player owner, ItemStack carrier, CompoundTag record) {
        return record.hasUUID("Owner") && record.getUUID("Owner").equals(owner.getUUID())
                && record.getString("Maid").equals(ContractCarrierData.getBoundMaidUUID(carrier))
                && record.getString("Binding").equals(ContractCarrierData.getBindingId(carrier));
    }
    public static void clearRuntime(Player owner, ItemStack carrier, String maid) {
        ContractActiveDeployments.forgetMaid(owner.getUUID(), maid); ContractRecoveryService.cancel(owner, maid);
        ContractCompanionService.cancelRequests(owner); ContractCompanionState.clear(carrier);
        ContractRecoveryService.clearLocation(carrier);
    }
    /** At most eight carried contracts per pass, with no disk IO or backup discovery. */
    public static void reconcile(ServerPlayer owner) {
        var candidates = new ArrayList<>(ContractWeaponLocator.indexByBinding(owner).values());
        if (candidates.isEmpty()) return;
        int cursor = CURSORS.getOrDefault(owner.getUUID(), 0) % candidates.size();
        CURSORS.put(owner.getUUID(), (cursor + 8) % candidates.size());
        for (int i = 0; i < Math.min(8, candidates.size()); i++) {
            var carrier = candidates.get((cursor + i) % candidates.size());
            if (!MaidInfusion.isInfused(carrier) || !ContractCarrierData.isOwner(carrier, owner)
                    || ContractCarrierData.isContractSuperseded(carrier) || ContractTransferSafetyService.isProjectionPhantom(carrier)) continue;
            if (MaidInfusion.containsMaid(carrier)) {
                stored(owner, carrier); // clear old REVIEW_REQUIRED even on a complete interrupted commit
                continue;
            }
            var entity = ContractWeaponLocator.findManifestedMaid(owner, ContractCarrierData.getBoundMaidUUID(carrier));
            if (entity != null) {
                ContractRecoveryJournal.get(owner.getServer()).clearIncident(ContractCarrierData.getBindingId(carrier));
                continue;
            }
            ContractActiveDeployments.forgetMaid(owner.getUUID(), ContractCarrierData.getBoundMaidUUID(carrier));
            // Keep the last known position and bounded chunk-recall path for unloaded maids.
            // Upgrade an old proven removal; unknown/unloaded references never authorize another spawn.
            var legacy = incident(owner, carrier);
            if (matches(owner, carrier, legacy) && legacy.getBoolean("Confirmed")) {
                var retained = retainedSnapshot(owner, carrier);
                if (retained != null && !ContractRecoveryJournal.get(owner.getServer()).isMaidPresent(legacy.getString("Maid"))
                        && ContractAuthorization.allows(owner, carrier)
                        && ContractMaidStorage.commit(owner, carrier, retained)) {
                    SoulTalismanService.captured(owner, carrier); stored(owner, carrier);
                }
            }
        }
    }
    /** Legacy signature: an incident is never permission to clear or respawn an entity. */
    @Deprecated
    public static boolean cleanup(Player owner, ItemStack carrier, CompoundTag record) {
        return cleanup(owner, carrier);
    }
    public static boolean cleanup(Player owner, ItemStack carrier) {
        if (!ContractCarrierData.isOwner(carrier, owner) || ContractWeaponLocator.findManifestedMaid(owner,
                ContractCarrierData.getBoundMaidUUID(carrier)) != null) return false;
        clearRuntime(owner, carrier, ContractCarrierData.getBoundMaidUUID(carrier));
        if (MaidInfusion.containsMaid(carrier)) stored(owner, carrier);
        return true; // retain identity so native restoration can reconnect, including foxes
    }
    public static CompoundTag retainedSnapshot(Player owner, ItemStack carrier) {
        var record = incident(owner, carrier);
        if (!matches(owner, carrier, record) || !record.getString("Source").equals("CURRENT_REMOVED_SNAPSHOT")) return null;
        try {
            var data = MaidEntityDataCodec.read(record);
            return data.hasUUID("Owner") && data.getUUID("Owner").equals(owner.getUUID())
                    && data.hasUUID("UUID") && data.getUUID("UUID").toString().equals(record.getString("Maid")) ? data : null;
        } catch (java.io.IOException invalid) { return null; }
    }
    public static void clearSession() { TRANSITIONS.clear(); CURSORS.clear(); }
    private ContractAnomalyRecoveryService() { }
}

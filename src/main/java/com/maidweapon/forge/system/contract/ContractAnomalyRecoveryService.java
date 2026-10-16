package com.maidweapon.forge.system.contract;

import com.maidweapon.forge.api.ContractAuthorization;
import com.maidweapon.forge.compat.tlm.*;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.deployment.*;
import com.maidweapon.forge.system.fox.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Incident recovery is evidence-based. Missing loaded entities alone never authorize resurrection or deletion. */
public final class ContractAnomalyRecoveryService {
    public enum State { STORED, MANIFESTED, TRANSITIONING, SUSPECT_MISSING, RECOVERY_PENDING, UNBOUND }
    private static final Map<UUID, Integer> CURSORS = new HashMap<>();
    private static final Set<ItemStack> TRANSITIONS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<UUID, Map<String, CompletableFuture<TlmBackupBridge.Result>>> READS = new HashMap<>();
    public static boolean transitioning(ItemStack item) { return TRANSITIONS.contains(item); }
    public static void begin(ItemStack item) { TRANSITIONS.add(item); }
    public static void end(ItemStack item) { TRANSITIONS.remove(item); }
    public static State state(Player owner, ItemStack carrier) {
        if (!MaidInfusion.isInfused(carrier) || ContractCarrierData.isContractSuperseded(carrier)) return State.UNBOUND;
        if (TRANSITIONS.contains(carrier)) return State.TRANSITIONING;
        var incident = incident(owner, carrier);
        if ((pending(incident) || incident.getString("Status").equals("REVIEW_REQUIRED")) && incident.getBoolean("Confirmed")) return State.RECOVERY_PENDING;
        if (MaidInfusion.containsMaid(carrier)) return State.STORED;
        String id = ContractCarrierData.getBoundMaidUUID(carrier);
        Entity entity = id == null ? null : ContractWeaponLocator.findManifestedMaid(owner, id);
        return entity != null && entity.isAlive() && !entity.isRemoved() ? State.MANIFESTED : State.SUSPECT_MISSING;
    }
    public static CompoundTag incident(Player owner, ItemStack carrier) {
        String binding = ContractCarrierData.getBindingId(carrier);
        return owner.getServer() == null || binding == null ? new CompoundTag()
                : ContractRecoveryJournal.get(owner.getServer()).incident(binding);
    }
    private static boolean pending(CompoundTag record) {
        return record.getString("Status").equals("PENDING") || record.getString("Status").equals("COMMITTING");
    }
    public static boolean releasesSlot(Player owner, ItemStack carrier) {
        var record = incident(owner, carrier);
        return (pending(record) || record.getString("Status").equals("REVIEW_REQUIRED")) && record.getBoolean("Confirmed")
                && matches(owner, carrier, record) && ContractWeaponLocator.findManifestedMaid(owner, record.getString("Maid")) == null;
    }
    /** A directly deleted, still-healthy native entity retains the newest inventory; no old-backup rollback. */
    public static boolean preserveRemoved(Player owner, ItemStack carrier, LivingEntity maid) {
        boolean killed = maid.getRemovalReason() == Entity.RemovalReason.KILLED;
        if (maid.getRemovalReason() != Entity.RemovalReason.DISCARDED && !killed
                || !killed && maid.getHealth() <= 0
                || maid.getPersistentData().getBoolean("MaidWeaponNativeStorageTransfer")
                || carrier.isEmpty() || !ContractCarrierData.isOwner(carrier, owner)
                || !ContractCarrierData.isBoundMaid(carrier, maid) || !ContractAuthorization.allows(owner, carrier)
                || !Objects.equals(ContractCarrierData.getBindingId(carrier),
                    maid.getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID))) return false;
        var record = incident(owner, carrier);
        if (record.getBoolean("BackupSpent") && pending(record)) return false;
        try {
            var data = TlmEntityAdapter.save(maid);
            if (!data.hasUUID("Owner") || !owner.getUUID().equals(data.getUUID("Owner"))
                    || !data.hasUUID("UUID") || !maid.getUUID().equals(data.getUUID("UUID"))) return false;
            if (killed) {
                if (!emptyEquipment(data) || !safeCapabilities(data.getCompound("ForgeCaps"))) return false;
                data.putFloat("Health", Math.max(1, maid.getMaxHealth() * .25f));
                data.remove("DeathTime"); data.remove("HurtTime");
            }
            if (!ContractMaidStorage.commit(owner, carrier, data)) {
                // A malformed/oversized carrier must not cost the last valid, independently encodable entity snapshot.
                record(owner, carrier, "CURRENT_REMOVED_SNAPSHOT", true);
                var retained = incident(owner, carrier);
                retained.putString("Source", "CURRENT_REMOVED_SNAPSHOT");
                if (FoxSpiritState.hasResident(carrier)) retained.put("Fox", FoxSpiritState.resident(carrier).copy());
                try {
                    com.maidweapon.forge.system.MaidEntityDataCodec.write(retained, data);
                    ContractRecoveryJournal.get(owner.getServer()).incident(ContractCarrierData.getBindingId(carrier), retained);
                } catch (java.io.IOException unsafe) { /* Do not replace any older safe snapshot. */ }
                return false;
            }
            clearRuntime(owner, carrier, maid.getStringUUID());
            record(owner, carrier, "CURRENT_REMOVED_SNAPSHOT", true);
            var saved = incident(owner, carrier); saved.putString("Status", "RESTORED_CURRENT");
            ContractRecoveryJournal.get(owner.getServer()).incident(ContractCarrierData.getBindingId(carrier), saved);
            return true;
        } catch (RuntimeException failed) { return false; }
    }

    public static void stored(Player owner, ItemStack carrier) {
        var record = incident(owner, carrier);
        if (pending(record) && matches(owner, carrier, record) && ContractCarrierData.hasMaidEntityData(carrier)) {
            record.putString("Status", "RESOLVED");
            com.maidweapon.forge.system.MaidEntityDataCodec.remove(record);
            ContractRecoveryJournal.get(owner.getServer()).incident(ContractCarrierData.getBindingId(carrier), record);
        }
    }

    public static void recallFailed(Player owner, ItemStack carrier) {
        record(owner, carrier, "RECALL_FAILED", false);
    }
    public static void observeOffline(Entity entity, UUID owner) {
        var reason = entity.getRemovalReason();
        if (entity.getServer() == null || reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED
                || entity.getPersistentData().getBoolean("MaidWeaponNativeStorageTransfer")
                || entity.getPersistentData().getBoolean("MaidWeaponCoreStorageTransfer")) return;
        String binding = entity.getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID);
        if (binding.isEmpty()) return;
        var journal = ContractRecoveryJournal.get(entity.getServer());
        var record = journal.incident(binding);
        if (!record.isEmpty() && record.hasUUID("Owner") && !record.getUUID("Owner").equals(owner)) return;
        if (record.getString("Status").equals("COMMITTING")) return;
        record.putUUID("Owner", owner); record.putString("Binding", binding); record.putString("Maid", entity.getStringUUID());
        record.putString("Reason", reason.name()); record.putString("Status", "PENDING"); record.putBoolean("Confirmed", true);
        record.putLong("Detected", entity.getServer().overworld().getGameTime());
        record.putString("Special", entity.getPersistentData().getString("MaidWeaponEmbeddedSpirit"));
        journal.incident(binding, record);
    }

    public static void record(Player owner, ItemStack carrier, String reason, boolean confirmed) {
        if (owner.level().isClientSide || owner.getServer() == null || !MaidInfusion.isInfused(carrier)
                || !ContractCarrierData.isOwner(carrier, owner)) return;
        String binding = ContractCarrierData.getBindingId(carrier), maid = ContractCarrierData.getBoundMaidUUID(carrier);
        if (binding == null || maid == null || binding.isBlank() || maid.isBlank()) return;
        var journal = ContractRecoveryJournal.get(owner.getServer());
        var previous = journal.incident(binding);
        if (pending(previous) && matches(owner, carrier, previous) && (!confirmed || previous.getBoolean("Confirmed"))) return;
        var value = new CompoundTag(); value.putUUID("Owner", owner.getUUID()); value.putString("Maid", maid);
        value.putString("Binding", binding); value.putString("Reason", reason); value.putBoolean("Confirmed", confirmed);
        value.putString("Status", "PENDING"); value.putLong("Detected", owner.getServer().overworld().getGameTime());
        if (FoxSpiritState.hasResident(carrier)) value.put("Fox", FoxSpiritState.resident(carrier).copy());
        if (previous.getBoolean("BackupSpent")) value.putBoolean("BackupSpent", true);
        if (previous.contains("Backup")) value.putString("Backup", previous.getString("Backup"));
        journal.incident(binding, value);
        if (confirmed) clearRuntime(owner, carrier, maid);
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
    /** Low frequency and bounded by carried contracts. No world-wide scan and no IO in combat callbacks. */
    public static void reconcile(ServerPlayer owner) {
        var candidates = new ArrayList<>(ContractWeaponLocator.indexByBinding(owner).values());
        if (candidates.isEmpty()) return;
        int cursor = CURSORS.getOrDefault(owner.getUUID(), 0) % candidates.size();
        CURSORS.put(owner.getUUID(), (cursor + 8) % candidates.size());
        for (int index = 0; index < Math.min(8, candidates.size()); index++) {
            var carrier = candidates.get((cursor + index) % candidates.size());
            if (!MaidInfusion.isInfused(carrier) || !ContractCarrierData.isOwner(carrier, owner)
                    || ContractCarrierData.isContractSuperseded(carrier) || ContractTransferSafetyService.isProjectionPhantom(carrier)) continue;
            var record = incident(owner, carrier);
            if (!pending(record)) {
                if (!MaidInfusion.containsMaid(carrier) && state(owner, carrier) == State.SUSPECT_MISSING)
                    record(owner, carrier, "NOT_LOADED", false);
                continue;
            }
            if (!matches(owner, carrier, record)) continue;
            var entity = ContractWeaponLocator.findManifestedMaid(owner, record.getString("Maid"));
            if (entity != null && entity.isAlive() && !entity.isRemoved() || MaidInfusion.containsMaid(carrier)) {
                if (record.getString("Status").equals("COMMITTING")) {
                    record.putString("Status", "REVIEW_REQUIRED"); record.putString("Diagnostic", "Interrupted commit: inspect stored payload, do not replay");
                } else record.putString("Status", "RESOLVED");
                ContractRecoveryJournal.get(owner.getServer()).incident(record.getString("Binding"), record); continue;
            }
            if (!record.getBoolean("Confirmed")) continue; // unload, travel, logout and unknown loss stay uncertain
            clearRuntime(owner, carrier, record.getString("Maid"));
            if (FoxSpiritState.hasResident(carrier) && !record.contains("Fox")) {
                var fox = FoxSpiritState.resident(carrier);
                if (FoxSpiritLedger.get(owner.getServer()).owns(fox, "WEAPON") && fox.getUUID("OwnerUUID").equals(owner.getUUID())) {
                    record.put("Fox", fox.copy()); ContractRecoveryJournal.get(owner.getServer()).incident(record.getString("Binding"), record);
                }
            }
            if (record.contains("Fox") || !record.getString("Special").isEmpty() || FoxSpiritState.hasResident(carrier)
                    || com.maidweapon.forge.system.MaidEntityDataCodec.hasData(record)) continue; // never automatically overwrite equipment from an older backup
            var read = READS.computeIfAbsent(owner.getUUID(), key -> new HashMap<>());
            String binding = record.getString("Binding");
            var future = read.get(binding);
            if (future == null) {
                if (record.getBoolean("BackupChecked")) continue;
                try { read.put(binding, TlmBackupBridge.latest(owner, UUID.fromString(record.getString("Maid")))); }
                catch (IllegalArgumentException invalid) { record.putString("Diagnostic", "Invalid maid UUID retained"); }
            } else if (future.isDone()) {
                var result = future.join(); read.remove(binding);
                record.putBoolean("BackupChecked", true); record.putString("Diagnostic", result.diagnostic());
                if (result.data() == null && result.diagnostic().equals("No TLM backup")) cleanup(owner, carrier, record);
                else { record.putString("Backup", result.file()); ContractRecoveryJournal.get(owner.getServer()).incident(binding, record); }
            }
        }
    }
    public static boolean cleanup(Player owner, ItemStack carrier, CompoundTag record) {
        if (!matches(owner, carrier, record) || !pending(record) || !record.getBoolean("Confirmed")
                || record.contains("Fox") || !record.getString("Special").isEmpty() || FoxSpiritState.hasResident(carrier)
                || com.maidweapon.forge.api.EmbeddedSpiritApi.isSpirit(carrier, FoxSpiritState.WHITE)
                || com.maidweapon.forge.api.EmbeddedSpiritApi.isSpirit(carrier, FoxSpiritState.BLACK)
                || !ContractAuthorization.allows(owner, carrier)
                || MaidInfusion.containsMaid(carrier) || ContractWeaponLocator.findManifestedMaid(owner, record.getString("Maid")) != null) return false;
        clearRuntime(owner, carrier, record.getString("Maid"));
        ContractChannelStorage.clearContract(carrier); // native refine, kills, capabilities, other channels and mastery remain untouched
        record.putString("Status", "UNBOUND");
        ContractRecoveryJournal.get(owner.getServer()).incident(record.getString("Binding"), record);
        return true;
    }
    public static TlmBackupBridge.Result retainedSnapshot(Player owner, ItemStack carrier) {
        var record = incident(owner, carrier);
        if (!matches(owner, carrier, record) || !record.getString("Source").equals("CURRENT_REMOVED_SNAPSHOT")
                || !com.maidweapon.forge.system.MaidEntityDataCodec.hasData(record)) return null;
        try { return new TlmBackupBridge.Result("CURRENT_REMOVED_SNAPSHOT",
                com.maidweapon.forge.system.MaidEntityDataCodec.read(record), "Newest proven removed entity; no old-backup rollback"); }
        catch (java.io.IOException invalid) { return null; }
    }

    /** Administrator-only caller; no automatic inventory rollback, no arbitrary entity spawning. */
    public static String restore(ServerPlayer owner, ItemStack carrier, TlmBackupBridge.Result result) {
        var record = incident(owner, carrier);
        String denied = preflight(owner, carrier, record, result);
        if (denied != null) return denied;
        var journal = ContractRecoveryJournal.get(owner.getServer());
        record.putString("Status", "COMMITTING"); record.putBoolean("BackupSpent", true);
        record.putString("Backup", result.file()); journal.incident(record.getString("Binding"), record);
        owner.getServer().overworld().getDataStorage().save(); // persist spent receipt before publishing another stored identity
        var payload = result.data().copy();
        payload.getCompound("ForgeData").putString(ContractMaidKeys.ENTITY_BINDING_ID, record.getString("Binding"));
        payload.putFloat("Health", Math.max(1, payload.getFloat("Health")));
        if (!ContractMaidStorage.commit(owner, carrier, payload)) {
            record.putString("Status", "PENDING"); record.putBoolean("BackupSpent", false);
            record.putString("Diagnostic", "Encoder rejected before publication; retained snapshot can be retried after carrier repair");
            journal.incident(record.getString("Binding"), record); owner.getServer().overworld().getDataStorage().save();
            return "Storage failed before publication; data retained for operator review";
        }
        clearRuntime(owner, carrier, record.getString("Maid"));
        owner.getServer().getPlayerList().saveAll();
        record.putString("Status", "RESTORED"); record.putString("Diagnostic", result.diagnostic());
        journal.incident(record.getString("Binding"), record); owner.getServer().overworld().getDataStorage().save();
        return "RESTORED: same UUID stored in current carrier; " + result.diagnostic();
    }
    public static String preflight(ServerPlayer owner, ItemStack carrier, CompoundTag record, TlmBackupBridge.Result result) {
        if (!matches(owner, carrier, record) || !pending(record) || !record.getBoolean("Confirmed")) return "No confirmed matching incident";
        if (record.getBoolean("BackupSpent") || record.getString("Status").equals("COMMITTING")) return "Receipt spent/interrupted; operator review required";
        if (!ContractAuthorization.allows(owner, carrier) || !ContractCarrierData.isOwner(carrier, owner)
                || ContractCarrierData.isContractSuperseded(carrier) || ContractTransferSafetyService.isProjectionPhantom(carrier)) return "Invalid carrier authority";
        if (MaidInfusion.containsMaid(carrier)) return "Current stored snapshot takes precedence";
        if (ContractWeaponLocator.findManifestedMaid(owner, record.getString("Maid")) != null) return "Original entity still exists";
        var transfer = ContractRecoveryJournal.get(owner.getServer()).transfer(record.getString("Maid"));
        if (transfer.getString("Kind").equals("SEAL")) return "Maid moved into soul talisman";
        if (FoxSpiritState.hasResident(carrier) || record.contains("Fox") || !record.getString("Special").isEmpty()) {
            var fox = FoxSpiritState.resident(carrier);
            if (!FoxSpiritState.valid(fox) || !FoxSpiritState.sameIdentity(fox, record.getCompound("Fox"))
                    || !FoxSpiritLedger.get(owner.getServer()).owns(fox, "WEAPON")) return "Fox ledger/token/origin moved or conflicts";
        }
        if (result == null || result.data() == null) return result == null ? "Backup read pending" : result.diagnostic();
        var data = result.data();
        if (!data.hasUUID("Owner") || !owner.getUUID().equals(data.getUUID("Owner")) || !data.hasUUID("UUID")
                || !record.getString("Maid").equals(data.getUUID("UUID").toString())) return "Backup owner/UUID mismatch";
        String binding = data.getCompound("ForgeData").getString(ContractMaidKeys.ENTITY_BINDING_ID);
        if (!binding.isEmpty() && !binding.equals(record.getString("Binding"))) return "Backup belongs to another binding";
        var backupFox = data.getCompound("ForgeData").getCompound(FoxSpiritState.ROOT);
        if (!backupFox.isEmpty() && !FoxSpiritState.sameIdentity(backupFox, record.getCompound("Fox"))) return "Backup fox identity/token conflict";
        // TLM deposits inventory and a film in its tombstone. An old inventory snapshot cannot be replayed safely.
        var retained = retainedSnapshot(owner, carrier);
        boolean newestProven = retained != null && retained.file().equals(result.file()) && retained.data().equals(data);
        if (!newestProven && !emptyEquipment(data)) return "Backup contains equipment/capabilities: loot ownership unproven; restoration blocked";
        if (!newestProven && !safeCapabilities(data.getCompound("ForgeCaps"))) return "Unknown backup capabilities require operator review";
        return null;
    }
    /** Verified native SlashBlade 1.9.65 state caps carry numbers, never inventory. Unknown caps are not replayed. */
    public static boolean safeCapabilities(CompoundTag caps) {
        var vocabulary = Map.of("slashblade:mobeffect", Set.of("StunTimeout"),
                "slashblade:inputstate", Set.of("Command"), "slashblade:concentration", Set.of("rawPoint", "lastupdate"));
        for (String id : caps.getAllKeys()) {
            var fields = vocabulary.get(id);
            if (fields == null || !caps.contains(id, 10)) return false;
            var data = caps.getCompound(id);
            if (!fields.containsAll(data.getAllKeys())) return false;
            for (String field : data.getAllKeys())
                if (!(data.get(field) instanceof net.minecraft.nbt.NumericTag number) || !Double.isFinite(number.getAsDouble())) return false;
        }
        return true;
    }

    public static boolean emptyEquipment(CompoundTag data) {
        for (var key : List.of("HandItems", "ArmorItems")) for (var tag : data.getList(key, 10))
            if (!ItemStack.of((CompoundTag)tag).isEmpty()) return false;
        for (var key : List.of("MaidInventory", "MaidBaubleInventory", "MaidHideInventory", "MaidTaskInventory"))
            if (!data.getCompound(key).getList("Items", 10).isEmpty()) return false;
        return data.getCompound("BackpackData").isEmpty() && (data.getString("MaidBackpackType").isEmpty()
                || data.getString("MaidBackpackType").equals("touhou_little_maid:empty"));
    }
    public static void clearSession() { TRANSITIONS.clear(); READS.clear(); CURSORS.clear(); }
    private ContractAnomalyRecoveryService() { }
}

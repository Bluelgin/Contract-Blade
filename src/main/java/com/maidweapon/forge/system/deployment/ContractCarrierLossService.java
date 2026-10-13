package com.maidweapon.forge.system.deployment;

import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.system.contract.ContractRecoveryJournal;
import com.maidweapon.forge.system.contract.ContractRecoveryJournal.Presence;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.maidweapon.forge.system.fox.FoxSpiritLedger;
import com.maidweapon.forge.compat.tlm.ContractMaidStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Bounded carrier-loss work. Identity checks are cheap; entity restoration is scheduled. */
public final class ContractCarrierLossService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CARRIER_LOSS_CONFIRM_TICKS = 40;
    private static final int CHECKS_PER_PASS = 32;
    private static final int RESTORES_PER_PLAYER = 2;
    private static final int RESTORES_PER_SERVER_TICK = 8;
    private static final int RETRY_TICKS = 200;
    public record Candidate(String maidId, String bindingId, ItemStack snapshot, long detectedAt) {}
    private enum RecoveryResult {
        MAID_RELEASED("maid_weapon.message.carrier_destroyed_maid_released"),
        FILM_DELIVERED("maid_weapon.message.carrier_destroyed_film_created"),
        NATIVE_BACKUP_REQUIRED("maid_weapon.recovery.native_backup"),
        STILL_IN_WORLD("maid_weapon.recovery.still_away"),
        RETRY_LATER(null), INVALID_IDENTITY(null);
        final String message;
        RecoveryResult(String message) { this.message = message; }
        boolean complete() {
            return switch (this) {
                case RETRY_LATER, INVALID_IDENTITY -> false;
                default -> true;
            };
        }
    }
    static final class Task {
        Candidate candidate;
        long nextAttempt;
        boolean durabilityConfirmed;
        boolean deliveryReserved;
        boolean quarantined;
        int attempts;
        Task(Candidate candidate, boolean durabilityConfirmed) {
            this.candidate = candidate; this.durabilityConfirmed = durabilityConfirmed;
        }
    }
    private static final Map<MinecraftServer, Budget> BUDGETS = new WeakHashMap<>();
    private static final class Budget {
        long tick = Long.MIN_VALUE;
        int used;
        final Map<UUID, Integer> players = new HashMap<>();
    }

    /** Called only after actual durability consumption, never from generic Forge destruction events. */
    public static void scheduleDestroyed(Player player, String maidId, String bindingId, ItemStack snapshot) {
        boolean unresolved = maidId == null || maidId.isEmpty() || bindingId == null || bindingId.isEmpty();
        if (unresolved) {
            if (snapshot.isEmpty() || !MaidInfusion.isInfused(snapshot)) return;
            // A recovery task key is not a fabricated BindingId. Retain unknown legacy identity without running it.
            bindingId = "unresolved:" + UUID.randomUUID();
            maidId = maidId == null ? "" : maidId;
        }
        var journal = ContractCarrierLossJournal.get(player.getServer());
        var pending = journal.pending.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashMap<>());
        Task existing = pending.get(bindingId);
        // Deduplicate before copying the full payload; retain the earliest loss time.
        if (existing != null) {
            boolean changed = false;
            if (!existing.durabilityConfirmed) {
                existing.candidate = new Candidate(maidId, bindingId, snapshot.copy(), clock(player));
                existing.nextAttempt = 0;
                existing.durabilityConfirmed = true;
                changed = true;
            }
            if (!MaidInfusion.containsMaid(existing.candidate.snapshot()) && MaidInfusion.containsMaid(snapshot)) {
                existing.candidate = new Candidate(maidId, bindingId, snapshot.copy(), existing.candidate.detectedAt());
                changed = true;
            }
            if (changed) journal.setDirty();
            return;
        }
        var task = new Task(new Candidate(maidId, bindingId, snapshot.copy(), clock(player)), true);
        task.quarantined = unresolved;
        pending.put(bindingId, task);
        journal.setDirty();
    }

    public static boolean confirmedSnapshot(Player player, ItemStack snapshot) {
        if (player == null || player.getServer() == null || snapshot.isEmpty()) return false;
        var queue = ContractCarrierLossJournal.get(player.getServer()).pending.get(player.getUUID());
        var task = queue == null ? null : queue.get(com.maidweapon.forge.system.contract.ContractCarrierData.getBindingId(snapshot));
        return task != null && task.durabilityConfirmed
                && task.candidate.snapshot().save(new net.minecraft.nbt.CompoundTag()).equals(snapshot.save(new net.minecraft.nbt.CompoundTag()));
    }

    public static boolean hasPending(Player player) {
        return ContractCarrierLossJournal.get(player.getServer()).pending.containsKey(player.getUUID());
    }

    public static void process(Player player, Map<String, ItemStack> carriers,
                               BiConsumer<Player, Candidate> completed) {
        var journal = ContractCarrierLossJournal.get(player.getServer());
        var pending = journal.pending.get(player.getUUID());
        if (pending == null) return;
        long now = clock(player);
        Budget budget = BUDGETS.computeIfAbsent(player.getServer(), ignored -> new Budget());
        if (budget.tick != now) { budget.tick = now; budget.used = 0; budget.players.clear(); }
        int restores = budget.players.getOrDefault(player.getUUID(), 0);
        int checks = Math.min(CHECKS_PER_PASS, pending.size());
        boolean changed = false;
        for (int i = 0; i < checks; i++) {
            var entry = pending.entrySet().iterator().next();
            String binding = entry.getKey();
            Task task = entry.getValue();
            pending.remove(binding);
            Candidate candidate = task.candidate;
            if (carriers.containsKey(binding)) { changed = true; continue; }
            pending.put(binding, task);
            // Old journals did not prove that a destruction event came from durability.
            // Preserve their only snapshots, but never run an unproven rescue.
            if (!task.durabilityConfirmed) continue;
            if (task.deliveryReserved || task.quarantined || task.attempts >= 12) continue;
            // Rotate every retained task: a failed first entry cannot starve later ones.
            if (now - candidate.detectedAt() >= CARRIER_LOSS_CONFIRM_TICKS
                    && now >= task.nextAttempt && restores < RESTORES_PER_PLAYER
                    && budget.used < RESTORES_PER_SERVER_TICK) {
                restores++;
                budget.players.put(player.getUUID(), restores);
                budget.used++;
                task.attempts++;
                task.nextAttempt = now + RETRY_TICKS;
                changed = true;
                try {
                    RecoveryResult result = resolve(player, candidate);
                    if (result.complete()) {
                        // Persist completion before observers: a broken callback must not replay a film.
                        pending.remove(binding);
                        journal.setDirty();
                        try {
                            completed.accept(player, candidate);
                            player.displayClientMessage(Component.translatable(result.message), false);
                        } catch (RuntimeException notificationFailure) {
                            LOGGER.error("[Contract Blade] Recovery {} completed; observer failed for {}",
                                    result, binding, notificationFailure);
                        }
                    }
                } catch (RuntimeException failure) {
                    LOGGER.warn("[Contract Blade] Carrier recovery deferred for {}", binding, failure);
                }
            }
        }
        if (pending.isEmpty()) { journal.pending.remove(player.getUUID()); changed = true; }
        // Queue rotation is transient fairness, not a change to contract authority.
        // Only cancellation, retry state or completion requires a saved-data write.
        if (changed) journal.setDirty();
    }

    private static long clock(Player player) {
        return player.getServer().overworld().getGameTime();
    }

    private static boolean deliverEmergencyFilm(Player player, ItemStack film) {
        if (film.isEmpty()) return false;
        ItemStack remainder = film.copy();
        player.getInventory().add(remainder);
        if (remainder.isEmpty()) return true;
        return player.drop(remainder, false) != null;
    }

    /**
     * A destroyed carrier releases the real maid instead of destroying or replacing her.
     *
     * <p>If she was already manifested, the entity is kept alive. If she was stored in
     * the destroyed stack, its final snapshot is manifested beside the owner first.
     * A resurrection film is retained only as the last data-safety fallback when a
     * stored maid cannot be instantiated.</p>
     */
    private static RecoveryResult resolve(
            Player player, Candidate candidate) {
        ItemStack snapshot = candidate.snapshot();
        Entity maid = ContractWeaponLocator.findManifestedMaid(player, candidate.maidId());
        Entity other = ContractWeaponLocator.findEntity(player, candidate.maidId());
        if (other != null && other != maid || maid != null && !candidate.bindingId().equals(
                maid.getPersistentData().getString(TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID))) return RecoveryResult.INVALID_IDENTITY;
        var presence = ContractRecoveryJournal.get(player.getServer()).presence(candidate.maidId());
        if (maid == null && presence == Presence.PRESENT) {
            freeDestination(player, candidate, Presence.PRESENT); // unload is not permission to emit a backup film or respawn
            return RecoveryResult.STILL_IN_WORLD;
        }

        if (maid == null && MaidInfusion.containsMaid(snapshot)) {
            ItemStack transientCarrier = snapshot.copy();
            if (ContractLifecycleService.manifest(player, transientCarrier, false)) {
                maid = ContractWeaponLocator.findManifestedMaid(player, candidate.maidId());
            }
            if (maid == null) {
                ItemStack film = TouhouLittleMaidHelper.createEmergencyResurrectionFilm(
                        player, snapshot, null);
                if (!film.isEmpty() && deliverEmergencyFilm(player, film)) {
                    var fox = FoxSpiritState.resident(snapshot);
                    if (FoxSpiritState.valid(fox))
                        FoxSpiritLedger.get(player.getServer()).record(fox, "EMERGENCY");
                    freeDestination(player, candidate, Presence.ABSENT);
                    if (player instanceof net.minecraft.server.level.ServerPlayer)
                        player.getServer().getPlayerList().saveAll();
                    LOGGER.warn("[MaidWeapon] Contract carrier {} was destroyed; maid {} could not "
                                    + "manifest and was preserved in a TLM film",
                            snapshot.getItem(), candidate.maidId());
                    return RecoveryResult.FILM_DELIVERED;
                }
                if (ContractMaidStorage.read(player, snapshot) == null) {
                    freeDestination(player, candidate, Presence.ABSENT);
                    return RecoveryResult.NATIVE_BACKUP_REQUIRED;
                }
                return RecoveryResult.RETRY_LATER;
            }
        }

        if (maid == null) {
            // The maid may be unloaded. Detach on her next join; never create a replacement.
            freeDestination(player, candidate, presence);
            return RecoveryResult.NATIVE_BACKUP_REQUIRED;
        }

        if (!snapshot.isEmpty() && snapshot.getTag() != null
                && snapshot.getTag().contains("MaidData")) {
            maid.getPersistentData().put(
                    com.maidweapon.forge.compat.tlm.ContractMaidKeys.EMERGENCY_FILM_PROGRESS,
                    snapshot.getTag().getCompound("MaidData").copy());
        }
        var residentFox = FoxSpiritState.resident(snapshot);
        var ledger = FoxSpiritLedger.get(player.getServer());
        if (FoxSpiritState.valid(residentFox) && !ledger.owns(residentFox, "WEAPON"))
            return RecoveryResult.INVALID_IDENTITY;
        // Optional cleanup may throw. Do it before publishing FREE_MAID, otherwise a
        // retry would reject its own partially detached fox as no longer WEAPON-owned.
        ContractMaidRuntimeService.cleanupBeforeRecall(player, snapshot, maid);
        if (FoxSpiritState.valid(residentFox)) {
            maid.getPersistentData().put(FoxSpiritState.ROOT, residentFox.copy());
            ledger.record(residentFox, "FREE_MAID");
        }
        maid.getPersistentData().remove(TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID);
        ContractCompanionState.clear(maid);
        freeDestination(player, candidate, Presence.PRESENT);
        LOGGER.warn("[MaidWeapon] Contract carrier {} was destroyed; maid {} remains alive "
                        + "and has been detached from the missing carrier",
                snapshot.isEmpty() ? "unknown" : snapshot.getItem(), candidate.maidId());
        return RecoveryResult.MAID_RELEASED;
    }


    private static void freeDestination(Player owner, Candidate candidate, Presence presence) {
        var journal = ContractRecoveryJournal.get(owner.getServer());
        if (!journal.knownSchema()) return;
        var fox = FoxSpiritState.resident(candidate.snapshot());
        // Destination is independent of existence. A destroyed carrier cannot keep
        // ownership of a fox that will detach on her next join/native restoration.
        // A delivered film has already moved its ledger to EMERGENCY and is preserved.
        if (FoxSpiritState.valid(fox) && FoxSpiritLedger.get(owner.getServer()).owns(fox, "WEAPON"))
            FoxSpiritLedger.get(owner.getServer()).record(fox, "FREE_MAID");
        journal.destination(candidate.maidId(), owner.getUUID(), candidate.bindingId(), "FREE_MAID",
                presence, FoxSpiritState.valid(fox) ? fox : null);
        journal.clearIncident(candidate.bindingId());
    }

    public static void clearSession() { BUDGETS.clear(); }
    private ContractCarrierLossService() {}
}

package com.maidweapon.forge.system.deployment;

import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
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
    static final class Task {
        Candidate candidate;
        long nextAttempt;
        boolean durabilityConfirmed;
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
        if (maidId == null || maidId.isEmpty() || bindingId == null || bindingId.isEmpty()) return;
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
        pending.put(bindingId, new Task(new Candidate(maidId, bindingId, snapshot.copy(), clock(player)), true));
        journal.setDirty();
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
            // Rotate every retained task: a failed first entry cannot starve later ones.
            if (now - candidate.detectedAt() >= CARRIER_LOSS_CONFIRM_TICKS
                    && now >= task.nextAttempt && restores < RESTORES_PER_PLAYER
                    && budget.used < RESTORES_PER_SERVER_TICK) {
                restores++;
                budget.players.put(player.getUUID(), restores);
                budget.used++;
                task.nextAttempt = now + RETRY_TICKS;
                changed = true;
                try {
                    if (resolve(player, candidate, completed)) pending.remove(binding);
                } catch (RuntimeException failure) {
                    LOGGER.warn("[Contract Blade] Carrier recovery deferred for {}: {}",
                            binding, failure.toString());
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
    private static boolean resolve(
            Player player, Candidate candidate, java.util.function.BiConsumer<Player, Candidate> completed) {
        ItemStack snapshot = candidate.snapshot();
        Entity maid = ContractWeaponLocator.findManifestedMaid(player, candidate.maidId());

        if (maid == null && MaidInfusion.containsMaid(snapshot)) {
            ItemStack transientCarrier = snapshot.copy();
            if (ContractLifecycleService.manifest(player, transientCarrier, false)) {
                maid = ContractWeaponLocator.findManifestedMaid(player, candidate.maidId());
            }
            if (maid == null) {
                ItemStack film = TouhouLittleMaidHelper.createEmergencyResurrectionFilm(
                        player, snapshot, null);
                if (!film.isEmpty() && deliverEmergencyFilm(player, film)) {
                    completed.accept(player, candidate);
                    player.displayClientMessage(Component.translatable(
                            "maid_weapon.message.carrier_destroyed_film_created"), false);
                    LOGGER.error("[MaidWeapon] Contract carrier {} was destroyed; maid {} could not "
                                    + "manifest and was preserved in a TLM film",
                            snapshot.getItem(), candidate.maidId());
                    return true;
                }
                return false;
            }
        }

        if (maid == null) {
            // Never invent a replacement or delete state when the live maid may simply
            // be in an unloaded chunk. Keep the candidate retryable.
            return false;
        }

        if (!snapshot.isEmpty() && snapshot.getTag() != null
                && snapshot.getTag().contains("MaidData")) {
            maid.getPersistentData().put(
                    com.maidweapon.forge.compat.tlm.ContractMaidKeys.EMERGENCY_FILM_PROGRESS,
                    snapshot.getTag().getCompound("MaidData").copy());
        }
        ContractMaidRuntimeService.cleanupBeforeRecall(player, snapshot, maid);
        maid.getPersistentData().remove(TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID);
        ContractCompanionState.clear(maid);
        completed.accept(player, candidate);
        player.displayClientMessage(Component.translatable(
                "maid_weapon.message.carrier_destroyed_maid_released"), false);
        LOGGER.warn("[MaidWeapon] Contract carrier {} was destroyed; maid {} remains alive "
                        + "and has been detached from the missing carrier",
                snapshot.isEmpty() ? "unknown" : snapshot.getItem(), candidate.maidId());
        return true;
    }


    public static void clearSession() { BUDGETS.clear(); }
    private ContractCarrierLossService() {}
}

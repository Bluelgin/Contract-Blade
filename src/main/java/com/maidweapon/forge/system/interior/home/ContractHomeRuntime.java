package com.maidweapon.forge.system.interior.home;

import com.maidweapon.forge.compat.tlm.TlmEntityAdapter;
import com.maidweapon.forge.compat.tlm.TlmHomeBehaviorController;
import com.maidweapon.forge.system.interior.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import java.time.Instant;
import java.util.*;

/** Online behavior only. Maid manifestation/capture remains in ContractInteriorService. */
public final class ContractHomeRuntime {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    public static final int DECISION_INTERVAL = 600;
    public static final int PATH_TIMEOUT = 400;
    private static final String PAUSED = "ContractHomePaused";
    private static final String RESIDENT = "ContractHomeResident";

    private static final class Session {
        final String binding;
        final Mob maid;
        final ContractHomeOfflineState state;
        final ContractInteriorSavedData saved;
        final ContractHomeFurnitureRegistry registry;
        final Set<String> failed = new HashSet<>();
        ContractHomeFurnitureRegistry.Entry target;
        long nextDecision;
        long approachDeadline;
        long nextMovement;
        boolean active;
        boolean performing;
        Session(String binding, Mob maid, ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot) {
            this.binding = binding; this.maid = maid; this.saved = saved; this.state = plot.home();
            registry = new ContractHomeFurnitureRegistry(new BlockPos(ContractInteriorSavedData.originX(plot),
                    ContractInteriorTerrainBuilder.ORIGIN_Y, ContractInteriorSavedData.originZ(plot)),
                    ContractInteriorTerrainBuilder.radiusForStage(plot.generatedStage()));
        }
    }
    public static boolean attached(ServerPlayer player) { return SESSIONS.containsKey(player.getUUID()); }
    public static void start(ServerPlayer player, String binding, Mob maid,
                             ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot) {
        start(player, binding, maid, saved, plot, false);
    }
    public static void start(ServerPlayer player, String binding, Mob maid,
                             ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot, boolean arrival) {
        stop(player);
        if (!plot.hasTerrainTheme() || !TlmEntityAdapter.isOwnedMaid(maid, player)
                || !binding.equals(maid.getPersistentData().getString(
                        com.maidweapon.forge.compat.tlm.ContractMaidKeys.ENTITY_BINDING_ID))) return;
        if (maid.getPersistentData().getBoolean(PAUSED)) {
            maid.setNoAi(false); maid.getPersistentData().remove(PAUSED);
        }
        // Respect a player's pre-existing NoAI setting.
        if (maid.isNoAi()) return;
        var session = new Session(binding, maid, saved, plot);
        session.active = TlmHomeBehaviorController.begin(maid);
        if (!session.active) return;
        maid.getPersistentData().putString(RESIDENT, binding);
        SESSIONS.put(player.getUUID(), session);
        if (arrival) prepareArrival(session, player);
        tick(player);
    }
    private static void prepareArrival(Session s, ServerPlayer player) {
        ServerLevel level = (ServerLevel) s.maid.level();
        long tick = level.getGameTime();
        Instant now = Instant.now();
        var reading = ContractHomeClock.read(s.state.mode, s.state.zone, now, player.getServer().overworld().getDayTime());
        boolean sameMaid = s.state.maidId.equals(s.maid.getStringUUID());
        boolean continuing = ContractHomeArrivalPlanner.continuePrevious(sameMaid, s.state.departedAt,
                now.toEpochMilli(), s.state.activity, reading.phase());
        s.registry.refresh(level, tick);
        var entries = s.registry.available(level, s.maid, Set.of()).stream()
                // First arrival scenes use only proven bed/chair/Joy interactions.
                // Board games and meals remain ordinary attended activities.
                .filter(e -> e.adapter() instanceof com.maidweapon.forge.compat.tlm.TlmHomeFurnitureAdapter
                        || e.adapter() instanceof com.maidweapon.forge.compat.tlm.TlmHomeJoyAdapter).toList();
        var available = EnumSet.of(ContractHomeActivity.IDLE, ContractHomeActivity.WANDER);
        entries.forEach(e -> available.add(e.target().activity()));
        ContractHomeActivity activity = ContractHomeArrivalPlanner.choose(s.binding, s.maid.getUUID(), reading,
                available, s.state.activity, sameMaid, s.state.departedAt, now.toEpochMilli(),
                TlmEntityAdapter.favorability(s.maid));
        long seed = ContractHomeActivityResolver.seed(s.binding, s.maid.getUUID(), reading.slot());
        final var selectedActivity = activity;
        var candidates = new ArrayList<>(entries.stream().filter(e -> e.target().activity() == selectedActivity).toList());
        // Reuse the prior furniture on a short absence before trying alternatives.
        if (continuing) candidates.sort(Comparator.comparing(e -> !e.target().key().equals(s.state.target)));
        boolean posed = false;
        int attempts = 0;
        while (!candidates.isEmpty() && attempts++ < 4) {
            var chosen = continuing && candidates.get(0).target().key().equals(s.state.target)
                    ? candidates.get(0) : selectTarget(candidates, player, TlmEntityAdapter.favorability(s.maid), seed);
            candidates.remove(chosen);
            var before = s.maid.position();
            float yaw = s.maid.getYRot(), pitch = s.maid.getXRot();
            if (!stageAtFurniture(s, chosen, level)) {
                chosen.adapter().stop(s.maid);
                s.maid.moveTo(before.x, before.y, before.z, yaw, pitch);
                continue;
            }
            s.target = chosen; s.performing = true; posed = true;
            break;
        }
        // No target was safe: keep the checkpoint, not a random teleport or a
        // phantom furniture animation. Normal online decisions can try later.
        if (!posed && activity != ContractHomeActivity.WANDER) activity = ContractHomeActivity.IDLE;
        s.state.slot = reading.slot(); s.state.seed = seed; s.state.maidId = s.maid.getStringUUID();
        s.state.activity = activity; s.state.target = posed ? s.target.target().key() : "";
        if (!continuing) s.state.activityStartedAt = now.toEpochMilli();
        s.state.lastSimulatedAt = now.toEpochMilli();
        s.nextDecision = tick + DECISION_INTERVAL;
        s.nextMovement = tick + 100;
        ContractResidentPositionService.remember(s.maid);
        s.saved.setDirty();
    }
    private static boolean stageAtFurniture(Session s, ContractHomeFurnitureRegistry.Entry entry, ServerLevel level) {
        BlockPos pos = entry.target().position();
        if (!s.registry.contains(pos) || !level.hasChunkAt(pos) || !entry.adapter().valid(level, entry.target(), s.maid)) return false;
        // Validate a real approach route and a collision-free standing location
        // beside the furniture before calling its native seat/sleep API.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            BlockPos stand = pos.offset(dx, 0, dz);
            if (!s.registry.contains(stand) || !level.hasChunkAt(stand)
                    || level.getBlockState(stand.below()).isAir()) continue;
            var box = s.maid.getBoundingBox().move(stand.getX() + .5 - s.maid.getX(),
                    stand.getY() - s.maid.getY(), stand.getZ() + .5 - s.maid.getZ());
            if (!level.noCollision(s.maid, box)) continue;
            // Freshly deserialized mobs have not had a physics tick yet. Probe
            // navigation without treating their stale OnGround flag as proof
            // that all furniture is unreachable; restore it before any action.
            boolean grounded = s.maid.onGround();
            net.minecraft.world.level.pathfinder.Path path;
            try {
                s.maid.setOnGround(true);
                path = s.maid.getNavigation().createPath(stand, 1);
            } finally { s.maid.setOnGround(grounded); }
            if (path == null || !path.canReach()) continue;
            s.maid.moveTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
            try { TlmHomeBehaviorController.center(s.maid, pos); }
            catch (ReflectiveOperationException unsupported) { return false; }
            return entry.adapter().start(level, entry.target(), s.maid)
                    && entry.adapter().running(level, entry.target(), s.maid);
        }
        return false;
    }
    public static void tick(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        Mob maid = s.maid;
        if (!s.binding.equals(ContractInteriorService.activeOwnedBinding(player))) { pause(player); return; }
        if (!maid.isAlive() || maid.level() != player.level()
                || !ContractInteriorService.isInside(player) || ContractInteriorService.isGallerySession(player)) {
            stop(player); return;
        }
        long tick = player.level().getGameTime();
        ContractResidentPositionService.remember(maid);
        // Return emergency behavior to TLM; no home path retries during combat/fire/drowning.
        if (maid.hurtTime > 0 || maid.isOnFire() || maid.getAirSupply() < 200
                || maid.getTarget() != null || maid.isLeashed()) {
            if (s.active) { endAction(s); TlmHomeBehaviorController.restore(maid); s.active = false; }
            s.nextDecision = tick + DECISION_INTERVAL;
            return;
        }
        if (!s.active) {
            if (tick < s.nextDecision) return;
            s.active = TlmHomeBehaviorController.begin(maid);
            if (!s.active) { s.nextDecision = tick + DECISION_INTERVAL; return; }
        }
        s.registry.refresh((ServerLevel) maid.level(), tick);
        if (s.target == null && tick >= s.nextMovement) {
            s.nextMovement = tick + 100;
            if (s.state.activity == ContractHomeActivity.STAY_NEAR_PLAYER && s.registry.contains(player.blockPosition())) {
                if (maid.distanceToSqr(player) > 9 && maid.getNavigation().isDone()) approach(s, player.blockPosition(), tick);
                maid.getLookControl().setLookAt(player);
            } else if (s.state.activity == ContractHomeActivity.WANDER && maid.getNavigation().isDone()) {
                var random = new SplittableRandom(s.state.seed ^ tick / 100);
                BlockPos dest = maid.blockPosition().offset(random.nextInt(-6, 7), 0, random.nextInt(-6, 7));
                if (s.registry.contains(dest)) approach(s, dest, tick);
            }
        }
        if (s.target != null) {
            var target = s.target.target();
            var adapter = s.target.adapter();
            if (!adapter.valid((ServerLevel) maid.level(), target, maid)) fail(s, tick);
            else if (s.performing) {
                if (!adapter.running((ServerLevel) maid.level(), target, maid)) fail(s, tick);
            } else if (target.position().distToCenterSqr(maid.position()) <= 4.0) {
                TlmHomeBehaviorController.clearWalk(maid);
                if (adapter.start((ServerLevel) maid.level(), target, maid)) s.performing = true;
                else fail(s, tick);
            } else if (tick >= s.approachDeadline) fail(s, tick);
        }
        if (s.target == null && s.approachDeadline > 0 && tick >= s.approachDeadline) {
            TlmHomeBehaviorController.clearWalk(maid); s.approachDeadline = 0;
        }
        if (tick < s.nextDecision) return;
        s.nextDecision = tick + DECISION_INTERVAL;
        resolve(s, player, tick);
    }
    private static void resolve(Session s, ServerPlayer player, long tick) {
        Instant now = Instant.now();
        var reading = ContractHomeClock.read(s.state.mode, s.state.zone, now,
                player.getServer().overworld().getDayTime());
        boolean sameSlot = s.state.slot == reading.slot() && s.state.maidId.equals(s.maid.getStringUUID());
        if (!sameSlot) s.failed.clear();
        // A half-hour real-time slot must not mean half an hour frozen in one
        // idle pose. Keep sleep stable, but vary waking activities periodically.
        if (s.state.activity != ContractHomeActivity.SLEEP
                && now.toEpochMilli() - s.state.activityStartedAt >= 120000) sameSlot = false;
        var entries = s.registry.available((ServerLevel) s.maid.level(), s.maid, s.failed);
        var available = EnumSet.of(ContractHomeActivity.IDLE, ContractHomeActivity.WANDER,
                ContractHomeActivity.STAY_NEAR_PLAYER);
        entries.forEach(e -> available.add(e.target().activity()));
        long seed = ContractHomeActivityResolver.seed(s.binding, s.maid.getUUID(), reading.slot());
        if (!sameSlot) seed ^= tick / DECISION_INTERVAL;
        int favorability = TlmEntityAdapter.favorability(s.maid);
        var activity = sameSlot && available.contains(s.state.activity) ? s.state.activity
                : ContractHomeActivityResolver.resolve(seed, reading.phase(), available, favorability, true,
                        ContractHomeArrivalPlanner.preference(s.maid.getUUID()));
        ContractHomeFurnitureRegistry.Entry chosen = null;
        final var selectedActivity = activity;
        var candidates = entries.stream().filter(e -> e.target().activity() == selectedActivity).toList();
        if (!candidates.isEmpty()) {
            if (sameSlot) chosen = candidates.stream().filter(e -> e.target().key().equals(s.state.target)).findFirst().orElse(null);
            if (chosen == null) chosen = selectTarget(candidates, player, favorability, seed);
        }
        boolean unchanged = sameSlot && activity == s.state.activity
                && Objects.equals(chosen == null ? "" : chosen.target().key(), s.state.target);
        if (!unchanged || (chosen != null && s.target == null)) {
            endAction(s);
            s.target = chosen;
            if (chosen != null && !approach(s, chosen.target().position(), tick)) {
                s.failed.add(chosen.target().key()); s.target = null;
                activity = ContractHomeActivity.IDLE;
            }
        }
        if (chosen == null && s.target == null) {
            if (activity == ContractHomeActivity.STAY_NEAR_PLAYER && s.registry.contains(player.blockPosition())) {
                if (s.maid.distanceToSqr(player) > 9) approach(s, player.blockPosition(), tick);
                s.maid.getLookControl().setLookAt(player);
            } else if (activity == ContractHomeActivity.WANDER && s.maid.getNavigation().isDone()) {
                var random = new SplittableRandom(seed ^ tick / DECISION_INTERVAL);
                // One bounded attempt per decision; rejected locations never cause a tight retry loop.
                BlockPos dest = s.maid.blockPosition().offset(random.nextInt(-6, 7), 0, random.nextInt(-6, 7));
                if (s.registry.contains(dest)) approach(s, dest, tick);
            }
        }
        if (!unchanged) s.state.activityStartedAt = now.toEpochMilli();
        s.state.lastSimulatedAt = now.toEpochMilli();
        s.state.slot = reading.slot(); s.state.seed = seed; s.state.maidId = s.maid.getStringUUID();
        s.state.activity = activity;
        s.state.target = s.target == null ? "" : s.target.target().key();
        s.saved.setDirty();
    }
    private static ContractHomeFurnitureRegistry.Entry selectTarget(List<ContractHomeFurnitureRegistry.Entry> entries,
            ServerPlayer player, int favorability, long seed) {
        int total = 0;
        for (var entry : entries) total += targetWeight(entry.target(), player, favorability);
        int roll = new SplittableRandom(seed ^ 0x484f4d45L).nextInt(total);
        for (var entry : entries) {
            roll -= targetWeight(entry.target(), player, favorability);
            if (roll < 0) return entry;
        }
        return entries.get(0);
    }
    private static int targetWeight(ActivityTarget target, ServerPlayer player, int favorability) {
        return Math.max(1, Math.min(100, target.weight())) +
                (target.position().distToCenterSqr(player.position()) < 64 ? Math.max(0, Math.min(384, favorability)) / 64 : 0);
    }
    private static boolean approach(Session s, BlockPos dest, long tick) {
        if (!s.registry.contains(dest) || !s.maid.level().hasChunkAt(dest)) return false;
        if (dest.distToCenterSqr(s.maid.position()) <= 4) { s.approachDeadline = tick + PATH_TIMEOUT; return true; }
        var path = s.maid.getNavigation().createPath(dest, 1);
        if (path == null || !path.canReach()) return false;
        try { TlmHomeBehaviorController.center(s.maid, dest); }
        catch (ReflectiveOperationException unsupported) { return false; }
        s.maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(dest, 0.5f, 1));
        s.maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(dest));
        s.approachDeadline = tick + PATH_TIMEOUT;
        return true;
    }
    private static void fail(Session s, long tick) {
        if (s.target != null) s.failed.add(s.target.target().key());
        endAction(s);
        s.state.target = ""; s.state.activity = ContractHomeActivity.IDLE;
        // No immediate retry loop: wait for the next low-frequency decision.
        s.nextDecision = tick + DECISION_INTERVAL;
        s.saved.setDirty();
    }
    private static void endAction(Session s) {
        if (s.target != null && !s.maid.isRemoved()) s.target.adapter().stop(s.maid);
        s.target = null; s.performing = false;
        TlmHomeBehaviorController.clearWalk(s.maid);
    }
    public static void invalidate(ServerLevel level, BlockPos pos) {
        SESSIONS.values().stream().filter(s -> s.maid.level() == level).forEach(s -> s.registry.invalidate(pos));
    }
    public static void clockChanged(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        endAction(s); s.state.slot = Long.MIN_VALUE; s.failed.clear(); s.nextDecision = 0;
        tick(player);
    }
    public static boolean stop(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s == null) return true;
        ContractResidentPositionService.remember(s.maid);
        // Persist the logical selection before stopping its real pose/navigation.
        s.state.lastSimulatedAt = Instant.now().toEpochMilli(); s.saved.setDirty();
        s.state.departedAt = s.state.lastSimulatedAt;
        // Retain the marker until lifecycle capture; failed lookup must still pause unattended AI.
        endAction(s);
        return TlmHomeBehaviorController.restore(s.maid);
    }
    public static void pause(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        stop(player);
        pauseUncaptured(s.maid, s.binding);
    }
    public static boolean prepareCapture(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof Mob maid)) return false;
        // Never serialize Home Life's temporary brain/task/schedule scope into the contract.
        if (!TlmHomeBehaviorController.restore(maid)) return false;
        if (maid.getPersistentData().getBoolean(PAUSED)) {
            maid.setNoAi(false); maid.getPersistentData().remove(PAUSED);
        }
        maid.getPersistentData().remove(RESIDENT);
        return true;
    }

    /** Crash/restart and failed-recall safety. Never load a chunk to find an absent maid. */
    public static void guardResident(Mob maid) {
        if (!maid.level().dimension().equals(ContractInteriorService.INTERIOR_LEVEL)
                || !maid.getPersistentData().contains(RESIDENT)
                || !(maid.level() instanceof ServerLevel level)) return;

        String binding = maid.getPersistentData().getString(RESIDENT);
        ContractInteriorSavedData.Plot plot = ContractInteriorSavedData.get(level.getServer()).find(binding);
        Session session = SESSIONS.values().stream().filter(s -> s.maid == maid).findFirst().orElse(null);

        if (plot != null && plot.generatedStage() > 0
                && (maid.getY() < 30 || !insidePlot(maid.blockPosition(), plot))) {
            BlockPos origin = new BlockPos(
                    ContractInteriorSavedData.originX(plot),
                    ContractInteriorTerrainBuilder.ORIGIN_Y,
                    ContractInteriorSavedData.originZ(plot));
            // getChunkNow is containment-only: never create a ticket for an unattended home.
            if (level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4) != null) {
                if (session != null) {
                    endAction(session);
                    session.failed.clear();
                    session.nextDecision = 0;
                }
                TlmHomeBehaviorController.clearWalk(maid);
                maid.teleportTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
                maid.fallDistance = 0.0F;
                if (session != null && maid.getPersistentData().getBoolean(PAUSED)) {
                    // This PAUSED marker was set by the no-ticket containment fallback.
                    // An attended session has made the home safely available again.
                    maid.setNoAi(false);
                    maid.getPersistentData().remove(PAUSED);
                }
            } else {
                TlmHomeBehaviorController.restore(maid);
                if (!maid.isNoAi()) maid.setNoAi(true);
                maid.getPersistentData().putBoolean(PAUSED, true);
                return;
            }
        }

        boolean attended = session != null;
        if (!attended && !maid.getPersistentData().getBoolean(PAUSED)) {
            TlmHomeBehaviorController.restore(maid);
            if (!maid.isNoAi()) {
                maid.setNoAi(true);
                maid.getPersistentData().putBoolean(PAUSED, true);
            }
        }
    }

    private static boolean insidePlot(BlockPos position, ContractInteriorSavedData.Plot plot) {
        int radius = ContractInteriorTerrainBuilder.radiusForStage(plot.generatedStage());
        double x = Math.abs(position.getX() - ContractInteriorSavedData.originX(plot)) / (double) radius;
        double z = Math.abs(position.getZ() - ContractInteriorSavedData.originZ(plot)) / (double) radius;
        return Math.pow(x, 6) + Math.pow(z, 6) <= 1.0D;
    }
    public static void pauseUncaptured(net.minecraft.world.entity.Entity entity, String binding) {
        if (!(entity instanceof Mob maid)) return;
        maid.getPersistentData().putString(RESIDENT, binding);
        guardResident(maid);
    }

    public static void clear() { SESSIONS.clear(); }
    private ContractHomeRuntime() {}
}

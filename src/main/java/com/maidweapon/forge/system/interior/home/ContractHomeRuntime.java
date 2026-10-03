package com.maidweapon.forge.system.interior.home;

import com.maidweapon.forge.compat.tlm.TlmEntityAdapter;
import com.maidweapon.forge.compat.tlm.TlmHomeBehaviorController;
import com.maidweapon.forge.system.interior.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.time.Instant;
import java.util.*;

/** Native living observation plus one-shot arrival staging; never an online AI scheduler. */
public final class ContractHomeRuntime {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final int SNAPSHOT_INTERVAL = 100;
    private static final String PAUSED = "ContractHomePaused";
    private static final String RESIDENT = "ContractHomeResident";

    private static final class Session {
        final String binding;
        final Mob maid;
        final ContractHomeOfflineState state;
        final ContractInteriorSavedData saved;
        final ContractHomeFurnitureRegistry registry;
        final BlockPos homeCenter;
        ContractHomeFurnitureRegistry.Entry target;
        long nextSnapshot;
        Boolean customNight;
        long arrivalAt = -1;
        ServerPlayer owner;
        Session(String binding, Mob maid, ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot) {
            this.binding = binding; this.maid = maid; this.saved = saved; this.state = plot.home();
            registry = new ContractHomeFurnitureRegistry(new BlockPos(ContractInteriorSavedData.originX(plot),
                    ContractInteriorTerrainBuilder.ORIGIN_Y, ContractInteriorSavedData.originZ(plot)),
                    ContractInteriorTerrainBuilder.radiusForStage(plot.generatedStage()));
            // Anchor native home range to the restored resident, not the player's entrance.
            homeCenter = maid.blockPosition();
        }
    }
    public static boolean attached(ServerPlayer player) { return SESSIONS.containsKey(player.getUUID()); }
    /** Wait for fresh entity/chunk registration only when an arrival scene is needed. */
    public static void startOnVisit(ServerPlayer player, String binding, Mob maid,
                                    ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot, boolean arrival) {
        start(player, binding, maid, saved, plot, arrival, arrival);
    }
    public static void start(ServerPlayer player, String binding, Mob maid,
                             ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot) {
        start(player, binding, maid, saved, plot, false, false);
    }
    public static void start(ServerPlayer player, String binding, Mob maid,
                             ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot, boolean arrival) {
        start(player, binding, maid, saved, plot, arrival, false);
    }
    private static void start(ServerPlayer player, String binding, Mob maid,
                              ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot,
                              boolean arrival, boolean deferred) {
        stop(player);
        if (!plot.hasTerrainTheme() || !TlmEntityAdapter.isOwnedMaid(maid, player)
                || !binding.equals(maid.getPersistentData().getString(
                        com.maidweapon.forge.compat.tlm.ContractMaidKeys.ENTITY_BINDING_ID))) return;
        if (maid.getPersistentData().getBoolean(PAUSED)) {
            maid.setNoAi(false); maid.getPersistentData().remove(PAUSED);
        }
        if (maid.isNoAi()) return;
        var session = new Session(binding, maid, saved, plot);
        session.owner = player;
        if (!TlmHomeBehaviorController.begin(maid)) {
            LOGGER.warn("[ContractHome] Native home setup unavailable; original AI preserved for {}", binding);
            return;
        }
        maid.getPersistentData().putString(RESIDENT, binding);
        SESSIONS.put(player.getUUID(), session);
        if (deferred) {
            loadArrivalChunks(session);
            session.arrivalAt = maid.level().getGameTime() + 2;
        } else if (arrival) {
            prepareArrival(session, player);
            handoffToNative(session);
        }
        tick(player);
    }
    private static void prepareArrival(Session s, ServerPlayer player) {
        ServerLevel level = (ServerLevel) s.maid.level();
        loadArrivalChunks(s);
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
                LOGGER.info("[ContractHome] Arrival furniture rejected binding={}, target={}; retaining checkpoint {}",
                        s.binding, chosen.target().key(), before);
                chosen.adapter().stop(s.maid);
                s.maid.moveTo(before.x, before.y, before.z, yaw, pitch);
                continue;
            }
            s.target = chosen; posed = true;
            break;
        }
        // No target was safe: keep the checkpoint, not a random teleport or a
        // phantom furniture animation. Normal online decisions can try later.
        if (!posed) activity = ContractHomeActivity.WANDER;
        s.state.slot = reading.slot(); s.state.seed = seed; s.state.maidId = s.maid.getStringUUID();
        s.state.activity = activity; s.state.target = posed ? s.target.target().key() : "";
        if (!continuing) s.state.activityStartedAt = now.toEpochMilli();
        s.state.lastSimulatedAt = now.toEpochMilli();
        ContractResidentPositionService.remember(s.maid);
        s.saved.setDirty();
        LOGGER.info("[ContractHome] Arrival binding={}, maid={}, candidates={}, activity={}, posed={}, target={}, position={}",
                s.binding, s.maid.getUUID(), entries.size(), activity, posed, s.state.target, s.maid.position());
    }
    private static void loadArrivalChunks(Session s) {
        // Bounded visit-only loading around the checkpoint; never retain tickets offline.
        ServerLevel level = (ServerLevel) s.maid.level();
        BlockPos checkpoint = s.maid.blockPosition();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            level.getChunk((checkpoint.getX() >> 4) + dx, (checkpoint.getZ() >> 4) + dz);
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
        long tick = maid.level().getGameTime();
        if (s.arrivalAt >= 0) {
            if (tick < s.arrivalAt) return;
            s.arrivalAt = -1;
            prepareArrival(s, player);
            handoffToNative(s);
        }
        // Observation only: TLM owns walking, furniture choice, combat and emergency behavior.
        ContractResidentPositionService.remember(maid);
        if (tick < s.nextSnapshot) return;
        s.nextSnapshot = tick + SNAPSHOT_INTERVAL;
        syncNativeState(s, player);
    }
    private static void handoffToNative(Session s) {
        // Keep the actual native seat/sleep pose; do not stop it merely to hand over.
        s.target = null;
        TlmHomeBehaviorController.releaseManagedSeat(s.maid);
        if (!TlmHomeBehaviorController.enableNativeLiving(s.maid, s.homeCenter)) {
            LOGGER.warn("[ContractHome] Native arrival refresh failed; no replacement AI installed for {}", s.binding);
        }
    }
    private static boolean night(Session s, ServerPlayer player) {
        return ContractHomeClock.read(s.state.mode, s.state.zone, Instant.now(),
                player.getServer().overworld().getDayTime()).phase() == ContractHomeClock.Phase.NIGHT;
    }
    private static void syncNativeState(Session s, ServerPlayer player) {
        // Default Minecraft mode uses the original TLM schedule unchanged.
        if (s.state.mode != ContractHomeClock.Mode.MINECRAFT_TIME) {
            boolean night = night(s, player);
            if (s.customNight == null || s.customNight != night) {
                TlmHomeBehaviorController.syncNativeClock(s.maid, night);
                s.customNight = night;
            }
        } else if (s.customNight != null) {
            TlmHomeBehaviorController.enableNativeLiving(s.maid, s.homeCenter);
            s.customNight = null;
        }
        s.registry.refresh((ServerLevel) s.maid.level(), s.maid.level().getGameTime());
        var activity = s.maid.isSleeping() ? ContractHomeActivity.SLEEP
                : s.maid.isPassenger() ? ContractHomeActivity.SIT : ContractHomeActivity.WANDER;
        String target = "";
        for (var entry : s.registry.available((ServerLevel) s.maid.level(), s.maid, Set.of())) {
            if (entry.adapter().running((ServerLevel) s.maid.level(), entry.target(), s.maid)) {
                activity = entry.target().activity(); target = entry.target().key(); break;
            }
        }
        if (activity != s.state.activity || !target.equals(s.state.target)) s.state.activityStartedAt = Instant.now().toEpochMilli();
        s.state.activity = activity; s.state.target = target; s.state.maidId = s.maid.getStringUUID();
        s.state.lastSimulatedAt = Instant.now().toEpochMilli();
        s.saved.setDirty();
    }
    public static boolean usesCustomClock(Mob maid) {
        return SESSIONS.values().stream().anyMatch(s -> s.maid == maid
                && s.state.mode != ContractHomeClock.Mode.MINECRAFT_TIME);
    }
    /** Only bridge explicitly selected custom clocks, never default native dismounts. */
    public static boolean bridgeNativeSeat(Mob maid, net.minecraft.world.entity.Entity seat) {
        if (!"com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit".equals(seat.getClass().getName())) return false;
        Session s = SESSIONS.values().stream().filter(value -> value.maid == maid && TlmHomeBehaviorController.isNativeLiving(maid)
                && value.state.mode != ContractHomeClock.Mode.MINECRAFT_TIME).findFirst().orElse(null);
        if (s == null) return false;
        if (night(s, s.owner) || !seat.hasPassenger(maid)) return false;
        try {
            if (net.minecraft.world.entity.schedule.Activity.IDLE.equals(
                    maid.getClass().getMethod("getScheduleDetail").invoke(maid))) return false;
            BlockPos pos = (BlockPos) seat.getClass().getMethod("getAssociatedBlockPos").invoke(seat);
            var adapter = new com.maidweapon.forge.compat.tlm.TlmHomeJoyAdapter();
            var target = adapter.blockTarget((ServerLevel) maid.level(), pos);
            return target.isPresent() && adapter.valid((ServerLevel) maid.level(), target.get(), maid);
        } catch (ReflectiveOperationException unsupported) { return false; }
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
    private static void endAction(Session s) {
        if (s.target != null && !s.maid.isRemoved()) s.target.adapter().stop(s.maid);
        s.target = null;
        TlmHomeBehaviorController.clearWalk(s.maid);
    }
    public static void invalidate(ServerLevel level, BlockPos pos) {
        SESSIONS.values().stream().filter(s -> s.maid.level() == level).forEach(s -> s.registry.invalidate(pos));
    }
    public static void clockChanged(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        syncNativeState(s, player);
    }
    public static boolean stop(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s == null) return true;
        if (s.maid.isAlive()) syncNativeState(s, player);
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
                    session.nextSnapshot = 0;
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

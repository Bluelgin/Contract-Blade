package com.maidweapon.forge.system.resonance;

import java.util.*;

/** Session-only server reports, with bounded expiry and independent ability sources. */
public final class ResonanceActivityTracker {
    private record Report(ResonanceActivity activity, long until) { }
    private static final Map<UUID, Map<String, Map<String, Report>>> REPORTS = new HashMap<>();
    public static void report(ResonanceContract context, String source, ResonanceActivity activity, int ticks) {
        if (source == null || source.isBlank() || source.length() > 128 || ticks < 1 || ticks > 1200)
            throw new IllegalArgumentException("Invalid activity report");
        var contracts = REPORTS.computeIfAbsent(context.owner().getUUID(), id -> new HashMap<>());
        var sources = contracts.computeIfAbsent(context.maid(), id -> new HashMap<>());
        if (sources.size() >= 32 && !sources.containsKey(source)) return;
        sources.put(source, new Report(activity, context.owner().getServer().overworld().getGameTime() + ticks));
    }
    public static ResonanceActivity current(ResonanceContract context) {
        var live = context.liveMaid();
        var base = live == null ? ResonanceActivity.STORED : ResonanceActivity.MANIFESTED_IDLE;
        if (live instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null && mob.getTarget().isAlive())
            base = ResonanceActivity.COMBAT;
        return activity(context, base);
    }
    public static ResonanceActivity activity(ResonanceContract context, ResonanceActivity base) {
        var contracts = REPORTS.get(context.owner().getUUID());
        if (contracts == null) return base;
        var sources = contracts.get(context.maid()); if (sources == null) return base;
        long now = context.owner().getServer().overworld().getGameTime();
        sources.values().removeIf(report -> report.until <= now);
        for (var report : sources.values()) if (report.activity.ordinal() > base.ordinal()) base = report.activity;
        return base;
    }
    public static void clear(UUID owner) { REPORTS.remove(owner); }
    public static void clearAll() { REPORTS.clear(); }
    private ResonanceActivityTracker() { }
}

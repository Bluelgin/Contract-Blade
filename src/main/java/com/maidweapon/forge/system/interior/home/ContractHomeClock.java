package com.maidweapon.forge.system.interior.home;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Pure clock calculation: never consults the machine's implicit local timezone. */
public final class ContractHomeClock {
    public enum Mode { REAL_TIME, MINECRAFT_TIME, SERVER_TIME }
    public enum Phase { MORNING, DAY, EVENING, NIGHT }
    public record Reading(long date, int minute, long slot, Phase phase) {}
    public static final int SLOT_MINUTES = 30;
    // Operators may configure a named zone or a fixed offset; UTC is the explicit default.
    public static ZoneId serverZone() {
        return zone(System.getProperty("contractblade.home.serverZone", "UTC"));
    }
    public static ZoneId zone(String id) {
        try { return ZoneId.of(id); }
        catch (RuntimeException invalid) { return ZoneOffset.UTC; }
    }
    public static Reading read(Mode mode, String zone, Instant now, long overworldDayTime) {
        long day;
        int minute;
        if (mode == Mode.MINECRAFT_TIME) {
            // Minecraft tick zero is 06:00. Use the overworld, not the fixed-time interior.
            long shifted = overworldDayTime + 6000L;
            day = Math.floorDiv(shifted, 24000L);
            minute = (int) (Math.floorMod(shifted, 24000L) * 1440L / 24000L);
        } else {
            var time = now.atZone(mode == Mode.SERVER_TIME ? serverZone() : zone(zone));
            day = time.toLocalDate().toEpochDay();
            minute = time.getHour() * 60 + time.getMinute();
        }
        int hour = minute / 60;
        Phase phase = hour < 6 || hour >= 23 ? Phase.NIGHT
                : hour < 10 ? Phase.MORNING : hour < 17 ? Phase.DAY : Phase.EVENING;
        return new Reading(day, minute, day * 48L + minute / SLOT_MINUTES, phase);
    }

    /** Per-viewer sky time; never changes the shared dimension's clock. */
    public static long skyTime(Mode mode, String zone, Instant now, long overworldDayTime) {
        if (mode == Mode.MINECRAFT_TIME) return overworldDayTime;
        Reading reading = read(mode, zone, now, overworldDayTime);
        return Math.floorMod(reading.minute() * 24000L / 1440L - 6000L, 24000L);
    }
    private ContractHomeClock() {}
}

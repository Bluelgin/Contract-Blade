import com.maidweapon.forge.system.interior.home.*;
import java.time.Instant;
import java.util.*;

public class HomeClockResolverTest {
    static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        var real = ContractHomeClock.Mode.REAL_TIME;
        check(ContractHomeClock.read(real, "+08:00", Instant.parse("2026-09-28T22:00:00Z"), 0).phase()
                == ContractHomeClock.Phase.MORNING, "offset/day rollover");
        String[] times = {"05:59", "06:00", "09:59", "10:00", "16:59", "17:00", "22:59", "23:00"};
        String[] phases = {"NIGHT", "MORNING", "MORNING", "DAY", "DAY", "EVENING", "EVENING", "NIGHT"};
        for (int i = 0; i < times.length; i++)
            check(ContractHomeClock.read(real, "UTC", Instant.parse("2026-09-28T" + times[i] + ":00Z"), 0)
                    .phase().name().equals(phases[i]), "phase boundary " + times[i]);
        System.setProperty("contractblade.home.serverZone", "Asia/Shanghai");
        check(ContractHomeClock.read(ContractHomeClock.Mode.SERVER_TIME, "-08:00",
                Instant.parse("2026-09-28T22:00:00Z"), 0).minute() == 360,
                "server uses explicit server zone, not per-home zone");
        System.clearProperty("contractblade.home.serverZone");
        check(ContractHomeClock.read(real, "invalid-zone", Instant.EPOCH, 0).minute() == 0,
                "invalid persisted zone safely defaults UTC");
        var minecraft = ContractHomeClock.Mode.MINECRAFT_TIME;
        check(ContractHomeClock.skyTime(minecraft, "UTC", Instant.EPOCH, 18000) == 18000,
                "minecraft sky follows exact overworld midnight, not fixed noon");
        check(ContractHomeClock.skyTime(real, "+08:00", Instant.parse("2026-09-28T16:00:00Z"), 6000) == 18000,
                "real-time midnight sky matches routine despite overworld noon");
        check(ContractHomeClock.skyTime(real, "UTC", Instant.parse("2026-09-28T12:00:00Z"), 18000) == 6000,
                "independent home noon does not change other home clock");
        check(ContractHomeClock.read(minecraft, "UTC", Instant.EPOCH, 0).minute() == 360, "MC dawn");
        check(ContractHomeClock.read(minecraft, "UTC", Instant.EPOCH, 18000).minute() == 0, "MC midnight");
        check(ContractHomeClock.read(minecraft, "UTC", Instant.EPOCH, -1).minute() == 359, "negative MC time");
        var a = ContractHomeClock.read(real, "America/New_York", Instant.parse("2026-11-01T05:15:00Z"), 0);
        var b = ContractHomeClock.read(real, "America/New_York", Instant.parse("2026-11-01T06:15:00Z"), 0);
        check(a.slot() == b.slot(), "DST repeated wall slot should retain activity");
        var maid = UUID.fromString("4b4baef0-119d-4d43-96b2-43fa22ad429d");
        long seed = ContractHomeActivityResolver.seed("binding-a", maid, a.slot());
        check(seed == ContractHomeActivityResolver.seed("binding-a", maid, b.slot()), "stable reload seed");
        check(seed != ContractHomeActivityResolver.seed("binding-b", maid, b.slot()), "binding isolation");
        check(seed != ContractHomeActivityResolver.seed("binding-a", new UUID(0, 1), b.slot()), "maid isolation");
        var available = EnumSet.of(ContractHomeActivity.IDLE, ContractHomeActivity.WANDER);
        var night = ContractHomeClock.read(real, "UTC", Instant.parse("2026-09-28T23:00:00Z"), 0);
        var furniture = EnumSet.of(ContractHomeActivity.SLEEP, ContractHomeActivity.READ, ContractHomeActivity.SIT, ContractHomeActivity.PLAY);
        check(ContractHomeArrivalPlanner.choose("binding-a", maid, night, furniture,
                ContractHomeActivity.SLEEP, true, 1000000, 1010000, 0) == ContractHomeActivity.SLEEP,
                "short absence preserves sleep");
        check(ContractHomeArrivalPlanner.preference(maid) == ContractHomeArrivalPlanner.preference(maid), "stable maid personality");
        check(ContractHomeArrivalPlanner.weight(ContractHomeActivity.READ, ContractHomeActivity.READ,
                ContractHomeActivity.IDLE, true, ContractHomeClock.Phase.DAY, 0)
                > ContractHomeArrivalPlanner.weight(ContractHomeActivity.READ, ContractHomeActivity.SIT,
                ContractHomeActivity.IDLE, true, ContractHomeClock.Phase.DAY, 0), "reading personality preference");
        check(ContractHomeArrivalPlanner.weight(ContractHomeActivity.READ, ContractHomeActivity.READ,
                ContractHomeActivity.READ, true, ContractHomeClock.Phase.DAY, 0)
                < ContractHomeArrivalPlanner.weight(ContractHomeActivity.READ, ContractHomeActivity.READ,
                ContractHomeActivity.IDLE, true, ContractHomeClock.Phase.DAY, 0), "avoid repeated waking activity");
        check(!ContractHomeArrivalPlanner.continuePrevious(true, 1000000, 5000000,
                ContractHomeActivity.READ, ContractHomeClock.Phase.DAY), "long absence re-evaluates scene");
        check(!ContractHomeArrivalPlanner.continuePrevious(true, 2000000, 1000000,
                ContractHomeActivity.READ, ContractHomeClock.Phase.DAY), "clock rollback does not preserve stale scene");
        for (int i = 0; i < 10000; i++) {
            var identity = new UUID(0, i);
            var choice = ContractHomeArrivalPlanner.choose("home-" + i, identity, night,
                    Set.of(ContractHomeActivity.MEAL), ContractHomeActivity.MEAL, true, 1000, 1000000, 384);
            check(choice == ContractHomeActivity.IDLE || choice == ContractHomeActivity.WANDER,
                    "arrival does not invent missing furniture, food costs or player-follow activity");
        }
        for (int i = 0; i < 10000; i++) {
            var choice = ContractHomeActivityResolver.resolve(i, a.phase(), available, 384, true);
            check(available.contains(choice), "missing furniture removed from pool");
            check(choice == ContractHomeActivityResolver.resolve(i, a.phase(), available, 384, true), "determinism");
        }
        check(ContractHomeActivityResolver.resolve(seed, a.phase(), Set.of(), 0, false)
                == ContractHomeActivity.IDLE, "empty pool fallback");
        check(ContractHomeActivityResolver.weight(ContractHomeActivity.STAY_NEAR_PLAYER, a.phase(), 384, true)
                > ContractHomeActivityResolver.weight(ContractHomeActivity.STAY_NEAR_PLAYER, a.phase(), 0, true), "affection weight");
        check(ContractHomeActivityResolver.weight(ContractHomeActivity.STAY_NEAR_PLAYER, a.phase(), 384, false) == 0, "absent player");
        check(ContractHomeActivityResolver.weight(ContractHomeActivity.READ, ContractHomeClock.Phase.EVENING, 0, true)
                > ContractHomeActivityResolver.weight(ContractHomeActivity.READ, ContractHomeClock.Phase.NIGHT, 0, true),
                "reading prefers waking hours");
        check(ContractHomeActivityResolver.weight(ContractHomeActivity.MEAL, ContractHomeClock.Phase.MORNING, 0, true)
                > ContractHomeActivityResolver.weight(ContractHomeActivity.MEAL, ContractHomeClock.Phase.NIGHT, 0, true),
                "meals prefer waking hours");
        System.out.println("Home Clock/Resolver: phase, timezone, DST, Minecraft, binding/maid seeds and 10000 deterministic choices passed.");
    }
}

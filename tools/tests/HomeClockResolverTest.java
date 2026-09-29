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
        System.out.println("Home Clock/Resolver: phase, timezone, DST, Minecraft, binding/maid seeds and 10000 deterministic choices passed.");
    }
}

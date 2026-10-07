package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.fox.BlackFoxBossCompat;
import com.maidweapon.forge.entity.BlackFoxMinorCutEntity;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

/** Phase-one projectile ownership only; attacks reuse SlashBlade's existing judgement-cut render resources. */
final class BlackFoxMinorCuts {
    private record Flight(BlackFoxMinorCutEntity entity, long expires) { }
    private final BlackFoxCombatant actor;
    private final List<Flight> flights = new ArrayList<>();
    BlackFoxMinorCuts(BlackFoxCombatant actor) { this.actor = actor; }
    void tick() {
        long now = actor.body().level().getGameTime();
        flights.removeIf(flight -> {
            if (now >= flight.expires()) flight.entity().discard();
            return flight.entity().isRemoved();
        });
    }
    void launch(ServerPlayer player) {
        tick();
        if (flights.size() >= 2) return;
        var at = actor.body().position().add(0, 1, 0);
        var entity = BlackFoxBossCompat.spawnMinorCut(actor, at, player.getEyePosition().subtract(at).normalize().scale(.34));
        if (entity != null) flights.add(new Flight(entity, actor.body().level().getGameTime() + 60));
    }
    void clear() { flights.forEach(flight -> flight.entity().discard()); flights.clear(); }
}

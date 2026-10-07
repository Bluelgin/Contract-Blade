package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.BlackFoxPhantomCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Bounded native phantom volleys and committed seal lanes, independent of phase choreography. */
public final class BlackFoxDomainAttacks {
    private record Shot(Entity entity, long expires) { }
    private record Beam(Vec3 from, Vec3 to, long born) { }
    private final BlackFoxCombatant actor;
    private final BlackFoxSwordFormation formation;
    private final List<Shot> shots = new ArrayList<>();
    private final List<Beam> beams = new ArrayList<>();
    public static final int MAX_SHOTS = 60;
    public static final int VOLLEY_INTERVAL = 24;
    private long volleyAt, sealAt, rainAt, rainFallsAt;
    private Vec3 rainCenter;
    private long lastScatterTick = Long.MIN_VALUE;
    BlackFoxDomainAttacks(BlackFoxCombatant actor) { this.actor = actor; formation = new BlackFoxSwordFormation(actor); }
    public BlackFoxSwordFormation formation() { return formation; }
    public boolean held(Entity shot) { return formation.holds(shot); }
    public void cancelFormation() { formation.clear(); prune(); }
    private long now() { return actor.body().level().getGameTime(); }
    public boolean owns(Entity shot) { return shots.stream().anyMatch(entry -> entry.entity() == shot && !shot.isRemoved()); }
    public int activeShots() { return shots.size(); }
    private void prune() {
        shots.removeIf(shot -> { if (now() >= shot.expires()) shot.entity().discard(); return shot.entity().isRemoved(); });
    }
    private void launch(Vec3 at, Vec3 direction, boolean blade, float speed) {
        if (shots.size() >= MAX_SHOTS) return;
        var shot = BlackFoxPhantomCompat.spawn(actor.body(), at, direction, blade, speed);
        shots.add(new Shot(shot, now() + 60));
    }
    public void scatter(Vec3 core,ServerPlayer player) {
        prune();
        if (now() % 10 != 0 || lastScatterTick == now()) return;
        lastScatterTick = now();
        for (int i = 0; i < 4; i++) {
            Vec3 aim=player.getEyePosition().subtract(core).normalize();
            if(aim.lengthSqr()<.01) aim=new Vec3(0,-1,0);
            // Alternate aimed volleys with broad, downward random scatter, keeping the same entity cap/rate.
            Vec3 direction;
            if ((now() / 10 & 1) == 0)
                direction = aim.yRot((float)((actor.body().getRandom().nextDouble()-.5)*.18)).normalize();
            else {
                double angle = actor.body().getRandom().nextDouble() * Math.PI * 2;
                direction = new Vec3(Math.cos(angle), -.25 - actor.body().getRandom().nextDouble() * .6, Math.sin(angle)).normalize();
            }
            launch(core.add(direction.scale(1.4)), direction, (i & 1) != 0, (i&1)==0?1.35f:1.1f);
        }
    }
    public void wheel(ServerPlayer player) {
        prune(); formation.beginWheel(player, MAX_SHOTS - shots.size(), shot -> shots.add(new Shot(shot, now() + 120)));
    }
    public void rain(ServerPlayer player) {
        rainFallsAt = now() + 28;
        rainCenter = new Vec3(net.minecraft.util.Mth.clamp(player.getX(), actor.arenaOrigin().getX() - 18, actor.arenaOrigin().getX() + 18),
                actor.arenaOrigin().getY() + 1, net.minecraft.util.Mth.clamp(player.getZ(), actor.arenaOrigin().getZ() - 18, actor.arenaOrigin().getZ() + 18));
    }
    public void seal(ServerPlayer player) {
        Vec3 center = Vec3.atBottomCenterOf(actor.arenaOrigin()).add(0, 1.1, 0);
        Vec3 direction = BlackFoxActions.horizontal(player.position().subtract(center)).normalize();
        if (direction.lengthSqr() < .01) direction = new Vec3(1, 0, 0);
        for (int i = 0; i < 2; i++) {
            Vec3 axis = direction.yRot((float) (i * Math.PI / 2));
            double length = 23 / Math.max(Math.abs(axis.x), Math.abs(axis.z));
            beams.add(new Beam(center.subtract(axis.scale(length)), center.add(axis.scale(length)), now()));
        }
    }
    public void volley(ServerPlayer player) {
        prune();
        formation.begin(player, MAX_SHOTS - shots.size(), shot -> shots.add(new Shot(shot, now() + 84)));
    }
    public void arcVolley(ServerPlayer player) {
        prune();
        formation.begin(player, MAX_SHOTS - shots.size(), shot -> shots.add(new Shot(shot, now() + 84)), true);
    }
    public void crossCut(Vec3 at, Vec3 direction) { prune(); launch(at, direction.normalize(), true, 1.1f); }
    public void domainCut(Vec3 at, Vec3 direction) {
        prune();
        if (shots.size() >= MAX_SHOTS) return;
        var shot = BlackFoxPhantomCompat.spawn(actor.body(), at, direction.normalize(), true, 1.15f);
        BlackFoxPhantomCompat.size(shot, 2.2f);
        shots.add(new Shot(shot, now() + 60));
    }
    public void tick(ServerPlayer player, boolean casting) {
        prune();
        if (actor.combat().fight().skill() == BlackFoxFight.Skill.STAGGER
                || actor.combat().fight().skill() == BlackFoxFight.Skill.DIMENSION_STRIKE) cancelFormation();
        formation.tick(player);
        beams.removeIf(beam -> now() - beam.born() >= 84);
        if (casting && now() >= volleyAt) {
            volleyAt = now() + VOLLEY_INTERVAL;
            volley(player);
        }
        if (casting && rainCenter == null && now() >= rainAt) {
            rainAt = now() + 180; rain(player);
        }
        if (rainCenter != null) {
            if (now() < rainFallsAt) {
                if (now() % 4 == 0) for (int i = 0; i < 12; i++) {
                    double angle = Math.PI * 2 * i / 12;
                    Vec3 at = rainCenter.add(Math.cos(angle) * 4, .12, Math.sin(angle) * 4);
                    ((ServerLevel) actor.body().level()).sendParticles(BlackFoxEffects.POLLUTION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                }
            } else {
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                    launch(rainCenter.add(x * 3, 10, z * 3), new Vec3(0, -1, 0), false, .9f);
                rainCenter = null;
            }
        }
        if (casting && now() >= sealAt) {
            sealAt = now() + 160; seal(player);
        }
        var level = (ServerLevel) actor.body().level();
        for (var beam : beams) {
            long age = now() - beam.born();
            if (age % 4 == 0) BlackFoxEffects.line(level, beam.from(), beam.to(), age < 24);
            if (age >= 24 && age % 10 == 0 && Math.abs(player.getY() - beam.from().y) < 2.4
                    && distance(player.position(), beam) < .75 && !actor.combat().protects(player))
                player.hurt(actor.body().damageSources().mobAttack(actor.body()), 8);
        }
    }
    private static double distance(Vec3 point, Beam beam) {
        Vec3 span = BlackFoxActions.horizontal(beam.to().subtract(beam.from()));
        Vec3 offset = BlackFoxActions.horizontal(point.subtract(beam.from()));
        double fraction = net.minecraft.util.Mth.clamp(offset.dot(span) / span.lengthSqr(), 0, 1);
        return offset.subtract(span.scale(fraction)).length();
    }
    public void clear() { formation.clear(); shots.forEach(shot -> shot.entity().discard()); shots.clear(); beams.clear(); rainCenter = null; }
}

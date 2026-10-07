package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Committed, warned relocation. Never moves the body continuously or teleports during a cut. */
final class BlackFoxBlink {
    private static final int WINDUP = 8, RECOVERY = 8, COOLDOWN = 40;
    private final BlackFoxActions actions;
    private Vec3 destination;
    private BlackFoxFight.Skill owner;
    private long arriveAt, readyAt, nextAt;
    BlackFoxBlink(BlackFoxActions actions) { this.actions = actions; }
    boolean busy() { return destination != null || now() < readyAt; }
    void request(ServerPlayer player, boolean sideways) {
        if (busy() || now() < nextAt) return;
        Vec3 front = BlackFoxActions.horizontal(player.getLookAngle()).normalize();
        if (front.lengthSqr() < .01) {
            double yaw=Math.toRadians(player.getYRot());
            front=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        }
        double side=actions.boss().getRandom().nextBoolean() ? 1 : -1;
        // Stay in the forward half-plane. Do not fall back to a blind-side/back landing at arena edges.
        for (double angle : sideways ? new double[]{side*35,-side*35,0,side*55,-side*55}
                : new double[]{0,side*30,-side*30,side*55,-side*55}) {
            Vec3 candidate = actions.bounded(player.position().add(front.yRot((float) Math.toRadians(angle)).scale(BlackFoxSpacing.TARGET)));
            double distance = BlackFoxActions.horizontal(candidate.subtract(player.position())).length();
            if (distance < BlackFoxSpacing.MIN || distance > BlackFoxSpacing.MAX
                    || front.dot(BlackFoxActions.horizontal(candidate.subtract(player.position())).normalize())<.5
                    || !actions.boss().level().noCollision(actions.boss(),
                    actions.boss().getBoundingBox().move(candidate.subtract(actions.boss().position())))) continue;
            destination = candidate; owner = actions.fight.skill(); arriveAt = now() + WINDUP;
            BlackFoxEffects.blink(actions.boss(), destination, false);
            return;
        }
        nextAt=now()+10; // Bounded retry when every forward landing is blocked.
    }
    void tick(ServerPlayer player) {
        if (owner != null && owner != actions.fight.skill()) { cancel(); return; }
        if (destination == null || now() < arriveAt) return;
        // Cancel if the player stepped into the committed landing point; never surprise them with body contact.
        double distance=BlackFoxActions.horizontal(destination.subtract(player.position())).length();
        if (distance >= BlackFoxSpacing.CLOSE && distance <= BlackFoxSpacing.FAR
                && actions.boss().level().noCollision(actions.boss(), actions.boss().getBoundingBox()
                        .move(destination.subtract(actions.boss().position())))) {
            BlackFoxEffects.blink(actions.boss(), destination, true);
            actions.boss().teleportTo(destination.x, destination.y, destination.z);
            actions.boss().setDeltaMovement(Vec3.ZERO);
            actions.face(player.position());
        }
        destination = null; readyAt = now() + RECOVERY; nextAt = now() + COOLDOWN;
    }
    void cancel() { destination = null; owner = null; readyAt = 0; }
    void holdUntil(long until) { cancel(); nextAt=Math.max(nextAt,until); }
    private long now() { return actions.boss().level().getGameTime(); }
}

package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Two fixed side portals: commits the player's position at warning time, never homes afterwards. */
public final class BlackFoxDomainCross {
    public static final int FIRST = 32, SECOND = 48;
    private final BlackFoxActions actions;
    private Vec3 left = Vec3.ZERO, right = Vec3.ZERO, target = Vec3.ZERO;
    BlackFoxDomainCross(BlackFoxActions actions) { this.actions = actions; }
    public Vec3 left() { return left; }
    public Vec3 right() { return right; }
    public Vec3 target() { return target; }
    void tick(ServerPlayer player) {
        int age = actions.fight.age();
        if (age == 1) {
            target = actions.bounded(player.position()).add(0, 1, 0);
            Vec3 toward = BlackFoxActions.horizontal(target.subtract(actions.boss().position())).normalize();
            if (toward.lengthSqr() < .01) toward = new Vec3(0, 0, 1);
            Vec3 side = toward.yRot((float) (Math.PI / 2));
            left = actions.bounded(target.add(side.scale(7)).subtract(toward.scale(3))).add(0, 1, 0);
            right = actions.bounded(target.subtract(side.scale(7)).subtract(toward.scale(3))).add(0, 1, 0);
            BlackFoxEffects.tell(actions.boss(), false);
        }
        if (age == FIRST - 8 || age == SECOND - 8) BlackFoxEffects.tell(actions.boss(), true);
        if (age == FIRST) actions.ranged().domainCut(left, target.subtract(left));
        if (age == SECOND) actions.ranged().domainCut(right, target.subtract(right));
    }
}

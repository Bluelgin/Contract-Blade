package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Two committed native drive cuts; portal positions and aim stop following before firing. */
public final class BlackFoxRiftCross {
    private final BlackFoxActions actions;
    private Vec3 left = Vec3.ZERO, right = Vec3.ZERO, target = Vec3.ZERO;
    BlackFoxRiftCross(BlackFoxActions actions) { this.actions = actions; }
    public Vec3 left() { return left; }
    public Vec3 right() { return right; }
    void tick(ServerPlayer player) {
        int age = actions.fight.age();
        if (age == 1) {
            actions.commit(player);
            Vec3 side = actions.committed.yRot((float) (Math.PI / 2));
            Vec3 center = player.position().subtract(actions.committed.scale(5));
            left = actions.bounded(center.add(side.scale(4))).add(0,1.1,0);
            right = actions.bounded(center.subtract(side.scale(4))).add(0,1.1,0);
            BlackFoxEffects.tell(actions.boss(), false);
        }
        if (age == 16) { target = player.position().add(0,1,0); BlackFoxEffects.tell(actions.boss(), true); }
        if (age == 24) actions.ranged().crossCut(left, target.subtract(left));
        if (age == 40) actions.ranged().crossCut(right, target.subtract(right));
    }
}

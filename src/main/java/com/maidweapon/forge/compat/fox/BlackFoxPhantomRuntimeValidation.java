package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.compat.BlackFoxPhantomCompat;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;

/** Explicit opt-in smoke test for both userdev and the reobfuscated, packaged client. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class BlackFoxPhantomRuntimeValidation {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.blackFoxPhantom.validation")) return;
        var entities = new ArrayList<Entity>();
        try {
            var level = event.getServer().overworld();
            level.getChunk(0, 0);
            var owner = EntityType.ZOMBIE.create(level);
            var target = EntityType.ZOMBIE.create(level);
            if (owner == null || target == null) throw new IllegalStateException("Missing test entities");
            entities.add(owner); entities.add(target);
            owner.setNoAi(true); target.setNoAi(true);
            owner.setPos(0.5, 150, 0.5); target.setPos(4.5, 150, 0.5);
            level.addFreshEntity(owner); level.addFreshEntity(target);
            for (boolean drive : new boolean[]{false, true}) {
                var sword = BlackFoxPhantomCompat.spawn(owner, owner.position().add(0, 2, 0),
                        new Vec3(1, 0, 0), drive, 0);
                entities.add(sword);
                check(sword instanceof Projectile && sword.getDeltaMovement().lengthSqr() == 0,
                        "native projectile holds without movement");
                BlackFoxPhantomCompat.fire(sword, new Vec3(1, 0, 0), 1.2f);
                check(sword.getDeltaMovement().lengthSqr() > 0, "held projectile releases through mapped virtual call");
                BlackFoxPhantomCompat.fire(sword, new Vec3(0, 0, 1), 0);
                check(sword.getDeltaMovement().lengthSqr() == 0, "released projectile can be held again");
                if (drive) BlackFoxPhantomCompat.size(sword, 1.4f);
                BlackFoxPhantomCompat.impact(sword, target);
            }
            var moving = BlackFoxPhantomCompat.spawn(owner, owner.position().add(0, 2, 0), new Vec3(0, 0, 1));
            entities.add(moving);
            check(moving.getDeltaMovement().lengthSqr() > 0, "default phantom spawn launches immediately");
            LogUtils.getLogger().info("BLACK_FOX_PHANTOM_RUNTIME_PASS");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("BLACK_FOX_PHANTOM_RUNTIME_FAIL", failure);
        } finally {
            entities.forEach(Entity::discard);
            event.getServer().halt(false);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    private BlackFoxPhantomRuntimeValidation() { }
}

package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.compat.*;
import com.maidweapon.forge.entity.*;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opt-in packaged-runtime fixture. Only launch against a disposable copied world. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class BlackFoxSwordCoreValidation {
    @SubscribeEvent public static void started(net.minecraftforge.event.server.ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.swordCore.validation")) return;
        var spawned = new ArrayList<Entity>();
        try {
            var level = event.getServer().overworld(); level.getChunk(0, 0);
            var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SwordCoreFixture"));
            player.setPos(.5, 150, .5);
            var blade = BlackFoxSlashCompat.blade(level);
            blade.getOrCreateTag().putUUID(FoxSpiritState.ORIGIN, UUID.randomUUID());
            player.setItemSlot(EquipmentSlot.MAINHAND, blade);
            var core = BlackFoxBossCompat.spawnSwordCore(player); check(core != null, "Core registered"); spawned.add(core);
            var enemy = EntityType.ZOMBIE.create(level); check(enemy != null, "Enemy fixture");
            enemy.setNoAi(true); enemy.setNoGravity(true); enemy.setPos(1.5, 150, 1.5);
            enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000); enemy.setHealth(10000);
            level.addFreshEntity(enemy); spawned.add(enemy);
            var friend = EntityType.COW.create(level); friend.setNoAi(true); friend.setNoGravity(true); friend.setPos(1.5, 150, 1.5);
            level.addFreshEntity(friend); spawned.add(friend);
            var emitter = BlackFoxBossCompat.spawnSwordActor(core, player, null); spawned.add(emitter);
            for (boolean drive : new boolean[]{false, true}) {
                var shot = BlackFoxPhantomCompat.spawn(emitter, core.position(), new net.minecraft.world.phys.Vec3(0, -1, 0), drive, 1);
                spawned.add(shot); float friendlyHealth = friend.getHealth();
                BlackFoxPhantomCompat.impact(shot, friend); check(friend.getHealth() == friendlyHealth, "Native phantom excludes friend");
                float before = enemy.getHealth(); enemy.invulnerableTime = 0;
                BlackFoxPhantomCompat.impact(shot, enemy); check(enemy.getHealth() < before, "Native phantom damage executes");
                shot.discard();
            }
            var actor = BlackFoxBossCompat.spawnSwordActor(core, player, enemy); spawned.add(actor);
            var stages = new HashSet<String>(); float initial = enemy.getHealth(); float friendlyHealth = friend.getHealth();
            long initialTime = level.getGameTime();
            for (int age = 0; age < 180; age++) {
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(initialTime + age); enemy.invulnerableTime = 0;
                actor.tick();
                if (actor.nativeStage() != null) stages.add(actor.nativeStage().toString());
                for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                        core.getBoundingBox().inflate(24))) if (entity.getOwner() == actor && !entity.isRemoved()) {
                    if (!spawned.contains(entity)) spawned.add(entity); entity.tick();
                }
            }
            for (int i = 1; i <= 7; i++) check(stages.contains("slashblade:combo_b" + i), "Native B stage " + i);
            check(enemy.getHealth() < initial, "Native B actually damages enemy");
            check(friend.getHealth() == friendlyHealth, "Native B excludes friend");
            core.follow(null); check(core.following(), "Switch to native B");
            core.follow(null); check(!core.following(), "Switch back to native barrage");
            core.follow(null); check(core.following(), "Switch again");
            LogUtils.getLogger().info("SWORD_CORE_NATIVE_PASS: B1-B7={}, enemy damage={}, bidirectional switches, friendly protection", stages, initial - enemy.getHealth());
        } catch (Throwable failure) { LogUtils.getLogger().error("SWORD_CORE_NATIVE_FAIL", failure); }
        finally { spawned.forEach(Entity::discard); event.getServer().halt(false); }
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}

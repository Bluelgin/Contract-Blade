package com.maidweapon.forge.compat.fox;

import com.maidweapon.common.BlackFoxServerConfig;
import com.maidweapon.forge.system.fox.challenge.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/** Real event bus and HP checks in the opt-in isolated guard fixture. Restores changed config values. */
final class BlackFoxDifficultyValidation {
    private static int actualNativeHits;
    private static boolean observing;
    static void run(BlackFoxCombatant boss, ServerPlayer player) throws Exception {
        if (!observing) {
            observing = true;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                    net.minecraftforge.eventbus.api.EventPriority.LOWEST, false, LivingHurtEvent.class, event -> {
                        if (event.getEntity() instanceof ServerPlayer target
                                && event.getSource().getEntity() instanceof BlackFoxCombatant owner
                                && BlackFoxDamageTuning.nativeComboHit(owner,target)) actualNativeHits++;
                    });
        }
        double oldMultiplier=BlackFoxServerConfig.DAMAGE_MULTIPLIER.get();
        double oldHealth=BlackFoxServerConfig.MAX_HEALTH.get();
        try {
            var source=boss.body().damageSources().mobAttack(boss.body());
            BlackFoxServerConfig.DAMAGE_MULTIPLIER.set(.5);
            var direct=new LivingHurtEvent(player,source,10);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(direct);
            check(direct.getAmount()==5,"global multiplier applies once");
            try(var nativeHit=BlackFoxDamageTuning.nativeHit(boss.body(),player)) {
                var nativeEvent=new LivingHurtEvent(player,source,10);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(nativeEvent);
                check(Math.abs(nativeEvent.getAmount()-3.25)<.001,"native ratio combines with global multiplier once");
            }
            check(!BlackFoxDamageTuning.nativeComboHit(boss,player),"native scope clears after hit");
            try(var nativeHit=BlackFoxDamageTuning.nativeHit(player,boss.body())) {
                check(!BlackFoxDamageTuning.nativeComboHit(boss,player),"player attacks and reward SA cannot be treated as Boss B");
            }
            var unrelated=new LivingHurtEvent(player,player.damageSources().generic(),10);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(unrelated);
            check(unrelated.getAmount()==10,"unrelated damage unchanged");
            BlackFoxServerConfig.DAMAGE_MULTIPLIER.set(0.0);
            var disabled=new LivingHurtEvent(player,source,10);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(disabled);
            check(disabled.isCanceled(),"zero multiplier disables encounter damage");
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            BlackFoxGuardValidation.input(player,30);
            BlackFoxServerConfig.DAMAGE_MULTIPLIER.set(1.0);
            player.setHealth(1000); player.invulnerableTime=0;
            player.hurt(source,10); float full=1000-player.getHealth();
            BlackFoxServerConfig.DAMAGE_MULTIPLIER.set(.5);
            player.setHealth(1000); player.invulnerableTime=0;
            player.hurt(source,10); float half=1000-player.getHealth();
            check(full>0 && Math.abs(half*2-full)<.001,"actual HP loss respects global multiplier");
            BlackFoxServerConfig.MAX_HEALTH.set(800.0);
            var fresh=new com.maidweapon.forge.entity.BlackFoxBossEntity(BlackFoxBossCompat.type(),player.level());
            fresh.initialize(player,boss.arenaOrigin());
            check(fresh.getMaxHealth()==800 && fresh.getHealth()==800,"new challenge starts at configured full HP");
            BlackFoxServerConfig.MAX_HEALTH.set(200.0);
            check(fresh.getMaxHealth()==800 && fresh.getHealth()==800,"reload never heals/resets existing Boss");
            fresh.discard();
            com.mojang.logging.LogUtils.getLogger().info("BLACK_FOX_DIFFICULTY_PASS directHP={} halfHP={} native=3.25 maxHP=800 playerSA=unchanged",full,half);
        } finally {
            BlackFoxServerConfig.DAMAGE_MULTIPLIER.set(oldMultiplier);
            BlackFoxServerConfig.MAX_HEALTH.set(oldHealth);
            actualNativeHits=0;
        }
    }
    static void verifyNativeHits() {
        check(actualNativeHits>0,"real native B traversed the scoped difficulty hook");
        com.mojang.logging.LogUtils.getLogger().info("BLACK_FOX_NATIVE_DIFFICULTY_PASS realNativeHits={}",actualNativeHits);
    }
    private static void check(boolean ok,String label) { if(!ok)throw new IllegalStateException(label); }
    private BlackFoxDifficultyValidation() { }
}

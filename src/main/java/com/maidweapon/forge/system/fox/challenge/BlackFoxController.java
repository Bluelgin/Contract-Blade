package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.BlackFoxNativeCombo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Session/phase orchestration. Skill execution, movement and pure timing have separate owners. */
public final class BlackFoxController {
    private final BlackFoxCombatant actor;
    private final BlackFoxFight fight = new BlackFoxFight();
    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.maid_weapon.black_fox_boss"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private final BlackFoxActions actions;
    private final BlackFoxPhaseOne phaseOne;
    private final BlackFoxDefense defense;
    private final BlackFoxNativeCombo nativeCombo;
    private final BlackFoxDomainAttacks ranged;
    private final BlackFoxDimensionStrike dimensionStrike;
    private final BlackFoxCorruption corruption;
    private final BlackFoxBalance balance=new BlackFoxBalance();
    private long blockedFeedback = -100;
    private boolean engaged;
    private int displayedMarks = -1, displayedStyle = -1;
    public static final int PARRY_PROTECTION_TICKS = 24;
    private long parryProtectedUntil = Long.MIN_VALUE;
    public BlackFoxController(BlackFoxCombatant actor) {
        this.actor = actor;
        nativeCombo = new BlackFoxNativeCombo(actor);
        ranged = new BlackFoxDomainAttacks(actor);
        actions = new BlackFoxActions(actor, fight, nativeCombo, ranged);
        dimensionStrike = new BlackFoxDimensionStrike(actions);
        phaseOne = new BlackFoxPhaseOne(actions, actor, dimensionStrike);
        defense = new BlackFoxDefense(actions, phaseOne);
        corruption = new BlackFoxCorruption(actor, fight, actions, ranged);
    }
    public BlackFoxNativeCombo nativeCombo() { return nativeCombo; }
    public void damageAccepted() {
        defense.accepted(); fight.wakeFaster();
        if(boss().getHealth()<=boss().getMaxHealth()*.10f) lastStand();
    }
    public BlackFoxDomainAttacks ranged() { return ranged; }
    public BlackFoxDimensionStrike dimensionStrike() { return dimensionStrike; }
    public BlackFoxRiftCross riftCross() { return phaseOne.riftCross(); }
    void feedbackParry(ServerPlayer player, boolean comboClash) {
        if(!corruption.finalStand() && !fight.offBalance()) balance.parried(boss().level().getGameTime());
        com.maidweapon.forge.compat.BlackFoxRankCompat.reward(player, comboClash);
        com.maidweapon.forge.network.BlackFoxFeedbackNetwork.contact(actor, player, comboClash ? 2 : 1);
        if (!fight.phaseTwo()) phaseOne.parried(boss().level().getGameTime(), comboClash);
        else corruption.parried();
        updateBar(player, false);
    }
    public BlackFoxFight fight() { return fight; }
    public void showBar(ServerPlayer player) { bar.addPlayer(player); updateBar(player, true); }
    private void updateBar(ServerPlayer player, boolean force) {
        int style = fight.offBalance() ? 2 : fight.phaseTwo() ? 1 : 0;
        int marks = fight.offBalance() ? BlackFoxBalance.REQUIRED : balance.progress();
        if (!force && marks == displayedMarks && style == displayedStyle) return;
        displayedMarks = marks; displayedStyle = style;
        com.maidweapon.forge.network.BlackFoxFeedbackNetwork.bar(actor, player, bar.getId(), marks, style, true);
    }
    public void close() {
        com.maidweapon.forge.network.BlackFoxFeedbackNetwork.bar(actor, player(), bar.getId(), 0, 0, false);
        com.maidweapon.forge.network.BlackFoxFeedbackNetwork.music(actor, player(), false);
        phaseOne.close(); corruption.close(); nativeCombo.stop(); parryProtectedUntil = Long.MIN_VALUE; bar.removeAllPlayers();
    }
    public BlackFoxCorruption corruption() { return corruption; }
    public float damageTaken(float amount) {
        return fight.phaseTwo() && fight.skill() != BlackFoxFight.Skill.STAGGER ? amount * .7f : amount;
    }
    public boolean protects(ServerPlayer player) {
        return actor.challenger().equals(player.getUUID()) && boss().level() == player.level()
                && BlackFoxEncounters.registered(actor) && parryGuarded();
    }
    private boolean parryGuarded() { return boss().level().getGameTime() < parryProtectedUntil; }
    void protectParry() {
        parryProtectedUntil = boss().level().getGameTime() + PARRY_PROTECTION_TICKS;
    }
    private Mob boss() { return actor.body(); }
    private ServerPlayer player() { return boss().getServer().getPlayerList().getPlayer(actor.challenger()); }
    public void tick() {
        ServerPlayer player = player();
        if (!BlackFoxEncounters.registered(actor) || player == null || !player.isAlive()
                || player.level() != boss().level() || !FoxChallengeArena.contains(actor.arenaOrigin(), player.getX(), player.getY(), player.getZ())) {
            BlackFoxEncounters.retire(actor); return;
        }
        if (fight.skill() == BlackFoxFight.Skill.DEFEATED) {
            boss().setDeltaMovement(Vec3.ZERO); fight.advance(); synchronize();
            if (fight.finished()) BlackFoxEncounters.stop(player);
            return;
        }
        if (!engaged) {
            actions.face(player.position());
            if (boss().distanceTo(player) > 22) { synchronize(); return; }
            engaged = true;
            com.maidweapon.forge.network.BlackFoxFeedbackNetwork.music(actor, player, true);
            player.sendSystemMessage(Component.translatable("maid_weapon.fox.boss.warning"));
            BlackFoxEffects.tell(boss(), false);
        }
        if (fight.updatePhase(boss().getHealth(), boss().getMaxHealth())) {
            phaseOne.close();
            nativeCombo.stop(); corruption.begin();
            BlackFoxEffects.tell(boss(), false);
        }
        bar.setProgress(Math.max(0, boss().getHealth() / boss().getMaxHealth()));
        updateBar(player, false);
        fight.advance();
        if (fight.skill() != BlackFoxFight.Skill.RETURN_BLADE) nativeCombo.stop();
        if (tryClash(player)) { actions.clamp(); return; }
        if (fight.phaseTwo()) corruption.tick(player);
        else phaseOne.tick(player);
        if(balance.take() && !corruption.finalStand()) {
            phaseOne.close(); ranged.clear(); nativeCombo.stop(); fight.loseBalance();
            boss().setDeltaMovement(Vec3.ZERO); BlackFoxEffects.breakMomentum(boss());
        }
        if (!fight.phaseTwo() && fight.finished() && fight.skill() != BlackFoxFight.Skill.APPROACH
                && fight.skill() != BlackFoxFight.Skill.DEFEATED) {
            var completed = fight.skill(); long sequence = fight.sequence();
            boolean failed = fight.complete();
            nativeCombo.stop();
            phaseOne.completed(completed, sequence, player);
            if (failed) BlackFoxEffects.tell(boss(), false);
        }
        synchronize();
        actions.clamp();
    }
    public boolean tryClash(ServerPlayer attacker) {
        if (!engaged || !actor.challenger().equals(attacker.getUUID()) || attacker.level() != boss().level()
                || !fight.clashWindow() || !BlackFoxEncounters.freshComboB(attacker)
                || boss().distanceToSqr(attacker) > 4.8 * 4.8 || !actions.front(attacker) || !actions.facingBoss(attacker)) return false;
        BlackFoxEncounters.consumeComboB(attacker);
        BlackFoxEncounters.consumeParry(attacker);
        protectParry();
        feedbackParry(attacker, true);
        fight.counter();
        nativeCombo.stop();
        boss().setDeltaMovement(Vec3.ZERO);
        BlackFoxEffects.contact(boss(), attacker, true);
        attacker.displayClientMessage(Component.translatable("maid_weapon.fox.boss.clash"), true);
        synchronize(); return true;
    }
    public boolean parryNative(ServerPlayer player) {
        return actions.parry(player);
    }
    public boolean parryNative(ServerPlayer player, net.minecraft.world.entity.Entity incoming) {
        if (fight.skill() != BlackFoxFight.Skill.DOMAIN_CROSS) return parryNative(player);
        Vec3 direction = BlackFoxActions.horizontal(incoming.position().subtract(player.position())).normalize();
        if (direction.lengthSqr() < .01) direction = BlackFoxActions.horizontal(incoming.getDeltaMovement().scale(-1)).normalize();
        return BlackFoxActions.horizontal(player.getLookAngle()).normalize().dot(direction) >= .15 && actions.parryArena(player);
    }
    public boolean intercept(DamageSource source) {
        if(corruption.finalStand()) return true;
        if (fight.skill() == BlackFoxFight.Skill.DIMENSION_STRIKE && BlackFoxDimensionTimeline.submerged(fight.age())) return true;
        if (fight.phaseTwo() && !BlackFoxDamagePolicy.bladeMelee(source, boss())) return true;
        if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker
                && tryClash(attacker)) return true;
        // Both sides are protected during the clash itself, including already-emitted player slashes.
        if (parryGuarded() && !fight.offBalance()) return true;
        if (fight.skill() == BlackFoxFight.Skill.TRANSITION) return true;
        if (fight.skill() == BlackFoxFight.Skill.RETURN_BLADE && fight.age() < fight.rushTimeout()
                && source.getEntity() instanceof ServerPlayer attacker
                && source.getDirectEntity() == attacker && boss().distanceToSqr(attacker) <= 4.8 * 4.8
                && actions.parry(attacker)) return true;
        if (defense.protectedNow()) return true;
        if (corruption.retaliate(source)) return true;
        if (defense.counter(source)) return true;
        if (!fight.guard() || source.getEntity() == null || !actions.front(source.getEntity().position())) return false;
        if (boss().level().getGameTime() - blockedFeedback >= 4) {
            blockedFeedback = boss().level().getGameTime();
            if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)
                BlackFoxEffects.contact(boss(), attacker, false);
        }
        return true;
    }
    public void landedHit(float damage) {
        if (!fight.phaseTwo() && phaseOne.opportunisticHit()) return;
        if (!fight.phaseTwo()) phaseOne.pressured(boss().level().getGameTime());
        var before = fight.skill(); fight.hit(damage);
        if (before != fight.skill()) { BlackFoxEffects.tell(boss(), true); synchronize(); }
    }
    public void defeat() {
        if(fight.skill()==BlackFoxFight.Skill.DEFEATED) return;
        lastStand();
    }
    private void lastStand() {
        if(corruption.finalStand() || fight.skill()==BlackFoxFight.Skill.DEFEATED) return;
        balance.clear(); phaseOne.close(); nativeCombo.stop();
        if(!fight.phaseTwo()) { fight.updatePhase(0,boss().getMaxHealth()); corruption.begin(); }
        boss().setHealth(Math.max(1,boss().getHealth()));
        var target=player();
        if(target!=null) corruption.startUltimate(target);
    }
    void finishDefeat() {
        if (fight.skill() == BlackFoxFight.Skill.DEFEATED) return;
        fight.start(BlackFoxFight.Skill.DEFEATED);
        phaseOne.close();
        corruption.close();
        nativeCombo.stop();
        boss().setHealth(1); boss().setDeltaMovement(Vec3.ZERO); bar.setProgress(0);
        var player = player();
        if (player != null) {
            com.maidweapon.forge.network.BlackFoxFeedbackNetwork.music(actor, player, false);
            BlackFoxEncounters.recordWin(player);
            boolean rescued = BlackFoxShrineReturn.get(player.getServer()).rescue(player);
            player.sendSystemMessage(Component.translatable(rescued ? "maid_weapon.fox.black.returning"
                    : "maid_weapon.fox.boss.defeated"));
        }
        BlackFoxEffects.tell(boss(), true); synchronize();
    }
    private void synchronize() {
        var motion = fight.skill() == BlackFoxFight.Skill.RETURN_BLADE ? nativeCombo.motion()
                : fight.skill() == BlackFoxFight.Skill.APPROACH && engaged
                && boss().getDeltaMovement().horizontalDistance() > .05 ? BlackFoxFight.Motion.WALK : fight.motion();
        actor.sync(motion, fight.phaseTwo());
    }

}


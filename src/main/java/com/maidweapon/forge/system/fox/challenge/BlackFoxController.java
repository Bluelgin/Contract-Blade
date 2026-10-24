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
    private final BlackFoxParryCredit parryCredit = new BlackFoxParryCredit();
    private final BlackFoxSealArray seals;
    public BlackFoxSealArray seals() { return seals; }
    private long blockedFeedback = -100;
    private boolean engaged;
    private int displayedMarks = -1, displayedStyle = -1;
    private int displayedSeals = -1;
    public static final int PARRY_PROTECTION_TICKS = 8;
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
        seals = new BlackFoxSealArray(actor);
    }
    public BlackFoxNativeCombo nativeCombo() { return nativeCombo; }
    public void damageAccepted() {
        fight.wakeFaster();
    }
    public BlackFoxDomainAttacks ranged() { return ranged; }
    public BlackFoxDimensionStrike dimensionStrike() { return dimensionStrike; }
    public BlackFoxRiftCross riftCross() { return phaseOne.riftCross(); }
    void feedbackParry(ServerPlayer player, net.minecraft.world.entity.Entity incoming) {
        boolean credit = !corruption.finalStand() && !fight.offBalance()
                && (incoming == null || incoming == boss() ? parryCredit.accept(fight.skill(), fight.sequence(), fight.parryRound())
                : BlackFoxAttackOrigin.credit(incoming, parryCredit));
        if (credit) balance.parried(boss().level().getGameTime());
        // The real native Guard already awards rank, animation and its own invulnerability.
        com.maidweapon.forge.network.BlackFoxFeedbackNetwork.contact(actor, player, 1);
        if (!fight.phaseTwo()) phaseOne.parried(boss().level().getGameTime());
        else corruption.parried();
        updateBar(player, false);
    }
    public BlackFoxFight fight() { return fight; }
    /** Presentation only, restricted to the nearby challenger. */
    public void impact(int strength) {
        var target = player();
        if (target != null && target.level() == boss().level() && target.distanceToSqr(boss()) <= 14 * 14)
            com.maidweapon.forge.network.BlackFoxFeedbackNetwork.contact(actor, target, strength);
    }
    public void showBar(ServerPlayer player) { bar.addPlayer(player); updateBar(player, true); }
    private void updateBar(ServerPlayer player, boolean force) {
        int style = fight.offBalance() ? 2 : fight.phaseTwo() ? 1 : 0;
        int marks = fight.offBalance() ? BlackFoxBalance.REQUIRED : balance.progress();
        int remaining = seals.remaining();
        if (!force && marks == displayedMarks && style == displayedStyle && remaining == displayedSeals) return;
        displayedSeals = remaining;
        displayedMarks = marks; displayedStyle = style;
        com.maidweapon.forge.network.BlackFoxFeedbackNetwork.bar(actor, player, bar.getId(), marks, style, true, remaining);
    }
    public void close() {
        com.maidweapon.forge.network.BlackFoxFeedbackNetwork.bar(actor, player(), bar.getId(), 0, 0, false);
        seals.close(); phaseOne.close(); corruption.close(); nativeCombo.stop(); parryProtectedUntil = Long.MIN_VALUE; bar.removeAllPlayers();
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
            player.sendSystemMessage(Component.translatable("maid_weapon.fox.boss.warning"));
            BlackFoxEffects.tell(boss(), false);
        }
        fight.updatePhase(boss().getHealth(), boss().getMaxHealth());
        bar.setProgress(corruption.finalStand() ? 0 : Math.max(0, boss().getHealth() / boss().getMaxHealth()));
        updateBar(player, false);
        fight.advance();
        if (!corruption.finalStand() && seals.begin(player)) { phaseOne.close(); ranged.clear(); nativeCombo.stop(); }
        seals.tick(player);
        if (!fight.nativeComboSkill()) nativeCombo.stop();
        if (corruption.finalStand()) corruption.tick(player);
        else phaseOne.tick(player);
        if(fight.skill() != BlackFoxFight.Skill.SEAL_ARRAY && balance.take() && !corruption.finalStand()) {
            phaseOne.close(); ranged.clear(); nativeCombo.stop(); fight.loseBalance();
            boss().setDeltaMovement(Vec3.ZERO); BlackFoxEffects.breakMomentum(boss());
            com.maidweapon.forge.network.BlackFoxFeedbackNetwork.contact(actor, player, 3); updateBar(player, true);
            player.displayClientMessage(Component.translatable("maid_weapon.fox.boss.balance_break"), true);
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
    public boolean parryNative(ServerPlayer player) {
        return actions.parry(player);
    }
    public boolean parryNative(ServerPlayer player, net.minecraft.world.entity.Entity incoming) {
        return actions.parryProjectile(player, incoming);
    }
    public boolean intercept(DamageSource source) {
        if(corruption.finalStand()) return true;
        if (seals.active()) {
            if (boss().level().getGameTime() - blockedFeedback >= 8 && source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
                blockedFeedback = boss().level().getGameTime(); BlackFoxEffects.contact(boss(), attacker, true);
            }
            return true;
        }
        if (fight.skill() == BlackFoxFight.Skill.DIMENSION_STRIKE && BlackFoxDimensionTimeline.submerged(fight.age())) return true;
        if (fight.phaseTwo() && !BlackFoxDamagePolicy.bladeMelee(source, boss())) return true;
        // Both sides are protected during the clash itself, including already-emitted player slashes.
        if (parryGuarded() && !fight.offBalance()) return true;
        if (fight.skill() == BlackFoxFight.Skill.TRANSITION) return true;
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
        if (!fight.phaseTwo()) phaseOne.pressured(boss().level().getGameTime());
        var before = fight.skill(); fight.hit(damage);
        if (before != fight.skill()) { BlackFoxEffects.tell(boss(), true); synchronize(); }
    }
    public void defeat() {
        if(fight.skill()==BlackFoxFight.Skill.DEFEATED) return;
        // The entity redirects lethal death here; low health alone never starts the final core.
        lastStand();
    }
    private void lastStand() {
        if(corruption.finalStand() || fight.skill()==BlackFoxFight.Skill.DEFEATED) return;
        seals.close(); balance.clear(); phaseOne.close(); nativeCombo.stop();
        if(!fight.phaseTwo()) { fight.beginFinalStand(); corruption.begin(); }
        // Keep the body alive internally while presenting its combat health as exhausted.
        boss().setHealth(1); bar.setProgress(0);
        var target=player();
        if(target!=null) corruption.startUltimate(target);
    }
    void finishDefeat() {
        if (fight.skill() == BlackFoxFight.Skill.DEFEATED) return;
        fight.start(BlackFoxFight.Skill.DEFEATED);
        seals.close();
        phaseOne.close();
        corruption.close();
        nativeCombo.stop();
        boss().setHealth(1); boss().setDeltaMovement(Vec3.ZERO); bar.setProgress(0);
        var player = player();
        if (player != null) {
            BlackFoxEncounters.recordWin(player);
            boolean rescued = BlackFoxShrineReturn.get(player.getServer()).rescue(player);
            player.sendSystemMessage(Component.translatable(rescued ? "maid_weapon.fox.black.returning"
                    : "maid_weapon.fox.boss.defeated"));
            FoxChallengeCollapse.begin(player);
        }
        BlackFoxEffects.tell(boss(), true); synchronize();
    }
    private void synchronize() {
        var motion = fight.nativeComboSkill() ? nativeCombo.motion()
                : fight.skill() == BlackFoxFight.Skill.APPROACH && engaged
                && boss().getDeltaMovement().horizontalDistance() > .05 ? BlackFoxFight.Motion.WALK : fight.motion();
        actor.sync(motion, fight.phaseTwo());
    }

}


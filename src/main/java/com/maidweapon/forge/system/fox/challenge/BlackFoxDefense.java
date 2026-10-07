package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.BlackFoxSlashCompat;
import com.maidweapon.forge.compat.SlashBladeCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/** Boss-local hit cadence and readable, cooldown-limited counter. No vanilla i-frame resets. */
final class BlackFoxDefense {
    static final int HIT_INTERVAL = 16, OPENING_INTERVAL = 8, COUNTER_INTERVAL = 60;
    private final BlackFoxActions actions;
    private final BlackFoxPhaseOne phaseOne;
    private long damageAt, counterAt, guardingUntil;
    BlackFoxDefense(BlackFoxActions actions, BlackFoxPhaseOne phaseOne) { this.actions = actions; this.phaseOne = phaseOne; }
    private long now() { return actions.boss().level().getGameTime(); }
    boolean protectedNow() { return now() < damageAt || now() < guardingUntil; }
    void accepted() {
        damageAt = now() + (actions.fight.skill() == BlackFoxFight.Skill.STAGGER
                || BlackFoxOpenings.active(actions.fight.skill(), actions.fight.age()) ? OPENING_INTERVAL : HIT_INTERVAL);
    }
    boolean counter(DamageSource source) {
        var fight = actions.fight;
        boolean ready = fight.skill() == BlackFoxFight.Skill.APPROACH || fight.skill() == BlackFoxFight.Skill.PHANTOM_FEINT
                || fight.skill() == BlackFoxFight.Skill.SIDESTEP_CUT && fight.age() < 12
                || fight.skill() == BlackFoxFight.Skill.PROBE_CUT && fight.age() < 6;
        if (fight.phaseTwo() || !ready || now() < counterAt || !(source.getEntity() instanceof ServerPlayer player)
                || source.is(DamageTypeTags.IS_PROJECTILE) || actions.boss().distanceToSqr(player) > 36
                || !actions.front(player)) return false;
        var projectile = BlackFoxAttackTrace.projectile();
        if (projectile != null ? !BlackFoxSlashCompat.nativeSlash(projectile) : source.getDirectEntity() != player) return false;
        var trace = BlackFoxAttackTrace.current();
        var blade = trace == null ? player.getMainHandItem() : trace.originalBlade();
        if (!SlashBladeCompat.isSlashBlade(blade) && !blade.canPerformAction(net.minecraftforge.common.ToolActions.SWORD_SWEEP)) return false;
        counterAt = now() + COUNTER_INTERVAL; guardingUntil = now() + 8;
        phaseOne.close();
        actions.begin(BlackFoxFight.Skill.COUNTER_CUT, player);
        BlackFoxSlashCompat.slash(actions.boss(), 65);
        BlackFoxEffects.contact(actions.boss(), player, true);
        com.maidweapon.forge.network.BlackFoxFeedbackNetwork.contact((BlackFoxCombatant) actions.boss(), player, 0);
        return true;
    }
}

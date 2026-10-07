package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.BlackFoxJudgementCompat;
import com.maidweapon.forge.compat.fox.BlackFoxBossCompat;
import com.maidweapon.forge.entity.BlackFoxCoreEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/** Phase-two arena choreography. Finite hazards and the breakable ultimate share session ownership. */
public final class BlackFoxCorruption {
    private final BlackFoxCombatant actor;
    private final BlackFoxFight fight;
    private final BlackFoxActions actions;
    private final BlackFoxDomainAttacks ranged;
    private final BlackFoxRifts rifts;
    private final BlackFoxDomainPool pool = new BlackFoxDomainPool();
    private final BlackFoxDomainCross cross;
    private BlackFoxCoreEntity core;
    private long nextGuard, nextAmbientRift, nextAmbientSword;
    private boolean finalStand;
    private boolean closed;
    BlackFoxCorruption(BlackFoxCombatant actor, BlackFoxFight fight, BlackFoxActions actions, BlackFoxDomainAttacks ranged) {
        this.actor = actor; this.fight = fight; this.actions = actions;
        this.ranged = ranged;
        rifts = new BlackFoxRifts(actor, actions);
        cross = new BlackFoxDomainCross(actions);
    }
    public BlackFoxDomainCross cross() { return cross; }
    private long now() { return actor.body().level().getGameTime(); }
    private Vec3 center() { return Vec3.atBottomCenterOf(actor.arenaOrigin()).add(0, 1, 0); }
    public void begin() {
        ranged.clear(); pool.reset();
        closed = false; fight.start(BlackFoxFight.Skill.TRANSITION);
        actor.body().teleportTo(center().x, center().y, center().z);
        actor.body().setNoGravity(true);
        actor.body().setGlowingTag(true);
        BlackFoxJudgementCompat.purpleBlade(actor.body());
        nextGuard = now() + 100;
        nextAmbientRift = nextAmbientSword = now() + 60;
    }
    public void tick(ServerPlayer player) {
        var boss = actor.body();
        var level = (ServerLevel) boss.level();
        actions.face(player.position());
        rifts.pruneVisuals();
        switch (fight.skill()) {
            case TRANSITION -> {
                hover(center().add(0, Math.min(2.5, fight.age() / 24.0), 0));
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 1, boss.getZ(), 6, 2, 1, 2, .08);
                if (fight.age() % 4 == 0) for (int i = 0; i < 6; i++) {
                    double angle = Math.PI * 2 * i / 6 + fight.age() * .025;
                    Vec3 source = center().add(Math.cos(angle) * 18, 1, Math.sin(angle) * 18);
                    Vec3 current = source.lerp(boss.position().add(0, 1, 0), fight.age() / 60.0);
                    level.sendParticles(BlackFoxEffects.POLLUTION, current.x, current.y, current.z, 3, .15, .15, .15, 0);
                }
                if (fight.finished()) fight.start(BlackFoxFight.Skill.DOMAIN);
            }
            case STAGGER -> {
                rifts.clear();
                boss.setNoGravity(false);
                if (fight.finished()) { boss.setNoGravity(true); fight.start(BlackFoxFight.Skill.DOMAIN); }
            }
            case DOOM -> ultimate(player);
            default -> {
                if (fight.skill() == BlackFoxFight.Skill.RETURN_BLADE) {
                    BlackFoxSkills.tick(actions, player);
                    if (fight.finished()) { actions.nativeCombo().stop(); fight.start(BlackFoxFight.Skill.DOMAIN); }
                }
                else {
                    hover(center().add(0, fight.skill() == BlackFoxFight.Skill.DOMAIN
                            || BlackFoxOpenings.active(fight.skill(), fight.age()) ? .6 : 2.5, 0));
                    domain(player);
                }
            }
        }
        ambient(player);
        rifts.tick(player);
        if (fight.skill() != BlackFoxFight.Skill.DOOM && fight.skill() != BlackFoxFight.Skill.TRANSITION)
            ranged.tick(player, false);
    }
    private void ambient(ServerPlayer player) {
        if (BlackFoxOpenings.active(fight.skill(), fight.age())) {
            rifts.clear(); ranged.cancelFormation(); return;
        }
        if (BlackFoxDomainCadence.rifts(fight.skill(), fight.age()) && now() >= nextAmbientRift) {
            nextAmbientRift = now() + BlackFoxDomainCadence.RIFT_INTERVAL;
            rifts.spawn(player, center());
        }
        if (BlackFoxDomainCadence.swords(fight.skill(), fight.age()) && now() >= nextAmbientSword) {
            nextAmbientSword = now() + BlackFoxDomainCadence.SWORD_INTERVAL;
            ranged.volley(player);
        }
    }
    private void domain(ServerPlayer player) {
        switch (fight.skill()) {
            case DOMAIN -> {
                if (fight.age() < BlackFoxDomainPool.REST) return;
                var next = pool.select(now(), actor.body().getRandom().nextInt(Integer.MAX_VALUE));
                if (next != BlackFoxFight.Skill.DOMAIN) { ranged.clear(); rifts.clear(); fight.start(next); }
            }
            case SWORD_WHEEL -> { if (fight.age() == 1) ranged.wheel(player); }
            case DOMAIN_CROSS -> cross.tick(player);
            case DOMAIN_RAIN -> { if (fight.age() == 1) ranged.rain(player); }
            case DOMAIN_SEAL -> {
                if (fight.age() == 1) ranged.seal(player);
            }
            default -> { }
        }
        if (fight.skill() != BlackFoxFight.Skill.DOMAIN && fight.finished()) fight.start(BlackFoxFight.Skill.DOMAIN);
    }
    public void parried() {
        if (fight.skill() != BlackFoxFight.Skill.DOMAIN_CROSS) return;
        ranged.clear(); rifts.clear(); fight.clash(); BlackFoxEffects.breakMomentum(actor.body());
    }
    private void hover(Vec3 point) {
        var boss = actor.body();
        boss.setDeltaMovement(Vec3.ZERO);
        boss.setPos(point.x, point.y, point.z);
    }
    public boolean retaliate(DamageSource source) {
        if (!fight.phaseTwo() || fight.skill() != BlackFoxFight.Skill.DOMAIN || fight.age() < 24 || now() < nextGuard
                || source.getEntity() == null || actor.body().distanceToSqr(source.getEntity()) > 36) return false;
        nextGuard = now() + 160;
        fight.start(BlackFoxFight.Skill.RETURN_BLADE);
        BlackFoxEffects.tell(actor.body(), true);
        return false; // The initiating hit lands; the ensuing native B has a readable wind-up.
    }
    public boolean finalStand() { return finalStand; }
    public void startUltimate(ServerPlayer player) {
        if(finalStand) return;
        finalStand=true;
        clearHazards(); fight.start(BlackFoxFight.Skill.DOOM);
        core = BlackFoxBossCompat.spawnCore(actor, center().add(0, 12, 0));
        // Stay invulnerable and retry a missing core; never treat a failed spawn as a successful break.
        player.displayClientMessage(Component.translatable("maid_weapon.fox.boss.core"), true);
        actor.body().playSound(SoundEvents.WITHER_SPAWN, .6f, .6f);
    }
    private void ultimate(ServerPlayer player) {
        hover(center().add(0, 2.5, 0));
        if (core == null || core.isRemoved()) {
            if(fight.age()%20==0) core=BlackFoxBossCompat.spawnCore(actor,center().add(0,12,0));
            return;
        }
        core.setPos(center().add(0, 12 * Math.max(0,1 - fight.age() / (double) BlackFoxFight.CORE_FALL_TICKS), 0));
        ranged.scatter(core.position(),player);
        if (fight.age() % 4 == 0) ((ServerLevel) actor.body().level()).sendParticles(ParticleTypes.REVERSE_PORTAL,
                core.getX(), core.getY() + .75, core.getZ(), 12, 3, .6, 3, .08);
        if (fight.age() % 40 == 0) actor.body().playSound(SoundEvents.RESPAWN_ANCHOR_AMBIENT, .65f, .65f);
        if (fight.age() % 10 == 0 && player.distanceToSqr(core) < 9) damage(player, 10);
        if (fight.finished() && (fight.age()-BlackFoxFight.CORE_FALL_TICKS)%40==0) {
            damage(player, 60);
            ((ServerLevel) actor.body().level()).sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    center().x, center().y, center().z, 1, 0, 0, 0, 0);
        }
    }
    private void damage(ServerPlayer player, float amount) {
        if (!actor.combat().protects(player)) player.hurt(actor.body().damageSources().mobAttack(actor.body()), amount);
    }
    public void breakCore(BlackFoxCoreEntity target) { if (target == core && !closed) broken(); }
    private void broken() {
        if (core != null) core.discard(); core = null;
        clearHazards(); actions.nativeCombo().stop();
        actor.body().playSound(SoundEvents.GLASS_BREAK, 1, .6f);
        actor.combat().finishDefeat();
    }
    private void clearHazards() { ranged.clear(); rifts.clear(); }
    public void close() {
        closed = true; clearHazards(); if (core != null) core.discard(); core = null;
        actor.body().setNoGravity(false); actor.body().setGlowingTag(false);
    }
}

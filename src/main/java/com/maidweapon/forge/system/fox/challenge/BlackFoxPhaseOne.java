package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;

/** Phase-one dispatch and selection only. Each added skill has a separate executor. */
final class BlackFoxPhaseOne {
    private final BlackFoxActions actions;
    private final BlackFoxTactics tactics = new BlackFoxTactics();
    private final BlackFoxBlink blink;
    private final BlackFoxMinorCuts minorCuts;
    private final BlackFoxSwordplay swordplay = new BlackFoxSwordplay();
    private final BlackFoxDimensionStrike dimensionStrike;
    private final BlackFoxRiftCross riftCross;
    private boolean pendingBreak;
    private long restUntil;
    private long activeSequence = -1;
    BlackFoxPhaseOne(BlackFoxActions actions, BlackFoxCombatant actor, BlackFoxDimensionStrike dimensionStrike) {
        this.actions = actions; blink = new BlackFoxBlink(actions); minorCuts = new BlackFoxMinorCuts(actor);
        this.dimensionStrike = dimensionStrike;
        riftCross = new BlackFoxRiftCross(actions);
    }
    BlackFoxRiftCross riftCross() { return riftCross; }
    boolean opportunisticHit() {
        if (!BlackFoxOpenings.active(actions.fight.skill(), actions.fight.age())) return false;
        var actor = (BlackFoxCombatant) actions.boss();
        var player = actions.boss().getServer().getPlayerList().getPlayer(actor.challenger());
        breakMomentum();
        if (player != null) {
            actor.combat().protectParry();
            BlackFoxEffects.contact(actions.boss(), player, true);
            com.maidweapon.forge.network.BlackFoxFeedbackNetwork.contact(actor, player, 1);
            com.maidweapon.forge.compat.BlackFoxRankCompat.reward(player, false);
        }
        return true;
    }
    void close() { blink.cancel(); minorCuts.clear(); actions.ranged().cancelFormation(); }
    void pressured(long now) { tactics.pressured(now); }
    void parried(long now, boolean comboClash) {
        restUntil=Math.max(restUntil,now+BlackFoxSpacing.PARRY_HOLD);
        blink.holdUntil(restUntil);
        if (comboClash) { swordplay.clash(); pendingBreak = false; return; }
        var skill = actions.fight.skill();
        if (skill == BlackFoxFight.Skill.RIFT_CROSS || skill == BlackFoxFight.Skill.ARC_BARRAGE) {
            breakMomentum(); return;
        }
        if (skill != BlackFoxFight.Skill.PROBE_CUT && skill != BlackFoxFight.Skill.CROSS_CUT
                && skill != BlackFoxFight.Skill.SIDESTEP_CUT && skill != BlackFoxFight.Skill.COUNTER_CUT
                && skill != BlackFoxFight.Skill.RETURN_BLADE && skill != BlackFoxFight.Skill.SKY) return;
        int window = skill == BlackFoxFight.Skill.CROSS_CUT && actions.fight.age() >= 36 ? 1 : 0;
        if (!swordplay.parry(now, actions.fight.sequence(), window)) return;
        if (skill == BlackFoxFight.Skill.RETURN_BLADE) pendingBreak = true;
        else breakMomentum();
    }
    private void breakMomentum() {
        if (actions.fight.skill() == BlackFoxFight.Skill.RIFT_CROSS || actions.fight.skill() == BlackFoxFight.Skill.ARC_BARRAGE)
            actions.ranged().clear();
        close(); actions.nativeCombo().stop(); actions.fight.clash();
        BlackFoxEffects.breakMomentum(actions.boss());
    }
    void completed(BlackFoxFight.Skill skill, long sequence, ServerPlayer player) {
        if (pendingBreak) { pendingBreak = false; breakMomentum(); return; }
        if(actions.boss().level().getGameTime()<restUntil) return;
        if (skill != BlackFoxFight.Skill.PROBE_CUT || !swordplay.wasParried(sequence)
                || actions.boss().distanceTo(player) > 6 || actions.fight.pressure() >= BlackFoxFight.PRESSURE_COST) return;
        // Remain at the same position. Answer the successful parry, or briefly hold rather than blink away.
        switch (actions.boss().getRandom().nextInt(3)) {
            case 0 -> { if (actions.fight.ready(BlackFoxFight.Skill.CROSS_CUT)) actions.begin(BlackFoxFight.Skill.CROSS_CUT, player); }
            case 1 -> { if (actions.fight.ready(BlackFoxFight.Skill.RETURN_BLADE)) actions.begin(BlackFoxFight.Skill.RETURN_BLADE, player); }
            default -> restUntil = actions.boss().level().getGameTime() + 12;
        }
    }
    void tick(ServerPlayer player) {
        if (activeSequence != actions.fight.sequence()) {
            activeSequence = actions.fight.sequence();
            if (actions.fight.skill() == BlackFoxFight.Skill.DIMENSION_STRIKE
                    || actions.fight.skill() == BlackFoxFight.Skill.STANDING_IAIDO) blink.cancel();
            if (actions.fight.skill() == BlackFoxFight.Skill.STAGGER) swordplay.clash();
        }
        actions.boss().setNoGravity(true);
        actions.boss().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        blink.tick(player);
        minorCuts.tick();
        swordplay.advance(actions.boss().level().getGameTime(), actions.boss().distanceTo(player) <= 6);
        actions.ranged().tick(player, false); // Same session-owned tracker; expire first-phase volleys too.
        switch (actions.fight.skill()) {
            case DIMENSION_STRIKE -> dimensionStrike.tick(player);
            case STANDING_IAIDO -> BlackFoxStandingIaido.tick(actions,player);
            case APPROACH -> approach(player);
            case SIDESTEP_CUT -> BlackFoxSideCut.tick(actions, blink, player);
            case CROSS_CUT -> BlackFoxCrossCuts.tick(actions, player);
            case PHANTOM_FEINT -> BlackFoxPhantomFeint.tick(actions, blink, minorCuts, player);
            case RIFT_CROSS -> riftCross.tick(player);
            case ARC_BARRAGE -> BlackFoxArcBarrage.tick(actions, blink, player);
            case COUNTER_CUT -> BlackFoxCounterCut.tick(actions, player);
            case PROBE_CUT -> BlackFoxProbeCut.tick(actions, player);
            default -> BlackFoxSkills.tick(actions, player);
        }
    }
    void approach(ServerPlayer player) {
        actions.face(player.position());
        if (actions.boss().level().getGameTime() < restUntil) return;
        double distance = BlackFoxActions.horizontal(player.position().subtract(actions.boss().position())).length();
        if (BlackFoxSpacing.needsAdjustment(distance)) blink.request(player, false);
        if (blink.busy()) return;
        if (!actions.fight.finished()) return;
        var next = actions.fight.choose(actions.boss().distanceTo(player), player.getY() - actions.boss().getY(),
                actions.boss().getRandom().nextInt(Integer.MAX_VALUE));
        if (actions.fight.pressure() < BlackFoxFight.PRESSURE_COST
                && actions.fight.ready(BlackFoxFight.Skill.SIDESTEP_CUT)
                && actions.boss().distanceTo(player) <= 8 && tactics.reposition(actions.boss().level().getGameTime()))
            next = BlackFoxFight.Skill.SIDESTEP_CUT;
        if (next != BlackFoxFight.Skill.APPROACH) actions.begin(next, player);
    }
}

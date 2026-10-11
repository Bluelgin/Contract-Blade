package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;

/** Phase-one dispatch and selection only. Each added skill has a separate executor. */
final class BlackFoxPhaseOne {
    private final BlackFoxActions actions;
    private final BlackFoxCombatant actor;
    private final BlackFoxTactics tactics = new BlackFoxTactics();
    private final BlackFoxBlink blink;
    private final BlackFoxMinorCuts minorCuts;
    private long lastParriedSequence = -1;
    private final BlackFoxDimensionStrike dimensionStrike;
    private final BlackFoxRiftCross riftCross;
    private final BlackFoxTripleCombo tripleCombo;
    private long restUntil;
    private long activeSequence = -1;
    BlackFoxPhaseOne(BlackFoxActions actions, BlackFoxCombatant actor, BlackFoxDimensionStrike dimensionStrike) {
        this.actions = actions; this.actor = actor; blink = new BlackFoxBlink(actions); minorCuts = new BlackFoxMinorCuts(actor);
        this.dimensionStrike = dimensionStrike;
        riftCross = new BlackFoxRiftCross(actions);
        tripleCombo = new BlackFoxTripleCombo(actions);
    }
    BlackFoxRiftCross riftCross() { return riftCross; }
    void close() { blink.cancel(); tripleCombo.cancel(); minorCuts.clear(); actions.ranged().cancelFormation(); actor.combat().corruption().clearCombatRifts(); }
    void pressured(long now) { tactics.pressured(now); }
    void parried(long now, boolean comboClash) {
        // Feedback/protection and the shared five-parry counter are owned by the controller.
        // Do not pause, relocate or cancel a skill for an individual parry.
        lastParriedSequence = actions.fight.sequence();
    }
    void completed(BlackFoxFight.Skill skill, long sequence, ServerPlayer player) {
        if (actor.combat().seals().active()) return;
        if(actions.boss().level().getGameTime()<restUntil) return;
        if (skill != BlackFoxFight.Skill.PROBE_CUT || lastParriedSequence != sequence
                || actions.boss().distanceTo(player) > 6 || actions.fight.pressure() >= BlackFoxFight.PRESSURE_COST) return;
        // Remain at the same position. Answer the successful parry, or briefly hold rather than blink away.
        switch (actions.boss().getRandom().nextInt(3)) {
            case 0 -> { if (actions.fight.ready(BlackFoxFight.Skill.CROSS_CUT)) actions.begin(BlackFoxFight.Skill.CROSS_CUT, player); }
            case 1 -> { if (actions.fight.ready(BlackFoxFight.Skill.RETURN_BLADE)) actions.begin(BlackFoxFight.Skill.RETURN_BLADE, player); }
            default -> restUntil = actions.boss().level().getGameTime() + 6;
        }
    }
    void tick(ServerPlayer player) {
        if (activeSequence != actions.fight.sequence()) {
            activeSequence = actions.fight.sequence();
            tripleCombo.cancel();
            if (actions.fight.skill() == BlackFoxFight.Skill.DIMENSION_STRIKE
                    || actions.fight.skill() == BlackFoxFight.Skill.STANDING_IAIDO
                    || actions.fight.skill() == BlackFoxFight.Skill.TRIPLE_COMBO) blink.cancel();
        }
        actions.boss().setNoGravity(true);
        actions.boss().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        blink.tick(player);
        minorCuts.tick();
        actions.ranged().tick(player, false); // Same session-owned tracker; expire first-phase volleys too.
        actor.combat().corruption().combatSkills(player);
        switch (actions.fight.skill()) {
            case SEAL_ARRAY -> actions.face(player.position());
            case TRIPLE_COMBO -> tripleCombo.tick(player);
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
        boolean sealing = actor.combat().seals().active();
        if (BlackFoxSpacing.needsAdjustment(distance) && (!sealing || distance < 3 || distance > 16)) blink.request(player, false);
        if (blink.busy()) return;
        if (!actions.fight.finished()) return;
        if (sealing) {
            var candidates = new BlackFoxFight.Skill[] { BlackFoxFight.Skill.SEAL, BlackFoxFight.Skill.RIFT_CROSS, BlackFoxFight.Skill.PROBE_CUT };
            int first = actions.boss().getRandom().nextInt(candidates.length);
            for (int n = 0; n < candidates.length; n++) {
                var candidate = candidates[(first + n) % candidates.length];
                if (actions.fight.ready(candidate)) { actions.begin(candidate, player); return; }
            }
            return;
        }
        var next = actions.fight.choose(actions.boss().distanceTo(player), player.getY() - actions.boss().getY(),
                actions.boss().getRandom().nextInt(Integer.MAX_VALUE));
        if (actions.fight.pressure() < BlackFoxFight.PRESSURE_COST
                && actions.fight.ready(BlackFoxFight.Skill.SIDESTEP_CUT)
                && actions.boss().distanceTo(player) <= 8 && tactics.reposition(actions.boss().level().getGameTime()))
            next = BlackFoxFight.Skill.SIDESTEP_CUT;
        if (next != BlackFoxFight.Skill.APPROACH) actions.begin(next, player);
    }
}

package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.compat.*;
import com.maidweapon.forge.system.fox.challenge.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Opt-in real Guard fixture: native capabilities and native success hook, not a mocked cancellation. */
final class BlackFoxGuardValidation {
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void input(ServerPlayer player, long pressedAgo, String... commands) throws Exception {
        player.setOnGround(true);
        var cap = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName(
                "mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState").getField("INPUT_STATE").get(null);
        var state = player.getCapability(cap).resolve().orElseThrow();
        var api = Class.forName("mods.flammpfeil.slashblade.capability.inputstate.IInputState");
        var commandType = (Class<? extends Enum>) Class.forName("mods.flammpfeil.slashblade.util.InputCommand");
        var set = (Set) api.getMethod("getCommands").invoke(state);
        set.clear();
        for (String command : commands) set.add(Enum.valueOf(commandType, command));
        var times = (Map) api.getMethod("getLastPressTimes").invoke(state);
        times.put(Enum.valueOf(commandType, "SNEAK"), player.level().getGameTime() - pressedAgo);
        player.setShiftKeyDown(set.contains(Enum.valueOf(commandType, "SNEAK")));
    }
    static void clock(ServerPlayer player, int ticks) {
        var data = (net.minecraft.world.level.storage.ServerLevelData) player.getServer().overworld().getLevelData();
        data.setGameTime(player.level().getGameTime() + ticks);
    }
    static void check(boolean ok, String label) {
        if (!ok) throw new IllegalStateException(label);
    }
    static void face(ServerPlayer player, float yaw) {
        player.setYRot(yaw); player.setYHeadRot(yaw); player.yRotO = yaw; player.yHeadRotO = yaw;
        player.setXRot(0); player.xRotO = 0;
    }
    static void run(BlackFoxCombatant actor, ServerPlayer player) throws Exception {
        var boss = actor.body();
        var fight = actor.combat().fight();
        var blade = player.getMainHandItem();
        blade.enchant(Enchantments.THORNS, 1);
        player.setPos(boss.position().add(0, 0, -4)); face(player, 0); boss.setYRot(180);
        check(BlackFoxGuardCompat.available(), "verified native guard hook installed");
        fight.start(BlackFoxFight.Skill.RETURN_BLADE);
        input(player, 0);
        BlackFoxEncounters.swing(player, blade, true);
        check(!actor.combat().parryNative(player), "swing alone cannot parry");
        input(player, 0, "SNEAK", "FORWARD");
        check(!actor.combat().parryNative(player), "native moving sneak cannot guard");
        input(player, 0, "SNEAK"); player.setOnGround(false);
        check(!actor.combat().parryNative(player), "native airborne guard is unavailable");
        input(player, 0, "SNEAK"); face(player, 180);
        check(!actor.combat().parryNative(player), "rear attacks cannot be guarded");
        face(player, 0);
        input(player, 30, "SNEAK");
        check(!actor.combat().parryNative(player), "holding sneak cannot perfect guard a melee hit");
        // Ordinary native projectile guard remains available, but cannot award a perfect-guard mark.
        BlackFoxNativeCombo.update(player, net.minecraft.resources.ResourceLocation.parse("slashblade:none"));
        var rankCap = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName(
                "mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank").getField("RANK_POINT").get(null);
        var rank = player.getCapability(rankCap).resolve().orElseThrow();
        var rankApi = Class.forName("mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank");
        long unit = (long) rankApi.getMethod("getUnitCapacity").invoke(rank);
        rankApi.getMethod("setRawRankPoint", long.class).invoke(rank, unit * 20);
        rankApi.getMethod("setLastUpdte", long.class).invoke(rank, player.level().getGameTime());
        var shot = BlackFoxPhantomCompat.spawn(boss, player.position().add(0, 1, 2), new Vec3(0,0,-1));
        check(BlackFoxGuardCompat.defend(player, actor, shot) == BlackFoxGuardCompat.Result.BLOCK,
                "old held guard blocks projectile without becoming Just Guard");
        shot.discard();
        // A copied or unrelated canceled event is never evidence of native success.
        var unrelated = new net.minecraftforge.event.entity.living.LivingAttackEvent(player, boss.damageSources().mobAttack(boss), 1);
        unrelated.setCanceled(true); BlackFoxGuardCompat.just(unrelated);
        input(player, 30, "SNEAK");
        check(BlackFoxGuardCompat.defend(player, actor, null) == BlackFoxGuardCompat.Result.NONE,
                "unrelated cancellation cannot leak a success");
        input(player, 0, "SNEAK");
        fight.start(BlackFoxFight.Skill.PROBE_CUT);
        check(actor.combat().parryNative(player), "actual native Just Guard accepted on ordinary cut");
        check(actor.combat().protects(player), "successful guard provides encounter-local protection");
        clock(player, 20);
        for (int i = 0; i < 5; i++) {
            fight.start(BlackFoxFight.Skill.RETURN_BLADE);
            input(player, 0, "SNEAK");
            check(actor.combat().parryNative(player), "fresh native skill guard " + i);
            check(actor.combat().parryNative(player), "dense followup is protected without a second reward");
            input(player, 30); clock(player, 1); actor.combat().tick();
            check(fight.offBalance() == (i == 4), "only fifth eligible skill guard breaks balance");
            clock(player, 20);
            player.setPos(boss.position().add(0, 0, -4)); face(player, 0);
        }
        check(fight.duration() == BlackFoxBalance.RECOVERY_TICKS, "existing short recovery preserved");
        check(!BlackFoxGuardCompat.defend(player, actor, null).equals(BlackFoxGuardCompat.Result.JUST), "guard scope cleared");
        com.mojang.logging.LogUtils.getLogger().info("BLACK_FOX_GUARD_PASS: native conditions, ordinary block, provenance, five skill guards, protection and scope cleanup");
    }
    private BlackFoxGuardValidation() { }
}

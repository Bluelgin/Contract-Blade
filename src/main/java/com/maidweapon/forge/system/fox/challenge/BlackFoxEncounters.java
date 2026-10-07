package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.api.BlackFoxEncounterApi;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.compat.fox.BlackFoxBossCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Server authority for private cells, attack provenance and session cleanup. No model/name-based identification. */
public final class BlackFoxEncounters {
    private record Swing(ItemStack blade, long tick) { }
    private static final Map<MinecraftServer, Map<UUID, BlackFoxCombatant>> SESSIONS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Swing> SWINGS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Swing> COMBO_B = new WeakHashMap<>();
    private static final Map<ServerPlayer, Swing> PARRY = new WeakHashMap<>();
    private static final String WON = "MaidWeaponBlackFoxDefeated";
    public static void setup() { BlackFoxEncounterApi.setResolver(BlackFoxEncounters::resolve); }
    public static BlackFoxCombatant get(ServerPlayer player) {
        return SESSIONS.getOrDefault(player.getServer(), Map.of()).get(player.getUUID());
    }
    public static boolean start(ServerPlayer player) {
        if (!FoxChallengeService.inside(player) || !player.isAlive() || !BlackFoxBossCompat.available()) return false;
        stop(player);
        var boss = BlackFoxBossCompat.spawn(player, FoxChallengeService.origin(player));
        if (boss == null) return false;
        SESSIONS.computeIfAbsent(player.getServer(), ignored -> new HashMap<>()).put(player.getUUID(), boss);
        boss.combat().showBar(player);
        return true;
    }
    public static void stop(ServerPlayer player) {
        var sessions = SESSIONS.get(player.getServer());
        var boss = sessions == null ? null : sessions.remove(player.getUUID());
        if (boss != null) { boss.combat().close(); boss.body().discard(); }
        var returns = BlackFoxShrineReturn.get(player.getServer());
        if (returns.pending(player.getUUID())) returns.settle(player);
        SWINGS.remove(player);
        COMBO_B.remove(player);
        PARRY.remove(player);
    }
    public static boolean registered(BlackFoxCombatant boss) {
        return !boss.body().isRemoved() && boss.body().level().dimension().equals(FoxChallengeService.LEVEL)
                && SESSIONS.getOrDefault(boss.body().getServer(), Map.of()).get(boss.challenger()) == boss;
    }
    public static void retire(BlackFoxCombatant boss) {
        var player = boss.body().getServer().getPlayerList().getPlayer(boss.challenger());
        if (player != null) {
            var returns = BlackFoxShrineReturn.get(player.getServer());
            if (returns.pending(player.getUUID())) returns.settle(player);
        }
        var sessions = SESSIONS.get(boss.body().getServer());
        if (sessions != null) sessions.remove(boss.challenger(), boss);
        boss.combat().close(); boss.body().discard();
    }
    public static boolean authorized(DamageSource source, BlackFoxCombatant boss) {
        if (!registered(boss) || boss.combat().fight().skill() == BlackFoxFight.Skill.DEFEATED) return false;
        var attacker = source.getEntity();
        return attacker != null && attacker.level() == boss.body().level()
                && (attacker instanceof ServerPlayer player && boss.challenger().equals(player.getUUID())
                || attacker instanceof TamableAnimal maid && boss.challenger().equals(maid.getOwnerUUID()));
    }
    public static void swing(ServerPlayer player, ItemStack blade, boolean nativeCombo) {
        var boss = get(player);
        if (boss == null || !player.isAlive() || boss.body().isRemoved()) return;
        boolean weapon = SlashBladeCompat.isSlashBlade(blade) || blade.canPerformAction(net.minecraftforge.common.ToolActions.SWORD_SWEEP);
        if (!weapon || !nativeCombo && player.getAttackStrengthScale(.5f) < .8f) return;
        SWINGS.put(player, new Swing(blade, player.level().getGameTime()));
        PARRY.put(player, new Swing(blade, player.level().getGameTime()));
        // Generic swings provide damage provenance only, never permission to interrupt Combo B.
    }
    public static void nativeSwing(ServerPlayer player, ItemStack blade, net.minecraft.resources.ResourceLocation combo) {
        swing(player, blade, true);
        if (!SlashBladeCompat.isSlashBlade(blade) || !combo.getNamespace().equals("slashblade")
                || !combo.getPath().matches("combo_b[1-7]")) return;
        COMBO_B.put(player, new Swing(blade, player.level().getGameTime()));
        var boss = get(player);
        if (boss != null) boss.combat().tryClash(player);
    }
    public static boolean freshComboB(ServerPlayer player) {
        return fresh(player, COMBO_B.get(player));
    }
    public static void consumeComboB(ServerPlayer player) { COMBO_B.remove(player); }
    public static boolean consumeParry(ServerPlayer player) {
        if (!fresh(player, PARRY.get(player))) return false;
        PARRY.remove(player);
        return true;
    }
    private static boolean fresh(ServerPlayer player, Swing swing) {
        return swing != null && player.level().getGameTime() - swing.tick() >= 0
                && player.level().getGameTime() - swing.tick() <= 3 && swing.blade() == player.getMainHandItem();
    }
    public static boolean freshSwing(ServerPlayer player) {
        return fresh(player, SWINGS.get(player));
    }
    private static BlackFoxEncounterApi.HitContext resolve(DamageSource source, LivingEntity target) {
        if (!(target instanceof BlackFoxCombatant boss) || !authorized(source, boss)
                || boss.combat().fight().guard()) return null;
        // SlashBlade's area attacks can report a player as their direct source. Resolve the
        // projectile's birth-time blade through the scoped trace, never a later held sword.
        var projectileHit = BlackFoxAttackTrace.current();
        if (projectileHit != null) {
            if (!projectileHit.owner().getUUID().equals(boss.challenger())
                    || !contains(projectileHit.owner(), projectileHit.originalBlade())) return null;
            return projectileHit;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            var melee = BlackFoxAttackTrace.meleeContext(source.getEntity());
            return melee != null && contains(melee.owner(), melee.originalBlade()) ? melee : null;
        }
        if (source.getDirectEntity() != player || !freshSwing(player)) return null;
        return new BlackFoxEncounterApi.HitContext(player, SWINGS.get(player).blade());
    }
    private static boolean contains(ServerPlayer player, ItemStack blade) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (player.getInventory().getItem(i) == blade) return true;
        return false;
    }
    public static boolean recordWin(ServerPlayer player) {
        var root = player.getPersistentData();
        var data = root.getCompound(Player.PERSISTED_NBT_TAG);
        boolean first = !data.getBoolean(WON);
        data.putBoolean(WON, true); root.put(Player.PERSISTED_NBT_TAG, data);
        return first;
    }
    public static void copyWin(Player old, Player replacement) {
        if (!old.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(WON)) return;
        var root = replacement.getPersistentData();
        var data = root.getCompound(Player.PERSISTED_NBT_TAG); data.putBoolean(WON, true); root.put(Player.PERSISTED_NBT_TAG, data);
    }
    public static void clear(MinecraftServer server) {
        var sessions = SESSIONS.remove(server);
        if (sessions != null) sessions.values().forEach(boss -> boss.combat().close());
        SWINGS.clear(); COMBO_B.clear(); PARRY.clear(); BlackFoxAttackTrace.clear();
    }
    private BlackFoxEncounters() { }
}

package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.fox.BlackFoxBossCompat;
import com.maidweapon.forge.entity.BlackFoxCoreEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Encounter-owned objectives: no crystals, explosions, saves or regenerated cores. */
public final class BlackFoxSealArray {
    private final BlackFoxCombatant actor;
    private final BlackFoxCoreEntity[] cores = new BlackFoxCoreEntity[4];
    private final List<Entity> shots = new ArrayList<>();
    private boolean used;
    private int elapsed, cursor, warnedSlot = -1;
    private Vec3 aim;
    private long sequence;
    BlackFoxSealArray(BlackFoxCombatant actor) { this.actor = actor; }
    public int remaining() {
        int count = 0; for (var core : cores) if (core != null && core.isAlive() && !core.isRemoved()) count++;
        return count;
    }
    public boolean active() { return remaining() > 0; }
    boolean begin(ServerPlayer player) {
        var fight = actor.combat().fight();
        if (used || fight.skill() != BlackFoxFight.Skill.APPROACH || fight.offBalance()
                || actor.body().getHealth() > actor.body().getMaxHealth() * .5f) return false;
        used = true; fight.start(BlackFoxFight.Skill.SEAL_ARRAY); sequence = fight.sequence();
        player.sendSystemMessage(Component.translatable("maid_weapon.fox.boss.seal_summon"));
        BlackFoxEffects.tell(actor.body(), true); return true;
    }
    void tick(ServerPlayer player) {
        var fight = actor.combat().fight();
        if (fight.skill() == BlackFoxFight.Skill.SEAL_ARRAY && fight.age() == 12) {
            var origin = actor.arenaOrigin();
            for (int slot = 0; slot < 4; slot++) {
                Vec3 at = new Vec3(origin.getX() + .5 + ((slot & 1) == 0 ? -17 : 17), origin.getY() + 1,
                        origin.getZ() + .5 + ((slot & 2) == 0 ? -17 : 17));
                cores[slot] = BlackFoxBossCompat.spawnSeal(actor, at, slot);
                if (cores[slot] == null) { close(); return; } // Fail open rather than lock the encounter.
            }
        }
        shots.removeIf(Entity::isRemoved);
        boolean vanished = false;
        for (int slot = 0; slot < 4; slot++) if (cores[slot] != null && cores[slot].isRemoved()) {
            cores[slot] = null; vanished = true;
        }
        if (vanished && !active()) { close(); announce(player); }
        if (!active()) return;
        elapsed++;
        if (elapsed % 40 == 32) {
            warnedSlot = -1;
            for (int n = 0; n < 4; n++) { int slot = cursor++ & 3;
                if (cores[slot] != null && cores[slot].isAlive()) { warnedSlot = slot; break; }
            }
            if (warnedSlot >= 0) {
                aim = player.getEyePosition(); BlackFoxEffects.tell(cores[warnedSlot], true);
            }
        }
        if (elapsed % 40 == 0 && warnedSlot >= 0) {
            var core = cores[warnedSlot];
            if (core != null && core.isAlive()) {
                var shot = actor.combat().ranged().sealPulse(core.position().add(0, .75, 0), aim, sequence, warnedSlot);
                if (shot != null) shots.add(shot);
            }
            warnedSlot = -1;
        }
    }
    public void breakCore(BlackFoxCoreEntity core, int slot) {
        if (slot < 0 || slot >= 4 || cores[slot] != core) return;
        cores[slot] = null; core.discard();
        BlackFoxEffects.breakMomentum(core);
        var player = actor.body().getServer().getPlayerList().getPlayer(actor.challenger());
        if (!active()) close();
        if (player != null) announce(player);
    }
    private void announce(ServerPlayer player) {
        player.displayClientMessage(Component.translatable(active() ? "maid_weapon.fox.boss.seal_remaining"
                : "maid_weapon.fox.boss.seal_broken", remaining()), true);
    }
    public void close() {
        for (int slot = 0; slot < 4; slot++) { if (cores[slot] != null) cores[slot].discard(); cores[slot] = null; }
        shots.forEach(Entity::discard); shots.clear(); warnedSlot = -1; aim = null;
    }
}

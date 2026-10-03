package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.system.deployment.ContractCompanionService;
import com.maidweapon.forge.system.deployment.ContractCompanionState;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in isolated native lifecycle fixture. Never enable on a player's world. */
@Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID)
public final class ContractCompanionValidation {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.companion.validation")) return;
        try {
            run(event);
            ContractHandbookValidation.run(event.getServer());
            LogUtils.getLogger().info("[MaidWeapon] COMPANION_VALIDATION_PASS");
        } catch (Throwable error) {
            LogUtils.getLogger().error("[MaidWeapon] COMPANION_VALIDATION_FAIL", error);
        } finally { event.getServer().halt(false); }
    }

    private static void run(ServerStartedEvent event) throws ReflectiveOperationException {
        var level = event.getServer().overworld();
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString(
                        "4141fcab-0b65-453d-8956-d875d0c9719b"), "CompanionFixture"));
        owner.getInventory().clearContent();
        owner.getInventory().selected = 0;
        owner.moveTo(0.5, 100, 0.5, 0, 0);
        owner.setHealth(20);
        Mob maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(level);
        TlmEntityAdapter.tame(maid, owner);
        maid.moveTo(0.5, 100, 0.5, 0, 0);
        level.addFreshEntity(maid);
        var carrier = new ItemStack(ModItems.MAID_SWORD.get());
        owner.getInventory().setItem(0, carrier);
        try {
            check(ContractLifecycleService.capture(owner, maid, carrier, false), "fixture contract captured");
            var identity = maid.getUUID();
            for (int tick = 0; tick < 300; tick++) {
                owner.tickCount = tick;
                ContractCompanionService.tick(owner);
            }
            check(MaidInfusion.containsMaid(carrier) && level.getEntity(identity) == null,
                    "holding a contract never auto manifests");
            call(owner);
            maid = (Mob) level.getEntity(identity);
            check(maid != null && !MaidInfusion.containsMaid(carrier), "manual call manifests one native maid");
            check(ContractCompanionState.mode(carrier) == ContractCompanionState.Mode.MANUAL, "manual intent persists on carrier");
            owner.getInventory().setItem(1, new ItemStack(Items.STICK));
            owner.getInventory().selected = 1;
            owner.tickCount = 400;
            ContractCompanionService.tick(owner);
            check(level.getEntity(identity) == maid, "manual companion remains after switching items");
            var data = MaidInfusion.data(carrier);
            data.setResonance(20);
            com.maidweapon.forge.system.contract.ContractCarrierData.setMaidData(carrier, data);
            var ownerTick = new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                    net.minecraftforge.event.TickEvent.Phase.END, owner);
            com.maidweapon.forge.event.MaidBondCombatHandler.onPlayerTick(ownerTick);
            check(MaidInfusion.data(carrier).getResonance() == 20
                    + com.maidweapon.common.MaidWeaponConfig.RESONANCE_PASSIVE_RECOVERY.get(),
                    "switched-away companion still recovers resonance");
            long originalTime = level.getGameTime();
            int drainInterval = com.maidweapon.common.MaidWeaponConfig.RESONANCE_COMBAT_DRAIN_INTERVAL.get();
            var clock = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
            clock.setGameTime(((originalTime / drainInterval) + 1) * drainInterval);
            var combatant = new Zombie(level);
            com.maidweapon.forge.event.MaidBondCombatHandler.onDamage(
                    new net.minecraftforge.event.entity.living.LivingDamageEvent(owner,
                            owner.damageSources().mobAttack(combatant), 2));
            int beforeDrain = MaidInfusion.data(carrier).getResonance();
            com.maidweapon.forge.event.MaidBondCombatHandler.onPlayerTick(ownerTick);
            check(MaidInfusion.data(carrier).getResonance() == beforeDrain - 1,
                    "switched-away companion still drains combat resonance");
            com.maidweapon.forge.event.MaidBondCombatHandler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(owner));
            clock.setGameTime(originalTime);
            owner.getInventory().selected = 0;
            maid.getClass().getMethod("setHomeModeEnable", boolean.class).invoke(maid, true);
            ContractCompanionService.tick(owner);
            check(ContractCompanionState.mode(carrier) == ContractCompanionState.Mode.RESIDENT, "native home mode selects residence");
            int residentResonance = MaidInfusion.data(carrier).getResonance();
            com.maidweapon.forge.event.MaidBondCombatHandler.onPlayerTick(ownerTick);
            check(MaidInfusion.data(carrier).getResonance() == residentResonance,
                    "resident stays excluded from following resonance ticks");
            InfusedMaidDeploymentSystem.onLogout(new PlayerEvent.PlayerLoggedOutEvent(owner));
            check(level.getEntity(identity) == maid && !MaidInfusion.containsMaid(carrier), "resident survives owner logout");
            owner.moveTo(100.5, 100, 0.5, 0, 0);
            call(owner);
            check(level.getEntity(identity) == maid && !MaidInfusion.containsMaid(carrier), "distant recall cannot capture or relocate resident");
            var attacker = new Zombie(level);
            ContractCompanionService.attacked(owner, attacker, 2);
            owner.setHealth(18);
            ContractCompanionService.tick(owner);
            check(level.getEntity(identity) == maid && maid.getX() < 2, "injury cannot pull a resident out of the house");

            // Simulate save/unload: only the entity save owns her inventory and state.
            var saved = new net.minecraft.nbt.CompoundTag();
            maid.saveWithoutId(saved); // World saves retain Pos; contract storage intentionally strips it.
            var storedCarrier = carrier.save(new net.minecraft.nbt.CompoundTag());
            maid.discard();
            carrier = ItemStack.of(storedCarrier);
            owner.getInventory().setItem(0, carrier);
            ContractCompanionService.clearSession();
            call(owner);
            check(level.getEntity(identity) == null && !MaidInfusion.containsMaid(carrier), "unloaded resident never clones from a reference");
            var nether = event.getServer().getLevel(Level.NETHER);
            maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(nether);
            TlmEntityAdapter.load(maid, saved);
            check(nether.addFreshEntity(maid), "native resident added in another world");
            ContractCompanionState.mark(carrier, maid, ContractCompanionState.Mode.RESIDENT, 0);
            call(owner);
            check(!maid.isRemoved() && !MaidInfusion.containsMaid(carrier), "cross-world recall is rejected");
            maid.discard();
            maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(level);
            TlmEntityAdapter.load(maid, saved);
            level.addFreshEntity(maid);
            check(TlmResidenceAdapter.isResident(maid), "native residence survives save and reload");
            owner.moveTo(32.5, 100, 0.5, 0, 0);
            call(owner);
            check(MaidInfusion.containsMaid(carrier) && level.getEntity(identity) == null, "recall succeeds at exactly 32 blocks");

            owner.setHealth(20);
            ContractCompanionService.attacked(owner, null, 2);
            owner.setHealth(18);
            ContractCompanionService.tick(owner);
            check(MaidInfusion.containsMaid(carrier), "environmental damage does not summon");
            owner.setHealth(20);
            ContractCompanionService.attacked(owner, attacker, 0);
            owner.setHealth(18);
            ContractCompanionService.tick(owner);
            check(MaidInfusion.containsMaid(carrier), "zero damage does not summon");
            owner.setHealth(20);
            ContractCompanionService.attacked(owner, attacker, 2);
            ContractCompanionService.tick(owner);
            check(MaidInfusion.containsMaid(carrier), "cancelled damage without health loss does not summon");
            ContractCompanionService.attacked(owner, attacker, 2);
            owner.setHealth(0);
            ContractCompanionService.tick(owner);
            check(MaidInfusion.containsMaid(carrier), "lethal injury cannot grant a last-minute rescue");
            owner.setHealth(20);
            carrier.getOrCreateTag().putBoolean("MaidWantsAttention", true);
            carrier.getOrCreateTag().putLong("MaidAttentionStoredTicks", 120000);
            ContractCompanionService.attacked(owner, attacker, 2);
            owner.setHealth(18);
            ContractCompanionService.tick(owner);
            maid = (Mob) level.getEntity(identity);
            check(maid != null && ContractCompanionState.mode(carrier) == ContractCompanionState.Mode.GUARD,
                    "real health loss queues protective manifestation");
            check(carrier.getOrCreateTag().getLong("MaidAttentionStoredTicks") == 0
                    && !carrier.getOrCreateTag().getBoolean("MaidWantsAttention"),
                    "protective manifestation resets stored attention");
            var pendingVoices = com.maidweapon.forge.system.MaidAttentionSystem.class.getDeclaredField("PENDING_VOICES");
            pendingVoices.setAccessible(true);
            check(!((java.util.Map<?, ?>) pendingVoices.get(null)).containsKey(owner.getUUID()),
                    "protective manifestation never queues attention idle voice");
            long deadline = ContractCompanionState.until(carrier);
            check(deadline >= level.getGameTime() + 400, "guard stays at least twenty seconds");
            ContractCompanionService.tick(owner);
            check(level.getEntity(identity) == maid, "guard does not vanish on next tick");
            ContractCompanionState.mark(carrier, maid, ContractCompanionState.Mode.GUARD, level.getGameTime() - 1);
            ContractCompanionService.tick(owner);
            check(MaidInfusion.containsMaid(carrier) && level.getEntity(identity) == null,
                    "safe expired guard recalls with equipment intact");
            check(owner.getHealth() == 18, "dialogue does not heal or buff the player");
        } finally {
            if (maid != null && !maid.isRemoved()) maid.discard();
            ContractCompanionService.disconnect(owner);
        }
    }

    private static void call(net.minecraft.world.entity.player.Player owner) {
        ContractCompanionService.disconnect(owner); // Advance past the request throttle for fixture calls.
        ContractCompanionService.toggle(owner);
    }
    private static void check(boolean passed, String label) {
        if (!passed) throw new IllegalStateException(label);
        LogUtils.getLogger().info("[MaidWeapon] Companion fixture: {}", label);
    }
    private ContractCompanionValidation() { }
}

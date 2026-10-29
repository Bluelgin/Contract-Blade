package com.maidweapon.forge.compat.tlm;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.resonance.*;
import com.maidweapon.forge.system.deployment.*;
import com.maidweapon.forge.event.MaidBondCombatHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.UUID;
import static com.maidweapon.forge.system.resonance.ResonanceCostPolicy.Payment.*;

/** Native server fixture with real TLM slots, codec, lifecycle and fake independent abilities. */
@Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID)
public final class ContractResonanceValidation {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.resonance.validation")) return;
        try { run(event); if (!Boolean.getBoolean("contractblade.resonance.resume")) OathValidation.run(event.getServer()); LogUtils.getLogger().info("CONTRACT_RESONANCE_PASS"); }
        catch (Throwable failure) { LogUtils.getLogger().error("CONTRACT_RESONANCE_FAIL", failure); }
        finally { event.getServer().halt(false); }
    }
    private static void run(ServerStartedEvent event) throws Exception {
        var level = event.getServer().overworld();
        var a = net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(
                UUID.fromString("0c9f6240-815b-4766-8056-fa41821320f7"), "ResonanceA"));
        var b = net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(
                UUID.fromString("2505410b-5bd9-4c4d-a1ae-ec9e6fd39f80"), "ResonanceB"));
        if (Boolean.getBoolean("contractblade.resonance.resume")) {
            check(ResonanceProgressService.stage(a) == 3 && ResonanceProgressService.stage(b) == 0,
                    "world restart retains player progression and isolation");
            OathValidation.restart(event.getServer());
            LogUtils.getLogger().info("CONTRACT_RESONANCE_RESTART_PASS"); return;
        }
        // Damage callbacks resolve the owner through the real online-player index.
        var playerIndex = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
        playerIndex.setAccessible(true);
        @SuppressWarnings("unchecked")
        var onlinePlayers = (java.util.Map<UUID, net.minecraft.server.level.ServerPlayer>) playerIndex.get(event.getServer().getPlayerList());
        onlinePlayers.put(a.getUUID(), a);
        a.getInventory().clearContent(); b.getInventory().clearContent();
        check(ResonanceProgressService.upgrade(a, 2) == ResonanceProgressService.Upgrade.SKIPPED, "skip refused");
        var upgrades = java.util.List.of(ModItems.AWAKENING_VEIN.get(), ModItems.FLOWING_VEIN.get(), ModItems.ENDLESS_VEIN.get());
        a.getAbilities().instabuild = false;
        for (int stage = 1; stage <= 3; stage++) {
            var item = upgrades.get(stage - 1); var stack = new ItemStack(item, 3);
            a.getInventory().setItem(0, stack); a.getInventory().selected = 0;
            item.use(level, a, net.minecraft.world.InteractionHand.MAIN_HAND);
            check(ResonanceProgressService.stage(a) == stage && stack.getCount() == 2, "stage unlock consumes one");
            item.use(level, a, net.minecraft.world.InteractionHand.MAIN_HAND);
            check(ResonanceProgressService.stage(a) == stage && stack.getCount() == 2, "repeat and max-stage refuse without consumption");
        }
        a.getInventory().clearContent();
        check(ResonanceProgressService.multiplier(a) == 2 && ResonanceProgressService.multiplier(b) == 1, "player isolation");
        var saved = ResonanceProgressSavedData.get(event.getServer()).save(new CompoundTag());
        check(ResonanceProgressSavedData.load(saved).stage(a.getUUID()) == 3, "saved data reload");
        var future = saved.copy(); future.putInt("SchemaVersion", 99);
        var unknown = ResonanceProgressSavedData.load(future);
        check(!unknown.upgrade(a.getUUID(), 1, 3) && unknown.save(new CompoundTag()).equals(future), "future schema preserved");
        var first = create(a, 0); var second = create(a, 1);
        String binding = ContractCarrierData.getBindingId(first);
        check(ResonanceService.recover(a, first, binding, 1), "new contracts inherit owner progression");
        set(a, first, 0);
        advance(a, 20);
        check(value(a, first) == 5 && value(a, second) == 5, "stored zero recovery global multiplier and separate contracts");
        check(com.maidweapon.forge.system.InfusedMaidDeploymentSystem.isEligibleWeapon(first, a), "normal contract eligible before waiver");
        ItemStack duplicate = first.copy(); a.getInventory().setItem(2, duplicate);
        check(ResonanceService.canPay(a, first, binding, 1) == UNAUTHORIZED, "duplicate binding rejected");
        advance(a, 20); check(value(a, first) == 5, "duplicate reference cannot recover"); a.getInventory().setItem(2, ItemStack.EMPTY);
        check(ResonanceService.canPay(a, null, binding, 0) == UNAUTHORIZED && !ResonanceService.waived(null, null), "null/invalid contexts fail closed");
        check(ResonanceService.canPay(b, first, binding, 0) == UNAUTHORIZED, "foreign player rejected");
        check(ResonanceService.canPay(a, first.copy(), binding, 0) == UNAUTHORIZED, "detached copy rejected");
        check(ResonanceService.canPay(a, first, binding, 20) == INSUFFICIENT, "insufficient mock ability");
        var operation = ResonanceService.newAttempt();
        check(ResonanceService.pay(a, first, binding, 1, operation) == PAID, "normal mock ability");
        check(ResonanceService.pay(a, first, binding, 1, operation) == ALREADY_PAID && value(a, first) == 4, "one operation once");
        var payload = ContractMaidStorage.read(a, first);
        var inventory = new net.minecraftforge.items.ItemStackHandler(30);
        inventory.setStackInSlot(0, new ItemStack(ModItems.ENDLESS_CONTRACT.get()));
        payload.put("MaidBaubleInventory", inventory.serializeNBT());
        check(ContractMaidStorage.commit(a, first, payload), "commit equipped native slot");
        set(a, first, 0);
        check(ResonanceService.waived(a, first), "stored waiver at zero");
        check(ResonanceService.pay(a, first, binding, 200, ResonanceService.newAttempt()) == WAIVED, "waived mock ability");
        check(!ResonanceService.waived(a, second), "another maid cannot borrow waiver");
        check(com.maidweapon.forge.system.InfusedMaidDeploymentSystem.isEligibleWeapon(first, a), "zero resonance eligibility");
        check(ContractLifecycleService.manifest(a, first, false), "manifest waived zero contract");
        var maid = (Mob) ContractWeaponLocator.findManifestedMaid(a, ContractCarrierData.getBoundMaidUUID(first));
        check(maid != null && ResonanceService.waived(a, first), "live native waiver");
        advance(a, 20); check(value(a, first) == 40, "manifested idle recovery times global multiplier");
        set(a, first, 0);
        // TLM reads ATTACK_TARGET from its Brain rather than Mob's legacy target field.
        maid.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,
                new net.minecraft.world.entity.monster.Zombie(level));
        check(maid.getTarget() != null, "native brain has a real combat target");
        advance(a, 20); check(value(a, first) == 20, "actual maid combat target reduces recovery");
        maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET); set(a, first, 0);
        ResonanceService.drain(a, first, 20); check(value(a, first) == 0, "zero live mandatory upkeep waived");
        check(TlmProjectionBaubles.mode(maid) == ContractProjectionMode.COMBINED, "ultimate inherits combined projection");
        var slots = (IItemHandlerModifiable) maid.getClass().getMethod("getMaidBauble").invoke(maid);
        check(slots.isItemValid(0, new ItemStack(ModItems.ENDLESS_CONTRACT.get())), "ultimate accepted by native bauble slot");
        slots.setStackInSlot(0, ItemStack.EMPTY);
        a.getInventory().setItem(2, new ItemStack(ModItems.ENDLESS_CONTRACT.get()));
        check(!ResonanceService.waived(a, first), "removal immediate; player inventory is not a bauble slot");
        check(ResonanceService.canPay(a, first, binding, 1) == INSUFFICIENT, "normal costs after removal");
        check(com.maidweapon.forge.system.InfusedMaidDeploymentSystem.isEligibleWeapon(first, a), "unwaived zero resonance remains eligible");
        a.tickCount = 20;
        ContractCompanionService.tick(a);
        check(!com.maidweapon.forge.item.MaidInfusion.containsMaid(first), "zero resonance does not recall live maid");
        var attacker = new net.minecraft.world.entity.monster.Zombie(level);
        maid.setHealth(10);
        var injury = new net.minecraftforge.event.entity.living.LivingDamageEvent(maid, maid.damageSources().mobAttack(attacker), 2);
        MaidBondCombatHandler.onDamage(injury);
        check(!injury.isCanceled() && value(a, first) == 0, "ordinary injury never costs resonance or recalls at zero");
        set(a, first, 123);
        var lethal = new net.minecraftforge.event.entity.living.LivingDamageEvent(maid, maid.damageSources().mobAttack(attacker), 100);
        MaidBondCombatHandler.onDamage(lethal);
        check(lethal.isCanceled() && com.maidweapon.forge.item.MaidInfusion.containsMaid(first)
                && value(a, first) == 123, "unwaived near-death recall retains all resonance");
        check(ContractMaidStorage.read(a, first).getFloat("Health") == 1.0f, "protective recall does not heal stored maid");
        check(!com.maidweapon.forge.system.InfusedMaidDeploymentSystem.isEligibleWeapon(first, a), "protective recall retains cooldown");
        check(ContractLifecycleService.manifest(a, first, false), "fixture remanifest to exercise death fallback");
        maid = (Mob) ContractWeaponLocator.findManifestedMaid(a, ContractCarrierData.getBoundMaidUUID(first));
        slots = (IItemHandlerModifiable) maid.getClass().getMethod("getMaidBauble").invoke(maid);
        slots.setStackInSlot(0, new ItemStack(ModItems.ENDLESS_CONTRACT.get()));
        check(MaidBondCombatHandler.protectContractDeath(a, maid) == MaidBondCombatHandler.DeathProtection.RECALLED,
                "waiver never cancels lethal safety recall");
        check(ResonanceService.waived(a, first), "recall keeps real native slots");
        payload = ContractMaidStorage.read(a, first); payload.put("MaidBaubleInventory", new net.minecraftforge.items.ItemStackHandler(30).serializeNBT());
        check(ContractMaidStorage.commit(a, first, payload) && !ResonanceService.waived(a, first), "stored cache invalidates after equipment change");
        set(a, first, 100);
        for (int target = 0; target < 8; target++)
            check(ResonanceAttackCosts.pay(a, first, SpiritAbilityResonanceCosts.Action.RED_SPACE_CUT, 10), "multi-target shared cut window");
        check(value(a, first) == 98, "eight targets pay only one cut fee");
        a.getInventory().selected = 1;
        check(ResonanceAttackCosts.pay(a, first, SpiritAbilityResonanceCosts.Action.RED_SPACE_CUT, 10)
                && value(a, first) == 98, "switching hand keeps funded window");
        check(!ResonanceAttackCosts.pay(b, first, SpiritAbilityResonanceCosts.Action.RED_SPACE_CUT, 10), "foreign owner cannot borrow paid window");
        advance(a, 10);
        check(ResonanceAttackCosts.pay(a, first, SpiritAbilityResonanceCosts.Action.RED_SPACE_CUT, 10)
                && value(a, first) == 96, "next cut window pays exactly once");
        a.getInventory().selected = 0;
        var archived = ContractChannelStorage.captureContract(second);
        ContractChannelStorage.clearContract(second); second.getOrCreateTag().put("MaidWeaponExternalContract", archived);
        check(ResonanceService.canPay(a, second, archived.getString("MaidBindingId"), 0) == PAID, "independent archived channel");
        // Activity is deliberately reported by a mock ability, with no three-fox dependency.
        ResonanceRecoveryEvents.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(a));
        set(a, first, 0);
        check(ResonanceService.reportActivity(a, first, binding, "mock_domain", ResonanceActivity.ABILITY_ATTACKING, 100), "server activity report");
        advance(a, 20); check(value(a, first) == 0, "stored combat ability has no natural recovery");
        ResonanceRecoveryEvents.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(a));
        set(a, first, 0);
        check(ResonanceService.reportActivity(a, first, binding, "mock_idle", ResonanceActivity.ABILITY_IDLE, 100), "idle domain report");
        advance(a, 20); check(value(a, first) == 5, "idle ability natural recovery");
        int oldMaximum = com.maidweapon.common.ContractRulesConfig.POOL_MAX_RESONANCE.get();
        set(a, first, 100);
        com.maidweapon.common.ContractRulesConfig.POOL_MAX_RESONANCE.set(80);
        advance(a, 20); check(value(a, first) == 80
                && first.getTag().getCompound("MaidData").getInt("ContractResonance") != 80, "lowered cap persisted");
        com.maidweapon.common.ContractRulesConfig.POOL_MAX_RESONANCE.set(oldMaximum);
        advance(a, 1); // one actual tick never pays elapsed/offline time
        check(value(a, first) <= 81, "no elapsed time catch-up");
        first.getTag().getCompound("MaidData").putInt("ContractSchema", 99);
        check(ResonanceService.canPay(a, first, binding, 0) == UNAUTHORIZED, "future contract schema read-only");
        first.getTag().getCompound("MaidData").putInt("ContractSchema", 2);
        ContractCarrierData.setContractSuperseded(first, true);
        check(ResonanceService.canPay(a, first, binding, 0) == UNAUTHORIZED, "superseded rejected");
        for (String name : new String[]{"spirit_crystal", "awakening_vein", "flowing_vein", "endless_vein", "endless_contract"})
            check(level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("maid_weapon", "altar/" + name)).isPresent(), "altar recipe loaded");
    }
    private static ItemStack create(Player owner, int slot) throws Exception {
        var maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(owner.level());
        TlmEntityAdapter.tame(maid, owner); maid.moveTo(slot * 3, 100, 0, 0, 0);
        check(owner.level().addFreshEntity(maid), "spawn maid");
        var carrier = new ItemStack(ModItems.MAID_SWORD.get()); owner.getInventory().setItem(slot, carrier);
        check(ContractLifecycleService.capture(owner, maid, carrier, false), "capture maid"); set(owner, carrier, 0); return carrier;
    }
    private static void advance(Player owner, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            var level = owner.getServer().overworld();
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 1);
            var event = new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner);
            ResonanceRecoveryEvents.tick(event); ResonanceRecoveryEvents.tick(event);
        }
    }
    private static int value(Player owner, ItemStack carrier) {
        return ResonancePoolSavedData.get(owner.getServer()).save(new CompoundTag())
                .getCompound("Players").getCompound(owner.getUUID().toString())
                .getCompound(ContractCarrierData.getBoundMaidUUID(carrier)).getInt("Current");
    }
    private static void set(Player owner, ItemStack carrier, int amount) {
        check(ResonanceService.set(owner, carrier, ContractCarrierData.getBindingId(carrier), amount),
                "server-owned pool adjustable");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private ContractResonanceValidation() { }
}

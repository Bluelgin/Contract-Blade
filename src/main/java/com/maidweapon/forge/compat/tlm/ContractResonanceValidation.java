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
        try { run(event); LogUtils.getLogger().info("CONTRACT_RESONANCE_PASS"); }
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
            LogUtils.getLogger().info("CONTRACT_RESONANCE_RESTART_PASS"); return;
        }
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
        set(first, 0);
        advance(a, 20);
        check(value(first) == 2 && value(second) == 2, "stored zero recovery global multiplier and separate contracts");
        ItemStack duplicate = first.copy(); a.getInventory().setItem(2, duplicate);
        check(ResonanceService.canPay(a, first, binding, 1) == UNAUTHORIZED, "duplicate binding rejected");
        advance(a, 20); check(value(first) == 2, "duplicate reference cannot recover"); a.getInventory().setItem(2, ItemStack.EMPTY);
        check(ResonanceService.canPay(b, first, binding, 0) == UNAUTHORIZED, "foreign player rejected");
        check(ResonanceService.canPay(a, first.copy(), binding, 0) == UNAUTHORIZED, "detached copy rejected");
        check(ResonanceService.canPay(a, first, binding, 20) == INSUFFICIENT, "insufficient mock ability");
        var operation = ResonanceService.newAttempt();
        check(ResonanceService.pay(a, first, binding, 1, operation) == PAID, "normal mock ability");
        check(ResonanceService.pay(a, first, binding, 1, operation) == ALREADY_PAID && value(first) == 1, "one operation once");
        var payload = ContractMaidStorage.read(a, first);
        var inventory = new net.minecraftforge.items.ItemStackHandler(30);
        inventory.setStackInSlot(0, new ItemStack(ModItems.ENDLESS_CONTRACT.get()));
        payload.put("MaidBaubleInventory", inventory.serializeNBT());
        check(ContractMaidStorage.commit(a, first, payload), "commit equipped native slot");
        set(first, 0);
        check(ResonanceService.waived(a, first), "stored waiver at zero");
        check(ResonanceService.pay(a, first, binding, 200, ResonanceService.newAttempt()) == WAIVED, "waived mock ability");
        check(!ResonanceService.waived(a, second), "another maid cannot borrow waiver");
        check(com.maidweapon.forge.system.InfusedMaidDeploymentSystem.isEligibleWeapon(first, a), "zero resonance eligibility");
        check(ContractLifecycleService.manifest(a, first, false), "manifest waived zero contract");
        var maid = (Mob) ContractWeaponLocator.findManifestedMaid(a, ContractCarrierData.getBoundMaidUUID(first));
        check(maid != null && ResonanceService.waived(a, first), "live native waiver");
        check(TlmProjectionBaubles.mode(maid) == ContractProjectionMode.COMBINED, "ultimate inherits combined projection");
        var slots = (IItemHandlerModifiable) maid.getClass().getMethod("getMaidBauble").invoke(maid);
        slots.setStackInSlot(0, ItemStack.EMPTY);
        a.getInventory().setItem(2, new ItemStack(ModItems.ENDLESS_CONTRACT.get()));
        check(!ResonanceService.waived(a, first), "removal immediate; player inventory is not a bauble slot");
        check(ResonanceService.canPay(a, first, binding, 1) == INSUFFICIENT, "normal costs after removal");
        slots.setStackInSlot(0, new ItemStack(ModItems.ENDLESS_CONTRACT.get()));
        check(MaidBondCombatHandler.protectContractDeath(a, maid) == MaidBondCombatHandler.DeathProtection.RECALLED,
                "waiver never cancels lethal safety recall");
        check(ResonanceService.waived(a, first), "recall keeps real native slots");
        payload = ContractMaidStorage.read(a, first); payload.put("MaidBaubleInventory", new net.minecraftforge.items.ItemStackHandler(30).serializeNBT());
        check(ContractMaidStorage.commit(a, first, payload) && !ResonanceService.waived(a, first), "stored cache invalidates after equipment change");
        var archived = ContractChannelStorage.captureContract(second);
        ContractChannelStorage.clearContract(second); second.getOrCreateTag().put("MaidWeaponExternalContract", archived);
        check(ResonanceService.canPay(a, second, archived.getString("MaidBindingId"), 0) == PAID, "independent archived channel");
        // Activity is deliberately reported by a mock ability, with no three-fox dependency.
        ResonanceRecoveryEvents.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(a));
        set(first, 0);
        check(ResonanceService.reportActivity(a, first, binding, "mock_domain", ResonanceActivity.ABILITY_ATTACKING, 100), "server activity report");
        advance(a, 20); check(value(first) == 1, "combat ability recovers at half rate times global multiplier");
        ResonanceRecoveryEvents.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(a));
        set(first, 0);
        check(ResonanceService.reportActivity(a, first, binding, "mock_idle", ResonanceActivity.ABILITY_IDLE, 100), "idle domain report");
        advance(a, 20); check(value(first) == 2, "idle ability natural recovery");
        int oldMaximum = com.maidweapon.common.ContractRulesConfig.MAX_RESONANCE.get();
        set(first, 100);
        com.maidweapon.common.ContractRulesConfig.MAX_RESONANCE.set(80);
        advance(a, 20); check(value(first) == 80 && first.getTag().getCompound("MaidData").getInt("ContractResonance") == 80, "lowered cap persisted");
        com.maidweapon.common.ContractRulesConfig.MAX_RESONANCE.set(oldMaximum);
        advance(a, 1); // one actual tick never pays elapsed/offline time
        check(value(first) <= 81, "no elapsed time catch-up");
        ContractCarrierData.setContractSuperseded(first, true);
        check(ResonanceService.canPay(a, first, binding, 0) == UNAUTHORIZED, "superseded rejected");
        for (String name : new String[]{"awakening_vein", "flowing_vein", "endless_vein", "endless_contract"})
            check(level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("maid_weapon", "altar/" + name)).isPresent(), "altar recipe loaded");
    }
    private static ItemStack create(Player owner, int slot) throws Exception {
        var maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(owner.level());
        TlmEntityAdapter.tame(maid, owner); maid.moveTo(slot * 3, 100, 0, 0, 0);
        check(owner.level().addFreshEntity(maid), "spawn maid");
        var carrier = new ItemStack(ModItems.MAID_SWORD.get()); owner.getInventory().setItem(slot, carrier);
        check(ContractLifecycleService.capture(owner, maid, carrier, false), "capture maid"); set(carrier, 0); return carrier;
    }
    private static void advance(Player owner, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            var level = owner.getServer().overworld();
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 1);
            var event = new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner);
            ResonanceRecoveryEvents.tick(event); ResonanceRecoveryEvents.tick(event);
        }
    }
    private static int value(ItemStack carrier) { return ContractCarrierData.getMaidData(carrier).getResonance(); }
    private static void set(ItemStack carrier, int amount) { var data = ContractCarrierData.getMaidData(carrier); data.setResonance(amount); ContractCarrierData.setMaidData(carrier, data); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private ContractResonanceValidation() { }
}

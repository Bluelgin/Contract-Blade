package com.maidweapon.forge.compat.tlm;

import com.maidweapon.common.ContractRulesConfig;
import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.system.deployment.ContractActiveDeployments;
import com.maidweapon.forge.system.deployment.ContractCompanionService;
import com.maidweapon.forge.system.deployment.ContractCompanionState;
import com.maidweapon.forge.system.deployment.ContractRecoveryService;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Real TLM entities; no reflection into deployment bookkeeping and no player's save. */
final class ContractMultiCompanionValidation {
    static void run(MinecraftServer server) throws ReflectiveOperationException {
        var level = server.overworld();
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString(
                        "86fa51ca-e34c-4024-980f-7aeab297c10e"), "MultiCompanionFixture"));
        int originalLimit = ContractRulesConfig.MAX_FOLLOWERS.get();
        boolean originalAuto = ContractRulesConfig.AUTO_MANIFEST.get();
        owner.getInventory().clearContent(); owner.moveTo(4.5, 100, 4.5, 0, 0);
        owner.setHealth(20); owner.tickCount = 500;
        var a = captured(owner, 1); // B precedes A in inventory: existing follower gets priority.
        var b = captured(owner, 0);
        var c = captured(owner, 2);
        String aId = ContractCarrierData.getBoundMaidUUID(a), bId = ContractCarrierData.getBoundMaidUUID(b);
        try {
            ContractRulesConfig.MAX_FOLLOWERS.set(1);
            call(owner, 1); Mob maidA = maid(owner, aId);
            home(maidA, true); tick(owner);
            var residentDrop = new ItemEntity(level, owner.getX(), owner.getY(), owner.getZ(), a);
            InfusedMaidDeploymentSystem.onContractToss(new net.minecraftforge.event.entity.item.ItemTossEvent(residentDrop, owner));
            check(!MaidInfusion.containsMaid(a) && maid(owner, aId) == maidA, "toss cannot unexpectedly recall a resident");
            call(owner, 0); Mob maidB = maid(owner, bId);
            check(maidB != null, "resident A allows B at limit one");
            home(maidA, false); tick(owner);
            check(TlmResidenceAdapter.isResident(maidA) && !TlmResidenceAdapter.isResident(maidB),
                    "home-mode bypass restores A without displacing earlier-slot B");
            ContractRulesConfig.MAX_FOLLOWERS.set(2);
            home(maidA, false); tick(owner);
            check(!TlmResidenceAdapter.isResident(maidA) && ContractActiveDeployments.snapshot(owner.getUUID()).size() == 2,
                    "limit two accepts two independent following contracts");
            call(owner, 2);
            check(MaidInfusion.containsMaid(c), "third manual summon rejected at limit two");
            ContractRulesConfig.AUTO_MANIFEST.set(true); owner.setHealth(20);
            ContractCompanionService.attacked(owner, new net.minecraft.world.entity.monster.Zombie(level), 2);
            owner.setHealth(18); tick(owner);
            check(MaidInfusion.containsMaid(c), "third automatic summon respects the same limit");
            owner.setHealth(20); ContractRulesConfig.AUTO_MANIFEST.set(false);
            for (var carrier : new ItemStack[]{a, b}) {
                var data = MaidInfusion.data(carrier); data.setResonance(10);
                ContractCarrierData.setMaidData(carrier, data);
            }
            var reward = com.maidweapon.forge.event.MaidBondCombatHandler.class.getDeclaredMethod("rewardCooperation",
                    net.minecraft.world.entity.player.Player.class, long.class, ItemStack.class);
            reward.setAccessible(true);
            reward.invoke(null, owner, level.getGameTime(), a); reward.invoke(null, owner, level.getGameTime(), b);
            int expected = 10 + com.maidweapon.common.MaidWeaponConfig.RESONANCE_COOP_REWARD.get();
            check(MaidInfusion.data(a).getResonance() == expected && MaidInfusion.data(b).getResonance() == expected,
                    "cooperative rewards do not steal another contract's cooldown");
            reward.invoke(null, owner, level.getGameTime(), a);
            check(MaidInfusion.data(a).getResonance() == expected, "same-contract cooperation remains throttled");
            owner.getInventory().setItem(3, a.copy());
            check(ContractCompanionService.canFollow(owner, a), "duplicate carrier does not consume another slot");
            owner.getInventory().setItem(3, ItemStack.EMPTY);

            call(owner, 1);
            check(MaidInfusion.containsMaid(a) && maid(owner, aId) == null && maid(owner, bId) == maidB
                    && ContractActiveDeployments.snapshot(owner.getUUID()).size() == 1,
                    "recalling A preserves B entity and runtime record");
            ContractRulesConfig.AUTO_MANIFEST.set(true);
            owner.getInventory().selected = 1;
            ContractCompanionService.attacked(owner, new net.minecraft.world.entity.monster.Zombie(level), 2);
            owner.setHealth(18); tick(owner);
            check(maid(owner, aId) != null && maid(owner, bId) == maidB
                    && ContractCompanionState.mode(a) == ContractCompanionState.Mode.GUARD,
                    "automatic guard can join B when limit two has a free slot");
            owner.setHealth(20); ContractRulesConfig.AUTO_MANIFEST.set(false);
            check(InfusedMaidDeploymentSystem.forceRecall(owner, aId, 0)
                    && maid(owner, bId) == maidB && ContractActiveDeployments.snapshot(owner.getUUID()).size() == 1,
                    "forced recall of A leaves B tracked");
            call(owner, 1); tick(owner);

            var chest = new net.minecraft.world.SimpleContainer(27);
            chest.setItem(0, a); owner.getInventory().setItem(1, ItemStack.EMPTY);
            owner.containerMenu = net.minecraft.world.inventory.ChestMenu.threeRows(19, owner.getInventory(), chest);
            check(!ContractCompanionService.canFollow(owner, c), "carrier in a container still occupies its tracked follower slot");
            InfusedMaidDeploymentSystem.onContainerClose(new net.minecraftforge.event.entity.player.PlayerContainerEvent.Close(owner, owner.containerMenu));
            check(MaidInfusion.containsMaid(chest.getItem(0)) && maid(owner, bId) == maidB
                    && ContractActiveDeployments.snapshot(owner.getUUID()).size() == 1,
                    "container transfer recalls A without untracking B");
            chest.setItem(0, ItemStack.EMPTY); owner.containerMenu = owner.inventoryMenu;
            owner.getInventory().setItem(1, a); call(owner, 1); tick(owner);
            var drop = new ItemEntity(level, owner.getX(), owner.getY(), owner.getZ(), a);
            owner.getInventory().setItem(1, ItemStack.EMPTY);
            var toss = new net.minecraftforge.event.entity.item.ItemTossEvent(drop, owner);
            InfusedMaidDeploymentSystem.onContractToss(toss);
            check(MaidInfusion.containsMaid(drop.getItem()) && maid(owner, aId) == null && maid(owner, bId) == maidB
                    && ContractActiveDeployments.snapshot(owner.getUUID()).size() == 1,
                    "tossing A recalls only A and keeps B tracked");
            owner.getInventory().setItem(1, a);

            call(owner, 1); tick(owner);
            InfusedMaidDeploymentSystem.onPlayerDeath(new net.minecraftforge.event.entity.living.LivingDeathEvent(owner, owner.damageSources().generic()));
            check(stored(a, b), "death recalls both independent followers");
            call(owner, 0); call(owner, 1); tick(owner);
            InfusedMaidDeploymentSystem.onChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(owner, Level.OVERWORLD, Level.NETHER));
            check(stored(a, b), "dimension change recalls both followers");
            call(owner, 0); call(owner, 1); tick(owner);
            InfusedMaidDeploymentSystem.onLogout(new PlayerEvent.PlayerLoggedOutEvent(owner));
            check(stored(a, b), "logout recalls both followers");

            call(owner, 0); call(owner, 1); tick(owner);
            ContractRulesConfig.MAX_FOLLOWERS.set(1); tick(owner);
            int residents = (TlmResidenceAdapter.isResident(maid(owner, aId)) ? 1 : 0)
                    + (TlmResidenceAdapter.isResident(maid(owner, bId)) ? 1 : 0);
            check(residents == 1 && ContractActiveDeployments.snapshot(owner.getUUID()).size() == 1,
                    "lowering live limit preserves excess maid as resident");
            ContractRulesConfig.MAX_FOLLOWERS.set(2);
            TlmResidenceAdapter.startFollowing(maid(owner, aId));
            TlmResidenceAdapter.startFollowing(maid(owner, bId)); tick(owner);

            // Recovery tickets are contract-keyed; finishing A cannot cancel B.
            check(ContractRecoveryService.start(owner, aId, ContractCarrierData.getBindingId(a), a)
                    == ContractRecoveryService.StartResult.STARTED, "A recovery starts");
            check(ContractRecoveryService.start(owner, bId, ContractCarrierData.getBindingId(b), b)
                    == ContractRecoveryService.StartResult.STARTED, "B recovery queues independently");
            ContractRecoveryService.cancel(owner, aId);
            check(bId.equals(ContractRecoveryService.currentMaidId(owner)), "cancel A recovery preserves B tickets");
            ContractRecoveryService.cancel(owner);
            check(!ContractRecoveryService.hasRecovery(owner), "all recovery tickets released");
            InfusedMaidDeploymentSystem.onLogout(new PlayerEvent.PlayerLoggedOutEvent(owner));
            check(stored(a, b), "both remain recallable after recovery queue cleanup");
            LogUtils.getLogger().info("[MaidWeapon] MULTI_COMPANION_VALIDATION_PASS");
        } finally {
            ContractRulesConfig.MAX_FOLLOWERS.set(originalLimit);
            ContractRulesConfig.AUTO_MANIFEST.set(originalAuto);
            owner.containerMenu = owner.inventoryMenu;
            ContractRecoveryService.cancel(owner);
            for (var carrier : new ItemStack[]{a, b, c}) {
                var entity = InfusedMaidDeploymentSystem.findManifestedMaid(owner, ContractCarrierData.getBoundMaidUUID(carrier));
                if (entity != null) entity.discard();
            }
            ContractActiveDeployments.clear(owner.getUUID()); ContractCompanionService.disconnect(owner);
        }
    }
    private static ItemStack captured(ServerPlayer owner, int slot) throws ReflectiveOperationException {
        Mob maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(owner.level());
        TlmEntityAdapter.tame(maid, owner); maid.moveTo(owner.position()); owner.serverLevel().addFreshEntity(maid);
        var carrier = new ItemStack(ModItems.MAID_SWORD.get()); owner.getInventory().setItem(slot, carrier);
        check(ContractLifecycleService.capture(owner, maid, carrier, false), "capture fixture " + slot);
        return carrier;
    }
    private static Mob maid(ServerPlayer owner, String id) { return (Mob) InfusedMaidDeploymentSystem.findManifestedMaid(owner, id); }
    private static void home(Mob maid, boolean home) throws ReflectiveOperationException {
        maid.getClass().getMethod("setHomeModeEnable", boolean.class).invoke(maid, home);
    }
    private static void tick(ServerPlayer owner) { ContractCompanionService.tick(owner); }
    private static void call(ServerPlayer owner, int slot) {
        owner.getInventory().selected = slot; ContractCompanionService.disconnect(owner); ContractCompanionService.toggle(owner);
    }
    private static boolean stored(ItemStack a, ItemStack b) { return MaidInfusion.containsMaid(a) && MaidInfusion.containsMaid(b); }
    private static void check(boolean value, String label) {
        if (!value) throw new IllegalStateException(label);
        LogUtils.getLogger().info("[MaidWeapon] Multi companion fixture: {}", label);
    }
    private ContractMultiCompanionValidation() { }
}

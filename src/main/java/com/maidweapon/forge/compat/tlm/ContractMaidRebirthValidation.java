package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.event.MaidBondCombatHandler;
import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.system.deployment.ContractCompanionService;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;

/** Opt-in regression fixture using native maids, real menu slots and native blade refinement. */
final class ContractMaidRebirthValidation {
    static void run(MinecraftServer server) throws ReflectiveOperationException {
        var level = server.overworld();
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString(
                        "8c765bfe-6a28-4e85-955a-11c01bda7602"), "RebirthFixture"));
        owner.getInventory().clearContent();
        owner.getInventory().selected = 0;
        owner.setHealth(20);
        owner.containerMenu = owner.inventoryMenu;
        var spawn = level.getSharedSpawnPos();
        owner.moveTo(spawn.getX()+.5, 100, spawn.getZ()+.5, 0, 0);
        var maid = (LivingEntity) TlmEntityAdapter.maidClass().getConstructor(net.minecraft.world.level.Level.class)
                .newInstance(level);
        TlmEntityAdapter.tame(maid, owner);
        maid.setPos(owner.position());
        level.addFreshEntity(maid);
        var carrier = net.minecraftforge.fml.ModList.get().isLoaded("slashblade")
                ? new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.parse("slashblade:slashblade")))
                : new ItemStack(ModItems.MAID_SWORD.get());
        owner.getInventory().setItem(0, carrier);
        check(ContractLifecycleService.capture(owner, maid, carrier, false), "capture native contract");
        String binding = ContractCarrierData.getBindingId(carrier);
        var identity = maid.getUUID();
        CompoundTag storedBeforeRefine = ContractMaidStorage.read(owner, carrier);
        if (net.minecraftforge.fml.ModList.get().isLoaded("slashblade")) {
            var type = Class.forName("mods.flammpfeil.slashblade.event.handler.RefineHandler");
            Object handler = type.getMethod("getInstance").invoke(null);
            var material = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.parse("slashblade:proudsoul_sphere")), 64);
            for (int i = 0; i < 100; i++) {
                var update = new net.minecraftforge.event.AnvilUpdateEvent(carrier, material, "", 0, owner);
                type.getMethod("onAnvilUpdateEvent", net.minecraftforge.event.AnvilUpdateEvent.class).invoke(handler, update);
                if (i == 0) check(!update.getOutput().isEmpty(), "native highest soul produces refinement output");
                if (!update.getOutput().isEmpty()) carrier = update.getOutput();
                check(MaidInfusion.containsMaid(carrier) && binding.equals(ContractCarrierData.getBindingId(carrier))
                        && identity.toString().equals(ContractCarrierData.getBoundMaidUUID(carrier)), "refinement preserves stored soul");
                check(storedBeforeRefine.equals(ContractMaidStorage.read(owner, carrier)), "refinement does not mutate maid health or inventory");
            }
            owner.getInventory().setItem(0, carrier);
        }
        check(ContractLifecycleService.manifest(owner, carrier, false), "manifest refined blade");
        maid = (LivingEntity) level.getEntity(identity);
        var menu = new AnvilMenu(17, owner.getInventory());
        owner.getInventory().setItem(0, ItemStack.EMPTY);
        owner.containerMenu = menu;
        menu.getSlot(0).set(carrier);
        menu.getSlot(2).set(carrier.copy()); // Input and preview legitimately share the contract.
        check(MaidBondCombatHandler.preventContractDeath(owner, maid), "emergency resolves real anvil input");
        check(MaidInfusion.containsMaid(carrier) && level.getEntity(identity) == null, "anvil emergency stores living soul");
        check(menu.getSlot(2).getItem().isEmpty() || MaidInfusion.containsMaid(menu.getSlot(2).getItem()),
                "anvil cannot hand out a stale manifested preview after recall");
        menu.getSlot(0).set(ItemStack.EMPTY); menu.getSlot(2).set(ItemStack.EMPTY);
        owner.containerMenu = owner.inventoryMenu;
        owner.getInventory().setItem(0, carrier);
        check(ContractLifecycleService.manifest(owner, carrier, false), "manifest for legacy film regression");
        maid = (LivingEntity) level.getEntity(identity);
        CompoundTag filmData = TlmEntityAdapter.save(maid);
        maid.discard();
        var revived = (LivingEntity) TlmEntityAdapter.maidClass().getConstructor(net.minecraft.world.level.Level.class)
                .newInstance(level);
        CompoundTag wrongOwner = filmData.copy(); wrongOwner.putUUID("Owner", java.util.UUID.randomUUID());
        check(!ContractMaidRebirthBridge.restoreIdentity(owner, revived, wrongOwner), "reject another owner's film");
        check(ContractMaidRebirthBridge.restoreIdentity(owner, revived, filmData), "restore proven original identity");
        // Exactly the TLM film path: base Entity.load is NOT called.
        revived.getClass().getMethod("readAdditionalSaveData", CompoundTag.class).invoke(revived, filmData);
        revived.setPos(owner.position());
        level.addFreshEntity(revived);
        check(revived.getUUID().equals(identity) && binding.equals(revived.getPersistentData()
                .getString(ContractMaidKeys.ENTITY_BINDING_ID)), "film loading retains UUID and binding");
        var duplicate = (LivingEntity) TlmEntityAdapter.maidClass().getConstructor(net.minecraft.world.level.Level.class)
                .newInstance(level);
        check(!ContractMaidRebirthBridge.restoreIdentity(owner, duplicate, filmData), "reject duplicate live identity");
        check(InfusedMaidDeploymentSystem.recallRequested(owner, carrier), "revived maid can be recalled");
        check(MaidInfusion.containsMaid(carrier), "recall removes stale following state");
        if (net.minecraftforge.fml.ModList.get().isLoaded("slashblade")) {
            check(ContractLifecycleService.manifest(owner, carrier, false), "manifest for stand emergency");
            maid = (LivingEntity) level.getEntity(identity);
            owner.getInventory().setItem(0, ItemStack.EMPTY);
            var stand = new ItemFrame(level, owner.blockPosition().offset(2,0,0), net.minecraft.core.Direction.NORTH);
            stand.setItem(carrier); level.addFreshEntity(stand);
            check(MaidBondCombatHandler.preventContractDeath(owner, maid), "stand carrier resolves by exact binding");
            check(MaidInfusion.containsMaid(stand.getItem()), "stand emergency republishes stored soul");
            carrier = stand.getItem().copy(); stand.discard(); owner.getInventory().setItem(0, carrier);
        }
        var other = (LivingEntity) TlmEntityAdapter.maidClass().getConstructor(net.minecraft.world.level.Level.class)
                .newInstance(level);
        TlmEntityAdapter.tame(other, owner); other.setPos(owner.position()); level.addFreshEntity(other);
        var second = new ItemStack(ModItems.MAID_SWORD.get());
        owner.getInventory().setItem(1, second);
        check(ContractLifecycleService.capture(owner, other, second, false), "capture second contract");
        owner.getInventory().selected = 1;
        ContractCompanionService.disconnect(owner);
        ContractCompanionService.toggle(owner);
        check(!MaidInfusion.containsMaid(second), "another companion is no longer blocked");
        InfusedMaidDeploymentSystem.recallRequested(owner, second);
        ContractCompanionService.disconnect(owner);
        owner.getInventory().clearContent();
        LogUtils.getLogger().info("CONTRACT_REBIRTH_PASS: 100 native refinements, anvil/stand emergency, film identity, duplicate rejection and second companion");
    }
    private static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
    private ContractMaidRebirthValidation() { }
}

package com.maidweapon.forge.system.mastery;

import com.maidweapon.common.ContractRulesConfig;
import com.maidweapon.forge.api.ContractGrowthAttributes;
import com.maidweapon.forge.compat.tlm.TlmEntityAdapter;
import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.deployment.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;

/** Real native slots, codec and Forge damage; disposable resonance fixture only. */
public final class ContractSupportBaubleValidation {
    public static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.fromString("e2e00705-dced-4e18-860d-596cd7c1f5b8"), "SupportBaubles"));
        owner.getInventory().clearContent(); owner.getInventory().selected = 0; owner.moveTo(0, 100, 0, 0, 0);
        owner.setHealth(20);
        var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var online = (Map<UUID, ServerPlayer>) field.get(server.getPlayerList());
        online.put(owner.getUUID(), owner);
        String oldMode = ContractRulesConfig.AUTO_MANIFEST_MODE.get(); boolean oldAuto = ContractRulesConfig.AUTO_MANIFEST.get();
        var maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(level);
        TlmEntityAdapter.tame(maid, owner); TlmEntityAdapter.setFavorability(maid, 384);
        maid.moveTo(1, 100, 0, 0, 0); level.addFreshEntity(maid);
        var slots = (IItemHandlerModifiable) maid.getClass().getMethod("getMaidBauble").invoke(maid);
        var carrier = new ItemStack(ModItems.MAID_SWORD.get()); owner.getInventory().setItem(0, carrier);
        var target = new Zombie(level); target.moveTo(3, 100, 0, 0, 0);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        target.setHealth(200); level.addFreshEntity(target);
        try {
            var mastery = new com.maidweapon.common.data.ContractMasteryData(); mastery.setLevel(10);
            ContractMasterySavedData.get(server).write(owner.getUUID(), mastery);
            check(ContractLifecycleService.capture(owner, maid, carrier, false), "capture without accessories");
            near(ContractGrowthAttributes.resolve(owner, carrier).additiveBonus(), 0, "no emblem no bonus");
            check(ContractLifecycleService.manifest(owner, carrier, false), "open native slots");
            maid = (Mob) ContractWeaponLocator.findManifestedMaid(owner, ContractCarrierData.getBoundMaidUUID(carrier));
            slots = (IItemHandlerModifiable) maid.getClass().getMethod("getMaidBauble").invoke(maid);
            check(slots.isItemValid(0, new ItemStack(ModItems.CONTRACT_EMBLEM.get()))
                    && slots.isItemValid(1, new ItemStack(ModItems.GUARD_KNOT.get())), "native bauble registration");
            slots.setStackInSlot(0, new ItemStack(ModItems.CONTRACT_EMBLEM.get()));
            slots.setStackInSlot(1, new ItemStack(ModItems.CONTRACT_EMBLEM.get()));
            slots.setStackInSlot(2, new ItemStack(ModItems.GUARD_KNOT.get()));
            near(ContractGrowthAttributes.resolve(owner, carrier).additiveBonus(), 0, "manifested pauses growth");
            check(ContractLifecycleService.capture(owner, maid, carrier, false), "store native accessories");
            near(ContractGrowthAttributes.resolve(owner, carrier).additiveBonus(), 15, "full growth affection, duplicate does not stack");
            near(ContractGrowthAttributes.resolve(owner, carrier.copy()).additiveBonus(), 0, "inventory copy has no authority");
            float before = target.getHealth();
            check(target.hurt(level.damageSources().playerAttack(owner), 5), "native hit accepted");
            near(before - target.getHealth(), 20, "real Forge hit gains fifteen once");
            var repeat = new LivingHurtEvent(target, level.damageSources().playerAttack(owner), 5);
            ContractBaubleCombat.hurt(repeat); near(repeat.getAmount(), 5, "rapid/multi hit shares window");
            var clock = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
            clock.setGameTime(level.getGameTime() + ContractRulesConfig.GROWTH_HIT_WINDOW.get());
            var shot = new Arrow(level, owner); ContractBaubleCombat.stamp(shot, owner, carrier);
            owner.getInventory().selected = 8;
            var delayed = new LivingHurtEvent(target, level.damageSources().arrow(shot, owner), 5);
            ContractBaubleCombat.hurt(delayed); near(delayed.getAmount(), 20, "verified projectile retains original carrier after switching hands");
            clock.setGameTime(level.getGameTime() + ContractRulesConfig.GROWTH_HIT_WINDOW.get());
            shot.setOwner(target);
            var rebound = new LivingHurtEvent(target, level.damageSources().arrow(shot, owner), 5);
            ContractBaubleCombat.hurt(rebound); near(rebound.getAmount(), 5, "changed projectile owner cannot borrow growth");
            owner.getInventory().selected = 0;
            ContractRulesConfig.AUTO_MANIFEST_MODE.set("disabled");
            check(!ContractGuardPolicy.allows(owner, carrier), "disabled overrides accessory");
            ContractRulesConfig.AUTO_MANIFEST_MODE.set("migrate"); ContractRulesConfig.AUTO_MANIFEST.set(true);
            check(ContractRulesConfig.autoManifestMode().equals("always"), "legacy enabled preserves behavior");
            ContractRulesConfig.AUTO_MANIFEST.set(false);
            check(ContractRulesConfig.autoManifestMode().equals("bauble"), "new default requires accessory");
            check(ContractGuardPolicy.allows(owner, carrier), "stored guard grants permission");
            com.maidweapon.forge.system.resonance.ResonanceService.set(owner, carrier, ContractCarrierData.getBindingId(carrier), 100);
            ContractCompanionService.attacked(owner, target, 2); owner.setHealth(18); ContractCompanionService.tick(owner);
            check(!com.maidweapon.forge.item.MaidInfusion.containsMaid(carrier), "real injury manifests guard companion");
            near(ContractGrowthAttributes.resolve(owner, carrier).additiveBonus(), 0, "automatic guard pauses weapon growth");
            check(!com.maidweapon.forge.system.oath.OathService.active(owner), "guard never starts complete manifestation");
            com.mojang.logging.LogUtils.getLogger().info("SUPPORT_BAUBLES_NATIVE_PASS");
        } finally {
            ContractRulesConfig.AUTO_MANIFEST_MODE.set(oldMode); ContractRulesConfig.AUTO_MANIFEST.set(oldAuto);
            var live = ContractWeaponLocator.findManifestedMaid(owner, ContractCarrierData.getBoundMaidUUID(carrier));
            if (live != null) ContractLifecycleService.capture(owner, live, carrier, false);
            ContractCompanionService.disconnect(owner); online.remove(owner.getUUID()); target.discard();
        }
    }
    private static void near(float actual, float expected, String label) { check(Math.abs(actual - expected) < .01, label + " actual=" + actual); }
    private static void check(boolean value, String label) { if (!value) throw new IllegalStateException("Support bauble fixture: " + label); }
    private ContractSupportBaubleValidation() { }
}

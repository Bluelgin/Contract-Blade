package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.system.oath.*;
import com.maidweapon.forge.system.resonance.*;
import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.deployment.*;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.event.MaidBondCombatHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;

/** Extends the existing disposable resonance fixture. Real TLM storage, slots and Forge damage.
 * FakePlayer is used for deterministic server behavior; this is not client gameplay acceptance.
 */
public final class OathValidation {
    public static void restart(MinecraftServer server) {
        var state = OathSavedData.get(server).session(UUID.fromString("07a4f283-201e-488e-a0e8-d9269fe65dcd"));
        check(!state.getBoolean("Active") && state.getString("LastOutcome").equals("fixture_disconnect"),
                "real server restart retains finished session without replay");
        com.mojang.logging.LogUtils.getLogger().info("OATH_RESTART_PASS");
    }
    public static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.fromString("07a4f283-201e-488e-a0e8-d9269fe65dcd"), "OathFixture"));
        var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var online = (Map<UUID, ServerPlayer>) field.get(server.getPlayerList());
        online.put(owner.getUUID(), owner);
        owner.getInventory().clearContent(); owner.getInventory().selected = 5;
        owner.getAbilities().instabuild = false; owner.setHealth(owner.getMaxHealth());
        owner.moveTo(0, 100, 0, 0, 0);
        try {
            var blade = create(owner, 0);
            String binding = ContractCarrierData.getBindingId(blade);
            check(ContractLifecycleService.manifest(owner, blade, false), "native manifestation");
            var maid = (LivingEntity) ContractWeaponLocator.findManifestedMaid(owner, ContractCarrierData.getBoundMaidUUID(blade));
            var slots = (IItemHandlerModifiable) maid.getClass().getMethod("getMaidBauble").invoke(maid);
            slots.setStackInSlot(0, new ItemStack(ModItems.OATH_RING.get()));
            slots.setStackInSlot(1, new ItemStack(ModItems.ENDLESS_CONTRACT.get()));
            check(!ResonanceService.waived(owner, blade), "ring-first disables waiver");
            set(owner, blade, 0); OathService.activate(owner); check(!OathService.active(owner), "zero rejected");
            set(owner, blade, 1); OathService.activate(owner); check(OathService.active(owner), "one accepted without hand requirement");
            check(!InfusedMaidDeploymentSystem.recallRequested(owner, blade), "manual recall locked");
            check(!ContractCompanionService.canFollow(owner, blade), "switching companions locked");
            slots.setStackInSlot(0, ItemStack.EMPTY); slots.setStackInSlot(1, ItemStack.EMPTY);
            check(OathService.active(owner), "removing ring retains session");
            check(!ResonanceService.recover(owner, blade, binding, 500), "all positive recovery rejected");
            tick(owner); check(!OathService.active(owner), "one point burns out");
            check(owner.getEffect(OathEffects.BACKLASH.get()).getAmplifier() == 0, "first burnout I");
            check(com.maidweapon.forge.item.MaidInfusion.containsMaid(blade), "burnout safely recalled");
            tick(owner); check(owner.getEffect(OathEffects.BACKLASH.get()).getAmplifier() == 0, "no duplicate settlement");
            check(!owner.getEffect(OathEffects.BACKLASH.get()).isCurativeItem(new ItemStack(Items.MILK_BUCKET)), "native milk immunity");
            owner.removeEffect(OathEffects.BACKLASH.get());
            check(ContractLifecycleService.manifest(owner, blade, false), "second manifestation");
            maid = (LivingEntity) ContractWeaponLocator.findManifestedMaid(owner, ContractCarrierData.getBoundMaidUUID(blade));
            slots = (IItemHandlerModifiable) maid.getClass().getMethod("getMaidBauble").invoke(maid);
            slots.setStackInSlot(0, new ItemStack(ModItems.ENDLESS_CONTRACT.get()));
            slots.setStackInSlot(1, new ItemStack(ModItems.OATH_RING.get()));
            set(owner, blade, ResonanceService.maximum()); OathService.activate(owner);
            check(!OathService.active(owner) && ResonanceService.waived(owner, blade), "endless-first excludes activation");
            slots.setStackInSlot(0, new ItemStack(ModItems.OATH_RING.get()));
            slots.setStackInSlot(1, new ItemStack(ModItems.ENDLESS_CONTRACT.get()));
            OathService.activate(owner); check(OathService.active(owner), "full activation");
            maid.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            float hp = maid.getHealth(); int durability = maid.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
            check(!maid.hurt(level.damageSources().generic(), 1000), "real hurt rejected before defenses");
            check(maid.getHealth() == hp && maid.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == durability,
                    "health and armor unchanged; no protective recall");
            check(OathService.empowered(maid), "still present after damage");
            Zombie target = new Zombie(level); target.moveTo(1, 100, 0, 0, 0); level.addFreshEntity(target);
            var attack = new LivingHurtEvent(target, level.damageSources().playerAttack(owner), 10);
            OathCombat.hurt(attack); near(attack.getAmount(), 12, "all-weapon player damage");
            var maidAttack = new LivingHurtEvent(target, level.damageSources().mobAttack(maid), 10);
            OathCombat.hurt(maidAttack); near(maidAttack.getAmount(), 13, "maid damage");
            // Feed the existing effective-hit callback, then examine the second attack's amount.
            MaidBondCombatHandler.onDamage(new LivingDamageEvent(target, level.damageSources().playerAttack(owner), 1));
            var coop = new LivingHurtEvent(target, level.damageSources().mobAttack(maid), 10);
            OathCombat.hurt(coop); near(coop.getAmount(), 14.95f, "same-target cooperation");
            var spam = new LivingHurtEvent(target, level.damageSources().mobAttack(maid), 10);
            OathCombat.hurt(spam); near(spam.getAmount(), 13, "per-target frequency limit");
            var unknown = new LivingHurtEvent(target, level.damageSources().generic(), 10);
            OathCombat.hurt(unknown); near(unknown.getAmount(), 10, "environment has no attacker bonus");
            var incoming = new LivingHurtEvent(owner, level.damageSources().generic(), 10);
            OathCombat.hurt(incoming); near(incoming.getAmount(), 9, "player defense");
            var second = create(owner, 1);
            check(ResonanceService.recover(owner, second, ContractCarrierData.getBindingId(second), 1), "other pool recovers");
            int maximum = ResonanceService.maximum();
            for (int i = 0; i < 799; i++) tick(owner);
            check(OathService.active(owner), "full burn lasts through tick 799");
            tick(owner); check(!OathService.active(owner) && value(owner, blade) == 0, "full pool 40 second burnout");
            check(owner.getEffect(OathEffects.BACKLASH.get()).getAmplifier() == 0, "first remaining burnout I");
            // A different maid burns out while the player's native backlash is still present.
            check(ContractLifecycleService.manifest(owner, second, false), "different companion deploys");
            var secondMaid = ContractWeaponLocator.findManifestedMaid(owner, ContractCarrierData.getBoundMaidUUID(second));
            var secondSlots = (IItemHandlerModifiable) secondMaid.getClass().getMethod("getMaidBauble").invoke(secondMaid);
            secondSlots.setStackInSlot(0, new ItemStack(ModItems.OATH_RING.get()));
            set(owner, second, 1); OathService.activate(owner); tick(owner);
            check(owner.getEffect(OathEffects.BACKLASH.get()).getAmplifier() == 1, "different maid stacks backlash II");
            check(owner.getEffect(OathEffects.BACKLASH.get()).getDuration() == 3600, "native duration refreshed");
            var output = new LivingHurtEvent(target, level.damageSources().playerAttack(owner), 10);
            OathCombat.hurt(output); near(output.getAmount(), 8, "backlash outgoing II");
            owner.removeEffect(OathEffects.BACKLASH.get());
            check(ContractLifecycleService.manifest(owner, second, false), "interruption deploy");
            set(owner, second, maximum); OathService.activate(owner); tick(owner);
            int before = value(owner, second);
            OathService.end(owner, "fixture_disconnect", false);
            check(!OathService.active(owner) && value(owner, second) == before && !owner.hasEffect(OathEffects.BACKLASH.get()),
                    "interruption preserves resource, no backlash");
            var saved = OathSavedData.get(server).save(new net.minecraft.nbt.CompoundTag());
            check(!saved.getCompound(owner.getStringUUID()).getBoolean("Active"), "persisted session tombstone");
            OathService.end(owner, "fixture_repeat", true);
            check(!owner.hasEffect(OathEffects.BACKLASH.get()), "repeat ending cannot impose backlash");
            target.discard();
            com.mojang.logging.LogUtils.getLogger().info("OATH_NATIVE_PASS");
        } finally {
            OathService.end(owner, "fixture_cleanup", false); online.remove(owner.getUUID());
        }
    }
    private static ItemStack create(ServerPlayer owner, int slot) throws Exception {
        var maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(owner.level());
        TlmEntityAdapter.tame(maid, owner); maid.moveTo(slot * 3, 100, 0, 0, 0); owner.level().addFreshEntity(maid);
        var carrier = new ItemStack(ModItems.MAID_SWORD.get()); owner.getInventory().setItem(slot, carrier);
        check(ContractLifecycleService.capture(owner, maid, carrier, false), "native capture"); set(owner, carrier, 0);
        return carrier;
    }
    private static void tick(ServerPlayer owner) {
        var level = owner.getServer().overworld();
        ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 1);
        OathService.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, owner));
        ResonanceRecoveryEvents.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner));
    }
    private static void set(ServerPlayer p, ItemStack c, int n) { check(ResonanceService.set(p, c, ContractCarrierData.getBindingId(c), n), "set pool"); }
    private static int value(ServerPlayer p, ItemStack c) { return ResonanceService.current(p, c, ContractCarrierData.getBindingId(c)); }
    private static void near(float actual, float expected, String message) { check(Math.abs(actual - expected) < .001, message + ": " + actual); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private OathValidation() { }
}

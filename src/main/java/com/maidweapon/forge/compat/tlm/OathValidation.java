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
        check(OathSavedData.get(server).rests(UUID.fromString("07a4f283-201e-488e-a0e8-d9269fe65dcd"))
                .getInt(state.getString("RestValidationMaid")) == 123, "real restart preserves paused per-maid rest");
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
            var ordered = new net.minecraftforge.items.ItemStackHandler(3);
            ordered.setStackInSlot(0, new ItemStack(ModItems.RESONANCE_SWORD_TASSEL.get()));
            ordered.setStackInSlot(1, new ItemStack(ModItems.OATH_RING.get()));
            ordered.setStackInSlot(2, new ItemStack(ModItems.ENDLESS_CONTRACT.get()));
            var effects = ContractBaubleEffects.resolveSlots(ordered);
            check(effects.contains(ContractBaubleEffects.Effect.OATH_MANIFESTATION)
                    && !effects.contains(ContractBaubleEffects.Effect.RESONANCE_WAIVER)
                    && !effects.contains(ContractBaubleEffects.Effect.ARMOR_PROJECTION), "ordinary projection slot policy preserved");
            var inventory = ordered.serializeNBT();
            var items = inventory.getList("Items", 10); var reversed = new net.minecraft.nbt.ListTag();
            for (int i = items.size() - 1; i >= 0; i--) reversed.add(items.get(i).copy());
            inventory.put("Items", reversed); var payload = new net.minecraft.nbt.CompoundTag();
            payload.put("MaidBaubleInventory", inventory);
            check(StoredMaidBaubleResolver.readNativeEffects(payload).equals(effects), "stored slot order ignores list order");
            verifySpiritSession(owner);
            var blade = create(owner, 0);
            String binding = ContractCarrierData.getBindingId(blade);
            check(ContractLifecycleService.manifest(owner, blade, false), "native manifestation");
            var maid = (LivingEntity) ContractWeaponLocator.findManifestedMaid(owner, ContractCarrierData.getBoundMaidUUID(blade));
            var slots = (IItemHandlerModifiable) maid.getClass().getMethod("getMaidBauble").invoke(maid);
            slots.setStackInSlot(0, new ItemStack(ModItems.OATH_RING.get()));
            slots.setStackInSlot(1, new ItemStack(ModItems.ENDLESS_CONTRACT.get()));
            check(!ResonanceService.waived(owner, blade), "ring-first disables waiver");
            set(owner, blade, 0); OathService.activate(owner); check(!OathService.active(owner), "zero rejected");
            check(ContractLifecycleService.capture(owner, maid, blade, false), "store equipped ring for one-key activation");
            owner.getInventory().selected = 0;
            set(owner, blade, 1); OathService.activate(owner);
            check(OathService.active(owner) && !com.maidweapon.forge.item.MaidInfusion.containsMaid(blade),
                    "one key summons and activates at one resonance");
            maid = (LivingEntity) ContractWeaponLocator.findManifestedMaid(owner, ContractCarrierData.getBoundMaidUUID(blade));
            slots = (IItemHandlerModifiable) maid.getClass().getMethod("getMaidBauble").invoke(maid);
            int beforeSkill = value(owner, blade);
            check(SpiritAbilityResonanceCosts.pay(owner, blade, SpiritAbilityResonanceCosts.Action.BLACK_SUMMON)
                    && value(owner, blade) == beforeSkill, "session pays through burn, not per-skill resonance");
            owner.getInventory().selected = 5; // Later weapon-switch checks must not overwrite the real carrier.
            check(!InfusedMaidDeploymentSystem.recallRequested(owner, blade), "manual recall locked");
            check(!ContractCompanionService.canFollow(owner, blade), "switching companions locked");
            slots.setStackInSlot(0, ItemStack.EMPTY); slots.setStackInSlot(1, ItemStack.EMPTY);
            check(OathService.active(owner), "removing ring retains session");
            check(!ResonanceService.recover(owner, blade, binding, 500), "all positive recovery rejected");
            tick(owner); check(!OathService.active(owner), "one point burns out");
            check(owner.getEffect(OathEffects.BACKLASH.get()).getAmplifier() == 0, "first burnout I");
            check(com.maidweapon.forge.item.MaidInfusion.containsMaid(blade), "burnout safely recalled");
            String restingMaid = ContractCarrierData.getBoundMaidUUID(blade);
            check(com.maidweapon.forge.system.oath.OathRest.resting(owner, restingMaid), "burnout locks maid identity");
            check(!ContractLifecycleService.manifest(owner, blade, false), "burnout blocks manual manifestation");
            check(!ContractMaidLifecycleService.manifest(owner, blade, false), "native conversion cannot bypass rest");
            check(!ContractCompanionService.canFollow(owner, blade), "automatic follow blocked during rest");
            var rests = OathSavedData.get(server).save(new net.minecraft.nbt.CompoundTag());
            check(rests.getCompound("RestingMaids").getCompound(owner.getStringUUID()).getInt(restingMaid) > 0,
                    "maid rest persists independently of carrier");
            tick(owner); check(owner.getEffect(OathEffects.BACKLASH.get()).getAmplifier() == 0, "no duplicate settlement");
            check(!owner.getEffect(OathEffects.BACKLASH.get()).isCurativeItem(new ItemStack(Items.MILK_BUCKET)), "native milk immunity");
            owner.removeEffect(OathEffects.BACKLASH.get());
            check(!com.maidweapon.forge.system.oath.OathRest.resting(owner, restingMaid), "cleared backlash releases rest");
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
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            var arrow = new net.minecraft.world.entity.projectile.Arrow(level, owner);
            var arrowSource = level.damageSources().arrow(arrow, owner);
            var ranged = new LivingHurtEvent(target, arrowSource, 10);
            OathCombat.hurt(ranged); near(ranged.getAmount(), 12, "projectile ownership independent of held weapon");
            arrow.setOwner(maid);
            var reassigned = new LivingHurtEvent(target, arrowSource, 10);
            OathCombat.hurt(reassigned); near(reassigned.getAmount(), 10, "changed projectile owner cannot borrow attribution");
            arrow.discard();
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
            var limit = new com.maidweapon.forge.compat.DomainTargetCompatibility.State[]{
                    new com.maidweapon.forge.compat.DomainTargetCompatibility.State(5, 0, false, "fixture")};
            com.maidweapon.forge.compat.DomainTargetCompatibility.register("oath_fixture",
                    new com.maidweapon.forge.compat.DomainTargetCompatibility.Adapter() {
                        public boolean supports(LivingEntity t) { return t == target; }
                        public com.maidweapon.forge.compat.DomainTargetCompatibility.State inspect(LivingEntity t) { return limit[0]; }
                    });
            var capped = new LivingHurtEvent(target, level.damageSources().playerAttack(owner), 5);
            OathCombat.hurt(capped); near(capped.getAmount(), 5, "verified hit cap retained");
            limit[0] = new com.maidweapon.forge.compat.DomainTargetCompatibility.State(5, 20, false, "fixture");
            var budget = new LivingHurtEvent(target, level.damageSources().playerAttack(owner), 4);
            OathCombat.hurt(budget); near(budget.getAmount(), 4, "no bonus appended after rolling budget");
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
            String secondId = ContractCarrierData.getBoundMaidUUID(second);
            var restStore = OathSavedData.get(server);
            com.maidweapon.forge.system.oath.OathRest.begin(owner, restingMaid, 2);
            com.maidweapon.forge.system.oath.OathRest.tick(owner);
            com.maidweapon.forge.system.oath.OathRest.tick(owner);
            check(!com.maidweapon.forge.system.oath.OathRest.resting(owner, restingMaid)
                    && com.maidweapon.forge.system.oath.OathRest.resting(owner, secondId),
                    "each maid expires independently while backlash remains");
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
            com.maidweapon.forge.system.oath.OathRest.begin(owner, secondId, 123);
            var restartState = OathSavedData.get(server).session(owner.getUUID());
            restartState.putString("RestValidationMaid", secondId);
            OathSavedData.get(server).put(owner.getUUID(), restartState);
            target.discard();
            com.mojang.logging.LogUtils.getLogger().info("OATH_NATIVE_PASS");
        } finally {
            OathService.end(owner, "fixture_cleanup", false); online.remove(owner.getUUID());
        }
    }
    private static void verifySpiritSession(ServerPlayer owner) throws Exception {
        var weapon = new ItemStack(Items.IRON_SWORD);
        boolean provider = net.minecraftforge.fml.ModList.get().isLoaded("blade_tetra");
        if (provider) {
            owner.getInventory().selected = 0;
            var prepare = com.maidweapon.forge.system.akatsuki.AkatsukiDomainValidation.class
                    .getDeclaredMethod("prepareBlade", ServerPlayer.class);
            prepare.setAccessible(true); weapon = (ItemStack) prepare.invoke(null, owner);
            owner.getInventory().setItem(0, ItemStack.EMPTY);
        }
        owner.getInventory().setItem(6, weapon); owner.getInventory().selected = 6;
        String spirit = "blade_tetra:akatsuki";
        check(com.maidweapon.forge.api.IntrinsicSpiritApi.ensureIntrinsicSpirit(owner, weapon, spirit, "赤月")
                && com.maidweapon.forge.api.IntrinsicSpiritApi.setIntrinsicSpiritActive(owner, weapon, spirit, true),
                "native intrinsic spirit for oath authorization");
        set(owner, weapon, ResonanceService.maximum()); OathService.activate(owner);
        check(!OathService.active(owner) && com.maidweapon.forge.item.MaidInfusion.containsMaid(weapon),
                "missing ring never summons or starts a session");
        check(ContractLifecycleService.manifest(owner, weapon, false), "ordinary spirit manifestation");
        var maid = ContractWeaponLocator.findManifestedMaid(owner, ContractCarrierData.getBoundMaidUUID(weapon));
        var slots = (IItemHandlerModifiable) maid.getClass().getMethod("getMaidBauble").invoke(maid);
        slots.setStackInSlot(0, new ItemStack(ModItems.OATH_RING.get()));
        check(!OathService.permitsSpirit(owner, weapon, spirit), "ordinary manifestation still pauses spirit abilities");
        check(ContractLifecycleService.capture(owner, maid, weapon, false), "store intrinsic with ring");
        set(owner, weapon, ResonanceService.maximum()); OathService.activate(owner);
        check(OathService.active(owner) && OathService.permitsSpirit(owner, weapon, spirit), "one-key intrinsic permission");
        check(!OathService.permitsSpirit(owner, weapon, "maid_weapon:white_fox")
                && !OathService.permitsSpirit(owner, weapon.copy(), spirit), "other spirit and copied carrier rejected");
        check(!com.maidweapon.forge.api.IntrinsicSpiritApi.stored(weapon, spirit), "permission never fabricates stored payload");
        int before = value(owner, weapon);
        check(ResonanceAttackCosts.pay(owner, weapon, SpiritAbilityResonanceCosts.Action.RED_SPACE_CUT, 10)
                && value(owner, weapon) == before, "manifested spirit attack billing uses shared burn");
        int initial = value(owner, weapon);
        check(initial == ResonanceService.maximum(), "one-key activation preserves starting full resonance");
        for (int i = 0; i < 20; i++) {
            owner.tickCount++;
            weapon.inventoryTick(owner.level(), owner, 6, true);
            tick(owner);
            ContractCompanionService.tick(owner);
        }
        check(OathService.active(owner) && OathService.permitsSpirit(owner, weapon, spirit),
                "one-key manifestation remains present after twenty real session ticks");
        near(value(owner, weapon), initial - Math.round(ResonanceService.maximum() * com.maidweapon.common.OathConfig.BURN.get()),
                "one-key manifestation burns only the configured amount per second");
        if (provider) com.mojang.logging.LogUtils.getLogger().info("OATH_PROVIDER_ONEKEY_PASS");
        OathService.end(owner, "fixture_spirit_complete", false);
        check(!OathService.permitsSpirit(owner, weapon, spirit), "session end revokes permission immediately");
        owner.getInventory().setItem(6, ItemStack.EMPTY); owner.getInventory().selected = 5;
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

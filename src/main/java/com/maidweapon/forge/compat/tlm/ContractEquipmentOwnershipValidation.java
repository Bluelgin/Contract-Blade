package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.compat.ContractEquipmentCleanup;
import com.maidweapon.forge.compat.TaczCompat;
import com.maidweapon.forge.compat.TripleMagicCompat;
import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.deployment.ContractMaidRuntimeService;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandlerModifiable;

/** Item conservation regressions; optional TACZ cases run only with the real mod installed. */
public final class ContractEquipmentOwnershipValidation {
    public static void run(Level level, Player player) throws ReflectiveOperationException {
        Mob maid = create(level, player);
        var slots = baubles(maid);
        ItemStack carrier = new ItemStack(ModItems.MAID_SWORD.get());
        slots.setStackInSlot(0, new ItemStack(ModItems.RESONANCE_SWORD_TASSEL.get()));
        ContractMaidRuntimeService.prepare(player, carrier, maid);
        maid.getPersistentData().putString("MaidWeaponMagicLoadout", "cached-same-item");
        carrier.getOrCreateTag().putString("OwnershipFixtureSpellChange", "changed");
        ContractMaidRuntimeService.prepare(player, carrier, maid);
        check(!maid.getPersistentData().contains("MaidWeaponMagicLoadout"),
                "same-item projection data change invalidates spell loadout cache");
        maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        slots.setStackInSlot(0, ItemStack.EMPTY);
        ContractMaidRuntimeService.prepare(player, carrier, maid);
        check(count(maid, Items.IRON_SWORD) == 2, "identical replacement retains both real swords");
        // Older versions could leave a generic phantom without any restoration snapshot.
        maid.setItemSlot(EquipmentSlot.MAINHAND, TripleMagicCompat.createProjection(carrier, player));
        ContractEquipmentCleanup.clear(player, maid, carrier);
        check(maid.getMainHandItem().isEmpty(), "orphan phantom removed without touching real backpack items");
        check(count(maid, Items.IRON_SWORD) == 1, "orphan repair never duplicates originals");

        if (!TaczCompat.isLoaded()) return;
        Class<?> builderType = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
        Object builder = builderType.getMethod("create").invoke(null);
        builderType.getMethod("setId", net.minecraft.resources.ResourceLocation.class).invoke(builder,
                new net.minecraft.resources.ResourceLocation("tacz", "ak47"));
        ItemStack gun = (ItemStack) builderType.getMethod("build").invoke(builder);
        check(!gun.isEmpty() && TaczCompat.isGun(gun), "real TACZ gun available");
        ContractCarrierData.ensureBindingId(gun);

        for (boolean manual : new boolean[]{false, true}) {
            Mob armed = create(level, player);
            var equipped = baubles(armed);
            equipped.setStackInSlot(0, new ItemStack(ModItems.RESONANCE_SWORD_TASSEL.get()));
            ContractMaidRuntimeService.prepare(player, gun, armed);
            check(TaczCompat.isProjection(armed.getMainHandItem()), "native gun projection active");
            if (manual) {
                armed.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                for (int i = 0; i < 5; i++) ContractMaidRuntimeService.prepare(player, gun, armed);
                check(armed.getMainHandItem().is(Items.DIAMOND_SWORD), "TACZ respects manual replacement");
            }
            equipped.setStackInSlot(0, ItemStack.EMPTY);
            for (int i = 0; i < 5; i++) ContractMaidRuntimeService.prepare(player, gun, armed);
            check(!TripleMagicCompat.isPhantom(armed.getMainHandItem()) && !TaczCompat.isProjection(armed.getMainHandItem()),
                    "disabling TACZ leaves no nested phantom");
            check(count(armed, Items.IRON_SWORD) == 1, "original real gun-hand sword restored exactly once");
            if (manual) check(count(armed, Items.DIAMOND_SWORD) == 1, "manual real sword retained exactly once");
            check(!armed.getPersistentData().contains("MaidWeaponTaczOriginalHand"), "specialized snapshot cleared");
            check(!armed.getPersistentData().contains("MaidWeaponProjectionGear"), "general snapshot cleared");
        }

        Mob captured = create(level, player);
        baubles(captured).setStackInSlot(0, new ItemStack(ModItems.RESONANCE_SWORD_TASSEL.get()));
        ContractMaidRuntimeService.prepare(player, gun, captured);
        ContractEquipmentCleanup.clear(player, captured, gun);
        check(captured.getMainHandItem().is(Items.IRON_SWORD) && count(captured, Items.IRON_SWORD) == 1,
                "storage cleanup unwinds nested snapshots in ownership order");
        com.mojang.logging.LogUtils.getLogger().info("[MaidWeapon] TACZ_OWNERSHIP_VALIDATION_PASS");
    }

    private static Mob create(Level level, Player player) throws ReflectiveOperationException {
        Mob maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(level);
        TlmEntityAdapter.tame(maid, player);
        maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        return maid;
    }

    private static IItemHandlerModifiable baubles(Mob maid) throws ReflectiveOperationException {
        return (IItemHandlerModifiable) maid.getClass().getMethod("getMaidBauble").invoke(maid);
    }

    private static int count(Mob maid, net.minecraft.world.item.Item item) throws ReflectiveOperationException {
        int total = maid.getMainHandItem().is(item) ? maid.getMainHandItem().getCount() : 0;
        var pack = (IItemHandlerModifiable) maid.getClass().getMethod("getAvailableBackpackInv").invoke(maid);
        for (int i = 0; i < pack.getSlots(); i++) if (pack.getStackInSlot(i).is(item)) total += pack.getStackInSlot(i).getCount();
        return total;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Equipment ownership: " + message);
    }

    private ContractEquipmentOwnershipValidation() { }
}

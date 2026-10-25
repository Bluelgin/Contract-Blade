package com.maidweapon.forge.compat.tacz;

import com.maidweapon.forge.compat.ContractProjectionIdentity;
import com.maidweapon.forge.compat.TaczCompat;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/** Native provider fixture: real TACZ items and extraction API, no mocked round arithmetic. */
public final class TaczAmmoValidation {
    public static void run(LivingEntity maid, ItemStack gun) {
        AbstractGunItem item = (AbstractGunItem) gun.getItem();
        ResourceLocation ammoId = TaczLoadedCompat.ammoId(gun);
        for (int[] test : new int[][]{{50,0,30,20,0}, {10,50,30,0,30},
                {0,30,30,0,0}, {5,7,30,0,0}, {0,0,30,0,0}}) {
            var owner = new ItemStackHandler(1);
            var pack = new ItemStackHandler(1);
            owner.setStackInSlot(0, rounds(ammoId, test[0]));
            pack.setStackInSlot(0, rounds(ammoId, test[1]));
            var view = new TaczPriorityAmmoHandler(owner, pack, maid, () -> true);
            int consumed = item.findAndExtractInventoryAmmo(view, gun, test[2]);
            check(consumed == Math.min(test[2], test[0] + test[1]), "actual native extraction result");
            check(owner.getStackInSlot(0).getCount() == test[3], "owner priority");
            check(pack.getStackInSlot(0).getCount() == test[4], "maid fallback");
        }
        var owner = new ItemStackHandler(1);
        var pack = new ItemStackHandler(1);
        owner.setStackInSlot(0, rounds(ammoId, 40));
        pack.setStackInSlot(0, rounds(ammoId, 50));
        var first = new TaczPriorityAmmoHandler(owner, pack, maid, () -> true);
        var second = new TaczPriorityAmmoHandler(owner, pack, maid, () -> true);
        check(item.findAndExtractInventoryAmmo(first, gun, 30) == 30, "first maid shared supply");
        check(item.findAndExtractInventoryAmmo(second, gun, 30) == 30, "second maid shared supply");
        check(owner.getStackInSlot(0).isEmpty() && pack.getStackInSlot(0).getCount() == 30, "no shared inventory double spend");
        owner.setStackInSlot(0, rounds(ammoId, 50));
        check(item.findAndExtractInventoryAmmo(new TaczPriorityAmmoHandler(owner, pack, maid, () -> false), gun, 5) == 5,
                "revoked owner access keeps maid ammo");
        check(owner.getStackInSlot(0).getCount() == 50, "revoked owner untouched");
        // A native box remains the actual inventory object, never a count-caching facade.
        ItemStack boxStack = new ItemStack(ModItems.AMMO_BOX.get());
        IAmmoBox box = (IAmmoBox) boxStack.getItem();
        box.setAmmoId(boxStack, ammoId);
        box.setAmmoCount(boxStack, 10);
        owner.setStackInSlot(0, boxStack);
        pack.setStackInSlot(0, rounds(ammoId, 50));
        check(item.findAndExtractInventoryAmmo(first, gun, 30) == 30, "native box plus fallback");
        check(box.getAmmoCount(boxStack) == 0 && pack.getStackInSlot(0).getCount() == 30, "box actual debit");
        // Inventory may change between a successful presence read and consumption.
        owner.setStackInSlot(0, rounds(ammoId, 30));
        pack.setStackInSlot(0, ItemStack.EMPTY);
        check(!first.getStackInSlot(0).isEmpty(), "presence read");
        owner.setStackInSlot(0, ItemStack.EMPTY);
        check(item.findAndExtractInventoryAmmo(first, gun, 30) == 0, "stale availability cannot mint rounds");
        ItemStack projection = gun.copy();
        projection.getOrCreateTag().putBoolean(TaczCompat.PROJECTION_TAG, true);
        check(ContractProjectionIdentity.isProjection(projection), "unified gun identity");
        check(!ContractProjectionIdentity.isProjection(gun), "real gun identity retained");
        owner.setStackInSlot(0, projection);
        check(first.getStackInSlot(0).isEmpty(), "projection cannot be its own supply");
        var player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(
                (net.minecraft.server.level.ServerLevel) maid.level());
        var container = new net.minecraft.world.SimpleContainer(27);
        container.setItem(0, projection);
        var menu = net.minecraft.world.inventory.ChestMenu.threeRows(99, player.getInventory(), container);
        for (var click : new net.minecraft.world.inventory.ClickType[]{
                net.minecraft.world.inventory.ClickType.PICKUP,
                net.minecraft.world.inventory.ClickType.QUICK_MOVE,
                net.minecraft.world.inventory.ClickType.SWAP,
                net.minecraft.world.inventory.ClickType.THROW,
                net.minecraft.world.inventory.ClickType.CLONE,
                net.minecraft.world.inventory.ClickType.QUICK_CRAFT}) {
            menu.clicked(0, 0, click, player);
            check(container.getItem(0) == projection && menu.getCarried().isEmpty(), "menu blocks " + click);
        }
        com.mojang.logging.LogUtils.getLogger().info("[MaidWeapon] TACZ_AMMO_VALIDATION_PASS");
    }
    private static ItemStack rounds(ResourceLocation id, int count) {
        return count <= 0 ? ItemStack.EMPTY : AmmoItemBuilder.create().setId(id).setCount(count).build();
    }
    private static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("TACZ ammo: " + message);
    }
    private TaczAmmoValidation() { }
}

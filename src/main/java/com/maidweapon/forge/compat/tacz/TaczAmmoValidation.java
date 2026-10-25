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
    /** Register a fake player only in this disposable test's online lookup, then restore it. */
    @SuppressWarnings("unchecked")
    public static void verifyOwnerRoute(net.minecraft.world.entity.Mob maid,
            net.minecraft.server.level.ServerPlayer owner, ItemStack source) throws ReflectiveOperationException {
        var players = owner.getServer().getPlayerList();
        java.util.Map<java.util.UUID, net.minecraft.server.level.ServerPlayer> online = null;
        for (Class<?> type = players.getClass(); type != null && online == null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                String signature = field.getGenericType().getTypeName();
                if (java.util.Map.class.isAssignableFrom(field.getType())
                        && signature.contains("java.util.UUID") && signature.contains("ServerPlayer")) {
                    field.setAccessible(true);
                    online = (java.util.Map<java.util.UUID, net.minecraft.server.level.ServerPlayer>) field.get(players);
                    break;
                }
            }
        }
        if (online == null) throw new IllegalStateException("Native fixture cannot locate online player map");
        var previous = online.put(owner.getUUID(), owner);
        ItemStack savedGunSlot = owner.getInventory().getItem(8);
        ItemStack savedAmmoSlot = owner.getInventory().getItem(0);
        var pack = (net.minecraftforge.items.IItemHandlerModifiable) maid.getClass().getMethod("getMaidInv").invoke(maid);
        ItemStack savedMaidAmmo = pack.getStackInSlot(0);
        var baubles = (net.minecraftforge.items.IItemHandlerModifiable) maid.getClass().getMethod("getMaidBauble").invoke(maid);
        try {
            com.maidweapon.forge.system.contract.ContractCarrierData.setOwner(source, owner);
            com.maidweapon.forge.system.contract.ContractCarrierData.setBoundMaidUUID(source, maid.getStringUUID());
            String binding = com.maidweapon.forge.system.contract.ContractCarrierData.ensureBindingId(source);
            maid.getPersistentData().putString(com.maidweapon.forge.compat.TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID, binding);
            owner.getInventory().setItem(8, source);
            owner.getInventory().setItem(0, rounds(TaczLoadedCompat.ammoId(source), 10));
            pack.setStackInSlot(0, rounds(TaczLoadedCompat.ammoId(source), 50));
            baubles.setStackInSlot(0, new ItemStack(com.maidweapon.forge.init.ModItems.RESONANCE_SWORD_TASSEL.get()));
            maid.setPos(owner.getX(), owner.getY(), owner.getZ());
            check(owner.serverLevel().addFreshEntity(maid), "owner-route maid spawned");
            com.maidweapon.forge.system.deployment.ContractMaidRuntimeService.prepare(owner, source, maid);
            ItemStack projection = maid.getMainHandItem();
            AbstractGunItem gun = (AbstractGunItem) projection.getItem();
            check(TaczLoadedCompat.resolve(maid) != null, "real authority resolver accepts online owner");
            check(gun.canReload(maid, projection), "native reload check sees owner ammunition");
            var api = new com.tacz.guns.item.ModernKineticGunScriptAPI();
            api.setShooter(maid);
            api.setItemStack(projection);
            api.setDataHolder(new com.tacz.guns.entity.shooter.ShooterDataHolder());
            check(api.hasAmmoToConsume(), "native script presence call");
            check(api.consumeAmmoFromPlayer(30) == 30, "native script consumes owner then maid");
            check(owner.getInventory().getItem(0).isEmpty() && pack.getStackInSlot(0).getCount() == 30,
                    "native routed debit priority");
            check(api.putAmmoInMagazine(30) == 0 && gun.getCurrentAmmoCount(projection) == 30,
                    "native magazine receives exactly debited rounds");
            check(api.reduceAmmoOnce() && gun.getCurrentAmmoCount(projection) == 29,
                    "native shot debit and ledger");
            check(projection.getTag().getInt(TaczAmmoAccounting.PAID) == 29, "paid credits follow native shot");
            maid.setPos(owner.getX() + 65, owner.getY(), owner.getZ());
            check(TaczLoadedCompat.resolve(maid) == null, "range revokes owner supply");
            check(api.consumeAmmoFromPlayer(1) == 1 && pack.getStackInSlot(0).getCount() == 29,
                    "out of range uses maid supply");
            check(api.putAmmoInMagazine(1) == 0 && api.reduceAmmoOnce(), "out-of-range round is loaded then spent");
            maid.setPos(owner.getX(), owner.getY(), owner.getZ());
            online.remove(owner.getUUID());
            check(api.consumeAmmoFromPlayer(1) == 1 && pack.getStackInSlot(0).getCount() == 28,
                    "offline owner uses maid supply");
            check(api.putAmmoInMagazine(1) == 0, "offline maid round loaded");
            online.put(owner.getUUID(), owner);
            com.maidweapon.forge.compat.ContractEquipmentCleanup.clear(owner, maid, source);
            check(!TaczCompat.isProjection(maid.getMainHandItem()), "route cleanup restores real equipment");
            check(owner.getInventory().getItem(0).getCount() == 30
                    && pack.getStackInSlot(0).getCount() == 28, "60 starting rounds = 58 refunded/reserved + 2 spent");
            com.mojang.logging.LogUtils.getLogger().info("[MaidWeapon] TACZ_OWNER_ROUTE_VALIDATION_PASS");
        } finally {
            if (previous == null) online.remove(owner.getUUID()); else online.put(owner.getUUID(), previous);
            owner.getInventory().setItem(8, savedGunSlot);
            owner.getInventory().setItem(0, savedAmmoSlot);
            pack.setStackInSlot(0, savedMaidAmmo);
            baubles.setStackInSlot(0, ItemStack.EMPTY);
            maid.discard();
        }
    }

    private static ItemStack rounds(ResourceLocation id, int count) {
        return count <= 0 ? ItemStack.EMPTY : AmmoItemBuilder.create().setId(id).setCount(count).build();
    }
    private static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("TACZ ammo: " + message);
    }
    private TaczAmmoValidation() { }
}

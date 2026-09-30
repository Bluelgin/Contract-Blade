package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.system.deployment.ContractMaidRuntimeService;
import com.maidweapon.forge.system.interior.*;
import com.maidweapon.forge.system.interior.home.ContractInteriorGuideService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Explicit native fixture: resident identity/gear safety across the real lifecycle. */
public final class ContractHomeSafetyValidation {
    public static void run(ServerLevel level) throws ReflectiveOperationException {
        check(Boolean.getBoolean("contractblade.home.nativeValidation"), "fixture disabled");
        check(level.dimensionType().fixedTime().isEmpty(), "sky is not fixed noon");
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("ea9b6090-e546-41d8-8c48-58cd16bfb92d"), "HomeSafetyFixture"));
        player.getInventory().clearContent();
        ItemStack carrier = new ItemStack(ModItems.MAID_SWORD.get());
        String binding = MaidWeaponItem.ensureBindingId(carrier);
        var saved = ContractInteriorSavedData.get(level.getServer());
        saved.chooseTerrain(binding, "plains_garden");
        var plot = saved.getOrCreate(binding);
        BlockPos origin = new BlockPos(ContractInteriorSavedData.originX(plot), 80, ContractInteriorSavedData.originZ(plot));
        ContractInteriorTerrainBuilder.ensureGenerated(level, origin, new ContractInteriorProfile(1, 1), saved, binding);
        player.setPos(origin.getX() + .5, origin.getY() + 1.1, origin.getZ() + .5);
        player.getInventory().setItem(0, carrier);
        Mob maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(level);
        TlmEntityAdapter.tame(maid, player);
        double x = origin.getX() + 7.5, y = origin.getY() + 1.1, z = origin.getZ() + 5.5;
        maid.moveTo(x, y, z, 67, 0);
        check(level.addFreshEntity(maid), "resident spawned");
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.getOrCreateTag().putString("Fixture", "original helmet");
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.getOrCreateTag().putString("Fixture", "original sword");
        maid.setItemSlot(EquipmentSlot.HEAD, helmet.copy());
        maid.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
        java.util.UUID maidId = maid.getUUID();
        AbstractContainerMenu menu = null;
        try {
            check(ContractLifecycleService.capture(player, maid, carrier, false), "first capture");
            check(ContractLifecycleService.manifest(player, carrier, false), "home manifest");
            maid = (Mob) level.getEntity(maidId);
            check(maid != null && ContractHomeEquipmentGuard.resident(maid), "same resident identity");
            ContractResidentPositionService.restore(maid, plot);
            maid.moveTo(x, y, z, 67, 0);
            ContractResidentPositionService.remember(maid);
            check(ContractInteriorSavedData.load(saved.save(new CompoundTag())).find(binding).home().residentX == x,
                    "resident position persists through save/load");
            maid.moveTo(x + 1, -1, z);
            ContractResidentPositionService.remember(maid);
            check(plot.home().residentX == x && plot.home().residentY == y,
                    "void fall cannot overwrite safe checkpoint");
            ContractResidentPositionService.restore(maid, plot);
            check(Math.abs(maid.getX() - x) < .01 && Math.abs(maid.getZ() - z) < .01,
                    "position restore recovers saved checkpoint");
            var armor = maid.getItemBySlot(EquipmentSlot.HEAD).save(new CompoundTag());
            var hand = maid.getMainHandItem().save(new CompoundTag());
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            ContractMaidLifecycleService.prepareManifestedMaid(player, carrier, maid);
            ContractMaidRuntimeService.maintain(player, carrier, maid);
            check(armor.equals(maid.getItemBySlot(EquipmentSlot.HEAD).save(new CompoundTag()))
                    && hand.equals(maid.getMainHandItem().save(new CompoundTag())), "interior skips combat projection");

            menu = (AbstractContainerMenu) Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.inventory.container.backpack.EmptyBackpackContainer")
                    .getConstructor(int.class, net.minecraft.world.entity.player.Inventory.class, int.class)
                    .newInstance(91, player.getInventory(), maid.getId());
            check(ContractHomeEquipmentGuard.protect(player, menu), "real TLM menu guarded");
            player.getInventory().selected = 1;
            player.getInventory().setItem(1, new ItemStack(Items.NETHERITE_HELMET));
            var direct = new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(
                    player, net.minecraft.world.InteractionHand.MAIN_HAND, maid);
            ContractHomeEquipmentGuard.onInteract(direct);
            check(direct.isCanceled() && player.getMainHandItem().is(Items.NETHERITE_HELMET),
                    "direct equipment interaction retains player item");
            player.getInventory().selected = 0;
            player.getInventory().setItem(1, ItemStack.EMPTY);
            menu.setCarried(new ItemStack(Items.NETHERITE_HELMET));
            menu.clicked(36, 0, ClickType.PICKUP, player);
            check(menu.getCarried().is(Items.NETHERITE_HELMET), "blocked armor pickup retains player item");
            menu.setCarried(new ItemStack(Items.DIAMOND_SWORD));
            menu.clicked(40, 0, ClickType.PICKUP, player);
            check(menu.getCarried().is(Items.DIAMOND_SWORD), "blocked hand placement retains sword");
            menu.clicked(42, 0, ClickType.PICKUP, player);
            check(menu.getCarried().is(Items.DIAMOND_SWORD), "backpack rejects weapons without consuming cursor");
            menu.setCarried(ItemStack.EMPTY);
            player.getInventory().setItem(1, new ItemStack(Items.DIAMOND_SWORD));
            menu.clicked(28, 0, ClickType.QUICK_MOVE, player);
            check(player.getInventory().getItem(1).is(Items.DIAMOND_SWORD), "shift-click cannot insert weapons");
            menu.clicked(40, 1, ClickType.SWAP, player);
            check(hand.equals(maid.getMainHandItem().save(new CompoundTag())), "number-key swap cannot change equipment");
            menu.setCarried(new ItemStack(Items.APPLE));
            menu.clicked(42, 0, ClickType.PICKUP, player);
            check(menu.getCarried().isEmpty() && menu.getSlot(42).getItem().is(Items.APPLE), "ordinary backpack food remains usable");
            menu.clicked(42, 0, ClickType.PICKUP, player);
            check(menu.getCarried().is(Items.APPLE), "food retrieval remains usable");
            menu.setCarried(ItemStack.EMPTY);
            menu.removed(player); menu = null;

            check(ContractLifecycleService.capture(player, maid, carrier, false), "resident recall");
            check(ContractLifecycleService.manifest(player, carrier, false), "resident reentry");
            maid = (Mob) level.getEntity(maidId);
            check(maid != null, "same UUID after reentry");
            ContractResidentPositionService.restore(maid, plot);
            check(Math.abs(maid.getX() - x) < .01 && Math.abs(maid.getZ() - z) < .01,
                    "reentry uses last home position, not player spawn");
            check(armor.equals(maid.getItemBySlot(EquipmentSlot.HEAD).save(new CompoundTag()))
                    && hand.equals(maid.getMainHandItem().save(new CompoundTag())), "original gear survives recall/reentry");

            ContractInteriorGuideService.give(player, saved, plot);
            var book = player.getInventory().items.stream().filter(stack -> stack.is(Items.WRITTEN_BOOK)).findFirst().orElseThrow();
            book.getOrCreateTag().putString("author", "与你缔约的女仆");
            book.getOrCreateTag().remove("ContractInteriorGuideVersion");
            int books = player.getInventory().countItem(Items.WRITTEN_BOOK);
            ContractInteriorGuideService.give(player, saved, plot);
            check(book.getTag().getInt("ContractInteriorGuideVersion") == 2
                    && books == player.getInventory().countItem(Items.WRITTEN_BOOK), "legacy guide upgrades without duplication");
            System.out.println("CONTRACT_HOME_SAFETY_VALIDATION_PASSED: resident position, no projection, real TLM pickup/shift/swap, food, gear NBT, guide upgrade");
        } finally {
            if (menu != null) { menu.setCarried(ItemStack.EMPTY); menu.removed(player); }
            Mob remaining = (Mob) level.getEntity(maidId);
            if (remaining != null) remaining.discard();
            player.getInventory().clearContent();
        }
    }
    private static void check(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException("Home safety validation: " + reason);
    }
    private ContractHomeSafetyValidation() {}
}

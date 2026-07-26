package com.maidweapon.forge.menu;

import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.init.ModBlocks;
import com.maidweapon.forge.init.ModMenus;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MaidInjectorMenu extends AbstractContainerMenu {
    public static final int INJECT_BUTTON = 0;
    private final SimpleContainer slotsContainer = new SimpleContainer(4);
    private final ContainerLevelAccess access;
    private final Player player;

    public MaidInjectorMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, data.readBlockPos());
    }

    public MaidInjectorMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.MAID_INJECTOR.get(), id);
        this.player = inventory.player;
        this.access = ContainerLevelAccess.create(player.level(), pos);

        addSlot(new Slot(slotsContainer, 0, 35, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return MaidInfusion.isWeapon(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(slotsContainer, 1, 71, 35) {
            @Override public boolean mayPlace(ItemStack stack) {
                return TouhouLittleMaidHelper.isFilledMaidFilm(stack)
                        || TouhouLittleMaidHelper.isEmptyMaidFilm(stack);
            }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(slotsContainer, 2, 116, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addSlot(new Slot(slotsContainer, 3, 142, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    public boolean canInject() {
        ItemStack weapon = slotsContainer.getItem(0);
        ItemStack film = slotsContainer.getItem(1);
        boolean forward = MaidInfusion.isWeapon(weapon) && !MaidInfusion.isInfused(weapon)
                && TouhouLittleMaidHelper.isFilledMaidFilm(film);
        boolean reverse = MaidInfusion.containsMaid(weapon)
                && TouhouLittleMaidHelper.isEmptyMaidFilm(film)
                && MaidWeaponItem.isOwner(weapon, player);
        return (forward || reverse)
                && slotsContainer.getItem(2).isEmpty() && slotsContainer.getItem(3).isEmpty();
    }

    public boolean isExtracting() {
        return MaidInfusion.containsMaid(slotsContainer.getItem(0))
                && TouhouLittleMaidHelper.isEmptyMaidFilm(slotsContainer.getItem(1));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != INJECT_BUTTON || player.level().isClientSide || !canInject()) return false;
        ItemStack weapon = slotsContainer.getItem(0);
        ItemStack film = slotsContainer.getItem(1);
        if (isExtracting()) {
            ItemStack filledFilm = TouhouLittleMaidHelper.extractMaidToFilm(player, weapon, film);
            if (filledFilm.isEmpty()) return false;
            ItemStack cleanWeapon = weapon.copy();
            MaidWeaponItem.clearMaidContract(cleanWeapon);
            slotsContainer.setItem(2, cleanWeapon);
            slotsContainer.setItem(3, filledFilm);
            slotsContainer.setItem(0, ItemStack.EMPTY);
            slotsContainer.setItem(1, ItemStack.EMPTY);
            broadcastChanges();
            return true;
        }
        boolean result = TouhouLittleMaidHelper.infuseFromFilm(player, film, weapon);
        if (result) {
            ItemStack emptyFilm = TouhouLittleMaidHelper.createEmptyMaidStoreItem(film);
            slotsContainer.setItem(2, weapon.copy());
            slotsContainer.setItem(3, emptyFilm);
            slotsContainer.setItem(0, ItemStack.EMPTY);
            slotsContainer.setItem(1, ItemStack.EMPTY);
            broadcastChanges();
        }
        return result;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;
        ItemStack source = slot.getItem();
        result = source.copy();
        if (index < 4) {
            if (!moveItemStackTo(source, 4, 40, true)) return ItemStack.EMPTY;
        } else if (MaidInfusion.isWeapon(source)) {
            if (!moveItemStackTo(source, 0, 1, false)) return ItemStack.EMPTY;
        } else if (TouhouLittleMaidHelper.isFilledMaidFilm(source)
                || TouhouLittleMaidHelper.isEmptyMaidFilm(source)) {
            if (!moveItemStackTo(source, 1, 2, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, slotsContainer);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.MAID_INJECTOR.get());
    }
}

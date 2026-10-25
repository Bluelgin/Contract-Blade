package com.maidweapon.forge.compat.tacz;

import com.maidweapon.forge.compat.ContractProjectionIdentity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;

/** A call-local, read/extract-only view. Never installed as an entity capability. */
public final class TaczPriorityAmmoHandler implements IItemHandler {
    private final IItemHandler owner;
    private final IItemHandler maid;
    private final LivingEntity shooter;
    private final int ownerSlots;

    public TaczPriorityAmmoHandler(IItemHandler owner, IItemHandler maid, LivingEntity shooter) {
        this.owner = owner;
        this.maid = maid;
        this.shooter = shooter;
        this.ownerSlots = owner.getSlots();
    }
    private IItemHandler inventory(int slot) { return slot < ownerSlots ? owner : maid; }
    private int index(int slot) { return slot < ownerSlots ? slot : slot - ownerSlots; }
    private boolean allowed(int slot) {
        return slot >= 0 && slot < getSlots() && !shooter.level().isClientSide
                && shooter.getServer() != null && shooter.getServer().isSameThread()
                && (slot >= ownerSlots || TaczLoadedCompat.resolve(shooter) != null);
    }
    @Override public int getSlots() { return ownerSlots + maid.getSlots(); }
    @Override public ItemStack getStackInSlot(int slot) {
        if (!allowed(slot)) return ItemStack.EMPTY;
        ItemStack stack = inventory(slot).getStackInSlot(index(slot));
        if (ContractProjectionIdentity.isProjection(stack)) return ItemStack.EMPTY;
        if (stack.getItem() instanceof IAmmo) return stack;
        if (stack.getItem() instanceof IAmmoBox box && box.getAmmoCount(stack) > 0) return stack;
        return ItemStack.EMPTY;
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack current = getStackInSlot(slot);
        if (current.isEmpty() || !(current.getItem() instanceof IAmmo)) return ItemStack.EMPTY;
        ItemStack taken = inventory(slot).extractItem(index(slot), amount, simulate);
        if (!simulate && !taken.isEmpty()) changed(slot);
        return taken;
    }
    private void changed(int slot) {
        if (slot < ownerSlots) {
            var context = TaczLoadedCompat.resolve(shooter);
            if (context != null) context.owner().getInventory().setChanged();
        }
    }
    /** Native interfaces, synchronous debit, and return ONLY what was actually removed. */
    public int consume(ItemStack gun, int requested) {
        if (requested <= 0 || shooter.getServer() == null || !shooter.getServer().isSameThread()) return 0;
        int remaining = requested;
        int paid = 0;
        for (int slot = 0; slot < getSlots() && remaining > 0; slot++) {
            ItemStack stack = getStackInSlot(slot);
            if (stack.getItem() instanceof IAmmo ammo && ammo.isAmmoOfGun(gun, stack)) {
                int extracted = extractItem(slot, remaining, false).getCount();
                remaining -= extracted;
                paid += extracted;
            } else if (stack.getItem() instanceof IAmmoBox box && box.isAmmoBoxOfGun(gun, stack)) {
                int before = Math.max(0, box.getAmmoCount(stack));
                int take = Math.min(before, remaining);
                box.setAmmoCount(stack, before - take);
                // A native creative box supplies rounds without a physical debit.
                boolean infinite = box.isCreative(stack) || box.isAllTypeCreative(stack);
                int actual = infinite ? take : Math.min(take,
                        Math.max(0, before - Math.max(0, box.getAmmoCount(stack))));
                if (!infinite && box.getAmmoCount(stack) <= 0) box.setAmmoId(stack, DefaultAssets.EMPTY_AMMO_ID);
                remaining -= actual;
                if (!infinite) paid += actual;
                if (actual > 0) changed(slot);
            }
        }
        TaczAmmoAccounting.purchased(gun, paid);
        return requested - remaining;
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
    @Override public int getSlotLimit(int slot) { return inventory(slot).getSlotLimit(index(slot)); }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
}

package com.maidweapon.forge.compat.tacz;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmoBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Internal TACZ ammo-box facade. It stores no rounds: TACZ reads a count from
 * the contract owner and writes a reduced count back, which atomically consumes
 * the matching rounds from that owner's inventory.
 */
public final class ContractAmmoLinkItem extends Item implements IAmmoBox {
    public ContractAmmoLinkItem(Properties properties) {
        super(properties);
    }

    @Override
    public ResourceLocation getAmmoId(ItemStack ammoBox) {
        TaczLoadedCompat.LinkContext context = TaczLoadedCompat.resolve(ammoBox);
        return context == null
                ? DefaultAssets.EMPTY_AMMO_ID
                : TaczLoadedCompat.ammoId(context.gun());
    }

    @Override
    public int getAmmoCount(ItemStack ammoBox) {
        TaczLoadedCompat.LinkContext context = TaczLoadedCompat.resolve(ammoBox);
        int count = context == null ? 0 : TaczLoadedCompat.countOwnerAmmo(context);
        ammoBox.getOrCreateTag().putInt(TaczLoadedCompat.LAST_REPORTED_TAG, count);
        return count;
    }

    @Override
    public void setAmmoId(ItemStack ammoBox, ResourceLocation ammoId) {
        // The ammo type always follows the currently projected gun. TACZ clears
        // ordinary empty ammo boxes, but clearing this logical link is a no-op.
    }

    @Override
    public void setAmmoCount(ItemStack ammoBox, int count) {
        TaczLoadedCompat.LinkContext context = TaczLoadedCompat.resolve(ammoBox);
        if (context == null) return;
        int reported = ammoBox.getOrCreateTag().getInt(TaczLoadedCompat.LAST_REPORTED_TAG);
        int requested = Math.max(0, reported - Math.max(0, count));
        int consumed = TaczLoadedCompat.consumeOwnerAmmo(context, requested);
        ammoBox.getOrCreateTag().putInt(
                TaczLoadedCompat.LAST_REPORTED_TAG, Math.max(0, reported - consumed));
    }

    @Override
    public boolean isAmmoBoxOfGun(ItemStack gun, ItemStack ammoBox) {
        TaczLoadedCompat.LinkContext context = TaczLoadedCompat.resolve(ammoBox);
        return context != null
                && TaczLoadedCompat.sameGun(context.gun(), gun)
                && (context.owner().isCreative()
                || TaczLoadedCompat.countOwnerAmmo(context) > 0);
    }

    @Override
    public ItemStack setAmmoLevel(ItemStack ammoBox, int ammoLevel) {
        return ammoBox;
    }

    @Override
    public int getAmmoLevel(ItemStack ammoBox) {
        return 0;
    }

    @Override
    public boolean isCreative(ItemStack ammoBox) {
        TaczLoadedCompat.LinkContext context = TaczLoadedCompat.resolve(ammoBox);
        return context != null && context.owner().isCreative();
    }

    @Override
    public boolean isAllTypeCreative(ItemStack ammoBox) {
        return false;
    }

    @Override
    public ItemStack setCreative(ItemStack ammoBox, boolean isAllType) {
        return ammoBox;
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return false;
    }
}

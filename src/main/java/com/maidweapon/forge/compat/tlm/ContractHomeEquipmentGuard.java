package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.interior.ContractInteriorService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Reject player gear transfers before mutation; never delete an inventory stack. */
@Mod.EventBusSubscriber
public final class ContractHomeEquipmentGuard {
    public static boolean resident(Entity entity) {
        return entity != null && entity.level().dimension().equals(ContractInteriorService.INTERIOR_LEVEL)
                && TlmEntityAdapter.isMaidEntity(entity)
                && !entity.getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID).isEmpty();
    }
    public static boolean gear(ItemStack stack) {
        return !stack.isEmpty() && (MaidInfusion.isWeapon(stack)
                || stack.getItem() instanceof ArmorItem || stack.getItem() instanceof ShieldItem
                || stack.getItem() instanceof net.minecraft.world.item.ElytraItem);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOpen(PlayerContainerEvent.Open event) {
        if (protect(event.getEntity(), event.getContainer()))
            event.getEntity().displayClientMessage(Component.translatable("maid_weapon.home.equipment_locked"), true);
    }
    public static boolean protect(Player player, AbstractContainerMenu menu) {
        Entity maid;
        try {
            Object value = menu.getClass().getMethod("getMaid").invoke(menu);
            if (!(value instanceof Entity entity) || !resident(entity)) return false;
            maid = entity;
        } catch (ReflectiveOperationException | RuntimeException unrelated) { return false; }
        boolean main = false;
        for (Class<?> type = menu.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidMainContainer")) main = true;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container == player.getInventory() || slot instanceof GuardedSlot) continue;
            // TLM main menus put the owner's 36 slots first, then armor + hands.
            // Other maid equipment/bauble slots are guarded conservatively.
            boolean equipment = !main || (i >= 36 && i < 42);
            Slot guarded = new GuardedSlot(slot, maid, equipment);
            guarded.index = slot.index;
            menu.slots.set(i, guarded);
        }
        return true;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) { blockGear(event, event.getTarget()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) { blockGear(event, event.getTarget()); }
    private static void blockGear(PlayerInteractEvent event, Entity target) {
        if (!resident(target) || !gear(event.getItemStack())) return;
        // A bound carrier is allowed to open the existing resident's GUI; the
        // item itself returns PASS rather than equipping/capturing again.
        if (MaidInfusion.isInfused(event.getItemStack())) return;
        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        event.setCanceled(true);
        if (!event.getLevel().isClientSide)
            event.getEntity().displayClientMessage(Component.translatable("maid_weapon.home.equipment_locked"), true);
    }
    private static final class GuardedSlot extends Slot {
        private final Slot original;
        private final Entity maid;
        private final boolean equipment;
        GuardedSlot(Slot original, Entity maid, boolean equipment) {
            super(original.container, original.getSlotIndex(), original.x, original.y);
            this.original = original; this.maid = maid; this.equipment = equipment;
        }
        @Override public boolean mayPlace(ItemStack stack) {
            return (!resident(maid) || (!equipment && !gear(stack))) && original.mayPlace(stack);
        }
        @Override public boolean mayPickup(Player player) { return (!resident(maid) || !equipment) && original.mayPickup(player); }
        @Override public ItemStack getItem() { return original.getItem(); }
        @Override public void set(ItemStack stack) { original.set(stack); }
        @Override public void setByPlayer(ItemStack stack) { original.setByPlayer(stack); }
        @Override public void setChanged() { original.setChanged(); }
        @Override public ItemStack remove(int count) { return original.remove(count); }
        @Override public int getMaxStackSize() { return original.getMaxStackSize(); }
        @Override public int getMaxStackSize(ItemStack stack) { return original.getMaxStackSize(stack); }
        @Override public boolean isActive() { return original.isActive(); }
        @Override public void onTake(Player player, ItemStack stack) { original.onTake(player, stack); }
        @Override public com.mojang.datafixers.util.Pair<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> getNoItemIcon() {
            return original.getNoItemIcon();
        }
    }
    private ContractHomeEquipmentGuard() {}
}

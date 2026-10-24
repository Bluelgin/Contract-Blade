package com.maidweapon.forge.compat.tlm;

import net.minecraft.world.entity.LivingEntity;

/** Storage-only meal cleanup; ordinary equipment maintenance must never cancel eating. */
public final class TlmMealLifecycle {
    public static void prepareForStorage(LivingEntity maid) {
        if (!maid.isUsingItem() || !maid.getUseItem().isEdible()
                || !TlmEntityAdapter.isMaidEntity(maid)) return;
        var hand = maid.getUsedItemHand();
        maid.stopUsingItem();
        // Native TLM borrows only the offhand. Addons may eat from the main hand;
        // in that case leave both real hand stacks untouched for serialization.
        if (hand != net.minecraft.world.InteractionHand.OFF_HAND) return;
        try {
            var restore = maid.getClass().getDeclaredMethod("backCurrentHandItemStack");
            restore.setAccessible(true);
            restore.invoke(maid);
        } catch (ReflectiveOperationException ignored) {
            // Preserve the hand in entity NBT if this native helper is unavailable.
        }
    }

    private TlmMealLifecycle() { }
}

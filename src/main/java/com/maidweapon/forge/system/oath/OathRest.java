package com.maidweapon.forge.system.oath;

import net.minecraft.world.entity.player.Player;

/** Per-maid recovery, independent of refreshed player backlash stacks. Offline time does not advance it. */
public final class OathRest {
    public static boolean resting(Player owner, String maid) {
        if (owner == null || owner.level().isClientSide || owner.getServer() == null || maid == null) return false;
        if (!owner.hasEffect(OathEffects.BACKLASH.get())) {
            clear(owner);
            return false;
        }
        return OathSavedData.get(owner.getServer()).rests(owner.getUUID()).getInt(maid) > 0;
    }

    public static boolean deny(Player owner, String maid, boolean notify) {
        if (!resting(owner, maid)) return false;
        if (notify) owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("maid_weapon.oath.resting"), true);
        return true;
    }

    public static void begin(Player owner, String maid, int ticks) {
        var store = OathSavedData.get(owner.getServer());
        var rests = store.rests(owner.getUUID());
        rests.putInt(maid, Math.max(1, ticks));
        store.rests(owner.getUUID(), rests);
    }

    public static void tick(Player owner) {
        var store = OathSavedData.get(owner.getServer());
        var rests = store.rests(owner.getUUID());
        if (rests.isEmpty()) return;
        if (!owner.hasEffect(OathEffects.BACKLASH.get())) { clear(owner); return; }
        for (String maid : java.util.Set.copyOf(rests.getAllKeys())) {
            // A failed initial safe recall is retried; never manufacture a replacement entity.
            if (owner.tickCount % 20 == 0) {
                for (var carrier : owner.getInventory().items) {
                    if (!com.maidweapon.forge.system.contract.ContractCarrierData.isOwner(carrier, owner)
                            || !maid.equals(com.maidweapon.forge.system.contract.ContractCarrierData.getBoundMaidUUID(carrier))) continue;
                    if (com.maidweapon.forge.system.deployment.ContractWeaponLocator.findManifestedMaid(owner, maid) != null) {
                        try { com.maidweapon.forge.system.InfusedMaidDeploymentSystem.forceRecall(owner, maid, carrier, 0); }
                        catch (RuntimeException failure) {
                            com.mojang.logging.LogUtils.getLogger().warn("Oath resting maid recall failed owner={} maid={}", owner.getUUID(), maid, failure);
                        }
                    }
                    break;
                }
            }
            int remaining = rests.getInt(maid) - 1;
            if (remaining <= 0) rests.remove(maid);
            else rests.putInt(maid, remaining);
        }
        store.rests(owner.getUUID(), rests);
    }

    public static void clear(Player owner) {
        var store = OathSavedData.get(owner.getServer());
        if (!store.rests(owner.getUUID()).isEmpty()) store.rests(owner.getUUID(), new net.minecraft.nbt.CompoundTag());
    }
    private OathRest() { }
}

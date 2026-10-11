package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.system.fox.challenge.ShrineRitualSites;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Identity claiming and one-time tutorial; legacy narrative receipts remain dormant and intact. */
public final class ShrineFoxStory {
    public static final String OFFERING = FoxSpiritState.OFFERING;
    private static final String PROGRESS = "MaidWeaponShrineFoxStory";

    public static void observe(ServerPlayer player) {
        if (!SlashBladeCompat.isLoaded()) return;
        boolean hasOffering = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            FoxSpiritTransferService.claimOffering(player, stack);
            if (FoxSpiritTransferService.storyEligible(player, stack)) {
                hasOffering = true;
                break;
            }
        }
        if (!hasOffering) return;

        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag progress = persisted.getCompound(PROGRESS);
        long day = ShrineStoryPolicy.gameDay(player.getServer().overworld().getDayTime());
        boolean firstObservation = !progress.contains("FirstDay");
        if (firstObservation) progress.putLong("FirstDay", day);
        // Tutorial receipt is separate from dormant story progress, including legacy saves.
        boolean entryHint = !progress.getBoolean("EntryHint")
                && ShrineRitualSites.nearDais(player.serverLevel(), player.blockPosition());
        if (!firstObservation && !entryHint) return;
        if (entryHint) {
            player.sendSystemMessage(Component.translatable("maid_weapon.fox.challenge.entry_hint"));
            progress.putBoolean("EntryHint", true);
        }
        persisted.put(PROGRESS, progress);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static void copyProgress(Player oldPlayer, Player newPlayer) {
        CompoundTag old = oldPlayer.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!old.contains(PROGRESS)) return;
        CompoundTag data = newPlayer.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(PROGRESS, old.getCompound(PROGRESS).copy());
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private ShrineFoxStory() { }
}

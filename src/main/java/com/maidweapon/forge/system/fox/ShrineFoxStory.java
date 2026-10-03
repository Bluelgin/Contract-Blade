package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.api.EmbeddedSpiritApi;
import com.maidweapon.forge.api.IntrinsicSpiritApi;
import com.maidweapon.forge.compat.BladeTetraStoryCompat;
import com.maidweapon.forge.compat.SlashBladeCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Narrative progress and initial identity claiming; no placeholder maid or forced teleport. */
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
        var beat = ShrineStoryPolicy.next(BladeTetraStoryCompat.isLoaded(),
                progress.getBoolean("Greeting"), progress.getBoolean("Encounter"),
                progress.getBoolean("EchoHeard"),
                BladeTetraStoryCompat.hasCompletedDivinePrologue(player));
        switch (beat) {
            case ENCOUNTER -> {
                boolean hasAkatsuki = hasAkatsuki(player);
                say(player, hasAkatsuki ? "akatsuki.recognize" : "white.intro");
                if (hasAkatsuki) say(player, "white.answer");
                say(player, "white.request");
                if (hasAkatsuki) say(player, "akatsuki.request");
                progress.putBoolean("Encounter", true);
            }
            case ECHO -> {
                say(player, "white.echo");
                progress.putBoolean("EchoHeard", true);
            }
            case GREETING -> {
                // Standalone SlashBlade never receives unavailable quests.
                say(player, "white.greeting");
                progress.putBoolean("Greeting", true);
            }
            case NONE -> { return; }
        }
        persisted.put(PROGRESS, progress);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static boolean hasAkatsuki(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && (IntrinsicSpiritApi.hasIntrinsicSpirit(stack, "blade_tetra:akatsuki")
                    || EmbeddedSpiritApi.isSpirit(stack, "blade_tetra:akatsuki")
                    || BladeTetraStoryCompat.isAkatsuki(stack))) return true;
        }
        return false;
    }

    public static void copyProgress(Player oldPlayer, Player newPlayer) {
        CompoundTag old = oldPlayer.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!old.contains(PROGRESS)) return;
        CompoundTag data = newPlayer.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(PROGRESS, old.getCompound(PROGRESS).copy());
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static void say(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("maid_weapon.fox." + key));
    }

    private ShrineFoxStory() { }
}

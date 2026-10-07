package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.api.EmbeddedSpiritApi;
import com.maidweapon.forge.api.IntrinsicSpiritApi;
import com.maidweapon.forge.compat.BladeTetraStoryCompat;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.system.fox.challenge.ShrineRitualSites;
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
        long day = ShrineStoryPolicy.gameDay(player.getServer().overworld().getDayTime());
        boolean firstObservation = !progress.contains("FirstDay");
        if (firstObservation) progress.putLong("FirstDay", day);
        boolean bladeTetra = BladeTetraStoryCompat.isLoaded();
        boolean inDivineDomain = bladeTetra && player.level().dimension().location()
                .toString().equals("blade_tetra:divine_domain");
        var storyProgress = new ShrineStoryPolicy.Progress(progress.getBoolean("Greeting"),
                progress.getBoolean("Encounter"), progress.getBoolean("Reunion"), progress.getBoolean("EchoHeard"),
                progress.getBoolean("DreamMemory") || progress.getBoolean("DreamHint"),
                progress.getBoolean("DivineRecognized") || progress.getBoolean("EchoHeard"));
        var context = new ShrineStoryPolicy.Context(bladeTetra, bladeTetra && hasAkatsuki(player),
                ShrineStoryPolicy.followingDay(progress.getLong("FirstDay"), day), inDivineDomain,
                BladeTetraStoryCompat.clearedRoute(player) != null);
        var beat = ShrineStoryPolicy.next(storyProgress, context);
        boolean dreamHint = !bladeTetra && storyProgress.memoryHeard() && !progress.getBoolean("DreamHint")
                && ShrineRitualSites.nearDais(player.serverLevel(), player.blockPosition());
        if (beat == ShrineStoryPolicy.Beat.NONE && !dreamHint && !firstObservation) return;
        switch (beat) {
            case ENCOUNTER -> {
                say(player, "white.intro");
                say(player, "white.black_fox");
                say(player, "white.request");
                say(player, "white.wait");
                progress.putBoolean("Encounter", true);
            }
            case REUNION -> {
                say(player, "akatsuki.recognize");
                say(player, "white.answer");
                say(player, "akatsuki.ask");
                say(player, "white.reunion_news");
                if (storyProgress.domainRecognized() || BladeTetraStoryCompat.hasVisitedDivineDomain(player)) {
                    say(player, "akatsuki.request");
                } else {
                    say(player, "white.route_unknown");
                    say(player, "akatsuki.mikage");
                }
                say(player, "white.reunion_request");
                progress.putBoolean("Greeting", true);
                progress.putBoolean("Encounter", true);
                progress.putBoolean("Reunion", true);
            }
            case ECHO -> {
                say(player, "white.echo");
                progress.putBoolean("EchoHeard", true);
            }
            case GREETING -> {
                say(player, "white.greeting");
                progress.putBoolean("Greeting", true);
            }
            case MEMORY -> {
                say(player, "challenge.white.dream_memory");
                progress.putBoolean("DreamMemory", true);
                if (ShrineRitualSites.nearDais(player.serverLevel(), player.blockPosition())) {
                    say(player, "challenge.white.dream");
                    progress.putBoolean("DreamHint", true);
                } else {
                    say(player, "challenge.white.dream_return");
                }
            }
            case ARRIVAL -> {
                say(player, "white.domain_sense");
                progress.putBoolean("DivineRecognized", true);
            }
            case NONE -> { }
        }
        if (dreamHint) {
            say(player, "challenge.white.dream");
            progress.putBoolean("DreamHint", true);
        }
        persisted.put(PROGRESS, progress);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static boolean hasHeardDivineEcho(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                .getCompound(PROGRESS).getBoolean("EchoHeard");
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

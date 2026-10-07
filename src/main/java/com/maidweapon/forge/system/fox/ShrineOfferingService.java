package com.maidweapon.forge.system.fox;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.forge.compat.SlashBladeCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;

/** One stand, one payer, one cake. Blade capabilities and spirit data remain untouched. */
public final class ShrineOfferingService {
    public static final String STAND_TAG = "maid_weapon_shrine_offering";
    public static final String ACCEPTED_BY = "MaidWeaponShrineOfferingPlayer";
    public static final String TAKEN = "MaidWeaponShrineOfferingTaken";
    private static final String DIALOGUE_NOTICE = "MaidWeaponShrineDialogueNotice";

    public static boolean isProtected(ItemFrame stand) {
        var blade = stand.getItem();
        return stand.getTags().contains(STAND_TAG) && !stand.getPersistentData().getBoolean(TAKEN)
                && blade.hasTag() && blade.getTag().getBoolean(FoxSpiritState.OFFERING)
                && SlashBladeCompat.isNamedBlade(blade, "item.slashblade.fox_white");
    }

    public static long totalKills(ServerPlayer player) {
        long total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            total += SlashBladeCompat.getKillCount(player.getInventory().getItem(slot));
        }
        return total;
    }

    /** Called at the actual native interaction, not an earlier cancellable Forge click event. */
    public static boolean take(ItemFrame stand, ServerPlayer player, InteractionHand hand) {
        if (!isProtected(stand)) return true;
        if (hand != InteractionHand.MAIN_HAND) return false;
        if (player.isShiftKeyDown()) {
            hint(player, "stop_sneaking");
            return false;
        }
        var data = stand.getPersistentData();
        if (data.hasUUID(ACCEPTED_BY) && !data.getUUID(ACCEPTED_BY).equals(player.getUUID())) {
            say(stand, player, "reserved");
            return false;
        }
        var held = player.getItemInHand(hand);
        if (!held.isEmpty() && !held.is(Items.CAKE)) {
            hint(player, data.hasUUID(ACCEPTED_BY) ? "empty_hand" : "offer_cake");
            return false;
        }
        if (totalKills(player) < MaidWeaponConfig.SHRINE_WHITE_FOX_KILLS.get()) {
            needKills(stand, player);
            return false;
        }
        if (!data.hasUUID(ACCEPTED_BY)) {
            if (!held.is(Items.CAKE)) {
                say(stand, player, "cake");
                hint(player, "offer_cake");
                return false;
            }
            data.putUUID(ACCEPTED_BY, player.getUUID());
            held.shrink(1);
            say(stand, player, "accepted");
            hint(player, "empty_hand");
            // Always consume this click, including when the last cake leaves an empty hand.
            return false;
        }
        if (!held.isEmpty()) {
            hint(player, "empty_hand");
            return false;
        }
        return true;
    }

    /** Release protection only after the native method actually delivers the blade. */
    public static void finishTake(ItemFrame stand, ServerPlayer player, InteractionHand hand) {
        var data = stand.getPersistentData();
        var received = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND && stand.getItem().isEmpty()
                && stand.getTags().contains(STAND_TAG) && data.hasUUID(ACCEPTED_BY)
                && data.getUUID(ACCEPTED_BY).equals(player.getUUID())
                && received.hasTag() && received.getTag().getBoolean(FoxSpiritState.OFFERING)
                && SlashBladeCompat.isNamedBlade(received, "item.slashblade.fox_white")) {
            data.putBoolean(TAKEN, true);
            // Record the acquisition date after delivery, including a click just before dawn.
            ShrineFoxStory.observe(player);
        }
    }

    private static void needKills(ItemFrame stand, ServerPlayer player) {
        say(stand, player, "kills");
        showProgress(player);
    }

    private static void showProgress(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("maid_weapon.fox.offering.progress",
                totalKills(player), MaidWeaponConfig.SHRINE_WHITE_FOX_KILLS.get()), true);
    }

    private static void hint(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("maid_weapon.fox.offering." + key), true);
    }

    private static void say(ItemFrame stand, ServerPlayer player, String key) {
        // A bounded receipt prevents repeated identical clicks from flooding chat.
        var notice = player.getPersistentData().getCompound(DIALOGUE_NOTICE);
        long now = stand.level().getGameTime();
        if (notice.hasUUID("Stand") && notice.getUUID("Stand").equals(stand.getUUID())
                && key.equals(notice.getString("Line"))
                && now >= notice.getLong("Time") && now - notice.getLong("Time") < 100) return;
        notice.putUUID("Stand", stand.getUUID());
        notice.putString("Line", key);
        notice.putLong("Time", now);
        player.getPersistentData().put(DIALOGUE_NOTICE, notice);
        player.sendSystemMessage(Component.translatable("maid_weapon.fox.offering." + key));
    }

    private ShrineOfferingService() { }
}

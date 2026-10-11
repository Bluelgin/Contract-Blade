package com.maidweapon.forge.event;

import com.maidweapon.forge.compat.BlackFoxSwordArtCompat;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class BlackFoxSwordArtEvents {
    @SubscribeEvent public static void projectile(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile shot
                && shot.getOwner() instanceof com.maidweapon.forge.entity.BlackFoxSwordActorEntity actor) actor.capture(shot);
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (!player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getBoolean("MaidWeaponBlackFoxDefeated")) return;
        // Migrate only the victorious owner's original rescued blade, without overriding later SA choices.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var blade = player.getInventory().getItem(slot);
            var identity = FoxSpiritState.resident(blade);
            if (FoxSpiritState.isOriginalHome(blade) && identity.getString("SpiritId").equals(FoxSpiritState.BLACK)
                    && identity.getUUID("OwnerUUID").equals(player.getUUID())) BlackFoxSwordArtCompat.unlock(blade, player);
        }
    }
    @SubscribeEvent public static void tooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        if (event.getItemStack().hasTag() && event.getItemStack().getTag().contains("MaidWeaponBlackFoxSwordArt"))
            event.getToolTip().add(net.minecraft.network.chat.Component.translatable("maid_weapon.fox.sa.tooltip").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer player) BlackFoxSwordArtCompat.clear(player); }
    @SubscribeEvent public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer player) BlackFoxSwordArtCompat.clear(player); }
    @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer player) BlackFoxSwordArtCompat.clear(player); }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) { BlackFoxSwordArtCompat.clearAll(); }
    private BlackFoxSwordArtEvents() { }
}

package com.maidweapon.forge.event;

import com.maidweapon.forge.compat.BlackFoxSwordArtCompat;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class BlackFoxSwordArtEvents {
    @SubscribeEvent public static void projectile(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile shot)) return;
        // Native WaveEdge joins its drives before assigning the shooter. Supply the actual scoped actor at birth.
        var pending = com.maidweapon.forge.compat.BlackFoxCoreNativeBarrage.launchingActor();
        if (shot.getOwner() == null && pending != null && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(shot.getType())
                .equals(net.minecraft.resources.ResourceLocation.parse("slashblade:drive"))) shot.setOwner(pending);
        if (shot.getOwner() instanceof com.maidweapon.forge.entity.BlackFoxSwordActorEntity actor) actor.capture(shot);
    }
    private static com.maidweapon.forge.entity.BlackFoxSwordActorEntity sourceActor(net.minecraft.world.damagesource.DamageSource source) {
        var scoped = com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.projectile();
        var origin = scoped == null ? source.getDirectEntity() : scoped;
        var actor = origin instanceof net.minecraft.world.entity.projectile.Projectile shot ? shot.getOwner() : source.getEntity();
        return actor instanceof com.maidweapon.forge.entity.BlackFoxSwordActorEntity swordActor ? swordActor : null;
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void damage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0) return;
        var actor = sourceActor(event.getSource());
        if (actor != null && actor.permits(event.getEntity()) && actor.masteryCredit() != null)
            actor.core().onNativeHit(actor.contractOwner(),event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player && event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity enemy) {
            var core = BlackFoxSwordArtCompat.active(player);
            if (core != null) core.onOwnerHurt(player,enemy);
        }
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void markedDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide) for (var core : BlackFoxSwordArtCompat.activeCores())
            if (!core.isRemoved() && core.level() == event.getEntity().level()) core.onMarkedDeath(event.getEntity());
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        BlackFoxSwordArtCompat.refreshGrowth(player);
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

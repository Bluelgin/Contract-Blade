package com.maidweapon.forge.event;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.compat.ModCompatManager;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.system.MonsterTierRegistry;
import com.maidweapon.common.system.ResonanceSystem;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.ChallengeTracker;
import com.maidweapon.forge.system.SinFragmentSystem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Contract growth and resonance rules. */
@Mod.EventBusSubscriber
public final class ContractCombatEventHandler {
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        ItemStack weapon = player.getMainHandItem();
        if (!MaidInfusion.isInfused(weapon) || !MaidWeaponItem.isOwner(weapon, player)) return;

        String targetId = entityId(event.getEntity());
        int tier = MonsterTierRegistry.getTier(targetId);
        if (tier <= 0) tier = MonsterTierRegistry.estimateTierFromHealth(
                event.getEntity().getMaxHealth());
        UUID playerId = player.getUUID();
        Integer previous = ChallengeTracker.getTier(playerId);
        if (previous == null || tier > previous) ChallengeTracker.setTier(playerId, tier);

        boolean boss = MonsterTierRegistry.isBoss(targetId)
                || ModCompatManager.getExternalBosses().getOrDefault(targetId, false)
                || MonsterTierRegistry.isBossFromHealth(event.getEntity().getMaxHealth());
        if (boss) ChallengeTracker.setHasBoss(playerId, true);
        if ("minecraft:ender_dragon".equals(targetId)) ChallengeTracker.setHasDragon(playerId, true);
        if ("minecraft:wither".equals(targetId)) ChallengeTracker.setHasWither(playerId, true);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        ItemStack weapon = player.getMainHandItem();
        if (!MaidInfusion.isInfused(weapon) || !MaidWeaponItem.isOwner(weapon, player)) return;

        UUID playerId = player.getUUID();
        Integer tier = ChallengeTracker.getTier(playerId);
        if (tier == null) return;
        if (Boolean.TRUE.equals(ChallengeTracker.isFailed(playerId))) {
            ChallengeTracker.clear(playerId);
            return;
        }

        MaidWeaponData data = MaidWeaponItem.getMaidData(weapon);
        data.addKill();
        data.addResonance(5);
        int oldLevel = data.getLevel();
        boolean upgraded = data.tryUpgrade(tier, ChallengeTracker.hasDragon(playerId),
                ChallengeTracker.hasWither(playerId));
        if (upgraded) {
            player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        50, 1.5, 1.0, 1.5, 0.5);
            }
            player.displayClientMessage(Component.translatable(
                    "maid_weapon.message.weapon_upgraded", data.getLevel()), true);
            ModCompatManager.notifyUpgrade("maid_sword", oldLevel, data.getLevel());
        }

        String targetId = entityId(event.getEntity());
        ModCompatManager.notifyMonsterKilled("maid_sword", targetId, tier,
                ChallengeTracker.hasBoss(playerId));
        MaidWeaponItem.setMaidData(weapon, data);
        SinFragmentSystem.consumeOnKill(player, weapon);
        ChallengeTracker.clear(playerId);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID playerId = player.getUUID();
        if (ChallengeTracker.getTier(playerId) == null) return;
        ItemStack weapon = player.getMainHandItem();
        if (MaidInfusion.isInfused(weapon) && MaidWeaponItem.isOwner(weapon, player)) {
            MaidWeaponData data = MaidWeaponItem.getMaidData(weapon);
            data.reduceResonance(MaidWeaponConfig.RESONANCE_DEATH_PENALTY.get());
            MaidWeaponItem.setMaidData(weapon, data);
        }
        ModCompatManager.notifyChallengeFailed("maid_sword", "unknown");
        ChallengeTracker.clear(playerId);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ChallengeTracker.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        ItemStack weapon = player.getMainHandItem();
        if (!MaidInfusion.isInfused(weapon) || !MaidWeaponItem.isOwner(weapon, player)) {
            weapon = player.getOffhandItem();
            if (!MaidInfusion.isInfused(weapon) || !MaidWeaponItem.isOwner(weapon, player)) return;
        }

        UUID playerId = player.getUUID();
        MaidWeaponData data = MaidWeaponItem.getMaidData(weapon);
        int food = player.getFoodData().getFoodLevel();
        Integer lastFood = ChallengeTracker.getLastFoodLevel(playerId);
        if (lastFood != null && food > lastFood) {
            ResonanceSystem.onEat(data, food - lastFood);
            MaidWeaponItem.setMaidData(weapon, data);
        }
        ChallengeTracker.setFoodLevel(playerId, food);

        float health = player.getHealth();
        Float lastHealth = ChallengeTracker.getLastHealth(playerId);
        if (lastHealth != null && health > lastHealth) {
            ResonanceSystem.onHeal(data, health - lastHealth);
            MaidWeaponItem.setMaidData(weapon, data);
        } else if (lastHealth != null && health < lastHealth) {
            ResonanceSystem.onOwnerHurt(data, lastHealth - health);
            MaidWeaponItem.setMaidData(weapon, data);
        }
        ChallengeTracker.setHealth(playerId, health);
    }

    private static String entityId(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id.toString();
    }

    private ContractCombatEventHandler() {
    }
}

package com.maidweapon.forge.event;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.compat.ModCompatManager;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.system.MonsterTierRegistry;
import com.maidweapon.common.system.ResonanceSystem;
import com.maidweapon.forge.system.resonance.*;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.ChallengeTracker;
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
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0) return;
        com.maidweapon.forge.system.mastery.ContractCombatCredits.record(event.getEntity(), event.getSource());
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled()) return;
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer dead) {
            com.maidweapon.forge.system.mastery.ContractCombatCredits.invalidate(dead.getUUID());
            ItemStack weapon = dead.getMainHandItem();
            if (MaidInfusion.isInfused(weapon) && ContractCarrierData.isOwner(weapon, dead)) {
                ResonanceService.drainLegacy(dead, weapon, MaidWeaponConfig.RESONANCE_DEATH_PENALTY.get());
            }
            ModCompatManager.notifyChallengeFailed("maid_sword", "unknown");
            ChallengeTracker.clear(dead.getUUID());
            return;
        }
        var credit = com.maidweapon.forge.system.mastery.ContractCombatCredits.settle(event.getEntity(), event.getSource());
        if (credit == null) return;
        var player = credit.player(); ItemStack weapon = credit.carrier();
        com.maidweapon.forge.system.mastery.ContractMasteryService.observe(player, weapon);
        String targetId = entityId(event.getEntity());
        int tier = MonsterTierRegistry.getTier(targetId);
        if (tier <= 0) tier = MonsterTierRegistry.estimateTierFromHealth(event.getEntity().getMaxHealth());
        com.maidweapon.forge.system.mastery.ContractMasteryService.tryAdvance(player, tier, targetId);
        ResonanceService.recoverLegacy(player, weapon, ContractCarrierData.getBindingId(weapon),
                com.maidweapon.common.ContractRulesConfig.KILL_REWARD.get());
        boolean boss = MonsterTierRegistry.isBoss(targetId)
                || ModCompatManager.getExternalBosses().getOrDefault(targetId, false)
                || MonsterTierRegistry.isBossFromHealth(event.getEntity().getMaxHealth());
        ModCompatManager.notifyMonsterKilled("maid_sword", targetId, tier, boss);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        ItemStack weapon = player.getMainHandItem();
        if (!MaidInfusion.isInfused(weapon) || !ContractCarrierData.isOwner(weapon, player)) {
            weapon = player.getOffhandItem();
            if (!MaidInfusion.isInfused(weapon) || !ContractCarrierData.isOwner(weapon, player)) return;
        }

        com.maidweapon.forge.system.mastery.ContractMasteryService.observe(player, weapon);
        if (ResonanceContract.resolve(player, weapon, ContractCarrierData.getBindingId(weapon)) == null) return;
        UUID playerId = player.getUUID();
        String binding = ContractCarrierData.getBindingId(weapon);
        int food = player.getFoodData().getFoodLevel();
        Integer lastFood = ChallengeTracker.getLastFoodLevel(playerId);
        if (lastFood != null && food > lastFood)
            ResonanceService.recoverLegacy(player, weapon, binding,
                    ResonanceSystem.scaled(food - lastFood, MaidWeaponConfig.RESONANCE_FOOD_MULTIPLIER.get()));
        ChallengeTracker.setFoodLevel(playerId, food);

        float health = player.getHealth();
        Float lastHealth = ChallengeTracker.getLastHealth(playerId);
        if (lastHealth != null && health > lastHealth)
            ResonanceService.recoverLegacy(player, weapon, binding,
                    ResonanceSystem.scaled(health - lastHealth, MaidWeaponConfig.RESONANCE_HEAL_MULTIPLIER.get()));
        else if (lastHealth != null && health < lastHealth)
            ResonanceService.recoverLegacy(player, weapon, binding,
                    ResonanceSystem.scaled(lastHealth - health, MaidWeaponConfig.RESONANCE_OWNER_HURT_MULTIPLIER.get()));
        ChallengeTracker.setHealth(playerId, health);
    }

    private static String entityId(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id.toString();
    }

    private ContractCombatEventHandler() {
    }
}

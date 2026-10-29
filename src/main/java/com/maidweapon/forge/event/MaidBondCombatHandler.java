package com.maidweapon.forge.event;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.forge.system.resonance.*;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.MaidCareTaskSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Combat and recovery rules for the short-term contract resonance resource. */
@Mod.EventBusSubscriber
public final class MaidBondCombatHandler {
    private static final Map<LivingEntity, Map<String, Long>> OATH_COOLDOWNS = new java.util.WeakHashMap<>();
    /** Reuse the effective-hit records and 60 tick window. No independent hit tracker or hurt call. */
    public static boolean oathCooperation(Player owner, LivingEntity target, boolean maidAttack) {
        if (!(target instanceof net.minecraft.world.entity.monster.Enemy) || target.isAlliedTo(owner)) return false;
        String binding = com.maidweapon.forge.system.oath.OathService.binding(owner);
        if (binding.isEmpty()) return false;
        long time = owner.level().getGameTime();
        String key = key(owner.getUUID(), target.getUUID());
        Long previous = maidAttack ? PLAYER_HITS.get(key) : MAID_HITS.get(key + ":" + binding);
        if (previous == null || time < previous || time - previous > 60) return false;
        var cooldown = OATH_COOLDOWNS.computeIfAbsent(target, entity -> new HashMap<>());
        String actor = owner.getStringUUID() + ":" + binding;
        long last = cooldown.getOrDefault(actor, Long.MIN_VALUE / 2);
        if (time - last < com.maidweapon.common.OathConfig.COOP_INTERVAL.get()) return false;
        cooldown.put(actor, time);
        return true;
    }
    private static final Map<String, Long> PLAYER_HITS = new HashMap<>();
    private static final Map<String, Long> MAID_HITS = new HashMap<>();
    private static final Map<String, Long> LAST_COOP_REWARD = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0) return;
        Entity attacker = event.getSource().getEntity();

        if (event.getEntity() instanceof Player owner) {
            markCombat(owner, owner.level().getGameTime());
        }
        if (attacker instanceof Player player) recordPlayerHit(player, event.getEntity());
        if (TouhouLittleMaidHelper.isMaidEntity(attacker)) {
            handleMaidAttack(attacker, event.getEntity());
        }
        if (TouhouLittleMaidHelper.isMaidEntity(event.getEntity())) {
            handleMaidHurt(event, event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidDeath(LivingDeathEvent event) {
        if (!TouhouLittleMaidHelper.isMaidEntity(event.getEntity())) return;
        Player owner = getOwner(event.getEntity());
        if (owner == null) return;
        if (protectContractDeath(owner, event.getEntity()) != DeathProtection.NOT_CONTRACT) event.setCanceled(true);
    }

    public enum DeathProtection { NOT_CONTRACT, RECALLED, LIVE_PROTECTED_RECALL_FAILED }
    /** True means data was committed and recall completed, not merely that death was intercepted. */
    public static boolean preventContractDeath(Player owner, LivingEntity maid) {
        return protectContractDeath(owner, maid) == DeathProtection.RECALLED;
    }
    public static DeathProtection protectContractDeath(Player owner, LivingEntity maid) {
        if (maid.level().isClientSide) return DeathProtection.NOT_CONTRACT;
        var carrier = com.maidweapon.forge.system.deployment.ContractEmergencyCarrier.resolve(owner, maid);
        ItemStack weapon = carrier.stack();
        if (!isActiveContract(owner, weapon)
                || !com.maidweapon.forge.api.ContractAuthorization.allows(owner, weapon)) return DeathProtection.NOT_CONTRACT;
        maid.setHealth(Math.max(1.0f, maid.getHealth()));
        var failure = com.maidweapon.forge.system.contract.ContractAnomalyRecoveryService.incident(owner, weapon);
        if (failure.getString("Status").equals("PENDING") && failure.getString("Reason").equals("RECALL_FAILED")
                && owner.getServer().overworld().getGameTime() - failure.getLong("Detected") < 40)
            return DeathProtection.LIVE_PROTECTED_RECALL_FAILED;
        boolean recalled;
        try { recalled = InfusedMaidDeploymentSystem.forceRecall(owner, maid.getStringUUID(), weapon, 300); }
        catch (RuntimeException recallError) { recalled = false; }
        if (recalled) {
            carrier.publish(owner);
            return DeathProtection.RECALLED;
        }
        return DeathProtection.LIVE_PROTECTED_RECALL_FAILED;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = event.getEntity().getUUID();
        OATH_COOLDOWNS.values().forEach(map -> map.keySet().removeIf(key -> key.startsWith(player + ":")));
        LAST_COOP_REWARD.keySet().removeIf(key -> key.startsWith(player + ":"));
        PLAYER_HITS.keySet().removeIf(key -> key.startsWith(player + ":"));
        MAID_HITS.keySet().removeIf(key -> key.startsWith(player + ":"));
    }

    private static void recordPlayerHit(Player player, LivingEntity target) {
        long time = player.level().getGameTime();
        markCombat(player, time);
        List<ItemStack> contracts = followingContracts(player);
        if (contracts.isEmpty()) return;
        String key = key(player.getUUID(), target.getUUID());
        PLAYER_HITS.put(key, time);
        for (ItemStack weapon : contracts) {
            Long maidHit = MAID_HITS.get(key + ":" + ContractCarrierData.ensureBindingId(weapon));
            if (maidHit != null && time - maidHit <= 60) rewardCooperation(player, time, weapon);
        }
    }

    private static void handleMaidAttack(Entity maid, LivingEntity target) {
        Player owner = getOwner(maid);
        if (owner == null) return;
        ItemStack weapon = InfusedMaidDeploymentSystem.findBoundWeapon(owner, maid.getStringUUID());
        if (!isActiveContract(owner, weapon)) return;

        long time = owner.level().getGameTime();
        markCombat(owner, time);
        String key = key(owner.getUUID(), target.getUUID());
        MAID_HITS.put(key + ":" + ContractCarrierData.ensureBindingId(weapon), time);
        Long playerHit = PLAYER_HITS.get(key);
        if (playerHit != null && time - playerHit <= 60) {
            rewardCooperation(owner, time, weapon);
        }
    }

    private static void handleMaidHurt(LivingDamageEvent event, LivingEntity maid) {
        Player owner = getOwner(maid);
        if (owner == null) return;
        ItemStack weapon = InfusedMaidDeploymentSystem.findBoundWeapon(owner, maid.getStringUUID());
        if (!isActiveContract(owner, weapon)) return;

        markCombat(owner, owner.level().getGameTime());
        if (maid.getHealth() - event.getAmount() <= 1.0f) {
            event.setCanceled(true);
            // Preserve the injury and all recovered resonance; recall does not heal.
            maid.setHealth(1.0f);
            InfusedMaidDeploymentSystem.forceRecall(owner, maid.getStringUUID(), weapon, 300);
        }
    }

    private static void rewardCooperation(Player player, long time, ItemStack weapon) {
        String bindingKey = player.getUUID() + ":" + ContractCarrierData.ensureBindingId(weapon);
        long last = LAST_COOP_REWARD.getOrDefault(bindingKey, Long.MIN_VALUE / 2);
        if (time - last < 20) return;
        ResonanceService.recoverLegacy(player, weapon, ContractCarrierData.getBindingId(weapon), MaidWeaponConfig.RESONANCE_COOP_REWARD.get());
        LAST_COOP_REWARD.put(bindingKey, time);
    }

    private static void markCombat(Player player, long time) {
        var seen = new HashSet<String>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack weapon = player.getInventory().getItem(slot);
            for (var channel : com.maidweapon.forge.system.contract.ContractChannelStorage.channels(weapon)) {
                String binding = channel.getString("MaidBindingId");
                if (seen.add(binding)) ResonanceService.reportActivity(player, weapon, binding,
                        "owner_combat", ResonanceActivity.COMBAT,
                        Math.max(1, Math.min(1200, MaidWeaponConfig.RESONANCE_RECOVERY_DELAY.get())));
            }
        }
        MaidCareTaskSystem.markDanger(player);
    }

    private static boolean isActiveContract(Player player, ItemStack weapon) {
        return !weapon.isEmpty() && MaidInfusion.isInfused(weapon)
                && ContractCarrierData.isOwner(weapon, player)
                && !ContractCarrierData.isContractSuperseded(weapon);
    }

    /** Resolve actual following companions, not the player's current selection. */
    private static List<ItemStack> followingContracts(Player player) {
        List<ItemStack> result = new ArrayList<>();
        var seen = new HashSet<String>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack weapon = player.getInventory().getItem(slot);
            if (!isActiveContract(player, weapon)
                    || com.maidweapon.forge.system.deployment.ContractTransferSafetyService.isProjectionPhantom(weapon)
                    || !InfusedMaidDeploymentSystem.hasDeployedMaid(player, weapon)
                    || com.maidweapon.forge.system.deployment.ContractCompanionService.isResident(player, weapon)) continue;
            if (seen.add(ContractCarrierData.ensureBindingId(weapon))) result.add(weapon);
        }
        return result;
    }

    public static Player getOwner(Entity maid) {
        if (!(maid instanceof TamableAnimal tamable) || tamable.getOwnerUUID() == null
                || maid.getServer() == null) return null;
        ServerPlayer player = maid.getServer().getPlayerList().getPlayer(tamable.getOwnerUUID());
        return player;
    }

    private static String key(UUID player, UUID target) {
        return player + ":" + target;
    }

    private MaidBondCombatHandler() {}
}

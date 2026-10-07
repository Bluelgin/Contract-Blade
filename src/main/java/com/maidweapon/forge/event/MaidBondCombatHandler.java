package com.maidweapon.forge.event;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.system.ResonanceSystem;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.MaidCareTaskSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
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
    private static final Map<String, Long> PLAYER_HITS = new HashMap<>();
    private static final Map<String, Long> MAID_HITS = new HashMap<>();
    private static final Map<String, Long> LAST_COOP_REWARD = new HashMap<>();
    private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity attacker = event.getSource().getEntity();

        if (event.getEntity() instanceof Player owner) {
            if (!followingContracts(owner).isEmpty()) {
                markCombat(owner, owner.level().getGameTime());
            }
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
        if (preventContractDeath(owner, event.getEntity())) event.setCanceled(true);
    }

    /** Called before TLM's custom death pipeline as well as vanilla death. */
    public static boolean preventContractDeath(Player owner, LivingEntity maid) {
        if (maid.level().isClientSide) return false;
        String maidId = maid.getStringUUID();
        var carrier = com.maidweapon.forge.system.deployment.ContractEmergencyCarrier.resolve(owner, maid);
        ItemStack weapon = carrier.stack();
        if (!isActiveContract(owner, weapon)) return false;

        maid.setHealth(1.0f);
        spend(weapon, MaidWeaponConfig.RESONANCE_MAID_EMERGENCY_COST.get());
        InfusedMaidDeploymentSystem.forceRecall(owner, maidId, weapon, 300);
        carrier.publish(owner);
        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || event.player.tickCount % 20 != 0) return;

        Player player = event.player;
        for (ItemStack weapon : followingContracts(player)) tickResonance(player, weapon);
    }

    private static void tickResonance(Player player, ItemStack weapon) {
        MaidWeaponData data = MaidInfusion.data(weapon);
        long time = player.level().getGameTime();
        long lastCombat = LAST_COMBAT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        boolean inCombat = time - lastCombat <= MaidWeaponConfig.RESONANCE_RECOVERY_DELAY.get();

        if (inCombat) {
            int interval = MaidWeaponConfig.RESONANCE_COMBAT_DRAIN_INTERVAL.get();
            if (interval > 0 && time % interval == 0) data.reduceResonance(1);
        } else {
            ResonanceSystem.recover(data, MaidWeaponConfig.RESONANCE_PASSIVE_RECOVERY.get());
        }
        ContractCarrierData.setMaidData(weapon, data);

        if (data.getResonance() <= 0) {
            String maidId = ContractCarrierData.getBoundMaidUUID(weapon);
            if (maidId != null) InfusedMaidDeploymentSystem.forceRecall(player, maidId, 300);
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.resonance_depleted"), true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = event.getEntity().getUUID();
        LAST_COMBAT.remove(player);
        LAST_COOP_REWARD.keySet().removeIf(key -> key.startsWith(player + ":"));
        PLAYER_HITS.keySet().removeIf(key -> key.startsWith(player + ":"));
        MAID_HITS.keySet().removeIf(key -> key.startsWith(player + ":"));
    }

    private static void recordPlayerHit(Player player, LivingEntity target) {
        List<ItemStack> contracts = followingContracts(player);
        if (contracts.isEmpty()) return;
        long time = player.level().getGameTime();
        markCombat(player, time);
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
        int loss = Math.max(1, (int) Math.ceil(event.getAmount() / 4.0f));
        spend(weapon, loss);
        MaidWeaponData data = MaidInfusion.data(weapon);
        if (maid.getHealth() - event.getAmount() <= 1.0f || data.getResonance() <= 0) {
            event.setCanceled(true);
            maid.setHealth(Math.max(1.0f, maid.getHealth()));
            if (maid.getHealth() - event.getAmount() <= 1.0f) {
                spend(weapon, MaidWeaponConfig.RESONANCE_MAID_EMERGENCY_COST.get());
            }
            InfusedMaidDeploymentSystem.forceRecall(owner, maid.getStringUUID(), 300);
        }
    }

    private static void rewardCooperation(Player player, long time, ItemStack weapon) {
        String bindingKey = player.getUUID() + ":" + ContractCarrierData.ensureBindingId(weapon);
        long last = LAST_COOP_REWARD.getOrDefault(bindingKey, Long.MIN_VALUE / 2);
        if (time - last < 20) return;
        MaidWeaponData data = MaidInfusion.data(weapon);
        ResonanceSystem.recover(data, MaidWeaponConfig.RESONANCE_COOP_REWARD.get());
        ContractCarrierData.setMaidData(weapon, data);
        LAST_COOP_REWARD.put(bindingKey, time);
    }

    private static void spend(ItemStack weapon, int amount) {
        MaidWeaponData data = MaidInfusion.data(weapon);
        data.reduceResonance(amount);
        ContractCarrierData.setMaidData(weapon, data);
    }

    private static void markCombat(Player player, long time) {
        LAST_COMBAT.put(player.getUUID(), time);
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

    private static Player getOwner(Entity maid) {
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

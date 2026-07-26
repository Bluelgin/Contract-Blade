package com.maidweapon.forge.event;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.sin.SinType;
import com.maidweapon.common.system.ResonanceSystem;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
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

/** Combat and recovery rules for the short-term contract resonance resource. */
@Mod.EventBusSubscriber
public final class MaidBondCombatHandler {
    private static final Map<String, Long> PLAYER_HITS = new HashMap<>();
    private static final Map<String, Long> MAID_HITS = new HashMap<>();
    private static final Map<UUID, Long> LAST_COOP_REWARD = new HashMap<>();
    private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity attacker = event.getSource().getEntity();

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
        String maidId = event.getEntity().getStringUUID();
        ItemStack weapon = InfusedMaidDeploymentSystem.findBoundWeapon(owner, maidId);
        if (!isActiveContract(owner, weapon)) return;

        event.setCanceled(true);
        event.getEntity().setHealth(1.0f);
        spend(weapon, MaidWeaponConfig.RESONANCE_MAID_EMERGENCY_COST.get());
        InfusedMaidDeploymentSystem.forceRecall(owner, maidId, 300);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || event.player.tickCount % 20 != 0) return;

        Player player = event.player;
        ItemStack weapon = player.getMainHandItem();
        if (!isActiveContract(player, weapon)
                || !InfusedMaidDeploymentSystem.hasDeployedMaid(player, weapon)) return;

        MaidWeaponData data = MaidInfusion.data(weapon);
        long time = player.level().getGameTime();
        long lastCombat = LAST_COMBAT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        boolean inCombat = time - lastCombat <= MaidWeaponConfig.RESONANCE_RECOVERY_DELAY.get();

        if (inCombat) {
            int interval = MaidWeaponConfig.RESONANCE_COMBAT_DRAIN_INTERVAL.get();
            if (interval > 0 && time % interval == 0) data.reduceResonance(1);
        } else if (!data.hasSin(SinType.LUST)) {
            ResonanceSystem.recover(data, MaidWeaponConfig.RESONANCE_PASSIVE_RECOVERY.get());
        }
        MaidWeaponItem.setMaidData(weapon, data);

        if (data.getResonance() <= 0) {
            String maidId = MaidWeaponItem.getBoundMaidUUID(weapon);
            if (maidId != null) InfusedMaidDeploymentSystem.forceRecall(player, maidId, 300);
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.resonance_depleted"), true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = event.getEntity().getUUID();
        LAST_COMBAT.remove(player);
        LAST_COOP_REWARD.remove(player);
        PLAYER_HITS.keySet().removeIf(key -> key.startsWith(player + ":"));
        MAID_HITS.keySet().removeIf(key -> key.startsWith(player + ":"));
    }

    private static void recordPlayerHit(Player player, LivingEntity target) {
        ItemStack weapon = player.getMainHandItem();
        if (!isActiveContract(player, weapon)) return;
        long time = player.level().getGameTime();
        markCombat(player, time);
        String key = key(player.getUUID(), target.getUUID());
        PLAYER_HITS.put(key, time);
        Long maidHit = MAID_HITS.get(key);
        if (maidHit != null && time - maidHit <= 60) rewardCooperation(player, time, weapon);
    }

    private static void handleMaidAttack(Entity maid, LivingEntity target) {
        Player owner = getOwner(maid);
        if (owner == null) return;
        ItemStack weapon = InfusedMaidDeploymentSystem.findBoundWeapon(owner, maid.getStringUUID());
        if (!isActiveContract(owner, weapon)) return;

        long time = owner.level().getGameTime();
        markCombat(owner, time);
        String key = key(owner.getUUID(), target.getUUID());
        MAID_HITS.put(key, time);
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
        long last = LAST_COOP_REWARD.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        if (time - last < 20) return;
        MaidWeaponData data = MaidInfusion.data(weapon);
        ResonanceSystem.recover(data, MaidWeaponConfig.RESONANCE_COOP_REWARD.get());
        MaidWeaponItem.setMaidData(weapon, data);
        LAST_COOP_REWARD.put(player.getUUID(), time);
    }

    private static void spend(ItemStack weapon, int amount) {
        MaidWeaponData data = MaidInfusion.data(weapon);
        data.reduceResonance(amount);
        MaidWeaponItem.setMaidData(weapon, data);
    }

    private static void markCombat(Player player, long time) {
        LAST_COMBAT.put(player.getUUID(), time);
    }

    private static boolean isActiveContract(Player player, ItemStack weapon) {
        return !weapon.isEmpty() && MaidInfusion.isInfused(weapon)
                && MaidWeaponItem.isOwner(weapon, player)
                && !MaidWeaponItem.isContractSuperseded(weapon);
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

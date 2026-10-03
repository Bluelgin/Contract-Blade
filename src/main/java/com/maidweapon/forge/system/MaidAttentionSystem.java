package com.maidweapon.forge.system;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidInfusion;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gives stored contract maids a restrained way to ask for attention.
 *
 * <p>Time advances only while the real contract item is carried by its owner.
 * The first stage is a one-shot text/chime reminder. TLM voice is played only
 * after the maid has actually manifested and exists on the client.</p>
 */
@Mod.EventBusSubscriber
public final class MaidAttentionSystem {
    private static final String STORED_TICKS = "MaidAttentionStoredTicks";
    private static final String WANTS_ATTENTION = "MaidWantsAttention";
    private static final String ATTENTION_NOTIFIED = "MaidAttentionNotified";
    private static final int COUNTER_INTERVAL = 200;
    private static final int SAFE_AFTER_COMBAT_TICKS = 100;
    private static final double DANGER_RADIUS = 12.0;

    private record PendingVoice(UUID maidId, long playAt) {
    }

    private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();
    private static final Map<UUID, PendingVoice> PENDING_VOICES = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        Player player = event.player;
        if (player.tickCount % 20 == 0) playPendingVoice(player);
        if (player.tickCount % COUNTER_INTERVAL != 0
                || !MaidWeaponConfig.ENABLE_ATTENTION_REMINDERS.get()) return;

        boolean anotherContractAlreadySpoke = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack weapon = player.getInventory().getItem(slot);
            if (!isStoredOwnedContract(player, weapon)) continue;

            var tag = weapon.getOrCreateTag();
            long storedTicks = tag.getLong(STORED_TICKS) + COUNTER_INTERVAL;
            tag.putLong(STORED_TICKS, storedTicks);
            if (storedTicks >= MaidWeaponConfig.ATTENTION_DELAY_TICKS.get()) {
                tag.putBoolean(WANTS_ATTENTION, true);
            }
            if (tag.getBoolean(ATTENTION_NOTIFIED)) {
                anotherContractAlreadySpoke = true;
            }
        }

        if (anotherContractAlreadySpoke || !isSafeToNotify(player)) return;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack weapon = player.getInventory().getItem(slot);
            if (!isStoredOwnedContract(player, weapon)) continue;
            var tag = weapon.getOrCreateTag();
            if (!tag.getBoolean(WANTS_ATTENTION)
                    || tag.getBoolean(ATTENTION_NOTIFIED)) continue;

            MaidWeaponData data = MaidInfusion.data(weapon);
            int variant = player.getRandom().nextInt(3);
            player.displayClientMessage(Component.translatable(
                    "maid_weapon.message.attention." + variant, data.getMaidName()), true);
            if (MaidWeaponConfig.ATTENTION_CHIME.get()) {
                player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.28f, 1.55f);
            }
            tag.putBoolean(ATTENTION_NOTIFIED, true);
            break;
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof Player player) markCombat(player);
        if (event.getSource().getEntity() instanceof Player player) markCombat(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        LAST_COMBAT.remove(playerId);
        PENDING_VOICES.remove(playerId);
    }

    /**
     * Called only after a stored maid has successfully become a real entity.
     * Merely selecting or scrolling over a weapon never resets attention time.
     */
    public static void onManifested(Player owner, ItemStack weapon, Entity maid) {
        if (weapon.isEmpty() || maid == null) return;
        var tag = weapon.getOrCreateTag();
        boolean wantedAttention = tag.getBoolean(WANTS_ATTENTION);

        tag.putLong(STORED_TICKS, 0L);
        tag.remove(WANTS_ATTENTION);
        tag.remove(ATTENTION_NOTIFIED);

        // A rescue manifestation still resets stored time, but combat is not
        // the place for an attention response or an idle voice.
        PENDING_VOICES.remove(owner.getUUID());
        if (!wantedAttention || !MaidWeaponConfig.ENABLE_ATTENTION_REMINDERS.get()
                || com.maidweapon.forge.system.deployment.ContractCompanionState.mode(maid)
                    == com.maidweapon.forge.system.deployment.ContractCompanionState.Mode.GUARD
                || !isSafeToNotify(owner)) return;

        MaidWeaponData data = MaidInfusion.data(weapon);
        int variant = owner.getRandom().nextInt(3);
        owner.displayClientMessage(Component.translatable(
                "maid_weapon.message.attention_response." + variant, data.getMaidName()), true);

        if (MaidWeaponConfig.ATTENTION_VOICE_ON_MANIFEST.get()) {
            // Wait for the maid's spawn packet to reach the client. TLM's custom
            // voice packet resolves the sound pack through the live entity ID.
            PENDING_VOICES.put(owner.getUUID(),
                    new PendingVoice(maid.getUUID(), owner.level().getGameTime() + 10));
        }
    }

    private static void playPendingVoice(Player owner) {
        PendingVoice pending = PENDING_VOICES.get(owner.getUUID());
        if (pending == null || owner.level().getGameTime() < pending.playAt()) return;
        PENDING_VOICES.remove(owner.getUUID());
        if (!MaidWeaponConfig.ATTENTION_VOICE_ON_MANIFEST.get()
                || !isSafeToNotify(owner)) return;

        Entity maid = InfusedMaidDeploymentSystem.findManifestedMaid(
                owner, pending.maidId().toString());
        if (maid != null && maid.level() == owner.level()) {
            TouhouLittleMaidHelper.playMaidIdleVoice(maid);
        }
    }

    private static boolean isStoredOwnedContract(Player player, ItemStack weapon) {
        return MaidInfusion.containsMaid(weapon)
                && ContractCarrierData.isOwner(weapon, player)
                && !ContractCarrierData.isContractSuperseded(weapon);
    }

    private static void markCombat(Player player) {
        if (!player.level().isClientSide) {
            LAST_COMBAT.put(player.getUUID(), player.level().getGameTime());
        }
    }

    private static boolean isSafeToNotify(Player player) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping()
                || player.isOnFire()
                || player.getHealth() <= player.getMaxHealth() * 0.35f) return false;
        if (player.getActiveEffects().stream()
                .anyMatch(effect -> effect.getEffect().getCategory()
                        == MobEffectCategory.HARMFUL)) return false;

        long lastCombat = LAST_COMBAT.getOrDefault(
                player.getUUID(), Long.MIN_VALUE / 2);
        if (player.level().getGameTime() - lastCombat < SAFE_AFTER_COMBAT_TICKS) {
            return false;
        }

        return player.level().getEntitiesOfClass(
                Mob.class, player.getBoundingBox().inflate(DANGER_RADIUS),
                mob -> mob.isAlive() && (mob instanceof Enemy
                        || mob.getTarget() == player))
                .isEmpty();
    }

    private MaidAttentionSystem() {
    }
}

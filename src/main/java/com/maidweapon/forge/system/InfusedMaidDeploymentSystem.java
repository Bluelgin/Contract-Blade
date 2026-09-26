package com.maidweapon.forge.system;

import com.mojang.logging.LogUtils;
import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.forge.compat.TouhouLittleMaidCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.api.EmbeddedSpiritApi;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.deployment.ContractDeploymentEffects;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import com.maidweapon.forge.system.deployment.ContractRecoveryService;
import com.maidweapon.forge.system.deployment.ContractTransferSafetyService;
import com.maidweapon.forge.system.deployment.ContractMaidRuntimeService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;

/** Automatically deploys maids from generic infused weapons while they are held. */
@Mod.EventBusSubscriber
public final class InfusedMaidDeploymentSystem {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double MAX_DEPLOYMENT_DISTANCE_SQR = 64.0 * 64.0;
    private record ActiveDeployment(String maidId, String bindingId) {}
    private record DesiredDeployment(String maidId, String bindingId) {}
    private static final class PendingDeployment {
        private final DesiredDeployment desired;
        private int ticksRemaining;

        private PendingDeployment(DesiredDeployment desired, int ticksRemaining) {
            this.desired = desired;
            this.ticksRemaining = ticksRemaining;
        }
    }
    private static final class PendingRecall {
        private final ActiveDeployment active;
        private final DesiredDeployment next;
        private int ticksRemaining;

        private PendingRecall(ActiveDeployment active, DesiredDeployment next, int ticksRemaining) {
            this.active = active;
            this.next = next;
            this.ticksRemaining = ticksRemaining;
        }
    }
    private static final Map<UUID, ActiveDeployment> ACTIVE_WEAPONS = new HashMap<>();
    /** Last known carrier snapshot; used only if a third-party item deletes itself. */
    private static final Map<UUID, ItemStack> ACTIVE_CARRIERS = new HashMap<>();
    private static final Map<UUID, PendingDeployment> PENDING_DEPLOYMENTS = new HashMap<>();
    private static final Map<UUID, PendingRecall> PENDING_RECALLS = new HashMap<>();
    private static final Map<String, Long> DEPLOY_COOLDOWNS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !TouhouLittleMaidCompat.isTouhouLittleMaidLoaded()) return;

        Player player = event.player;
        UUID playerId = player.getUUID();
        boolean maintenanceTick = player.tickCount % 5 == 0;
        if (maintenanceTick) {
            ContractMaidRuntimeService.purgeLeakedProjections(player);
            ContractTransferSafetyService.rescueSelfStoredContract(player);
        }
        if (ContractRecoveryService.hasRecovery(player)) {
            if (maintenanceTick) processRecovery(player);
            return;
        }

        ItemStack held = player.getMainHandItem();
        boolean ownedGenericContract = MaidInfusion.isInfused(held)
                && !MaidInfusion.isContractBlade(held)
                && MaidWeaponItem.isOwner(held, player);
        if (ownedGenericContract) {
            MaidWeaponItem.ensureBindingId(held);
        }
        String heldMaid = ownedGenericContract
                ? MaidWeaponItem.getBoundMaidUUID(held) : null;
        String desiredBinding = isAutoWeapon(held, player) ? MaidWeaponItem.getBindingId(held) : null;
        String desiredMaid = desiredBinding == null ? null : heldMaid;
        DesiredDeployment desired = desiredBinding == null || desiredMaid == null
                ? null : new DesiredDeployment(desiredMaid, desiredBinding);
        ActiveDeployment active = ACTIVE_WEAPONS.get(playerId);

        if (active != null && ownedGenericContract
                && (MaidInfusion.data(held).getResonance() <= 0
                || EmbeddedSpiritApi.isDormant(held))) {
            forceRecall(player, active.maidId(), 300);
            return;
        }

        // Inventory, maid backpack and Curios page changes temporarily move the selected
        // stack through menu slots. Freeze transitions until the menu is closed.
        if (player.containerMenu != player.inventoryMenu) {
            if (active != null && maintenanceTick) maintainActiveDeployment(player, active);
            return;
        }

        if (active != null) {
            if (maintenanceTick && !ensureActiveDeploymentSafe(player, active)) return;

            if (desired != null && active.bindingId().equals(desired.bindingId())) {
                PENDING_RECALLS.remove(playerId);
                PENDING_DEPLOYMENTS.remove(playerId);
                if (maintenanceTick) maintainDesiredDeployment(player, held, desired);
                return;
            }

            // A quick move of the same concrete contract between slots keeps its binding
            // and reaches the branch above. Different binding IDs represent different
            // contract containers and must use a safe serialized handover.
            PendingRecall pending = PENDING_RECALLS.get(playerId);
            int delay = desired == null
                    ? MaidWeaponConfig.MANIFEST_RECALL_DELAY.get()
                    : MaidWeaponConfig.MANIFEST_HANDOVER_DELAY.get();
            if (pending == null || !pending.active.equals(active)
                    || !sameDesired(pending.next, desired)) {
                pending = new PendingRecall(active, desired, delay);
                PENDING_RECALLS.put(playerId, pending);
                PENDING_DEPLOYMENTS.remove(playerId);
                ContractDeploymentEffects.recallBuildup(findManifestedMaid(player, active.maidId()));
            }

            if (pending.ticksRemaining > 0) pending.ticksRemaining--;
            if (pending.ticksRemaining > 0) {
                if (pending.ticksRemaining % 5 == 0) ContractDeploymentEffects.recallBuildup(findManifestedMaid(player, active.maidId()));
                return;
            }

            PENDING_RECALLS.remove(playerId);
            Entity oldMaid = findManifestedMaid(player, active.maidId());
            if (oldMaid != null) ContractDeploymentEffects.recallFinish(oldMaid);
            if (!recall(player, active.maidId())) {
                ItemStack activeWeapon = findBoundWeapon(player, active.maidId());
                startRecovery(player, active, activeWeapon);
                return;
            }
            ACTIVE_WEAPONS.remove(playerId);
            ACTIVE_CARRIERS.remove(playerId);

            // The handover delay covers both halves of the transition. Re-check the
            // current hand before manifesting so rapid hotbar scrolling cannot summon
            // an outdated target.
            DesiredDeployment current = desiredDeployment(player);
            if (desired != null && sameDesired(desired, current)) {
                manifestDesired(player, player.getMainHandItem(), desired);
            }
            return;
        }

        PENDING_RECALLS.remove(playerId);
        if (desired == null) {
            PENDING_DEPLOYMENTS.remove(playerId);
            return;
        }

        PendingDeployment pending = PENDING_DEPLOYMENTS.get(playerId);
        if (pending == null || !sameDesired(pending.desired, desired)) {
            pending = new PendingDeployment(desired,
                    MaidWeaponConfig.MANIFEST_DEPLOY_DELAY.get());
            PENDING_DEPLOYMENTS.put(playerId, pending);
            ContractDeploymentEffects.deployBuildup(player);
        }
        if (pending.ticksRemaining > 0) pending.ticksRemaining--;
        if (pending.ticksRemaining > 0) {
            if (pending.ticksRemaining % 4 == 0) ContractDeploymentEffects.deployBuildup(player);
            return;
        }

        PENDING_DEPLOYMENTS.remove(playerId);
        manifestDesired(player, held, desired);
    }

    @SubscribeEvent
    public static void onContractCarrierDestroyed(PlayerDestroyItemEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        ActiveDeployment active = ACTIVE_WEAPONS.get(player.getUUID());
        ItemStack destroyed = event.getOriginal();
        if (active == null || destroyed.isEmpty()
                || !active.maidId().equals(MaidWeaponItem.getBoundMaidUUID(destroyed))
                || !active.bindingId().equals(MaidWeaponItem.getBindingId(destroyed))) return;

        ACTIVE_CARRIERS.put(player.getUUID(), destroyed.copy());
        emergencyFilmRecovery(player, active, destroyed);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ActiveDeployment active = ACTIVE_WEAPONS.get(event.getEntity().getUUID());
        cancelRecovery(event.getEntity());
        clearTransitions(event.getEntity());
        ACTIVE_WEAPONS.remove(event.getEntity().getUUID());
        ACTIVE_CARRIERS.remove(event.getEntity().getUUID());
        if (active != null && !event.getEntity().level().isClientSide) {
            recall(event.getEntity(), active.maidId());
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        ActiveDeployment active = ACTIVE_WEAPONS.get(player.getUUID());
        cancelRecovery(player);
        clearTransitions(player);
        ACTIVE_WEAPONS.remove(player.getUUID());
        ACTIVE_CARRIERS.remove(player.getUUID());
        if (active != null) recall(player, active.maidId());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        ActiveDeployment active = ACTIVE_WEAPONS.get(player.getUUID());
        cancelRecovery(player);
        clearTransitions(player);
        ACTIVE_WEAPONS.remove(player.getUUID());
        ACTIVE_CARRIERS.remove(player.getUUID());
        if (active != null) recall(player, active.maidId());
    }

    @SubscribeEvent
    public static void onContractToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide) return;
        ItemStack weapon = event.getEntity().getItem();
        if (!MaidInfusion.isInfused(weapon) || !MaidWeaponItem.isOwner(weapon, player)) return;

        ActiveDeployment active = ACTIVE_WEAPONS.get(player.getUUID());
        String maidId = MaidWeaponItem.getBoundMaidUUID(weapon);
        String bindingId = MaidWeaponItem.getBindingId(weapon);
        if (active == null || maidId == null || bindingId == null
                || !active.maidId().equals(maidId)
                || !active.bindingId().equals(bindingId)) return;

        clearTransitions(player);
        Entity maid = findManifestedMaid(player, maidId);
        if (maid != null) ContractDeploymentEffects.recallFinish(maid);
        if (recallIntoStack(player, maidId, weapon)) {
            ACTIVE_WEAPONS.remove(player.getUUID());
            ACTIVE_CARRIERS.remove(player.getUUID());
        } else {
            // Forge returns a cancelled toss to the player inventory. Keeping the
            // contract is safer than allowing its active entity anchor to disappear.
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable(
                    "maid_weapon.message.deployed_contract_transfer_blocked"), true);
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        // A manifested maid must never serialize a contract weapon that is still
        // inside her own inventory. Move it back to the player's selected slot
        // before any recall can discard the inventory-owning entity.
        ContractTransferSafetyService.rescueSelfStoredContract(player);
        // A contract blade can also be placed into a container while its maid is manually
        // manifested. Recall into the stack while the menu slots are still addressable.
        for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots) {
            // Opening another screen closes the previous menu first. Player inventory
            // slots (including the held contract weapon) are not an external transfer
            // and must never trigger recall, otherwise the maid immediately respawns
            // at the player's feet and TLM's maid screen is lost.
            if (slot.container == player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            // TLM/Curios can close one maid page before opening another. The manifested
            // maid carries a tagged, non-droppable visual copy of the contract weapon;
            // it must never be treated as a real external transfer during that page switch.
            if (ContractTransferSafetyService.isProjectionPhantom(stack)) continue;
            if (!hasDeployedMaid(player, stack)) continue;
            String maidId = MaidWeaponItem.getBoundMaidUUID(stack);
            if (maidId != null) forceRecall(player, maidId, 0);
        }
        // Merely closing a maid/Curios/inventory page is not a reason for an abrupt
        // recall. The normal tick state machine resumes and applies the configured delay.
    }

    /**
     * Returns a contract weapon accidentally placed in its own manifested maid's menu.
     *
     * <p>This is deliberately checked both while the menu is open and from the close
     * event. Shift-click can move the weapon and close the screen inside the same tick.
     * Removing the stack before maid serialization makes the operation atomic: either
     * the weapon is back with the player, or any remainder is dropped beside the owner.</p>
     */
    private static boolean deliverEmergencyFilm(Player player, ItemStack film) {
        if (film.isEmpty()) return false;
        ItemStack remainder = film.copy();
        player.getInventory().add(remainder);
        if (remainder.isEmpty()) return true;
        return player.drop(remainder, false) != null;
    }

    private static DesiredDeployment desiredDeployment(Player player) {
        ItemStack held = player.getMainHandItem();
        if (!isAutoWeapon(held, player)) return null;
        String maidId = MaidWeaponItem.getBoundMaidUUID(held);
        if (maidId == null || maidId.isEmpty()) return null;
        return new DesiredDeployment(maidId, MaidWeaponItem.ensureBindingId(held));
    }

    private static boolean sameDesired(DesiredDeployment left, DesiredDeployment right) {
        return left == right || left != null && right != null
                && Objects.equals(left.maidId(), right.maidId())
                && Objects.equals(left.bindingId(), right.bindingId());
    }

    /**
     * Performs safety recovery independently of the cosmetic recall delay.
     * Cross-dimension and excessive-distance cases must never wait for hotbar state.
     */
    private static boolean ensureActiveDeploymentSafe(Player player, ActiveDeployment active) {
        Entity maid = findManifestedMaid(player, active.maidId());
        ItemStack weapon = findBoundWeapon(player, active.maidId());
        if (maid == null) {
            if (!weapon.isEmpty() && MaidInfusion.containsMaid(weapon)) {
                ACTIVE_WEAPONS.remove(player.getUUID());
                ACTIVE_CARRIERS.remove(player.getUUID());
                clearTransitions(player);
                return false;
            }
            if (!weapon.isEmpty() && !ContractRecoveryService.failed(weapon)) {
                startRecovery(player, active, weapon);
            }
            return false;
        }
        if (weapon.isEmpty()) {
            ItemStack snapshot = ACTIVE_CARRIERS.getOrDefault(
                    player.getUUID(), ItemStack.EMPTY);
            emergencyFilmRecovery(player, active, snapshot);
            return false;
        }
        if (maid.level() == player.level()
                && maid.distanceToSqr(player) <= MAX_DEPLOYMENT_DISTANCE_SQR) return true;

        if (!weapon.isEmpty()) rememberDeploymentLocation(weapon, maid);
        clearTransitions(player);
        if (recall(player, active.maidId())) {
            ACTIVE_WEAPONS.remove(player.getUUID());
            ACTIVE_CARRIERS.remove(player.getUUID());
        } else if (!weapon.isEmpty()) {
            startRecovery(player, active, weapon);
        }
        return false;
    }

    private static void maintainActiveDeployment(Player player, ActiveDeployment active) {
        Entity maid = findManifestedMaid(player, active.maidId());
        ItemStack weapon = findBoundWeapon(player, active.maidId());
        if (maid == null || weapon.isEmpty()) return;
        maintainManifestedMaid(player, weapon, maid);
    }

    private static void maintainDesiredDeployment(Player player, ItemStack weapon,
                                                  DesiredDeployment desired) {
        Entity maid = findManifestedMaid(player, desired.maidId());
        if (maid == null) return;
        String entityBinding = maid.getPersistentData().getString(
                TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID);
        if (!desired.bindingId().equals(entityBinding)) return;
        ACTIVE_WEAPONS.put(player.getUUID(),
                new ActiveDeployment(desired.maidId(), desired.bindingId()));
        maintainManifestedMaid(player, weapon, maid);
    }

    private static void maintainManifestedMaid(Player player, ItemStack weapon, Entity maid) {
        ACTIVE_CARRIERS.put(player.getUUID(), weapon.copy());
        if (player.tickCount % 20 == 0 || !ContractRecoveryService.hasLocation(weapon)) {
            rememberDeploymentLocation(weapon, maid);
        }
        ContractRecoveryService.clearFailure(weapon);
        ContractMaidRuntimeService.maintain(player, weapon, maid);
    }

    /**
     * Converts a carrier-less live contract into a full TLM resurrection film.
     * Delivery is committed before the live maid is removed, so every failure is retryable.
     */
    private static boolean emergencyFilmRecovery(Player player, ActiveDeployment active,
                                                 ItemStack carrierSnapshot) {
        Entity maid = findManifestedMaid(player, active.maidId());
        if (maid == null || carrierSnapshot.isEmpty()) return false;
        ItemStack film = TouhouLittleMaidHelper.createEmergencyResurrectionFilm(
                player, carrierSnapshot, maid);
        if (film.isEmpty() || !deliverEmergencyFilm(player, film)) {
            player.displayClientMessage(Component.translatable(
                    "maid_weapon.message.emergency_film_failed"), false);
            return false;
        }

        maid.discard();
        MaidCareTaskSystem.clearOriginalTask(carrierSnapshot);
        clearTransitions(player);
        cancelRecovery(player);
        ACTIVE_WEAPONS.remove(player.getUUID());
        ACTIVE_CARRIERS.remove(player.getUUID());
        player.displayClientMessage(Component.translatable(
                "maid_weapon.message.carrier_destroyed_film_created"), false);
        LOGGER.warn("[MaidWeapon] Contract carrier {} was destroyed; maid {} was preserved in a TLM film",
                carrierSnapshot.getItem(), active.maidId());
        return true;
    }

    private static boolean manifestDesired(Player player, ItemStack weapon,
                                           DesiredDeployment desired) {
        DesiredDeployment current = desiredDeployment(player);
        if (!sameDesired(desired, current)) return false;

        Entity maid = findManifestedMaid(player, desired.maidId());
        if (maid != null) {
            String entityBinding = maid.getPersistentData().getString(
                    TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID);
            if (!desired.bindingId().equals(entityBinding)) return false;
            ACTIVE_WEAPONS.put(player.getUUID(),
                    new ActiveDeployment(desired.maidId(), desired.bindingId()));
            maintainManifestedMaid(player, weapon, maid);
            return true;
        }
        if (!MaidInfusion.containsMaid(weapon)) {
            if (!ContractRecoveryService.failed(weapon)) {
                startRecovery(player,
                        new ActiveDeployment(desired.maidId(), desired.bindingId()), weapon);
            }
            return false;
        }

        if (!TouhouLittleMaidHelper.convertWeaponToMaid(player, weapon, false)) return false;
        maid = findManifestedMaid(player, desired.maidId());
        if (maid == null) return false;

        // Lifecycle restore owns entity initialization; runtime service owns
        // temporary care/combat task selection and optional-mod projections.
        ContractMaidRuntimeService.selectCombatTask(player, weapon, maid);
        rememberDeploymentLocation(weapon, maid);
        ContractRecoveryService.clearFailure(weapon);
        ACTIVE_WEAPONS.put(player.getUUID(),
                new ActiveDeployment(desired.maidId(), desired.bindingId()));
        ContractDeploymentEffects.deployFinish(maid);
        return true;
    }
    private static void clearTransitions(Player player) {
        PENDING_DEPLOYMENTS.remove(player.getUUID());
        PENDING_RECALLS.remove(player.getUUID());
    }

    private static boolean isAutoWeapon(ItemStack stack, Player player) {
        return MaidInfusion.isInfused(stack) && !MaidInfusion.isContractBlade(stack)
                && MaidWeaponItem.isOwner(stack, player)
                && !MaidWeaponItem.isContractSuperseded(stack)
                && !EmbeddedSpiritApi.isDormant(stack)
                && MaidInfusion.data(stack).getResonance() > 0
                && !isCoolingDown(player, MaidWeaponItem.getBoundMaidUUID(stack));
    }
    public static boolean forceRecall(Player player, String maidId, int cooldownTicks) {
        clearTransitions(player);
        Entity deployed = findManifestedMaid(player, maidId);
        float recoveryHealth = deployed instanceof LivingEntity living
                ? Math.max(1.0f, living.getMaxHealth() * 0.25f) : 1.0f;
        boolean recalled = recall(player, maidId);
        ACTIVE_WEAPONS.remove(player.getUUID());
        ACTIVE_CARRIERS.remove(player.getUUID());
        if (recalled && cooldownTicks > 0) {
            ItemStack weapon = findBoundWeapon(player, maidId);
            TouhouLittleMaidHelper.setStoredMaidHealth(player, weapon, recoveryHealth);
            DEPLOY_COOLDOWNS.put(cooldownKey(player, maidId),
                    player.level().getGameTime() + cooldownTicks);
        }
        return recalled;
    }

    private static boolean recall(Player player, String maidId) {
        // Final invariant: never serialize and discard a maid while her own
        // contract stack is still held by the currently open maid container.
        ContractTransferSafetyService.rescueSelfStoredContract(player);
        ItemStack weapon = findBoundWeapon(player, maidId);
        return recallIntoStack(player, maidId, weapon);
    }

    private static boolean recallIntoStack(Player player, String maidId, ItemStack weapon) {
        Entity maid = findManifestedMaid(player, maidId);
        if (weapon.isEmpty() || maid == null) return false;

        ContractMaidRuntimeService.cleanupBeforeRecall(player, weapon, maid);
        if (TouhouLittleMaidHelper.convertMaidToWeapon(player, maid, weapon, false)) {
            MaidCareTaskSystem.clearOriginalTask(weapon);
            clearDeploymentLocation(weapon);
            return true;
        }
        return false;
    }

    public static ItemStack findBoundWeapon(Player player, String maidId) {
        return ContractWeaponLocator.findBoundWeapon(player, maidId);
    }

    public static Entity findManifestedMaid(Player player, String maidId) {
        return ContractWeaponLocator.findManifestedMaid(player, maidId);
    }

    /** True only while this exact contract's maid is currently present in a loaded level. */
    public static boolean hasDeployedMaid(Player player, ItemStack weapon) {
        return ContractWeaponLocator.hasDeployedMaid(player, weapon);
    }

    private static void rememberDeploymentLocation(ItemStack weapon, Entity maid) {
        ContractRecoveryService.rememberLocation(weapon, maid);
    }

    private static void clearDeploymentLocation(ItemStack weapon) {
        ContractRecoveryService.clearLocation(weapon);
    }

    private static boolean startRecovery(Player player, ActiveDeployment deployment,
                                         ItemStack weapon) {
        ContractRecoveryService.StartResult result = ContractRecoveryService.start(
                player, deployment.maidId(), deployment.bindingId(), weapon);
        if (result == ContractRecoveryService.StartResult.STARTED) {
            clearTransitions(player);
            return true;
        }
        if (result == ContractRecoveryService.StartResult.TERMINAL_FAILURE) {
            ACTIVE_WEAPONS.remove(player.getUUID());
            ACTIVE_CARRIERS.remove(player.getUUID());
            clearTransitions(player);
            return true;
        }
        return false;
    }

    /** Returns true while recovery owns this player's deployment processing for this tick. */
    /** Returns true while recovery owns this player's deployment processing for this tick. */
    private static boolean processRecovery(Player player) {
        ContractRecoveryService.TickResult result = ContractRecoveryService.tick(player);
        if (result == ContractRecoveryService.TickResult.IDLE) return false;
        if (result == ContractRecoveryService.TickResult.MAID_AVAILABLE) {
            String maidId = ContractRecoveryService.currentMaidId(player);
            if (maidId != null && recall(player, maidId)) {
                ContractRecoveryService.finish(player);
                ACTIVE_WEAPONS.remove(player.getUUID());
                ACTIVE_CARRIERS.remove(player.getUUID());
                clearTransitions(player);
            }
            return true;
        }
        if (result == ContractRecoveryService.TickResult.TIMED_OUT) {
            ContractRecoveryService.finish(player);
            ACTIVE_WEAPONS.remove(player.getUUID());
            ACTIVE_CARRIERS.remove(player.getUUID());
            clearTransitions(player);
        }
        return true;
    }

    private static void cancelRecovery(Player player) {
        ContractRecoveryService.cancel(player);
    }

    private static boolean isCoolingDown(Player player, String maidId) {
        if (maidId == null) return false;
        String key = cooldownKey(player, maidId);
        long until = DEPLOY_COOLDOWNS.getOrDefault(key, 0L);
        if (until <= player.level().getGameTime()) {
            DEPLOY_COOLDOWNS.remove(key);
            return false;
        }
        return true;
    }

    private static String cooldownKey(Player player, String maidId) {
        return player.getUUID() + ":" + maidId;
    }

    private InfusedMaidDeploymentSystem() {}
}

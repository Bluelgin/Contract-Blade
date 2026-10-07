package com.maidweapon.forge.system;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.compat.TouhouLittleMaidCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.api.EmbeddedSpiritApi;
import com.maidweapon.forge.system.deployment.ContractDeploymentEffects;
import com.maidweapon.forge.system.deployment.ContractCarrierLossService;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import com.maidweapon.forge.system.deployment.ContractRecoveryService;
import com.maidweapon.forge.system.deployment.ContractTransferSafetyService;
import com.maidweapon.forge.system.deployment.ContractMaidRuntimeService;
import com.maidweapon.forge.system.deployment.ContractCompanionService;
import com.maidweapon.forge.system.deployment.ContractCompanionState;
import com.maidweapon.forge.system.deployment.ContractActiveDeployments;
import com.maidweapon.forge.system.deployment.ContractActiveDeployments.Deployment;
import com.maidweapon.forge.compat.tlm.TlmResidenceAdapter;
import com.maidweapon.forge.system.interior.ContractInteriorService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Lifecycle orchestration and safety; companion policy owns summon/recall intent. */
@Mod.EventBusSubscriber
public final class InfusedMaidDeploymentSystem {
    private record DesiredDeployment(String maidId, String bindingId) {}
    private static final Map<String, Long> DEPLOY_COOLDOWNS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !TouhouLittleMaidCompat.isTouhouLittleMaidLoaded()) return;
        Player player = event.player;
        if (player.level().dimension().equals(ContractInteriorService.INTERIOR_LEVEL)) {
            clearTransitions(player);
            ContractActiveDeployments.clear(player.getUUID());
            return;
        }
        if (player.tickCount % 5 == 0) {
            ContractMaidRuntimeService.purgeLeakedProjections(player);
            ContractTransferSafetyService.rescueSelfStoredContract(player);
            if (ContractCarrierLossService.hasPending(player)) {
                var carriers = ContractWeaponLocator.indexByBinding(player);
                ContractCarrierLossService.process(player, carriers, InfusedMaidDeploymentSystem::clearCarrierLossRuntime);
            }
        }
        if (ContractRecoveryService.hasRecovery(player)) {
            if (player.tickCount % 5 == 0) processRecovery(player);
        }
        ContractCompanionService.tick(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ContractCompanionService.disconnect(event.getEntity());
        if (!event.getEntity().level().isClientSide) recallFollowers(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        recallFollowers(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        recallFollowers(player);
    }

    private static void recallFollowers(Player player) {
        cancelRecovery(player);
        clearTransitions(player);
        // Rebuild from carried references too: login/load or a native home toggle
        // may precede the first maintenance tick. Never instantiate absent entities.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack carrier = player.getInventory().getItem(slot);
            if (!MaidInfusion.isInfused(carrier) || MaidInfusion.containsMaid(carrier)
                    || !ContractCarrierData.isOwner(carrier, player)
                    || ContractCarrierData.isContractSuperseded(carrier)
                    || ContractTransferSafetyService.isProjectionPhantom(carrier)
                    || ContractCompanionService.isResident(player, carrier)) continue;
            String maid = ContractCarrierData.getBoundMaidUUID(carrier);
            if (maid != null) ContractActiveDeployments.remember(player.getUUID(), maid, ContractCarrierData.ensureBindingId(carrier));
        }
        for (var active : ContractActiveDeployments.snapshot(player.getUUID())) {
            ItemStack weapon = findBoundWeapon(player, active.maidId());
            if (!ContractCompanionService.isResident(player, weapon)) recall(player, active.maidId());
        }
        ContractActiveDeployments.clear(player.getUUID());
    }

    @SubscribeEvent
    public static void onContractToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide) return;
        ItemStack weapon = event.getEntity().getItem();
        if (!MaidInfusion.isInfused(weapon) || !ContractCarrierData.isOwner(weapon, player)) return;
        if (ContractCompanionService.isResident(player, weapon)) return;

        String maidId = ContractCarrierData.getBoundMaidUUID(weapon);
        String bindingId = ContractCarrierData.getBindingId(weapon);
        Deployment active = ContractActiveDeployments.get(player.getUUID(), bindingId);
        if (active == null && maidId != null && bindingId != null && hasDeployedMaid(player, weapon)
                && !ContractCompanionService.isResident(player, weapon)) active = new Deployment(maidId, bindingId);
        if (active == null || maidId == null || bindingId == null
                || !active.maidId().equals(maidId)
                || !active.bindingId().equals(bindingId)) return;

        clearTransitions(player);
        Entity maid = findManifestedMaid(player, maidId);
        if (maid != null) ContractDeploymentEffects.recallFinish(maid);
        if (recallIntoStack(player, maidId, weapon)) {
            ContractActiveDeployments.forgetMaid(player.getUUID(), maidId);
        } else {
            // ItemTossEvent removes the stack before firing. Cancellation prevents
            // the entity from spawning, so the protected contract must be restored
            // explicitly or it would disappear from the system.
            ItemStack protectedStack = event.getEntity().getItem().copy();
            event.setCanceled(true);
            player.getInventory().placeItemBackInInventory(protectedStack);
            player.displayClientMessage(Component.translatable(
                    "maid_weapon.message.deployed_contract_transfer_blocked"), true);
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        // Contract Interior owns its own real-maid lifecycle. Keep the generic
        // container-transfer recall path out of that dimension; only rescue stacks.
        if (player.level().dimension().equals(ContractInteriorService.INTERIOR_LEVEL)) {
            ContractTransferSafetyService.rescueSelfStoredContract(player);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                ContractInteriorService.rescueActiveContractFromContainer(serverPlayer, true);
            }
            return;
        }
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
            if (ContractCompanionService.isResident(player, stack)) continue;
            String maidId = ContractCarrierData.getBoundMaidUUID(stack);
            if (maidId != null) forceRecall(player, maidId, 0);
        }
        // Merely closing a maid/Curios/inventory page is not a reason for an abrupt
        // recall. The normal tick state machine resumes and applies the configured delay.
    }

    private static DesiredDeployment desiredDeployment(Player player) {
        ItemStack held = player.getMainHandItem();
        if (!isEligibleWeapon(held, player)) return null;
        String maidId = ContractCarrierData.getBoundMaidUUID(held);
        if (maidId == null || maidId.isEmpty()) return null;
        return new DesiredDeployment(maidId, ContractCarrierData.ensureBindingId(held));
    }

    private static boolean sameDesired(DesiredDeployment left, DesiredDeployment right) {
        return left == right || left != null && right != null
                && Objects.equals(left.maidId(), right.maidId())
                && Objects.equals(left.bindingId(), right.bindingId());
    }

    private static void maintainManifestedMaid(Player player, ItemStack weapon, Entity maid) {
        if (player.tickCount % 20 == 0 || !ContractRecoveryService.hasLocation(weapon)) {
            rememberDeploymentLocation(weapon, maid);
        }
        ContractRecoveryService.clearFailure(weapon);
        ContractMaidRuntimeService.maintain(player, weapon, maid);
    }

    private static void clearCarrierLossRuntime(Player player, ContractCarrierLossService.Candidate candidate) {
        Deployment active = ContractActiveDeployments.get(player.getUUID(), candidate.bindingId());
        if (active != null && active.bindingId().equals(candidate.bindingId())) {
            clearTransitions(player);
            ContractActiveDeployments.forgetMaid(player.getUUID(), candidate.maidId());
        }
        ContractRecoveryService.cancel(player, candidate.maidId());
    }

    public static boolean manifestRequested(Player player, ItemStack weapon) {
        return manifestRequested(player, weapon,
                com.maidweapon.forge.system.deployment.ContractCompanionState.Mode.MANUAL);
    }

    public static boolean manifestRequested(Player player, ItemStack weapon,
            com.maidweapon.forge.system.deployment.ContractCompanionState.Mode mode) {
        if (weapon != player.getMainHandItem() || !isEligibleWeapon(weapon, player)
                || !MaidInfusion.containsMaid(weapon) || !ContractCompanionService.canFollow(player, weapon)) return false;
        DesiredDeployment desired = desiredDeployment(player);
        return desired != null && manifestDesired(player, weapon, desired, mode);
    }

    public static boolean recallRequested(Player player, ItemStack weapon) {
        if (!MaidInfusion.isInfused(weapon) || !ContractCarrierData.isOwner(weapon, player)) return false;
        String id = ContractCarrierData.getBoundMaidUUID(weapon);
        Entity maid = id == null ? null : findManifestedMaid(player, id);
        if (maid == null || ContractCarrierData.isContractSuperseded(weapon)
                || player.level().dimension().equals(ContractInteriorService.INTERIOR_LEVEL)
                || maid.level() != player.level()
                || maid.distanceToSqr(player) > 32.0 * 32.0
                || !ContractCarrierData.ensureBindingId(weapon).equals(maid.getPersistentData()
                    .getString(TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID))) return false;
        boolean result = recallIntoStack(player, id, weapon);
        if (result) {
            ContractActiveDeployments.forgetMaid(player.getUUID(), id);
            clearTransitions(player);
        }
        return result;
    }

    public static void maintainCompanion(Player player, ItemStack weapon, Entity maid, boolean resident) {
        if (!resident) {
            ContractActiveDeployments.remember(player.getUUID(), maid.getStringUUID(), ContractCarrierData.ensureBindingId(weapon));
        } else {
            ContractActiveDeployments.forgetMaid(player.getUUID(), maid.getStringUUID());
        }
        maintainManifestedMaid(player, weapon, maid);
    }

    private static boolean manifestDesired(Player player, ItemStack weapon,
                                           DesiredDeployment desired,
            com.maidweapon.forge.system.deployment.ContractCompanionState.Mode mode) {
        DesiredDeployment current = desiredDeployment(player);
        if (!sameDesired(desired, current)) return false;

        Entity maid = findManifestedMaid(player, desired.maidId());
        if (maid != null) {
            String entityBinding = maid.getPersistentData().getString(
                    TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID);
            if (!desired.bindingId().equals(entityBinding)) return false;
            ContractActiveDeployments.remember(player.getUUID(), desired.maidId(), desired.bindingId());
            maintainManifestedMaid(player, weapon, maid);
            return true;
        }
        if (!MaidInfusion.containsMaid(weapon)) {
            if (!ContractRecoveryService.failed(weapon)) {
                startRecovery(player,
                        new Deployment(desired.maidId(), desired.bindingId()), weapon);
            }
            return false;
        }

        if (!ContractLifecycleService.manifest(player, weapon, false,
                entity -> {
                    TlmResidenceAdapter.startFollowing(entity);
                    com.maidweapon.forge.system.deployment.ContractCompanionState.prepareEntity(entity, mode);
                })) return false;
        maid = findManifestedMaid(player, desired.maidId());
        if (maid == null) return false;

        // Lifecycle restore owns entity initialization; runtime service owns
        // temporary care/combat task selection and optional-mod projections.
        ContractMaidRuntimeService.selectCombatTask(player, weapon, maid);
        rememberDeploymentLocation(weapon, maid);
        ContractRecoveryService.clearFailure(weapon);
        ContractActiveDeployments.remember(player.getUUID(), desired.maidId(), desired.bindingId());
        ContractDeploymentEffects.deployFinish(maid);
        return true;
    }
    private static void clearTransitions(Player player) {
        ContractCompanionService.cancelRequests(player);
    }

    public static boolean isEligibleWeapon(ItemStack stack, Player player) {
        return MaidInfusion.isInfused(stack)
                && ContractCarrierData.isOwner(stack, player)
                && !ContractCarrierData.isContractSuperseded(stack)
                && !EmbeddedSpiritApi.isDormant(stack)
                && MaidInfusion.data(stack).getResonance() > 0
                && !isCoolingDown(player, ContractCarrierData.getBoundMaidUUID(stack));
    }
    public static boolean forceRecall(Player player, String maidId, int cooldownTicks) {
        ContractTransferSafetyService.rescueSelfStoredContract(player);
        return forceRecall(player, maidId, findBoundWeapon(player, maidId), cooldownTicks);
    }

    /** Emergency callers may have resolved a real carrier in a nearby blade stand. */
    public static boolean forceRecall(Player player, String maidId, ItemStack weapon, int cooldownTicks) {
        if (weapon.isEmpty() || !ContractCarrierData.isOwner(weapon, player)
                || !maidId.equals(ContractCarrierData.getBoundMaidUUID(weapon))) return false;
        clearTransitions(player);
        Entity deployed = findManifestedMaid(player, maidId);
        float recoveryHealth = deployed instanceof LivingEntity living
                ? Math.max(1.0f, living.getMaxHealth() * 0.25f) : 1.0f;
        boolean recalled = recallIntoStack(player, maidId, weapon);
        if (recalled) ContractActiveDeployments.forgetMaid(player.getUUID(), maidId);
        if (recalled && cooldownTicks > 0) {
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
        if (ContractLifecycleService.capture(player, maid, weapon, false)) {
            ContractActiveDeployments.forgetMaid(player.getUUID(), maidId);
            ContractRecoveryService.cancel(player, maidId);
            MaidCareTaskSystem.clearOriginalTask(weapon);
            clearDeploymentLocation(weapon);
            ContractCompanionState.clear(weapon);
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

    private static boolean startRecovery(Player player, Deployment deployment,
                                         ItemStack weapon) {
        ContractRecoveryService.StartResult result = ContractRecoveryService.start(
                player, deployment.maidId(), deployment.bindingId(), weapon);
        if (result == ContractRecoveryService.StartResult.STARTED) {
            clearTransitions(player);
            return true;
        }
        if (result == ContractRecoveryService.StartResult.TERMINAL_FAILURE) {
            ContractActiveDeployments.forgetMaid(player.getUUID(), deployment.maidId());
            clearTransitions(player);
            return true;
        }
        return false;
    }

    /** Returns true while recovery owns this player's deployment processing for this tick. */
    private static boolean processRecovery(Player player) {
        String maidId = ContractRecoveryService.currentMaidId(player);
        if (maidId != null && findManifestedMaid(player, maidId) != null
                && recall(player, maidId)) {
            ContractRecoveryService.cancel(player, maidId);
            ContractActiveDeployments.forgetMaid(player.getUUID(), maidId);
            clearTransitions(player);
            return true;
        }

        ContractRecoveryService.TickResult result = ContractRecoveryService.tick(player);
        if (result == ContractRecoveryService.TickResult.IDLE) return false;
        if (result == ContractRecoveryService.TickResult.TIMED_OUT) {
            ContractRecoveryService.finish(player);
            ContractActiveDeployments.forgetMaid(player.getUUID(), maidId);
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

    public static void clearSession() {
        ContractActiveDeployments.clear();
        ContractCarrierLossService.clearSession();
        DEPLOY_COOLDOWNS.clear();
    }

    private InfusedMaidDeploymentSystem() {}
}

package com.maidweapon.forge.system;

import com.mojang.logging.LogUtils;
import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.compat.TripleMagicCompat;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Method;
import org.slf4j.Logger;

/** Automatically deploys maids from generic infused weapons while they are held. */
@Mod.EventBusSubscriber
public final class InfusedMaidDeploymentSystem {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ORIGINAL_TASK = "MaidInfusionOriginalTask";
    private static final String ATTACK_TASK = "touhou_little_maid:attack";
    private static final String MAGIC_TASK_FAILURE = "MaidInfusionMagicTaskFailure";
    private static final String SLASHBLADE_TASK_FAILURE = "MaidInfusionSlashBladeTaskFailure";
    private static final String DEPLOYMENT_LOCATION = "MaidDeploymentLocation";
    private static final String RECOVERY_FAILED = "MaidDeploymentRecoveryFailed";
    private static final double MAX_DEPLOYMENT_DISTANCE_SQR = 64.0 * 64.0;
    private static final int RECOVERY_NEIGHBOR_DELAY = 20;
    private static final int RECOVERY_TIMEOUT = 40;
    private static final TicketType<UUID> RECOVERY_TICKET = TicketType.create(
            "maid_weapon_recovery", UUID::compareTo, RECOVERY_TIMEOUT + 20);
    private record ActiveDeployment(String maidId, String bindingId) {}
    private record DesiredDeployment(String maidId, String bindingId) {}
    private record DeploymentLocation(ResourceKey<Level> dimension, BlockPos pos) {}
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
    private static final class RecoveryAttempt {
        private final ActiveDeployment deployment;
        private final DeploymentLocation location;
        private final long startedAt;
        private final Set<ChunkPos> tickets = new HashSet<>();
        private boolean neighborsRequested;

        private RecoveryAttempt(ActiveDeployment deployment, DeploymentLocation location,
                                long startedAt) {
            this.deployment = deployment;
            this.location = location;
            this.startedAt = startedAt;
        }
    }
    private static final Map<UUID, ActiveDeployment> ACTIVE_WEAPONS = new HashMap<>();
    private static final Map<UUID, PendingDeployment> PENDING_DEPLOYMENTS = new HashMap<>();
    private static final Map<UUID, PendingRecall> PENDING_RECALLS = new HashMap<>();
    private static final Map<String, Long> DEPLOY_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, RecoveryAttempt> RECOVERIES = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !TouhouLittleMaidCompat.isTouhouLittleMaidLoaded()) return;

        Player player = event.player;
        UUID playerId = player.getUUID();
        boolean maintenanceTick = player.tickCount % 5 == 0;
        if (maintenanceTick) {
            TripleMagicCompat.purgeLeakedCopies(player);
            rescueSelfStoredContract(player);
        }
        if (RECOVERIES.containsKey(playerId)) {
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
                && MaidInfusion.data(held).getResonance() <= 0) {
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
                emitRecallBuildup(player, active);
            }

            if (pending.ticksRemaining > 0) pending.ticksRemaining--;
            if (pending.ticksRemaining > 0) {
                if (pending.ticksRemaining % 5 == 0) emitRecallBuildup(player, active);
                return;
            }

            PENDING_RECALLS.remove(playerId);
            Entity oldMaid = findMaid(player, active.maidId());
            if (oldMaid != null) emitRecallFinish(oldMaid);
            if (!recall(player, active.maidId())) {
                ItemStack activeWeapon = findBoundWeapon(player, active.maidId());
                startRecovery(player, active, activeWeapon);
                return;
            }
            ACTIVE_WEAPONS.remove(playerId);

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
            emitDeployBuildup(player);
        }
        if (pending.ticksRemaining > 0) pending.ticksRemaining--;
        if (pending.ticksRemaining > 0) {
            if (pending.ticksRemaining % 4 == 0) emitDeployBuildup(player);
            return;
        }

        PENDING_DEPLOYMENTS.remove(playerId);
        manifestDesired(player, held, desired);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ActiveDeployment active = ACTIVE_WEAPONS.get(event.getEntity().getUUID());
        cancelRecovery(event.getEntity());
        clearTransitions(event.getEntity());
        ACTIVE_WEAPONS.remove(event.getEntity().getUUID());
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
        Entity maid = findMaid(player, maidId);
        if (maid != null) emitRecallFinish(maid);
        if (recallIntoStack(player, maidId, weapon)) {
            ACTIVE_WEAPONS.remove(player.getUUID());
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
        rescueSelfStoredContract(player);
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
            if (TripleMagicCompat.isPhantom(stack)) continue;
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
    private static boolean rescueSelfStoredContract(Player player) {
        Entity menuMaid = getOpenedMaid(player.containerMenu);
        if (!TouhouLittleMaidHelper.isOwnedMaid(menuMaid, player)) return false;

        String maidId = menuMaid.getStringUUID();
        String entityBinding = menuMaid.getPersistentData().getString(
                TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID);
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container == player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (TripleMagicCompat.isPhantom(stack)) continue;
            if (!MaidInfusion.isInfused(stack) || !MaidWeaponItem.isOwner(stack, player)) continue;

            String weaponBinding = MaidWeaponItem.getBindingId(stack);
            boolean exactBinding = !entityBinding.isEmpty() && entityBinding.equals(weaponBinding);
            boolean legacyBinding = maidId.equals(MaidWeaponItem.getBoundMaidUUID(stack));
            if (!exactBinding && !legacyBinding) continue;

            ItemStack rescued = stack.copy();
            slot.set(ItemStack.EMPTY);
            slot.setChanged();
            returnContractToPlayer(player, rescued);
            player.containerMenu.broadcastChanges();
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.self_contract_inventory_blocked"),
                    true);
            return true;
        }
        return false;
    }

    private static Entity getOpenedMaid(AbstractContainerMenu menu) {
        if (menu == null) return null;
        try {
            Method getter = menu.getClass().getMethod("getMaid");
            Object value = getter.invoke(menu);
            return value instanceof Entity entity ? entity : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static void returnContractToPlayer(Player player, ItemStack stack) {
        int selected = player.getInventory().selected;
        if (player.getInventory().getItem(selected).isEmpty()) {
            player.getInventory().setItem(selected, stack);
            return;
        }
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false);
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
        Entity maid = findMaid(player, active.maidId());
        ItemStack weapon = findBoundWeapon(player, active.maidId());
        if (maid == null) {
            if (!weapon.isEmpty() && MaidInfusion.containsMaid(weapon)) {
                ACTIVE_WEAPONS.remove(player.getUUID());
                clearTransitions(player);
                return false;
            }
            if (!weapon.isEmpty() && !weapon.getOrCreateTag().getBoolean(RECOVERY_FAILED)) {
                startRecovery(player, active, weapon);
            }
            return false;
        }
        if (maid.level() == player.level()
                && maid.distanceToSqr(player) <= MAX_DEPLOYMENT_DISTANCE_SQR) return true;

        if (!weapon.isEmpty()) rememberDeploymentLocation(weapon, maid);
        clearTransitions(player);
        if (recall(player, active.maidId())) {
            ACTIVE_WEAPONS.remove(player.getUUID());
        } else if (!weapon.isEmpty()) {
            startRecovery(player, active, weapon);
        }
        return false;
    }

    private static void maintainActiveDeployment(Player player, ActiveDeployment active) {
        Entity maid = findMaid(player, active.maidId());
        ItemStack weapon = findBoundWeapon(player, active.maidId());
        if (maid == null || weapon.isEmpty()) return;
        maintainManifestedMaid(player, weapon, maid);
    }

    private static void maintainDesiredDeployment(Player player, ItemStack weapon,
                                                  DesiredDeployment desired) {
        Entity maid = findMaid(player, desired.maidId());
        if (maid == null) return;
        String entityBinding = maid.getPersistentData().getString(
                TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID);
        if (!desired.bindingId().equals(entityBinding)) return;
        ACTIVE_WEAPONS.put(player.getUUID(),
                new ActiveDeployment(desired.maidId(), desired.bindingId()));
        maintainManifestedMaid(player, weapon, maid);
    }

    private static void maintainManifestedMaid(Player player, ItemStack weapon, Entity maid) {
        if (player.tickCount % 20 == 0 || readDeploymentLocation(weapon) == null) {
            rememberDeploymentLocation(weapon, maid);
        }
        if (player.tickCount % 20 == 0) {
            TouhouLittleMaidHelper.syncFavorabilityFromMaid(maid, weapon);
        }
        weapon.getOrCreateTag().remove(RECOVERY_FAILED);
        TouhouLittleMaidHelper.setAllDaySchedule(maid);
        if (maid instanceof LivingEntity living) {
            TripleMagicCompat.equipPhantoms(player, living, weapon);
            TripleMagicCompat.syncMaidSpellLoadout(player, living, weapon);
            configureCombatTask(player, weapon, maid);
            TripleMagicCompat.castFallbackSpell(player, living, weapon);
        }
    }

    private static boolean manifestDesired(Player player, ItemStack weapon,
                                           DesiredDeployment desired) {
        DesiredDeployment current = desiredDeployment(player);
        if (!sameDesired(desired, current)) return false;

        Entity maid = findMaid(player, desired.maidId());
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
            if (!weapon.getOrCreateTag().getBoolean(RECOVERY_FAILED)) {
                startRecovery(player,
                        new ActiveDeployment(desired.maidId(), desired.bindingId()), weapon);
            }
            return false;
        }

        if (!TouhouLittleMaidHelper.convertWeaponToMaid(player, weapon, false)) return false;
        maid = findMaid(player, desired.maidId());
        if (maid == null) return false;

        weapon.getOrCreateTag().putString(ORIGINAL_TASK,
                TouhouLittleMaidHelper.getMaidTaskId(maid));
        TouhouLittleMaidHelper.setAllDaySchedule(maid);
        if (maid instanceof LivingEntity living) {
            TripleMagicCompat.equipPhantoms(player, living, weapon);
            TripleMagicCompat.syncMaidSpellLoadout(player, living, weapon);
            configureCombatTask(player, weapon, maid);
        }
        rememberDeploymentLocation(weapon, maid);
        weapon.getOrCreateTag().remove(RECOVERY_FAILED);
        ACTIVE_WEAPONS.put(player.getUUID(),
                new ActiveDeployment(desired.maidId(), desired.bindingId()));
        emitDeployFinish(maid);
        return true;
    }

    private static void emitDeployBuildup(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                player.getX(), player.getY() + 0.15, player.getZ(),
                6, 0.45, 0.08, 0.45, 0.015);
    }

    private static void emitDeployFinish(Entity maid) {
        if (!(maid.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                maid.getX(), maid.getY() + maid.getBbHeight() * 0.55, maid.getZ(),
                24, 0.55, maid.getBbHeight() * 0.45, 0.55, 0.035);
        level.sendParticles(ParticleTypes.END_ROD,
                maid.getX(), maid.getY() + 0.2, maid.getZ(),
                8, 0.35, 0.08, 0.35, 0.015);
        level.playSound(null, maid.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.7f, 1.35f);
    }

    private static void emitRecallBuildup(Player player, ActiveDeployment active) {
        Entity maid = findMaid(player, active.maidId());
        if (!(maid != null && maid.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                maid.getX(), maid.getY() + maid.getBbHeight() * 0.5, maid.getZ(),
                5, 0.5, maid.getBbHeight() * 0.4, 0.5, 0.01);
    }

    private static void emitRecallFinish(Entity maid) {
        if (!(maid.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.POOF,
                maid.getX(), maid.getY() + maid.getBbHeight() * 0.5, maid.getZ(),
                16, 0.5, maid.getBbHeight() * 0.4, 0.5, 0.025);
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                maid.getX(), maid.getY() + maid.getBbHeight() * 0.5, maid.getZ(),
                14, 0.45, maid.getBbHeight() * 0.35, 0.45, 0.02);
        level.playSound(null, maid.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.65f, 0.8f);
    }

    private static void clearTransitions(Player player) {
        PENDING_DEPLOYMENTS.remove(player.getUUID());
        PENDING_RECALLS.remove(player.getUUID());
    }

    private static boolean isAutoWeapon(ItemStack stack, Player player) {
        return MaidInfusion.isInfused(stack) && !MaidInfusion.isContractBlade(stack)
                && MaidWeaponItem.isOwner(stack, player)
                && !MaidWeaponItem.isContractSuperseded(stack)
                && MaidInfusion.data(stack).getResonance() > 0
                && !isCoolingDown(player, MaidWeaponItem.getBoundMaidUUID(stack));
    }

    private static void configureCombatTask(Player owner, ItemStack weapon, Entity maid) {
        boolean slashBladeMode = SlashBladeCompat.usesTruePowerTask(weapon);
        boolean magicMode = !slashBladeMode && TripleMagicCompat.usesMaidSpellTask(weapon);
        String desired = slashBladeMode
                ? SlashBladeCompat.getTruePowerTaskId()
                : magicMode ? TripleMagicCompat.getMaidSpellRangedTaskId() : ATTACK_TASK;
        String current = TouhouLittleMaidHelper.getMaidTaskId(maid);
        if (!desired.equals(current)) {
            TouhouLittleMaidHelper.switchMaidTask(maid, desired);
            current = TouhouLittleMaidHelper.getMaidTaskId(maid);
        }

        if (slashBladeMode && !desired.equals(current)) {
            reportTaskFailure(owner, weapon, SLASHBLADE_TASK_FAILURE,
                    "SlashBlade", desired, current,
                    "maid_weapon.message.slashblade_task_unavailable");
            if (!ATTACK_TASK.equals(current)) {
                TouhouLittleMaidHelper.switchMaidTask(maid, ATTACK_TASK);
            }
        } else if (magicMode && !desired.equals(current)) {
            reportTaskFailure(owner, weapon, MAGIC_TASK_FAILURE,
                    "magic", desired, current,
                    "maid_weapon.message.magic_task_unavailable");
            if (!ATTACK_TASK.equals(current)) {
                TouhouLittleMaidHelper.switchMaidTask(maid, ATTACK_TASK);
            }
        } else if (slashBladeMode) {
            weapon.getOrCreateTag().remove(SLASHBLADE_TASK_FAILURE);
            weapon.getOrCreateTag().remove(MAGIC_TASK_FAILURE);
        } else if (magicMode) {
            weapon.getOrCreateTag().remove(MAGIC_TASK_FAILURE);
            weapon.getOrCreateTag().remove(SLASHBLADE_TASK_FAILURE);
        } else {
            weapon.getOrCreateTag().remove(MAGIC_TASK_FAILURE);
            weapon.getOrCreateTag().remove(SLASHBLADE_TASK_FAILURE);
        }
    }

    private static void reportTaskFailure(Player owner, ItemStack weapon, String failureTag,
                                          String mode, String desired, String current,
                                          String messageKey) {
        if (weapon.getOrCreateTag().getBoolean(failureTag)) return;
        weapon.getOrCreateTag().putBoolean(failureTag, true);
        LOGGER.warn("[MaidWeapon] Failed to select {} task for {}: item={}, desired={}, actual={}",
                mode, owner.getScoreboardName(), weapon.getItem(), desired, current);
        owner.displayClientMessage(Component.translatable(messageKey), true);
    }

    public static boolean forceRecall(Player player, String maidId, int cooldownTicks) {
        clearTransitions(player);
        Entity deployed = findMaid(player, maidId);
        float recoveryHealth = deployed instanceof LivingEntity living
                ? Math.max(1.0f, living.getMaxHealth() * 0.25f) : 1.0f;
        boolean recalled = recall(player, maidId);
        ACTIVE_WEAPONS.remove(player.getUUID());
        if (recalled && cooldownTicks > 0) {
            ItemStack weapon = findBoundWeapon(player, maidId);
            if (weapon.getTag() != null && weapon.getTag().contains("MaidEntityData")) {
                weapon.getTag().getCompound("MaidEntityData").putFloat("Health", recoveryHealth);
            }
            DEPLOY_COOLDOWNS.put(cooldownKey(player, maidId),
                    player.level().getGameTime() + cooldownTicks);
        }
        return recalled;
    }

    private static boolean recall(Player player, String maidId) {
        // Final invariant: never serialize and discard a maid while her own
        // contract stack is still held by the currently open maid container.
        rescueSelfStoredContract(player);
        ItemStack weapon = findBoundWeapon(player, maidId);
        return recallIntoStack(player, maidId, weapon);
    }

    private static boolean recallIntoStack(Player player, String maidId, ItemStack weapon) {
        Entity maid = findMaid(player, maidId);
        if (weapon.isEmpty() || maid == null) return false;

        String original = weapon.getOrCreateTag().getString(ORIGINAL_TASK);
        if (!original.isEmpty()) TouhouLittleMaidHelper.switchMaidTask(maid, original);
        if (maid instanceof LivingEntity living) {
            TripleMagicCompat.clearPhantoms(living, weapon);
        }
        if (TouhouLittleMaidHelper.convertMaidToWeapon(player, maid, weapon, false)) {
            weapon.getOrCreateTag().remove(ORIGINAL_TASK);
            clearDeploymentLocation(weapon);
            return true;
        }
        return false;
    }

    public static ItemStack findBoundWeapon(Player player, String maidId) {
        Entity deployed = findMaid(player, maidId);
        String deployedBinding = deployed == null ? ""
                : deployed.getPersistentData().getString(
                        TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID);
        ItemStack uuidCandidate = ItemStack.EMPTY;
        int uuidMatches = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!deployedBinding.isEmpty()
                    && deployedBinding.equals(MaidWeaponItem.getBindingId(stack))) return stack;
            if (maidId.equals(MaidWeaponItem.getBoundMaidUUID(stack))) {
                uuidCandidate = stack;
                uuidMatches++;
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (!deployedBinding.isEmpty()
                && deployedBinding.equals(MaidWeaponItem.getBindingId(offhand))) return offhand;
        if (maidId.equals(MaidWeaponItem.getBoundMaidUUID(offhand)) && offhand != uuidCandidate) {
            uuidCandidate = offhand;
            uuidMatches++;
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (!deployedBinding.isEmpty()
                && deployedBinding.equals(MaidWeaponItem.getBindingId(carried))) return carried;
        if (maidId.equals(MaidWeaponItem.getBoundMaidUUID(carried)) && carried != uuidCandidate) {
            uuidCandidate = carried;
            uuidMatches++;
        }
        for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (!deployedBinding.isEmpty()
                    && deployedBinding.equals(MaidWeaponItem.getBindingId(stack))) return stack;
            if (maidId.equals(MaidWeaponItem.getBoundMaidUUID(stack)) && stack != uuidCandidate) {
                uuidCandidate = stack;
                uuidMatches++;
            }
        }
        // Legacy fallback is allowed only when the maid UUID identifies exactly one item.
        return uuidMatches == 1 ? uuidCandidate : ItemStack.EMPTY;
    }

    private static Entity findMaid(Player player, String maidId) {
        try {
            UUID uuid = UUID.fromString(maidId);
            if (player.getServer() == null) return null;
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity entity = level.getEntity(uuid);
                if (TouhouLittleMaidHelper.isOwnedMaid(entity, player)) return entity;
            }
            return null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** True only while this exact contract's maid is currently present in a loaded level. */
    public static boolean hasDeployedMaid(Player player, ItemStack weapon) {
        if (!MaidInfusion.isInfused(weapon) || !MaidWeaponItem.isOwner(weapon, player)) return false;
        String maidId = MaidWeaponItem.getBoundMaidUUID(weapon);
        return maidId != null && !maidId.isEmpty() && findMaid(player, maidId) != null;
    }

    private static void rememberDeploymentLocation(ItemStack weapon, Entity maid) {
        CompoundTag location = new CompoundTag();
        location.putString("Dimension", maid.level().dimension().location().toString());
        location.putInt("X", maid.blockPosition().getX());
        location.putInt("Y", maid.blockPosition().getY());
        location.putInt("Z", maid.blockPosition().getZ());
        weapon.getOrCreateTag().put(DEPLOYMENT_LOCATION, location);
    }

    private static DeploymentLocation readDeploymentLocation(ItemStack weapon) {
        CompoundTag root = weapon.getTag();
        if (root == null || !root.contains(DEPLOYMENT_LOCATION)) return null;
        CompoundTag location = root.getCompound(DEPLOYMENT_LOCATION);
        try {
            ResourceLocation id = new ResourceLocation(location.getString("Dimension"));
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
            return new DeploymentLocation(dimension, new BlockPos(
                    location.getInt("X"), location.getInt("Y"), location.getInt("Z")));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void clearDeploymentLocation(ItemStack weapon) {
        CompoundTag tag = weapon.getTag();
        if (tag == null) return;
        tag.remove(DEPLOYMENT_LOCATION);
        tag.remove(RECOVERY_FAILED);
    }

    private static boolean startRecovery(Player player, ActiveDeployment deployment,
                                         ItemStack weapon) {
        if (weapon.isEmpty() || RECOVERIES.containsKey(player.getUUID())) return false;
        DeploymentLocation location = readDeploymentLocation(weapon);
        if (location == null || player.getServer() == null) return false;
        ServerLevel source = player.getServer().getLevel(location.dimension());
        if (source == null) {
            notifyRecoveryFailure(player, weapon, location);
            ACTIVE_WEAPONS.remove(player.getUUID());
            return true;
        }
        RecoveryAttempt attempt = new RecoveryAttempt(deployment, location,
                player.getServer().overworld().getGameTime());
        clearTransitions(player);
        RECOVERIES.put(player.getUUID(), attempt);
        addRecoveryTicket(source, attempt, new ChunkPos(location.pos()));
        return true;
    }

    /** Returns true while recovery owns this player's deployment processing for this tick. */
    private static boolean processRecovery(Player player) {
        RecoveryAttempt attempt = RECOVERIES.get(player.getUUID());
        if (attempt == null || player.getServer() == null) return false;
        Entity maid = findMaid(player, attempt.deployment.maidId());
        if (maid != null && recall(player, attempt.deployment.maidId())) {
            finishRecovery(player, attempt);
            return true;
        }

        long elapsed = player.getServer().overworld().getGameTime() - attempt.startedAt;
        ServerLevel source = player.getServer().getLevel(attempt.location.dimension());
        if (source != null && elapsed >= RECOVERY_NEIGHBOR_DELAY && !attempt.neighborsRequested) {
            attempt.neighborsRequested = true;
            ChunkPos center = new ChunkPos(attempt.location.pos());
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || z != 0) {
                        addRecoveryTicket(source, attempt,
                                new ChunkPos(center.x + x, center.z + z));
                    }
                }
            }
        }
        if (elapsed < RECOVERY_TIMEOUT) return true;

        ItemStack weapon = findBoundWeapon(player, attempt.deployment.maidId());
        notifyRecoveryFailure(player, weapon, attempt.location);
        finishRecovery(player, attempt);
        return true;
    }

    private static void addRecoveryTicket(ServerLevel level, RecoveryAttempt attempt,
                                          ChunkPos chunk) {
        if (attempt.tickets.add(chunk)) {
            level.getChunkSource().addRegionTicket(
                    RECOVERY_TICKET, chunk, 0, attemptOwner(attempt));
        }
    }

    private static UUID attemptOwner(RecoveryAttempt attempt) {
        return UUID.nameUUIDFromBytes((attempt.deployment.bindingId() + ":"
                + attempt.deployment.maidId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void finishRecovery(Player player, RecoveryAttempt attempt) {
        if (player.getServer() != null) {
            ServerLevel source = player.getServer().getLevel(attempt.location.dimension());
            if (source != null) {
                UUID owner = attemptOwner(attempt);
                for (ChunkPos chunk : attempt.tickets) {
                    source.getChunkSource().removeRegionTicket(
                            RECOVERY_TICKET, chunk, 0, owner);
                }
            }
        }
        RECOVERIES.remove(player.getUUID());
        ACTIVE_WEAPONS.remove(player.getUUID());
        clearTransitions(player);
    }

    private static void cancelRecovery(Player player) {
        RecoveryAttempt attempt = RECOVERIES.get(player.getUUID());
        if (attempt != null) finishRecovery(player, attempt);
    }

    private static void notifyRecoveryFailure(Player player, ItemStack weapon,
                                              DeploymentLocation location) {
        if (!weapon.isEmpty()) weapon.getOrCreateTag().putBoolean(RECOVERY_FAILED, true);
        player.displayClientMessage(Component.translatable(
                "maid_weapon.message.teleport_recovery_failed",
                location.dimension().location().toString(), location.pos().getX(),
                location.pos().getY(), location.pos().getZ()), false);
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

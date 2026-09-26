package com.maidweapon.forge.system.deployment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns cross-chunk/cross-dimension recovery bookkeeping for manifested maids.
 *
 * <p>The hotbar deployment state machine only decides when recovery is needed;
 * this service owns location persistence, region tickets, timeout and failure
 * diagnostics.</p>
 */
public final class ContractRecoveryService {
    public static final String RECOVERY_FAILED = "MaidDeploymentRecoveryFailed";
    private static final String DEPLOYMENT_LOCATION = "MaidDeploymentLocation";
    private static final int RECOVERY_NEIGHBOR_DELAY = 20;
    private static final int RECOVERY_TIMEOUT = 40;
    private static final TicketType<UUID> RECOVERY_TICKET = TicketType.create(
            "maid_weapon_recovery", UUID::compareTo, RECOVERY_TIMEOUT + 20);

    public enum StartResult {
        NOT_STARTED,
        STARTED,
        TERMINAL_FAILURE
    }

    public enum TickResult {
        IDLE,
        WAITING,
        TIMED_OUT
    }

    private record DeploymentLocation(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final class RecoveryAttempt {
        private final String maidId;
        private final String bindingId;
        private final DeploymentLocation location;
        private final long startedAt;
        private final Set<ChunkPos> tickets = new HashSet<>();
        private boolean neighborsRequested;

        private RecoveryAttempt(String maidId, String bindingId,
                                DeploymentLocation location, long startedAt) {
            this.maidId = maidId;
            this.bindingId = bindingId;
            this.location = location;
            this.startedAt = startedAt;
        }
    }

    private static final Map<UUID, RecoveryAttempt> RECOVERIES = new HashMap<>();

    public static boolean hasRecovery(Player player) {
        return player != null && RECOVERIES.containsKey(player.getUUID());
    }

    public static String currentMaidId(Player player) {
        RecoveryAttempt attempt = player == null ? null : RECOVERIES.get(player.getUUID());
        return attempt == null ? null : attempt.maidId;
    }

    public static void rememberLocation(ItemStack weapon, Entity maid) {
        if (weapon.isEmpty() || maid == null) return;
        CompoundTag location = new CompoundTag();
        location.putString("Dimension", maid.level().dimension().location().toString());
        location.putInt("X", maid.blockPosition().getX());
        location.putInt("Y", maid.blockPosition().getY());
        location.putInt("Z", maid.blockPosition().getZ());
        weapon.getOrCreateTag().put(DEPLOYMENT_LOCATION, location);
    }

    public static boolean hasLocation(ItemStack weapon) {
        return readLocation(weapon) != null;
    }

    public static void clearLocation(ItemStack weapon) {
        CompoundTag tag = weapon.getTag();
        if (tag == null) return;
        tag.remove(DEPLOYMENT_LOCATION);
        tag.remove(RECOVERY_FAILED);
    }

    public static boolean failed(ItemStack weapon) {
        return !weapon.isEmpty()
                && weapon.getTag() != null
                && weapon.getTag().getBoolean(RECOVERY_FAILED);
    }

    public static void clearFailure(ItemStack weapon) {
        if (!weapon.isEmpty() && weapon.getTag() != null) {
            weapon.getTag().remove(RECOVERY_FAILED);
        }
    }

    public static StartResult start(
            Player player, String maidId, String bindingId, ItemStack weapon) {
        if (player == null || weapon.isEmpty() || maidId == null || bindingId == null
                || RECOVERIES.containsKey(player.getUUID())) {
            return StartResult.NOT_STARTED;
        }
        DeploymentLocation location = readLocation(weapon);
        if (location == null || player.getServer() == null) {
            return StartResult.NOT_STARTED;
        }

        ServerLevel source = player.getServer().getLevel(location.dimension());
        if (source == null) {
            notifyFailure(player, weapon, location);
            return StartResult.TERMINAL_FAILURE;
        }

        RecoveryAttempt attempt = new RecoveryAttempt(
                maidId, bindingId, location,
                player.getServer().overworld().getGameTime());
        RECOVERIES.put(player.getUUID(), attempt);
        addTicket(source, attempt, new ChunkPos(location.pos()));
        return StartResult.STARTED;
    }

    public static TickResult tick(Player player) {
        RecoveryAttempt attempt = player == null ? null : RECOVERIES.get(player.getUUID());
        if (attempt == null || player.getServer() == null) return TickResult.IDLE;

        long elapsed = player.getServer().overworld().getGameTime() - attempt.startedAt;
        ServerLevel source = player.getServer().getLevel(attempt.location.dimension());
        if (source != null && elapsed >= RECOVERY_NEIGHBOR_DELAY && !attempt.neighborsRequested) {
            attempt.neighborsRequested = true;
            ChunkPos center = new ChunkPos(attempt.location.pos());
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || z != 0) {
                        addTicket(source, attempt,
                                new ChunkPos(center.x + x, center.z + z));
                    }
                }
            }
        }
        if (elapsed < RECOVERY_TIMEOUT) return TickResult.WAITING;

        ItemStack weapon = ContractWeaponLocator.findBoundWeapon(player, attempt.maidId);
        notifyFailure(player, weapon, attempt.location);
        return TickResult.TIMED_OUT;
    }

    public static void finish(Player player) {
        RecoveryAttempt attempt = player == null ? null : RECOVERIES.remove(player.getUUID());
        if (attempt == null || player.getServer() == null) return;
        ServerLevel source = player.getServer().getLevel(attempt.location.dimension());
        if (source == null) return;
        UUID owner = attemptOwner(attempt);
        for (ChunkPos chunk : attempt.tickets) {
            source.getChunkSource().removeRegionTicket(
                    RECOVERY_TICKET, chunk, 0, owner);
        }
    }

    public static void cancel(Player player) {
        finish(player);
    }

    private static DeploymentLocation readLocation(ItemStack weapon) {
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

    private static void addTicket(
            ServerLevel level, RecoveryAttempt attempt, ChunkPos chunk) {
        if (attempt.tickets.add(chunk)) {
            level.getChunkSource().addRegionTicket(
                    RECOVERY_TICKET, chunk, 0, attemptOwner(attempt));
        }
    }

    private static UUID attemptOwner(RecoveryAttempt attempt) {
        return UUID.nameUUIDFromBytes(
                (attempt.bindingId + ":" + attempt.maidId).getBytes(StandardCharsets.UTF_8));
    }

    private static void notifyFailure(
            Player player, ItemStack weapon, DeploymentLocation location) {
        if (!weapon.isEmpty()) weapon.getOrCreateTag().putBoolean(RECOVERY_FAILED, true);
        player.displayClientMessage(Component.translatable(
                "maid_weapon.message.teleport_recovery_failed",
                location.dimension().location().toString(),
                location.pos().getX(), location.pos().getY(), location.pos().getZ()), false);
    }

    private ContractRecoveryService() {}
}

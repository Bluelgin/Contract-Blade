package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.api.IntrinsicSpiritApi;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Arena/transport lifecycle. The separate encounter service owns combat and cleanup. */
public final class FoxChallengeService {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.parse("maid_weapon:fox_challenge"));
    private static final String RETURN = "MaidWeaponFoxChallengeReturn";
    private static final String FAILURE = "MaidWeaponFoxChallengeFailed";

    public static boolean failurePending(ServerPlayer player) {
        return inside(player) && player.getPersistentData().getBoolean(FAILURE);
    }

    /** Cancel lethal damage now; travel at the player tick boundary, not inside an attack callback. */
    public static boolean queueFailure(ServerPlayer player) {
        if (FoxChallengeCollapse.active(player)) return false;
        if (!inside(player) || BlackFoxEncounters.get(player) == null && !failurePending(player)) return false;
        player.getPersistentData().putBoolean(FAILURE, true);
        player.setHealth(Math.max(1, player.getMaxHealth() * .25f));
        player.clearFire();
        for (var effect : player.getActiveEffects().stream().map(instance -> instance.getEffect()).toList())
            if (effect.getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) player.removeEffect(effect);
        player.fallDistance = 0;
        player.invulnerableTime = 40;
        return true;
    }

    public static boolean tickFailure(ServerPlayer player) {
        if (!failurePending(player)) return false;
        BlackFoxEncounters.stop(player);
        // Use the same companion-safe recall/return path; pending failure blocks further damage if recall must retry.
        if (leave(player)) {
            player.invulnerableTime = 40;
            player.sendSystemMessage(Component.translatable("maid_weapon.fox.challenge.failed"));
        }
        return true;
    }

    public static boolean inside(ServerPlayer player) { return player.level().dimension().equals(LEVEL); }

    public static BlockPos origin(ServerPlayer player) {
        return FoxChallengeArena.origin(FoxChallengeSavedData.get(player.getServer()).cell(player.getUUID()));
    }

    public static boolean enter(ServerPlayer player, ItemStack white, CompoundTag returnPoint) {
        if (inside(player) || player.isPassenger() || !player.isAlive()) return false;
        var target = player.getServer().getLevel(LEVEL);
        if (target == null) return false;
        if (!white.isEmpty() && !IntrinsicSpiritApi.prepareSpiritTransfer(player, white, FoxSpiritState.WHITE)) {
            player.displayClientMessage(Component.translatable("maid_weapon.fox.challenge.recall_first"), true);
            return false;
        }
        BlockPos origin = origin(player);
        FoxChallengeArena.build(target, origin);
        returnPoint = returnPoint.copy();
        var root = player.getPersistentData();
        var persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(RETURN, returnPoint);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
        if (player.isSleeping()) player.stopSleepInBed(true, false);
        BlockPos entry = FoxChallengeArena.entry(origin);
        player.teleportTo(target, entry.getX() + 0.5, entry.getY() + 0.1, entry.getZ() + 0.5, 0, 0);
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.sendSystemMessage(Component.translatable("maid_weapon.fox.challenge.entered"));
        player.sendSystemMessage(Component.translatable("maid_weapon.fox.challenge.leave")
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/foxchallenge leave"))));
        if (!white.isEmpty()) BlackFoxEncounters.start(player);
        return true;
    }

    public static CompoundTag position(ServerPlayer player) {
        return position(player.level().dimension().location(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    public static CompoundTag position(ResourceLocation dimension, double x, double y, double z, float yaw, float pitch) {
        var point = new CompoundTag();
        point.putString("Dimension", dimension.toString());
        point.putDouble("X", x); point.putDouble("Y", y); point.putDouble("Z", z);
        point.putFloat("Yaw", yaw); point.putFloat("Pitch", pitch);
        return point;
    }

    public static boolean leave(ServerPlayer player) {
        if (!inside(player)) return false;
        // Recall the owner's manifested carriers in this arena before crossing dimensions.
        // Shared deployment authority serializes the maid, not this transport service.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!ContractCarrierData.isOwner(stack, player)) continue;
            var uuid = ContractCarrierData.getBoundMaidUUID(stack);
            var maid = uuid == null ? null
                    : ContractWeaponLocator.findManifestedMaid(player, uuid);
            if (maid != null && maid.level() == player.level()
                    && !InfusedMaidDeploymentSystem.forceRecall(player, uuid, 0)) {
                player.displayClientMessage(Component.translatable("maid_weapon.fox.challenge.recall_first"), true);
                return false;
            }
        }
        return restore(player);
    }

    /** Used for death/reconnect too; never changes respawn points, inventory or health. */
    public static boolean restore(ServerPlayer player) {
        BlackFoxEncounters.stop(player);
        var destination = returnDestination(player);
        FoxChallengeDrops.returnLoose(player, destination);
        player.teleportTo(destination.level(), destination.position().x, destination.position().y, destination.position().z,
                destination.yaw(), destination.pitch());
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        forgetReturn(player);
        return true;
    }

    public record Destination(ServerLevel level, Vec3 position, float yaw, float pitch) { }

    /** Shared by player return and actual death drops; never reads or restores an inventory snapshot. */
    public static Destination returnDestination(ServerPlayer player) {
        var root = player.getPersistentData();
        var persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        var point = persisted.getCompound(RETURN);
        var id = ResourceLocation.tryParse(point.getString("Dimension"));
        var target = id == null ? null : player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        boolean saved = target != null && !target.dimension().equals(LEVEL)
                && point.contains("X") && Double.isFinite(point.getDouble("X"))
                && Double.isFinite(point.getDouble("Y")) && Double.isFinite(point.getDouble("Z"));
        if (!saved) target = player.getServer().overworld();
        BlockPos spawn = target.getSharedSpawnPos();
        return new Destination(target, new Vec3(saved ? point.getDouble("X") : spawn.getX() + 0.5,
                saved ? point.getDouble("Y") : spawn.getY() + 1.1,
                saved ? point.getDouble("Z") : spawn.getZ() + 0.5), point.getFloat("Yaw"), point.getFloat("Pitch"));
    }

    public static boolean hasReturn(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(RETURN);
    }

    public static void forgetReturn(ServerPlayer player) {
        FoxChallengeCollapse.clear(player);
        player.getPersistentData().remove(FAILURE);
        var root = player.getPersistentData();
        var persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.remove(RETURN);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static void copyReturn(Player old, Player replacement) {
        var point = old.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(RETURN);
        if (point.isEmpty()) return;
        var persisted = replacement.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(RETURN, point.copy());
        replacement.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private FoxChallengeService() { }
}

package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import java.util.Collection;
import java.util.function.Function;

/** Moves concrete drops, never copies a pre-fight inventory or changes keepInventory. */
public final class FoxChallengeDrops {
    private static final String RECOVERY = "MaidWeaponFoxRecoveryUntil";

    public static int returnDeathDrops(ServerPlayer player, Collection<ItemEntity> drops) {
        if (!FoxChallengeService.inside(player)) return 0;
        int moved = 0;
        var destination = FoxChallengeService.returnDestination(player);
        for (var iterator = drops.iterator(); iterator.hasNext();) {
            ItemEntity drop = iterator.next();
            if (move(drop, destination)) {
                // The native death pipeline must not spawn the old entity after it has moved.
                iterator.remove(); moved++;
            }
        }
        return moved;
    }

    /** Private-cell leftovers, including pre-fix deaths. Only loaded items in this owner's enclosure. */
    public static int returnLoose(ServerPlayer player, FoxChallengeService.Destination destination) {
        var arena = player.getServer().getLevel(FoxChallengeService.LEVEL);
        if (arena == null || destination.level() == arena) return 0;
        var origin = FoxChallengeService.origin(player);
        var bounds = new AABB(origin.offset(-24, 0, -24), origin.offset(25, 19, 25));
        int moved = 0;
        for (var item : arena.getEntitiesOfClass(ItemEntity.class, bounds,
                entity -> !entity.isRemoved() && FoxChallengeArena.contains(origin, entity.getX(), entity.getY(), entity.getZ())))
            if (move(item, destination)) moved++;
        return moved;
    }

    private static boolean move(ItemEntity item, FoxChallengeService.Destination destination) {
        if (item.isRemoved() || item.getItem().isEmpty() || item.level() == destination.level()) return false;
        Entity moved = item.changeDimension(destination.level(), new ITeleporter() {
            @Override public PortalInfo getPortalInfo(Entity entity, ServerLevel level, Function<ServerLevel, PortalInfo> vanilla) {
                return new PortalInfo(destination.position().add(0, .25, 0), Vec3.ZERO, 0, 0);
            }
            @Override public Entity placeEntity(Entity entity, ServerLevel from, ServerLevel to, float yaw,
                    Function<Boolean, Entity> reposition) { return reposition.apply(false); }
        });
        if (!(moved instanceof ItemEntity returned)) return false;
        // Preserve the entity's owner, item capabilities, custom lifespan and other mod data.
        var state = returned.saveWithoutId(new CompoundTag());
        state.putShort("Age", (short) 0);
        state.putShort("PickupDelay", (short) 10);
        returned.load(state);
        returned.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    /** Existing admin preview loads the old private cell; return after entity loading has had time to finish. */
    public static boolean startRecovery(ServerPlayer player) {
        if (FoxChallengeService.inside(player)) return FoxChallengeService.leave(player);
        if (!FoxChallengeService.enter(player, ItemStack.EMPTY, FoxChallengeService.position(player))) return false;
        player.getPersistentData().putLong(RECOVERY, player.level().getGameTime() + 40);
        return true;
    }
    public static void tickRecovery(ServerPlayer player) {
        var data = player.getPersistentData();
        if (!data.contains(RECOVERY)) return;
        if (!FoxChallengeService.inside(player)) { data.remove(RECOVERY); return; }
        if (player.level().getGameTime() >= data.getLong(RECOVERY) && player.isAlive()
                && FoxChallengeService.leave(player)) data.remove(RECOVERY);
    }
    private FoxChallengeDrops() { }
}

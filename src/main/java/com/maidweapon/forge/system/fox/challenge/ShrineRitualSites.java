package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.SlashBladeCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/** Authored shrine anchors, learned from the retained native offering stand and kept after its removal. */
public final class ShrineRitualSites extends SavedData {
    public static final BlockPos WHITE_STAND = new BlockPos(23, 4, 26);
    public static final BlockPos BLACK_STAND = new BlockPos(23, 6, 39);
    private final Set<BlockPos> origins = new HashSet<>();

    public static ShrineRitualSites get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ShrineRitualSites::load,
                ShrineRitualSites::new, "maid_weapon_shrine_ritual_sites");
    }

    public static void observe(ItemFrame frame) {
        if (frame.level() instanceof ServerLevel level && frame.getTags().contains(
                com.maidweapon.forge.system.fox.ShrineOfferingService.STAND_TAG)) {
            var sites = get(level);
            if (sites.origins.add(frame.getPos().subtract(WHITE_STAND))) sites.setDirty();
        }
    }

    public static boolean prepared(ServerLevel level, BlockPos bed) {
        return preparedStand(level, bed) != null;
    }

    public static ItemFrame preparedStand(ServerLevel level, BlockPos bed) {
        for (BlockPos anchor : nearbyDaises(level, bed)) {
            var stands = level.getEntitiesOfClass(ItemFrame.class, new AABB(anchor).inflate(1),
                    frame -> frame.getPos().equals(anchor)
                            && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(frame.getType())
                            .toString().equals("slashblade:blade_stand_entity")
                            && SlashBladeCompat.isNamedBlade(frame.getItem(), "item.slashblade.fox_black"));
            if (!stands.isEmpty()) return stands.get(0);
        }
        return null;
    }

    public static boolean nearDais(ServerLevel level, BlockPos position) {
        return !nearbyDaises(level, position).isEmpty();
    }

    private static List<BlockPos> nearbyDaises(ServerLevel level, BlockPos position) {
        // Migrates already loaded old shrines without requiring a new structure or a retained blade.
        level.getEntitiesOfClass(ItemFrame.class, new AABB(position).inflate(24, 16, 24))
                .forEach(ShrineRitualSites::observe);
        var nearby = new ArrayList<BlockPos>();
        for (BlockPos origin : get(level).origins) {
            BlockPos anchor = origin.offset(BLACK_STAND);
            if (anchor.distSqr(position) > 8 * 8 || !level.hasChunkAt(anchor)
                    || !level.getBlockState(anchor.below()).is(Blocks.POLISHED_BLACKSTONE_BRICKS)) continue;
            nearby.add(anchor);
        }
        return nearby;
    }

    private static ShrineRitualSites load(CompoundTag tag) {
        var data = new ShrineRitualSites();
        for (long origin : tag.getLongArray("Origins")) data.origins.add(BlockPos.of(origin));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("Origins", origins.stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }
}

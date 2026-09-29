package com.maidweapon.forge.system.interior.home;

import com.maidweapon.forge.compat.tlm.TlmHomeBoardGameAdapter;
import com.maidweapon.forge.compat.tlm.TlmHomeFurnitureAdapter;
import com.maidweapon.forge.compat.tlm.TlmHomeJoyAdapter;
import com.maidweapon.forge.compat.tlm.TlmHomePicnicAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Per-session index over loaded chunk block entities, NOT a block-volume scan.
 * Rebuilt on entry, coalesced edit events and at most once per minute for external changes.
 * No chunk tickets, getChunk(), world generation or retained offline sessions.
 */
public final class ContractHomeFurnitureRegistry {
    private static final List<ContractHomeFurnitureAdapter> ADAPTERS = new ArrayList<>(List.of(
            new TlmHomeFurnitureAdapter(), new TlmHomeBoardGameAdapter(),
            new TlmHomeJoyAdapter(), new TlmHomePicnicAdapter()));
    public static void registerAdapter(ContractHomeFurnitureAdapter adapter) { ADAPTERS.add(Objects.requireNonNull(adapter)); }
    public record Entry(ActivityTarget target, ContractHomeFurnitureAdapter adapter) {}
    private final NavigableMap<String, Entry> targets = new TreeMap<>();
    private final BlockPos origin;
    private final int radius;
    private long nextRefresh;
    private boolean dirty = true;
    public static final int MAX_TARGETS = 512;
    public static final int MAX_BLOCK_ENTITIES = 8192;

    public ContractHomeFurnitureRegistry(BlockPos origin, int radius) { this.origin = origin; this.radius = radius; }
    public boolean contains(BlockPos pos) {
        double x = Math.abs(pos.getX() - origin.getX()) / (double) radius;
        double z = Math.abs(pos.getZ() - origin.getZ()) / (double) radius;
        return Math.pow(x, 6) + Math.pow(z, 6) <= 1;
    }
    public void invalidate(BlockPos pos) { if (contains(pos)) dirty = true; }
    public void refresh(ServerLevel level, long tick) {
        if (!dirty && tick < nextRefresh) return;
        // At most one coalesced refresh per 20 seconds after block-event storms.
        if (tick < nextRefresh - 800) return;
        dirty = false;
        nextRefresh = tick + 1200;
        targets.clear();
        int examined = 0;
        outer: for (int cx = (origin.getX() - radius) >> 4; cx <= (origin.getX() + radius) >> 4; cx++) {
            for (int cz = (origin.getZ() - radius) >> 4; cz <= (origin.getZ() + radius) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                // Stable truncation even in a heavily furnished plot.
                var positions = new ArrayList<>(chunk.getBlockEntities().keySet());
                positions.sort(Comparator.comparingLong(BlockPos::asLong));
                for (BlockPos pos : positions) {
                    if (++examined > MAX_BLOCK_ENTITIES) break outer;
                    if (!contains(pos)) continue;
                    for (var adapter : ADAPTERS) {
                        var found = adapter.blockTarget(level, pos);
                        if (found.isPresent()) { add(found.get(), adapter); break; }
                    }
                }
            }
        }
        var bounds = new AABB(origin.getX() - radius, level.getMinBuildHeight(), origin.getZ() - radius,
                origin.getX() + radius + 1, level.getMaxBuildHeight(), origin.getZ() + radius + 1);
        var entities = level.getEntities((Entity) null, bounds, e -> contains(e.blockPosition()));
        entities.sort(Comparator.comparing(Entity::getStringUUID));
        int count = 0;
        for (Entity entity : entities) {
            if (++count > MAX_BLOCK_ENTITIES) break;
            for (var adapter : ADAPTERS) {
                var found = adapter.entityTarget(entity);
                if (found.isPresent()) { add(found.get(), adapter); break; }
            }
        }
    }
    private void add(ActivityTarget target, ContractHomeFurnitureAdapter adapter) {
        if (targets.size() < MAX_TARGETS) targets.put(target.key(), new Entry(target, adapter));
    }
    public List<Entry> available(ServerLevel level, Mob maid, Set<String> failed) {
        return targets.values().stream().filter(e -> !failed.contains(e.target.key())
                && e.adapter.valid(level, e.target, maid)).toList();
    }
    public Optional<Entry> find(String key) { return Optional.ofNullable(targets.get(key)); }
}

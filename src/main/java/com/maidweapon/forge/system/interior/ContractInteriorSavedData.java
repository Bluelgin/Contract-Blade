package com.maidweapon.forge.system.interior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Persistent allocation and progression for player-owned contract interior plots.
 *
 * <p>The selected terrain and seed are immutable after first choice. Generated
 * stage is monotonic so later upgrades only materialize newly unlocked land.</p>
 */
public final class ContractInteriorSavedData extends SavedData {
    private static final String DATA_NAME = "maid_weapon_contract_interiors";
    private static final String TAG_NEXT_INDEX = "NextIndex";
    private static final String TAG_PLOTS = "Plots";

    public static final int CELL_SPACING = 1024;
    private static final int CELLS_PER_ROW = 4096;

    private final Map<String, Plot> plots = new HashMap<>();
    private int nextIndex;

    public static final class Plot {
        private final int index;
        private int generatedStage;
        private String terrainTheme;
        private long terrainSeed;

        private Plot(
                int index,
                int generatedStage,
                String terrainTheme,
                long terrainSeed
        ) {
            this.index = index;
            this.generatedStage = generatedStage;
            this.terrainTheme = terrainTheme == null ? "" : terrainTheme;
            this.terrainSeed = terrainSeed;
        }

        public int index() {
            return index;
        }

        /** Backwards-compatible alias for old gallery/interior callers. */
        public int builtSpaceStage() {
            return generatedStage;
        }

        public int generatedStage() {
            return generatedStage;
        }

        public String terrainTheme() {
            return terrainTheme;
        }

        public long terrainSeed() {
            return terrainSeed;
        }

        public boolean hasTerrainTheme() {
            return terrainTheme != null && !terrainTheme.isEmpty();
        }
    }

    public static ContractInteriorSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ContractInteriorSavedData::load,
                ContractInteriorSavedData::new,
                DATA_NAME
        );
    }

    public Plot getOrCreate(String bindingId) {
        Plot existing = plots.get(bindingId);
        if (existing != null) return existing;

        Plot created = new Plot(nextIndex++, 0, "", 0L);
        plots.put(bindingId, created);
        setDirty();
        return created;
    }

    public boolean chooseTerrain(String bindingId, String themeId) {
        Plot plot = getOrCreate(bindingId);
        if (plot.hasTerrainTheme()) return false;

        plot.terrainTheme = themeId;
        plot.terrainSeed = ThreadLocalRandom.current().nextLong();
        // Old experimental home stages must not suppress the first terrain build.
        plot.generatedStage = 0;
        setDirty();
        return true;
    }

    public void markGenerated(String bindingId, int spaceStage) {
        Plot plot = getOrCreate(bindingId);
        if (spaceStage <= plot.generatedStage) return;
        plot.generatedStage = spaceStage;
        setDirty();
    }

    /** @deprecated use {@link #markGenerated(String, int)}. */
    @Deprecated
    public void markBuilt(String bindingId, int spaceStage) {
        markGenerated(bindingId, spaceStage);
    }

    public static int originX(Plot plot) {
        return (plot.index % CELLS_PER_ROW) * CELL_SPACING;
    }

    public static int originZ(Plot plot) {
        return (plot.index / CELLS_PER_ROW) * CELL_SPACING;
    }

    public static ContractInteriorSavedData load(CompoundTag tag) {
        ContractInteriorSavedData data = new ContractInteriorSavedData();
        data.nextIndex = Math.max(0, tag.getInt(TAG_NEXT_INDEX));

        ListTag list = tag.getList(TAG_PLOTS, Tag.TAG_COMPOUND);
        for (Tag value : list) {
            CompoundTag entry = (CompoundTag) value;
            String binding = entry.getString("Binding");
            if (binding.isEmpty()) continue;
            int index = Math.max(0, entry.getInt("Index"));
            int generated = Math.max(
                    0,
                    entry.contains("GeneratedStage")
                            ? entry.getInt("GeneratedStage")
                            : entry.getInt("BuiltSpaceStage")
            );
            String theme = entry.getString("TerrainTheme");
            long seed = entry.contains("TerrainSeed")
                    ? entry.getLong("TerrainSeed")
                    : 0L;
            data.plots.put(
                    binding,
                    new Plot(index, generated, theme, seed)
            );
            data.nextIndex = Math.max(data.nextIndex, index + 1);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(TAG_NEXT_INDEX, nextIndex);

        ListTag list = new ListTag();
        for (Map.Entry<String, Plot> entry : plots.entrySet()) {
            Plot value = entry.getValue();
            CompoundTag plot = new CompoundTag();
            plot.putString("Binding", entry.getKey());
            plot.putInt("Index", value.index);
            plot.putInt("GeneratedStage", value.generatedStage);
            // Keep the legacy field for downgrade/debug readability.
            plot.putInt("BuiltSpaceStage", value.generatedStage);
            if (value.hasTerrainTheme()) {
                plot.putString("TerrainTheme", value.terrainTheme);
                plot.putLong("TerrainSeed", value.terrainSeed);
            }
            list.add(plot);
        }
        tag.put(TAG_PLOTS, list);
        return tag;
    }
}

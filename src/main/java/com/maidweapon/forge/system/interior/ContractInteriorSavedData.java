package com.maidweapon.forge.system.interior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Persistent allocation and build progress for contract interior plots. */
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
        private int builtSpaceStage;

        private Plot(int index, int builtSpaceStage) {
            this.index = index;
            this.builtSpaceStage = builtSpaceStage;
        }

        public int index() {
            return index;
        }

        public int builtSpaceStage() {
            return builtSpaceStage;
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

        Plot created = new Plot(nextIndex++, 0);
        plots.put(bindingId, created);
        setDirty();
        return created;
    }

    public void markBuilt(String bindingId, int spaceStage) {
        Plot plot = getOrCreate(bindingId);
        if (spaceStage <= plot.builtSpaceStage) return;
        plot.builtSpaceStage = spaceStage;
        setDirty();
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
            int built = Math.max(0, entry.getInt("BuiltSpaceStage"));
            data.plots.put(binding, new Plot(index, built));
            data.nextIndex = Math.max(data.nextIndex, index + 1);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(TAG_NEXT_INDEX, nextIndex);

        ListTag list = new ListTag();
        for (Map.Entry<String, Plot> entry : plots.entrySet()) {
            CompoundTag plot = new CompoundTag();
            plot.putString("Binding", entry.getKey());
            plot.putInt("Index", entry.getValue().index);
            plot.putInt("BuiltSpaceStage", entry.getValue().builtSpaceStage);
            list.add(plot);
        }
        tag.put(TAG_PLOTS, list);
        return tag;
    }
}

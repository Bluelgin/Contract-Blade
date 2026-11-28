package com.maidweapon.port.storage;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * A data-preserving 1.21.1 SavedData factory/serializer proof for the actual
 * historic world key. NOT registered or used by the probe boot path.
 */
public final class PortMasterySavedData extends SavedData {
    public static final String WORLD_KEY = "maid_weapon_contract_mastery";
    public static final int SCHEMA_V1 = 1;

    private final CompoundTag players;
    private final CompoundTag sources;
    private CompoundTag unsupported;

    public PortMasterySavedData() {
        this(new CompoundTag(), new CompoundTag());
    }

    private PortMasterySavedData(CompoundTag players, CompoundTag sources) {
        this.players = players;
        this.sources = sources;
    }

    public static PortMasterySavedData load(CompoundTag root, HolderLookup.Provider registries) {
        PortMasterySavedData result = new PortMasterySavedData(
            root.getCompound("Players").copy(), root.getCompound("LegacySources").copy());
        if (root.getInt("SchemaVersion") > SCHEMA_V1) result.unsupported = root.copy();
        return result;
    }

    public static PortMasterySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PortMasterySavedData::new, PortMasterySavedData::load, null),
            WORLD_KEY);
    }

    public boolean writable(UUID owner) {
        return unsupported == null && owner != null
            && players.getCompound(owner.toString()).getInt("SchemaVersion") <= SCHEMA_V1;
    }

    public CompoundTag readDetached(UUID owner) {
        if (!writable(owner)) throw new IllegalStateException("Unrecognized world mastery schema");
        return players.getCompound(owner.toString()).copy();
    }

    public void replaceOwned(UUID owner, CompoundTag data) {
        if (!writable(owner) || data == null || data.getInt("SchemaVersion") > SCHEMA_V1)
            throw new IllegalStateException("Cannot overwrite unsupported mastery");
        players.put(owner.toString(), data.copy());
        setDirty();
    }

    public boolean hasSource(String source) {
        return sources.contains(source);
    }

    @Override
    public CompoundTag save(CompoundTag target, HolderLookup.Provider registries) {
        if (unsupported != null) return unsupported.copy();
        target.putInt("SchemaVersion", SCHEMA_V1);
        target.put("Players", players.copy());
        target.put("LegacySources", sources.copy());
        return target;
    }
}

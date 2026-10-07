package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Player cells persist independently of transient Boss sessions. Never stores a second weapon copy. */
public final class FoxChallengeSavedData extends SavedData {
    private final Map<UUID, Integer> cells = new HashMap<>();
    private int nextIndex;

    public static FoxChallengeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FoxChallengeSavedData::load,
                FoxChallengeSavedData::new, "maid_weapon_fox_challenges");
    }

    public int cell(UUID player) {
        return cells.computeIfAbsent(player, ignored -> { setDirty(); return nextIndex++; });
    }

    private static FoxChallengeSavedData load(CompoundTag tag) {
        var data = new FoxChallengeSavedData();
        data.nextIndex = tag.getInt("NextIndex");
        for (var entry : tag.getList("Cells", 10)) {
            var cell = (CompoundTag) entry;
            data.cells.put(cell.getUUID("Player"), cell.getInt("Index"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextIndex", nextIndex);
        var list = new ListTag();
        cells.forEach((player, index) -> {
            var cell = new CompoundTag();
            cell.putUUID("Player", player);
            cell.putInt("Index", index);
            list.add(cell);
        });
        tag.put("Cells", list);
        return tag;
    }
}

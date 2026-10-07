package com.maidweapon.forge.system.deployment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Recovery journal survives logout and orderly server restart; weapon format is unchanged. */
public final class ContractCarrierLossJournal extends SavedData {
    final Map<UUID, LinkedHashMap<String, ContractCarrierLossService.Task>> pending = new HashMap<>();

    public static ContractCarrierLossJournal get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ContractCarrierLossJournal::load, ContractCarrierLossJournal::new, "maid_weapon_carrier_losses");
    }

    public static ContractCarrierLossJournal load(CompoundTag root) {
        var journal = new ContractCarrierLossJournal();
        for (Tag value : root.getList("Tasks", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            var candidate = new ContractCarrierLossService.Candidate(entry.getString("Maid"),
                    entry.getString("Binding"), ItemStack.of(entry.getCompound("Carrier")), entry.getLong("Detected"));
            var task = new ContractCarrierLossService.Task(candidate, entry.getBoolean("DurabilityConfirmed"));
            task.nextAttempt = entry.getLong("Retry");
            journal.pending.computeIfAbsent(entry.getUUID("Player"), ignored -> new LinkedHashMap<>())
                    .put(candidate.bindingId(), task);
        }
        return journal;
    }

    @Override public CompoundTag save(CompoundTag root) {
        ListTag tasks = new ListTag();
        pending.forEach((owner, queue) -> queue.values().forEach(task -> {
            var candidate = task.candidate;
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", owner);
            entry.putString("Maid", candidate.maidId());
            entry.putString("Binding", candidate.bindingId());
            entry.put("Carrier", candidate.snapshot().save(new CompoundTag()));
            entry.putLong("Detected", candidate.detectedAt());
            entry.putLong("Retry", task.nextAttempt);
            entry.putBoolean("DurabilityConfirmed", task.durabilityConfirmed);
            tasks.add(entry);
        }));
        root.put("Tasks", tasks);
        return root;
    }
}

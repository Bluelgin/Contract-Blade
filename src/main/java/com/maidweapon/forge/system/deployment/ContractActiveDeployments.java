package com.maidweapon.forge.system.deployment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runtime references only: each owner's contracts are independent, never entity snapshots. */
public final class ContractActiveDeployments {
    public record Deployment(String maidId, String bindingId) { }
    private static final Map<UUID, Map<String, Deployment>> ACTIVE = new LinkedHashMap<>();
    public static void remember(UUID owner, String maid, String binding) {
        ACTIVE.computeIfAbsent(owner, ignored -> new LinkedHashMap<>()).put(binding, new Deployment(maid, binding));
    }
    public static Deployment get(UUID owner, String binding) {
        var entries = ACTIVE.get(owner);
        return entries == null ? null : entries.get(binding);
    }
    public static List<Deployment> snapshot(UUID owner) {
        var entries = ACTIVE.get(owner);
        return entries == null ? List.of() : List.copyOf(entries.values());
    }
    public static int count(UUID owner) {
        var entries = ACTIVE.get(owner);
        return entries == null ? 0 : entries.size();
    }
    public static void forgetMaid(UUID owner, String maid) {
        var entries = ACTIVE.get(owner);
        if (entries == null) return;
        entries.values().removeIf(entry -> entry.maidId().equals(maid));
        if (entries.isEmpty()) ACTIVE.remove(owner);
    }
    public static void clear(UUID owner) { ACTIVE.remove(owner); }
    public static void clear() { ACTIVE.clear(); }
    private ContractActiveDeployments() { }
}

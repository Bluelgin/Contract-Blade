package com.maidweapon.forge.api;

import com.maidweapon.forge.compat.CompatDiagnostics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/** Additional carrier authorization rules; never a replacement for owner checks. */
public final class ContractAuthorization {
    private static final Map<String, BiPredicate<Player, ItemStack>> RULES = new LinkedHashMap<>();

    /** Register once during mod setup, before contract actions are accepted. */
    public static synchronized void register(String id, BiPredicate<Player, ItemStack> rule) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rule, "rule");
        if (id.isBlank() || RULES.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate or empty contract authorization rule: " + id);
        }
        RULES.put(id, rule);
    }

    public static synchronized boolean allows(Player owner, ItemStack carrier) {
        if (owner == null || carrier == null || carrier.isEmpty()) return false;
        for (var entry : RULES.entrySet()) {
            try {
                if (!entry.getValue().test(owner, carrier)) return false;
            } catch (RuntimeException failure) {
                CompatDiagnostics.warnOnce("contract-rule:" + entry.getKey(), failure);
                return false; // A broken authorization rule must not grant access.
            }
        }
        return true;
    }

    private ContractAuthorization() {}
}

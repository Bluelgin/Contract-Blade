package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.api.ability.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Black Fox vocabulary and immutable execution profile. Executors never inspect a mastery level. */
public final class BlackFoxSwordCoreGrowth {
    public static final ResourceLocation ABILITY = id("sealed_sword_core");
    public static final ResourceLocation SUMMON = id("summon_core"), MARK = id("mark_priority"), PROTECT = id("protect_owner"),
            CHAIN = id("chain_attack"), COMBO = id("combo_mode"), FOLLOW = id("owner_follow"), MULTI = id("multi_hunter"),
            FOCUS = id("focus_target"), RETALIATE = id("retaliate_owner"), SYNC = id("domain_sync"),
            DRIVE = id("phantom_blade"), VERTICAL = id("phantom_blade_vertical"), WAVE = id("wave_edge");
    public static final Set<ResourceLocation> FEATURES = Set.of(SUMMON, MARK, PROTECT, CHAIN, COMBO, FOLLOW, MULTI,
            FOCUS, RETALIATE, SYNC, DRIVE, VERTICAL, WAVE);
    public static final Map<ResourceLocation, ContractAbilityRegistry.Parameter> PARAMETERS = Map.ofEntries(
            bound("barrage_radius", 12, 24), bound("combo_radius", 4, 10), bound("max_targets", 1, 8),
            bound("max_hunters", 1, 3), bound("projectile_count", 1, 4), bound("barrage_interval", 12, 40),
            bound("chain_count", 1, 3), bound("chain_cooldown", 20, 100), bound("mark_duration", 20, 200),
            bound("retaliate_window", 20, 100), bound("retaliate_cooldown", 12, 100), bound("sync_duration", 12, 40));
    public static ContractAbilityDefinition fallback() {
        return new ContractAbilityDefinition(ABILITY, List.of(
                milestone(1, Set.of(SUMMON), Map.of("barrage_radius",16d,"combo_radius",8d,"max_targets",8d,
                        "max_hunters",1d,"projectile_count",4d,"barrage_interval",12d)),
                milestone(2, Set.of(MARK), Map.of("mark_duration",80d)),
                milestone(3, Set.of(PROTECT), Map.of()),
                milestone(4, Set.of(CHAIN, DRIVE), Map.of("chain_count",2d,"chain_cooldown",40d)),
                milestone(5, Set.of(COMBO), Map.of()),
                milestone(6, Set.of(FOLLOW), Map.of()),
                milestone(7, Set.of(MULTI, VERTICAL), Map.of("max_hunters",3d)),
                milestone(8, Set.of(FOCUS, WAVE), Map.of()),
                milestone(9, Set.of(RETALIATE), Map.of("retaliate_window",60d,"retaliate_cooldown",24d)),
                milestone(10, Set.of(SYNC), Map.of("sync_duration",24d))));
    }
    private static ContractAbilityMilestone milestone(int level, Set<ResourceLocation> features, Map<String, Double> params) {
        var mapped = new HashMap<ResourceLocation, Double>(); params.forEach((key,value) -> mapped.put(id(key),value));
        return new ContractAbilityMilestone(level, features, mapped);
    }
    private static Map.Entry<ResourceLocation, ContractAbilityRegistry.Parameter> bound(String name, double min, double max) {
        return Map.entry(id(name),new ContractAbilityRegistry.Parameter(min,max));
    }
    public enum AttackMode { BARRAGE, COMBO_B }
    public enum PositionMode { ANCHORED, OWNER_FOLLOW }
    public record Profile(ContractAbilityFeatureSet capabilities) {
        public Profile { Objects.requireNonNull(capabilities); }
        public boolean has(ResourceLocation feature) { return capabilities.has(feature); }
        public double number(String key, double fallback) {
            var bounds = PARAMETERS.get(id(key));
            double value = capabilities.parameter(id(key),fallback);
            return Double.isFinite(value) ? Math.max(bounds.minimum(),Math.min(bounds.maximum(),value)) : fallback;
        }
        public int count(String key, int fallback) { return (int)number(key,fallback); }
        public double barrageRadius() { return number("barrage_radius",16); }
        public double comboRadius() { return Math.min(number("combo_radius",8), barrageRadius()-1); }
        public int hunters() { return has(COMBO) ? has(MULTI) ? count("max_hunters",3) : 1 : 0; }
        public PositionMode positionMode() { return has(FOLLOW) ? PositionMode.OWNER_FOLLOW : PositionMode.ANCHORED; }
        public double radius(AttackMode mode) { return mode == AttackMode.COMBO_B ? comboRadius() : barrageRadius(); }
    }
    public static Profile ready(ContractAbilityProgressionService.Result result) {
        return result != null && result.status() == ContractAbilityProgressionService.Status.READY
                && result.capabilities().has(SUMMON) ? new Profile(result.capabilities()) : null;
    }
    public static ResourceLocation id(String path) { return ResourceLocation.parse("maid_weapon:"+path); }
    private BlackFoxSwordCoreGrowth() { }
}

package com.maidweapon.forge.system.mastery;

import com.maidweapon.common.ContractRulesConfig;
import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.data.ContractMasteryData;
import com.maidweapon.forge.api.ability.*;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContractMasteryTest {
    @BeforeAll static void config() {
        ContractRulesConfig.SPEC.setConfig(CommentedConfig.inMemory());
        MaidWeaponConfig.SPEC.setConfig(CommentedConfig.inMemory());
    }
    private static ContractMasteryData level(int value) { var data = new ContractMasteryData(); data.setLevel(value); return data; }
    private static CompoundTag old(UUID owner, String binding, int level) {
        var tag = new CompoundTag(); tag.putUUID("OwnerUUID", owner); tag.putString("MaidUUID", UUID.randomUUID().toString());
        tag.putString("MaidBindingId", binding); var data = new CompoundTag(); data.putInt("Level", level);
        data.putInt("Favorability", 278); data.putInt("ContractResonance", 141); tag.put("MaidData", data); return tag;
    }
    @Test void joinIsMonotonicCommutativeIdempotentAndNeverSumsBosses() {
        var a = level(4); a.setEnderDragonKills(2); a.setWitherKills(3); a.setTotalKills(42);
        a.setUpgradeProgress(new int[]{0,0,0,0,0,0,2});
        var b = level(7); b.setEnderDragonKills(3); b.setWitherKills(1); b.setTotalKills(80);
        b.setUpgradeProgress(new int[]{0,0,0,0,0,0,1});
        var ab = a.copy(); ab.mergeLegacy(b); var ba = b.copy(); ba.mergeLegacy(a);
        assertEquals(ContractMasteryCodec.write(ab), ContractMasteryCodec.write(ba));
        var once = ContractMasteryCodec.write(ab); ab.mergeLegacy(b); ab.mergeLegacy(a);
        assertEquals(once, ContractMasteryCodec.write(ab)); assertEquals(7, ab.getLevel());
        assertEquals(3, ab.getEnderDragonKills()); assertEquals(3, ab.getWitherKills()); assertEquals(80, ab.getTotalKills());
        assertEquals(2, ab.getUpgradeProgress()[6]);
    }
    @Test void worldRoundtripLateDiscoveryReceiptsAndPlayersAreIndependent() {
        var world = new ContractMasterySavedData(); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(world.importSource(a, "sword-a", level(5)));
        world = ContractMasterySavedData.load(world.save(new CompoundTag()));
        assertEquals(5, world.read(a).getLevel()); assertEquals(1, world.read(b).getLevel());
        assertTrue(world.importSource(a, "boxed-lv10", level(10)));
        assertFalse(world.importSource(a, "sword-a", level(10))); assertEquals(10, world.read(a).getLevel());
        world.write(a, level(2)); assertFalse(world.importSource(a, "boxed-lv10", level(10)));
        assertEquals(2, world.read(a).getLevel(), "Admin override cannot be undone by a consumed old source");
        var view = world.read(a); view.setLevel(10); assertEquals(2, world.read(a).getLevel());
    }
    @Test void unknownFutureSchemaIsPreservedReadOnly() {
        var raw = new CompoundTag(); raw.putInt("SchemaVersion", 42); raw.putString("FuturePayload", "preserved");
        var storage = ContractMasterySavedData.load(raw); var owner = UUID.randomUUID();
        assertFalse(storage.writable(owner)); assertEquals(raw, storage.save(new CompoundTag()));
        assertThrows(IllegalStateException.class, () -> storage.write(owner, level(10)));
        assertEquals(raw, storage.save(new CompoundTag()));
        var channel = old(owner, "", 7); String source = ContractMasteryMigration.source(channel);
        channel.putString("MaidBindingId", UUID.randomUUID().toString());
        assertEquals(source, ContractMasteryMigration.source(channel), "Assigning the first binding cannot invent a migration source");
    }
    @Test void invalidOldRecordsFailWithoutTouchingRelations() {
        var tag = old(UUID.randomUUID(), UUID.randomUUID().toString(), 7); var snapshot = tag.copy();
        assertTrue(ContractMasteryMigration.validLegacy(tag));
        var highCount = tag.copy(); highCount.getCompound("MaidData").putInt("TotalKills", 2000000);
        assertTrue(ContractMasteryMigration.validLegacy(highCount));
        assertEquals(2000000, ContractMasteryCodec.read(highCount.getCompound("MaidData")).getTotalKills());
        ContractMasteryCodec.read(tag.getCompound("MaidData")); assertEquals(snapshot, tag);
        tag.getCompound("MaidData").putInt("Level", 11); assertFalse(ContractMasteryMigration.validLegacy(tag));
        tag.getCompound("MaidData").putInt("Level", 7); tag.putString("OwnerUUID", "broken"); assertFalse(ContractMasteryMigration.validLegacy(tag));
        tag = snapshot.copy(); tag.getCompound("MaidData").putInt("TotalKills", -1); assertFalse(ContractMasteryMigration.validLegacy(tag));
        tag = snapshot.copy(); tag.getCompound("MaidData").putInt("SchemaVersion", 42); assertFalse(ContractMasteryMigration.validLegacy(tag));
        tag = snapshot.copy(); tag.getCompound("MaidData").putBoolean("ClientSuppliedGrowth", true); assertFalse(ContractMasteryMigration.validLegacy(tag));
    }
    @Test void legacyTierEntitiesAndCapKeepConfiguredSemantics() {
        var data = level(1); for (int tier = 1; tier <= 4; tier++) assertTrue(data.tryUpgrade(tier, false, false));
        assertEquals(5, data.getLevel()); assertFalse(data.tryUpgrade(5, false, true)); assertTrue(data.tryUpgrade(5, true, false));
        assertTrue(data.tryUpgrade(5, false, true));
        for (int i = 0; i < 2; i++) { data.tryUpgrade(5, true, false); data.tryUpgrade(5, false, true); }
        assertTrue(data.getLevel() >= 8);
        var rule = ContractRulesConfig.LEVELS[6]; var mode = rule.mode().get(); var ids = rule.entities().get(); int kills = rule.kills().get(), tier = rule.tier().get();
        try {
            rule.mode().set("entities"); rule.entities().set(List.of("minecraft:wither", "minecraft:warden")); rule.kills().set(2);
            data = level(5); assertFalse(data.tryUpgrade(5, true, false)); assertFalse(data.tryUpgrade(4, false, false, "minecraft:warden"));
            data = ContractMasteryCodec.read(ContractMasteryCodec.write(data)); assertEquals(1, data.getNextUpgradeProgress());
            assertTrue(data.tryUpgrade(5, false, true)); assertEquals(6, data.getLevel());
            rule.mode().set("tier"); rule.tier().set(5); data = level(5);
            assertFalse(data.tryUpgrade(4, false, false)); assertFalse(data.tryUpgrade(5, false, true)); assertTrue(data.tryUpgrade(5, true, false));
            data = level(10); assertFalse(data.tryUpgrade(5, true, true)); assertEquals(10, data.getLevel());
        } finally { rule.mode().set(mode); rule.entities().set(ids); rule.kills().set(kills); rule.tier().set(tier); }
    }
    @Test void boundedCopiesAndDamageUseActualPlayerProgressModel() {
        var data = level(10); data.setTotalKills(Integer.MAX_VALUE); data.setWitherKills(-1);
        assertEquals(Integer.MAX_VALUE, data.getTotalKills()); data.addKill(); assertEquals(Integer.MAX_VALUE, data.getTotalKills()); assertEquals(0, data.getWitherKills());
        var copy = data.copy(); copy.setLevel(1); assertEquals(10, data.getLevel());
        var rule = ContractRulesConfig.LEVELS[10]; double old = rule.damage().get();
        try { rule.damage().set(7.5); assertEquals(7.5f, data.getAttackDamageBonus()); } finally { rule.damage().set(old); }
    }
    @Test void abilityDefinitionsAreValidatedAndReloadedWithNoOptionalModLink() throws Exception {
        ResourceLocation ability = new ResourceLocation("fixture:example_ability"), basic = new ResourceLocation("fixture:basic"), ultimate = new ResourceLocation("fixture:ultimate");
        var fallback = new ContractAbilityDefinition(ability, List.of(new ContractAbilityMilestone(1, Set.of(basic), Map.of())));
        ContractAbilityRegistry.register(ability, (player, carrier) -> false, Set.of(basic, ultimate), Map.of(), fallback);
        var json = JsonParser.parseString("{\"schema\":1,\"ability\":\"fixture:example_ability\",\"level_source\":\"player_mastery\",\"milestones\":[{\"level\":1,\"features\":[\"fixture:basic\"]},{\"level\":10,\"features\":[\"fixture:ultimate\"]}]}");
        var route = ContractAbilityJson.parse(json); ContractAbilityRegistry.replaceDatapackDefinitions(Map.of(ability, route));
        assertFalse(route.resolve(9).has(ultimate)); assertTrue(route.resolve(10).has(ultimate));
        ContractAbilityRegistry.replaceDatapackDefinitions(Map.of()); assertEquals(fallback, ContractAbilityRegistry.definition(ability));
        assertEquals(ContractAbilityProgressionService.Status.UNREGISTERED,
                ContractAbilityProgressionService.query(null, null, new ResourceLocation("absent:skill")).status());
        assertEquals(ContractAbilityProgressionService.Status.DENIED, ContractAbilityProgressionService.query(null, null, ability).status());
        assertThrows(IllegalArgumentException.class, () -> new ContractAbilityMilestone(11, Set.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ContractAbilityRegistry.validate(ability, ContractAbilityRegistry.registration(ability),
                new ContractAbilityDefinition(ability, List.of(new ContractAbilityMilestone(5, Set.of(new ResourceLocation("absent:code")), Map.of())))));
        json.getAsJsonObject().getAsJsonArray("milestones").get(0).getAsJsonObject().addProperty("level", 1.5);
        assertThrows(ArithmeticException.class, () -> ContractAbilityJson.parse(json));
        json.getAsJsonObject().getAsJsonArray("milestones").get(0).getAsJsonObject().addProperty("level", 1);
        var apply = ContractAbilityReloadListener.class.getDeclaredMethod("apply", Map.class,
                net.minecraft.server.packs.resources.ResourceManager.class, net.minecraft.util.profiling.ProfilerFiller.class);
        apply.setAccessible(true);
        var listener = new ContractAbilityReloadListener();
        apply.invoke(listener, Map.of(ability, json), null, null);
        assertTrue(ContractAbilityRegistry.definition(ability).resolve(10).has(ultimate));
        json.getAsJsonObject().getAsJsonArray("milestones").get(0).getAsJsonObject().addProperty("level", 12);
        apply.invoke(listener, Map.of(ability, json), null, null);
        assertEquals(fallback, ContractAbilityRegistry.definition(ability), "Invalid reload safely reverts to fallback");
    }
}

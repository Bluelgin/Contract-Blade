package com.maidweapon.forge.compat.tlm;

import com.maidweapon.common.ContractRulesConfig;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Runs only as part of the isolated, explicitly enabled companion fixture. */
final class ContractRulesValidation {
    static void run() {
        var rule = ContractRulesConfig.LEVELS[6];
        var oldMode = rule.mode().get();
        var oldIds = rule.entities().get();
        int oldKills = rule.kills().get(), oldMaximum = ContractRulesConfig.POOL_MAX_RESONANCE.get();
        double oldDamage = rule.damage().get();
        try {
            check(!ContractRulesConfig.AUTO_MANIFEST.get(), "hurt auto-manifest defaults off");
            var data = new MaidWeaponData();
            var mastery = new com.maidweapon.common.data.ContractMasteryData();
            mastery.setLevel(5);
            mastery.setEnderDragonKills(2);
            mastery.setWitherKills(1);
            data.setResonance(120);
            ContractRulesConfig.POOL_MAX_RESONANCE.set(400);
            var stack = new ItemStack(Items.IRON_SWORD);
            ContractCarrierData.setMaidData(stack, data);
            data = ContractCarrierData.getMaidData(stack);
            check(data.getResonance() == 120 && MaidWeaponData.maximumResonance() == 400,
                    "raising maximum preserves current resonance");
            data.addResonance(1000);
            check(data.getResonance() == 400, "recovery uses configured ceiling");
            stack.getOrCreateTag().getCompound("MaidData").remove("ContractResonance");
            check(ContractCarrierData.getMaidData(stack).getResonance() == 200,
                    "missing legacy field stays at 200 rather than refilling");
            ContractRulesConfig.POOL_MAX_RESONANCE.set(80);
            check(data.getResonance() == 80, "lowered maximum bounds existing data");
            rule.mode().set("entities");
            rule.entities().set(java.util.List.of("minecraft:wither", "minecraft:warden"));
            rule.kills().set(2);
            check(!mastery.tryUpgrade(5, true, false, "minecraft:ender_dragon"), "non-listed boss cannot upgrade");
            check(!mastery.tryUpgrade(4, false, false, "minecraft:warden"), "first any-of kill saves progress");
            mastery = com.maidweapon.forge.system.mastery.ContractMasteryCodec.read(
                    com.maidweapon.forge.system.mastery.ContractMasteryCodec.write(mastery));
            check(mastery.getUpgradeProgress()[6] == 1 && mastery.getLevel() == 5
                    && mastery.getEnderDragonKills() == 2 && mastery.getWitherKills() == 1,
                    "new progress round-trips without changing old progression");
            check(mastery.tryUpgrade(5, false, true, "minecraft:wither") && mastery.getLevel() == 6,
                    "second matching entity upgrades exactly once");
            rule.damage().set(7.5);
            check(mastery.getAttackDamageBonus() == 7.5f, "per-level damage override applies");
            mastery.setLevel(5);
            rule.mode().set("tier");
            check(!mastery.tryUpgrade(4, false, false, "minecraft:warden"), "tier rule rejects weaker target");
            rule.mode().set("legacy");
            check(!mastery.tryUpgrade(5, false, true, "minecraft:wither")
                    && mastery.tryUpgrade(5, true, false, "minecraft:ender_dragon"),
                    "legacy dragon requirement remains unchanged");
            com.mojang.logging.LogUtils.getLogger().info("[MaidWeapon] CONTRACT_RULES_VALIDATION_PASS");
        } finally {
            rule.mode().set(oldMode);
            rule.entities().set(oldIds);
            rule.kills().set(oldKills);
            rule.damage().set(oldDamage);
            ContractRulesConfig.POOL_MAX_RESONANCE.set(oldMaximum);
        }
    }
    private static void check(boolean result, String label) {
        if (!result) throw new IllegalStateException(label);
        com.mojang.logging.LogUtils.getLogger().info("[MaidWeapon] Rules fixture: {}", label);
    }
    private ContractRulesValidation() { }
}

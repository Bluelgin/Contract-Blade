package com.maidweapon.forge.client.tooltip;

import static org.junit.jupiter.api.Assertions.*;
import com.maidweapon.forge.system.mastery.ContractTooltipSnapshot;
import com.maidweapon.forge.system.akatsuki.AkatsukiDomainGrowth;
import com.maidweapon.forge.system.fox.BlackFoxSwordCoreGrowth;
import com.maidweapon.forge.system.fox.WhiteFoxBlessingGrowth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class FoxTooltipSectionTest {
    private static List<String> keys(List<Component> lines) {
        return lines.stream().map(line -> ((TranslatableContents) line.getContents()).getKey()).toList();
    }

    @Test void collapsedSectionsNeverLeakStatsOrLockedAbilities() {
        var routes = ContractTooltipSnapshot.project(10, Map.of(
                "black", BlackFoxSwordCoreGrowth.fallback(), "white", WhiteFoxBlessingGrowth.fallback(),
                "red", AkatsukiDomainGrowth.fallback()));
        List<Component> lines = new ArrayList<>();
        BlackFoxTooltipSection.append(lines, routes.getCompound("black"), false);
        WhiteFoxTooltipSection.append(lines, routes.getCompound("white"), false);
        AkatsukiTooltipSection.append(lines, routes.getCompound("red"), false);
        assertTrue(lines.isEmpty());
    }

    @Test void blackFoxShowsOnlyActualUnlockedSkills() {
        List<Component> lines = new ArrayList<>();
        BlackFoxTooltipSection.append(lines, ContractTooltipSnapshot.project(1,
                Map.of("black", BlackFoxSwordCoreGrowth.fallback())).getCompound("black"), true);
        assertTrue(keys(lines).contains("maid_weapon.tooltip.black.core_story"));
        assertFalse(keys(lines).contains("maid_weapon.tooltip.black.combo_story"));
        assertFalse(keys(lines).contains("maid_weapon.tooltip.compact.black.skill"));
        lines.clear();
        BlackFoxTooltipSection.append(lines, ContractTooltipSnapshot.project(5,
                Map.of("black", BlackFoxSwordCoreGrowth.fallback())).getCompound("black"), true);
        assertTrue(keys(lines).contains("maid_weapon.tooltip.black.combo_story"));
        assertFalse(keys(lines).contains("maid_weapon.tooltip.black.multi_story"));
    }

    @Test void whiteFoxPreservesServerNumbersWithoutRepeatingLearnedLists() {
        var profile = ContractTooltipSnapshot.project(1,
                Map.of("white", WhiteFoxBlessingGrowth.fallback())).getCompound("white");
        profile.getCompound("values").putDouble("guard_reduction", .123);
        List<Component> lines = new ArrayList<>();
        WhiteFoxTooltipSection.append(lines, profile, true);
        var keys = keys(lines);
        assertFalse(keys.contains("maid_weapon.tooltip.white.learned"));
        assertFalse(keys.contains("maid_weapon.tooltip.compact.white.oath"));
        var guard = (TranslatableContents) lines.get(keys.indexOf("maid_weapon.tooltip.compact.white.guard")).getContents();
        assertEquals(ContractTooltipFormat.percent(.123), guard.getArgs()[0]);
    }

    @Test void redFoxDoesNotAdvertiseLockedSoulCoreOrRescue() {
        List<Component> lines = new ArrayList<>();
        AkatsukiTooltipSection.append(lines, ContractTooltipSnapshot.project(1,
                Map.of("red", AkatsukiDomainGrowth.fallback())).getCompound("red"), true);
        assertFalse(keys(lines).contains("maid_weapon.tooltip.red.anchor_spawn"));
        assertFalse(keys(lines).contains("maid_weapon.tooltip.red.rescue"));
        assertFalse(keys(lines).contains("maid_weapon.tooltip.red.resonance"));
        assertTrue(keys(lines).contains("maid_weapon.tooltip.fox.red.soul_basic"));
        lines.clear();
        var max = ContractTooltipSnapshot.project(10,
                Map.of("red", AkatsukiDomainGrowth.fallback())).getCompound("red");
        AkatsukiTooltipSection.append(lines, max, true);
        assertTrue(keys(lines).contains("maid_weapon.tooltip.red.anchor_spawn"));
        assertTrue(keys(lines).contains("maid_weapon.tooltip.red.rescue"));
        assertTrue(keys(lines).contains("maid_weapon.tooltip.fox.red.complete"));
        FoxTooltipProgress.append(lines, max);
        assertFalse(keys(lines).contains("maid_weapon.tooltip.fox.next"));
    }
}

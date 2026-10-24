package com.maidweapon.forge.client.tooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimpleTooltipPresentationTest {
    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }

    @Test void collapsedShowsOnlyLoreAndHintWithoutBuildingDetails() {
        List<Component> lines = new ArrayList<>();
        AtomicBoolean evaluated = new AtomicBoolean();
        SimpleTooltipPresentation.append(lines, false, "example.lore", result -> evaluated.set(true));
        assertFalse(evaluated.get());
        assertEquals(List.of("example.lore", "maid_weapon.tooltip.simple.shift"), lines.stream().map(SimpleTooltipPresentationTest::key).toList());
    }

    @Test void expandedReplacesLoreAndHintWithDetails() {
        List<Component> lines = new ArrayList<>();
        SimpleTooltipPresentation.append(lines, true, "example.lore", result -> {
            SimpleTooltipPresentation.line(result, "example.effect");
            SimpleTooltipPresentation.line(result, "example.use", 3);
        });
        assertEquals(List.of("example.effect", "example.use"), lines.stream().map(SimpleTooltipPresentationTest::key).toList());
        assertArrayEquals(new Object[]{3}, ((TranslatableContents) lines.get(1).getContents()).getArgs());
    }

    @Test void partnerStateSurvivesBothPagesAndReleaseRestoresCollapsedPage() {
        for (boolean expanded : new boolean[]{false, true, false}) {
            Component state = Component.literal("partner: stored");
            List<Component> lines = new ArrayList<>(List.of(state));
            SimpleTooltipPresentation.append(lines, expanded, "example.lore", result -> SimpleTooltipPresentation.line(result, "example.use"));
            assertSame(state, lines.get(0));
            assertEquals(expanded ? "example.use" : "example.lore", key(lines.get(1)));
        }
    }
}

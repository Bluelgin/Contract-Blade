package com.maidweapon.forge.client.voice;

import static org.junit.jupiter.api.Assertions.*;
import com.maidweapon.forge.client.voice.InventoryVoiceGesturePolicy.Response;
import org.junit.jupiter.api.Test;

class InventoryVoiceGesturePolicyTest {
    private static final InventoryVoiceGesturePolicy.Chances ALL =
            new InventoryVoiceGesturePolicy.Chances(1, 1, 1);

    @Test
    void realThreeStageGestureFollowsEmotionNotItemEvents() {
        var p = new InventoryVoiceGesturePolicy();
        assertEquals(Response.NONE, p.observe(null, 1_000, .1, ALL));
        assertEquals(Response.GREETING, p.observe("a", 1_010, .1, ALL));
        assertEquals(Response.NONE, p.observe("a", 1_011, .1, ALL));
        assertEquals(Response.NONE, p.observe(null, 1_100, .1, ALL));
        assertEquals(Response.CURIOUS, p.observe("a", 1_500, .1, ALL));
        p.observe(null, 1_700, .1, ALL);
        assertEquals(Response.PLAYFUL, p.observe("a", 1_800, .1, ALL));
        p.observe(null, 1_900, .1, ALL);
        assertEquals(Response.NONE, p.observe("a", 2_000, .1, ALL));
        p.observe(null, 2_100, .1, ALL);
        assertEquals(Response.GREETING, p.observe("a", 8_500, .1, ALL));
    }

    @Test
    void randomizedSilenceDoesNotForgetRapidReturnStreak() {
        var p = new InventoryVoiceGesturePolicy();
        var defaults = InventoryVoiceGesturePolicy.DEFAULT_CHANCES;
        assertEquals(Response.NONE, p.observe("a", 1_000, .7, defaults));
        p.observe(null, 1_100, .7, defaults);
        assertEquals(Response.CURIOUS, p.observe("a", 1_200, .7, defaults));
        p.observe(null, 1_300, .7, defaults);
        assertEquals(Response.NONE, p.observe("a", 1_400, .7, defaults));
        p.observe(null, 1_500, .7, defaults);
        assertEquals(Response.NONE, p.observe("a", 1_600, .1, defaults)); // playful cooldown/streak
    }

    @Test
    void swappingDifferentBladesAndReopenNeverMintsReactions() {
        var p = new InventoryVoiceGesturePolicy();
        assertEquals(Response.GREETING, p.observe("a", 100, 0, ALL));
        assertEquals(Response.NONE, p.observe("b", 101, 0, ALL));
        assertEquals(Response.NONE, p.observe("b", 101, 0, ALL));
        p.observe(null, 102, 0, ALL);
        assertEquals(Response.CURIOUS, p.observe("b", 103, 0, ALL));
        p.resetGesture();
        assertEquals(Response.NONE, p.observe("a", 104, 0, ALL));
    }

    @Test
    void separateCooldownsAndDifferentBindingsCannotShareAStreak() {
        var p = new InventoryVoiceGesturePolicy();
        assertEquals(Response.GREETING, p.observe("a", 1_000, 0, ALL));
        p.observe(null, 1_100, 0, ALL);
        assertEquals(Response.NONE, p.observe("b", 1_200, 0, ALL)); // greeting cooldown
        p.observe(null, 1_300, 0, ALL);
        assertEquals(Response.CURIOUS, p.observe("b", 1_350, 0, ALL));
        p.resetGesture();
        assertEquals(Response.NONE, p.observe("b", 1_400, 0, ALL)); // no rapid return
        p.resetSession();
        p.observe(null, 1_410, 0, ALL);
        assertEquals(Response.GREETING, p.observe("b", 1_420, 0, ALL));
    }

    @Test
    void probabilitiesRejectInvalidRollsAndCanDisableEachEmotion() {
        var p = new InventoryVoiceGesturePolicy();
        var zero = new InventoryVoiceGesturePolicy.Chances(0, 0, 0);
        assertEquals(Response.NONE, p.observe("a", 100, 0, zero));
        p.observe(null, 101, 0, zero);
        assertEquals(Response.NONE, p.observe("a", 102, 0, zero));
        p.observe(null, 103, 0, zero);
        assertEquals(Response.NONE, p.observe("a", 104, 0, zero));
        assertEquals(Response.NONE, p.observe("a", 105, Double.NaN, ALL));
        assertThrows(IllegalArgumentException.class,
                () -> new InventoryVoiceGesturePolicy.Chances(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new InventoryVoiceGesturePolicy.Chances(0, -1, 0));
    }
}

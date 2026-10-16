package com.maidweapon.forge.client.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InventoryVoiceGesturePolicyTest {
    @Test
    void firstPickupThenRealReturnTriggersTeasingDespiteNormalCooldown() {
        var p = new InventoryVoiceGesturePolicy();
        assertEquals(InventoryVoiceGesturePolicy.Response.NONE, p.observe(null, 1_000));
        assertEquals(InventoryVoiceGesturePolicy.Response.PICKUP, p.observe("blade-a", 1_010));
        assertEquals(InventoryVoiceGesturePolicy.Response.NONE, p.observe("blade-a", 1_011));
        assertEquals(InventoryVoiceGesturePolicy.Response.NONE, p.observe(null, 1_100));
        assertEquals(InventoryVoiceGesturePolicy.Response.TEASE, p.observe("blade-a", 1_500));
        assertEquals(InventoryVoiceGesturePolicy.Response.NONE, p.observe(null, 1_650));
        assertEquals(InventoryVoiceGesturePolicy.Response.NONE, p.observe("blade-a", 1_800));
        assertEquals(InventoryVoiceGesturePolicy.Response.NONE, p.observe(null, 1_900));
        assertEquals(InventoryVoiceGesturePolicy.Response.PICKUP, p.observe("blade-a", 8_000));
    }

    @Test
    void otherWeaponAndStaleReturnCannotStealPreviousGesture() {
        var p = new InventoryVoiceGesturePolicy();
        assertEquals(InventoryVoiceGesturePolicy.Response.PICKUP, p.observe("a", 5));
        p.observe(null, 10);
        assertEquals(InventoryVoiceGesturePolicy.Response.NONE, p.observe("b", 100));
        p.observe(null, 200);
        assertEquals(InventoryVoiceGesturePolicy.Response.TEASE, p.observe("b", 300));
        p.observe(null, 400);
        assertEquals(InventoryVoiceGesturePolicy.Response.NONE, p.observe("a", 450));
        p.resetGesture();
        assertEquals(InventoryVoiceGesturePolicy.Response.NONE, p.observe("a", 460));
        p.resetSession();
        p.observe(null, 470);
        assertEquals(InventoryVoiceGesturePolicy.Response.PICKUP, p.observe("a", 480));
    }

    @Test
    void noFakePickupOnSwapOrRepeatedClickAndNoOverlap() {
        var p = new InventoryVoiceGesturePolicy();
        assertEquals(InventoryVoiceGesturePolicy.Response.PICKUP, p.observe("one", 10));
        assertEquals(InventoryVoiceGesturePolicy.Response.NONE, p.observe("two", 15));
        assertEquals(InventoryVoiceGesturePolicy.Response.NONE, p.observe("two", 16));
        p.observe(null, 20);
        assertEquals(InventoryVoiceGesturePolicy.Response.TEASE, p.observe("two", 30));
        p.observe(null, 40);
        assertEquals(InventoryVoiceGesturePolicy.Response.NONE, p.observe("two", 50));
        p.resetGesture();
        assertEquals(InventoryVoiceGesturePolicy.Response.NONE, p.observe("two", 60));
    }
}

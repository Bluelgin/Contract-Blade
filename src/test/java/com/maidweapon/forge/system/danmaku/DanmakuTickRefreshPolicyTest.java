package com.maidweapon.forge.system.danmaku;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DanmakuTickRefreshPolicyTest {
    @Test void idleRefreshIsBounded() {
        assertFalse(DanmakuTickRefreshPolicy.shouldValidate(false, false, 105, 100));
        assertFalse(DanmakuTickRefreshPolicy.shouldValidate(false, false, 109, 100));
        assertTrue(DanmakuTickRefreshPolicy.shouldValidate(false, false, 110, 100));
    }
    @Test void activeSessionsAndChangedEquipmentRefreshImmediately() {
        assertTrue(DanmakuTickRefreshPolicy.shouldValidate(true, false, 101, 100));
        assertTrue(DanmakuTickRefreshPolicy.shouldValidate(false, true, 101, 100));
    }
    @Test void rewoundPlayerTickForcesRefresh() {
        assertTrue(DanmakuTickRefreshPolicy.shouldValidate(false, false, 0, Long.MIN_VALUE));
        assertTrue(DanmakuTickRefreshPolicy.shouldValidate(false, false, 1, 250));
        assertFalse(DanmakuTickRefreshPolicy.shouldValidate(false, false, 1, 1));
    }
}

package com.maidweapon.forge.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DanmakuPressGestureTest {
    @Test void subTickTapCastsExactlyOnce() {
        var g=new DanmakuPressGesture();
        assertEquals(DanmakuPressGesture.Action.CAST,g.sample(false,true));
        assertEquals(DanmakuPressGesture.Action.NONE,g.sample(false,false));
    }
    @Test void holdOpensAndScreenResetDropsRelease() {
        var g=new DanmakuPressGesture();g.sample(true,true);
        for(int i=0;i<5;i++) assertEquals(DanmakuPressGesture.Action.NONE,g.sample(true,false));
        assertEquals(DanmakuPressGesture.Action.OPEN_WHEEL,g.sample(true,false));
        g.reset(); assertEquals(DanmakuPressGesture.Action.NONE,g.sample(false,false));
    }
    @Test void normalPressReleaseCastsOnceAndResetDropsStaleState() {
        var g=new DanmakuPressGesture();g.sample(true,true);
        assertEquals(DanmakuPressGesture.Action.CAST,g.sample(false,false));
        assertEquals(DanmakuPressGesture.Action.NONE,g.sample(false,false));
        g.sample(true,true);g.reset();
        assertEquals(DanmakuPressGesture.Action.NONE,g.sample(false,false));
    }
}

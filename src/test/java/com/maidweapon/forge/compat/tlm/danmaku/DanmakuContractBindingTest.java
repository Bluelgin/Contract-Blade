package com.maidweapon.forge.compat.tlm.danmaku;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DanmakuContractBindingTest {
    @Test void bindingIsNotTransferable() {
        assertTrue(DanmakuContractBinding.accepts("maid-A", "maid-A"));
        assertFalse(DanmakuContractBinding.accepts("maid-A", "maid-B"));
        assertFalse(DanmakuContractBinding.accepts(null, "maid-A"));
        assertFalse(DanmakuContractBinding.accepts("maid-A", null));
        assertFalse(DanmakuContractBinding.accepts("", ""));
    }
}

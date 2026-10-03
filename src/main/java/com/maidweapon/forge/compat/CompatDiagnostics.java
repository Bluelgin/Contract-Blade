package com.maidweapon.forge.compat;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** One warning per capability per process; failures remain visible without tick-log spam. */
public final class CompatDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    public static void warnOnce(String capability, Throwable failure) {
        if (REPORTED.add(capability)) {
            LOGGER.warn("[Contract Blade] Capability '{}' failed; its fallback is being used", capability, failure);
        }
    }

    private CompatDiagnostics() {}
}

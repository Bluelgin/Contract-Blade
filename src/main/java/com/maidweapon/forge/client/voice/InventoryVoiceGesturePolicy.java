package com.maidweapon.forge.client.voice;

import java.util.Objects;

/**
 * Pure client-side, transient inventory gesture state machine.
 *
 * Only transitions of the cursor's actual carried ItemStack are observations;
 * clicking a slot repeatedly without putting the weapon down is not a pickup.
 * Distinct binding IDs must never share a rapid-return streak.
 */
public final class InventoryVoiceGesturePolicy {
    public enum Response { NONE, PICKUP, TEASE }

    private static final long RAPID_RETURN_MS = 1_800;
    private static final long PICKUP_GAP_MS = 6_000;
    private static final long TEASE_GAP_MS = 25_000;

    private String carriedBinding;
    private String mostRecentlyPlaced;
    private long placedAtMs = -1;
    private long nextPickupAtMs;
    private long nextTeaseAtMs;

    public Response observe(String currentBinding, long nowMs) {
        if (nowMs < 0) return Response.NONE;
        if (Objects.equals(carriedBinding, currentBinding)) return Response.NONE;
        if (carriedBinding != null) {
            // It was genuinely placed back into a slot, not merely clicked again.
            String placed = carriedBinding;
            carriedBinding = currentBinding;
            if (currentBinding != null) {
                // Swaps never count as a new lift or start a rapid-return streak.
                mostRecentlyPlaced = null;
                placedAtMs = -1;
                return Response.NONE;
            }
            mostRecentlyPlaced = placed;
            placedAtMs = nowMs;
            return Response.NONE;
        }
        if (currentBinding == null) return Response.NONE;
        carriedBinding = currentBinding;
        boolean rapidReturn = currentBinding.equals(mostRecentlyPlaced)
                && placedAtMs >= 0 && nowMs >= placedAtMs
                && nowMs - placedAtMs <= RAPID_RETURN_MS;
        mostRecentlyPlaced = null;
        placedAtMs = -1;
        if (rapidReturn) {
            if (nowMs < nextTeaseAtMs) return Response.NONE;
            nextTeaseAtMs = nowMs + TEASE_GAP_MS;
            nextPickupAtMs = Math.max(nextPickupAtMs, nowMs + PICKUP_GAP_MS);
            return Response.TEASE;
        }
        if (nowMs < nextPickupAtMs) return Response.NONE;
        nextPickupAtMs = nowMs + PICKUP_GAP_MS;
        return Response.PICKUP;
    }

    /** Screen close, dimension/client switch and login are not inventory gestures. */
    public void resetGesture() {
        carriedBinding = null;
        mostRecentlyPlaced = null;
        placedAtMs = -1;
    }

    /** New game/server: reset the cooldowns along with the transient gesture. */
    public void resetSession() {
        resetGesture();
        nextPickupAtMs = 0;
        nextTeaseAtMs = 0;
    }
}

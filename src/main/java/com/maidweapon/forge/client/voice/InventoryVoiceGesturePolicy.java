package com.maidweapon.forge.client.voice;

import java.util.Objects;

/**
 * Pure, transient inventory-gesture and personality state machine.
 *
 * This class never reads NBT or plays audio. A response is a possible emotion,
 * not a claim that a PECO recording says a particular Japanese sentence.
 */
public final class InventoryVoiceGesturePolicy {
    public enum Response { NONE, GREETING, CURIOUS, PLAYFUL }

    public record Chances(double greeting, double curious, double playful) {
        public Chances {
            if (!valid(greeting) || !valid(curious) || !valid(playful))
                throw new IllegalArgumentException("probabilities must be finite [0,1]");
        }
        private static boolean valid(double value) {
            return Double.isFinite(value) && value >= 0 && value <= 1;
        }
    }

    public static final Chances DEFAULT_CHANCES = new Chances(.40, .80, .35);
    private static final long RAPID_RETURN_MS = 1_800;
    private static final long GREETING_GAP_MS = 5_000;
    private static final long CURIOUS_GAP_MS = 8_000;
    private static final long PLAYFUL_GAP_MS = 30_000;

    private String carriedBinding;
    private String recentlyPlacedBinding;
    private long recentlyPlacedAt = -1;
    private int consecutiveLifts;
    private long nextGreetingAt;
    private long nextCuriousAt;
    private long nextPlayfulAt;

    /**
     * @param roll stable [0,1) roll supplied by the client for testability
     * @return emotional intent, or NONE if no actual eligible pickup / random silent reaction
     */
    public Response observe(String currentBinding, long nowMs, double roll, Chances chances) {
        if (nowMs < 0 || !Double.isFinite(roll) || roll < 0 || roll >= 1 || chances == null)
            return Response.NONE;
        if (Objects.equals(carriedBinding, currentBinding)) return Response.NONE;
        if (carriedBinding != null) {
            String previouslyHeld = carriedBinding;
            carriedBinding = currentBinding;
            if (currentBinding != null) {
                // Swapping stacks directly is not a fresh pickup.
                resetSequence();
                return Response.NONE;
            }
            recentlyPlacedBinding = previouslyHeld;
            recentlyPlacedAt = nowMs;
            return Response.NONE;
        }
        if (currentBinding == null) return Response.NONE;
        carriedBinding = currentBinding;
        boolean repeated = currentBinding.equals(recentlyPlacedBinding)
                && recentlyPlacedAt >= 0 && nowMs >= recentlyPlacedAt
                && nowMs - recentlyPlacedAt <= RAPID_RETURN_MS;
        consecutiveLifts = repeated ? Math.min(4, consecutiveLifts + 1) : 1;
        recentlyPlacedBinding = null;
        recentlyPlacedAt = -1;

        if (consecutiveLifts >= 3) {
            // Rare playful reaction; no false "injury" sound.
            if (nowMs >= nextPlayfulAt && roll < chances.playful()) {
                nextPlayfulAt = nowMs + PLAYFUL_GAP_MS;
                return Response.PLAYFUL;
            }
            // Do not turn rapid spamming into guaranteed voices.
            return Response.NONE;
        }
        if (consecutiveLifts == 2) {
            if (nowMs >= nextCuriousAt && roll < chances.curious()) {
                nextCuriousAt = nowMs + CURIOUS_GAP_MS;
                return Response.CURIOUS;
            }
            return Response.NONE;
        }
        if (nowMs >= nextGreetingAt && roll < chances.greeting()) {
            nextGreetingAt = nowMs + GREETING_GAP_MS;
            return Response.GREETING;
        }
        return Response.NONE;
    }

    private void resetSequence() {
        recentlyPlacedBinding = null;
        recentlyPlacedAt = -1;
        consecutiveLifts = 0;
    }

    /** A closed GUI or server hop is never a deliberate return to the same sword. */
    public void resetGesture() {
        carriedBinding = null;
        resetSequence();
    }

    public void resetSession() {
        resetGesture();
        nextGreetingAt = 0;
        nextCuriousAt = 0;
        nextPlayfulAt = 0;
    }
}

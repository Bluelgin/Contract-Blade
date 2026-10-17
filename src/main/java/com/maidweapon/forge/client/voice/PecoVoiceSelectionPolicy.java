package com.maidweapon.forge.client.voice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Deterministic acoustic-only shortlist. No language/semantic claims.
 * Selects clean, non-duplicate and conversational-length PCM utterances.
 * A clip's current-list index is only used until the TLM SoundCache changes.
 */
public final class PecoVoiceSelectionPolicy {
    public enum Mood { GREETING, CURIOUS, PLAYFUL }

    public record Sample(int index, long fingerprint, double durationSeconds,
                         double rootMeanSquare, double quietFraction) {}

    public static List<Integer> select(Mood mood, List<Sample> samples) {
        if (mood == null || samples == null || samples.isEmpty()) return List.of();
        final double ideal = switch (mood) {
            case GREETING -> 1.65;
            case CURIOUS -> 1.30;
            case PLAYFUL -> .95;
        };
        final double minimum = switch (mood) {
            case GREETING -> 1.50; // Longer conversational voice, not sharp exclamations.
            case CURIOUS -> .45;
            case PLAYFUL -> .28;
        };
        final double maximum = switch (mood) {
            case GREETING -> 4.0;
            case CURIOUS -> 3.5;
            case PLAYFUL -> 1.45; // Short idle reactions, not battle calls.
        };
        final int targetCount = switch (mood) {
            case GREETING -> 8;
            case CURIOUS -> 6;
            case PLAYFUL -> 5;
        };
        var ranked = new ArrayList<Sample>();
        var unique = new HashSet<Long>();
        for (var sample : samples) {
            if (sample == null || sample.index() < 0
                    || !Double.isFinite(sample.durationSeconds())
                    || !Double.isFinite(sample.rootMeanSquare())
                    || !Double.isFinite(sample.quietFraction())
                    || sample.durationSeconds() < minimum
                    || sample.durationSeconds() > maximum
                    || sample.rootMeanSquare() < .012
                    || sample.quietFraction() > .58
                    || sample.quietFraction() < 0
                    || sample.quietFraction() > 1
                    || !unique.add(sample.fingerprint())) continue;
            ranked.add(sample);
        }
        ranked.sort(Comparator.comparingDouble(
                    (Sample s) -> Math.abs(s.durationSeconds() - ideal)
                        + Math.max(0, s.quietFraction() - .25) * 2
                        + Math.max(0, .06 - s.rootMeanSquare()) * 2)
                .thenComparingLong(Sample::fingerprint));
        return ranked.stream().limit(targetCount).map(Sample::index).toList();
    }

    private PecoVoiceSelectionPolicy() {}
}

package com.maidweapon.forge.client.voice;

import static org.junit.jupiter.api.Assertions.*;
import com.maidweapon.forge.client.voice.PecoVoiceSelectionPolicy.Mood;
import com.maidweapon.forge.client.voice.PecoVoiceSelectionPolicy.Sample;
import java.util.List;
import org.junit.jupiter.api.Test;

class PecoVoiceSelectionPolicyTest {
    @Test void technicalScreenRejectsNoiseSilenceLongSpeechAndDuplicates() {
        var samples = List.of(
            new Sample(0, 1, 1.65, .09, .1),
            new Sample(1, 1, 1.65, .09, .1),
            new Sample(2, 2, 8.0, .09, .1),
            new Sample(3, 3, 1.65, .001, .1),
            new Sample(4, 4, 1.65, .09, .85),
            new Sample(5, 5, 2.0, .08, .12));
        assertEquals(List.of(0, 5), PecoVoiceSelectionPolicy.select(Mood.GREETING, samples));
    }

    @Test void differentMoodsHaveDifferentDurationWindows() {
        var samples = List.of(new Sample(0, 1, .95, .09, .1),
            new Sample(1, 2, 1.85, .09, .1),
            new Sample(2, 3, 2.95, .09, .1));
        assertEquals(List.of(1, 2), PecoVoiceSelectionPolicy.select(Mood.GREETING, samples));
        assertEquals(List.of(0), PecoVoiceSelectionPolicy.select(Mood.PLAYFUL, samples));
        assertEquals(List.of(0, 1, 2), PecoVoiceSelectionPolicy.select(Mood.CURIOUS, samples));
    }

    @Test void sourceOrderDoesNotChangeSelectedFingerprints() {
        var first = List.of(new Sample(0, 101, 1.7, .05, .1),
            new Sample(1, 202, 1.8, .05, .1));
        var reversed = List.of(new Sample(5, 202, 1.8, .05, .1),
            new Sample(6, 101, 1.7, .05, .1));
        assertEquals(List.of(0, 1), PecoVoiceSelectionPolicy.select(Mood.GREETING, first));
        assertEquals(List.of(6, 5), PecoVoiceSelectionPolicy.select(Mood.GREETING, reversed));
    }

    @Test void invalidMetadataNeverBecomesEligible() {
        assertTrue(PecoVoiceSelectionPolicy.select(Mood.CURIOUS,
             List.of(new Sample(0, 1, Double.NaN, .1, .1),
                     new Sample(1, 2, 1, Double.NaN, .1))).isEmpty());
        assertTrue(PecoVoiceSelectionPolicy.select(null, List.of()).isEmpty());
    }
}

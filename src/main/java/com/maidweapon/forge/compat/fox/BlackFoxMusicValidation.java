package com.maidweapon.forge.compat.fox;

import com.maidweapon.common.BlackFoxMusicEnvelope;
import com.maidweapon.forge.client.BlackFoxMusicSound;
import com.maidweapon.forge.client.BlackFoxMusicStream;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.audio.OggAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;

/** Opt-in resource decoding and gain checks; no encounter or save is opened. */
final class BlackFoxMusicValidation {
    static void run() throws Exception {
        var envelope = new BlackFoxMusicEnvelope();
        envelope.tick(true, false);
        require(envelope.gain() < .001, "silent soft entry");
        for (int i = 1; i < 30; i++) envelope.tick(true, false);
        require(Math.abs(envelope.gain() - .5f) < .001, "three-second entry midpoint");
        for (int i = 30; i < 60; i++) envelope.tick(true, false);
        require(envelope.gain() == 1, "intro gain");
        envelope.parry();
        for (int i = 0; i < 8; i++) envelope.tick(true, false);
        require(Math.abs(envelope.gain() - .45f) < .001, "parry duck");
        for (int i = 0; i < 20; i++) envelope.tick(true, true);
        require(Math.abs(envelope.gain() - .55f) < .001, "final stand gain");
        for (int i = 0; i < 10; i++) envelope.tick(false, false);
        require(envelope.gain() == 0 && !envelope.ended(), "reversible mute");
        for (int i = 0; i < 60; i++) envelope.tick(true, false);
        envelope.end();
        for (int i = 0; i < 41; i++) envelope.tick(true, false);
        require(envelope.ended(), "exit fade");
        var resources = Minecraft.getInstance().getResourceManager();
        var intro = new OggAudioStream(resources.open(BlackFoxMusicSound.INTRO));
        var loop = new LoopingAudioStream(OggAudioStream::new, resources.open(BlackFoxMusicSound.LOOP));
        long decoded = 0;
        // Cross the short lead-in (~0.24s) and two complete author-loop boundaries (~91.19s each).
        try (var stream = new BlackFoxMusicStream(intro, loop, BlackFoxMusicSound.INTRO_FRAMES)) {
            require(stream.getFormat().getSampleRate() == 48000 && stream.getFormat().getChannels() == 2, "format");
            int entryBytes = BlackFoxMusicSound.INTRO_FRAMES * 4;
            var boundary = stream.read(entryBytes + 4096);
            require(boundary.remaining() >= entryBytes + 4096, "short lead-in join buffer");
            try (var reference = new OggAudioStream(resources.open(BlackFoxMusicSound.LOOP))) {
                var start = reference.read(4096);
                for (int i = 0; i < 4096; i++)
                    require(boundary.get(entryBytes + i) == start.get(i), "exact loop entry without padded lead-in");
            }
            decoded += boundary.remaining();
            long minimum = (long) (190 * 48000 * 4);
            while (decoded < minimum) {
                var chunk = stream.read(65536);
                require(chunk.hasRemaining(), "premature stream end");
                decoded += chunk.remaining();
            }
        }
        var definition = Minecraft.getInstance().getSoundManager().getSoundEvent(BlackFoxMusicSound.EVENT);
        require(definition != null && definition.getSound(net.minecraft.util.RandomSource.create()).shouldStream(), "streaming event");
        LogUtils.getLogger().info("BLACK_FOX_MUSIC_PASS: {} decoded PCM bytes across intro and loop boundaries; duck, mute, final stand and fade", decoded);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("Black Fox music: " + message);
    }
    private BlackFoxMusicValidation() { }
}

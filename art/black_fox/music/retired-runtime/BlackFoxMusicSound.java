package com.maidweapon.forge.client;

import com.maidweapon.common.BlackFoxClientConfig;
import com.maidweapon.common.BlackFoxMusicEnvelope;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Local, non-positional MUSIC channel; all gain decisions stay in the encounter owner. */
public final class BlackFoxMusicSound extends AbstractTickableSoundInstance {
    public static final ResourceLocation EVENT = ResourceLocation.parse("maid_weapon:black_fox_music");
    public static final ResourceLocation INTRO = ResourceLocation.parse("maid_weapon:sounds/black_fox/epicbattle_j_intro.ogg");
    public static final ResourceLocation LOOP = ResourceLocation.parse("maid_weapon:sounds/black_fox/epicbattle_j_loop.ogg");
    public static final int INTRO_FRAMES = 11556; // Generated manifest: 0.24075 seconds at 48 kHz.
    private final BlackFoxMusicEnvelope envelope;
    private final boolean firstPassage;
    public BlackFoxMusicSound(BlackFoxMusicEnvelope envelope, boolean firstPassage) {
        super(SoundEvent.createVariableRangeEvent(EVENT), SoundSource.MUSIC, RandomSource.create());
        this.envelope = envelope;
        this.firstPassage = firstPassage;
        relative = true; attenuation = Attenuation.NONE; looping = true; delay = 0; volume = 0;
    }
    @Override public boolean canStartSilent() { return true; }
    @Override public void tick() {
        volume = envelope.gain() * BlackFoxClientConfig.BOSS_MUSIC_VOLUME.get().floatValue();
        if (envelope.ended()) stop();
    }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        if (!firstPassage) return buffers.getStream(LOOP, true);
        return buffers.getStream(INTRO, false).thenCompose(intro -> buffers.getStream(LOOP, true)
                .handle((loop, failure) -> {
                    if (failure != null) {
                        try { intro.close(); } catch (java.io.IOException close) { failure.addSuppressed(close); }
                        throw new CompletionException(failure);
                    }
                    try { return (AudioStream) new BlackFoxMusicStream(intro, loop, INTRO_FRAMES); }
                    catch (java.io.IOException error) { throw new CompletionException(error); }
                }));
    }
}

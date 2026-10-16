package com.maidweapon.forge.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.client.sound.CustomSoundLoader;
import com.github.tartaricacid.touhoulittlemaid.client.sound.data.SoundCache;
import com.github.tartaricacid.touhoulittlemaid.client.sound.data.SoundData;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.maidweapon.common.ContractVoiceClientConfig;
import com.maidweapon.forge.client.voice.InventoryVoiceGesturePolicy;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Optional PECO PCM bridge: no audio files or synthetic EntityMaid.
 * TLM native events map to mood families, not literal semantic dialogue.
 */
public final class TlmPecoVoiceBridge {
    private static final String PACK = "littlemaid_peco";
    private static final long MIN_VOICE_SPACING_MS = 2_400;
    private static InventoryVoiceSound active;
    private static long nextVoiceAt;

    public static void play(InventoryVoiceGesturePolicy.Response response) {
        if (response == InventoryVoiceGesturePolicy.Response.NONE) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long now = Util.getMillis();
        // Respect the previous voice's turn. Never cut her off or queue future spam.
        if (now < nextVoiceAt || active != null && mc.getSoundManager().isActive(active))
            return;

        SoundEvent event = eventFor(response);
        int preferred = preferredVariant(response);
        SoundCache cache = CustomSoundLoader.getSoundCache(PACK);
        List<SoundData> options = cache == null ? null : cache.buffers().get(event.getLocation());
        if (options == null || options.isEmpty()) return;
        if (preferred >= options.size()) return; // No wrong-category fallback.
        int selected = preferred >= 0
                ? preferred : ThreadLocalRandom.current().nextInt(options.size());
        active = new InventoryVoiceSound(event,
                (float) (double) ContractVoiceClientConfig.VOLUME.get(), options.get(selected));
        mc.getSoundManager().play(active);
        nextVoiceAt = now + MIN_VOICE_SPACING_MS;
    }

    private static SoundEvent eventFor(InventoryVoiceGesturePolicy.Response emotion) {
        return switch (emotion) {
            case GREETING -> InitSounds.MAID_IDLE.get();
            case CURIOUS -> InitSounds.MAID_TAMED.get();
            case PLAYFUL -> InitSounds.MAID_FIND_TARGET.get();
            case NONE -> throw new IllegalArgumentException("NONE is not a playable emotion");
        };
    }

    private static int preferredVariant(InventoryVoiceGesturePolicy.Response emotion) {
        return switch (emotion) {
            case GREETING -> ContractVoiceClientConfig.GREETING_VARIANT.get();
            case CURIOUS -> ContractVoiceClientConfig.CURIOUS_VARIANT.get();
            case PLAYFUL -> ContractVoiceClientConfig.PLAYFUL_VARIANT.get();
            case NONE -> -1;
        };
    }

    public static void stop() {
        if (active != null) Minecraft.getInstance().getSoundManager().stop(active);
        active = null;
        nextVoiceAt = 0;
    }

    @SubscribeEvent
    public static void onSource(PlaySoundSourceEvent event) {
        if (!(event.getSound() instanceof InventoryVoiceSound voice)) return;
        SoundBuffer buffer = voice.buffer();
        event.getChannel().attachStaticBuffer(buffer);
        event.getChannel().play();
    }

    private static final class InventoryVoiceSound extends AbstractTickableSoundInstance {
        private final SoundData sample;

        InventoryVoiceSound(SoundEvent event, float volume, SoundData sample) {
            super(event, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.sample = sample;
            this.volume = volume;
            this.relative = true;
        }

        @Override
        public void tick() {
            if (Minecraft.getInstance().player == null) stop();
        }

        SoundBuffer buffer() {
            return new SoundBuffer(sample.byteBuffer(), sample.audioFormat());
        }
    }

    private TlmPecoVoiceBridge() {}
}

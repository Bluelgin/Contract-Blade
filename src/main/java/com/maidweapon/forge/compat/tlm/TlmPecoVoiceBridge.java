package com.maidweapon.forge.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.client.sound.CustomSoundLoader;
import com.github.tartaricacid.touhoulittlemaid.client.sound.data.SoundCache;
import com.github.tartaricacid.touhoulittlemaid.client.sound.data.SoundData;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.maidweapon.common.ContractVoiceClientConfig;
import com.maidweapon.forge.client.voice.InventoryVoiceGesturePolicy;
import com.maidweapon.forge.client.voice.PecoVoiceSelectionPolicy;
import com.maidweapon.forge.client.voice.PecoVoiceSelectionPolicy.Mood;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.CRC32;

/**
 * TLM PECO audio bridge, exclusively inside the optional compat boundary.
 * Does not bundle or alter OGG, and does not manufacture a maid entity.
 *
 * 'Acoustic shortlist' is technical quality screening, NOT interpretation
 * of Japanese speech or an assertion about an actor's intended dialogue.
 */
public final class TlmPecoVoiceBridge {
    private static final String PACK = "littlemaid_peco";
    private static final long MIN_VOICE_SPACING_MS = 2_400;
    private static InventoryVoiceSound active;
    private static long nextVoiceAt;
    private static SoundCache cachedPack;
    private static Map<Mood, List<Integer>> shortlist = Map.of();
    private static SoundData lastSample;

    public static void play(InventoryVoiceGesturePolicy.Response response) {
        if (response != InventoryVoiceGesturePolicy.Response.NONE)
            playSelected(response, -1, false);
    }

    public static boolean preview(String name, int index) {
        var mood = moodFromName(name);
        return mood != null && playSelected(responseFor(mood), index, true);
    }

    public static int sampleCount(String name) {
        var mood = moodFromName(name);
        if (mood == null) return 0;
        SoundCache cache = CustomSoundLoader.getSoundCache(PACK);
        var samples = cache == null ? null : cache.buffers().get(eventFor(mood).getLocation());
        return samples == null ? 0 : samples.size();
    }

    /** The preview command uses current TLM cache indices, not OGG filenames. */
    public static List<Integer> curatedIndices(String name) {
        var mood = moodFromName(name);
        if (mood == null) return List.of();
        return currentShortlist().getOrDefault(mood, List.of());
    }

    private static boolean playSelected(
            InventoryVoiceGesturePolicy.Response response, int requestedIndex, boolean audition) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        long now = Util.getMillis();
        if (!audition && (now < nextVoiceAt
                || active != null && mc.getSoundManager().isActive(active))) return false;

        Mood mood = switch (response) {
            case GREETING -> Mood.GREETING;
            case CURIOUS -> Mood.CURIOUS;
            case PLAYFUL -> Mood.PLAYFUL;
            case NONE -> null;
        };
        if (mood == null) return false;

        SoundCache cache = CustomSoundLoader.getSoundCache(PACK);
        List<SoundData> samples = cache == null ? null : cache.buffers().get(eventFor(mood).getLocation());
        if (samples == null || samples.isEmpty()) return false;
        int requested = audition ? requestedIndex : preferredVariant(response);
        if (requested < -1 || requested >= samples.size()) return false;

        int selected;
        if (requested >= 0) {
            selected = requested; // Deliberate audition and hand-curated choice overrides policy.
        } else if (!audition && ContractVoiceClientConfig.AUTO_SHORTLIST.get()) {
            List<Integer> choices = currentShortlist().getOrDefault(mood, List.of());
            if (choices.isEmpty()) return false; // No unverified, bad-quality fallback.
            selected = chooseWithoutImmediateRepeat(samples, choices);
        } else {
            selected = ThreadLocalRandom.current().nextInt(samples.size());
        }
        if (audition && active != null) mc.getSoundManager().stop(active);
        SoundData sample = samples.get(selected);
        lastSample = sample;
        active = new InventoryVoiceSound(eventFor(mood),
                (float) (double) ContractVoiceClientConfig.VOLUME.get(), sample);
        mc.getSoundManager().play(active);
        nextVoiceAt = now + MIN_VOICE_SPACING_MS;
        return true;
    }

    private static int chooseWithoutImmediateRepeat(List<SoundData> samples, List<Integer> choices) {
        if (choices.size() == 1) return choices.get(0);
        var other = new ArrayList<Integer>(choices);
        other.removeIf(idx -> samples.get(idx) == lastSample);
        if (other.isEmpty()) other.addAll(choices);
        return other.get(ThreadLocalRandom.current().nextInt(other.size()));
    }

    private static Map<Mood, List<Integer>> currentShortlist() {
        SoundCache cache = CustomSoundLoader.getSoundCache(PACK);
        if (cache == null) {
            cachedPack = null;
            shortlist = Map.of();
            return shortlist;
        }
        if (cache == cachedPack) return shortlist;
        var result = new EnumMap<Mood, List<Integer>>(Mood.class);
        for (Mood mood : Mood.values()) {
            var clips = cache.buffers().get(eventFor(mood).getLocation());
            var summary = new ArrayList<PecoVoiceSelectionPolicy.Sample>();
            if (clips != null) {
                for (int i = 0; i < clips.size(); i++) {
                    var sample = summarize(clips.get(i), i);
                    if (sample != null) summary.add(sample);
                }
            }
            result.put(mood, PecoVoiceSelectionPolicy.select(mood, summary));
        }
        shortlist = Map.copyOf(result);
        cachedPack = cache;
        return shortlist;
    }

    /**
     * TLM OggReader decodes each OGG into PCM + AudioFormat. Compute stable
     * content fingerprint, seconds and approximate signal activity from a
     * duplicate ByteBuffer, never consuming the sound actually played.
     */
    private static PecoVoiceSelectionPolicy.Sample summarize(SoundData data, int index) {
        if (data == null || data.byteBuffer() == null || data.audioFormat() == null) return null;
        AudioFormat format = data.audioFormat();
        if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
                || format.getSampleSizeInBits() != 16
                || format.getFrameSize() <= 0 || format.getFrameRate() <= 0
                || !Float.isFinite(format.getFrameRate())) return null;
        ByteBuffer bytes = data.byteBuffer().asReadOnlyBuffer();
        bytes.rewind();
        int length = bytes.remaining();
        double duration = length / (format.getFrameRate() * format.getFrameSize());
        if (!Double.isFinite(duration) || duration <= 0) return null;
        CRC32 crc = new CRC32();
        crc.update(bytes.duplicate());
        long fingerprint = (crc.getValue() << 32) | Integer.toUnsignedLong(length);
        bytes.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        final int sampleCount = Math.min(2_000, length / 2);
        if (sampleCount == 0) return null;
        double power = 0;
        int quiet = 0;
        for (int i = 0; i < sampleCount; i++) {
            int offset = (int) (((long) i * (length / 2)) / sampleCount) * 2;
            double value = bytes.getShort(offset) / 32768.0;
            power += value * value;
            if (Math.abs(value) < .012) quiet++;
        }
        return new PecoVoiceSelectionPolicy.Sample(index, fingerprint, duration,
                Math.sqrt(power / sampleCount), (double) quiet / sampleCount);
    }

    private static Mood moodFromName(String name) {
        return switch (name) {
            case "greeting" -> Mood.GREETING;
            case "curious" -> Mood.CURIOUS;
            case "playful" -> Mood.PLAYFUL;
            default -> null;
        };
    }

    private static InventoryVoiceGesturePolicy.Response responseFor(Mood mood) {
        return switch (mood) {
            case GREETING -> InventoryVoiceGesturePolicy.Response.GREETING;
            case CURIOUS -> InventoryVoiceGesturePolicy.Response.CURIOUS;
            case PLAYFUL -> InventoryVoiceGesturePolicy.Response.PLAYFUL;
        };
    }

    private static SoundEvent eventFor(Mood mood) {
        return switch (mood) {
            case GREETING, PLAYFUL -> InitSounds.MAID_IDLE.get();
            case CURIOUS -> InitSounds.MAID_TAMED.get();
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
        lastSample = null;
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

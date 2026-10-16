package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.client.voice.InventoryVoiceGesturePolicy;

import com.github.tartaricacid.touhoulittlemaid.client.sound.CustomSoundLoader;
import com.github.tartaricacid.touhoulittlemaid.client.sound.data.SoundCache;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.maidweapon.common.ContractVoiceClientConfig;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * TLM 1.5.3 custom pack bridge. Reuses its decoded SoundCache buffers without
 * copying OGG resources into Contract-Blade or constructing a fake EntityMaid.
 *
 * The native MaidSoundInstance requires a live maid entity; an inventory weapon
 * can have its maid stored/compressed, hence our non-entity SoundInstance.
 */
public final class TlmPecoVoiceBridge {
    public static final String PECO_PACK_ID = "littlemaid_peco";
    private static InventoryVoiceSound playing;

    public static void play(InventoryVoiceGesturePolicy.Response response) {
        if (response == InventoryVoiceGesturePolicy.Response.NONE) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        SoundEvent event = response == InventoryVoiceGesturePolicy.Response.TEASE
                ? InitSounds.MAID_HURT.get() : InitSounds.MAID_ITEM_GET.get();
        // Missing packs or empty categories fail silently, never play maid/empty.
        SoundCache cache = CustomSoundLoader.getSoundCache(PECO_PACK_ID);
        if (cache == null || cache.buffers().get(event.getLocation()) == null
                || cache.buffers().get(event.getLocation()).isEmpty()) return;
        stop();
        playing = new InventoryVoiceSound(event, mc.player,
                (float) (double) ContractVoiceClientConfig.VOLUME.get());
        mc.getSoundManager().play(playing);
    }

    public static void stop() {
        if (playing != null) Minecraft.getInstance().getSoundManager().stop(playing);
        playing = null;
    }

    @SubscribeEvent
    public static void onSource(PlaySoundSourceEvent event) {
        if (!(event.getSound() instanceof InventoryVoiceSound ours)) return;
        SoundBuffer buffer = ours.getSoundBuffer();
        if (buffer != null) {
            event.getChannel().attachStaticBuffer(buffer);
            event.getChannel().play();
        }
    }

    private static final class InventoryVoiceSound extends AbstractTickableSoundInstance {
        private final LocalPlayer listener;

        InventoryVoiceSound(SoundEvent event, LocalPlayer listener, float volume) {
            super(event, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.listener = listener;
            this.volume = volume;
            this.x = listener.getX();
            this.y = listener.getY();
            this.z = listener.getZ();
            this.relative = true; // Local feedback; don't broadcast or emit world audio.
        }

        @Override
        public void tick() {
            if (Minecraft.getInstance().player != listener || listener.isRemoved()) {
                stop();
                return;
            }
            this.x = listener.getX();
            this.y = listener.getY();
            this.z = listener.getZ();
        }

        SoundBuffer getSoundBuffer() {
            SoundCache cache = CustomSoundLoader.getSoundCache(PECO_PACK_ID);
            return cache == null ? null : cache.getBuffer(location);
        }
    }

    private TlmPecoVoiceBridge() {}
}

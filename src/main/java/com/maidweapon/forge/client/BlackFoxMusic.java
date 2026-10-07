package com.maidweapon.forge.client;

import com.maidweapon.common.BlackFoxClientConfig;
import com.maidweapon.common.BlackFoxMusicEnvelope;
import com.maidweapon.forge.entity.BlackFoxBossEntity;
import com.maidweapon.forge.system.fox.challenge.BlackFoxFight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** One challenger-owned soundtrack, never a world scan or a phase-specific playlist. */
@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class BlackFoxMusic {
    private static ClientLevel source;
    private static int bossId, waiting, retry;
    private static UUID identity;
    private static boolean ending, started, previouslyEnabled;
    private static BlackFoxMusicEnvelope envelope;
    private static BlackFoxMusicSound sound;
    public static void session(int id, UUID uuid, boolean active) {
        var mc = Minecraft.getInstance();
        if (!active) {
            if (source == mc.level && id == bossId && uuid.equals(identity)) end();
            return;
        }
        if (mc.level == null || mc.player == null) return;
        if (source == mc.level && id == bossId && uuid.equals(identity)) return;
        clear(); source = mc.level; bossId = id; identity = uuid;
        envelope = new BlackFoxMusicEnvelope(); waiting = 40;
    }
    public static void contact(int id, int strength) {
        if (!ending && source == Minecraft.getInstance().level && id == bossId && strength >= 1 && strength <= 2 && envelope != null)
            envelope.parry();
    }
    private static void end() { ending = true; if (envelope != null) envelope.end(); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || source == null || mc.isPaused()) return;
        if (mc.level != source || mc.player == null) { clear(); return; }
        var entity = source.getEntity(bossId);
        boolean valid = entity instanceof BlackFoxBossEntity && identity.equals(entity.getUUID());
        if (valid) waiting = 0;
        else if (!ending && waiting-- > 0) return;
        if (!valid || !mc.player.isAlive() || entity instanceof BlackFoxBossEntity boss && boss.skill() == BlackFoxFight.Skill.DEFEATED) end();
        boolean enabled = BlackFoxClientConfig.BOSS_MUSIC.get() && BlackFoxClientConfig.BOSS_MUSIC_VOLUME.get() > 0;
        if (enabled && !previouslyEnabled && !ending) mc.getMusicManager().stopPlaying();
        previouslyEnabled = enabled;
        boolean doom = entity instanceof BlackFoxBossEntity boss && boss.skill() == BlackFoxFight.Skill.DOOM;
        envelope.tick(enabled, doom);
        if (envelope.ended()) { clear(); return; }
        if (sound == null && !ending && enabled && retry-- <= 0) {
            mc.getMusicManager().stopPlaying();
            sound = new BlackFoxMusicSound(envelope, !started); mc.getSoundManager().play(sound); started = true; retry = 40;
        } else if (sound != null && retry-- <= 0 && !mc.getSoundManager().isActive(sound)) {
            // Sound engine reload/master mute can stop channels. Retry boundedly, never each frame.
            sound = null; retry = 20;
        }
    }
    @SubscribeEvent public static void music(PlaySoundEvent event) {
        var incoming = event.getSound();
        if (source == Minecraft.getInstance().level && sound != null && !ending && incoming != null
                && incoming.getSource() == net.minecraft.sounds.SoundSource.MUSIC && !(incoming instanceof BlackFoxMusicSound)
                && BlackFoxClientConfig.BOSS_MUSIC.get() && BlackFoxClientConfig.BOSS_MUSIC_VOLUME.get() > 0)
            event.setSound(null);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    public static void clear() {
        if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
        source = null; identity = null; sound = null; envelope = null; waiting = retry = 0;
        ending = started = previouslyEnabled = false;
    }
    private BlackFoxMusic() { }
}

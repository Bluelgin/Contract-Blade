package com.maidweapon.forge.client;

import com.maidweapon.common.BlackFoxClientConfig;
import com.maidweapon.forge.entity.BlackFoxBossEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Brief view-only roll; never mutates player yaw/pitch, movement, aim or server time. */
@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class BlackFoxCameraShake {
    private static net.minecraft.client.multiplayer.ClientLevel source;
    private static double started;
    private static float power;
    private static int duration = 6;
    public static void contact(int bossId, int strength) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.level.getEntity(bossId) instanceof BlackFoxBossEntity)
                || !mc.player.isAlive() || strength < 0 || strength > 5 || BlackFoxClientConfig.PARRY_SHAKE.get() == 0
                || strength >= 4 && !BlackFoxClientConfig.BOSS_IMPACT_SHAKE.get()) return;
        double time = mc.level.getGameTime() + mc.getPartialTick();
        float incoming = switch (strength) {
            case 0 -> .35f; case 1 -> .35f; case 2 -> 1f;
            case 3 -> 1.8f; case 4 -> .85f; default -> .75f;
        };
        // No amplitude stacking; a fifth-parry break can immediately override the ordinary pulse.
        if (source == mc.level && time - started < duration
                && (incoming <= power || time - started < 2 && strength != 3)) return;
        power = incoming; duration = strength == 3 ? 10 : strength == 2 ? 8 : 6;
        source = mc.level; started = time;
    }
    public static float sample(double age, float intensity) {
        return sample(age, intensity, 6);
    }
    private static float sample(double age, float intensity, int length) {
        if (age < 0 || age >= length) return 0;
        double envelope = 1 - age / length;
        return (float) (Math.sin(age * Math.PI * .8) * envelope * envelope * intensity * 2.1);
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        var mc = Minecraft.getInstance();
        if (source == null) return;
        if (mc.level != source || mc.player == null || !mc.player.isAlive()) { clear(); return; }
        double age = mc.level.getGameTime() + event.getPartialTick() - started;
        if (age >= duration) { clear(); return; }
        event.setRoll(event.getRoll() + sample(age, power, duration) * BlackFoxClientConfig.PARRY_SHAKE.get().floatValue());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    private static void clear() { source = null; power = 0; }
    private BlackFoxCameraShake() { }
}

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
    public static void contact(int bossId, int strength) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.level.getEntity(bossId) instanceof BlackFoxBossEntity)
                || strength < 0 || strength > 2 || BlackFoxClientConfig.PARRY_SHAKE.get() == 0) return;
        double time = mc.level.getGameTime() + mc.getPartialTick();
        float incoming = strength == 2 ? 1 : strength == 1 ? .55f : .35f;
        power = Math.max(incoming, source == mc.level && time - started < 6 ? power : 0);
        source = mc.level; started = time;
    }
    public static float sample(double age, float intensity) {
        if (age < 0 || age >= 6) return 0;
        return (float) (Math.sin(age * Math.PI * .8) * (1 - age / 6) * intensity * 1.1);
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        var mc = Minecraft.getInstance();
        if (source == null) return;
        if (mc.level != source || mc.player == null) { clear(); return; }
        double age = mc.level.getGameTime() + event.getPartialTick() - started;
        if (age >= 6) { clear(); return; }
        event.setRoll(event.getRoll() + sample(age, power) * BlackFoxClientConfig.PARRY_SHAKE.get().floatValue());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    private static void clear() { source = null; power = 0; }
    private BlackFoxCameraShake() { }
}

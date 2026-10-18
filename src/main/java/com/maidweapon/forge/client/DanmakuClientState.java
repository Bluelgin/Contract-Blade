package com.maidweapon.forge.client;

import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.danmaku.DanmakuForm;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** UI-only, never authoritative. Server owns every real eligibility/cast decision. */
public final class DanmakuClientState {
    private static Player session;
    private static String binding = "";
    private static boolean enabled;
    private static int mastery;
    private static DanmakuForm selected = DanmakuForm.NEEDLE;

    private static void ensureSession() {
        var current = Minecraft.getInstance().player;
        if (current != session) {
            session = current;
            binding = "";
            enabled = false;
            mastery = 0;
            selected = DanmakuForm.NEEDLE;
        }
    }
    public static void receive(String nextBinding, boolean allowed, int level) {
        ensureSession();
        if (!binding.equals(nextBinding) && !nextBinding.isEmpty()) selected = DanmakuForm.NEEDLE;
        binding = nextBinding;
        enabled = allowed;
        mastery = level;
    }
    /** Automatic ordinary swings are unlocked at Lv.1, before active spells. */
    public static boolean autoAvailable() {
        ensureSession();
        var player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || !enabled || mastery < 1) return false;
        var held = ContractCarrierData.getBindingId(player.getMainHandItem());
        return held != null && held.equals(binding);
    }
    /** Active spell wheel remains gated at Lv.5. */
    public static boolean available() {
        return autoAvailable() && mastery >= 5;
    }
    public static int mastery() { ensureSession(); return mastery; }
    public static DanmakuForm selected() { ensureSession(); return selected; }
    public static void select(DanmakuForm form) {
        ensureSession();
        if (form != null && form.unlocked(mastery)) selected = form;
    }
    private DanmakuClientState() { }
}

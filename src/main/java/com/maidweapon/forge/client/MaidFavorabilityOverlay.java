package com.maidweapon.forge.client;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.api.EmbeddedSpiritApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MaidFavorabilityOverlay {
    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(), "contract_resonance",
                MaidFavorabilityOverlay::render);
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                               int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!MaidWeaponConfig.SHOW_RESONANCE_HUD.get()
                || minecraft.player == null
                || minecraft.options.hideGui) return;

        String oathMaid = com.maidweapon.forge.network.OathNetwork.clientMaid(minecraft.player);
        boolean burning = com.maidweapon.forge.network.OathNetwork.clientActive(minecraft.player);
        float ash = com.maidweapon.forge.network.OathNetwork.clientAsh(minecraft.player);
        ItemStack weapon = minecraft.player.getMainHandItem();
        if (!MaidInfusion.isInfused(weapon) || !ContractCarrierData.isOwner(weapon, minecraft.player)) {
            weapon = minecraft.player.getOffhandItem();
        }
        if (oathMaid == null && (!MaidInfusion.isInfused(weapon) || !ContractCarrierData.isOwner(weapon, minecraft.player))) return;

        String maid = oathMaid != null ? oathMaid : ContractCarrierData.getBoundMaidUUID(weapon);
        int current = com.maidweapon.forge.network.ResonanceNetwork.clientCurrent(minecraft.player, maid);
        int maximum = com.maidweapon.forge.network.ResonanceNetwork.clientMaximum(minecraft.player, maid);
        if (current < 0 || maximum <= 0) return;
        int width = 81;
        int filled = Math.round(width * current / (float) maximum);
        int x = screenWidth / 2 + 10;
        int y = screenHeight - 59 - MaidWeaponConfig.RESONANCE_HUD_OFFSET_Y.get();

        ResourceLocation spiritTexture = EmbeddedSpiritApi.getHudTexture(weapon);
        if (spiritTexture != null && oathMaid == null) {
            graphics.blit(spiritTexture, x - 19, y - 4,
                    0, 0, 16, 16, 16, 16);
        }

        graphics.fill(x - 1, y - 1, x + width + 1, y + 10, 0xCC10152B);
        graphics.fill(x, y, x + width, y + 9, 0xFF293051);
        if (filled > 0 && !burning && ash <= 0) {
            graphics.fill(x, y, x + filled, y + 9, 0xFF7165D8);
            graphics.fill(x, y, x + filled, y + 2, 0xFFA9E7FF);
        }
        if (burning || ash > 0) renderBurn(graphics, x, y, width, filled, current / (float) maximum,
                minecraft.player.level().getGameTime() + partialTick, burning, ash);
        graphics.drawCenteredString(minecraft.font,
                Component.translatable("maid_weapon.hud.resonance",
                        current, maximum),
                x + width / 2, y + 1, 0xFFFFFFFF);
    }

    private static void renderBurn(GuiGraphics g, int x, int y, int width, int filled, float ratio,
                                   float time, boolean burning, float ash) {
        if (!burning) {
            int alpha = Math.round(180 * ash) << 24;
            g.fill(x, y, x + Math.round(width * ash), y + 9, alpha | 0x675D57);
            for (int i = 0; i < 9; i++) {
                int px = x + i * 9 + (int) (Math.sin(i + time * .2) * 2);
                int py = y - (int) ((1 - ash) * (8 + i % 4 * 3));
                g.fill(px, py, px + 1, py + 1, alpha | 0xB4A99F);
            }
            return;
        }
        g.fillGradient(x, y, x + filled, y + 9, 0xFFFFD06B, 0xFFC52E27);
        boolean weak = ratio < .25f;
        for (int i = 0; i < filled; i += 3) {
            float wave = (float) Math.sin(time * .45 + i * 1.7);
            int h = Math.max(1, Math.round((weak ? 2 : 4) + wave * (weak ? 1 : 2)));
            if (!weak || Math.sin(time * .7 + i) > -.3)
                g.fillGradient(x + i, y - h, x + i + 2, y + 1, 0x30FF7733, 0xEEFFCE63);
        }
        for (int i = 0; i < 5 && filled > 0; i++) {
            float phase = (time * .035f + i * .23f) % 1;
            int px = x + ((i * 17 + (int) (Math.sin(time * .1 + i) * 3)) % Math.max(1, filled));
            int py = y - (int) (phase * 12);
            int a = (int) ((1 - phase) * (weak ? 100 : 210));
            g.fill(px, py, px + 1, py + 1, (a << 24) | 0xFFC56B);
        }
    }
    private MaidFavorabilityOverlay() {}
}

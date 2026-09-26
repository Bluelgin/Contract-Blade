package com.maidweapon.forge.client;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
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

        ItemStack weapon = minecraft.player.getMainHandItem();
        if (!MaidInfusion.isInfused(weapon) || !MaidWeaponItem.isOwner(weapon, minecraft.player)) {
            weapon = minecraft.player.getOffhandItem();
        }
        if (!MaidInfusion.isInfused(weapon) || !MaidWeaponItem.isOwner(weapon, minecraft.player)) return;

        MaidWeaponData data = MaidInfusion.data(weapon);
        int width = 81;
        int filled = Math.round(width * data.getResonance()
                / (float) MaidWeaponData.MAX_RESONANCE);
        int x = screenWidth / 2 + 10;
        int y = screenHeight - 59 - MaidWeaponConfig.RESONANCE_HUD_OFFSET_Y.get();

        ResourceLocation spiritTexture = EmbeddedSpiritApi.getHudTexture(weapon);
        if (spiritTexture != null) {
            graphics.blit(spiritTexture, x - 19, y - 4,
                    0, 0, 16, 16, 16, 16);
        }

        graphics.fill(x - 1, y - 1, x + width + 1, y + 10, 0xCC10152B);
        graphics.fill(x, y, x + width, y + 9, 0xFF293051);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + 9, 0xFF7165D8);
            graphics.fill(x, y, x + filled, y + 2, 0xFFA9E7FF);
        }
        graphics.drawCenteredString(minecraft.font,
                Component.translatable("maid_weapon.hud.resonance",
                        data.getResonance(), MaidWeaponData.MAX_RESONANCE),
                x + width / 2, y + 1, 0xFFFFFFFF);
    }

    private MaidFavorabilityOverlay() {}
}

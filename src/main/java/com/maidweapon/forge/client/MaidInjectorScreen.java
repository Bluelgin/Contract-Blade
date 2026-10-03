package com.maidweapon.forge.client;

import com.maidweapon.forge.menu.MaidInjectorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MaidInjectorScreen extends AbstractContainerScreen<MaidInjectorMenu> {
    private Button injectButton;

    public MaidInjectorScreen(MaidInjectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        injectButton = addRenderableWidget(Button.builder(
                Component.translatable("button.maid_weapon.inject"), button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                MaidInjectorMenu.INJECT_BUTTON);
                    }
                }).bounds(leftPos + 62, topPos + 58, 52, 20).build());
    }

    @Override
    public void containerTick() {
        super.containerTick();
        injectButton.active = menu.canInject();
        injectButton.setMessage(Component.translatable(menu.isFoxTransfer()
                ? (menu.isFoxExtracting() ? "maid_weapon.fox.button.seal" : "maid_weapon.fox.button.migrate")
                : (menu.isExtracting() ? "button.maid_weapon.extract" : "button.maid_weapon.inject")));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF4B263D);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + 73, 0xFFF4B4CC);
        graphics.fill(leftPos + 3, topPos + 76, leftPos + imageWidth - 3,
                topPos + imageHeight - 3, 0xFFD47B9F);
        drawSlot(graphics, 34, 34);
        drawSlot(graphics, 70, 34);
        drawSlot(graphics, 115, 34);
        drawSlot(graphics, 141, 34);
        graphics.fill(leftPos + 94, topPos + 41, leftPos + 108, topPos + 45, 0xFF8B3A62);
        graphics.fill(leftPos + 104, topPos + 38, leftPos + 110, topPos + 48, 0xFF8B3A62);
        for (int i = 0; i < 6; i++) {
            int px = leftPos + 12 + i * 27;
            int py = topPos + 14 + (i % 2) * 43;
            graphics.fill(px, py, px + 3, py + 3, 0xFFFFE3EC);
            graphics.fill(px + 3, py + 2, px + 6, py + 5, 0xFFE66F9F);
        }
        drawInventorySlots(graphics);
    }

    private void drawInventorySlots(GuiGraphics graphics) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) drawSlot(graphics, 7 + col * 18, 83 + row * 18);
        }
        for (int col = 0; col < 9; col++) drawSlot(graphics, 7 + col * 18, 141);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 18, topPos + y + 18, 0xFF6D3855);
        graphics.fill(leftPos + x + 1, topPos + y + 1,
                leftPos + x + 17, topPos + y + 17, 0xFF2A1D29);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 7, 0xFF55223A, false);
        graphics.drawString(font, Component.translatable("gui.maid_weapon.film_recipe"),
                8, 22, 0xFF6B2948, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}

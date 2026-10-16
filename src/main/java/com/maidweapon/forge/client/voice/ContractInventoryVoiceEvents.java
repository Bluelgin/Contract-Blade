package com.maidweapon.forge.client.voice;

import com.maidweapon.common.ContractVoiceClientConfig;
import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.compat.tlm.TlmPecoVoiceBridge;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Actual cursor transitions AFTER vanilla inventory clicks.
 *
 * No polling/stack scanning, packets or NBT mutation. Runs only for the player's
 * ordinary inventory; excludes JEI, creative cloning and shift-click transfers.
 */
@Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID, value = Dist.CLIENT)
public final class ContractInventoryVoiceEvents {
    private static final InventoryVoiceGesturePolicy POLICY = new InventoryVoiceGesturePolicy();
    private static Screen currentScreen;

    @SubscribeEvent
    public static void pressed(ScreenEvent.MouseButtonPressed.Post event) {
        observe(event.getScreen());
    }

    @SubscribeEvent
    public static void released(ScreenEvent.MouseButtonReleased.Post event) {
        observe(event.getScreen());
    }

    private static void observe(Screen screen) {
        var mc = Minecraft.getInstance();
        if (!(screen instanceof InventoryScreen inventory) || mc.player == null
                || mc.level == null || !ModList.get().isLoaded("touhou_little_maid")) return;
        if (currentScreen != screen) {
            POLICY.resetGesture();
            currentScreen = screen;
        }
        String binding = authorizedBinding(inventory.getMenu().getCarried(), mc);
        var result = POLICY.observe(binding, Util.getMillis());
        if (result != InventoryVoiceGesturePolicy.Response.NONE
                && ContractVoiceClientConfig.ENABLED.get()
                && ContractVoiceClientConfig.VOLUME.get() > 0)
            TlmPecoVoiceBridge.play(result);
    }

    private static String authorizedBinding(ItemStack stack, Minecraft mc) {
        if (stack.isEmpty() || !MaidInfusion.isInfused(stack)
                || ContractCarrierData.isContractSuperseded(stack)
                || !ContractCarrierData.isOwner(stack, mc.player)) return null;
        // Read only: never call ensureBindingId in the client inventory.
        String id = ContractCarrierData.getBindingId(stack);
        return id == null || id.isBlank() ? null : id;
    }

    @SubscribeEvent
    public static void closed(ScreenEvent.Closing event) {
        POLICY.resetGesture();
        currentScreen = null;
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        POLICY.resetSession();
        currentScreen = null;
        TlmPecoVoiceBridge.stop();
    }

    private ContractInventoryVoiceEvents() {}
}

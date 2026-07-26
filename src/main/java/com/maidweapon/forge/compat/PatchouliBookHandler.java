package com.maidweapon.forge.compat;

import com.maidweapon.common.MaidWeaponConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional Patchouli bridge. No Patchouli classes are linked at compile time. */
@Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PatchouliBookHandler {
    private static final ResourceLocation GUIDE_BOOK = new ResourceLocation("patchouli", "guide_book");
    private static final String BOOK_ID = "maid_weapon:contract_fragments";
    private static final String RECEIVED_FLAG = "MaidWeaponContractBookGiven";

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        boolean receivedByLegacyStory = event.getEntity().getPersistentData()
                .getCompound("MaidWeaponStory")
                .getBoolean("ContractFragmentsReceived");
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !ModList.get().isLoaded("patchouli")
                || player.getPersistentData().getBoolean(RECEIVED_FLAG)
                || receivedByLegacyStory) return;

        Item guideBook = ForgeRegistries.ITEMS.getValue(GUIDE_BOOK);
        if (guideBook == null || guideBook == Items.AIR) return;
        ItemStack stack = new ItemStack(guideBook);
        stack.getOrCreateTag().putString("patchouli:book", BOOK_ID);
        player.getInventory().placeItemBackInInventory(stack);
        player.getPersistentData().putBoolean(RECEIVED_FLAG, true);
        player.displayClientMessage(Component.translatable("maid_weapon.patchouli.book_received"), false);
    }

    private PatchouliBookHandler() { }
}

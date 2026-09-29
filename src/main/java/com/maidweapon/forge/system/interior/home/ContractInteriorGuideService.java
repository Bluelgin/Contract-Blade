package com.maidweapon.forge.system.interior.home;

import com.maidweapon.forge.system.interior.ContractInteriorSavedData;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Receipt belongs to the binding's plot. A full inventory retries later instead of losing the book. */
public final class ContractInteriorGuideService {
    public static void give(ServerPlayer player, ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot) {
        if (!plot.hasTerrainTheme() || plot.generatedStage() == 0 || plot.home().guideReceived) return;
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        var tag = book.getOrCreateTag();
        tag.putString("title", "关于我们的契约内景");
        tag.putString("author", "与你缔约的女仆");
        var pages = new ListTag();
        for (int page = 1; page <= 10; page++) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.translatable("maid_weapon.home.guide.page." + page))));
        }
        tag.put("pages", pages);
        // Keep translatable pages unresolved so the client uses its own language.
        tag.putBoolean("resolved", true);
        if (player.getInventory().add(book)) {
            plot.home().guideReceived = true;
            saved.setDirty();
        }
    }
    private ContractInteriorGuideService() {}
}

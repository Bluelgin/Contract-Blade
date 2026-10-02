package com.maidweapon.forge.system.interior.home;

import com.maidweapon.forge.system.interior.ContractInteriorSavedData;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A personal letter, not a settings UI. Receipt is tracked once per binding's plot. */
public final class ContractInteriorGuideService {
    public static final int GUIDE_VERSION = 4;
    public static final int PAGE_COUNT = 7;

    public static void give(ServerPlayer player, ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot) {
        // Upgrade marked letters and recognizable legacy guides in place, never unrelated books.
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack existing = player.getInventory().getItem(i);
            if (existing.is(Items.WRITTEN_BOOK) && existing.hasTag()
                    && (existing.getTag().getBoolean("ContractInteriorGuide")
                        || ("关于我们的契约内景".equals(existing.getTag().getString("title"))
                            && "与你缔约的女仆".equals(existing.getTag().getString("author"))))
                    && existing.getTag().getInt("ContractInteriorGuideVersion") < GUIDE_VERSION) write(existing);
        }
        if (!plot.hasTerrainTheme() || plot.generatedStage() == 0 || plot.home().guideReceived) return;
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        write(book);
        if (player.getInventory().add(book)) {
            plot.home().guideReceived = true;
            saved.setDirty();
        }
    }

    private static void write(ItemStack book) {
        var tag = book.getOrCreateTag();
        tag.putString("title", "写给主人的小册子");
        tag.putString("author", "与你缔约的女仆");
        tag.putBoolean("ContractInteriorGuide", true);
        tag.putInt("ContractInteriorGuideVersion", GUIDE_VERSION);
        var pages = new ListTag();
        for (int page = 1; page <= PAGE_COUNT; page++)
            pages.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.translatable("maid_weapon.home.guide.page." + page))));
        tag.put("pages", pages);
        // Leave translation components for the reader's own client language.
        tag.putBoolean("resolved", true);
    }

    private ContractInteriorGuideService() {}
}

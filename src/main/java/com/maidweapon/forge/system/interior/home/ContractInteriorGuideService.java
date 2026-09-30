package com.maidweapon.forge.system.interior.home;

import com.maidweapon.forge.system.interior.ContractInteriorSavedData;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Receipt belongs to the binding's plot. A full inventory retries later instead of losing the book. */
public final class ContractInteriorGuideService {
    public static void give(ServerPlayer player, ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot) {
        // Upgrade the already-delivered guide in place, without giving another
        // copy on every entry or touching unrelated player-written books.
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack existing = player.getInventory().getItem(i);
            if (existing.is(Items.WRITTEN_BOOK) && existing.hasTag()
                    && "关于我们的契约内景".equals(existing.getTag().getString("title"))
                    && ("与你缔约的女仆".equals(existing.getTag().getString("author"))
                    || existing.getTag().getBoolean("ContractInteriorGuide"))
                    && existing.getTag().getInt("ContractInteriorGuideVersion") < 2) write(existing);
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
        tag.putString("title", "关于我们的契约内景");
        tag.putString("author", "契约内景指引");
        tag.putBoolean("ContractInteriorGuide", true);
        tag.putInt("ContractInteriorGuideVersion", 2);
        var pages = new ListTag();
        for (int page = 1; page <= 10; page++) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(page(page))));
        }
        tag.put("pages", pages);
        // Keep translatable pages unresolved so the client uses its own language.
        tag.putBoolean("resolved", true);
    }
    private static Component page(int page) {
        MutableComponent text = Component.translatable("maid_weapon.home.guide.page." + page);
        if (page == 7) {
            text.append(Component.literal("\n\n"))
                    .append(command("maid_weapon.home.guide.clock.minecraft",
                            "/contractinterior clock minecraft"))
                    .append(Component.literal("  "))
                    .append(command("maid_weapon.home.guide.clock.realtime",
                            "/contractinterior clock realtime"))
                    .append(Component.literal("  "))
                    .append(command("maid_weapon.home.guide.clock.server",
                            "/contractinterior clock server"));
        } else if (page == 8) {
            text.append(Component.literal("\n\n"))
                    .append(command("maid_weapon.home.guide.zone.utc",
                            "/contractinterior timezone UTC"))
                    .append(Component.literal("  "))
                    .append(command("maid_weapon.home.guide.zone.plus8",
                            "/contractinterior timezone +08:00"))
                    .append(Component.literal("\n"))
                    .append(command("maid_weapon.home.guide.zone.shanghai",
                            "/contractinterior timezone Asia/Shanghai"));
        }
        return text;
    }

    private static Component command(String labelKey, String command) {
        return Component.translatable(labelKey).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("maid_weapon.home.guide.click")
                )));
    }

    private ContractInteriorGuideService() {}
}

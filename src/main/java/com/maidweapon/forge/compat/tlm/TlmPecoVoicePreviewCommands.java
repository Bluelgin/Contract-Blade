package com.maidweapon.forge.compat.tlm;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.List;

/**
 * Local, optional PECO sample audition; no OGG copying or server interaction.
 * Never registered without the Touhou Little Maid mod.
 */
public final class TlmPecoVoicePreviewCommands {
    private static final List<String> MOODS = List.of("greeting", "curious", "playful");

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("contractvoice")
                .then(Commands.literal("list").executes(ctx -> {
                    for (String mood : MOODS) {
                        int count = TlmPecoVoiceBridge.sampleCount(mood);
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("PECO " + mood + ": " + count + " clips (0–"
                                        + Math.max(0, count - 1) + ")"), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("preview")
                    .then(Commands.argument("mood", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(MOODS, builder))
                        .then(Commands.argument("index", IntegerArgumentType.integer(0, 255))
                            .executes(ctx -> {
                                String mood = StringArgumentType.getString(ctx, "mood");
                                int index = IntegerArgumentType.getInteger(ctx, "index");
                                boolean played = TlmPecoVoiceBridge.preview(mood, index);
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal(played
                                            ? "PECO preview: " + mood + " / " + index
                                            : "PECO sample unavailable: " + mood + " / " + index),
                                        false);
                                return played ? 1 : 0;
                            }))))
        );
    }

    private TlmPecoVoicePreviewCommands() {}
}

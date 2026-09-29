package com.maidweapon.forge.system.interior.home;

import com.maidweapon.forge.system.interior.ContractInteriorService;
import com.maidweapon.forge.system.interior.ContractInteriorSavedData;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class ContractHomeCommands {
    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        var clock = Commands.literal("clock");
        add(clock, "realtime", ContractHomeClock.Mode.REAL_TIME);
        add(clock, "minecraft", ContractHomeClock.Mode.MINECRAFT_TIME);
        add(clock, "server", ContractHomeClock.Mode.SERVER_TIME);
        root.then(clock).then(Commands.literal("timezone")
                .then(Commands.argument("zone", StringArgumentType.greedyString()).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String binding = ContractInteriorService.activeOwnedBinding(player);
                    if (binding.isEmpty()) return unavailable(player);
                    String input = StringArgumentType.getString(ctx, "zone").trim();
                    try {
                        ZoneId zone = input.matches("[+-]\\d{1,2}")
                                ? ZoneOffset.ofHours(Integer.parseInt(input)) : ZoneId.of(input);
                        var saved = ContractInteriorSavedData.get(player.getServer());
                        saved.getOrCreate(binding).home().zone = zone.getId();
                        saved.getOrCreate(binding).home().slot = Long.MIN_VALUE;
                        saved.setDirty(); ContractHomeRuntime.clockChanged(player);
                        player.sendSystemMessage(Component.translatable("maid_weapon.home.timezone_set", zone.getId()));
                        return 1;
                    } catch (RuntimeException invalid) {
                        player.sendSystemMessage(Component.translatable("maid_weapon.home.invalid_zone")); return 0;
                    }
                })));
    }
    private static void add(LiteralArgumentBuilder<CommandSourceStack> clock, String name, ContractHomeClock.Mode mode) {
        clock.then(Commands.literal(name).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String binding = ContractInteriorService.activeOwnedBinding(player);
            if (binding.isEmpty()) return unavailable(player);
            var saved = ContractInteriorSavedData.get(player.getServer());
            var home = saved.getOrCreate(binding).home();
            home.mode = mode; home.slot = Long.MIN_VALUE;
            saved.setDirty(); ContractHomeRuntime.clockChanged(player);
            player.sendSystemMessage(Component.translatable("maid_weapon.home.clock_set", name,
                    mode == ContractHomeClock.Mode.SERVER_TIME ? ContractHomeClock.serverZone().getId() : home.zone));
            return 1;
        }));
    }
    private static int unavailable(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("maid_weapon.home.own_interior_only")); return 0;
    }
    private ContractHomeCommands() {}
}

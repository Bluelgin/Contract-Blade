package com.maidweapon.forge.event;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.arguments.IntegerArgumentType;

/** Isolated fixture commands, compiled only in the development source set. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ContractInteriorValidationCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("contractblade.home.validation")) return;
        var root = Commands.literal("contractinterior");
        if (Boolean.getBoolean("contractblade.home.nativeValidation")
                && Boolean.getBoolean("contractblade.stress.validation")) {
            root.then(Commands.literal("stress-contracts").requires(source -> source.hasPermission(2))
                    .then(Commands.argument("payloadKiB", IntegerArgumentType.integer(0, 1024))
                            .executes(ctx -> {
                                try {
                                    String report = com.maidweapon.forge.compat.tlm.ContractStressValidation.run(
                                            ctx.getSource().getServer().overworld(),
                                            IntegerArgumentType.getInteger(ctx, "payloadKiB"));
                                    ctx.getSource().sendSuccess(() -> Component.literal(report), false);
                                    return 1;
                                } catch (Exception failure) {
                                    ctx.getSource().sendFailure(Component.literal("STRESS_FAILED: " + failure));
                                    return 0;
                                }
                            })));
        }
        root.then(Commands.literal("validate-home").requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    try {
                        var level = ctx.getSource().getServer().getLevel(
                                com.maidweapon.forge.system.interior.ContractInteriorService.INTERIOR_LEVEL);
                        com.maidweapon.forge.compat.tlm.ContractHomeValidation.run(level);
                        ctx.getSource().sendSuccess(() -> Component.literal("HOME_VALIDATION_PASSED"), false);
                        return 1;
                    } catch (Exception failure) {
                        ctx.getSource().sendFailure(Component.literal("HOME_VALIDATION_FAILED: " + failure));
                        return 0;
                    }
                }));
        event.getDispatcher().register(root);
    }
    private ContractInteriorValidationCommands() { }
}

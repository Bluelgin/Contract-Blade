package com.maidweapon.forge.event;

import com.maidweapon.forge.system.interior.ContractInteriorSelectionService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Player-facing commands used by clickable Contract Interior setup messages. */
@Mod.EventBusSubscriber
public final class ContractInteriorCommand {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        var choose = Commands.literal("choose");
        for (String theme : new String[]{
                "plains_garden",
                "sakura_garden",
                "bamboo_grove",
                "lake_islet",
                "hill_garden"
        }) {
            choose.then(Commands.literal(theme)
                    .executes(ctx -> ContractInteriorSelectionService.chooseAndEnter(
                            ctx.getSource().getPlayerOrException(),
                            theme
                    ) ? 1 : 0));
        }

        var root = Commands.literal("contractinterior").then(choose);
        com.maidweapon.forge.system.interior.home.ContractHomeCommands.addTo(root);
        if (Boolean.getBoolean("contractblade.home.validation")) {
            root.then(Commands.literal("validate-home").requires(source -> source.hasPermission(2))
                    .executes(ctx -> {
                        try {
                            var level = ctx.getSource().getServer().getLevel(
                                    com.maidweapon.forge.system.interior.ContractInteriorService.INTERIOR_LEVEL);
                            com.maidweapon.forge.compat.tlm.ContractHomeValidation.run(level);
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("HOME_VALIDATION_PASSED"), false);
                            return 1;
                        } catch (Exception failure) {
                            failure.printStackTrace();
                            ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("HOME_VALIDATION_FAILED: " + failure));
                            return 0;
                        }
                    }));
        }
        dispatcher.register(root);
    }

    private ContractInteriorCommand() {
    }
}

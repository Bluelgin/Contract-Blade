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

        dispatcher.register(Commands.literal("contractinterior")
                .then(choose));
    }

    private ContractInteriorCommand() {
    }
}

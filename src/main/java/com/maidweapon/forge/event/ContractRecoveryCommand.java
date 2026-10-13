package com.maidweapon.forge.event;

import com.maidweapon.forge.compat.tlm.SoulTalismanService;
import com.maidweapon.forge.system.contract.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Debug details stay in an operator command. Disaster backups are owned by /tlm backup. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ContractRecoveryCommand {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("recovery").requires(source -> source.hasPermission(2));
        for (String action : java.util.List.of("status", "cleanup", "migrate-film", "preflight", "restore"))
            root.then(Commands.literal(action).then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> execute(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), action))));
        event.getDispatcher().register(Commands.literal("maidweapon").then(root));
    }
    private static int execute(CommandSourceStack source, ServerPlayer player, String action) {
        var carrier = player.getMainHandItem();
        if (action.equals("status")) {
            source.sendSuccess(() -> Component.literal("State=" + ContractAnomalyRecoveryService.state(player, carrier)
                    + " Maid=" + ContractCarrierData.getBoundMaidUUID(carrier) + " Binding=" + ContractCarrierData.getBindingId(carrier)
                    + " Location=" + ContractRecoveryJournal.get(player.getServer()).transfer(ContractCarrierData.getBoundMaidUUID(carrier))
                    + " Legacy=" + ContractAnomalyRecoveryService.incident(player, carrier)
                    + " Losses=" + com.maidweapon.forge.system.deployment.ContractCarrierLossJournal.get(player.getServer()).describe(player.getUUID())), false);
            return 1;
        }
        if (action.equals("migrate-film")) {
            var result = SoulTalismanService.migrateLegacy(player, carrier);
            if (result.isEmpty()) { source.sendFailure(Component.translatable("maid_weapon.transfer.failed")); return 0; }
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, result); return 1;
        }
        if (action.equals("cleanup")) ContractAnomalyRecoveryService.cleanup(player, carrier, ContractAnomalyRecoveryService.incident(player, carrier));
        source.sendSuccess(() -> Component.translatable("maid_weapon.recovery.native_backup"), false);
        return 1; // old commands remain helpful aliases, never replay a backup into a weapon
    }
    private ContractRecoveryCommand() { }
}

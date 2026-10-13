package com.maidweapon.forge.event;

import com.maidweapon.forge.compat.tlm.SoulTalismanService;
import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import com.maidweapon.forge.item.MaidInfusion;
import com.mojang.logging.LogUtils;
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
        root.then(Commands.literal("confirm-absent").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.literal("after-world-audit").executes(ctx ->
                        confirmAbsent(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
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
        if (action.equals("cleanup")) ContractAnomalyRecoveryService.cleanup(player, carrier);
        source.sendSuccess(() -> Component.translatable("maid_weapon.recovery.native_backup"), false);
        return 1; // old commands remain helpful aliases, never replay a backup into a weapon
    }
    /** Explicit operator correction for external/offline deletion, never an automatic timeout. */
    private static int confirmAbsent(CommandSourceStack source, ServerPlayer owner) {
        var carrier = owner.getMainHandItem();
        String maid = ContractCarrierData.getBoundMaidUUID(carrier);
        var journal = ContractRecoveryJournal.get(owner.getServer());
        if (!source.hasPermission(2) || !MaidInfusion.isInfused(carrier)
                || !ContractCarrierData.isOwner(carrier, owner) || ContractCarrierData.isContractSuperseded(carrier)
                || !journal.knownSchema() || maid == null || maid.isBlank()) return 0;
        try { java.util.UUID.fromString(maid); }
        catch (IllegalArgumentException invalid) { return 0; }
        if (ContractWeaponLocator.findEntity(owner, maid) != null) {
            source.sendFailure(Component.literal("Maid UUID is still loaded; absence cannot be confirmed."));
            return 0;
        }
        // The literal after-world-audit is the operator's assertion about unloaded chunks
        // and all dimensions. Merely failing a loaded-entity lookup is never such evidence.
        journal.markMaidAbsent(maid);
        ContractAnomalyRecoveryService.cleanup(owner, carrier);
        LogUtils.getLogger().warn("[Contract Blade] Operator {} confirmed absence of maid {} after world audit",
                source.getTextName(), maid);
        source.sendSuccess(() -> Component.literal("Absence corrected for " + maid
                + "; existing snapshots/native backup may now be restored."), true);
        return 1;
    }
    private ContractRecoveryCommand() { }
}

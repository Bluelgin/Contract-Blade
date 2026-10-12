package com.maidweapon.forge.event;

import com.maidweapon.forge.compat.tlm.*;
import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Operators select a real player's held carrier. No UUID/NBT uploaded by clients. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ContractRecoveryCommand {
    private record Preview(String binding, long expires, CompletableFuture<TlmBackupBridge.Result> future) { }
    private static final Map<ServerPlayer, Preview> PREVIEWS = new WeakHashMap<>();
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("recovery").requires(source -> source.hasPermission(2));
        for (String action : List.of("status", "preflight", "restore", "cleanup", "migrate-film"))
            root.then(Commands.literal(action).then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> execute(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), action))));
        event.getDispatcher().register(Commands.literal("maidweapon").then(root));
    }
    private static int execute(CommandSourceStack source, ServerPlayer player, String action) {
        var carrier = player.getMainHandItem();
        var record = ContractAnomalyRecoveryService.incident(player, carrier);
        if (action.equals("migrate-film")) {
            var result = SoulTalismanService.migrateLegacy(player, carrier);
            if (result.isEmpty()) return report(source, "Legacy film cannot be validated; input retained", false);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, result);
            return report(source, "Validated legacy MaidInfo moved into a soul talisman", true);
        }
        if (action.equals("status")) return report(source, "State=" + ContractAnomalyRecoveryService.state(player, carrier)
                + " Maid=" + ContractCarrierData.getBoundMaidUUID(carrier) + " Binding=" + ContractCarrierData.getBindingId(carrier)
                + " Fox=" + record.contains("Fox") + " TLM=" + TlmBackupBridge.supported()
                + " Incident=" + record.getString("Status") + "/" + record.getString("Reason")
                + " ReceiptSpent=" + record.getBoolean("BackupSpent") + " Diagnostic=" + record.getString("Diagnostic")
                + " Losses=" + com.maidweapon.forge.system.deployment.ContractCarrierLossJournal.get(player.getServer()).describe(player.getUUID()), true);
        if (action.equals("cleanup")) {
            boolean cleaned = ContractAnomalyRecoveryService.cleanup(player, carrier, record);
            return report(source, "Confirmed ordinary cleanup=" + cleaned, cleaned);
        }
        String binding = ContractCarrierData.getBindingId(carrier), maid = ContractCarrierData.getBoundMaidUUID(carrier);
        if (binding == null || maid == null || !record.getBoolean("Confirmed")) return report(source, "No confirmed held contract incident", false);
        long now = player.getServer().overworld().getGameTime();
        var preview = PREVIEWS.get(player);
        if (action.equals("preflight")) {
            if (preview != null && preview.binding().equals(binding) && preview.expires() > now && !preview.future().isDone())
                return report(source, "Backup read already pending", false);
            CompletableFuture<TlmBackupBridge.Result> future;
            try {
                var retained = ContractAnomalyRecoveryService.retainedSnapshot(player, carrier);
                future = retained == null ? TlmBackupBridge.latest(player, UUID.fromString(maid)) : CompletableFuture.completedFuture(retained);
            }
            catch (IllegalArgumentException invalid) { return report(source, "Malformed legacy maid UUID retained", false); }
            preview = new Preview(binding, now + 1200, future); PREVIEWS.put(player, preview);
            var selected = preview;
            future.thenAccept(result -> player.getServer().execute(() -> {
                if (PREVIEWS.get(player) != selected || selected.expires() < player.getServer().overworld().getGameTime()) return;
                var current = player.getMainHandItem();
                String denied = ContractAnomalyRecoveryService.preflight(player, current, ContractAnomalyRecoveryService.incident(player, current), result);
                report(source, denied == null ? "READY: " + result.file() + "; restore requires explicit operator command" : denied, denied == null);
            }));
            return report(source, "Read-only backup inspection queued", true);
        }
        if (preview == null || !preview.binding().equals(binding) || preview.expires() <= now || !preview.future().isDone())
            return report(source, "Run preflight and wait for its result first", false);
        PREVIEWS.remove(player);
        return report(source, ContractAnomalyRecoveryService.restore(player, carrier, preview.future().join()), true);
    }
    private static int report(CommandSourceStack source, String message, boolean success) {
        if (success) source.sendSuccess(() -> Component.literal(message), false);
        else source.sendFailure(Component.literal(message));
        return success ? 1 : 0;
    }
    private ContractRecoveryCommand() { }
}

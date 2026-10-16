package com.maidweapon.forge.system.mastery;

import com.maidweapon.common.data.ContractMasteryData;
import com.maidweapon.common.compat.ModCompatManager;
import com.maidweapon.forge.network.ContractMasteryNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Sole gameplay write boundary. Item records and client snapshots cannot write player progression. */
public final class ContractMasteryService {
    private static final String CLIENT_SNAPSHOT = "maid_weapon:mastery_view";
    public static ContractMasteryData getProgress(Player player) {
        if (player instanceof ServerPlayer server) return ContractMasterySavedData.get(server.getServer()).read(server.getUUID());
        return player == null ? new ContractMasteryData() : ContractMasteryCodec.read(player.getPersistentData().getCompound(CLIENT_SNAPSHOT));
    }
    public static int getLevel(Player player) { return getProgress(player).getLevel(); }
    public static int getLevel(Player player, ItemStack carrier) {
        observe(player, carrier); return getLevel(player);
    }
    public static void observe(Player player, ItemStack carrier) {
        if (player instanceof ServerPlayer server && ContractMasteryMigration.observe(server, carrier)) sync(server);
    }
    public static void observeInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) observe(player, player.getInventory().getItem(i));
    }
    public static boolean tryAdvance(ServerPlayer player, int tier, String entityId) {
        if (!ContractMasterySavedData.get(player.getServer()).writable(player.getUUID())) return false;
        var data = getProgress(player); int oldLevel = data.getLevel(); data.addKill();
        boolean upgraded = data.tryUpgrade(tier, entityId.equals("minecraft:ender_dragon"), entityId.equals("minecraft:wither"), entityId);
        ContractMasterySavedData.get(player.getServer()).write(player.getUUID(), data); sync(player);
        if (upgraded) {
            player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1, 1);
            player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                    player.getX(), player.getY() + 1, player.getZ(), 50, 1.5, 1, 1.5, .5);
            player.displayClientMessage(Component.translatable("maid_weapon.message.weapon_upgraded", data.getLevel()), true);
            ModCompatManager.notifyUpgrade("maid_sword", oldLevel, data.getLevel());
        }
        return upgraded;
    }
    public static void setLevel(CommandSourceStack admin, ServerPlayer player, int level) {
        if (!admin.hasPermission(2)) throw new SecurityException("Operator permission 2 required");
        if (level < 1 || level > 10) throw new IllegalArgumentException("Mastery level must be 1..10");
        // Import held inventory before the explicit override; imported sources cannot undo a later admin correction.
        observeInventory(player);
        var data = getProgress(player); data.setLevel(level);
        ContractMasterySavedData.get(player.getServer()).write(player.getUUID(), data); sync(player);
    }
    public static void sync(ServerPlayer player) { ContractMasteryNetwork.send(player, getProgress(player)); }
    /** Called exclusively by PLAY_TO_CLIENT handler; never authoritative on a server. */
    public static void applyClientSnapshot(Player player, ContractMasteryData data) {
        if (player != null && player.level().isClientSide) player.getPersistentData().put(CLIENT_SNAPSHOT, ContractMasteryCodec.write(data));
    }
    private ContractMasteryService() { }
}

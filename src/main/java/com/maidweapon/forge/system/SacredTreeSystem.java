package com.maidweapon.forge.system;

import com.maidweapon.common.MaidWeaponConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.Structure;

/** 神树附近每秒恢复好感度 + 药房附近生命恢复 */
public class SacredTreeSystem {

    private static final ResourceLocation TREE_ID =
            new ResourceLocation(MaidWeaponConstants.MOD_ID, "sacred_tree");
    private static final ResourceLocation YAKKYOKU_ID =
            new ResourceLocation(MaidWeaponConstants.MOD_ID, "yakkyoku");

    /** 玩家累计 tick，用于每 2 tick（≈0.1秒）累加 0.1 */
    private static final java.util.Map<java.util.UUID, Float> TREE_TICK_ACCUM = new java.util.HashMap<>();
    /** 药房已触发记忆的玩家 */
    private static final java.util.Set<java.util.UUID> YAKKYOKU_MEMORY_SHOWN = new java.util.HashSet<>();

    /** 检测玩家附近是否有神树 */
    public static boolean isNearTree(Player player) {
        if (player.level().isClientSide) return false;
        ServerLevel level = (ServerLevel) player.level();

        Registry<Structure> registry = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, TREE_ID);
        Holder.Reference<Structure> holder = registry.getHolder(key).orElse(null);
        if (holder == null) return false;

        var result = level.getChunkSource().getGenerator().findNearestMapStructure(
                level, HolderSet.direct(holder),
                player.blockPosition(), 64, false
        );
        return result != null && result.getFirst().distSqr(player.blockPosition()) < 1024;
    }

    /** 获取恢复倍率（神树附近 ×3） */
    public static int getRecoveryMultiplier(Player player) {
        return isNearTree(player) ? 3 : 1;
    }

    /** 检测玩家是否在药房附近 */
    public static boolean isNearYakkyoku(Player player) {
        if (player.level().isClientSide) return false;
        ServerLevel level = (ServerLevel) player.level();

        Registry<Structure> registry = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, YAKKYOKU_ID);
        Holder.Reference<Structure> holder = registry.getHolder(key).orElse(null);
        if (holder == null) return false;

        BlockPos pos = player.blockPosition();
        var result = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(holder), pos, 10, false);
        if (result == null) return false;
        return pos.distSqr(result.getFirst()) < 25 * 25;
    }

    /** 药房首次进入记忆触发 */
    public static void tryTriggerYakkyokuMemory(Player player) {
        if (!isNearYakkyoku(player)) return;
        if (!YAKKYOKU_MEMORY_SHOWN.add(player.getUUID())) return;
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("maid_weapon.memory.potion_room"), false
        );
    }

    /**
     * 每 tick 恢复好感度（每秒 +0.5，积攒到整数时触发）
     * @return 本次恢复的好感度（0 或 1）
     */
    public static int tickRecovery(Player player) {
        if (!isNearTree(player)) return 0;
        java.util.UUID id = player.getUUID();
        float acc = TREE_TICK_ACCUM.getOrDefault(id, 0f);
        acc += 0.025f; // 每 tick（1/20秒）+0.025 = 每秒 +0.5
        if (acc >= 1.0f) {
            acc -= 1.0f;
            TREE_TICK_ACCUM.put(id, acc);
            return 1;
        }
        TREE_TICK_ACCUM.put(id, acc);
        return 0;
    }

    /** 尝试触发首次靠近回忆 */
    public static void tryTriggerMemory(Player player) {
        if (!isNearTree(player)) return;
        String key = player.getScoreboardName() + "_tree_memory";
        if (player.getPersistentData().getBoolean(key)) return;
        player.getPersistentData().putBoolean(key, true);

        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "§6✦ 你伸手触碰树干。§r\n§7掌心传来一阵温热——"
                ), false
        );
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "§7你好像曾经在这棵树下等过谁。"
                ), false
        );
    }
}

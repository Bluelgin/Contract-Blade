package com.maidweapon.forge.event;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.ContractNbtAudit;
import com.maidweapon.forge.system.ContractNbtGuard;
import com.maidweapon.forge.system.interior.ContractInteriorGallery;
import com.maidweapon.forge.system.interior.ContractInteriorService;
import com.maidweapon.forge.system.interior.ContractInteriorTerrainPreview;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 调试指令：/maidweapon level/favorability/resonance
 * 用于测试契约的等级、TLM 好感度与短期共鸣状态。
 */
@Mod.EventBusSubscriber
public class MaidWeaponCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("maidweapon")
                .requires(s -> s.hasPermission(2))

                // /maidweapon level <1-10>
                .then(Commands.literal("level")
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                                .executes(ctx -> setLevel(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "level")))))

                // /maidweapon favorability <0-384>
                .then(Commands.literal("favorability")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 384))
                                .executes(ctx -> setFavorability(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "value")))))

                // /maidweapon resonance <0-200>
                .then(Commands.literal("resonance")
                        .then(Commands.argument("value", IntegerArgumentType.integer(
                                        MaidWeaponData.MIN_RESONANCE,
                                        MaidWeaponData.MAX_RESONANCE))
                                .executes(ctx -> setResonance(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "value")))))

                // /maidweapon nbt
                .then(Commands.literal("nbt")
                        .executes(ctx -> auditNbt(ctx.getSource())))

                // /maidweapon interior gallery [stage/rebuild/leave]
                .then(Commands.literal("interior")
                        .then(Commands.literal("gallery")
                                .executes(ctx -> openInteriorGallery(ctx.getSource()))
                                .then(Commands.literal("stage")
                                        .then(Commands.argument(
                                                        "stage",
                                                        IntegerArgumentType.integer(1, 5))
                                                .executes(ctx -> visitInteriorGalleryStage(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(
                                                                ctx,
                                                                "stage")))))
                                .then(Commands.literal("rebuild")
                                        .then(Commands.argument(
                                                        "warmth",
                                                        IntegerArgumentType.integer(1, 6))
                                                .executes(ctx -> rebuildInteriorGallery(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(
                                                                ctx,
                                                                "warmth")))))
                                .then(Commands.literal("generate")
                                        .then(Commands.argument(
                                                        "warmth",
                                                        IntegerArgumentType.integer(1, 6))
                                                .executes(ctx -> generateInteriorGallery(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(
                                                                ctx,
                                                                "warmth")))))
                                .then(Commands.literal("leave")
                                        .executes(ctx -> leaveInteriorGallery(
                                                ctx.getSource()))))
                        .then(Commands.literal("terrain-preview")
                                .then(Commands.literal("generate")
                                        .executes(ctx -> generateInteriorTerrainPreview(
                                                ctx.getSource())))))
        );
    }

    private static int setLevel(CommandSourceStack source, int level) {
        try {
            ItemStack stack = editableContract(source);
            if (stack.isEmpty()) return 0;

            MaidWeaponData data = MaidInfusion.data(stack);
            data.setLevel(level);
            ContractCarrierData.setMaidData(stack, data);

            source.sendSuccess(() -> Component.literal("§a已将契约等级设为 Lv." + level), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c指令执行失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int setFavorability(CommandSourceStack source, int value) {
        try {
            ItemStack stack = editableContract(source);
            if (stack.isEmpty()) return 0;

            MaidWeaponData data = MaidInfusion.data(stack);
            data.setFavorability(value);
            ContractCarrierData.setMaidData(stack, data);
            source.sendSuccess(() -> Component.literal("§a已将契约好感度设为 " + value + "/384"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c指令执行失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int setResonance(CommandSourceStack source, int value) {
        try {
            ItemStack stack = editableContract(source);
            if (stack.isEmpty()) return 0;

            MaidWeaponData data = MaidInfusion.data(stack);
            data.setResonance(value);
            ContractCarrierData.setMaidData(stack, data);
            source.sendSuccess(() -> Component.literal(
                    "§b已将契约共鸣设为 " + value + "/" + MaidWeaponData.MAX_RESONANCE), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c指令执行失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int openInteriorGallery(CommandSourceStack source) {
        try {
            return ContractInteriorGallery.open(source.getPlayerOrException()) ? 1 : 0;
        } catch (Exception exception) {
            source.sendFailure(Component.literal(
                    "§c无法进入契约内景画廊: " + exception.getMessage()));
            return 0;
        }
    }

    private static int visitInteriorGalleryStage(CommandSourceStack source, int stage) {
        try {
            return ContractInteriorGallery.visitStage(
                    source.getPlayerOrException(),
                    stage
            ) ? 1 : 0;
        } catch (Exception exception) {
            source.sendFailure(Component.literal(
                    "§c无法前往 Stage " + stage + ": " + exception.getMessage()));
            return 0;
        }
    }

    private static int rebuildInteriorGallery(CommandSourceStack source, int warmth) {
        try {
            return ContractInteriorGallery.rebuild(
                    source.getPlayerOrException(),
                    warmth
            ) ? 1 : 0;
        } catch (Exception exception) {
            source.sendFailure(Component.literal(
                    "§c无法重建契约内景画廊: " + exception.getMessage()));
            return 0;
        }
    }

    private static int generateInteriorGallery(CommandSourceStack source, int warmth) {
        try {
            int applied = ContractInteriorGallery.rebuild(
                    source.getServer(),
                    warmth,
                    true
            );
            if (applied < 0) {
                source.sendFailure(Component.literal("§c契约内景维度没有加载"));
                return 0;
            }
            source.sendSuccess(
                    () -> Component.literal(
                            "§a已在无玩家模式下生成契约内景画廊，好感层级 " + applied),
                    true
            );
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal(
                    "§c无法生成契约内景画廊: " + exception.getMessage()));
            return 0;
        }
    }

    private static int generateInteriorTerrainPreview(CommandSourceStack source) {
        try {
            boolean generated = ContractInteriorTerrainPreview.generate(source.getServer());
            if (!generated) {
                source.sendFailure(Component.literal("§c契约内景维度没有加载"));
                return 0;
            }
            source.sendSuccess(
                    () -> Component.literal("§a已生成正式契约地形离线预览区域"),
                    true
            );
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal(
                    "§c无法生成契约地形预览: " + exception.getMessage()));
            return 0;
        }
    }

    private static int leaveInteriorGallery(CommandSourceStack source) {
        try {
            var player = source.getPlayerOrException();
            if (!ContractInteriorService.isGallerySession(player)) {
                source.sendFailure(Component.literal("§e你当前不在契约内景画廊"));
                return 0;
            }
            return ContractInteriorService.exit(player) ? 1 : 0;
        } catch (Exception exception) {
            source.sendFailure(Component.literal(
                    "§c无法离开契约内景画廊: " + exception.getMessage()));
            return 0;
        }
    }

    private static ItemStack editableContract(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (!MaidInfusion.isInfused(stack)) {
            source.sendFailure(Component.literal("§c请手持已有契约的武器"));
            return ItemStack.EMPTY;
        }
        if (!ContractCarrierData.isOwner(stack, player)) {
            source.sendFailure(Component.literal("§c只有契约原主人可以修改契约数据"));
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private static int auditNbt(CommandSourceStack source) {
        try {
            ItemStack stack = source.getPlayerOrException().getMainHandItem();
            if (stack.isEmpty()) {
                source.sendFailure(Component.literal("§c请手持要审计的契约武器"));
                return 0;
            }
            ContractNbtAudit.Report report = ContractNbtAudit.inspect(stack);
            source.sendSuccess(() -> Component.literal("§b契约 NBT 审计"), false);
            source.sendSuccess(() -> Component.literal("§7完整物品: §f"
                    + ContractNbtGuard.formatBytes(report.completeItemBytes())
                    + "§7；tag: §f" + ContractNbtGuard.formatBytes(report.itemTagBytes())), false);
            source.sendSuccess(() -> Component.literal("§7当前女仆数据: §f"
                    + ContractNbtGuard.formatBytes(report.currentMaidBytes())
                    + "§7；压缩载荷: §f"
                    + ContractNbtGuard.formatBytes(report.currentCompressedBytes())), false);
            source.sendSuccess(() -> Component.literal("§7内置归档: §f"
                    + ContractNbtGuard.formatBytes(report.intrinsicArchiveBytes())
                    + "§7；外部归档: §f"
                    + ContractNbtGuard.formatBytes(report.externalArchiveBytes())), false);
            source.sendSuccess(() -> Component.literal("§7当前投影: §f"
                    + (report.projectedSpirit().isEmpty() ? "无" : report.projectedSpirit())
                    + (report.duplicateProjection() ? " §c[发现重复投影]" : " §a[单份权威]")), false);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("§cNBT 审计失败: " + exception.getMessage()));
            return 0;
        }
    }
}

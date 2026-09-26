package com.maidweapon.forge.event;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.ContractNbtAudit;
import com.maidweapon.forge.system.ContractNbtGuard;
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
        );
    }

    private static int setLevel(CommandSourceStack source, int level) {
        try {
            ItemStack stack = source.getPlayerOrException().getMainHandItem();
            if (!(stack.getItem() instanceof MaidWeaponItem)) {
                source.sendFailure(Component.literal("§c请手持女仆之刃"));
                return 0;
            }
            if (!MaidWeaponItem.hasMaidData(stack)) {
                source.sendFailure(Component.literal("§c女仆之刃尚未绑定女仆"));
                return 0;
            }
            MaidWeaponData data = MaidWeaponItem.getMaidData(stack);
            data.setLevel(level);
            MaidWeaponItem.setMaidData(stack, data);

            source.sendSuccess(() -> Component.literal("§a已将女仆之刃等级设为 Lv." + level), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c指令执行失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int setFavorability(CommandSourceStack source, int value) {
        try {
            ItemStack stack = source.getPlayerOrException().getMainHandItem();
            if (!(stack.getItem() instanceof MaidWeaponItem)) {
                source.sendFailure(Component.literal("§c请手持女仆之刃"));
                return 0;
            }
            if (!MaidWeaponItem.hasMaidData(stack)) {
                source.sendFailure(Component.literal("§c女仆之刃尚未绑定女仆"));
                return 0;
            }
            MaidWeaponData data = MaidWeaponItem.getMaidData(stack);
            data.setFavorability(value);
            MaidWeaponItem.setMaidData(stack, data);
            source.sendSuccess(() -> Component.literal("§a已将女仆之刃好感度设为 " + value + "/384"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c指令执行失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int setResonance(CommandSourceStack source, int value) {
        try {
            ItemStack stack = source.getPlayerOrException().getMainHandItem();
            if (!MaidInfusion.isInfused(stack)) {
                source.sendFailure(Component.literal("§c请手持已有契约的武器"));
                return 0;
            }
            if (!MaidWeaponItem.isOwner(stack, source.getPlayerOrException())) {
                source.sendFailure(Component.literal("§c只有契约原主人可以修改共鸣"));
                return 0;
            }
            MaidWeaponData data = MaidInfusion.data(stack);
            data.setResonance(value);
            MaidWeaponItem.setMaidData(stack, data);
            source.sendSuccess(() -> Component.literal(
                    "§b已将契约共鸣设为 " + value + "/" + MaidWeaponData.MAX_RESONANCE), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c指令执行失败: " + e.getMessage()));
            return 0;
        }
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

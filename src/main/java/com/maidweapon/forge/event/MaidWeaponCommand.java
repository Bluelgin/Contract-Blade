package com.maidweapon.forge.event;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
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

            // 指令触发记忆碎片
            Player player = source.getPlayerOrException();
            String memoryKey = null;
            switch (level) {
                case 3 -> memoryKey = "maid_weapon.memory.level_3";
                case 5 -> memoryKey = "maid_weapon.memory.level_5";
                case 7 -> memoryKey = "maid_weapon.memory.level_7";
                case 10 -> memoryKey = "maid_weapon.memory.level_10";
            }
            if (memoryKey != null) {
                player.displayClientMessage(
                        Component.translatable(memoryKey), false
                );
            }

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
}

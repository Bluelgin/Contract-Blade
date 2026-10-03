package com.maidweapon.forge.event;

import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.worldgen.ShinkitsuShrinePiece;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Explicit administrator placement, including flat test worlds; natural terrain checks stay intact. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ShrinePlacementCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("maid_weapon")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("shrine").then(Commands.literal("place")
                        .executes(context -> place(context.getSource(), BlockPos.containing(
                                context.getSource().getPosition()).offset(10, -1, 10)))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> place(context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos")))))));
    }

    private static int place(CommandSourceStack source, BlockPos origin) {
        if (!SlashBladeCompat.isLoaded()) {
            source.sendFailure(Component.translatable("maid_weapon.command.shrine.requires_slashblade"));
            return 0;
        }
        var level = source.getLevel();
        var template = level.getStructureManager().get(ShinkitsuShrinePiece.TEMPLATE);
        if (template.isEmpty()) {
            source.sendFailure(Component.translatable("maid_weapon.command.shrine.failed"));
            return 0;
        }
        var size = template.get().getSize();
        BlockPos far = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        if (origin.getY() < level.getMinBuildHeight() || far.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(origin)
                || !level.getWorldBorder().isWithinBounds(far)) {
            source.sendFailure(Component.translatable("maid_weapon.command.shrine.outside_world"));
            return 0;
        }
        if (!template.get().placeInWorld(level, origin, origin,
                ShinkitsuShrinePiece.settings(), level.random, 2)) {
            source.sendFailure(Component.translatable("maid_weapon.command.shrine.failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("maid_weapon.command.shrine.placed",
                origin.getX(), origin.getY(), origin.getZ()), true);
        return 1;
    }

    private ShrinePlacementCommands() { }
}

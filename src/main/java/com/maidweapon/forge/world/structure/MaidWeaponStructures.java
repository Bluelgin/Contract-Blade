package com.maidweapon.forge.world.structure;

import com.maidweapon.common.MaidWeaponConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * 结构工具 — 提供通用放置逻辑，支持自定义生成条件。
 *
 * 示例 — 山顶生成：
 *
 *   // 在结构类中：
 *   public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
 *       return MaidWeaponStructures.placeTemplate(
 *           ctx.structureTemplateManager(), ctx, TEMPLATE, 0,
 *           (c, p) -> p.getY() >= c.chunkGenerator().getSeaLevel() + 15);
 *   }
 *
 * 示例 — 水中生成：
 *
 *   return MaidWeaponStructures.placeTemplate(..., 0,
 *       (c, p) -> p.getY() < c.chunkGenerator().getSeaLevel());
 */
public class MaidWeaponStructures {

    public static final ResourceLocation MOD_ID = new ResourceLocation(MaidWeaponConstants.MOD_ID);

    /** 平地放置（无特殊条件） */
    public static Optional<Structure.GenerationStub> placeTemplate(
            StructureTemplateManager manager,
            Structure.GenerationContext context,
            ResourceLocation templateName, int yOffset) {
        return placeTemplate(manager, context, templateName, yOffset, (ctx, pos) -> true);
    }

    /**
     * 带条件放置结构。
     *
     * @param canPlace (ctx, 放置坐标) → true 允许生成
     */
    public static Optional<Structure.GenerationStub> placeTemplate(
            StructureTemplateManager manager,
            Structure.GenerationContext context,
            ResourceLocation templateName,
            int yOffset,
            BiPredicate<Structure.GenerationContext, BlockPos> canPlace) {

        BlockPos centerPos = context.chunkPos().getMiddleBlockPosition(0);

        Optional<StructureTemplate> template = manager.get(templateName);
        if (template.isEmpty()) return Optional.empty();

        int groundY = context.chunkGenerator().getFirstOccupiedHeight(
                centerPos.getX(), centerPos.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());

        BlockPos pos = new BlockPos(centerPos.getX(), groundY + yOffset, centerPos.getZ());
        if (!canPlace.test(context, pos)) return Optional.empty();

        return Optional.of(new Structure.GenerationStub(pos, builder ->
                builder.addPiece(new MaidWeaponStructurePiece(manager, templateName, pos))));
    }
}

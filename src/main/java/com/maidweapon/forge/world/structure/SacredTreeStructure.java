package com.maidweapon.forge.world.structure;

import com.maidweapon.common.MaidWeaponConstants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.Optional;

/**
 * 神树 — 生成在雪地平原。
 * A: 靠近时好感度恢复 ×3
 * C: 第一次靠近触发回忆碎片
 */
public class SacredTreeStructure extends Structure {

    public static final ResourceLocation TEMPLATE =
            new ResourceLocation(MaidWeaponConstants.MOD_ID, "sacred_tree");

    public static final Codec<SacredTreeStructure> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(settingsCodec(instance))
                    .apply(instance, SacredTreeStructure::new));

    public SacredTreeStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        BlockPos centerPos = ctx.chunkPos().getMiddleBlockPosition(0);

        int surfaceY = ctx.chunkGenerator().getFirstOccupiedHeight(
                centerPos.getX(), centerPos.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG,
                ctx.heightAccessor(), ctx.randomState());

        // 结构原点在地表 - 结构包含地基和树
        BlockPos pos = new BlockPos(centerPos.getX(), surfaceY + 1, centerPos.getZ());

        return Optional.of(new GenerationStub(pos, piecesBuilder ->
                piecesBuilder.addPiece(
                        new MaidWeaponStructurePiece(ctx.structureTemplateManager(), TEMPLATE, pos))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.SACRED_TREE.get();
    }
}

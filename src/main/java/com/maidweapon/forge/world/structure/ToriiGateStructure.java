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
 * 水上鸟居 — 在湖面上自然生成。
 * 条件：水深 2~5 格，水面开阔（周围至少 60% 为水）。
 */
public class ToriiGateStructure extends Structure {

    public static final ResourceLocation TEMPLATE =
            new ResourceLocation(MaidWeaponConstants.MOD_ID, "torii_gate");

    public static final Codec<ToriiGateStructure> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(settingsCodec(instance))
                    .apply(instance, ToriiGateStructure::new));

    public ToriiGateStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        BlockPos centerPos = ctx.chunkPos().getMiddleBlockPosition(0);

        // 获取水面高度和海底高度
        int waterSurface = ctx.chunkGenerator().getFirstOccupiedHeight(
                centerPos.getX(), centerPos.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG,
                ctx.heightAccessor(), ctx.randomState());
        int waterFloor = ctx.chunkGenerator().getFirstOccupiedHeight(
                centerPos.getX(), centerPos.getZ(),
                Heightmap.Types.OCEAN_FLOOR_WG,
                ctx.heightAccessor(), ctx.randomState());

        int waterDepth = waterSurface - waterFloor;

        // 检查水深 2~5 格
        if (waterDepth < 2 || waterDepth > 5) return Optional.empty();

        // 检查水面开阔度（周围 8 格内至少 60% 为水）
        if (!isLakeOpen(ctx, centerPos, waterSurface)) return Optional.empty();

        // 生成在水面位置
        BlockPos pos = new BlockPos(centerPos.getX(), waterSurface, centerPos.getZ());

        return Optional.of(new GenerationStub(pos, piecesBuilder ->
                piecesBuilder.addPiece(
                        new MaidWeaponStructurePiece(ctx.structureTemplateManager(), TEMPLATE, pos))));
    }

    /** 检查水面是否足够开阔 */
    private boolean isLakeOpen(GenerationContext ctx, BlockPos center, int waterY) {
        int waterCount = 0;
        int total = 0;
        for (int dx = -8; dx <= 8; dx += 2) {
            for (int dz = -8; dz <= 8; dz += 2) {
                total++;
                int h = ctx.chunkGenerator().getFirstOccupiedHeight(
                        center.getX() + dx, center.getZ() + dz,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        ctx.heightAccessor(), ctx.randomState());
                if (h >= waterY - 1 && h <= waterY + 1) {
                    waterCount++;
                }
            }
        }
        return (float) waterCount / total >= 0.6f;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.TORII_GATE.get();
    }
}

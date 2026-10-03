package com.maidweapon.forge.worldgen;

import com.maidweapon.forge.compat.SlashBladeCompat;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/** A single authored shrine, gated before generation when SlashBlade is absent. */
public final class ShinkitsuShrineStructure extends Structure {
    public static final Codec<ShinkitsuShrineStructure> CODEC =
            simpleCodec(ShinkitsuShrineStructure::new);

    public ShinkitsuShrineStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!SlashBladeCompat.isLoaded()) return Optional.empty();
        int x = context.chunkPos().getMinBlockX();
        int z = context.chunkPos().getMinBlockZ();
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        // Avoid embedding a 47-block-wide authored courtyard into steep hills.
        for (int dx : new int[]{0, 23, 46}) {
            for (int dz : new int[]{0, 23, 46}) {
                int y = context.chunkGenerator().getBaseHeight(x + dx, z + dz,
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(),
                        context.randomState());
                low = Math.min(low, y);
                high = Math.max(high, y);
            }
        }
        if (high - low > 6 || low <= context.chunkGenerator().getSeaLevel()) {
            return Optional.empty();
        }
        BlockPos origin = new BlockPos(x, high - 1, z);
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(
                new ShinkitsuShrinePiece(context.structureTemplateManager(), origin))));
    }

    @Override
    public StructureType<?> type() {
        return ShrineWorldgen.STRUCTURE.get();
    }
}

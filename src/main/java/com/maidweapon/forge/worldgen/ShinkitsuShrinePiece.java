package com.maidweapon.forge.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Vanilla template placement owns chunk clipping, entity placement and save/load. */
public final class ShinkitsuShrinePiece extends TemplateStructurePiece {
    public static final ResourceLocation TEMPLATE =
            ResourceLocation.parse("maid_weapon:shinkitsu_shrine");

    public ShinkitsuShrinePiece(StructureTemplateManager manager, BlockPos origin) {
        super(ShrineWorldgen.PIECE.get(), 0, manager, TEMPLATE, TEMPLATE.toString(),
                settings(), origin);
    }

    public ShinkitsuShrinePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(ShrineWorldgen.PIECE.get(), tag, manager, ignored -> settings());
    }

    public static StructurePlaceSettings settings() {
        return new StructurePlaceSettings().setRotation(Rotation.NONE)
                .setIgnoreEntities(false).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .addProcessor(ShrineEntityProcessor.INSTANCE);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox bounds) {
        // The authored template has no data markers; its real blade stand is retained.
    }
}

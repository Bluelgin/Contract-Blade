package com.maidweapon.forge.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Forge's entity processor relocates the stand's hanging anchor before NBT loading. */
public final class ShrineEntityProcessor extends StructureProcessor {
    public static final ShrineEntityProcessor INSTANCE = new ShrineEntityProcessor();
    public static final Codec<ShrineEntityProcessor> CODEC = Codec.unit(INSTANCE);

    @Override
    public StructureTemplate.StructureEntityInfo processEntity(LevelReader level, BlockPos origin,
            StructureTemplate.StructureEntityInfo original,
            StructureTemplate.StructureEntityInfo current, StructurePlaceSettings settings,
            StructureTemplate template) {
        if (!"slashblade:blade_stand_entity".equals(current.nbt.getString("id"))) return current;
        var nbt = current.nbt.copy();
        nbt.putInt("TileX", current.blockPos.getX());
        nbt.putInt("TileY", current.blockPos.getY());
        nbt.putInt("TileZ", current.blockPos.getZ());
        return new StructureTemplate.StructureEntityInfo(current.pos, current.blockPos, nbt);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ShrineWorldgen.ENTITY_PROCESSOR.get();
    }

    private ShrineEntityProcessor() { }
}

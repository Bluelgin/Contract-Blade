package com.maidweapon.forge.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * 结构部件 — 放置一个 .nbt 模板。
 */
public class MaidWeaponStructurePiece extends TemplateStructurePiece {

    public MaidWeaponStructurePiece(
            StructureTemplateManager manager,
            ResourceLocation templateName,
            BlockPos pos) {
        super(ModStructures.MAID_WEAPON_PIECE.get(), 0, manager, templateName,
                templateName.toString(), makeSettings(), pos);
    }

    public MaidWeaponStructurePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(ModStructures.MAID_WEAPON_PIECE.get(), tag, manager, (name) -> makeSettings());
    }

    private static StructurePlaceSettings makeSettings() {
        return new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level,
                                     RandomSource random, BoundingBox box) {
        // 数据标记处理 — 可在 .nbt 中放结构虚空标记来触发自定义逻辑
        // 例如：在标记位置生成女仆、放置战利品箱等
    }
}

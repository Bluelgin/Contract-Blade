package com.maidweapon.forge.world.structure;

import com.maidweapon.common.MaidWeaponConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, MaidWeaponConstants.MOD_ID);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, MaidWeaponConstants.MOD_ID);

    /** 水上鸟居 */
    public static final RegistryObject<StructureType<ToriiGateStructure>> TORII_GATE =
            STRUCTURE_TYPES.register("torii_gate",
                    () -> () -> ToriiGateStructure.CODEC);

    /** 剑圣遗迹 */
    public static final RegistryObject<StructureType<KenseiStructure>> KENSEI =
            STRUCTURE_TYPES.register("kensei",
                    () -> () -> KenseiStructure.CODEC);

    /** 神树 */
    public static final RegistryObject<StructureType<SacredTreeStructure>> SACRED_TREE =
            STRUCTURE_TYPES.register("sacred_tree",
                    () -> () -> SacredTreeStructure.CODEC);

    /** 药房 */
    public static final RegistryObject<StructureType<YakkyokuStructure>> YAKKYOKU =
            STRUCTURE_TYPES.register("yakkyoku",
                    () -> () -> YakkyokuStructure.CODEC);

    /** 注入房屋到针叶林村庄（造桥加载时调用） */
    public static void injectVillageHouse() {
        // ponytail: 暂用独立生成；如需挂载村庄池需覆写
        // data/minecraft/worldgen/template_pool/village/taiga/houses.json
        // 并在末尾追加：
        //   {"weight":5,"element":{"element_type":"minecraft:single_pool_element",
        //    "projection":"rigid","location":"maid_weapon:yakkyoku","processors":"minecraft:empty"}}
        // 但这种方法需要包含全部原版房屋，否则村庄会缺失。
    }

    /** 通用结构部件 */
    public static final RegistryObject<StructurePieceType> MAID_WEAPON_PIECE =
            STRUCTURE_PIECES.register("maid_weapon_piece",
                    () -> (StructurePieceType) (StructurePieceSerializationContext ctx, CompoundTag tag) ->
                            new MaidWeaponStructurePiece(ctx.structureTemplateManager(), tag));
}

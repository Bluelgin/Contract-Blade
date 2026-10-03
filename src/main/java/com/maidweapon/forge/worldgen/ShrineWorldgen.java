package com.maidweapon.forge.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Registry-only bootstrap; optional content is gated by the structure itself. */
public final class ShrineWorldgen {
    private static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, "maid_weapon");
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, "maid_weapon");
    private static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, "maid_weapon");
    public static final RegistryObject<StructureProcessorType<ShrineEntityProcessor>> ENTITY_PROCESSOR =
            PROCESSORS.register("shrine_entity", () -> () -> ShrineEntityProcessor.CODEC);
    public static final RegistryObject<StructureType<ShinkitsuShrineStructure>> STRUCTURE =
            TYPES.register("shinkitsu_shrine", () -> () -> ShinkitsuShrineStructure.CODEC);
    public static final RegistryObject<StructurePieceType> PIECE = PIECES.register(
            "shinkitsu_shrine", () -> (StructurePieceType.StructureTemplateType)
                    ShinkitsuShrinePiece::new);

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        PIECES.register(bus);
        PROCESSORS.register(bus);
    }

    private ShrineWorldgen() { }
}

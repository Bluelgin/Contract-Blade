package com.maidweapon.port.init;

import com.maidweapon.port.ContractBladeMod;
import com.maidweapon.port.storage.ContractReference;
import com.maidweapon.port.storage.ContractChannels;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Native formal contract components and entity linkage; snapshots remain server-owned. */
public final class PortDataTypes {
    public static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(ContractBladeMod.ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ContractReference>> CONTRACT_REFERENCE =
        COMPONENTS.registerComponentType("contract_reference", builder -> builder
            .persistent(ContractReference.CODEC).networkSynchronized(ContractReference.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ContractChannels>> CONTRACT_CHANNELS =
        COMPONENTS.registerComponentType("contract_channels", builder -> builder
            .persistent(ContractChannels.CODEC).networkSynchronized(ContractChannels.STREAM_CODEC));

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ContractBladeMod.ID);
    // Access only after hasData; the default is never valid authority.
    public static final Supplier<AttachmentType<UUID>> MAID_BINDING =
        ATTACHMENTS.register("maid_binding", () -> AttachmentType
            .builder(() -> new UUID(0, 0)).serialize(UUIDUtil.CODEC).build());

    private PortDataTypes() {}
}

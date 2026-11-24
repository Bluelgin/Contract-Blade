package com.maidweapon.port.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

/** A small immutable handle, not authority and never a maid snapshot.
 * A new ticket is issued on every capture, invalidating old copied carriers.
 */
public record ContractReference(UUID maid, UUID owner, UUID binding, UUID ticket) {
    public ContractReference {
        Objects.requireNonNull(maid);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(binding);
        Objects.requireNonNull(ticket);
    }

    public static final Codec<ContractReference> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("maid").forGetter(ContractReference::maid),
        UUIDUtil.CODEC.fieldOf("owner").forGetter(ContractReference::owner),
        UUIDUtil.CODEC.fieldOf("binding").forGetter(ContractReference::binding),
        UUIDUtil.CODEC.fieldOf("ticket").forGetter(ContractReference::ticket)
    ).apply(i, ContractReference::new));

    public static final StreamCodec<ByteBuf, ContractReference> STREAM_CODEC = StreamCodec.of(
        (buf, ref) -> {
            writeUuid(buf, ref.maid);
            writeUuid(buf, ref.owner);
            writeUuid(buf, ref.binding);
            writeUuid(buf, ref.ticket);
        },
        buf -> new ContractReference(readUuid(buf), readUuid(buf), readUuid(buf), readUuid(buf))
    );

    private static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }
    private static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }
}

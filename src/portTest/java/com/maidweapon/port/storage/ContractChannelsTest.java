package com.maidweapon.port.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.NbtOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class ContractChannelsTest {
    private final UUID owner=UUID.randomUUID();
    private ContractReference ref(){return new ContractReference(UUID.randomUUID(),owner,UUID.randomUUID(),UUID.randomUUID());}
    @Test void immutableSelectionAndCodecs() {
        var external=ref(); var spirit=ref(); var id=ResourceLocation.fromNamespaceAndPath("test","spirit");
        var channels=new ContractChannels(Optional.of(external),List.of(new ContractChannels.Intrinsic(id,spirit)),Optional.empty());
        var selected=channels.select(Optional.of(id));
        assertEquals(external,channels.selected().orElseThrow());
        assertEquals(spirit,selected.selected().orElseThrow());
        assertEquals(channels.intrinsic(),selected.intrinsic());
        var tag=ContractChannels.CODEC.encodeStart(NbtOps.INSTANCE,selected).result().orElseThrow();
        assertEquals(selected,ContractChannels.CODEC.parse(NbtOps.INSTANCE,tag).result().orElseThrow());
        var buf=Unpooled.buffer();
        try {
            ContractChannels.STREAM_CODEC.encode(buf,selected);
            assertEquals(selected,ContractChannels.STREAM_CODEC.decode(buf)); assertEquals(0,buf.readableBytes());
        }finally{buf.release();}
    }
    @Test void malformedOrAmbiguousChannelsRejected() {
        var external=ref(); var id=ResourceLocation.fromNamespaceAndPath("test","spirit");
        var entry=new ContractChannels.Intrinsic(id,ref());
        assertThrows(IllegalArgumentException.class,()->new ContractChannels(Optional.of(external),List.of(entry,entry),Optional.empty()));
        assertThrows(IllegalArgumentException.class,()->new ContractChannels(Optional.of(external),List.of(new ContractChannels.Intrinsic(id,external)),Optional.empty()));
        assertThrows(IllegalArgumentException.class,()->new ContractChannels(Optional.of(external),List.of(),Optional.of(id)));
        var foreign=new ContractReference(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,()->new ContractChannels(Optional.of(external),List.of(new ContractChannels.Intrinsic(id,foreign)),Optional.empty()));
        var buf=Unpooled.buffer();
        try {buf.writeBoolean(false);buf.writeByte(65);assertThrows(IllegalArgumentException.class,()->ContractChannels.STREAM_CODEC.decode(buf));}
        finally{buf.release();}
    }
}

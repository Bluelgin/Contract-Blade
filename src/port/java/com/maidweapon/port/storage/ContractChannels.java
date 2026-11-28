package com.maidweapon.port.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable sealed-channel component. External is the ordinary contract;
 * intrinsic IDs are addon-owned namespaced IDs. Selection is a pointer, not
 * copying snapshots to an active NBT root. NOT a live-deployment authority.
 */
public record ContractChannels(Optional<ContractReference> external, List<Intrinsic> intrinsic,
                               Optional<ResourceLocation> selectedIntrinsic) {
    public static final int MAX_INTRINSIC=64;
    public record Intrinsic(ResourceLocation id,ContractReference reference) {
        public Intrinsic { Objects.requireNonNull(id); Objects.requireNonNull(reference); }
        public static final Codec<Intrinsic> CODEC=RecordCodecBuilder.create(i->i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Intrinsic::id),
            ContractReference.CODEC.fieldOf("reference").forGetter(Intrinsic::reference)
        ).apply(i,Intrinsic::new));
    }
    public ContractChannels {
        Objects.requireNonNull(external); Objects.requireNonNull(selectedIntrinsic);
        intrinsic=List.copyOf(intrinsic);
        if(intrinsic.size()>MAX_INTRINSIC) throw new IllegalArgumentException("Too many intrinsic channels");
        var ids=new HashSet<ResourceLocation>();
        var maids=new HashSet<UUID>();
        var bindings=new HashSet<UUID>();
        UUID owner=external.map(ContractReference::owner).orElse(null);
        if(external.isPresent()) {
            maids.add(external.get().maid()); bindings.add(external.get().binding());
        }
        for(Intrinsic channel:intrinsic) {
            var ref=channel.reference();
            if(owner==null) owner=ref.owner();
            if(!owner.equals(ref.owner()) || !ids.add(channel.id()) || !maids.add(ref.maid()) || !bindings.add(ref.binding()))
                throw new IllegalArgumentException("Mixed owner or duplicate channel identity");
        }
        if(selectedIntrinsic.isPresent() && !ids.contains(selectedIntrinsic.get()))
            throw new IllegalArgumentException("Selected intrinsic does not exist");
    }
    private record Raw(Optional<ContractReference> external,List<Intrinsic> intrinsic,Optional<ResourceLocation> selected) {}
    private static final Codec<Raw> RAW=RecordCodecBuilder.create(i->i.group(
        ContractReference.CODEC.optionalFieldOf("external").forGetter(Raw::external),
        Intrinsic.CODEC.listOf().fieldOf("intrinsic").forGetter(Raw::intrinsic),
        ResourceLocation.CODEC.optionalFieldOf("selected_intrinsic").forGetter(Raw::selected)
    ).apply(i,Raw::new));
    public static final Codec<ContractChannels> CODEC=RAW.flatXmap(raw->{
        try {return DataResult.success(new ContractChannels(raw.external(),raw.intrinsic(),raw.selected()));}
        catch(IllegalArgumentException invalid){return DataResult.error(invalid::getMessage);}
    },value->DataResult.success(new Raw(value.external(),value.intrinsic(),value.selectedIntrinsic())));
    public static final StreamCodec<ByteBuf,ContractChannels> STREAM_CODEC=StreamCodec.of((buf,value)->{
        buf.writeBoolean(value.external().isPresent());
        value.external().ifPresent(ref->ContractReference.STREAM_CODEC.encode(buf,ref));
        buf.writeByte(value.intrinsic().size());
        for(var c:value.intrinsic()) {
            ResourceLocation.STREAM_CODEC.encode(buf,c.id());
            ContractReference.STREAM_CODEC.encode(buf,c.reference());
        }
        buf.writeBoolean(value.selectedIntrinsic().isPresent());
        value.selectedIntrinsic().ifPresent(id->ResourceLocation.STREAM_CODEC.encode(buf,id));
    },buf->{
        Optional<ContractReference> external=buf.readBoolean()?Optional.of(ContractReference.STREAM_CODEC.decode(buf)):Optional.empty();
        int count=buf.readUnsignedByte();
        if(count>MAX_INTRINSIC) throw new IllegalArgumentException("Too many intrinsic channels");
        var channels=new java.util.ArrayList<Intrinsic>(count);
        for(int i=0;i<count;i++) channels.add(new Intrinsic(ResourceLocation.STREAM_CODEC.decode(buf),ContractReference.STREAM_CODEC.decode(buf)));
        Optional<ResourceLocation> selected=buf.readBoolean()?Optional.of(ResourceLocation.STREAM_CODEC.decode(buf)):Optional.empty();
        return new ContractChannels(external,channels,selected);
    });
    public Optional<ContractReference> selected() {
        if(selectedIntrinsic.isEmpty()) return external;
        return intrinsic.stream().filter(c->c.id().equals(selectedIntrinsic.get())).map(Intrinsic::reference).findFirst();
    }
    /** Rotate only the selected ticket after a successful recall. */
    public ContractChannels replaceSelected(ContractReference next) {
        var prior=selected().orElseThrow();
        if(!prior.maid().equals(next.maid()) || !prior.owner().equals(next.owner()) || !prior.binding().equals(next.binding()))
            throw new IllegalArgumentException("Cannot replace channel identity");
        if(selectedIntrinsic.isEmpty()) return new ContractChannels(Optional.of(next),intrinsic,selectedIntrinsic);
        return new ContractChannels(external,intrinsic.stream().map(c->c.id().equals(selectedIntrinsic.get())
            ?new Intrinsic(c.id(),next):c).toList(),selectedIntrinsic);
    }
    /** Only prepares a selection; caller must verify all refs STORED and recall any live deployment first. */
    public ContractChannels select(Optional<ResourceLocation> id) {
        return new ContractChannels(external,intrinsic,id);
    }
}

package com.maidweapon.port.network;

import com.maidweapon.port.PortProbeMod;
import com.maidweapon.port.domain.resonance.ResonanceRules;
import com.maidweapon.port.resonance.NativeResonanceService;
import com.maidweapon.port.storage.NativeChannelService;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Read-only server status, correlated by opaque selected ticket, never a client-supplied identity. */
public final class PortStatusNetwork {
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(PortProbeMod.ID,"status_request"));
        public static final StreamCodec<ByteBuf,Request> CODEC=StreamCodec.of((b,v)->{},b->new Request());
        @Override public Type<Request> type(){return TYPE;}
    }
    public record Status(boolean authorized,boolean manifested,int current,int maximum,UUID ticket) implements CustomPacketPayload {
        public static final Type<Status> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(PortProbeMod.ID,"status"));
        public Status {
            if(maximum<1 || maximum>1_000_000 || current<0 || current>maximum
                || authorized!=(ticket!=null) || (!authorized&&(current!=0||manifested)))
                throw new IllegalArgumentException("Invalid status");
        }
        public static final StreamCodec<ByteBuf,Status> CODEC=StreamCodec.of((b,v)->{
            b.writeBoolean(v.authorized);b.writeBoolean(v.manifested);b.writeInt(v.current);b.writeInt(v.maximum);
            if(v.authorized){b.writeLong(v.ticket.getMostSignificantBits());b.writeLong(v.ticket.getLeastSignificantBits());}
        },b->{
            boolean ok=b.readBoolean(),live=b.readBoolean();int current=b.readInt(),max=b.readInt();
            return new Status(ok,live,current,max,ok?new UUID(b.readLong(),b.readLong()):null);
        });
        @Override public Type<Status> type(){return TYPE;}
    }
    private record Cached(Status status,long receivedNanos){}
    private static final Map<ServerPlayer,Long> LAST_REQUEST=new WeakHashMap<>();
    private static final Map<Player,Cached> CLIENT_CACHE=new WeakHashMap<>();
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("2");
        registrar.playToServer(Request.TYPE,Request.CODEC,PortStatusNetwork::request);
        registrar.playToClient(Status.TYPE,Status.CODEC,PortStatusNetwork::receive);
    }
    private static void request(Request ignored,IPayloadContext context) {
        if(!(context.player() instanceof ServerPlayer player)||player.getServer()==null||!player.getServer().isSameThread())return;
        var server=player.getServer();
        long now=server.overworld().getGameTime();
        Long last=LAST_REQUEST.get(player);
        if(last!=null&&now>=last&&now-last<10)return;
        LAST_REQUEST.put(player,now);
        var stack=player.getMainHandItem();var owner=player.getUUID();
        int current=NativeResonanceService.currentStored(server,owner,stack);
        var live=current<0?NativeChannelService.selectedLive(server,owner,stack):null;
        if(live!=null)current=NativeResonanceService.currentLive(server,owner,live);
        var ref=NativeChannelService.displayReference(stack);
        boolean ok=current>=0&&ref!=null;
        context.reply(new Status(ok,ok&&live!=null,ok?current:0,ResonanceRules.DEFAULT.maximum(),ok?ref.ticket():null));
    }
    private static void receive(Status status,IPayloadContext context) {
        Player player=context.player();
        if(player.level().isClientSide)synchronized(CLIENT_CACHE){CLIENT_CACHE.put(player,new Cached(status,System.nanoTime()));}
    }
    public static Status cached(Player player) {
        synchronized(CLIENT_CACHE){
            var c=CLIENT_CACHE.get(player);
            return c==null||System.nanoTime()-c.receivedNanos()>3_000_000_000L?null:c.status();
        }
    }
    private PortStatusNetwork(){}
}

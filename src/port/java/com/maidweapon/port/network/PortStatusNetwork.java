package com.maidweapon.port.network;

import com.maidweapon.port.PortProbeMod;
import com.maidweapon.port.domain.resonance.ResonanceRules;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.resonance.NativeResonanceService;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Read-only, request/reply status. No client-provided UUID, cost, snapshot or target.
 * No Minecraft client classes loaded by a dedicated server. Cache is per actual client player object.
 */
public final class PortStatusNetwork {
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(PortProbeMod.ID,"status_request"));
        public static final StreamCodec<ByteBuf,Request> CODEC=StreamCodec.of((b,v)->{},b->new Request());
        @Override public Type<Request> type(){return TYPE;}
    }
    public record Status(boolean authorized,int current,int maximum) implements CustomPacketPayload {
        public static final Type<Status> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(PortProbeMod.ID,"status"));
        public Status {
            if(maximum<1 || maximum>1_000_000 || current<0 || current>maximum || (!authorized && current!=0))
                throw new IllegalArgumentException("Invalid status");
        }
        public static final StreamCodec<ByteBuf,Status> CODEC=StreamCodec.of(
            (b,v)->{b.writeBoolean(v.authorized);b.writeInt(v.current);b.writeInt(v.maximum);},
            b->new Status(b.readBoolean(),b.readInt(),b.readInt()));
        @Override public Type<Status> type(){return TYPE;}
    }
    private static final Map<ServerPlayer,Long> LAST_REQUEST=new WeakHashMap<>();
    private static final Map<Player,Status> CLIENT_CACHE=new WeakHashMap<>();
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("1");
        registrar.playToServer(Request.TYPE,Request.CODEC,PortStatusNetwork::request);
        registrar.playToClient(Status.TYPE,Status.CODEC,PortStatusNetwork::receive);
    }
    private static void request(Request ignored,IPayloadContext context) {
        if(!(context.player() instanceof ServerPlayer player) || player.getServer()==null || !player.getServer().isSameThread()) return;
        long now=player.getServer().overworld().getGameTime();
        Long last=LAST_REQUEST.get(player);
        if(last!=null && now>=last && now-last<10) return;
        LAST_REQUEST.put(player,now);
        int current=NativeResonanceService.currentStored(player.getServer(),player.getUUID(),player.getMainHandItem());
        context.reply(new Status(current>=0,Math.max(0,current),ResonanceRules.DEFAULT.maximum()));
    }
    private static void receive(Status status,IPayloadContext context) {
        Player player=context.player();
        if(player.level().isClientSide) synchronized(CLIENT_CACHE){CLIENT_CACHE.put(player,status);}
    }
    public static Status cached(Player player) {
        synchronized(CLIENT_CACHE){return CLIENT_CACHE.get(player);}
    }
    private PortStatusNetwork(){}
}

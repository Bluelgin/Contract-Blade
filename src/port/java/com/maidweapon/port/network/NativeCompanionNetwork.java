package com.maidweapon.port.network;

import com.maidweapon.port.ContractBladeMod;
import com.maidweapon.port.companion.NativeCompanionService;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

/** Same intent-only call and pre-enqueue flood gate as the maintained Forge network. */
public final class NativeCompanionNetwork {
    public record Call() implements CustomPacketPayload {
        public static final Type<Call> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ContractBladeMod.ID,"companion_call"));
        public static final StreamCodec<ByteBuf,Call> CODEC=StreamCodec.of((buffer,value)->{},buffer->new Call());
        @Override public Type<Call> type(){return TYPE;}
    }
    private static final Map<ServerPlayer,Long> LAST_CALL=new WeakHashMap<>();
    private static synchronized boolean accept(ServerPlayer player){
        long now=System.nanoTime();Long previous=LAST_CALL.get(player);
        if(previous!=null&&now-previous<100_000_000L)return false;
        LAST_CALL.put(player,now);return true;
    }
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("2").executesOn(HandlerThread.NETWORK).playToServer(Call.TYPE,Call.CODEC,(packet,context)->{
            if(context.player() instanceof ServerPlayer player&&accept(player))
                context.enqueueWork(()->NativeCompanionService.toggle(player));
        });
    }
    private NativeCompanionNetwork(){}
}

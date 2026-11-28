package com.maidweapon.port.client;

import com.maidweapon.port.ContractBladeMod;
import com.maidweapon.port.network.PortStatusNetwork;
import com.maidweapon.port.storage.NativeChannelService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Physical-client-only display. Does not calculate authority or predict balances. */
@EventBusSubscriber(modid=ContractBladeMod.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.GAME)
public final class PortResonanceHud {
    private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.getConnection()==null)return;
        if(++ticks%10==0&&NativeChannelService.displayReference(mc.player.getMainHandItem())!=null)
            PacketDistributor.sendToServer(new PortStatusNetwork.Request());
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.options.hideGui||mc.screen!=null)return;
        var ref=NativeChannelService.displayReference(mc.player.getMainHandItem());
        var s=PortStatusNetwork.cached(mc.player);
        if(ref==null||s==null||!s.authorized()||!ref.ticket().equals(s.ticket()))return;
        var g=event.getGuiGraphics();int w=110,x=(g.guiWidth()-w)/2,y=g.guiHeight()-69;
        g.fill(x-1,y-1,x+w+1,y+7,0xB0000000);
        g.fill(x,y,x+w,y+6,0xFF2B2635);
        g.fill(x,y,x+(int)((long)w*s.current()/s.maximum()),y+6,0xFFAB76E7);
        g.drawCenteredString(mc.font,Component.translatable(s.manifested()?"maid_weapon.native.hud.live":"maid_weapon.native.hud.stored",
            s.current(),s.maximum()),x+w/2,y-11,0xFFFFFFFF);
    }
}

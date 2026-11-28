package com.maidweapon.forge.compat.fox;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.init.ModItems;
import com.github.tartaricacid.touhoulittlemaid.client.resource.models.MaidModels;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.*;

/** Isolated visual experiment. Copy only into a throwaway checkout; NEVER ship this overlay. */
@Mod.EventBusSubscriber(modid=MaidWeaponConstants.MOD_ID,value=Dist.CLIENT)
public final class ForgeVisualAudit {
    static boolean logged;
    static int worldFrames;
    static boolean reloadStarted;
    static EntityMaid white,black;
    @SubscribeEvent public static void screen(ScreenEvent.Render.Post e){draw(e.getGuiGraphics());}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){if(Minecraft.getInstance().screen==null)draw(e.getGuiGraphics());}
    static void draw(GuiGraphics g){
        var mc=Minecraft.getInstance();
        g.fill(4,4,g.guiWidth()-4,178,0xED101020);
        g.drawString(mc.font,"AUDIT PROTOTYPE - original assets, no production gameplay",12,12,0xFFFFFF);
        g.drawString(mc.font,"Native animated item",12,30,0xFFFFFF);
        g.pose().pushPose();g.pose().translate(24,48,0);g.pose().scale(4,4,4);
        g.renderItem(new ItemStack(ModItems.AWAKENING_VEIN.get()),0,0);g.pose().popPose();
        g.drawString(mc.font,"Original Manka wheel",152,30,0xFFFFFF);
        g.blit(ResourceLocation.parse("maid_weapon:textures/gui/danmaku/wheel_base.png"),152,45,0,0,96,96,96,96);
        g.drawString(mc.font,"Original GLSL / native 1.20 buffer",290,30,0xFFFFFF);
        if(BlackFoxEnergyShader.ready()){
            g.flush();
            var buffers=g.bufferSource();var type=BlackFoxEnergyShader.type(false);
            var out=buffers.getBuffer(type);var matrix=g.pose().last().pose();int age=(int)(System.nanoTime()/500000L%10000);
            out.vertex(matrix,300,45,0).color(255,255,255,255).uv(0,0).uv2(age,0).endVertex();
            out.vertex(matrix,300,145,0).color(255,255,255,255).uv(0,1).uv2(age,0).endVertex();
            out.vertex(matrix,440,145,0).color(255,255,255,255).uv(1,1).uv2(age,0).endVertex();
            out.vertex(matrix,440,45,0).color(255,255,255,255).uv(1,0).uv2(age,0).endVertex();
            buffers.endBatch(type);
        }
        var ids=MaidModels.getInstance().getModelIdSet();
        boolean w=ids.contains("contract_fox:fox_white"),b=ids.contains("contract_fox:fox_black");
        g.drawString(mc.font,"TLM IDs: white="+w+" black="+b+" shader="+BlackFoxEnergyShader.ready(),12,155,0xFFFFFF);
        if(!logged){
            System.out.println("VISUAL_AUDIT_MODEL_IDS "+ids);
            System.out.println("VISUAL_AUDIT_SHADER_READY "+BlackFoxEnergyShader.ready());
            for(var registry:net.minecraft.core.registries.BuiltInRegistries.REGISTRY){
                var keys=registry.keySet().stream().filter(k->k.getNamespace().equals("maid_weapon")).toList();
                if(!keys.isEmpty())System.out.println("VISUAL_AUDIT_REGISTRY "+registry.key().location()+" "+keys);
            }
            logged=true;
        }
        if(mc.level!=null){
            if(white==null||white.level()!=mc.level){
                white=new EntityMaid(mc.level);white.setModelId("contract_fox:fox_white");
                black=new EntityMaid(mc.level);black.setModelId("contract_fox:fox_black");
                System.out.println("VISUAL_AUDIT_WORLD_READY");
                if(mc.player!=null&&mc.getSingleplayerServer()!=null){
                    var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();
                    server.execute(()->{var p=server.getPlayerList().getPlayer(owner);if(p!=null)p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);});
                }
            }
            if(++worldFrames>=400&&!reloadStarted){
                reloadStarted=true;System.out.println("VISUAL_AUDIT_RELOAD_START");
                mc.reloadResourcePacks().thenRun(()->System.out.println("VISUAL_AUDIT_RELOAD_COMPLETE"));
            }
            white.tickCount=black.tickCount=(int)(System.nanoTime()/50000000L%1000000);
            white.walkAnimation.update(.35f,.4f);black.walkAnimation.update(.35f,.4f);
            InventoryScreen.renderEntityInInventoryFollowsAngle(g,95,g.guiHeight()-25,70,0f,0f,white);
            InventoryScreen.renderEntityInInventoryFollowsAngle(g,265,g.guiHeight()-25,70,0f,0f,black);
        }else g.drawString(mc.font,"Model render requires an actual loaded world",12,185,0xFFFFFF);
    }
}

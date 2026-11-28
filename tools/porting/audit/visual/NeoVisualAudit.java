package com.maidweapon.port.audit;

import com.maidweapon.port.PortProbeMod;
import com.maidweapon.port.init.ProbeItems;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Isolated visual experiment. Copy only into a throwaway checkout; NEVER ship this overlay. */
@EventBusSubscriber(modid=PortProbeMod.ID,value=Dist.CLIENT)
public final class NeoVisualAudit {
    static ShaderInstance energy;
    static boolean logged;
    static int worldFrames;
    static boolean reloadStarted;
    static EntityMaid white,black;
    @EventBusSubscriber(modid=PortProbeMod.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent public static void shaders(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.parse("maid_weapon:black_fox_energy"),
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),s->{energy=s;System.out.println("VISUAL_AUDIT_SHADER_LINKED "+s.getName());});
        }
    }
    @SubscribeEvent public static void screen(ScreenEvent.Render.Post e){draw(e.getGuiGraphics());}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){if(Minecraft.getInstance().screen==null)draw(e.getGuiGraphics());}
    static void draw(GuiGraphics g){
        var mc=Minecraft.getInstance();
        g.fill(4,4,g.guiWidth()-4,178,0xED101020);
        g.drawString(mc.font,"AUDIT PROTOTYPE - original assets, no production gameplay",12,12,0xFFFFFF);
        g.drawString(mc.font,"Native animated item",12,30,0xFFFFFF);
        g.pose().pushPose();g.pose().translate(24,48,0);g.pose().scale(4,4,4);
        g.renderItem(new ItemStack(ProbeItems.VEIN_ONE.get()),0,0);g.pose().popPose();
        g.drawString(mc.font,"Original Manka wheel",152,30,0xFFFFFF);
        g.blit(ResourceLocation.parse("maid_weapon:textures/gui/danmaku/wheel_base.png"),152,45,0,0,96,96,96,96);
        g.drawString(mc.font,"Original GLSL / native 1.21 buffer",290,30,0xFFFFFF);
        if(energy!=null){
            g.flush();RenderSystem.enableBlend();RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,org.lwjgl.opengl.GL11.GL_ONE);
            RenderSystem.disableDepthTest();RenderSystem.setShader(()->energy);
            RenderSystem.setShaderTexture(0,ResourceLocation.parse("maid_weapon:textures/effect/black_fox_rift_energy.png"));
            var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            var matrix=g.pose().last().pose();int age=(int)((System.nanoTime()/500000L)%1000000);
            b.addVertex(matrix,300,45,0).setColor(255,255,255,255).setUv(0,0).setUv2(age,0);
            b.addVertex(matrix,300,145,0).setColor(255,255,255,255).setUv(0,1).setUv2(age,0);
            b.addVertex(matrix,440,145,0).setColor(255,255,255,255).setUv(1,1).setUv2(age,0);
            b.addVertex(matrix,440,45,0).setColor(255,255,255,255).setUv(1,0).setUv2(age,0);
            BufferUploader.drawWithShader(b.buildOrThrow());
            RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();RenderSystem.enableDepthTest();
        }
        var ids=MaidModels.getInstance().getModelIdSet();
        boolean w=ids.contains("contract_fox:fox_white"),b=ids.contains("contract_fox:fox_black");
        g.drawString(mc.font,"TLM IDs: white="+w+" black="+b+" shader="+(energy!=null),12,155,0xFFFFFF);
        if(!logged){System.out.println("VISUAL_AUDIT_MODEL_IDS "+ids);logged=true;}
        if(mc.level!=null){
            if(white==null||white.level()!=mc.level){
                white=new EntityMaid(mc.level);white.setModelId("contract_fox:fox_white");
                black=new EntityMaid(mc.level);black.setModelId("contract_fox:fox_black");
                System.out.println("VISUAL_AUDIT_WORLD_READY");
            }
            if(++worldFrames>=400&&!reloadStarted){
                reloadStarted=true;System.out.println("VISUAL_AUDIT_RELOAD_START");
                mc.reloadResourcePacks().thenRun(()->System.out.println("VISUAL_AUDIT_RELOAD_COMPLETE"));
            }
            white.tickCount=black.tickCount=(int)(System.nanoTime()/50000000L%1000000);
            white.walkAnimation.update(.35f,.4f);black.walkAnimation.update(.35f,.4f);
            InventoryScreen.renderEntityInInventoryFollowsAngle(g,20,180,170,g.guiHeight()-10,70,0f,0f,0f,white);
            InventoryScreen.renderEntityInInventoryFollowsAngle(g,190,180,340,g.guiHeight()-10,70,0f,0f,0f,black);
        }else g.drawString(mc.font,"Model render requires an actual loaded world",12,185,0xFFFFFF);
    }
}

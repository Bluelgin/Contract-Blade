package com.maidweapon.port.client;
import com.maidweapon.port.ContractBladeMod;
import com.maidweapon.port.network.PortStatusNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
@EventBusSubscriber(modid=ContractBladeMod.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class PortOathKeys{
    private static final KeyMapping TOGGLE=new KeyMapping("key.maid_weapon.oath",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_O,"key.categories.maid_weapon");
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent e){e.register(TOGGLE);}
    @EventBusSubscriber(modid=ContractBladeMod.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.GAME)
    public static final class Input{
        @SubscribeEvent public static void tick(ClientTickEvent.Post e){
            var mc=Minecraft.getInstance();
            while(TOGGLE.consumeClick()){
                if(mc.player!=null&&mc.getConnection()!=null&&mc.screen==null)
                    PacketDistributor.sendToServer(new PortStatusNetwork.OathToggle());
            }
        }
    }
}

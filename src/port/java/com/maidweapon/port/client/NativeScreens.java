package com.maidweapon.port.client;
import com.maidweapon.port.ContractBladeMod;
import com.maidweapon.port.content.NativeContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
@EventBusSubscriber(modid=ContractBladeMod.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class NativeScreens {
 @SubscribeEvent public static void register(RegisterMenuScreensEvent event){event.register(NativeContent.INJECTOR_MENU.get(),NativeMaidInjectorScreen::new);}
}

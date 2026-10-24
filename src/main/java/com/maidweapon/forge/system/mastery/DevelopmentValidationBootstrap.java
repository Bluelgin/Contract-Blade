package com.maidweapon.forge.system.mastery;

/** Development source set only; production bootstrap never links fixture classes. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "maid_weapon",
        bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class DevelopmentValidationBootstrap {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(ContractMasteryValidation::registerAbilities);
    }
    private DevelopmentValidationBootstrap() { }
}

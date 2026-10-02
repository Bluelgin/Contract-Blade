package com.maidweapon.forge.event;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.system.deployment.ContractCompanionService;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID)
public final class ContractCompanionEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0) return;
        if (event.getEntity() instanceof Player owner)
            ContractCompanionService.attacked(owner, event.getSource().getEntity(), event.getAmount());
        if (event.getSource().getEntity() instanceof Player owner) ContractCompanionService.prolongGuard(owner);
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ContractCompanionService.clearSession();
        com.maidweapon.forge.system.InfusedMaidDeploymentSystem.clearSession();
    }
    private ContractCompanionEvents() { }
}

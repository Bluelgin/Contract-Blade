package com.maidweapon.forge.mixin;

import com.maidweapon.forge.compat.tlm.TlmEntityAdapter;
import com.maidweapon.forge.system.contract.ContractRecoveryJournal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Persist a UUID's real world presence only after the server actually accepts its spawn. */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class ContractEntityPresenceMixin {
    @Inject(method = {"addEntity(Lnet/minecraft/world/entity/Entity;)Z", "m_8872_(Lnet/minecraft/world/entity/Entity;)Z"},
            at = @At("RETURN"), require = 1)
    private void contractblade$added(Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ() && TlmEntityAdapter.isMaidEntity(entity))
            ContractRecoveryJournal.get(entity.getServer()).markMaidPresent(entity.getStringUUID());
    }
}

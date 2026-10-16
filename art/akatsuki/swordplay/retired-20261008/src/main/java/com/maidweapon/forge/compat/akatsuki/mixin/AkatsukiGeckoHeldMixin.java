package com.maidweapon.forge.compat.akatsuki.mixin;

import com.maidweapon.forge.compat.akatsuki.AkatsukiSwordplay;
import com.maidweapon.forge.compat.akatsuki.AkatsukiSwordVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidHeld",remap=false)
public abstract class AkatsukiGeckoHeldMixin {
    private static final String RENDER="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Mob;FFFFFF)V";
    // TLM 1.5.3 stores offhand first, mainhand second. Replace only that local rendering input.
    @ModifyVariable(method=RENDER,at=@At("STORE"),ordinal=1,require=1)
    private ItemStack contractblade$visualOnly(ItemStack held, PoseStack stack, MultiBufferSource buffers, int light,
            Mob entity,float limbSwing,float limbAmount,float partial,float age,float yaw,float pitch) {
        return AkatsukiSwordplay.replaces(entity)?ItemStack.EMPTY:held;
    }
    @Inject(method=RENDER,at=@At("TAIL"),require=1)
    private void contractblade$katana(PoseStack stack,MultiBufferSource buffers,int light,Mob entity,
            float limbSwing,float limbAmount,float partial,float age,float yaw,float pitch,CallbackInfo ci) {
        AkatsukiSwordVisual.render(entity,partial,stack,buffers,light);
    }
}

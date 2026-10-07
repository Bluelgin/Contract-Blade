package com.maidweapon.forge.mixin;

import com.maidweapon.forge.system.deployment.ContractDurabilityBridge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Group;
import java.util.function.Consumer;

@Mixin(value = ItemStack.class, remap = false)
public abstract class ContractDurabilityMixin {
    @Group(name = "contractblade$durability", min = 1, max = 1)
    @Redirect(method = {"hurtAndBreak", "m_41622_"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), require = 0)
    private void contractblade$consume(ItemStack stack, int count, int damage,
                                      LivingEntity user, Consumer<LivingEntity> callback) {
        ContractDurabilityBridge.consume(stack, count, user);
    }

    @Group(name = "contractblade$durability", min = 1, max = 1)
    @Redirect(method = {"hurtAndBreak", "m_41622_"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;m_41774_(I)V"), require = 0)
    private void contractblade$consumeSrg(ItemStack stack, int count, int damage,
                                         LivingEntity user, Consumer<LivingEntity> callback) {
        ContractDurabilityBridge.consume(stack, count, user);
    }
}

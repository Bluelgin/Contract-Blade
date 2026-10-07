package com.maidweapon.forge.compat.fox.mixin;

import com.maidweapon.forge.system.deployment.ContractDurabilityBridge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Group;
import java.util.function.Consumer;

/** SlashBlade consumes destructible blades inside its own damageItem implementation. */
@Pseudo
@Mixin(targets = "mods.flammpfeil.slashblade.item.ItemSlashBlade", remap = false)
public abstract class SlashBladeDurabilityMixin {
    @Group(name = "contractblade$slashDurability", min = 1, max = 1)
    @Redirect(method = "damageItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), require = 0)
    private void contractblade$consume(ItemStack stack, int count, ItemStack original, int damage,
                                      LivingEntity user, Consumer<LivingEntity> callback) {
        ContractDurabilityBridge.consume(stack, count, user);
    }

    @Group(name = "contractblade$slashDurability", min = 1, max = 1)
    @Redirect(method = "damageItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;m_41774_(I)V"), require = 0)
    private void contractblade$consumeSrg(ItemStack stack, int count, ItemStack original, int damage,
                                         LivingEntity user, Consumer<LivingEntity> callback) {
        ContractDurabilityBridge.consume(stack, count, user);
    }
}

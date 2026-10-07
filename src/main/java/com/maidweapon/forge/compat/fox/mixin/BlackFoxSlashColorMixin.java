package com.maidweapon.forge.compat.fox.mixin;

import com.maidweapon.forge.compat.BlackFoxSlashColors;
import com.maidweapon.forge.entity.BlackFoxBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Restore Boss tint after native low-rank desaturation, without changing the entity's rank. */
@Pseudo
@Mixin(targets = "mods.flammpfeil.slashblade.client.renderer.entity.SlashEffectRenderer", remap = false)
public abstract class BlackFoxSlashColorMixin {
    private static final String DRAW = "render(Lmods/flammpfeil/slashblade/entity/EntitySlashEffect;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    @ModifyConstant(method = DRAW, constant = @Constant(intValue = 0x555555))
    private int contractBlade$bossTint(int original, @Coerce Entity slash, float yaw, float partial,
                                      PoseStack stack, MultiBufferSource buffers, int light) {
        return slash instanceof Projectile projectile && projectile.getOwner() instanceof BlackFoxBossEntity boss
                ? BlackFoxSlashColors.blade(boss.phaseTwo()) : original;
    }
    @ModifyConstant(method = DRAW, constant = @Constant(intValue = 0x404040))
    private int contractBlade$bossRim(int original, @Coerce Entity slash, float yaw, float partial,
                                     PoseStack stack, MultiBufferSource buffers, int light) {
        return slash instanceof Projectile projectile && projectile.getOwner() instanceof BlackFoxBossEntity boss
                ? BlackFoxSlashColors.rim(boss.phaseTwo()) : original;
    }
}

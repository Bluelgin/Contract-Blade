package com.maidweapon.forge.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Ownerless native visuals: the encounter, never the visual projectile, owns damage. */
public final class BlackFoxJudgementCompat {
    public static Entity visual(Level level, Vec3 position) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse("slashblade:judgement_cut")).orElseThrow();
        try {
            Entity visual = type.create(level);
            if (visual == null) throw new IllegalStateException("Cannot create native judgement cut");
            visual.setPos(position);
            visual.getClass().getMethod("setColor", int.class).invoke(visual, 0xB04CE5);
            return visual;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Judgement cut visual API", error); }
    }
    public static void purpleBlade(net.minecraft.world.entity.LivingEntity boss) {
        Object state = SlashBladeCompat.resolveBladeState(boss.getMainHandItem());
        try {
            Class.forName("mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState")
                    .getMethod("setColorCode", int.class).invoke(state, 0xB04CE5);
            boss.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, boss.getMainHandItem().copy());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Black Fox blade color", error); }
    }
    private BlackFoxJudgementCompat() { }
}

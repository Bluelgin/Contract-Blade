package com.maidweapon.forge.system.oath;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.*;
import java.util.List;

public final class OathEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "maid_weapon");
    public static final RegistryObject<MobEffect> BACKLASH = EFFECTS.register("oath_backlash", Backlash::new);
    private static final class Backlash extends MobEffect {
        Backlash() { super(MobEffectCategory.HARMFUL, 0xA35D42); }
        @Override public List<ItemStack> getCurativeItems() { return List.of(); }
    }
    private OathEffects() { }
}

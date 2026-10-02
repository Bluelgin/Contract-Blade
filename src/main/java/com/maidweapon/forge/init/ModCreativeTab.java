package com.maidweapon.forge.init;

import com.maidweapon.common.MaidWeaponConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MaidWeaponConstants.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = TABS.register(
            "main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MaidWeaponConstants.MOD_ID))
                    .icon(() -> new ItemStack(ModItems.MAID_SWORD.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.MAID_SWORD.get());
                        output.accept(ModItems.MAID_INJECTOR.get());
                        output.accept(ModItems.SPIRIT_CRYSTAL.get());
                        output.accept(ModItems.CONTRACT_INTERIOR_KEY.get());
                        output.accept(ModItems.RESONANCE_SWORD_TASSEL.get());
                        output.accept(ModItems.GUARDIAN_RIBBON.get());
                        output.accept(ModItems.HEARTBOUND_KNOT.get());
                        output.accept(ModItems.MAID_HEAVY.get());
                        output.accept(ModItems.MAID_FAST.get());
                        output.accept(ModItems.DOG_BLADE.get());
                        output.accept(ModItems.CAT_BLADE.get());
                    })
                    .build());

    private ModCreativeTab() {
    }
}

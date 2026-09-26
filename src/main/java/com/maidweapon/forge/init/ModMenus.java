package com.maidweapon.forge.init;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.menu.MaidInjectorMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MaidWeaponConstants.MOD_ID);

    public static final RegistryObject<MenuType<MaidInjectorMenu>> MAID_INJECTOR =
            MENUS.register("maid_injector", () -> IForgeMenuType.create(MaidInjectorMenu::new));

    private ModMenus() {
    }
}

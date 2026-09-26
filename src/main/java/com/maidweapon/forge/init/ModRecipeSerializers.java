package com.maidweapon.forge.init;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.recipe.MaidWeaponUpgradeRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 自定义配方序列化器注册表。 */
public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS,
                    MaidWeaponConstants.MOD_ID);

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final RegistryObject<RecipeSerializer<MaidWeaponUpgradeRecipe>>
            MAID_WEAPON_UPGRADE = (RegistryObject) RECIPE_SERIALIZERS.register(
                    "weapon_upgrade", MaidWeaponUpgradeRecipe.Serializer::new);

    private ModRecipeSerializers() {
    }
}

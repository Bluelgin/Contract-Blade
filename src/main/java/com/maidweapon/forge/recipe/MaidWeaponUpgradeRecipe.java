package com.maidweapon.forge.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * 女仆武器升级配方：把契约之刃合成为重刀/快刀时，将完整契约 NBT
 * （女仆数据、压缩实体数据、主人、绑定 ID 等）原样复制到产物上。
 *
 * 原版无羁绊合成不会保留材料 NBT，直接使用会导致升级后女仆消失。
 */
public class MaidWeaponUpgradeRecipe extends ShapelessRecipe {

    public MaidWeaponUpgradeRecipe(ResourceLocation id, String group,
                                   CraftingBookCategory category, ItemStack result,
                                   NonNullList<Ingredient> ingredients) {
        super(id, group, category, result, ingredients);
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess access) {
        ItemStack result = super.assemble(inv, access);
        ItemStack source = findMaidWeapon(inv);
        CompoundTag tag = source.getTag();
        if (tag != null && !tag.isEmpty()) {
            result.setTag(tag.copy());
        }
        return result;
    }

    /** 在合成格中找出带女仆契约的武器材料（契约之刃或其变体）。 */
    private static ItemStack findMaidWeapon(CraftingContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof MaidWeaponItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static class Serializer implements RecipeSerializer<MaidWeaponUpgradeRecipe> {
        @Override
        public MaidWeaponUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            String group = GsonHelper.getAsString(json, "group", "");
            CraftingBookCategory category = CraftingBookCategory.CODEC
                    .byName(GsonHelper.getAsString(json, "category", ""),
                            CraftingBookCategory.MISC);
            NonNullList<Ingredient> ingredients =
                    readIngredients(GsonHelper.getAsJsonArray(json, "ingredients"));
            ItemStack result = ShapedRecipe.itemStackFromJson(
                    GsonHelper.getAsJsonObject(json, "result"));
            return new MaidWeaponUpgradeRecipe(id, group, category, result, ingredients);
        }

        @Override
        public MaidWeaponUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            String group = buf.readUtf();
            CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
            int size = buf.readVarInt();
            NonNullList<Ingredient> ingredients =
                    NonNullList.withSize(size, Ingredient.EMPTY);
            for (int i = 0; i < ingredients.size(); i++) {
                ingredients.set(i, Ingredient.fromNetwork(buf));
            }
            ItemStack result = buf.readItem();
            return new MaidWeaponUpgradeRecipe(id, group, category, result, ingredients);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, MaidWeaponUpgradeRecipe recipe) {
            buf.writeUtf(recipe.getGroup());
            buf.writeEnum(recipe.category());
            buf.writeVarInt(recipe.getIngredients().size());
            for (Ingredient ingredient : recipe.getIngredients()) {
                ingredient.toNetwork(buf);
            }
            buf.writeItem(recipe.getResultItem(RegistryAccess.EMPTY));
        }

        private static NonNullList<Ingredient> readIngredients(JsonArray array) {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            for (int i = 0; i < array.size(); i++) {
                Ingredient ingredient = Ingredient.fromJson(array.get(i));
                if (!ingredient.isEmpty()) {
                    ingredients.add(ingredient);
                }
            }
            return ingredients;
        }
    }
}

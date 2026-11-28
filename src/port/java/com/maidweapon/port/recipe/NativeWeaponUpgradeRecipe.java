package com.maidweapon.port.recipe;
import com.maidweapon.port.content.NativeContent;
import com.maidweapon.port.item.NativeContractSwordItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
/** Native 1.21 recipe serializer; contract/foreign component patches survive a weapon upgrade. */
public final class NativeWeaponUpgradeRecipe extends ShapelessRecipe {
 public NativeWeaponUpgradeRecipe(ShapelessRecipe recipe){super(recipe.getGroup(),recipe.category(),recipe.getResultItem(RegistryAccess.EMPTY),recipe.getIngredients());}
 @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider lookup){
  var result=super.assemble(input,lookup);
  for(var source:input.items())if(source.getItem() instanceof NativeContractSwordItem){
   result.applyComponents(source.getComponentsPatch());break;
  }
  return result;
 }
 @Override public RecipeSerializer<?> getSerializer(){return NativeContent.UPGRADE.get();}
 public static final class Serializer implements RecipeSerializer<NativeWeaponUpgradeRecipe>{
  private final ShapelessRecipe.Serializer delegate=new ShapelessRecipe.Serializer();
  @Override public MapCodec<NativeWeaponUpgradeRecipe> codec(){return delegate.codec().xmap(NativeWeaponUpgradeRecipe::new,r->r);}
  @Override public StreamCodec<RegistryFriendlyByteBuf,NativeWeaponUpgradeRecipe> streamCodec(){return delegate.streamCodec().map(NativeWeaponUpgradeRecipe::new,r->r);}
 }
}

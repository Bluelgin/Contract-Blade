package com.maidweapon.port.content;

import com.maidweapon.port.block.NativeMaidInjectorBlock;
import com.maidweapon.port.item.NativeContractSwordItem;
import com.maidweapon.port.item.ProbeSpiritVeinItem;
import com.maidweapon.port.menu.NativeMaidInjectorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

/** Formal IDs only for content backed by actual native behavior. */
public final class NativeContent {
 public static final String ID="maid_weapon";
 public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
 public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
 public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,ID);
 public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPES=DeferredRegister.create(Registries.RECIPE_SERIALIZER,ID);
 public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>,com.maidweapon.port.recipe.NativeWeaponUpgradeRecipe.Serializer> UPGRADE=RECIPES.register("weapon_upgrade",com.maidweapon.port.recipe.NativeWeaponUpgradeRecipe.Serializer::new);
 public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
 public static final DeferredBlock<NativeMaidInjectorBlock> INJECTOR=BLOCKS.register("maid_injector",()->new NativeMaidInjectorBlock(BlockBehaviour.Properties.of().strength(3.5f).sound(SoundType.STONE).noOcclusion()));
 public static final DeferredItem<BlockItem> INJECTOR_ITEM=ITEMS.register("maid_injector",()->new BlockItem(INJECTOR.get(),new Item.Properties().rarity(Rarity.UNCOMMON)));
 public static final DeferredItem<NativeContractSwordItem> SWORD=ITEMS.register("maid_sword",()->new NativeContractSwordItem(Tiers.IRON,1,-2.4f));
 public static final DeferredItem<NativeContractSwordItem> HEAVY=ITEMS.register("maid_heavy",()->new NativeContractSwordItem(Tiers.DIAMOND,3,-3.2f));
 public static final DeferredItem<NativeContractSwordItem> FAST=ITEMS.register("maid_fast",()->new NativeContractSwordItem(Tiers.GOLD,0,-1.8f));
 public static final DeferredItem<ProbeSpiritVeinItem> AWAKENING=ITEMS.register("awakening_vein",()->new ProbeSpiritVeinItem(1));
 public static final DeferredItem<ProbeSpiritVeinItem> FLOWING=ITEMS.register("flowing_vein",()->new ProbeSpiritVeinItem(2));
 public static final DeferredItem<ProbeSpiritVeinItem> ENDLESS_VEIN=ITEMS.register("endless_vein",()->new ProbeSpiritVeinItem(3));
 public static final DeferredItem<Item> TASSEL=ITEMS.registerSimpleItem("resonance_sword_tassel",new Item.Properties().stacksTo(1));
 public static final DeferredItem<Item> GUARD=ITEMS.registerSimpleItem("guard_knot",new Item.Properties().stacksTo(1));
 public static final DeferredItem<Item> RIBBON=ITEMS.registerSimpleItem("guardian_ribbon",new Item.Properties().stacksTo(1));
 public static final DeferredItem<Item> KNOT=ITEMS.registerSimpleItem("heartbound_knot",new Item.Properties().stacksTo(1));
 public static final DeferredItem<Item> OATH_RING=ITEMS.registerSimpleItem("oath_ring",new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
 public static final DeferredItem<Item> ENDLESS=ITEMS.registerSimpleItem("endless_contract",new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
 public static final DeferredItem<NativeSpiritCrystalItem> CRYSTAL=ITEMS.register("spirit_crystal",NativeSpiritCrystalItem::new);
 public static final DeferredHolder<MenuType<?>,MenuType<NativeMaidInjectorMenu>> INJECTOR_MENU=MENUS.register("maid_injector",()->IMenuTypeExtension.create(NativeMaidInjectorMenu::new));
 public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("main",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.maid_weapon")).icon(()->new ItemStack(SWORD.get())).displayItems((p,out)->{out.accept(SWORD.get());out.accept(HEAVY.get());out.accept(FAST.get());out.accept(INJECTOR_ITEM.get());out.accept(AWAKENING.get());out.accept(FLOWING.get());out.accept(ENDLESS_VEIN.get());out.accept(TASSEL.get());out.accept(RIBBON.get());out.accept(KNOT.get());out.accept(GUARD.get());out.accept(OATH_RING.get());out.accept(ENDLESS.get());out.accept(CRYSTAL.get());}).build());
 public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);MENUS.register(bus);TABS.register(bus);RECIPES.register(bus);}
 private NativeContent(){}
}

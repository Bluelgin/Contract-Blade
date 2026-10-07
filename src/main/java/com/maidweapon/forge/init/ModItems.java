package com.maidweapon.forge.init;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.item.ContractInteriorKeyItem;
import com.maidweapon.forge.item.ContractProjectionBaubleItem;
import com.maidweapon.forge.system.deployment.ContractProjectionMode;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.item.PetWeaponItem;
import com.maidweapon.forge.item.variant.MaidWeaponVariantItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Items owned by the standalone contract core. */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MaidWeaponConstants.MOD_ID);

    public static final RegistryObject<Item> RESONANCE_SWORD_TASSEL = ITEMS.register(
            "resonance_sword_tassel", () -> new ContractProjectionBaubleItem(ContractProjectionMode.WEAPON));
    public static final RegistryObject<Item> GUARDIAN_RIBBON = ITEMS.register(
            "guardian_ribbon", () -> new ContractProjectionBaubleItem(ContractProjectionMode.ARMOR));
    public static final RegistryObject<Item> HEARTBOUND_KNOT = ITEMS.register(
            "heartbound_knot", () -> new ContractProjectionBaubleItem(ContractProjectionMode.COMBINED));

    public static final RegistryObject<Item> MAID_SWORD = ITEMS.register(
            "maid_sword", () -> new MaidWeaponItem(new Item.Properties()
                    .stacksTo(1).durability(MaidWeaponData.MAX_FAVORABILITY + 1)));

    public static final RegistryObject<Item> MAID_INJECTOR = ITEMS.register(
            "maid_injector", () -> new BlockItem(ModBlocks.MAID_INJECTOR.get(),
                    new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> MAID_HEAVY = ITEMS.register(
            "maid_heavy", () -> new MaidWeaponVariantItem(MaidWeaponVariantItem.Variant.HEAVY));
    public static final RegistryObject<Item> MAID_FAST = ITEMS.register(
            "maid_fast", () -> new MaidWeaponVariantItem(MaidWeaponVariantItem.Variant.FAST));

    public static final RegistryObject<Item> DOG_BLADE = ITEMS.register(
            "dog_blade", () -> new PetWeaponItem(PetWeaponItem.Kind.DOG));
    public static final RegistryObject<Item> CAT_BLADE = ITEMS.register(
            "cat_blade", () -> new PetWeaponItem(PetWeaponItem.Kind.CAT));

    public static final RegistryObject<Item> CONTRACT_INTERIOR_KEY = ITEMS.register(
            "contract_interior_key", () -> new ContractInteriorKeyItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> SPIRIT_CRYSTAL = ITEMS.register(
            "spirit_crystal", () -> new Item(new Item.Properties().stacksTo(16)) {
                @Override
                public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                              InteractionHand hand) {
                    ItemStack crystal = player.getItemInHand(hand);
                    ItemStack weapon = player.getMainHandItem();
                    if (!MaidInfusion.isInfused(weapon)) {
                        weapon = player.getOffhandItem();
                        if (!MaidInfusion.isInfused(weapon)) {
                            return InteractionResultHolder.pass(crystal);
                        }
                    }
                    if (level.isClientSide) return InteractionResultHolder.success(crystal);
                    if (!ContractCarrierData.isOwner(weapon, player)) {
                        player.displayClientMessage(
                                Component.translatable("maid_weapon.message.not_owner"), true);
                        return InteractionResultHolder.fail(crystal);
                    }
                    MaidWeaponData data = ContractCarrierData.getMaidData(weapon);
                    if (data.getResonance() >= MaidWeaponData.maximumResonance()) {
                        player.displayClientMessage(
                                Component.translatable("maid_weapon.message.resonance_full"), true);
                        return InteractionResultHolder.fail(crystal);
                    }
                    data.addResonance(50);
                    ContractCarrierData.setMaidData(weapon, data);
                    crystal.shrink(1);
                    player.displayClientMessage(Component.translatable(
                            "maid_weapon.message.resonance_restored", 50), true);
                    return InteractionResultHolder.success(crystal);
                }
            });

    /** Development blade retained for compatibility with existing test worlds. */
    public static final RegistryObject<Item> TEST_SLASHBLADE = ITEMS.register(
            "test_slashblade", () -> new MaidWeaponItem(new Item.Properties()
                    .stacksTo(1).durability(MaidWeaponData.MAX_FAVORABILITY + 1)) {
                @Override
                public void onCraftedBy(ItemStack stack, Level level, Player player) {
                    stack.getOrCreateTag().putBoolean(
                            MaidWeaponConstants.TAG_SLASHBLADE_MODE, true);
                }
            });

    private ModItems() {
    }
}

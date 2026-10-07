package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.api.EmbeddedSpiritApi;
import com.maidweapon.forge.api.IntrinsicSpiritApi;
import com.maidweapon.forge.event.FoxSoulProtectionEvents;
import com.maidweapon.forge.init.ModBlocks;
import com.maidweapon.forge.menu.MaidInjectorMenu;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.maidweapon.forge.system.fox.FoxSpiritLedger;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.maidweapon.forge.system.fox.FoxSpiritTransferService;
import com.maidweapon.forge.system.fox.ShrineFoxStory;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/** Server-only fixture using the actual four-slot contract table, not a mock transfer. */
public final class FoxSpiritTransferValidation {
    public static void run(MinecraftServer server, ServerPlayer owner, ItemStack white) {
        try {
            execute(server, owner, white);
            LogUtils.getLogger().info("FOX_TRANSFER_NATIVE_PASS");
        } catch (Exception failure) { throw new IllegalStateException("fox transfer fixture", failure); }
    }

    private static void execute(MinecraftServer server, ServerPlayer owner, ItemStack white) throws Exception {
        var previousMenu = owner.containerMenu;
        owner.getInventory().clearContent();
        BlockPos table = new BlockPos(250, 100, 250);
        owner.serverLevel().setBlock(table, ModBlocks.MAID_INJECTOR.get().defaultBlockState(), 2);
        var menu = new MaidInjectorMenu(7, owner.getInventory(), table);
        owner.containerMenu = menu;
        var soulItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("touhou_little_maid:film"));
        check(soulItem != Items.AIR, "native soul talisman exists");
        check(FoxSpiritTransferService.storyEligible(owner, white), "spirit in original blade permits story");
        check(!Ingredient.of(white.getItem()).test(white), "vanilla ingredient rejects occupied blade");
        var ingredientClass = Class.forName("mods.flammpfeil.slashblade.recipe.SlashBladeIngredient");
        var slashIngredient = (Ingredient) ingredientClass.getMethod("of", ResourceLocation.class)
                .invoke(null, ResourceLocation.parse("slashblade:fox_white"));
        check(!slashIngredient.test(white), "SlashBlade ingredient rejects occupied blade");

        var identity = FoxSpiritState.resident(white).copy();
        var first = click(menu, owner, white, new ItemStack(soulItem));
        check(com.maidweapon.forge.compat.WhiteFoxSpecialEffectCompat.hasEffect(first[0])
                        && !com.maidweapon.forge.compat.WhiteFoxSpecialEffectCompat.hasEffect(first[1]),
                "native SE stays on shrine sword rather than entering soul talisman");
        ItemStack original = first[0];
        ItemStack sealed = first[1];
        check(!FoxSpiritState.hasResident(original) && !FoxSpiritTransferService.storyEligible(owner, original),
                "empty original no longer permits story");
        check(Ingredient.of(original.getItem()).test(original) && slashIngredient.test(original),
                "empty original can be used in ordinary crafting");
        check(!IntrinsicSpiritApi.ensureIntrinsicSpirit(owner, original, FoxSpiritState.WHITE, "白狐"),
                "empty original never regenerates its departed spirit");
        check(!sealed.getOrCreateTag().contains("MaidInfo"), "sealed identity is not a native spawn payload");
        var staleSeal = sealed.copy();
        var ledgerSaved = FoxSpiritLedger.get(server).save(new CompoundTag());
        check(ledgerSaved.getCompound("Entries").getCompound(identity.getUUID("SpiritUUID").toString())
                .getString("Kind").equals("SEAL"), "migration authority is included in world save");
        check(FoxSpiritLedger.load(ledgerSaved).owns(FoxSpiritState.sealed(sealed), "SEAL"),
                "world reload restores soul talisman authority");
        var stranger = net.minecraftforge.common.util.FakePlayerFactory.get(owner.serverLevel(),
                new com.mojang.authlib.GameProfile(UUID.fromString("4dff1555-ec8b-4b3a-a565-5df8ef9050ea"), "FoxStranger"));
        check(!FoxSpiritTransferService.canTransfer(stranger, new ItemStack(Items.IRON_SWORD), sealed),
                "another player cannot move the spirit from her owner's talisman");
        owner.setItemInHand(InteractionHand.MAIN_HAND, sealed);
        var use = new PlayerInteractEvent.RightClickItem(owner, InteractionHand.MAIN_HAND);
        FoxSoulProtectionEvents.use(use);
        check(use.isCanceled(), "soul talisman cannot spawn a normal maid");
        owner.getInventory().clearContent();
        var sword = new ItemStack(Items.IRON_SWORD);
        var swordBefore = sword.save(new CompoundTag());
        var moved = click(menu, owner, sword, sealed);
        var away = moved[0];
        check(FoxSpiritState.resident(away).getUUID("SpiritUUID").equals(identity.getUUID("SpiritUUID")),
                "identity survives migration into another weapon");
        check(!FoxSpiritTransferService.storyEligible(owner, away), "other weapon never permits special story");
        var awayReceipt = owner.getPersistentData().copy();
        owner.getInventory().setItem(0, away);
        ShrineFoxStory.observe(owner);
        check(awayReceipt.equals(owner.getPersistentData()), "away carrier never advances original-blade dialogue");
        owner.getInventory().clearContent();
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
        menu.getSlot(1).set(staleSeal);
        check(!menu.canInject(), "consumed soul talisman copy cannot be replayed");
        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(1).set(ItemStack.EMPTY);
        var swordAfter = away.save(new CompoundTag());
        swordAfter.getCompound("tag").remove(FoxSpiritState.ROOT);
        if (swordAfter.getCompound("tag").isEmpty()) swordAfter.remove("tag");
        check(swordBefore.equals(swordAfter), "target weapon keeps its own original properties");
        var returnSeal = click(menu, owner, away, moved[1]);
        var returned = click(menu, owner, original, returnSeal[1]);
        white = returned[0];
        check(FoxSpiritTransferService.storyEligible(owner, white), "returning to original reopens story gate");
        ItemStack lookAlike = white.copy();
        lookAlike.getOrCreateTag().putUUID(FoxSpiritState.ORIGIN, UUID.randomUUID());
        check(!FoxSpiritTransferService.storyEligible(owner, lookAlike),
                "another White Fox blade cannot impersonate her original home");

        // Attach representative compressed contract data, without registering a placeholder model.
        UUID maidId = UUID.randomUUID();
        var seed = new ItemStack(Items.IRON_SWORD);
        var growth = new MaidWeaponData("白狐");
        growth.setLevel(3);
        growth.setFavorability(278);
        growth.setResonance(141);
        growth.setTotalKills(42);
        ContractCarrierData.setMaidData(seed, growth);
        ContractCarrierData.setOwner(seed, owner);
        ContractCarrierData.setBoundMaidUUID(seed, maidId.toString());
        String binding = ContractCarrierData.ensureBindingId(seed);
        seed.getOrCreateTag().putString(EmbeddedSpiritApi.TAG_SPIRIT_ID, FoxSpiritState.WHITE);
        seed.getOrCreateTag().putString(EmbeddedSpiritApi.TAG_SPIRIT_NAME, "白狐");
        var entity = new CompoundTag();
        entity.putString("id", "touhou_little_maid:maid");
        entity.putUUID("UUID", maidId);
        entity.putString("ModelId", "fixture:unregistered_white_fox");
        var hands = new ListTag();
        hands.add(new ItemStack(Items.IRON_SWORD).save(new CompoundTag()));
        hands.add(new ItemStack(Items.SHIELD).save(new CompoundTag()));
        entity.put("HandItems", hands);
        var armor = new ListTag();
        armor.add(ItemStack.EMPTY.save(new CompoundTag()));
        armor.add(ItemStack.EMPTY.save(new CompoundTag()));
        armor.add(new ItemStack(Items.DIAMOND_CHESTPLATE).save(new CompoundTag()));
        armor.add(ItemStack.EMPTY.save(new CompoundTag()));
        entity.put("ArmorItems", armor);
        entity.put("FixtureInventory", new ItemStack(Items.DIAMOND, 3).save(new CompoundTag()));
        MaidEntityDataCodec.write(seed.getOrCreateTag(), entity);
        check(IntrinsicSpiritApi.attachStoredSpirit(white, FoxSpiritState.WHITE, seed.getTag()),
                "compressed full contract is attached without spawning a maid");
        var staleWeapon = white.copy();
        var blockedTarget = new ItemStack(Items.DIAMOND_SWORD);
        ContractCarrierData.setMaidData(blockedTarget, new MaidWeaponData("Other maid"));
        var fullSeal = click(menu, owner, white, returned[1]);
        check(!FoxSpiritTransferService.authorizesContract(owner, staleWeapon),
                "departed spirit cannot manifest from an obsolete weapon copy");
        menu.getSlot(0).set(blockedTarget);
        menu.getSlot(1).set(fullSeal[1]);
        check(!menu.canInject(), "existing target contract cannot be overwritten");
        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(1).set(ItemStack.EMPTY);
        var damagedSeal = fullSeal[1].copy();
        FoxSpiritState.sealed(damagedSeal).getCompound("Contract")
                .putLong(MaidEntityDataCodec.CHECKSUM, -1L);
        var safeTarget = new ItemStack(Items.DIAMOND_SWORD);
        menu.getSlot(0).set(safeTarget);
        menu.getSlot(1).set(damagedSeal);
        var damagedBefore = damagedSeal.save(new CompoundTag());
        check(!menu.clickMenuButton(owner, MaidInjectorMenu.INJECT_BUTTON),
                "damaged compressed contract cannot be imported");
        check(damagedBefore.equals(menu.getSlot(1).getItem().save(new CompoundTag()))
                        && menu.getSlot(0).getItem() == safeTarget
                        && menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty(),
                "failed import leaves both inputs and outputs intact");
        check(FoxSpiritLedger.get(server).owns(FoxSpiritState.sealed(fullSeal[1]), "SEAL"),
                "failed import does not revoke the valid soul talisman");
        menu.getSlot(1).set(fullSeal[1]);
        menu.getSlot(2).set(new ItemStack(Items.STICK));
        check(!menu.canInject() && !menu.clickMenuButton(owner, MaidInjectorMenu.INJECT_BUTTON),
                "occupied output slot prevents migration without consuming inputs");
        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(1).set(ItemStack.EMPTY);
        menu.getSlot(2).set(ItemStack.EMPTY);
        var fullMoved = click(menu, owner, new ItemStack(Items.DIAMOND_SWORD), fullSeal[1]);
        check(FoxSpiritTransferService.authorizesContract(owner, fullMoved[0]),
                "current carrier retains spirit manifestation authority");
        check(entity.equals(MaidEntityDataCodec.read(fullMoved[0].getTag())),
                "equipment inventory and model data survive full transfer");
        var movedGrowth = ContractCarrierData.getMaidData(fullMoved[0]);
        check(movedGrowth.getLevel() == 3 && movedGrowth.getFavorability() == 278
                        && movedGrowth.getResonance() == 141 && movedGrowth.getTotalKills() == 42,
                "contract growth and relationship progress survive migration");
        check(binding.equals(ContractCarrierData.getBindingId(fullMoved[0]))
                        && maidId.toString().equals(ContractCarrierData.getBoundMaidUUID(fullMoved[0])),
                "maid and interior binding identities survive transfer");
        var staleFullSeal = fullSeal[1];
        check(!FoxSpiritTransferService.canTransfer(owner, new ItemStack(Items.IRON_SWORD), staleFullSeal),
                "full contract cannot be duplicated through stale seal");
        var fullReturnSeal = click(menu, owner, fullMoved[0], fullMoved[1]);
        var fullReturned = click(menu, owner, fullSeal[0], fullReturnSeal[1]);
        check(FoxSpiritTransferService.storyEligible(owner, fullReturned[0]),
                "full contract returns home without losing its original identity");
        check(entity.equals(MaidEntityDataCodec.read(fullReturned[0].getTag())),
                "round trip preserves all serialized maid data");
        var receipt = owner.getPersistentData().copy();
        owner.getInventory().setItem(0, fullReturned[0]);
        ShrineFoxStory.observe(owner);
        check(receipt.equals(owner.getPersistentData()), "return home does not replay completed dialogue");
        owner.getInventory().clearContent();
        owner.containerMenu = previousMenu;
    }

    static ItemStack[] click(MaidInjectorMenu menu, ServerPlayer owner, ItemStack weapon, ItemStack seal) {
        menu.getSlot(0).set(weapon);
        menu.getSlot(1).set(seal);
        check(menu.canInject(), "table accepts safe migration");
        check(menu.clickMenuButton(owner, MaidInjectorMenu.INJECT_BUTTON), "server commits both outputs");
        check(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(),
                "table consumes each input exactly once");
        ItemStack[] result = {menu.getSlot(2).getItem(), menu.getSlot(3).getItem()};
        menu.getSlot(2).set(ItemStack.EMPTY);
        menu.getSlot(3).set(ItemStack.EMPTY);
        return result;
    }

    private static void check(boolean result, String message) {
        if (!result) throw new IllegalStateException(message);
        LogUtils.getLogger().info("Fox transfer fixture: {}", message);
    }

    private FoxSpiritTransferValidation() { }
}

package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.system.ContractHandbookService;
import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Opt-in isolated native fixture; never runs in a player's normal game. */
public final class ContractHandbookValidation {
    public static void run(MinecraftServer server) {
        var owner = FakePlayerFactory.get(server.overworld(), new GameProfile(
                java.util.UUID.fromString("658ad56a-38ae-4e5e-bb56-d35ec1cda9f4"), "HandbookFixture"));
        owner.getInventory().clearContent();
        owner.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
        owner.tickCount = 20;
        var tick = new TickEvent.PlayerTickEvent(TickEvent.Phase.END, owner);
        ContractHandbookService.tick(tick);
        check(count(owner) == 0, "ordinary ticks never grant handbook");
        craft(owner, new ItemStack(Items.STICK));
        check(count(owner) == 0, "vanilla crafting does not grant handbook");
        craft(owner, new ItemStack(ModItems.DOG_BLADE.get()));
        var guideItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("patchouli:guide_book"));
        if (guideItem == null || guideItem == Items.AIR) {
            check(count(owner) == 0, "absent optional Patchouli creates no invalid item");
            return;
        }
        check(count(owner) == 1, "any mod item craft grants handbook once");
        craft(owner, new ItemStack(ModItems.MAID_SWORD.get()));
        check(count(owner) == 1, "subsequent crafts do not duplicate handbook");
        var savedReceipt = owner.getPersistentData().copy();
        owner.getInventory().clearContent();
        owner.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
        owner.getPersistentData().merge(savedReceipt);
        craft(owner, new ItemStack(ModItems.CONTRACT_INTERIOR_KEY.get()));
        check(count(owner) == 0, "saved receipt prevents replacement after loss/reconnect");
        var clone = FakePlayerFactory.get(server.overworld(), new GameProfile(
                java.util.UUID.fromString("d2942dc6-fc67-4d2f-8b6a-c39c016bec85"), "HandbookClone"));
        clone.getInventory().clearContent();
        clone.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
        ContractHandbookService.clone(new PlayerEvent.Clone(clone, owner, true));
        craft(clone, new ItemStack(ModItems.CAT_BLADE.get()));
        check(count(clone) == 0, "death clone retains one-time receipt");
        owner.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++)
            owner.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        craft(owner, new ItemStack(ModItems.GUARDIAN_RIBBON.get()));
        check(count(owner) == 0, "full inventory defers delivery without discarding book");
        owner.getInventory().setItem(0, ItemStack.EMPTY);
        ContractHandbookService.tick(tick);
        check(count(owner) == 1, "earned book delivered when space becomes available");
        owner.getInventory().clearContent();
        owner.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
        var advancement = server.getAdvancements().getAdvancement(ResourceLocation.parse("maid_weapon:tutorial_altar_crafted"));
        check(advancement != null, "native altar receipt advancement loads");
        var altar = new net.minecraftforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent(owner, advancement);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(altar);
        check(count(owner) == 1, "altar crafting grants the same handbook");
        ContractHandbookService.altarCrafted(altar);
        craft(owner, new ItemStack(ModItems.MAID_SWORD.get()));
        check(count(owner) == 1, "altar and table crafting share one receipt");
        com.mojang.logging.LogUtils.getLogger().info("[MaidWeapon] HANDBOOK_VALIDATION_PASS");
    }

    private static void craft(net.minecraft.server.level.ServerPlayer owner, ItemStack result) {
        ContractHandbookService.crafted(new PlayerEvent.ItemCraftedEvent(owner, result,
                new net.minecraft.world.SimpleContainer(9)));
    }

    private static long count(Player owner) {
        return owner.getInventory().items.stream().filter(stack -> stack.getTag() != null
                && "maid_weapon:contract_fragments".equals(stack.getTag().getString("patchouli:book"))).count();
    }

    private static void check(boolean passed, String label) {
        if (!passed) throw new IllegalStateException(label);
        com.mojang.logging.LogUtils.getLogger().info("[MaidWeapon] Handbook fixture: {}", label);
    }

    private ContractHandbookValidation() { }
}

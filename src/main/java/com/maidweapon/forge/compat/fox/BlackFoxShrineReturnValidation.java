package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.system.fox.*;
import com.maidweapon.forge.system.fox.challenge.BlackFoxShrineReturn;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.UUID;

/** Called only by the isolated opt-in sleep-route fixture, never by ordinary gameplay. */
final class BlackFoxShrineReturnValidation {
    static ItemFrame run(ServerPlayer player, ItemFrame stand) throws Exception {
        var server = player.getServer();
        var service = BlackFoxShrineReturn.get(server);
        var level = player.serverLevel();
        var anchor = stand.getPos();
        stand.getItem().getOrCreateTag().putString("ReturnFixture", "original-strengthening-SA-texture");
        var before = stand.getItem().save(new CompoundTag());
        check(service.begin(player, stand) && stand.getItem().isEmpty(), "one original blade in escrow");
        check(!service.begin(player, stand) && BlackFoxShrineReturn.protectedStand(stand), "second ritual rejected");
        check(!stand.hurt(level.damageSources().generic(), 10), "native reserved stand cannot be broken");
        var interrupted = BlackFoxShrineReturn.load(service.save(new CompoundTag()));
        interrupted.recoverInterrupted();
        check(!interrupted.save(new CompoundTag()).getList("Returns", 10).getCompound(0).getBoolean("Active"),
                "restart releases orphaned encounter without losing blade");
        service.settle(player);
        check(before.equals(stand.getItem().save(new CompoundTag())) && !service.pending(player.getUUID()),
                "loss preserves native and custom blade data");
        var playerIndex = server.getPlayerList().getClass().getSuperclass().getDeclaredField("playersByUUID");
        playerIndex.setAccessible(true);
        @SuppressWarnings("unchecked")
        var index = (java.util.Map<UUID, ServerPlayer>) playerIndex.get(server.getPlayerList());
        ServerPlayer previous = index.put(player.getUUID(), player);
        check(com.maidweapon.forge.system.fox.challenge.FoxChallengeService.enter(player, ItemStack.EMPTY,
                com.maidweapon.forge.system.fox.challenge.FoxChallengeService.position(player)), "victory fixture enters real arena");
        check(com.maidweapon.forge.system.fox.challenge.BlackFoxEncounters.start(player), "victory fixture spawns real encounter");
        check(service.begin(player, stand), "victory fixture escrows original shrine blade");
        var actor = com.maidweapon.forge.system.fox.challenge.BlackFoxEncounters.get(player);
        actor.combat().defeat();
        check(FoxSpiritState.BLACK.equals(FoxSpiritState.resident(ItemStack.of(service.save(new CompoundTag())
                .getList("Returns", 10).getCompound(0).getCompound("Blade"))).getString("SpiritId")),
                "real encounter defeat installs Black Fox identity");
        var victory = service.save(new CompoundTag());
        check(service.rescue(player) && victory.equals(service.save(new CompoundTag())), "victory is idempotent");
        var standNbt = stand.saveWithoutId(new CompoundTag());
        standNbt.remove("UUID");
        stand.discard();
        for (int tick = 0; tick < 100; tick++) actor.combat().tick();
        check(actor.body().isRemoved() && com.maidweapon.forge.system.fox.challenge.BlackFoxEncounters.get(player) == null,
                "defeat presentation ends and retires Boss, never converts it to a companion");
        com.maidweapon.forge.system.fox.challenge.FoxChallengeService.leave(player);
        if (previous == null) index.remove(player.getUUID()); else index.put(player.getUUID(), previous);
        check(service.pending(player.getUUID()), "missing stand leaves persistent pending return");
        check(victory.getList("Returns", 10).getCompound(0).getCompound("Blade").equals(
                BlackFoxShrineReturn.load(service.save(new CompoundTag())).save(new CompoundTag())
                .getList("Returns", 10).getCompound(0).getCompound("Blade")), "save/load preserves rescued blade and native capabilities");
        var replacement = (ItemFrame) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("slashblade:blade_stand_entity")).create(level);
        replacement.load(standNbt);
        replacement.setItem(new ItemStack(Items.IRON_SWORD));
        level.addFreshEntity(replacement);
        service.retry(server, player.getUUID());
        check(service.pending(player.getUUID()) && replacement.getItem().is(Items.IRON_SWORD), "occupied stand is never overwritten");
        replacement.setItem(ItemStack.EMPTY);
        service.retry(server, player.getUUID());
        var returned = replacement.getItem();
        check(!service.pending(player.getUUID()) && replacement.getPos().equals(anchor), "one blade returns to the exact original anchor");
        check(FoxSpiritState.hasResident(returned) && FoxSpiritState.BLACK.equals(FoxSpiritState.resident(returned).getString("SpiritId")),
                "returned spirit is Black Fox");
        ItemStack clean = returned.copy();
        clean.getOrCreateTag().remove(FoxSpiritState.ROOT);
        clean.getOrCreateTag().remove(FoxSpiritState.ORIGIN);
        check(before.equals(clean.save(new CompoundTag())), "victory does not change original blade capabilities");
        check(!service.canBegin(player, replacement), "repeat ritual cannot consume rescued spirit");
        var stranger = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BlackFoxReturnStranger"));
        check(!BlackFoxShrineReturn.allowTake(replacement, stranger, InteractionHand.MAIN_HAND), "stranger cannot claim returned blade");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(BlackFoxShrineReturn.allowTake(replacement, player, InteractionHand.MAIN_HAND), "owner takes with empty hand");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        check(!BlackFoxShrineReturn.allowTake(replacement, player, InteractionHand.MAIN_HAND), "held weapon cannot swap the returned blade");
        player.setItemInHand(InteractionHand.MAIN_HAND, returned.copy());
        replacement.setItem(ItemStack.EMPTY);
        ItemStack weapon = player.getMainHandItem();
        check(FoxSpiritCompanionService.initialize(player, weapon), "normal Black Fox companion contract initializes");
        check(FoxModelPackBootstrap.BLACK_MODEL.equals(MaidEntityDataCodec.read(weapon.getTag()).getString("ModelId")),
                "companion uses original Black Fox model, not Boss tint");
        check(!FoxSpiritTransferService.storyEligible(player, weapon), "Black Fox cannot trigger White Fox story");
        com.maidweapon.forge.system.deployment.ContractCompanionService.disconnect(player);
        com.maidweapon.forge.system.deployment.ContractCompanionService.toggle(player);
        var maidId = UUID.fromString(com.maidweapon.forge.system.contract.ContractCarrierData.getBoundMaidUUID(weapon));
        var maid = player.serverLevel().getEntity(maidId);
        check(maid != null && FoxModelPackBootstrap.BLACK_MODEL.equals(maid.getClass().getMethod("getModelId").invoke(maid)),
                "rescued Black Fox really manifests through the normal companion lifecycle");
        ((net.minecraft.world.level.storage.ServerLevelData) server.overworld().getLevelData())
                .setGameTime(player.serverLevel().getGameTime() + 10);
        com.maidweapon.forge.system.deployment.ContractCompanionService.toggle(player);
        check(com.maidweapon.forge.item.MaidInfusion.containsMaid(weapon), "Black Fox recalls into her original blade");
        ItemStack stale = weapon.copy();
        ItemStack seal = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("touhou_little_maid:film")));
        var extracted = FoxSpiritTransferService.transfer(player, weapon, seal);
        check(extracted != null && !FoxSpiritTransferService.authorizesContract(player, stale), "extraction revokes stale Black Fox contracts");
        var infused = FoxSpiritTransferService.transfer(player, new ItemStack(Items.IRON_SWORD), extracted.seal());
        check(infused != null && com.maidweapon.forge.api.EmbeddedSpiritApi.isSpirit(infused.weapon(), FoxSpiritState.BLACK),
                "existing soul-talisman transfer preserves Black Fox");
        player.getInventory().clearContent();
        LogUtils.getLogger().info("BLACK_FOX_SHRINE_RETURN_PASS: original blade, loss/restart, blocked return, ownership, companion model and soul transfer");
        return replacement;
    }
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}

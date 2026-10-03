package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.compat.BladeTetraStoryCompat;
import com.maidweapon.forge.system.fox.ShrineFoxStory;
import com.maidweapon.forge.worldgen.ShinkitsuShrinePiece;
import com.maidweapon.forge.worldgen.ShinkitsuShrineStructure;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Opt-in private test-world fixture. Never enable on a player's normal world. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ShrineFoxValidation {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.fox.validation")) return;
        try {
            run(event.getServer());
            LogUtils.getLogger().info("SHRINE_NATIVE_PASS");
        } catch (Throwable error) {
            LogUtils.getLogger().error("SHRINE_NATIVE_FAIL", error);
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var biomeTag = net.minecraft.tags.TagKey.create(Registries.BIOME,
                net.minecraft.resources.ResourceLocation.parse("maid_weapon:has_shinkitsu_shrine"));
        var shrineBiomes = server.registryAccess().registryOrThrow(Registries.BIOME)
                .getTag(biomeTag).orElseThrow();
        check(shrineBiomes.size() == 1 && shrineBiomes.get(0)
                        .is(net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS),
                "native shrine biome tag resolves to snowy plains only");
        var registered = server.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ShinkitsuShrinePiece.TEMPLATE);
        check(registered instanceof ShinkitsuShrineStructure, "custom structure codec loads");
        var template = level.getStructureManager().get(ShinkitsuShrinePiece.TEMPLATE).orElseThrow();
        check(template.getSize().equals(new BlockPos(47, 15, 47)), "template size loads");
        var piece = new ShinkitsuShrinePiece(level.getStructureManager(), new BlockPos(256, 100, 256));
        var restoredPiece = new ShinkitsuShrinePiece(level.getStructureManager(),
                piece.createTag(StructurePieceSerializationContext.fromLevel(level)));
        check(piece.getBoundingBox().equals(restoredPiece.getBoundingBox()),
                "structure piece restores the saved template position");
        var owner = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("448740fa-7d5e-459b-81dd-6c4648c3a503"), "ShrineFixture"));
        owner.getInventory().clearContent();
        owner.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
        ItemStack fake = new ItemStack(Items.IRON_SWORD);
        fake.getOrCreateTag().putBoolean(ShrineFoxStory.OFFERING, true);
        owner.getInventory().setItem(0, fake);
        ShrineFoxStory.observe(owner);
        check(!progress(owner).getBoolean("Greeting"), "marker on non-blade cannot trigger");
        if (!SlashBladeCompat.isLoaded()) {
            check(net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_SWORD)
                            .test(new ItemStack(Items.IRON_SWORD)),
                    "ordinary ingredients remain usable without SlashBlade");
            check(!progress(owner).getBoolean("Encounter"), "absent SlashBlade remains inert");
            var generator = level.getChunkSource().getGenerator();
            var start = registered.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
                    new ChunkPos(0, 0), 0, level, biome -> true);
            check(!start.isValid(), "absent SlashBlade cannot create a shrine structure start");
            return;
        }
        var generator = level.getChunkSource().getGenerator();
        boolean foundStart = false;
        for (int sample = 0; sample < 64 && !foundStart; sample++) {
            var start = registered.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
                    new ChunkPos((sample % 8 - 4) * 16, (sample / 8 - 4) * 16),
                    0, level, biome -> true);
            foundStart = start.isValid();
            if (foundStart) check(start.getPieces().size() == 1,
                    "valid terrain produces one authored template piece");
        }
        check(foundStart, "structure generation finds suitable terrain");
        BlockPos origin = new BlockPos(256, 100, 256);
        var area = new net.minecraft.world.phys.AABB(origin, origin.offset(47, 15, 47));
        level.getEntities((net.minecraft.world.entity.Entity) null, area,
                entity -> entity.getTags().contains("maid_weapon_shrine_offering"))
                .forEach(net.minecraft.world.entity.Entity::discard);
        for (int dx = 0; dx < 47; dx += 16) {
            for (int dz = 0; dz < 47; dz += 16) {
                var settings = ShinkitsuShrinePiece.settings().setBoundingBox(new BoundingBox(
                        origin.getX() + dx, 0, origin.getZ() + dz,
                        origin.getX() + dx + 15, 319, origin.getZ() + dz + 15));
                check(template.placeInWorld(level, origin, origin, settings, level.random, 2),
                        "clipped chunk placement succeeds");
            }
        }
        var stand = level.getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(origin, origin.offset(47, 15, 47)),
                entity -> entity.getTags().contains("maid_weapon_shrine_offering"));
        check(stand.size() == 1, "exactly one offering stand spawns");
        CompoundTag entity = stand.get(0).saveWithoutId(new CompoundTag());
        check(entity.getInt("TileX") == origin.getX() + 23
                        && entity.getInt("TileY") == origin.getY() + 4
                        && entity.getInt("TileZ") == origin.getZ() + 26,
                "hanging anchor relocates to world coordinates");
        for (int tick = 0; tick < 110; tick++) stand.get(0).tick();
        check(!stand.get(0).isRemoved(), "stand survives more than one hanging check interval");
        ItemStack blade = ItemStack.of(entity.getCompound("Item"));
        check(SlashBladeCompat.isNamedBlade(blade, "item.slashblade.fox_white"),
                "real capability identifies White Fox");
        check(!SlashBladeCompat.isNamedBlade(blade, "item.slashblade.fox_black"),
                "White Fox is not Black Fox");
        ShrineOfferingValidation.run((net.minecraft.world.entity.decoration.ItemFrame) stand.get(0), owner);
        CompoundTag before = blade.save(new CompoundTag());
        ItemStack unmarked = blade.copy();
        unmarked.getOrCreateTag().remove(ShrineFoxStory.OFFERING);
        owner.getInventory().setItem(0, unmarked);
        ShrineFoxStory.observe(owner);
        check(!progress(owner).getBoolean("Greeting"), "ordinary White Fox does not trigger");
        owner.getInventory().setItem(0, blade);
        ShrineFoxStory.observe(owner);
        boolean hasBladeTetra = BladeTetraStoryCompat.isLoaded();
        check(progress(owner).getBoolean("Greeting") == !hasBladeTetra,
                "standalone greeting is separated from BladeTetra's story");
        check(progress(owner).getBoolean("Encounter") == hasBladeTetra,
                "divine encounter requires the actual installed BladeTetra mod");
        var afterClaim = blade.save(new CompoundTag());
        afterClaim.getCompound("tag").remove(com.maidweapon.forge.system.fox.FoxSpiritState.ROOT);
        afterClaim.getCompound("tag").remove(com.maidweapon.forge.system.fox.FoxSpiritState.ORIGIN);
        check(before.equals(afterClaim), "story preserves full blade state");
        if (hasBladeTetra) {
            check(!progress(owner).getBoolean("EchoHeard"), "uncleared original divine domain cannot advance the echo");
            owner.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                    .putBoolean("blade_tetra_divine_domain_cleared", true);
            ShrineFoxStory.observe(owner);
            check(progress(owner).getBoolean("EchoHeard")
                            && BladeTetraStoryCompat.hasCompletedDivinePrologue(owner),
                    "real BladeTetra bridge reads the original clear flag without changing its quest state");
        }
        CompoundTag receipt = owner.getPersistentData().copy();
        ShrineFoxStory.observe(owner);
        check(receipt.equals(owner.getPersistentData()), "repeated ticks do not alter receipt");
        var clone = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("f9010901-26eb-41ee-88bd-9c2434434261"), "ShrineClone"));
        clone.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
        ShrineFoxStory.copyProgress(owner, clone);
        check(progress(clone).equals(progress(owner)), "death clone keeps shrine receipt");
        FoxSpiritTransferValidation.run(server, owner, blade);
        FoxCompanionValidation.run(owner, blade);
        owner.getInventory().clearContent();
        clone.getInventory().clearContent();
        owner.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
        clone.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
    }

    private static CompoundTag progress(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                .getCompound("MaidWeaponShrineFoxStory");
    }

    private static void check(boolean result, String message) {
        if (!result) throw new IllegalStateException(message);
        LogUtils.getLogger().info("Shrine fixture: {}", message);
    }

    private ShrineFoxValidation() { }
}

package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.compat.BladeTetraStoryCompat;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.event.FoxChallengeEvents;
import com.maidweapon.forge.system.fox.FoxSpiritTransferService;
import com.maidweapon.forge.system.fox.ShrineFoxStory;
import com.maidweapon.forge.system.fox.challenge.*;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Opt-in native-world fixture; no Boss entity or clear rewards are fabricated. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class FoxChallengeValidation {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.foxChallenge.validation")) return;
        try {
            run(event.getServer());
            LogUtils.getLogger().info("FOX_CHALLENGE_PASS");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("FOX_CHALLENGE_FAIL", failure);
        } finally { event.getServer().halt(false); }
    }

    private static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("270bca8c-093b-46f5-8a42-dfc69aa4f177"), "FoxChallengeFixture"));
        // Forge's fake connection discards teleport(), including its position update.
        // Use the vanilla listener with an unconnected transport so actual teleport logic is tested.
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND), player);
        player.getInventory().clearContent();
        player.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
        player.teleportTo(level, 1024.5, 100, 1024.5, 30, 0);
        var point = FoxChallengeService.position(player);
        check(server.getLevel(FoxChallengeService.LEVEL) != null, "realm codec loads without optional providers");
        check(FoxChallengeService.enter(player, ItemStack.EMPTY, point), "administrator preview enters");
        var arena = player.serverLevel();
        var origin = FoxChallengeService.origin(player);
        check(arena.getBlockState(origin).is(Blocks.BARRIER), "actual barrier fighting floor");
        check(arena.getBlockState(origin.offset(24, 4, 0)).is(Blocks.BARRIER), "enclosed physical boundary");
        check(arena.getBlockState(origin.above()).isAir(), "combat center remains empty");
        check(arena.dimensionType().bedWorks(), "beds cannot explode by dimension rules");
        check(arena.getBiome(origin).value().getMobSettings().getMobs(net.minecraft.world.entity.MobCategory.MONSTER).isEmpty(),
                "no natural monster spawns");
        var marker = origin.offset(0, -6, 0);
        check(arena.getBlockState(marker).is(Blocks.BEDROCK), "cell generated once");
        FoxChallengeArena.build(arena, origin);
        check(arena.getBlockState(origin.offset(0, -7, 0)).is(Blocks.BEDROCK), "scenery upgrade has its own one-time marker");
        // Emulate a legacy cell: its floor marker exists, but its scenery version does not.
        arena.setBlock(origin.offset(0, -7, 0), Blocks.AIR.defaultBlockState(), 2);
        arena.setBlock(origin.offset(22, -3, 0), Blocks.AIR.defaultBlockState(), 2);
        FoxChallengeArena.build(arena, origin);
        check(arena.getBlockState(origin.offset(22, -3, 0)).is(Blocks.CRYING_OBSIDIAN), "broken guiding arc stays below invisible floor");
        arena.setBlock(origin.offset(22, -3, 0), Blocks.BLACKSTONE.defaultBlockState(), 2);
        FoxChallengeArena.build(arena, origin);
        check(arena.getBlockState(origin.offset(22, -3, 0)).is(Blocks.BLACKSTONE), "repeat entry does not rebuild already-upgraded scenery");
        arena.setBlock(origin.offset(22, -3, 0), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
        check(FoxChallengeService.leave(player) && player.level() == level && player.getX() == 1024.5,
                "preview return restores original world and position");
        check(!FoxChallengeService.hasReturn(player), "return ticket clears");
        check(FoxChallengeService.enter(player, ItemStack.EMPTY, point), "reconnect fixture enters");
        FoxChallengeEvents.login(new PlayerEvent.PlayerLoggedInEvent(player));
        check(player.level() == level && !FoxChallengeService.hasReturn(player), "reconnect in arena returns safely");
        check(FoxChallengeService.enter(player, ItemStack.EMPTY, point), "re-entry uses saved cell");
        check(FoxChallengeService.origin(player).equals(origin), "same player retains cell");
        player.teleportTo(arena, origin.getX() + 80, 70, origin.getZ(), 0, 0);
        FoxChallengeEvents.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                net.minecraftforge.event.TickEvent.Phase.END, player));
        check(FoxChallengeArena.contains(origin, player.getX(), player.getY(), player.getZ()),
                "out-of-range player returns inside own cell");
        var clone = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("b6ea49ad-a921-4b75-b0f9-f394b25b92b3"), "FoxChallengeClone"));
        clone.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND), clone);
        FoxChallengeService.copyReturn(player, clone);
        FoxChallengeEvents.respawn(new PlayerEvent.PlayerRespawnEvent(clone, false));
        check(!FoxChallengeService.hasReturn(clone) && clone.getX() == 1024.5, "death clone returns without inventory edits");
        FoxChallengeEvents.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
        check(player.level() == level && !FoxChallengeService.hasReturn(player), "logout returns safely");
        if (!SlashBladeCompat.isLoaded()) {
            check(FoxChallengeEntry.whiteFox(player).isEmpty(), "missing SlashBlade has no story entrance");
            return;
        }
        var template = level.getStructureManager().get(ResourceLocation.parse("maid_weapon:shinkitsu_shrine")).orElseThrow();
        BlockPos shrine = new BlockPos(1024, 96, 1024);
        // Fake players do not provide client chunk tickets. Make the fixture chunks accessible.
        var managerField = net.minecraft.server.level.ServerLevel.class.getDeclaredField("entityManager");
        managerField.setAccessible(true);
        var entities = (net.minecraft.world.level.entity.PersistentEntitySectionManager<?>) managerField.get(level);
        for (int dx = 0; dx < 47; dx += 16) for (int dz = 0; dz < 47; dz += 16) {
            entities.updateChunkStatus(new net.minecraft.world.level.ChunkPos(shrine.offset(dx, 0, dz)),
                    net.minecraft.server.level.FullChunkStatus.FULL);
        }
        // Repeatable private fixture removes only its own old generated stands.
        level.getEntitiesOfClass(ItemFrame.class, new net.minecraft.world.phys.AABB(shrine, shrine.offset(47, 15, 47)))
                .forEach(ItemFrame::discard);
        for (int dx = 0; dx < 47; dx += 16) for (int dz = 0; dz < 47; dz += 16) {
            var settings = com.maidweapon.forge.worldgen.ShinkitsuShrinePiece.settings().setBoundingBox(
                    new net.minecraft.world.level.levelgen.structure.BoundingBox(shrine.getX() + dx, 0,
                            shrine.getZ() + dz, shrine.getX() + dx + 15, 319, shrine.getZ() + dz + 15));
            check(template.placeInWorld(level, shrine, shrine, settings, level.random, 2), "clipped shrine placement");
        }
        var offerings = level.getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(shrine, shrine.offset(47, 15, 47)),
                entity -> entity.getTags().contains("maid_weapon_shrine_offering"));
        check(offerings.size() == 1, "one real shrine offering is placed");
        var main = (ItemFrame) offerings.get(0);
        ItemStack white = main.getItem().copy();
        main.setItem(ItemStack.EMPTY);
        check(main.getPos().equals(shrine.offset(ShrineRitualSites.WHITE_STAND)), "native main stand anchor");
        check(java.util.Arrays.stream(ShrineRitualSites.get(level).save(new CompoundTag()).getLongArray("Origins"))
                .anyMatch(value -> value == shrine.asLong()), "shrine ritual anchor registered");
        FoxSpiritTransferService.claimOffering(player, white);
        player.getInventory().setItem(0, white);
        check(!FoxChallengeEntry.whiteFox(player).isEmpty(), "original shrine White Fox is the guide");
        level.setDayTime(6000);
        ShrineFoxStory.observe(player);
        check(story(player).getBoolean("Greeting") && story(player).getLong("FirstDay") == 0
                        && !story(player).getBoolean("Encounter") && !story(player).getBoolean("DreamMemory"),
                "acquisition day records date and greets without request");
        check(!story(player).getBoolean("DreamHint"), "taking White Fox away from secondary shrine gives no dream directions");
        if (BladeTetraStoryCompat.isLoaded()) {
            check(!BladeTetraStoryCompat.hasVisitedDivineDomain(player), "provider installation alone does not imply knowledge of Divine Domain");
            check(!story(player).getBoolean("Encounter") && !story(player).getBoolean("Reunion"),
                    "same-day request waits without Akatsuki");
            var akatsuki = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("slashblade:slashblade")));
            check(com.maidweapon.forge.api.IntrinsicSpiritApi.ensureIntrinsicSpirit(player, akatsuki,
                    "blade_tetra:akatsuki", "Akatsuki"), "real intrinsic Akatsuki channel created for late reunion");
            player.getInventory().setItem(1, akatsuki);
            ShrineFoxStory.observe(player);
            check(story(player).getBoolean("Reunion") && story(player).getBoolean("Encounter"),
                    "bringing Akatsuki later on acquisition day immediately opens reunion");
            var receipt = story(player).copy();
            ShrineFoxStory.observe(player);
            check(receipt.equals(story(player)), "late reunion never repeats");
            ShrineFoxStory.copyProgress(player, clone);
            check(receipt.equals(story(clone)), "death clone keeps date and reunion receipt");
            player.getInventory().setItem(1, ItemStack.EMPTY);
        }
        var black = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("slashblade:slashblade")));
        var capability = (Capability<?>) Class.forName("mods.flammpfeil.slashblade.item.ItemSlashBlade")
                .getField("BLADESTATE").get(null);
        Object bladeState = black.getCapability(capability).resolve().orElseThrow();
        bladeState.getClass().getMethod("setTranslationKey", String.class).invoke(bladeState, "item.slashblade.fox_black");
        BlockPos anchor = shrine.offset(ShrineRitualSites.BLACK_STAND);
        var stand = (ItemFrame) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("slashblade:blade_stand_entity")).create(level);
        var nbt = new CompoundTag();
        var standPosition = new net.minecraft.nbt.ListTag();
        standPosition.add(net.minecraft.nbt.DoubleTag.valueOf(anchor.getX() + 0.5));
        standPosition.add(net.minecraft.nbt.DoubleTag.valueOf(anchor.getY() + 0.375));
        standPosition.add(net.minecraft.nbt.DoubleTag.valueOf(anchor.getZ() + 0.5));
        nbt.put("Pos", standPosition);
        nbt.putString("StandType", "slashblade:bladestand_2");
        nbt.putByte("Facing", (byte) 1);
        nbt.putInt("TileX", anchor.getX()); nbt.putInt("TileY", anchor.getY()); nbt.putInt("TileZ", anchor.getZ());
        stand.load(nbt); stand.setItem(black);
        level.addFreshEntity(stand);
        check(stand.getPos().equals(anchor), "native secondary stand anchor");
        check(level.getBlockState(anchor.below()).is(Blocks.POLISHED_BLACKSTONE_BRICKS), "original raised dais material");
        check(SlashBladeCompat.isNamedBlade(stand.getItem(), "item.slashblade.fox_black"), "native Black Fox identity");
        check(!level.getEntitiesOfClass(ItemFrame.class, new net.minecraft.world.phys.AABB(anchor).inflate(1)).isEmpty(),
                "native secondary stand is spatially visible");
        var blackBefore = stand.getItem().save(new CompoundTag());
        BlockPos bed = shrine.offset(22, 4, 36);
        level.setBlock(bed.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(bed.south().below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(bed, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH)
                .setValue(BedBlock.PART, BedPart.FOOT), 2);
        level.setBlock(bed.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH)
                .setValue(BedBlock.PART, BedPart.HEAD), 2);
        check(ShrineRitualSites.prepared(level, bed.south()), "native Black Fox stand on authored secondary dais");
        main.discard();
        check(ShrineRitualSites.prepared(level, bed.south()), "recorded shrine survives removal of original offering stand");
        check(!ShrineRitualSites.prepared(level, bed.offset(40, 0, 0)), "unrelated bed does not open ritual");
        stand.setItem(new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        check(!ShrineRitualSites.prepared(level, bed.south()), "ordinary displayed sword is not Black Fox");
        stand.setItem(black);
        player.getInventory().setItem(0, ItemStack.EMPTY);
        check(FoxChallengeEntry.whiteFox(player).isEmpty(), "missing original White Fox cannot guide");
        player.getInventory().setItem(0, white);
        var whiteBefore = white.save(new CompoundTag()).copy();
        if (!BladeTetraStoryCompat.isLoaded()) {
            player.teleportTo(level, bed.getX() + 0.5, bed.getY(), bed.getZ() - 0.5, 0, 0);
            ShrineFoxStory.observe(player);
            check(!story(player).getBoolean("DreamHint") && !story(player).getBoolean("DreamMemory"),
                    "secondary shrine proximity does not bypass acquisition-day delay");
            player.getInventory().setItem(0, ItemStack.EMPTY);
            level.setDayTime(24000);
            ShrineFoxStory.observe(player);
            check(!story(player).getBoolean("DreamMemory"), "next-day reminder pauses while original blade is absent");
            player.getInventory().setItem(0, white);
            ShrineFoxStory.observe(player);
            check(story(player).getBoolean("DreamHint") && !story(player).getBoolean("Encounter"),
                    "bringing blade back next day at secondary shrine gives dream directions, not Divine plot");
            var receipt = story(player).copy();
            ShrineFoxStory.observe(player);
            check(receipt.equals(story(player)), "secondary shrine dream directions never repeat");
            ShrineFoxStory.copyProgress(player, clone);
            check(receipt.equals(story(clone)), "death clone keeps reminder date and standalone receipt");
            level.setWeatherParameters(6000, 6000, false, false);
            level.setDayTime(6000);
            level.updateSkyBrightness();
            check(player.startSleepInBed(bed.south()).left().isPresent(), "daytime cannot start the sleep ritual");
            FoxChallengeEntry.tick(player);
            check(!FoxChallengeService.inside(player), "failed sleep never teleports");
            level.setDayTime(18000);
            level.updateSkyBrightness();
            var sleep = player.startSleepInBed(bed.south());
            check(sleep.right().isPresent(), "native bed accepts sleep: " + sleep);
            // FakePlayer.tick is intentionally inert; advance vanilla's counter, not an invented sleep flag.
            var counter = Player.class.getDeclaredField("sleepCounter"); counter.setAccessible(true); counter.setInt(player, 20);
            FoxChallengeEntry.tick(player);
            check(FoxChallengeService.inside(player), "sleep ritual enters the real dimension");
            check(stand.getItem().isEmpty() && BlackFoxShrineReturn.get(server).pending(player.getUUID())
                    && whiteBefore.equals(white.save(new CompoundTag())), "ritual escrows real Black Fox and leaves White Fox intact");
            check(FoxChallengeService.leave(player), "dream route returns");
            check(blackBefore.equals(stand.getItem().save(new CompoundTag())), "loss restores original blade unchanged");
            stand = BlackFoxShrineReturnValidation.run(player, stand);
            player.getInventory().setItem(0, white);
        } else {
            validateDivine(server, player, white);
        }
        validateCompanionTravel(player, white);
        player.getInventory().clearContent();
        FoxChallengeDropsValidation.run(player);
        stand.discard();
    }

    private static void validateCompanionTravel(net.minecraft.server.level.ServerPlayer player, ItemStack white) throws Exception {
        check(com.maidweapon.forge.system.fox.FoxSpiritCompanionService.initialize(player, white),
                "real shrine White Fox contract initializes");
        player.getInventory().selected = 0;
        var id = UUID.fromString(com.maidweapon.forge.system.contract.ContractCarrierData.getBoundMaidUUID(white));
        var binding = com.maidweapon.forge.system.contract.ContractCarrierData.getBindingId(white);
        com.maidweapon.forge.system.deployment.ContractCompanionService.disconnect(player);
        com.maidweapon.forge.system.deployment.ContractCompanionService.toggle(player);
        var maid = (net.minecraft.world.entity.Mob) player.serverLevel().getEntity(id);
        check(maid != null, "real White Fox manifests before travel");
        maid.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new ItemStack(net.minecraft.world.item.Items.DIAMOND_AXE));
        var from = player.serverLevel();
        var point = FoxChallengeService.position(player);
        var managerField = net.minecraft.server.level.ServerLevel.class.getDeclaredField("entityManager");
        managerField.setAccessible(true);
        var entities = (net.minecraft.world.level.entity.PersistentEntitySectionManager<?>) managerField.get(from);
        entities.updateChunkStatus(new net.minecraft.world.level.ChunkPos(
                BlockPos.containing(player.getX() + 40, player.getY(), player.getZ())),
                net.minecraft.server.level.FullChunkStatus.FULL);
        maid.moveTo(player.getX() + 40, player.getY(), player.getZ(), 0, 0);
        var before = white.save(new CompoundTag()).copy();
        check(!FoxChallengeService.enter(player, white, point) && player.serverLevel() == from,
                "out-of-range spirit rejects travel");
        check(from.getEntity(id) == maid && before.equals(white.save(new CompoundTag())),
                "rejected travel changes neither blade nor live entity");
        maid.moveTo(player.getX() + 2, player.getY(), player.getZ(), 0, 0);
        check(FoxChallengeService.enter(player, white, point) && from.getEntity(id) == null,
                "nearby live spirit is safely recalled before dimension travel");
        var stored = com.maidweapon.forge.system.MaidEntityDataCodec.read(white.getTag());
        check(stored.getUUID("UUID").equals(id) && stored.getList("HandItems", 10).getCompound(0)
                        .getString("id").equals("minecraft:diamond_axe")
                        && binding.equals(com.maidweapon.forge.system.contract.ContractCarrierData.getBindingId(white)),
                "travel preserves spirit UUID, actual equipment and interior binding");
        check(FoxChallengeService.leave(player), "full companion contract returns safely");
        com.maidweapon.forge.system.deployment.ContractCompanionService.disconnect(player);
    }

    @SuppressWarnings("unchecked")
    private static void validateDivine(MinecraftServer server, net.minecraft.server.level.ServerPlayer player, ItemStack white) throws Exception {
        Class<?> manager = Class.forName("dev.bladetetra.challenge.DivineDomainManager");
        Class<?> sessionType = Class.forName("dev.bladetetra.challenge.DivineDomainManager$Session");
        var constructor = sessionType.getDeclaredConstructor(long.class, int.class, int.class); constructor.setAccessible(true);
        long id = 990001L;
        Object session = constructor.newInstance(id, 2048, 2048);
        var state = sessionType.getDeclaredField("state"); state.setAccessible(true);
        var participants = sessionType.getDeclaredField("players"); participants.setAccessible(true);
        ((java.util.Set<UUID>) participants.get(session)).add(player.getUUID());
        var sessionsField = manager.getDeclaredField("SESSIONS"); sessionsField.setAccessible(true);
        var sessions = (java.util.Map<Long, Object>) sessionsField.get(null); sessions.put(id, session);
        try {
            var dimension = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse("blade_tetra:divine_domain")));
            var data = player.getPersistentData();
            var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
            persisted.putBoolean("blade_tetra_divine_afterword_received", true); data.put(Player.PERSISTED_NBT_TAG, persisted);
            data.putLong("blade_tetra_divine_challenge", id);
            data.putInt("blade_tetra_divine_origin_x", 2048); data.putInt("blade_tetra_divine_origin_z", 2048);
            data.putString("blade_tetra_origin_dimension", "minecraft:overworld");
            data.putDouble("blade_tetra_origin_x", 1024.5); data.putDouble("blade_tetra_origin_y", 100);
            data.putDouble("blade_tetra_origin_z", 1024.5);
            player.teleportTo(dimension, 2071.5, 64.2, 2048.5, 0, 0);
            state.set(session, Enum.valueOf((Class) state.getType(), "ACTIVE"));
            check(BladeTetraStoryCompat.clearedRoute(player) == null, "prior clear cannot bypass active Divine replay");
            ShrineFoxStory.observe(player);
            check(story(player).getBoolean("DivineRecognized") && !ShrineFoxStory.hasHeardDivineEcho(player),
                    "actual arrival recognizes atmosphere but active replay never opens echo");
            state.set(session, Enum.valueOf((Class) state.getType(), "CLEARED"));
            check(BladeTetraStoryCompat.clearedRoute(player) != null, "actual current Divine clear recognized read-only");
            player.tickCount = 20;
            FoxChallengeEntry.tick(player);
            check(!player.getPersistentData().contains("MaidWeaponFoxJumpGuide"), "jump directions wait for the Divine echo");
            ShrineFoxStory.observe(player);
            check(ShrineFoxStory.hasHeardDivineEcho(player), "actual cleared Divine scene opens echo");
            var receipt = story(player).copy();
            ShrineFoxStory.observe(player);
            check(receipt.equals(story(player)), "Divine echo does not repeat");
            player.teleportTo(dimension, 2074.5, 61, 2048.5, 0, 0);
            player.setDeltaMovement(0, -1, 0);
            FoxChallengeEntry.tick(player);
            check(!FoxChallengeService.inside(player), "unguided fall does not open passage");
            player.teleportTo(dimension, 2071.5, 64.2, 2048.5, 0, 0);
            FoxChallengeEntry.tick(player);
            player.teleportTo(dimension, 2074.5, 61, 2048.5, 0, 0);
            // Teleport clears the guide through the dimension hook only on an actual dimension change.
            player.setDeltaMovement(0, -1, 0);
            FoxChallengeEntry.tick(player);
            check(FoxChallengeService.inside(player), "guided east-edge fall enters before provider recovery");
            check(FoxChallengeService.leave(player) && player.level() == server.overworld(), "Divine route returns to original outside world");
            check(BladeTetraStoryCompat.hasVisitedDivineDomain(player), "returning from Divine Domain keeps narrative familiarity");
        } finally { sessions.remove(id); }
    }

    private static CompoundTag story(net.minecraft.server.level.ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                .getCompound("MaidWeaponShrineFoxStory");
    }

    private static void check(boolean value, String description) {
        if (!value) throw new IllegalStateException(description);
        LogUtils.getLogger().info("FOX_CHALLENGE_CHECK {}", description);
    }
}

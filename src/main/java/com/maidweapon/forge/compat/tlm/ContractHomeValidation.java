package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.interior.ContractInteriorSavedData;
import com.maidweapon.forge.system.interior.home.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.registries.ForgeRegistries;

/** Explicit headless preview fixture only; never called by player-home runtime. */
public final class ContractHomeValidation {
    public static void run(ServerLevel level) throws ReflectiveOperationException {
        check(Boolean.getBoolean("contractblade.home.validation"), "preview validation disabled");
        var saved = new ContractInteriorSavedData();
        saved.chooseTerrain("home-test-a", "sakura_garden");
        saved.markGenerated("home-test-a", 5);
        var state = saved.getOrCreate("home-test-a").home();
        state.mode = ContractHomeClock.Mode.REAL_TIME; state.zone = "+08:00";
        state.guideReceived = true; state.slot = 123456; state.seed = 76543;
        state.activity = ContractHomeActivity.SLEEP; state.target = "test-bed";
        state.lastSimulatedAt = 1234; state.activityStartedAt = 1200; state.maidId = "test-maid";
        var reloaded = ContractInteriorSavedData.load(saved.save(new CompoundTag()));
        check(reloaded.getOrCreate("home-test-a").home().save().equals(state.save()), "plot NBT roundtrip");
        check(!reloaded.getOrCreate("home-test-b").home().guideReceived, "binding guide isolation");
        check(reloaded.getOrCreate("home-test-a").generatedStage() == 5, "terrain preserved");
        check(ContractHomeOfflineState.load(new CompoundTag()).mode == ContractHomeClock.Mode.MINECRAFT_TIME, "legacy migration");
        check(!ContractHomeOfflineState.load(new CompoundTag()).guideReceived, "legacy receipt");

        // Exercise real incremental expansion, not just independent render snapshots.
        var terrainSaved = new ContractInteriorSavedData();
        terrainSaved.chooseTerrain("incremental-fixture", "sakura_garden");
        BlockPos terrainOrigin = new BlockPos(-22000, 80, -22000);
        com.maidweapon.forge.system.interior.ContractInteriorTerrainBuilder.ensureGenerated(level, terrainOrigin,
                new com.maidweapon.forge.system.interior.ContractInteriorProfile(1, 1), terrainSaved, "incremental-fixture");
        BlockPos playerBuild = terrainOrigin.offset(3, 3, 3);
        level.setBlockAndUpdate(playerBuild, Blocks.DIAMOND_BLOCK.defaultBlockState());
        com.maidweapon.forge.system.interior.ContractInteriorTerrainBuilder.ensureGenerated(level, terrainOrigin,
                new com.maidweapon.forge.system.interior.ContractInteriorProfile(5, 6), terrainSaved, "incremental-fixture");
        check(level.getBlockState(playerBuild).is(Blocks.DIAMOND_BLOCK), "player building survives Lv1 to Lv10 expansion");
        check(terrainSaved.getOrCreate("incremental-fixture").generatedStage() == 5, "incremental generation stage");

        if (!Boolean.getBoolean("contractblade.home.nativeValidation")) {
            System.out.println("CONTRACT_HOME_DATA_VALIDATION_PASSED (native TLM tests not requested)");
            return;
        }
        check(TlmEntityAdapter.maidClass() != null, "native validation requires TLM");
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("ff372890-6a64-4ab7-a6f8-76fe3131f3a2"), "HomeFixture"));
        var contract = new net.minecraft.world.item.ItemStack(com.maidweapon.forge.init.ModItems.MAID_SWORD.get());
        com.maidweapon.forge.item.MaidWeaponItem.setMaidData(contract,
                new com.maidweapon.common.data.MaidWeaponData("Home fixture"));
        com.maidweapon.forge.item.MaidWeaponItem.setOwner(contract, player);
        String binding = com.maidweapon.forge.item.MaidWeaponItem.ensureBindingId(contract);
        var runtimeSaved = ContractInteriorSavedData.get(level.getServer());
        runtimeSaved.chooseTerrain(binding, "plains_garden");
        var plot = runtimeSaved.getOrCreate(binding);
        BlockPos origin = new BlockPos(ContractInteriorSavedData.originX(plot), 80, ContractInteriorSavedData.originZ(plot));
        com.maidweapon.forge.system.interior.ContractInteriorTerrainBuilder.ensureGenerated(level, origin,
                new com.maidweapon.forge.system.interior.ContractInteriorProfile(1, 1), runtimeSaved, binding);
        player.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        player.getInventory().clearContent();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            player.getInventory().setItem(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64));
        ContractInteriorGuideService.give(player, runtimeSaved, plot);
        check(!plot.home().guideReceived, "full inventory defers guide receipt");
        player.getInventory().clearContent();
        player.getInventory().add(contract);
        contract = player.getInventory().getItem(0);
        ContractInteriorGuideService.give(player, runtimeSaved, plot);
        check(plot.home().guideReceived, "guide receipt after successful inventory delivery");
        int books = player.getInventory().countItem(net.minecraft.world.item.Items.WRITTEN_BOOK);
        ContractInteriorGuideService.give(player, runtimeSaved, plot);
        check(books == 1 && player.getInventory().countItem(net.minecraft.world.item.Items.WRITTEN_BOOK) == 1, "guide only once");
        CompoundTag session = new CompoundTag(); session.putString("Binding", binding);
        player.getPersistentData().put("MaidWeaponInteriorReturn", session);
        check(binding.equals(com.maidweapon.forge.system.interior.ContractInteriorService.activeOwnedBinding(player)),
                "owner binding inside plot");
        player.setPos(origin.getX() + 15.5, origin.getY(), origin.getZ() + 15.5);
        check(com.maidweapon.forge.system.interior.ContractInteriorService.activeOwnedBinding(player).isEmpty(),
                "rounded footprint excludes corner outside land");
        player.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
            level.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        Mob maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(level);
        maid.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        check(level.addFreshEntity(maid), "real maid spawn");
        TlmEntityAdapter.tame(maid, player);
        maid.getPersistentData().putString(ContractMaidKeys.ENTITY_BINDING_ID, binding);
        com.maidweapon.forge.item.MaidWeaponItem.setBoundMaidUUID(contract, maid.getStringUUID());
        Entity chair = null;
        BlockPos bedPos = origin.offset(1, 0, 0);
        var adapter = new TlmHomeFurnitureAdapter();
        try {
            String oldTask = TlmEntityAdapter.taskId(maid);
            check(TlmHomeBehaviorController.begin(maid), "behavior scope install");
            chair = (Entity) Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair")
                    .getConstructor(Level.class).newInstance(level);
            chair.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + 1.5);
            check(level.addFreshEntity(chair), "real chair spawn");
            var seat = adapter.entityTarget(chair).orElseThrow();
            check(adapter.start(level, seat, maid), "real chair mount");
            check(maid.getVehicle() == chair && adapter.running(level, seat, maid), "chair passenger authority");
            adapter.stop(maid);
            chair.discard();
            check(!adapter.valid(level, seat, maid), "removed chair invalidated");

            var bed = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("touhou_little_maid", "maid_bed"));
            check(bed != null && bed != Blocks.AIR, "TLM maid bed registered");
            // Install both halves using the same property objects as TLM BlockMaidBed.
            var head = bed.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD);
            var footPos = bedPos.relative(head.getValue(BedBlock.FACING).getOpposite());
            level.setBlock(footPos, head.setValue(BedBlock.PART, BedPart.FOOT), 2);
            level.setBlock(bedPos, head, 2);
            var sleep = adapter.blockTarget(level, bedPos).orElseThrow();
            var index = new ContractHomeFurnitureRegistry(origin, 16);
            index.refresh(level, level.getGameTime());
            check(index.find(sleep.key()).isPresent(), "loaded block entity index discovers bed");
            check(adapter.start(level, sleep, maid), "native maid sleeping API");
            tickBrain(level, maid);
            check(adapter.running(level, sleep, maid), "home sleep survives native world-time mismatch");
            adapter.stop(maid);
            TlmHomeBehaviorController.restore(maid);
            var home = plot.home();
            home.mode = ContractHomeClock.Mode.REAL_TIME;
            home.slot = ContractHomeClock.read(home.mode, home.zone, java.time.Instant.now(), 0).slot();
            home.maidId = maid.getStringUUID(); home.activity = ContractHomeActivity.SLEEP; home.target = sleep.key();
            ContractHomeRuntime.start(player, binding, maid, runtimeSaved, plot);
            ContractHomeRuntime.tick(player);
            check(maid.isSleeping(), "runtime drives real furniture");
            long sameSlotSeed = home.seed;
            ContractHomeRuntime.stop(player);
            check(!maid.isSleeping(), "session stop releases real sleep");
            ContractHomeRuntime.pauseUncaptured(maid, binding);
            check(maid.isNoAi(), "unattended resident stops AI");
            ContractHomeRuntime.start(player, binding, maid, runtimeSaved, plot);
            ContractHomeRuntime.tick(player);
            check(!maid.isNoAi() && maid.isSleeping() && sameSlotSeed == home.seed, "same-slot reentry preserves selection");
            level.setBlockAndUpdate(bedPos, Blocks.AIR.defaultBlockState());
            ContractHomeRuntime.tick(player);
            check(!maid.isSleeping() && home.activity == ContractHomeActivity.IDLE, "removed target safely ends activity");
            ContractHomeRuntime.clockChanged(player);
            check(home.target.isEmpty(), "clock change cannot retain destroyed target");
            ContractHomeRuntime.stop(player);
            check(!adapter.valid(level, sleep, maid), "removed bed invalidated");
            TlmHomeBehaviorController.restore(maid);
            check(oldTask.equals(TlmEntityAdapter.taskId(maid)), "original task restored");
            check(!maid.getPersistentData().contains("ContractHomeBehaviorSettings"), "behavior backup released");
            System.out.println("CONTRACT_HOME_NATIVE_VALIDATION_PASSED: real TLM bed/chair, loaded index, invalidation, brain scope, restore, NBT isolation");
        } finally {
            ContractHomeRuntime.stop(player);
            player.getInventory().clearContent(); player.getPersistentData().remove("MaidWeaponInteriorReturn");
            adapter.stop(maid); TlmHomeBehaviorController.restore(maid);
            maid.discard(); if (chair != null) chair.discard();
            level.setBlockAndUpdate(bedPos, Blocks.AIR.defaultBlockState());
        }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void tickBrain(ServerLevel level, Mob maid) {
        Brain brain = maid.getBrain();
        brain.tick(level, maid);
    }
    private static void check(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException("Home validation: " + reason);
    }
    private ContractHomeValidation() {}
}

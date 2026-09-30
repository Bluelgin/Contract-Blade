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
        player.setPos(origin.getX() + 100.5, origin.getY(), origin.getZ() + .5);
        check(com.maidweapon.forge.system.interior.ContractInteriorService.activeOwnedBinding(player).isEmpty(),
                "same-dimension plot escape is detected");
        // FakePlayer has no normal client connection, so cross-position ServerPlayer.teleportTo
        // is not a faithful recovery assertion here. validate_home_life.py keeps a source
        // tripwire on recoverFromVoid's same-dimension containment branch.
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
        BlockPos boardPos = origin.offset(-1, 0, 0);
        BlockPos joyPos = origin.offset(0, 0, -1);
        BlockPos picnicPos = origin.offset(0, 0, 2);
        var adapter = new TlmHomeFurnitureAdapter();
        try {
            String oldTask = TlmEntityAdapter.taskId(maid);
            String oldSchedule = String.valueOf(maid.getClass().getMethod("getSchedule").invoke(maid));
            com.maidweapon.forge.system.MaidCareTaskSystem.rememberOriginalTask(contract, maid);
            check(TlmEntityAdapter.setAllDaySchedule(maid), "deployment schedule override");
            check("ALL".equals(TlmEntityAdapter.scheduleName(maid)), "deployment schedule set to ALL");
            com.maidweapon.forge.system.MaidCareTaskSystem.restoreOriginalTask(contract, maid);
            check(oldSchedule.equals(TlmEntityAdapter.scheduleName(maid)),
                    "deployment original schedule restored");
            com.maidweapon.forge.system.MaidCareTaskSystem.clearOriginalTask(contract);
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

            var boardBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("touhou_little_maid", "gomoku"));
            check(boardBlock != null && boardBlock != Blocks.AIR, "TLM gomoku registered");
            level.setBlockAndUpdate(boardPos, boardBlock.defaultBlockState());
            var boardAdapter = new TlmHomeBoardGameAdapter();
            var board = boardAdapter.blockTarget(level, boardPos).orElseThrow();
            check(board.activity() == ContractHomeActivity.PLAY, "board game indexed as PLAY");
            check(TlmHomeBehaviorController.beginBoardGame(maid), "native board-game task scope");
            check("touhou_little_maid:board_games".equals(TlmEntityAdapter.taskId(maid)),
                    "native board-game task selected");
            check("ALL".equals(String.valueOf(maid.getClass().getMethod("getSchedule").invoke(maid))),
                    "native board-game schedule selected");
            check(TlmHomeBehaviorController.endBoardGame(maid), "board-game task scope release");
            check(boardAdapter.start(level, board, maid), "native board-game sit");
            Entity boardSeat = maid.getVehicle();
            check(boardSeat != null, "board-game EntitySit created");
            for (int i = 0; i < 25; i++) boardSeat.tick();
            check(boardAdapter.running(level, board, maid), "board game survives TLM schedule guard");
            boardAdapter.stop(maid);
            check(!maid.isPassenger(), "board-game stop releases maid");

            var bookshelf = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("touhou_little_maid", "bookshelf"));
            check(bookshelf != null && bookshelf != Blocks.AIR, "TLM bookshelf registered");
            level.setBlockAndUpdate(joyPos, bookshelf.defaultBlockState());
            var joyAdapter = new TlmHomeJoyAdapter();
            var reading = joyAdapter.blockTarget(level, joyPos).orElseThrow();
            check(reading.activity() == ContractHomeActivity.READ, "bookshelf indexed as READ");
            check(joyAdapter.start(level, reading, maid), "native bookshelf sit");
            Entity joySeat = maid.getVehicle();
            check(joySeat != null, "bookshelf EntitySit created");
            maid.hurtTime = 5;
            check(!TlmHomeBehaviorController.shouldKeepManagedSeat(maid, joySeat),
                    "managed seat yields immediately to emergency behavior");
            maid.hurtTime = 0;
            for (int i = 0; i < 25; i++) joySeat.tick();
            check(joyAdapter.running(level, reading, maid),
                    "managed bookshelf survives world-time schedule mismatch");
            joyAdapter.stop(maid);
            check(!maid.isPassenger(), "managed bookshelf stop releases maid");
            level.setBlockAndUpdate(joyPos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(picnicPos, Blocks.AIR.defaultBlockState());

            var picnic = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("touhou_little_maid", "picnic_mat"));
            check(picnic != null && picnic != Blocks.AIR, "TLM picnic mat registered");
            level.setBlockAndUpdate(picnicPos, picnic.defaultBlockState());
            Object picnicTile = level.getBlockEntity(picnicPos);
            check(picnicTile != null, "picnic tile created");
            picnicTile.getClass().getMethod("setCenterPos", BlockPos.class).invoke(picnicTile, picnicPos);
            Object handler = picnicTile.getClass().getMethod("getHandler").invoke(picnicTile);
            handler.getClass().getMethod("setStackInSlot", int.class, net.minecraft.world.item.ItemStack.class)
                    .invoke(handler, 0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
            var picnicAdapter = new TlmHomePicnicAdapter();
            var meal = picnicAdapter.blockTarget(level, picnicPos).orElseThrow();
            check(meal.activity() == ContractHomeActivity.MEAL, "picnic indexed as MEAL");
            // Runtime only calls adapter.start after pathing within two blocks.
            // Earlier furniture leaves the fixture maid at its seat position, so
            // reproduce that real precondition rather than testing an impossible jump.
            maid.moveTo(picnicPos.getX() + 0.5, picnicPos.getY() + 0.2, picnicPos.getZ() + 0.5);
            check(picnicAdapter.start(level, meal, maid), "native picnic sit");
            Entity picnicSeat = maid.getVehicle();
            check(picnicSeat != null, "picnic EntitySit created");
            for (int i = 0; i < 25; i++) picnicSeat.tick();
            check(picnicAdapter.running(level, meal, maid),
                    "managed picnic survives world-time schedule mismatch");

            Class<?> mealTaskClass = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidHomeMealTask");
            Object mealTask = mealTaskClass.getConstructor().newInstance();
            java.lang.reflect.Method mealCheck = mealTaskClass.getDeclaredMethod(
                    "checkExtraStartConditions", ServerLevel.class, TlmEntityAdapter.maidClass());
            mealCheck.setAccessible(true);
            check(Boolean.TRUE.equals(mealCheck.invoke(mealTask, level, maid)),
                    "native Home Meal recognizes managed picnic");
            java.lang.reflect.Method mealStart = mealTaskClass.getDeclaredMethod(
                    "start", ServerLevel.class, TlmEntityAdapter.maidClass(), long.class);
            mealStart.setAccessible(true);
            mealStart.invoke(mealTask, level, maid, level.getGameTime());
            Object remaining = handler.getClass().getMethod("getStackInSlot", int.class).invoke(handler, 0);
            check(remaining instanceof net.minecraft.world.item.ItemStack stack && stack.isEmpty(),
                    "native Home Meal consumes picnic inventory");
            picnicAdapter.stop(maid);
            check(!maid.isPassenger(), "managed picnic stop releases maid");
            level.setBlockAndUpdate(picnicPos, Blocks.AIR.defaultBlockState());

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
            // As with picnic, runtime approaches the selected furniture before start().
            maid.moveTo(bedPos.getX() + 0.5, bedPos.getY() + 0.2, bedPos.getZ() + 0.5);
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

            maid.moveTo(origin.getX() + 100.5, origin.getY(), origin.getZ() + 0.5);
            ContractHomeRuntime.guardResident(maid);
            check(maid.blockPosition().closerThan(origin, 2.0), "resident maid plot escape is recovered");
            check(!maid.isSleeping(), "resident containment releases stale furniture pose");
            ContractHomeRuntime.tick(player);
            check(maid.isSleeping() && sameSlotSeed == home.seed,
                    "resident containment resumes deterministic home selection");

            level.setBlockAndUpdate(bedPos, Blocks.AIR.defaultBlockState());
            ContractHomeRuntime.tick(player);
            check(!maid.isSleeping() && home.activity == ContractHomeActivity.IDLE, "removed target safely ends activity");
            ContractHomeRuntime.clockChanged(player);
            check(home.target.isEmpty(), "clock change cannot retain destroyed target");
            ContractHomeRuntime.stop(player);
            check(!adapter.valid(level, sleep, maid), "removed bed invalidated");
            TlmHomeBehaviorController.restore(maid);
            check(oldTask.equals(TlmEntityAdapter.taskId(maid)), "original task restored");
            check(oldSchedule.equals(String.valueOf(maid.getClass().getMethod("getSchedule").invoke(maid))),
                    "original schedule restored");
            check(!maid.getPersistentData().contains("ContractHomeBehaviorSettings"), "behavior backup released");
            System.out.println("CONTRACT_HOME_NATIVE_VALIDATION_PASSED: real TLM bed/chair/board-game/Joy/picnic Home Meal, loaded index, managed-seat clock bridge, invalidation, brain scope, restore, NBT isolation");
        } finally {
            ContractHomeRuntime.stop(player);
            player.getInventory().clearContent(); player.getPersistentData().remove("MaidWeaponInteriorReturn");
            adapter.stop(maid); TlmHomeBehaviorController.restore(maid);
            maid.discard(); if (chair != null) chair.discard();
            level.setBlockAndUpdate(bedPos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(boardPos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(joyPos, Blocks.AIR.defaultBlockState());
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

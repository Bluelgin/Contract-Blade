package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.contract.ContractCarrierData;

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
        ContractCarrierValidation.dedicatedDeployment(level);
        ContractCarrierLossValidation.run(level.getServer().overworld());
        ContractHomeSafetyValidation.run(level);
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("ff372890-6a64-4ab7-a6f8-76fe3131f3a2"), "HomeFixture"));
        var contract = new net.minecraft.world.item.ItemStack(com.maidweapon.forge.init.ModItems.MAID_SWORD.get());
        com.maidweapon.forge.system.contract.ContractCarrierData.setMaidData(contract,
                new com.maidweapon.common.data.MaidWeaponData("Home fixture"));
        com.maidweapon.forge.system.contract.ContractCarrierData.setOwner(contract, player);
        String binding = com.maidweapon.forge.system.contract.ContractCarrierData.ensureBindingId(contract);
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
        ContractCarrierValidation.inventoryClicks(player, contract);
        contract = player.getInventory().getItem(0);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            level.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            // This isolated fixture room must not inherit random terrain-tree
            // obstacles when asserting that native furniture is reachable.
            for (int y = 0; y <= 3; y++) level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        Mob maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(level);
        maid.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        check(level.addFreshEntity(maid), "real maid spawn");
        TlmEntityAdapter.tame(maid, player);
        maid.getPersistentData().putString(ContractMaidKeys.ENTITY_BINDING_ID, binding);
        com.maidweapon.forge.system.contract.ContractCarrierData.setBoundMaidUUID(contract, maid.getStringUUID());
        Entity chair = null;
        BlockPos bedPos = origin.offset(1, 0, 0);
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
            adapter.stop(maid);
            TlmHomeBehaviorController.restore(maid);
            check(oldTask.equals(TlmEntityAdapter.taskId(maid)), "original task restored");
            check(oldSchedule.equals(TlmEntityAdapter.scheduleName(maid)), "original schedule restored");
            check(!maid.getPersistentData().contains("ContractHomeBehaviorSettings"), "behavior backup released");
            ContractHomeRuntime.start(player, binding, maid, runtimeSaved, plot);
            ContractHomeRuntime.pause(player);
            check(maid.isNoAi(), "unattended resident stops AI");
            ContractHomeRuntime.start(player, binding, maid, runtimeSaved, plot);
            check(!maid.isNoAi(), "attended resident resumes native AI");
            maid.moveTo(origin.getX() + 100.5, origin.getY(), origin.getZ() + .5);
            ContractHomeRuntime.guardResident(maid);
            check(maid.blockPosition().closerThan(origin, 2.0), "resident maid plot escape is recovered");
            ContractHomeRuntime.stop(player);
            level.setBlockAndUpdate(bedPos, Blocks.AIR.defaultBlockState());
            validateArrival(level, player, binding, maid, runtimeSaved, plot, origin);
            validateNativeOnline(level, player, binding, maid, runtimeSaved, plot, origin);
            System.out.println("CONTRACT_HOME_NATIVE_VALIDATION_PASSED: native living, bed/chair arrival, containment, restoration and NBT isolation");
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
    private static void validateArrival(ServerLevel level, net.minecraft.server.level.ServerPlayer player,
            String binding, Mob maid, ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot,
            BlockPos origin) throws ReflectiveOperationException {
        var home = plot.home();
        long oldDayTime = level.getServer().overworld().getDayTime();
        var oldMode = home.mode;
        java.util.UUID identity = maid.getUUID();
        BlockPos pos = origin.offset(2, 0, -2);
        Entity chair = null;
        Mob occupant = null;
        try {
            level.getServer().overworld().setDayTime(18000);
            home.mode = ContractHomeClock.Mode.MINECRAFT_TIME;
            var bed = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("touhou_little_maid", "maid_bed"));
            var head = bed.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD);
            BlockPos foot = pos.relative(head.getValue(BedBlock.FACING).getOpposite());
            level.setBlock(foot, head.setValue(BedBlock.PART, BedPart.FOOT), 2);
            level.setBlock(pos, head, 2);
            var sleep = new TlmHomeFurnitureAdapter().blockTarget(level, pos).orElseThrow();
            arrivalState(home, maid, ContractHomeActivity.SLEEP, sleep.key());
            maid.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            ContractHomeRuntime.start(player, binding, maid, saved, plot, true);
            check(maid.isSleeping(), "entry immediately restores real sleep rather than walking from player spawn");
            check(identity.equals(maid.getUUID()), "arrival keeps the same real maid UUID");
            ContractHomeRuntime.stop(player);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos wall = pos.offset(dx, 0, dz);
                if (wall.equals(pos) || wall.equals(foot)) continue;
                for (int dy = 0; dy <= 2; dy++) level.setBlockAndUpdate(wall.above(dy), Blocks.BARRIER.defaultBlockState());
            }
            arrivalState(home, maid, ContractHomeActivity.SLEEP, sleep.key());
            maid.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            var blockedCheckpoint = maid.position();
            ContractHomeRuntime.start(player, binding, maid, saved, plot, true);
            check(!maid.isSleeping() && maid.position().equals(blockedCheckpoint), "blocked furniture cannot force unsafe staging");
            ContractHomeRuntime.stop(player);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos wall = pos.offset(dx, 0, dz);
                if (wall.equals(pos) || wall.equals(foot)) continue;
                for (int dy = 0; dy <= 2; dy++) level.setBlockAndUpdate(wall.above(dy), Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(foot, Blocks.AIR.defaultBlockState());

            var keyboard = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("touhou_little_maid", "keyboard"));
            level.setBlockAndUpdate(pos, keyboard.defaultBlockState());
            var joy = new TlmHomeJoyAdapter();
            var play = joy.blockTarget(level, pos).orElseThrow();
            arrivalState(home, maid, ContractHomeActivity.PLAY, play.key());
            maid.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            ContractHomeRuntime.start(player, binding, maid, saved, plot, true);
            check(joy.running(level, play, maid), "entry immediately uses native keyboard seat/animation");
            check(!TlmHomeBehaviorController.shouldKeepManagedSeat(maid, maid.getVehicle()),
                    "Minecraft-mode furniture dismount is never cancelled by Contract Blade");
            var during = maid.position();
            ContractHomeRuntime.start(player, binding, maid, saved, plot);
            // TLM's native seat release can move the passenger beside its seat.
            check(maid.position().distanceToSqr(during) < 4, "ordinary session resume stays beside the same furniture");
            ContractHomeRuntime.stop(player);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());

            var bookshelf = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("touhou_little_maid", "bookshelf"));
            level.setBlockAndUpdate(pos, bookshelf.defaultBlockState());
            var read = joy.blockTarget(level, pos).orElseThrow();
            arrivalState(home, maid, ContractHomeActivity.READ, read.key());
            maid.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            ContractHomeRuntime.start(player, binding, maid, saved, plot, true);
            check(joy.running(level, read, maid), "entry restores native reading pose");
            ContractHomeRuntime.stop(player);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());

            chair = (Entity) Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair")
                    .getConstructor(Level.class).newInstance(level);
            chair.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            check(level.addFreshEntity(chair), "arrival chair spawned");
            var sit = new TlmHomeFurnitureAdapter().entityTarget(chair).orElseThrow();
            arrivalState(home, maid, ContractHomeActivity.SIT, sit.key());
            maid.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            ContractHomeRuntime.start(player, binding, maid, saved, plot, true);
            check(maid.getVehicle() == chair, "entry restores sitting/waiting on real chair");
            ContractHomeRuntime.stop(player);
            occupant = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(level);
            occupant.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            check(level.addFreshEntity(occupant) && occupant.startRiding(chair, true), "fixture chair occupied");
            arrivalState(home, maid, ContractHomeActivity.SIT, sit.key());
            maid.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            ContractHomeRuntime.start(player, binding, maid, saved, plot, true);
            check(!maid.isPassenger() && occupant.getVehicle() == chair, "arrival never steals an occupied chair");
            ContractHomeRuntime.stop(player);
            occupant.discard(); occupant = null;
            chair.discard();
            arrivalState(home, maid, ContractHomeActivity.SIT, sit.key());
            maid.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
            var checkpoint = maid.position();
            ContractHomeRuntime.start(player, binding, maid, saved, plot, true);
            check(!maid.isPassenger() && maid.position().equals(checkpoint), "missing furniture falls back to safe checkpoint");
            ContractHomeRuntime.stop(player);
            System.out.println("CONTRACT_HOME_ARRIVAL_VALIDATION_PASSED: native sleep, keyboard, reading, chair, same UUID, no mid-session teleport, missing furniture fallback");
        } finally {
            ContractHomeRuntime.stop(player);
            level.getServer().overworld().setDayTime(oldDayTime); home.mode = oldMode;
            if (chair != null) chair.discard();
            if (occupant != null) occupant.discard();
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }
    private static void validateNativeOnline(ServerLevel level, net.minecraft.server.level.ServerPlayer player,
            String binding, Mob maid, ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot,
            BlockPos origin) throws ReflectiveOperationException {
        long previousTime = level.getServer().overworld().getDayTime();
        var previousMode = plot.home().mode;
        String originalTask = TlmEntityAdapter.taskId(maid);
        var identity = maid.getUUID();
        try {
            plot.home().mode = ContractHomeClock.Mode.MINECRAFT_TIME;
            level.getServer().overworld().setDayTime(6000);
            maid.moveTo(origin.getX() + 7.5, origin.getY(), origin.getZ() + 5.5);
            var position = maid.position();
            ContractHomeRuntime.startOnVisit(player, binding, maid, saved, plot, false);
            check(TlmHomeBehaviorController.isNativeLiving(maid), "production resume hands online living to native TLM");
            check(maid.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.CORE), "native CORE remains active");
            var nativeSchedule = maid.getBrain().getSchedule();
            check("DAY".equals(TlmEntityAdapter.scheduleName(maid)), "home uses native DAY schedule");
            check(nativeSchedule.getActivityAt(18000) == net.minecraft.world.entity.schedule.Activity.REST,
                    "default native schedule retains night instead of a forced constant daytime");
            ContractHomeRuntime.clockChanged(player);
            check(maid.getBrain().getSchedule() == nativeSchedule,
                    "Minecraft clock observation never replaces native schedule");
            var nativeBrain = maid.getBrain();
            ContractHomeRuntime.tick(player);
            check(maid.getBrain() == nativeBrain, "online observation never rebuilds native brain");
            check(TlmEntityAdapter.setSchedule(maid, "NIGHT"), "player can select native night schedule");
            maid.getClass().getMethod("refreshBrain", ServerLevel.class).invoke(maid, level);
            var playerSchedule = maid.getBrain().getSchedule();
            ContractHomeRuntime.clockChanged(player);
            check("NIGHT".equals(TlmEntityAdapter.scheduleName(maid))
                            && maid.getBrain().getSchedule() == playerSchedule,
                    "default online observer respects player's manually selected native schedule");
            TlmEntityAdapter.setSchedule(maid, "DAY");
            maid.getClass().getMethod("refreshBrain", ServerLevel.class).invoke(maid, level);
            nativeSchedule = maid.getBrain().getSchedule();
            check(hasNativeGoal(maid.getBrain(), "MaidJoyTask") && hasNativeGoal(maid.getBrain(), "MaidRunOne")
                    && hasNativeGoal(maid.getBrain(), "MaidBedTask"), "native furniture, random look/walk and bed goals registered");
            tickBrain(level, maid);
            check(maid.position().equals(position) && identity.equals(maid.getUUID()), "native handoff does not teleport or duplicate resident");
            level.getServer().overworld().setDayTime(18000);
            ContractHomeRuntime.clockChanged(player);
            check(maid.getBrain().getSchedule() == nativeSchedule,
                    "day-night observation does not force REST or replace schedule");
            check(maid.level().getDayTime() == level.getServer().overworld().getDayTime(),
                    "native interior time agrees with displayed Minecraft time");
            ContractHomeRuntime.stop(player);
            check(!TlmHomeBehaviorController.isNativeLiving(maid) && originalTask.equals(TlmEntityAdapter.taskId(maid)),
                    "leaving releases native home scope and restores original task");
            System.out.println("CONTRACT_HOME_NATIVE_ONLINE_VALIDATION_PASSED: production handoff, native CORE/joy/random-walk/bed, native clock ownership, identity, original task restoration");
        } finally {
            ContractHomeRuntime.stop(player);
            level.getServer().overworld().setDayTime(previousTime);
            plot.home().mode = previousMode;
        }
    }
    private static boolean hasNativeGoal(Brain<?> brain, String name) throws ReflectiveOperationException {
        for (var field : Brain.class.getDeclaredFields()) {
            if (!java.util.Map.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            if (containsGoal(field.get(brain), name, 5)) return true;
        }
        return false;
    }
    private static boolean containsGoal(Object object, String name, int depth) {
        if (object == null || depth == 0) return false;
        if (object instanceof net.minecraft.world.entity.ai.behavior.BehaviorControl<?>)
            return object.getClass().getSimpleName().equals(name);
        if (object instanceof java.util.Map<?, ?> map)
            return map.values().stream().anyMatch(value -> containsGoal(value, name, depth - 1));
        if (object instanceof Iterable<?> values)
            for (Object value : values) if (containsGoal(value, name, depth - 1)) return true;
        return false;
    }
    private static void arrivalState(ContractHomeOfflineState home, Mob maid, ContractHomeActivity activity, String target) {
        home.maidId = maid.getStringUUID(); home.activity = activity; home.target = target;
        home.departedAt = java.time.Instant.now().toEpochMilli() - 1000;
        home.activityStartedAt = home.departedAt;
    }
    private static void check(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException("Home validation: " + reason);
    }
    private ContractHomeValidation() {}
}

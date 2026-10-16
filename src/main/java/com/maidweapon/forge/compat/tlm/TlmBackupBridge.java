package com.maidweapon.forge.compat.tlm;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.forge.system.ContractNbtGuard;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/** Read-only bridge for the verified TLM 1.5.3 world/data/maid_backups format; bounded IO off-thread. */
public final class TlmBackupBridge {
    public record Result(String file, CompoundTag data, String diagnostic) { }
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), runnable -> { var thread = new Thread(runnable, "Contract-Backup-Read"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.AbortPolicy());
    public static boolean supported() {
        try {
            var type = Class.forName("com.github.tartaricacid.touhoulittlemaid.world.backups.MaidBackupsManager");
            type.getMethod("getMaidIndexMap", ServerPlayer.class);
            type.getMethod("getMaidBackupFiles", ServerPlayer.class, UUID.class);
            type.getMethod("getMaidBackFile", ServerPlayer.class, UUID.class, String.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError absent) { return false; }
    }
    public static CompletableFuture<Result> latest(ServerPlayer player, UUID maid) {
        if (!supported()) return CompletableFuture.completedFuture(new Result("", null, "TLM backup API unavailable"));
        UUID owner = player.getUUID();
        Path base = player.getServer().getWorldPath(LevelResource.ROOT).resolve("data/maid_backups").toAbsolutePath().normalize();
        Path directory = base.resolve(owner.toString()).resolve(maid.toString());
        int limit = MaidWeaponConfig.CONTRACT_NBT_MAX_DECOMPRESSED_BYTES.get();
        int depth = MaidWeaponConfig.CONTRACT_NBT_MAX_DEPTH.get();
        try { return CompletableFuture.supplyAsync(() -> read(base, directory, owner, maid, limit, depth), IO); }
        catch (RejectedExecutionException busy) { return CompletableFuture.completedFuture(new Result("", null, "Backup IO budget busy")); }
    }
    private static Result read(Path base, Path directory, UUID owner, UUID maid, int limit, int depth) {
        try {
            if (!Files.isDirectory(directory)) return new Result("", null, "No TLM backup");
            Path actualBase = base.toRealPath();
            if (!directory.toRealPath().startsWith(actualBase)) return new Result("", null, "Backup path rejected");
            List<Path> candidates;
            try (var files = Files.list(directory)) {
                candidates = files.limit(129).toList();
            }
            if (candidates.size() > 128) return new Result("", null, "Backup directory exceeds lookup budget");
            candidates = candidates.stream().filter(path -> path.getFileName().toString().matches("\\d{4}(-\\d{2}){5}\\.dat"))
                    .sorted(Comparator.reverseOrder()).limit(3).toList();
            for (var file : candidates) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > limit) continue;
                CompoundTag data;
                try (var input = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
                    data = NbtIo.read(input, new NbtAccounter(limit));
                }
                if (!data.hasUUID("Owner") || !owner.equals(data.getUUID("Owner"))
                        || !data.hasUUID("UUID") || !maid.equals(data.getUUID("UUID"))
                        || !"touhou_little_maid:maid".equals(data.getString("id"))
                        || ContractNbtGuard.depth(data) > depth || ContractNbtGuard.serializedSize(data) > limit) continue;
                return new Result(file.getFileName().toString(), data, "Older snapshot: progress after this file may be lost");
            }
            return new Result("", null, "No valid owned UUID backup within latest-three budget");
        } catch (IOException | RuntimeException | StackOverflowError failure) {
            return new Result("", null, "Backup read rejected: " + failure.getClass().getSimpleName());
        }
    }
    private TlmBackupBridge() { }
}

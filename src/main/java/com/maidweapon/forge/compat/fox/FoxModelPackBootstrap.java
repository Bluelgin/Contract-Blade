package com.maidweapon.forge.compat.fox;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

/** Install the credited runtime pack; only an exact known managed original may be upgraded. */
public final class FoxModelPackBootstrap {
    public static final String WHITE_MODEL = "contract_fox:fox_white";
    public static final String BLACK_MODEL = "contract_fox:fox_black";
    private static final String RESOURCE = "/modelpacks/contract-fox-1.0.1.zip";
    private static final String ORIGINAL_SHA256 = "7fc3517d572a09964e97fe42ae68f45c3ad98b108a44b170039341dc0afbe308";
    private static final String METADATA = "assets/contract_fox/maid_model.json";
    private static final int METADATA_LIMIT = 65536;

    public static void install() {
        if (!supportsLoader()) return;
        try {
            install(FMLPaths.GAMEDIR.get().resolve("tlm_custom_pack"));
        } catch (IOException | RuntimeException failure) {
            LogUtils.getLogger().error("[MaidWeapon] Could not prepare fox model pack; existing packs were preserved", failure);
        }
    }

    public static void install(Path directory) throws IOException {
        Files.createDirectories(directory);
        // Keep the existing managed filename so upgrades cannot register the same IDs twice.
        Path target = directory.resolve("contract-blade-fox-1.0.0.zip");
        boolean upgrade = Files.isRegularFile(target) && isManagedOriginal(target);
        Set<String> existing = new HashSet<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                if (upgrade && path.equals(target)) continue;
                try {
                    if (Files.isDirectory(path)) {
                        Path metadata = path.resolve(METADATA);
                        if (Files.isRegularFile(metadata) && Files.size(metadata) <= METADATA_LIMIT)
                            collect(Files.readAllBytes(metadata), existing);
                    } else if (path.getFileName().toString().endsWith(".zip")) {
                        try (ZipFile zip = new ZipFile(path.toFile())) {
                            var entry = zip.getEntry(METADATA);
                            if (entry != null) try (var stream = zip.getInputStream(entry)) {
                                collect(stream.readNBytes(METADATA_LIMIT + 1), existing);
                            }
                        }
                    }
                } catch (IOException | RuntimeException failure) {
                    LogUtils.getLogger().warn("[MaidWeapon] Could not inspect a custom model pack; leaving it unchanged", failure);
                }
            }
        }
        // User-installed originals win. Never duplicate these IDs or replace edited assets.
        if (existing.contains(WHITE_MODEL) || existing.contains(BLACK_MODEL)) {
            if (!existing.containsAll(Set.of(WHITE_MODEL, BLACK_MODEL)))
                LogUtils.getLogger().warn("[MaidWeapon] Partial fox model pack found; automatic installation skipped to avoid duplicate IDs");
            return;
        }
        if (Files.exists(target) && !upgrade) {
            LogUtils.getLogger().warn("[MaidWeapon] Existing managed fox pack was not overwritten; inspect it if models are missing");
            return;
        }
        Path temporary = Files.createTempFile(directory, ".contract-fox-", ".tmp");
        try (var source = FoxModelPackBootstrap.class.getResourceAsStream(RESOURCE)) {
            if (source == null) throw new IOException("Missing bundled fox model archive");
            Files.copy(source, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (upgrade) {
                // Recheck immediately before replacing; preserve edited packs and a recoverable original.
                if (!isManagedOriginal(target)) return;
                Path backup = Files.createTempFile(directory, ".contract-fox-original-", ".bak");
                Files.copy(target, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.move(temporary, target); // Preserve a concurrently installed pack.
            }
            LogUtils.getLogger().info("[MaidWeapon] Installed credited White/Black Fox pack for the native TLM loader");
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean isManagedOriginal(Path path) throws IOException {
        try {
            return Set.of(ORIGINAL_SHA256, "2d4563d5afd573e1ee85fedffaf28184bd70890bfc2f629374cabacd08ee6289",
                    "a369393325c064bc9f8cb40ffc73f9f27b134696dc69d616ad8f7dc28cdb4e90",
                    "9c907d7583939b1576fca9ba9c3eb77ee2738e000653c0095054e391cd09d6e9")
                    .contains(java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void collect(byte[] bytes, Set<String> models) {
        if (bytes.length > METADATA_LIMIT) return;
        var list = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("model_list");
        if (list == null) return;
        for (var entry : list) models.add(entry.getAsJsonObject().get("model_id").getAsString());
    }

    /** Server-safe metadata availability; never loads client rendering classes. */
    public static boolean isRegistered(String model) {
        if (!supportsLoader()) return false;
        try {
            Class<?> type = Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.info.models.ServerMaidModels");
            Object models = type.getMethod("getInstance").invoke(null);
            return (Boolean) type.getMethod("containsInfo", String.class).invoke(models, model);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private static boolean supportsLoader() {
        return net.minecraftforge.fml.ModList.get().getModContainerById("touhou_little_maid")
                .map(container -> container.getModInfo().getVersion().compareTo(
                        new org.apache.maven.artifact.versioning.DefaultArtifactVersion("1.5.3")) >= 0)
                .orElse(false);
    }

    private FoxModelPackBootstrap() { }
}

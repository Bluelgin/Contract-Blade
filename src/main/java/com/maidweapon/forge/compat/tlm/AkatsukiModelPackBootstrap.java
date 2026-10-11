package com.maidweapon.forge.compat.tlm;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/** Installs the approved maid model without replacing user packs or duplicating IDs. */
public final class AkatsukiModelPackBootstrap {
    public static final String MODEL = "akatsuki_winefox:akatsuki";
    public static final String RESOURCE = "/modelpacks/contract-akatsuki-1.0.0.zip";
    private static final String METADATA = "assets/akatsuki_winefox/maid_model.json";
    private static final int METADATA_LIMIT = 65536;

    public static void install() {
        if (!net.minecraftforge.fml.ModList.get().getModContainerById("touhou_little_maid")
                .map(container -> container.getModInfo().getVersion().compareTo(
                        new org.apache.maven.artifact.versioning.DefaultArtifactVersion("1.5.3")) >= 0)
                .orElse(false)) return;
        try {
            install(FMLPaths.GAMEDIR.get().resolve("tlm_custom_pack"));
        } catch (IOException | RuntimeException failure) {
            LogUtils.getLogger().error("[MaidWeapon] Could not prepare Akatsuki model pack; existing packs were preserved", failure);
        }
    }

    public static void install(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve("contract-akatsuki-1.0.0.zip");
        // New releases expose all three models in the fox page. Retire only exact
        // managed standalone releases, and retain a recoverable non-loaded backup.
        Path merged = directory.resolve("contract-blade-fox-1.0.0.zip");
        if (hasModel(merged, "assets/contract_fox/maid_model.json")) {
            if (Files.isRegularFile(target) && isManagedOriginal(target)) {
                Path backup = Files.createTempFile(directory, ".contract-akatsuki-merged-", ".bak");
                Files.move(target, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                LogUtils.getLogger().info("[MaidWeapon] Standalone Akatsuki pack backed up; model now belongs to the shared fox page");
            }
            return;
        }
        boolean upgrade = Files.isRegularFile(target) && isManagedOriginal(target);
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                if (upgrade && path.equals(target)) continue;
                try {
                    if (Files.isDirectory(path)) {
                        Path metadata = path.resolve(METADATA);
                        if (Files.isRegularFile(metadata) && Files.size(metadata) <= METADATA_LIMIT
                                && containsModel(Files.readAllBytes(metadata))) return;
                    } else if (path.getFileName().toString().endsWith(".zip")) {
                        try (ZipFile zip = new ZipFile(path.toFile())) {
                            var entry = zip.getEntry(METADATA);
                            if (entry != null) try (var stream = zip.getInputStream(entry)) {
                                if (containsModel(stream.readNBytes(METADATA_LIMIT + 1))) return;
                            }
                        }
                    }
                } catch (IOException | RuntimeException failure) {
                    LogUtils.getLogger().warn("[MaidWeapon] Could not inspect a custom Akatsuki pack; leaving it unchanged", failure);
                }
            }
        }
        if (Files.exists(target) && !upgrade) {
            LogUtils.getLogger().warn("[MaidWeapon] Existing Akatsuki archive was not overwritten; inspect it if the model is missing");
            return;
        }
        Path temporary = Files.createTempFile(directory, ".contract-akatsuki-", ".tmp");
        try (var source = AkatsukiModelPackBootstrap.class.getResourceAsStream(RESOURCE)) {
            if (source == null) throw new IOException("Missing bundled Akatsuki model archive");
            Files.copy(source, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (upgrade) {
                // Only our exact prior release is replaceable, with a recoverable backup.
                if (!isManagedOriginal(target)) return;
                Path backup = Files.createTempFile(directory, ".contract-akatsuki-original-", ".bak");
                Files.copy(target, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.move(temporary, target); // A concurrent installation must also be preserved.
            }
            LogUtils.getLogger().info("[MaidWeapon] Installed credited Akatsuki pack for the native TLM loader");
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static boolean isManagedOriginal(Path path) throws IOException {
        try {
            String digest = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
            return java.util.Set.of("0e4f4339edce2925475c991381aa91686bb57effe97809d826ce70037d53bf8d",
                    "6c2cb7c85756c0798cd9f547c999bf087250758dc70d9578df743e49bb8e7d86",
                    "aa6242d888ddfc4fc7188dced3a245b00a3cad6e1b62f5fbd8688f6b8c0660ab",
                    "df427d336185c5ddffbb7461f913e7a4cffd2f4f0560163ec9f4452414fda9ba",
                    "b032c570576a4a6f9260f6f3d90f2ee0e4767266f9dd1fefb338a162d46be0e5").contains(digest)
                    || isCurrentBundledDigest(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static boolean isCurrentBundledDigest(String digest) throws IOException, java.security.NoSuchAlgorithmException {
        try (var source = AkatsukiModelPackBootstrap.class.getResourceAsStream(RESOURCE)) {
            return source != null && digest.equals(java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(source.readAllBytes())));
        }
    }

    /** Check all namespaces: model IDs need not belong to their page namespace. */
    public static boolean hasUnmanagedPack(Path directory, Path managedFox) throws IOException {
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                if (path.equals(managedFox)) continue;
                if (Files.isRegularFile(path) && path.getFileName().toString().equals("contract-akatsuki-1.0.0.zip")
                        && isManagedOriginal(path)) continue;
                if (Files.isDirectory(path)) {
                    Path assets = path.resolve("assets");
                    if (Files.isDirectory(assets)) try (var namespaces = Files.list(assets)) {
                        for (Path namespace : namespaces.toList()) {
                            Path metadata = namespace.resolve("maid_model.json");
                            if (Files.isRegularFile(metadata) && Files.size(metadata) <= METADATA_LIMIT
                                    && containsModel(Files.readAllBytes(metadata))) return true;
                        }
                    }
                } else if (path.getFileName().toString().endsWith(".zip")) {
                    try (ZipFile zip = new ZipFile(path.toFile())) {
                        for (var entry : java.util.Collections.list(zip.entries())) {
                            if (!entry.getName().matches("assets/[^/]+/maid_model\\.json")) continue;
                            try (var stream = zip.getInputStream(entry)) {
                                if (containsModel(stream.readNBytes(METADATA_LIMIT + 1))) return true;
                            }
                        }
                    } catch (IOException | RuntimeException failure) {
                        // An unreadable pack is not safe to replace or merge around.
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasModel(Path path, String metadata) throws IOException {
        if (!Files.isRegularFile(path)) return false;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var entry = zip.getEntry(metadata);
            if (entry == null) return false;
            try (var stream = zip.getInputStream(entry)) {
                return containsModel(stream.readNBytes(METADATA_LIMIT + 1));
            }
        }
    }

    private static boolean containsModel(byte[] bytes) {
        if (bytes.length > METADATA_LIMIT) return false;
        var list = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("model_list");
        if (list == null) return false;
        for (var entry : list) {
            if (MODEL.equals(entry.getAsJsonObject().get("model_id").getAsString())) return true;
        }
        return false;
    }

    private AkatsukiModelPackBootstrap() { }
}

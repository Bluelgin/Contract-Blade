import com.maidweapon.forge.compat.fox.FoxModelPackBootstrap;
import com.maidweapon.forge.compat.tlm.AkatsukiModelPackBootstrap;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Upgrade fixtures are private: never modifies a running client's packs. */
public class TrioPackValidator {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path directory = Files.createTempDirectory(root.resolve("tmp"), "trio-pack-");
        byte[] oldFox = Files.readAllBytes(root.resolve("run-white-fox-client/tlm_custom_pack/contract-blade-fox-1.0.0.zip"));
        byte[] oldAkatsuki = Files.readAllBytes(root.resolve("run-white-fox-client/tlm_custom_pack/contract-akatsuki-1.0.0.zip"));
        Path fox = directory.resolve("contract-blade-fox-1.0.0.zip");
        Path akatsuki = directory.resolve("contract-akatsuki-1.0.0.zip");
        Files.write(fox, oldFox); Files.write(akatsuki, oldAkatsuki);
        install(directory);
        require(!Files.exists(akatsuki), "Known standalone Akatsuki retired");
        byte[] merged = Files.readAllBytes(fox);
        require(!Arrays.equals(oldFox, merged), "Prior fox release upgraded");
        try (var paths = Files.list(directory)) {
            List<byte[]> backups = new ArrayList<>();
            for (Path path : paths.toList()) if (path.toString().endsWith(".bak")) backups.add(Files.readAllBytes(path));
            require(backups.stream().anyMatch(b -> Arrays.equals(b, oldFox)) && backups.stream().anyMatch(b -> Arrays.equals(b, oldAkatsuki)), "Both old archives recoverable");
        }
        try (ZipFile zip = new ZipFile(fox.toFile())) {
            require(zip.getEntry("assets/akatsuki_winefox/") != null, "Cross-namespace assets discoverable");
            require(zip.getEntry("assets/akatsuki_winefox/maid_model.json") == null, "No separate Akatsuki page");
            try (var stream = zip.getInputStream(zip.getEntry("assets/contract_fox/maid_model.json"))) {
                var metadata = JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var list = metadata.getAsJsonArray("model_list");
                require(list.size() == 3, "Shared page contains exactly three models");
                for (var entry : list) {
                    var model = entry.getAsJsonObject();
                    String id = model.get("model_id").getAsString();
                    require(zip.getEntry("assets/" + id.replace(":", "/models/entity/") + ".json") != null, "Native geometry exists: " + id);
                }
                require(metadata.getAsJsonArray("author").size() == 1, "Short consistent UI author field");
            }
            var gson = new com.google.gson.GsonBuilder().registerTypeAdapter(
                    net.minecraft.resources.ResourceLocation.class,
                    new net.minecraft.resources.ResourceLocation.Serializer()).create();
            try (var reader = new java.io.InputStreamReader(zip.getInputStream(zip.getEntry("assets/contract_fox/maid_model.json")), java.nio.charset.StandardCharsets.UTF_8)) {
                com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.CustomModelPack<com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo> nativePack = gson.fromJson(reader,
                        new com.google.gson.reflect.TypeToken<com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.CustomModelPack<com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo>>() {}.getType());
                nativePack.decorate("contract_fox");
                for (var model : nativePack.getModelList()) {
                    var resources = new ArrayList<>(model.getAnimation());
                    resources.add(model.getModel()); resources.add(model.getTexture());
                    for (var resource : resources) require(zip.getEntry("assets/" + resource.getNamespace() + "/" + resource.getPath()) != null,
                            "Native decorated resource resolves: " + resource);
                }
            }
        }
        install(directory);
        require(Arrays.equals(merged, Files.readAllBytes(fox)), "Repeated migration unchanged");
        Path custom = Files.createTempDirectory(root.resolve("tmp"), "trio-custom-");
        Path customAkatsuki = custom.resolve("player-akatsuki.zip");
        Files.write(customAkatsuki, oldAkatsuki); Files.write(customAkatsuki, new byte[]{42}, StandardOpenOption.APPEND);
        byte[] edited = Files.readAllBytes(customAkatsuki);
        Files.write(custom.resolve("contract-blade-fox-1.0.0.zip"), oldFox);
        install(custom);
        require(Arrays.equals(edited, Files.readAllBytes(customAkatsuki)), "Edited standalone preserved");
        require(Arrays.equals(oldFox, Files.readAllBytes(custom.resolve("contract-blade-fox-1.0.0.zip"))), "Custom same-ID prevents merged duplicate");
        Path fresh = Files.createTempDirectory(root.resolve("tmp"), "trio-fresh-");
        install(fresh);
        require(Files.exists(fresh.resolve("contract-blade-fox-1.0.0.zip")) && !Files.exists(fresh.resolve("contract-akatsuki-1.0.0.zip")), "Fresh installation only one page");
        Path shared = Files.createTempDirectory(root.resolve("tmp"), "trio-shared-upgrade-");
        Path sharedTarget = shared.resolve("contract-blade-fox-1.0.0.zip");
        byte[] previousShared = Files.readAllBytes(root.resolve("art/akatsuki/crimson-wrap/previous-shared-pack.zip"));
        Files.write(sharedTarget, previousShared); install(shared);
        require(Arrays.equals(merged, Files.readAllBytes(sharedTarget)), "Previous shared page upgrades without self-collision");
        Path firstWrap = Files.createTempDirectory(root.resolve("tmp"), "trio-wrap-upgrade-");
        Path firstWrapTarget = firstWrap.resolve("contract-blade-fox-1.0.0.zip");
        byte[] firstWrapBytes = Files.readAllBytes(root.resolve("art/akatsuki/crimson-wrap/revision-one/shared-pack.zip"));
        byte[] firstStandalone = Files.readAllBytes(root.resolve("art/akatsuki/crimson-wrap/revision-one/standalone-pack.zip"));
        Files.write(firstWrapTarget, firstWrapBytes);
        Files.write(firstWrap.resolve("contract-akatsuki-1.0.0.zip"), firstStandalone);
        install(firstWrap);
        require(Arrays.equals(merged, Files.readAllBytes(firstWrapTarget)), "First wrap revision upgrades to folded revision");
        require(!Files.exists(firstWrap.resolve("contract-akatsuki-1.0.0.zip")), "First wrap standalone safely retired");
        try(var backups = Files.list(firstWrap)) {
            var bytes = new ArrayList<byte[]>();
            for(var path : backups.toList()) if(path.toString().endsWith(".bak")) bytes.add(Files.readAllBytes(path));
            require(bytes.stream().anyMatch(b -> Arrays.equals(b, firstWrapBytes)) && bytes.stream().anyMatch(b -> Arrays.equals(b, firstStandalone)), "First wrap revisions backed up byte-for-byte");
        }
        Path secondWrap = Files.createTempDirectory(root.resolve("tmp"), "trio-attachment-upgrade-");
        byte[] secondWrapBytes = Files.readAllBytes(root.resolve("art/akatsuki/crimson-wrap/revision-two/contract-fox-1.0.1.zip"));
        Path secondWrapTarget = secondWrap.resolve("contract-blade-fox-1.0.0.zip");
        Files.write(secondWrapTarget, secondWrapBytes);
        install(secondWrap);
        require(Arrays.equals(merged, Files.readAllBytes(secondWrapTarget)), "Folded revision upgrades to attached ribbons");
        try(var backups = Files.list(secondWrap)) {
            boolean exactBackup = false;
            for(var path : backups.toList()) if(path.toString().endsWith(".bak") && Arrays.equals(Files.readAllBytes(path), secondWrapBytes)) exactBackup = true;
            require(exactBackup, "Folded revision recoverable after attachment update");
        }
        System.out.println("TRIO_PACK_MIGRATION_PASS");
    }
    private static void install(Path directory) throws Exception {
        FoxModelPackBootstrap.install(directory); AkatsukiModelPackBootstrap.install(directory);
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        System.out.println(message);
    }
}

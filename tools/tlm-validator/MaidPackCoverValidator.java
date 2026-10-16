import com.maidweapon.forge.compat.fox.FoxModelPackBootstrap;
import com.maidweapon.forge.compat.tlm.AkatsukiModelPackBootstrap;
import java.nio.file.*;
import java.util.*;

/** Standalone installer regression; only generates private temp fixture directories. */
public class MaidPackCoverValidator {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        verify(root, "fox", "contract-blade-fox-1.0.0.zip", "contract-fox-before-cover.zip");
        verify(root, "akatsuki", "contract-akatsuki-1.0.0.zip", "contract-akatsuki-before-cover.zip");
        System.out.println("MAID_PACK_COVER_INSTALL_PASS");
    }
    private static void install(String kind, Path directory) throws Exception {
        if (kind.equals("fox")) FoxModelPackBootstrap.install(directory);
        else AkatsukiModelPackBootstrap.install(directory);
    }
    private static void verify(Path root, String kind, String filename, String previous) throws Exception {
        Path directory = Files.createTempDirectory(root.resolve("tmp"), "cover-"+kind+"-");
        Path target = directory.resolve(filename);
        byte[] old = Files.readAllBytes(root.resolve("art/maid_pack_icons/before/"+previous));
        Files.write(target, old);
        install(kind, directory);
        byte[] current = Files.readAllBytes(target);
        require(!Arrays.equals(old, current), kind+": exact previous bundled archive upgraded");
        try (var files = Files.list(directory)) {
            var backups = files.filter(path -> path.toString().endsWith(".bak")).toList();
            require(backups.size()==1 && Arrays.equals(old, Files.readAllBytes(backups.get(0))), kind+": byte-identical recoverable backup");
        }
        install(kind, directory);
        require(Arrays.equals(current, Files.readAllBytes(target)), kind+": repeated installation unchanged");
        Files.write(target, new byte[]{42}, StandardOpenOption.APPEND); // Valid ZIP with user-modified bytes.
        byte[] edited = Files.readAllBytes(target);
        install(kind, directory);
        require(Arrays.equals(edited, Files.readAllBytes(target)), kind+": edited user archive preserved");
        Path supplied = directory.resolve("player-supplied.zip");
        Files.move(target, supplied);
        install(kind, directory);
        require(!Files.exists(target) && Arrays.equals(edited, Files.readAllBytes(supplied)), kind+": same-ID user archive prevents duplicate pack");
    }
    private static void require(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        System.out.println(message);
    }
}

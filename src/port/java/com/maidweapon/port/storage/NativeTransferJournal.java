package com.maidweapon.port.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import com.maidweapon.port.tlm.VerifiedTlmSnapshot;

/**
 * Probe-only durable intent + backup, written before item/entity mutation.
 * Previous-session records remain quarantined, including after clean shutdown.
 * An operator recovery protocol is intentionally NOT implemented yet.
 */
public final class NativeTransferJournal {
    public enum Kind { CAPTURE, MANIFEST }
    private static final WeakHashMap<MinecraftServer, NativeTransferJournal> SESSIONS = new WeakHashMap<>();
    private static final long MAX_FILE = 40L * 1024 * 1024;
    private final Path directory;
    private final UUID session;
    private final java.util.Set<UUID> durableThisSession=new java.util.HashSet<>();
    private final java.util.Set<UUID> recoveredLive=new java.util.HashSet<>();
    private final java.util.Set<UUID> denied=new java.util.HashSet<>();

    public NativeTransferJournal(Path directory, UUID session) {
        this.directory=directory; this.session=session;
    }
    public static NativeTransferJournal get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Server thread required");
        synchronized (SESSIONS) {
            return SESSIONS.computeIfAbsent(server, s -> new NativeTransferJournal(
                s.getWorldPath(LevelResource.ROOT).resolve("contract-port-transfer-journal"), UUID.randomUUID()));
        }
    }
    private Path path(UUID maid) { return directory.resolve(maid.toString()+".nbt"); }

    /** Absence is only accepted if the filesystem actually reports no file. */
    public boolean permits(UUID maid) throws IOException {
        Path file=path(maid);
        if (denied.contains(maid)) return false;
        if (durableThisSession.contains(maid) || recoveredLive.contains(maid)) return Files.isRegularFile(file);
        if (Files.notExists(file)) return true;
        // A valid pre-existing record cannot authorize a new server session.
        // Parse once on attempted access for diagnostics; never trust its session ID.
        read(maid);
        denied.add(maid);
        return false;
    }
    public CompoundTag read(UUID maid) throws IOException {
        Path file=path(maid);
        if (Files.size(file)>MAX_FILE) throw new IOException("Journal file too large");
        CompoundTag tag=NbtIo.readCompressed(file,NbtAccounter.create(64L*1024*1024));
        if (!tag.contains("schema",Tag.TAG_INT)||tag.getInt("schema")!=1
            ||!tag.hasUUID("session")||!tag.hasUUID("maid")||!maid.equals(tag.getUUID("maid"))
            ||!tag.hasUUID("owner")||!tag.hasUUID("binding")||!tag.hasUUID("ticket")||!tag.hasUUID("operation")
            ||!tag.contains("snapshot",Tag.TAG_COMPOUND))
            throw new IOException("Unknown or malformed journal record");
        try { Kind.valueOf(tag.getString("kind")); }
        catch (IllegalArgumentException invalid) { throw new IOException("Unknown journal kind",invalid); }
        CompoundTag snapshot=tag.getCompound("snapshot");
        ContractNbtBoundsGuard.validate(snapshot);
        VerifiedTlmSnapshot.requireIdentity(snapshot,tag.getUUID("owner"),maid);
        return tag;
    }
    /**
     * Called only after the server has observed exactly one matching, published live maid
     * and a committed LIVE ledger entry. No entity is spawned, deleted or deserialized.
     * A recovered authorization is session-local; next restart requires fresh evidence.
     */
    public boolean acknowledgeObservedLive(NativeContractStore.View live) throws IOException {
        if(live==null || live.phase()!=NativeContractStore.Phase.LIVE) return false;
        CompoundTag tag=read(live.maid());
        if(!Kind.MANIFEST.name().equals(tag.getString("kind"))
            || !live.owner().equals(tag.getUUID("owner")) || !live.binding().equals(tag.getUUID("binding"))
            || session.equals(tag.getUUID("session"))) return false;
        recoveredLive.add(live.maid());
        denied.remove(live.maid());
        return true;
    }
    /** Returns only after fsync + atomic replacement. Unsupported filesystem fails closed. */
    public void writeIntent(ContractReference ref,UUID operation,Kind kind,CompoundTag snapshot) throws IOException {
        if (!permits(ref.maid())) throw new IOException("Prior session requires reconciliation");
        ContractNbtBoundsGuard.validate(snapshot);
        VerifiedTlmSnapshot.requireIdentity(snapshot,ref.owner(),ref.maid());
        CompoundTag tag=new CompoundTag();
        tag.putInt("schema",1); tag.putUUID("session",session); tag.putUUID("maid",ref.maid());
        tag.putUUID("owner",ref.owner()); tag.putUUID("binding",ref.binding());
        tag.putUUID("ticket",ref.ticket()); tag.putUUID("operation",operation);
        tag.putString("kind",kind.name()); tag.put("snapshot",snapshot.copy());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag,bytes);
        if (bytes.size()>MAX_FILE) throw new IOException("Journal file too large");
        Files.createDirectories(directory);
        Path temp=Files.createTempFile(directory,"intent-",".tmp");
        try {
            try(FileChannel out=FileChannel.open(temp,StandardOpenOption.WRITE)) {
                ByteBuffer buf=ByteBuffer.wrap(bytes.toByteArray());
                while(buf.hasRemaining()) out.write(buf);
                out.force(true);
            }
            // Revoke cached permission before replacing; a failed fsync must quarantine even
            // if an earlier intent in this session had already succeeded.
            durableThisSession.remove(ref.maid());
            recoveredLive.remove(ref.maid());
            denied.add(ref.maid());
            Files.move(temp,path(ref.maid()),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            // Persist the directory entry as well. Do not silently fall back to a non-durable rename.
            try(FileChannel dir=FileChannel.open(directory,StandardOpenOption.READ)) {dir.force(true);}
            try(FileChannel parent=FileChannel.open(directory.getParent(),StandardOpenOption.READ)) {parent.force(true);}
            durableThisSession.add(ref.maid());
            denied.remove(ref.maid());
        } finally {Files.deleteIfExists(temp);}
    }
}

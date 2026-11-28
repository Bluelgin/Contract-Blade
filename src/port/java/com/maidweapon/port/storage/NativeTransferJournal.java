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
    private static final long MAX_FILE = 80L * 1024 * 1024;
    private final Path directory;
    private final UUID session;
    private final java.util.Set<UUID> durableThisSession=new java.util.HashSet<>();
    private final java.util.Set<UUID> recoveredLive=new java.util.HashSet<>();
    private final java.util.Map<UUID,ContractReference> checkpointRestored=new java.util.HashMap<>();
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
        if (durableThisSession.contains(maid) || recoveredLive.contains(maid) || checkpointRestored.containsKey(maid)) return Files.isRegularFile(file);
        if (Files.notExists(file)) return true;
        // A valid pre-existing record cannot authorize a new server session.
        // Parse once on attempted access for diagnostics; never trust its session ID.
        try {read(maid);} finally {denied.add(maid);}
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
    public boolean acknowledgeObservedLive(NativeContractStore.View live, ContractReference lease) throws IOException {
        if(live==null || live.phase()!=NativeContractStore.Phase.LIVE || lease==null
            ||!live.maid().equals(lease.maid())||!live.owner().equals(lease.owner())||!live.binding().equals(lease.binding())) return false;
        CompoundTag tag=read(live.maid());
        if(!Kind.MANIFEST.name().equals(tag.getString("kind"))
            || !live.owner().equals(tag.getUUID("owner")) || !live.binding().equals(tag.getUUID("binding"))
            || !lease.ticket().equals(tag.getUUID("ticket")) || session.equals(tag.getUUID("session"))) return false;
        recoveredLive.add(live.maid());
        denied.remove(live.maid());
        return true;
    }
    /** Candidates are only captures that this process itself durably began. */
    public java.util.Set<UUID> currentIntents(){return java.util.Set.copyOf(durableThisSession);}
    private static boolean matchesCapture(CompoundTag tag,ContractReference ref,CompoundTag snapshot){
        return Kind.CAPTURE.name().equals(tag.getString("kind"))
            &&ref.maid().equals(tag.getUUID("maid"))&&ref.owner().equals(tag.getUUID("owner"))
            &&ref.binding().equals(tag.getUUID("binding"))&&ref.ticket().equals(tag.getUUID("ticket"))
            &&snapshot.equals(tag.getCompound("snapshot"));
    }
    /**
     * Call only after the server-thread world/player/entity flush completed and
     * the committed STORED ledger still agrees. An unchecked journal cannot self-certify.
     * This is a normal save checkpoint, not a proof of hardware power-loss consistency.
     */
    public boolean markStoredAfterFlush(NativeContractStore store,ContractReference ref) throws IOException{
        if(!durableThisSession.contains(ref.maid()))return false;
        var tag=read(ref.maid());
        if(!session.equals(tag.getUUID("session"))||!matchesCapture(tag,ref,store.readStored(ref)))return false;
        tag.putBoolean("saved_checkpoint",true);
        replaceRecord(ref.maid(),tag);
        return true;
    }
    /** Previous-session authorization only from matching flushed capture and ledger. */
    public boolean permitsStored(NativeContractStore store,ContractReference ref) throws IOException{
        var v=store.inspect(ref.maid());
        if(v==null||v.phase()!=NativeContractStore.Phase.STORED||!ref.owner().equals(v.owner())
            ||!ref.binding().equals(v.binding())||!ref.ticket().equals(v.ticket()))return false;
        if(durableThisSession.contains(ref.maid()))return Files.isRegularFile(path(ref.maid()));
        if(ref.equals(checkpointRestored.get(ref.maid())))return Files.isRegularFile(path(ref.maid()));
        // No journal = no evidence for a stored entity, including old pre-journal probes.
        if(Files.notExists(path(ref.maid())))return false;
        var tag=read(ref.maid());
        if(!tag.contains("saved_checkpoint",Tag.TAG_BYTE)||!tag.getBoolean("saved_checkpoint")
            ||!matchesCapture(tag,ref,store.readStored(ref)))return false;
        checkpointRestored.put(ref.maid(),ref);
        denied.remove(ref.maid());
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
        replaceRecord(ref.maid(),tag);
    }
    private void replaceRecord(UUID maid,CompoundTag tag) throws IOException {
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
            durableThisSession.remove(maid);
            recoveredLive.remove(maid);
            checkpointRestored.remove(maid);
            denied.add(maid);
            Files.move(temp,path(maid),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            // Persist the directory entry as well. Do not silently fall back to a non-durable rename.
            try(FileChannel dir=FileChannel.open(directory,StandardOpenOption.READ)) {dir.force(true);}
            try(FileChannel parent=FileChannel.open(directory.getParent(),StandardOpenOption.READ)) {parent.force(true);}
            durableThisSession.add(maid);
            denied.remove(maid);
        } finally {Files.deleteIfExists(temp);}
    }
}

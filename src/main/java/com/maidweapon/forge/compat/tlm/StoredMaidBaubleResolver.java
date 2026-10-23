package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.maidweapon.forge.system.resonance.ResonanceContract;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import java.util.*;

/** No entities instantiated. Cache compares the entire codec payload, not a CRC alone. */
public final class StoredMaidBaubleResolver {
    private record Entry(CompoundTag payload, boolean valid,
                         java.util.EnumSet<ContractBaubleEffects.Effect> effects, long bytes) { }
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>();
    private static final long MAX_CACHE_BYTES = 32L * 1024 * 1024;
    private static long cachedBytes;
    public static boolean valid(ResonanceContract context) { return resolve(context).valid; }
    public static boolean hasWaiver(ResonanceContract context) {
        return has(context, ContractBaubleEffects.Effect.RESONANCE_WAIVER);
    }
    public static boolean has(ResonanceContract context, ContractBaubleEffects.Effect effect) {
        var entry = resolve(context);
        return entry.valid && entry.effects.contains(effect);
    }
    private static Entry resolve(ResonanceContract context) {
        // Explicitly supported native layout. Unknown versions fail closed.
        boolean supported = ModList.get().getModContainerById("touhou_little_maid")
                .map(mod -> java.util.Set.of("1.5.3", "1.5.3-forge+mc1.20.1").contains(mod.getModInfo().getVersion().toString())).orElse(false);
        var tag = context.channel();
        if (!MaidEntityDataCodec.hasData(tag)) return new Entry(new CompoundTag(), false,
                java.util.EnumSet.noneOf(ContractBaubleEffects.Effect.class), 0);
        var payload = new CompoundTag();
        for (String key : List.of(MaidEntityDataCodec.LEGACY_DATA, MaidEntityDataCodec.COMPRESSED_DATA,
                MaidEntityDataCodec.FORMAT, MaidEntityDataCodec.UNCOMPRESSED_SIZE, MaidEntityDataCodec.CHECKSUM))
            if (tag.contains(key)) payload.put(key, tag.get(key));
        String key = context.owner().getUUID() + ":" + context.maid() + ":" + context.binding();
        var cached = CACHE.get(key);
        if (cached != null && cached.payload.equals(payload)) return cached;
        var effects = java.util.EnumSet.noneOf(ContractBaubleEffects.Effect.class);
        boolean valid = false;
        // Authoritative boundary validates bounded decoding, checksums and unknown codec formats.
        ItemStack view = new ItemStack(context.carrier().getItem()); view.setTag(payload.copy());
        CompoundTag maid = ContractMaidStorage.read(context.owner(), view);
        if (maid != null && maid.hasUUID("UUID") && maid.getUUID("UUID").toString().equals(context.maid())
                && maid.hasUUID("Owner") && maid.getUUID("Owner").equals(context.owner().getUUID())
                && (!maid.contains("id") || maid.getString("id").equals("touhou_little_maid:maid"))) {
            String storedBinding = maid.getCompound("ForgeData").getString(ContractMaidKeys.ENTITY_BINDING_ID);
            valid = storedBinding.isEmpty() || storedBinding.equals(context.binding());
            if (valid && supported) effects = readNativeEffects(maid);
        }
        long bytes = com.maidweapon.forge.system.ContractNbtGuard.serializedSize(payload);
        var entry = new Entry(payload.copy(), valid, effects, bytes);
        var previous = CACHE.remove(key);
        if (previous != null) cachedBytes -= previous.bytes;
        while (!CACHE.isEmpty() && (CACHE.size() >= 256 || bytes > MAX_CACHE_BYTES - cachedBytes)) {
            var first = CACHE.keySet().iterator().next(); cachedBytes -= CACHE.remove(first).bytes;
        }
        if (bytes <= MAX_CACHE_BYTES) { CACHE.put(key, entry); cachedBytes += bytes; }
        return entry;
    }
    /** Original compatibility test: verify that a native TLM saved inventory grants waiver. */
    static boolean readNativeSlots(CompoundTag maid) {
        return readNativeEffects(maid).contains(ContractBaubleEffects.Effect.RESONANCE_WAIVER);
    }
    /** One decode resolves every supported effect; future baubles extend only Effect mapping. */
    static java.util.EnumSet<ContractBaubleEffects.Effect> readNativeEffects(CompoundTag maid) {
        var empty = java.util.EnumSet.noneOf(ContractBaubleEffects.Effect.class);
        if (!maid.contains("MaidBaubleInventory", Tag.TAG_COMPOUND)) return empty;
        var inventory = maid.getCompound("MaidBaubleInventory");
        int size = inventory.getInt("Size");
        if (size < 1 || size > 30 || !inventory.contains("Items", Tag.TAG_LIST)) return empty;
        var items = inventory.getList("Items", Tag.TAG_COMPOUND);
        if (items.size() > size) return empty;
        var occupied = new HashSet<Integer>();
        var effects = java.util.EnumSet.noneOf(ContractBaubleEffects.Effect.class);
        for (Tag item : items) {
            var entry = (CompoundTag) item;
            int slot = entry.getInt("Slot");
            if (!entry.contains("Slot", Tag.TAG_INT) || slot < 0 || slot >= size || !occupied.add(slot)) return empty;
            effects.addAll(ContractBaubleEffects.effects(ItemStack.of(entry)));
        }
        return effects;
    }
    public static void clear(UUID owner) {
        var entries = CACHE.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (entry.getKey().startsWith(owner + ":")) { cachedBytes -= entry.getValue().bytes; entries.remove(); }
        }
    }
    public static void clearAll() { CACHE.clear(); cachedBytes = 0; }
    private StoredMaidBaubleResolver() { }
}

package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.maidweapon.forge.system.resonance.ResonanceContract;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import java.util.*;

/** No entities instantiated. Cache compares the entire codec payload, not a CRC alone. */
public final class StoredMaidBaubleResolver {
    private record Entry(CompoundTag payload, boolean valid, boolean waived) { }
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>();
    public static boolean valid(ResonanceContract context) { return resolve(context).valid; }
    public static boolean hasWaiver(ResonanceContract context) { return resolve(context).waived; }
    private static Entry resolve(ResonanceContract context) {
        // Explicitly supported native layout. Unknown versions fail closed.
        boolean supported = ModList.get().getModContainerById("touhou_little_maid")
                .map(mod -> java.util.Set.of("1.5.3", "1.5.3-forge+mc1.20.1").contains(mod.getModInfo().getVersion().toString())).orElse(false);
        var tag = context.channel();
        if (!MaidEntityDataCodec.hasData(tag)) return new Entry(new CompoundTag(), false, false);
        var payload = new CompoundTag();
        for (String key : List.of(MaidEntityDataCodec.LEGACY_DATA, MaidEntityDataCodec.COMPRESSED_DATA,
                MaidEntityDataCodec.FORMAT, MaidEntityDataCodec.UNCOMPRESSED_SIZE, MaidEntityDataCodec.CHECKSUM))
            if (tag.contains(key)) payload.put(key, tag.get(key));
        String key = context.owner().getUUID() + ":" + context.maid() + ":" + context.binding();
        var cached = CACHE.get(key);
        if (cached != null && cached.payload.equals(payload)) return cached;
        boolean waived = false;
        boolean valid = false;
        // Authoritative boundary validates bounded decoding, checksums and unknown codec formats.
        ItemStack view = new ItemStack(context.carrier().getItem()); view.setTag(payload.copy());
        CompoundTag maid = ContractMaidStorage.read(context.owner(), view);
        if (maid != null && maid.hasUUID("UUID") && maid.getUUID("UUID").toString().equals(context.maid())
                && maid.hasUUID("Owner") && maid.getUUID("Owner").equals(context.owner().getUUID())
                && (!maid.contains("id") || maid.getString("id").equals("touhou_little_maid:maid"))) {
            String storedBinding = maid.getCompound("ForgeData").getString(ContractMaidKeys.ENTITY_BINDING_ID);
            valid = storedBinding.isEmpty() || storedBinding.equals(context.binding());
            waived = valid && supported && readNativeSlots(maid);
        }
        if (CACHE.size() >= 256) CACHE.remove(CACHE.keySet().iterator().next());
        var entry = new Entry(payload.copy(), valid, waived);
        CACHE.put(key, entry);
        return entry;
    }
    static boolean readNativeSlots(CompoundTag maid) {
        if (!maid.contains("MaidBaubleInventory", Tag.TAG_COMPOUND)) return false;
        var inventory = maid.getCompound("MaidBaubleInventory");
        int size = inventory.getInt("Size");
        if (size < 1 || size > 30 || !inventory.contains("Items", Tag.TAG_LIST)) return false;
        var items = inventory.getList("Items", Tag.TAG_COMPOUND);
        if (items.size() > size) return false;
        var occupied = new HashSet<Integer>();
        boolean found = false;
        for (Tag item : items) {
            var stack = (CompoundTag) item;
            int slot = stack.getInt("Slot");
            if (!stack.contains("Slot", Tag.TAG_INT) || slot < 0 || slot >= size || !occupied.add(slot)) return false;
            if (ContractBaubleEffects.effects(ItemStack.of(stack)).contains(ContractBaubleEffects.Effect.RESONANCE_WAIVER)) found = true;
        }
        return found;
    }
    public static void clear(UUID owner) { CACHE.keySet().removeIf(key -> key.startsWith(owner + ":")); }
    public static void clearAll() { CACHE.clear(); }
    private StoredMaidBaubleResolver() { }
}

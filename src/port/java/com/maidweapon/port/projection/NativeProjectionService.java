package com.maidweapon.port.projection;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.content.NativeContent;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.storage.NativeChannelService;
import com.maidweapon.port.storage.ContractNbtBoundsGuard;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Native six-slot projection. Real original equipment stays with the maid, not the weapon. */
public final class NativeProjectionService {
    private static final String KEY = "maid_weapon_projection";
    private static final EquipmentSlot[] SLOTS = { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD };
    private static final Set<EntityMaid> PENDING = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<UUID> REPORTED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<MinecraftServer, Map<UUID, EntityMaid>> ACTIVE = new WeakHashMap<>();

    /** First projection accessory wins, independently of the first ultimate accessory. */
    public static NativeProjection.Mode mode(EntityMaid maid) {
        var slots = maid.getMaidBauble();
        if (slots.getSlots() > 64) return NativeProjection.Mode.NONE;
        for (int i = 0; i < slots.getSlots(); i++) {
            var s = slots.getStackInSlot(i);
            if (NativeProjection.isCopy(s)) continue;
            if (s.is(NativeContent.TASSEL.get())) return NativeProjection.Mode.WEAPON;
            if (s.is(NativeContent.RIBBON.get())) return NativeProjection.Mode.ARMOR;
            if (s.is(NativeContent.KNOT.get()) || s.is(NativeContent.ENDLESS.get()) || s.is(NativeContent.OATH_RING.get()))
                return NativeProjection.Mode.COMBINED;
        }
        return NativeProjection.Mode.NONE;
    }
    private static NativeProjection.Mode effectiveMode(EntityMaid maid){
        return com.maidweapon.port.companion.NativeCompanionService.resident(maid)?NativeProjection.Mode.NONE:mode(maid);
    }
    private static CompoundTag state(EntityMaid maid) {
        if (!maid.getPersistentData().contains(KEY)) return new CompoundTag();
        var state = maid.getPersistentData().getCompound(KEY);
        if (state.getInt("schema") != 1 || state.getBoolean("quarantined") || state.contains("returning"))
            throw new IllegalStateException("Unknown or quarantined projection equipment state");
        if (!state.hasUUID("owner") || !Objects.equals(maid.getOwnerUUID(), state.getUUID("owner"))
                || !state.hasUUID("binding") || !maid.hasData(PortDataTypes.MAID_BINDING)
                || !maid.getData(PortDataTypes.MAID_BINDING).equals(state.getUUID("binding"))
                || !state.contains("slots", net.minecraft.nbt.Tag.TAG_COMPOUND))
            throw new IllegalStateException("Projection equipment identity/slot schema mismatch");
        for (var key : state.getCompound("slots").getAllKeys()) {
            if (Arrays.stream(SLOTS).noneMatch(slot -> slot.getName().equals(key))
                    || !state.getCompound("slots").contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND))
                throw new IllegalStateException("Unknown projection original equipment slot/type");
        }
        return state;
    }
    private static void write(EntityMaid maid, CompoundTag state) {
        if (state.getCompound("slots").isEmpty()) maid.getPersistentData().remove(KEY);
        else maid.getPersistentData().put(KEY, state);
    }
    /** Called only with a real, currently authoritative live carrier. */
    public static boolean maintain(ServerPlayer owner, EntityMaid maid, ItemStack source) {
        if (!owner.isAlive() || owner.isSpectator() || maid.level() != owner.level()
                || NativeChannelService.selectedLive(owner.getServer(), owner.getUUID(), source) != maid) return false;
        if (PENDING.contains(maid)) {
            if (!clear(maid)) return false;
            PENDING.remove(maid);
        }
        var mode = effectiveMode(maid);
        if (mode == NativeProjection.Mode.NONE) return clear(maid);
        if (maid.isUsingItem()) return true; // TLM temporarily owns its eating hand.
        try {
            var state = state(maid);
            if (!state.isEmpty() && (!state.hasUUID("owner") || !owner.getUUID().equals(state.getUUID("owner"))
                    || !state.hasUUID("binding") || !maid.getData(PortDataTypes.MAID_BINDING).equals(state.getUUID("binding"))))
                throw new IllegalStateException("Projection owner/binding mismatch");
            state.putInt("schema", 1); state.putUUID("owner", owner.getUUID());
            state.putUUID("binding", maid.getData(PortDataTypes.MAID_BINDING));
            for (var slot : SLOTS) {
                boolean enabled = slot.getType() == EquipmentSlot.Type.HAND ? mode.weapon() : mode.armor();
                if (!enabled) { restoreSlot(maid, state, slot); continue; }
                // Spell-provider accessories have their own adapter; never copy an unrelated offhand/shield.
                ItemStack original = slot == EquipmentSlot.MAINHAND ? source
                    : slot == EquipmentSlot.OFFHAND ? ItemStack.EMPTY : owner.getItemBySlot(slot);
                var gear = state.getCompound("slots"); var current = maid.getItemBySlot(slot);
                if (!gear.contains(slot.getName())) {
                    if (NativeProjection.isCopy(current)) throw new IllegalStateException("Orphan projection before snapshot");
                    gear.put(slot.getName(), current.saveOptional(maid.registryAccess()));
                    state.put("slots", gear); write(maid, state);
                } else if (!current.isEmpty() && !NativeProjection.isCopy(current)) continue;
                var replacement = NativeProjection.copy(original);
                if (!ItemStack.isSameItemSameComponents(current, replacement)) maid.setItemSlot(slot, replacement);
            }
            write(maid, state);
            ACTIVE.computeIfAbsent(owner.getServer(), s -> new HashMap<>()).put(maid.getUUID(), maid);
            return true;
        } catch (ReturnBlocked blocked) { return clear(maid); }
        catch (Exception failure) { quarantine(maid, failure); return false; }
    }
    private static ItemStack parse(EntityMaid maid, CompoundTag tag) {
        if (tag.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.parse(maid.registryAccess(), tag).filter(s -> !NativeProjection.isCopy(s))
            .orElseThrow(() -> new IllegalStateException("Invalid original projection equipment; preserved for diagnosis"));
    }
    private static void restoreSlot(EntityMaid maid, CompoundTag state, EquipmentSlot slot) {
        var gear = state.getCompound("slots"); var key = slot.getName();
        if (!gear.contains(key)) {
            if (NativeProjection.isCopy(maid.getItemBySlot(slot))) maid.setItemSlot(slot, ItemStack.EMPTY);
            return;
        }
        var original = parse(maid, gear.getCompound(key));
        var current = maid.getItemBySlot(slot);
        if (current.isEmpty() || NativeProjection.isCopy(current)) maid.setItemSlot(slot, original);
        else if (!original.isEmpty()) {
            // Same normal return policy as maintained TlmEquipmentReturns: backpack,
            // then the real remainder at the maid's feet. Only a published entity
            // may deliver originals. Mark interrupted delivery as non-retryable.
            if (maid.level().getEntity(maid.getId()) != maid) throw new ReturnBlocked();
            state.putString("returning", key); write(maid, state);
            var remaining = ItemHandlerHelper.insertItemStacked(maid.getAvailableBackpackInv(), original.copy(), false);
            if (!remaining.isEmpty() && maid.spawnAtLocation(remaining) == null)
                throw new IllegalStateException("Original equipment world return rejected");
            state.remove("returning");
        }
        gear.remove(key); state.put("slots", gear); write(maid, state);
    }
    /** Preflight every original before changing any slot. Failure preserves a quarantined record. */
    public static boolean clear(EntityMaid maid) { return clear(maid, false); }
    private static boolean clear(EntityMaid maid, boolean death) {
        if (!death && maid.isUsingItem() && maid.getPersistentData().contains(KEY)
                && (maid.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.EAT
                    || maid.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.DRINK)) return false;
        // An unloaded entity's disk snapshot still owns the originals. Never
        // deliver them from an obsolete in-memory instance after its save.
        if (maid.isRemoved() || maid.level().isClientSide) return false;
        try {
            var state = state(maid);
            ContractNbtBoundsGuard.validate(state);
            for (var slot : SLOTS) if (state.getCompound("slots").contains(slot.getName()))
                parse(maid, state.getCompound("slots").getCompound(slot.getName()));
            boolean blocked = false;
            for (var slot : SLOTS) {
                try { restoreSlot(maid, state, slot); }
                catch (ReturnBlocked full) { blocked = true; }
            }
            if (blocked) return false;
            maid.getPersistentData().remove(KEY);
            return true;
        } catch (Exception failure) { quarantine(maid, failure); return false; }
    }
    private static final class ReturnBlocked extends RuntimeException { }
    private static void quarantine(EntityMaid maid, Exception failure) {
        // No speculative retry after a partial delivery; retain raw originals and a diagnostic.
        var raw = maid.getPersistentData().getCompound(KEY);
        if (REPORTED.add(maid.getUUID())) LogUtils.getLogger().error("Projection equipment quarantined for maid {}", maid.getUUID(), failure);
        // Unknown future schema/type is read-only, including during quarantine.
        if (!maid.getPersistentData().contains(KEY) || maid.getPersistentData().get(KEY) instanceof CompoundTag && raw.getInt("schema") == 1) {
            raw.putInt("schema", 1); raw.putBoolean("quarantined", true); maid.getPersistentData().put(KEY, raw);
        }
    }
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer(); if (server.getTickCount() % 5 != 0) return;
        var observed = new HashSet<UUID>();
        for (var owner : server.getPlayerList().getPlayers()) {
            for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
                var source = owner.getInventory().getItem(i);
                if (NativeProjection.isCopy(source)) { owner.getInventory().setItem(i, ItemStack.EMPTY); continue; }
                if (NativeChannelService.displayReference(source) == null) continue;
                var maid = NativeChannelService.selectedLive(server, owner.getUUID(), source);
                if (maid != null && !observed.contains(maid.getUUID()) && effectiveMode(maid) != NativeProjection.Mode.NONE
                        && maintain(owner, maid, source)) observed.add(maid.getUUID());
            }
        }
        var active = ACTIVE.get(server);
        if (active != null) active.entrySet().removeIf(e -> {
            if (observed.contains(e.getKey()) && !e.getValue().isRemoved() && e.getValue().isAlive()
                    && effectiveMode(e.getValue()) != NativeProjection.Mode.NONE) return false;
            if (e.getValue().isRemoved()) return true;
            return clear(e.getValue());
        });
    }
    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity() instanceof ItemEntity item && NativeProjection.isCopy(item.getItem())) event.setCanceled(true);
        else if (event.getEntity() instanceof EntityMaid maid && maid.getPersistentData().contains(KEY)) PENDING.add(maid);
        // A later listener may reject publication. Restore only after actual
        // world acceptance, before AI; never drop real originals during join.
    }
    public static void beforeTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide && PENDING.contains(maid)) {
            if (maid.level().getEntity(maid.getId()) == maid && clear(maid)) PENDING.remove(maid);
            else {
                var raw = maid.getPersistentData().getCompound(KEY);
                if (raw.getInt("schema") != 1 || raw.getBoolean("quarantined")) event.setCanceled(true);
            }
        }
    }
    public static void drops(LivingDropsEvent event) { event.getDrops().removeIf(i -> NativeProjection.isCopy(i.getItem())); }
    public static void maidDeath(com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent event) { clear(event.getMaid(), true); }
    private static void clearOwner(net.minecraft.world.entity.player.Player owner) {
        var active = ACTIVE.get(owner.getServer()); if (active == null) return;
        active.entrySet().removeIf(e -> { if (!owner.getUUID().equals(e.getValue().getOwnerUUID())) return false; return e.getValue().isRemoved() || clear(e.getValue()); });
    }
    public static void death(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player owner) clearOwner(owner);
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clearOwner(event.getEntity()); }
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clearOwner(event.getEntity()); }
    public static void stop(ServerStoppingEvent event) { var active = ACTIVE.remove(event.getServer()); if (active != null) active.values().forEach(NativeProjectionService::clear); }
    private NativeProjectionService() { }
}

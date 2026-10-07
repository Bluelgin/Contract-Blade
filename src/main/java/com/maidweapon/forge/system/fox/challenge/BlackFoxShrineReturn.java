package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.fox.FoxSpiritLedger;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sole escrow of the ritual's real blade. Combat display equipment is never a reward.
 * A blocked return stays here until an empty native stand exists at the original anchor. */
public final class BlackFoxShrineReturn extends SavedData {
    private record Ticket(ResourceKey<Level> dimension, BlockPos anchor, ItemStack blade, boolean active) {
        Ticket waiting() { return new Ticket(dimension, anchor, blade, false); }
    }
    private final Map<UUID, Ticket> tickets = new HashMap<>();

    public static BlackFoxShrineReturn get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BlackFoxShrineReturn::load,
                BlackFoxShrineReturn::new, "maid_weapon_black_fox_returns");
    }

    public boolean canBegin(ServerPlayer player, ItemFrame stand) {
        if (stand == null || tickets.containsKey(player.getUUID()) || locked(stand)) return false;
        ItemStack blade = stand.getItem();
        return blade.getCount() == 1 && SlashBladeCompat.isNamedBlade(blade, "item.slashblade.fox_black")
                && !FoxSpiritState.isProtected(blade) && !ContractCarrierData.hasMaidData(blade)
                && !ContractCarrierData.hasMaidEntityData(blade)
                && (!blade.hasTag() || !(blade.getTag().contains("MaidWeaponIntrinsicSpirits")
                || blade.getTag().contains("MaidWeaponExternalContract")
                || blade.getTag().contains("MaidWeaponEmbeddedSpirit")));
    }

    public boolean begin(ServerPlayer player, ItemFrame stand) {
        if (!canBegin(player, stand)) return false;
        tickets.put(player.getUUID(), new Ticket(stand.level().dimension(), stand.getPos().immutable(),
                stand.getItem().copy(), true));
        stand.setItem(ItemStack.EMPTY);
        setDirty();
        return true;
    }

    /** Called only by the registered encounter's actual defeat path. Idempotent on repeated death callbacks. */
    public boolean rescue(ServerPlayer player) {
        Ticket ticket = tickets.get(player.getUUID());
        if (ticket == null || !ticket.active) return false;
        if (FoxSpiritState.hasResident(ticket.blade)) return true;
        var identity = new CompoundTag();
        UUID origin = UUID.randomUUID();
        identity.putInt("Version", 1);
        identity.putString("SpiritId", FoxSpiritState.BLACK);
        identity.putUUID("SpiritUUID", UUID.randomUUID());
        identity.putUUID("OriginUUID", origin);
        identity.putUUID("OwnerUUID", player.getUUID());
        identity.putUUID("Token", UUID.randomUUID());
        ticket.blade.getOrCreateTag().putUUID(FoxSpiritState.ORIGIN, origin);
        ticket.blade.getOrCreateTag().put(FoxSpiritState.ROOT, identity);
        FoxSpiritLedger.get(player.getServer()).record(identity, "WEAPON");
        setDirty();
        return true;
    }

    /** Defeat presentation finished, or the player left/died/disconnected. Loss returns an unchanged blade. */
    public void settle(ServerPlayer player) {
        tickets.computeIfPresent(player.getUUID(), (id, ticket) -> ticket.waiting());
        setDirty();
        retry(player.getServer(), player.getUUID());
    }

    public boolean pending(UUID owner) { return tickets.containsKey(owner); }

    public void retry(MinecraftServer server, UUID owner) {
        Ticket ticket = tickets.get(owner);
        if (ticket == null || ticket.active) return;
        var level = server.getLevel(ticket.dimension);
        // Do not load distant chunks or spawn substitute stands. Loaded/rebuilt stands are enough.
        if (level == null || !level.hasChunkAt(ticket.anchor)) return;
        var stands = level.getEntitiesOfClass(ItemFrame.class, new AABB(ticket.anchor).inflate(1),
                stand -> nativeStand(stand) && stand.getPos().equals(ticket.anchor));
        // An ambiguous or occupied anchor never gets overwritten.
        if (stands.size() != 1 || !stands.get(0).getItem().isEmpty()) return;
        stands.get(0).setItem(ticket.blade);
        tickets.remove(owner);
        setDirty();
    }

    public boolean locked(ItemFrame stand) {
        return tickets.values().stream().anyMatch(ticket -> ticket.dimension.equals(stand.level().dimension())
                && ticket.anchor.equals(stand.getPos()));
    }

    public static boolean protectedStand(ItemFrame stand) {
        if (!(stand.level() instanceof net.minecraft.server.level.ServerLevel level) || !nativeStand(stand)) return false;
        return get(level.getServer()).locked(stand) || rescuedBlade(stand.getItem());
    }

    public static boolean allowTake(ItemFrame stand, ServerPlayer player, InteractionHand hand) {
        if (!protectedStand(stand)) return true;
        var service = get(player.getServer());
        if (service.locked(stand)) {
            service.retry(player.getServer(), player.getUUID());
            if (service.locked(stand)) {
                player.displayClientMessage(Component.translatable("maid_weapon.fox.black.resting"), true);
                return false;
            }
        }
        var blade = stand.getItem();
        if (!rescuedBlade(blade)) return true;
        return FoxSpiritState.resident(blade).getUUID("OwnerUUID").equals(player.getUUID())
                && FoxSpiritLedger.get(player.getServer()).owns(FoxSpiritState.resident(blade), "WEAPON")
                && hand == InteractionHand.MAIN_HAND && !player.isShiftKeyDown()
                && player.getItemInHand(hand).isEmpty();
    }

    private static boolean rescuedBlade(ItemStack blade) {
        return FoxSpiritState.hasResident(blade)
                && FoxSpiritState.BLACK.equals(FoxSpiritState.resident(blade).getString("SpiritId"));
    }
    private static boolean nativeStand(ItemFrame stand) {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(stand.getType())
                .toString().equals("slashblade:blade_stand_entity");
    }

    /** Transient encounters do not survive a server restart; their sole stored blade does. */
    public void recoverInterrupted() {
        tickets.replaceAll((id, ticket) -> ticket.waiting());
        if (!tickets.isEmpty()) setDirty();
    }
    public static BlackFoxShrineReturn load(CompoundTag tag) {
        var data = new BlackFoxShrineReturn();
        for (var element : tag.getList("Returns", 10)) {
            var entry = (CompoundTag) element;
            var id = ResourceLocation.tryParse(entry.getString("Dimension"));
            ItemStack blade = ItemStack.of(entry.getCompound("Blade"));
            if (!entry.hasUUID("Owner") || id == null || blade.isEmpty()) continue;
            data.tickets.put(entry.getUUID("Owner"), new Ticket(ResourceKey.create(Registries.DIMENSION, id),
                    BlockPos.of(entry.getLong("Anchor")), blade, entry.getBoolean("Active")));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var list = new ListTag();
        tickets.forEach((owner, ticket) -> {
            var entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            entry.putString("Dimension", ticket.dimension.location().toString());
            entry.putLong("Anchor", ticket.anchor.asLong());
            entry.put("Blade", ticket.blade.save(new CompoundTag()));
            entry.putBoolean("Active", ticket.active);
            list.add(entry);
        });
        tag.put("Returns", list);
        return tag;
    }
}

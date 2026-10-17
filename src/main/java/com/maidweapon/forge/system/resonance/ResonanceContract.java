package com.maidweapon.forge.system.resonance;

import com.maidweapon.forge.api.ContractAuthorization;
import com.maidweapon.forge.compat.tlm.ContractMaidKeys;
import com.maidweapon.forge.compat.tlm.TlmEntityAdapter;
import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.deployment.*;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Re-resolves a channel on every operation; callers cannot retain a mutable authority token. */
public record ResonanceContract(Player owner, ItemStack carrier, CompoundTag channel, String binding, String maid) {
    public static ResonanceContract resolve(Player owner, ItemStack carrier, String binding) {
        if (owner == null || carrier == null || carrier.isEmpty() || carrier.getCount() != 1 || owner.getServer() == null
                || owner.level().isClientSide || !owner.getServer().isSameThread()
                || !com.maidweapon.forge.item.MaidInfusion.isWeapon(carrier)
                || ContractTransferSafetyService.isProjectionPhantom(carrier)
                || !ContractAuthorization.allows(owner, carrier)
                || ContractAnomalyRecoveryService.transitioning(carrier)) return null;
        // Only actual server inventory references, including the cursor; copies cannot obtain authority.
        boolean held = owner.containerMenu.getCarried() == carrier;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) held |= owner.getInventory().getItem(i) == carrier;
        for (var slot : owner.containerMenu.slots)
            if (!(slot.container instanceof net.minecraft.world.inventory.ResultContainer)) held |= slot.getItem() == carrier;
        if (!held) {
            // Emergency costs on a real nearby blade stand remain legal after capture removed the entity.
            for (var frame : owner.level().getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
                    owner.getBoundingBox().inflate(64))) if (frame.getItem() == carrier) { held = true; break; }
        }
        if (!held) return null;
        CompoundTag match = null;
        for (var tag : ContractChannelStorage.channels(carrier)) {
            if (Objects.equals(binding, tag.getString("MaidBindingId"))) {
                if (match != null) return null;
                match = tag;
            }
        }
        if (match == null || !ContractChannelStorage.validContract(match)
                || !match.hasUUID("OwnerUUID") || !owner.getUUID().equals(match.getUUID("OwnerUUID"))
                || match.getBoolean("MaidContractSuperseded") || match.getCompound("MaidData").getInt("ContractSchema") > 2) return null;
        String maid = match.getString("MaidUUID");
        try { UUID.fromString(maid); UUID.fromString(binding); } catch (IllegalArgumentException ex) { return null; }
        var journal = ContractRecoveryJournal.get(owner.getServer());
        if (!journal.knownSchema()) return null;
        var destination = journal.transfer(maid);
        if (destination.contains("Binding") && (!binding.equals(destination.getString("Binding"))
                || !destination.hasUUID("Owner") || !owner.getUUID().equals(destination.getUUID("Owner")))) return null;
        // Reject ambiguous duplicated physical carriers / duplicated maid references, rather than rewarding one arbitrarily.
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var references = new ArrayList<ItemStack>();
        references.add(owner.containerMenu.getCarried());
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) references.add(owner.getInventory().getItem(i));
        for (var slot : owner.containerMenu.slots)
            if (!(slot.container instanceof net.minecraft.world.inventory.ResultContainer)) references.add(slot.getItem());
        for (ItemStack other : references) {
            if (other.isEmpty() || !seen.add(other)) continue;
            for (var tag : ContractChannelStorage.channels(other)) {
                if (tag != match && !tag.getBoolean("MaidContractSuperseded")
                        && (binding.equals(tag.getString("MaidBindingId")) || maid.equals(tag.getString("MaidUUID")))) return null;
            }
        }
        var result = new ResonanceContract(owner, carrier, match, binding, maid);
        Entity live = ContractWeaponLocator.findEntity(owner, maid);
        if (MaidEntityDataCodec.hasData(match)) {
            if (live != null || journal.isMaidPresent(maid)
                    || !com.maidweapon.forge.compat.tlm.StoredMaidBaubleResolver.valid(result)) return null;
        } else if (live == null || !live.isAlive() || !TlmEntityAdapter.isOwnedMaid(live, owner)
                || !binding.equals(live.getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID))) return null;
        return result;
    }
    public Entity liveMaid() { return ContractWeaponLocator.findManifestedMaid(owner, maid); }
    public ItemStack view() {
        ItemStack view = new ItemStack(carrier.getItem()); CompoundTag tag = new CompoundTag();
        if (channel.contains("MaidData")) tag.put("MaidData", channel.getCompound("MaidData").copy());
        view.setTag(tag); return view;
    }
    public com.maidweapon.common.data.MaidWeaponData data() { return ContractCarrierData.getMaidData(view()); }
    public void write(com.maidweapon.common.data.MaidWeaponData data) {
        ItemStack view = view(); ContractCarrierData.setMaidData(view, data);
        channel.put("MaidData", view.getTag().getCompound("MaidData"));
        owner.getInventory().setChanged();
    }
}

package com.maidweapon.forge.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.maidweapon.forge.event.MaidBondCombatHandler;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.deployment.ContractRecoveryService;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/** Optional TLM entry points: protect before custom death and restore only proven contract identities. */
public final class ContractMaidRebirthBridge {
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, MaidDeathEvent.class,
                ContractMaidRebirthBridge::death);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, MaidAndItemTransformEvent.ToMaid.class,
                ContractMaidRebirthBridge::rebirth);
    }

    private static void death(MaidDeathEvent event) {
        var maid = event.getMaid();
        if (!maid.level().isClientSide && maid.getOwner() instanceof Player owner
                && MaidBondCombatHandler.preventContractDeath(owner, maid)) event.setCanceled(true);
    }

    private static void rebirth(MaidAndItemTransformEvent.ToMaid event) {
        var maid = event.getMaid();
        var data = event.getData();
        if (maid.level().isClientSide || maid.getServer() == null || !data.hasUUID("Owner")) return;
        Player owner = maid.getServer().getPlayerList().getPlayer(data.getUUID("Owner"));
        if (owner != null) restoreIdentity(owner, maid, data);
    }

    /** TLM calls readAdditionalSaveData, not Entity.load: UUID and ForgeData otherwise disappear. */
    public static boolean restoreIdentity(Player owner, Entity maid, CompoundTag data) {
        if (!data.hasUUID("UUID") || !data.hasUUID("Owner")
                || !owner.getUUID().equals(data.getUUID("Owner"))) return false;
        String id = data.getUUID("UUID").toString();
        String binding = data.getCompound("ForgeData").getString(ContractMaidKeys.ENTITY_BINDING_ID);
        var carrier = binding.isEmpty() ? ContractWeaponLocator.findBoundWeapon(owner, id)
                : ContractWeaponLocator.findBoundWeaponByBinding(owner, binding);
        if (carrier.isEmpty() || !MaidInfusion.isInfused(carrier)
                || !ContractCarrierData.isOwner(carrier, owner)
                || ContractCarrierData.isContractSuperseded(carrier)
                || MaidInfusion.containsMaid(carrier)
                || !id.equals(ContractCarrierData.getBoundMaidUUID(carrier))) return false;
        // Never replace an already-live maid with another copy from an old film.
        for (var level : owner.getServer().getAllLevels())
            if (level.getEntity(data.getUUID("UUID")) != null) return false;
        maid.setUUID(data.getUUID("UUID"));
        maid.getPersistentData().putString(ContractMaidKeys.ENTITY_BINDING_ID,
                ContractCarrierData.ensureBindingId(carrier));
        ContractRecoveryService.clearFailure(carrier);
        ContractRecoveryService.rememberLocation(carrier, maid);
        return true;
    }

    private ContractMaidRebirthBridge() { }
}

package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.forge.api.IntrinsicSpiritApi;
import com.maidweapon.forge.compat.fox.FoxModelPackBootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** One-time creation only. Presence, recall, residence and injury responses use Core's lifecycle. */
public final class FoxSpiritCompanionService {
    private static final String INITIALIZED = "CompanionInitialized";

    public static void synchronizeInventory(ServerPlayer owner) {
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++)
            initialize(owner, owner.getInventory().getItem(slot));
    }

    public static boolean initialize(ServerPlayer owner, ItemStack weapon) {
        var identity = FoxSpiritState.resident(weapon);
        if (!FoxSpiritState.valid(identity) || !identity.getUUID("OwnerUUID").equals(owner.getUUID())
                || !FoxSpiritLedger.get(owner.getServer()).owns(identity, "WEAPON")) return false;
        if (identity.getBoolean(INITIALIZED)) return true;
        String spirit = identity.getString("SpiritId");
        boolean black = FoxSpiritState.BLACK.equals(spirit);
        String model = black ? FoxModelPackBootstrap.BLACK_MODEL : FoxModelPackBootstrap.WHITE_MODEL;
        // Existing complete contracts retain the model chosen by their owner.
        if (IntrinsicSpiritApi.hasIntrinsicSpirit(weapon, spirit)) {
            identity.putBoolean(INITIALIZED, true);
            return true;
        }
        if (ContractCarrierData.hasMaidData(weapon) || ContractCarrierData.hasMaidEntityData(weapon)
                || !FoxModelPackBootstrap.isRegistered(model)) return false;
        ItemStack prepared = weapon.copy();
        if (!IntrinsicSpiritApi.ensureIntrinsicSpirit(owner, prepared, spirit, black ? "黑狐" : "白狐")
                || !IntrinsicSpiritApi.setIntrinsicSpiritModel(owner, prepared, spirit, model)
                || !IntrinsicSpiritApi.setIntrinsicSpiritActive(owner, prepared, spirit, true)) return false;
        FoxSpiritState.resident(prepared).putBoolean(INITIALIZED, true);
        weapon.setTag(prepared.getTag());
        owner.getInventory().setChanged();
        return true;
    }

    private FoxSpiritCompanionService() { }
}

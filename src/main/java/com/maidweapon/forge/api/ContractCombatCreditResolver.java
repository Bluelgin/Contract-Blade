package com.maidweapon.forge.api;

import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Server-only extensible attribution, independent of concrete skill execution. Providers return real carrier authority. */
public final class ContractCombatCreditResolver {
    public record Credit(ServerPlayer player, ItemStack carrier) { }
    private static final Map<String, Function<Entity, Credit>> PROVIDERS = new LinkedHashMap<>();
    public static synchronized void register(String id, Function<Entity, Credit> provider) {
        if (id == null || id.isBlank() || PROVIDERS.containsKey(id)) throw new IllegalArgumentException("Duplicate credit provider " + id);
        PROVIDERS.put(id, java.util.Objects.requireNonNull(provider));
    }
    public static boolean valid(Credit credit) {
        return credit != null && credit.player() != null && credit.carrier() != null && credit.player().isAlive() && !credit.player().isSpectator()
                && MaidInfusion.isInfused(credit.carrier()) && validMaidIdentity(credit.carrier()) && ContractCarrierData.isOwner(credit.carrier(), credit.player())
                && !ContractCarrierData.isContractSuperseded(credit.carrier())
                && !com.maidweapon.forge.system.deployment.ContractTransferSafetyService.isProjectionPhantom(credit.carrier())
                && ContractAuthorization.allows(credit.player(), credit.carrier());
    }
    private static boolean validMaidIdentity(ItemStack carrier) {
        String maid = ContractCarrierData.getBoundMaidUUID(carrier);
        try { return maid != null && java.util.UUID.fromString(maid) != null; }
        catch (IllegalArgumentException malformed) { return false; }
    }
    public static Credit actor(Entity actor) {
        if (actor == null || actor.level().isClientSide) return null;
        Credit credit = null;
        if (actor instanceof ServerPlayer player) credit = new Credit(player, player.getMainHandItem());
        else if (TouhouLittleMaidHelper.isMaidEntity(actor) && actor instanceof TamableAnimal maid
                && maid.getOwnerUUID() != null && actor.getServer() != null) {
            var owner = actor.getServer().getPlayerList().getPlayer(maid.getOwnerUUID());
            if (owner != null) credit = new Credit(owner, InfusedMaidDeploymentSystem.findBoundWeapon(owner, actor.getStringUUID()));
        }
        if (valid(credit)) return credit;
        for (var entry : PROVIDERS.entrySet()) try {
            credit = entry.getValue().apply(actor); if (valid(credit)) return credit;
        } catch (RuntimeException error) {
            com.maidweapon.forge.compat.CompatDiagnostics.warnOnce("credit:" + entry.getKey(), error);
        }
        return null;
    }
    public static Credit direct(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer && !source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) return null;
        // Delayed sources must have been stamped at launch, not inferred from the current held item.
        return source.getDirectEntity() == source.getEntity() ? actor(source.getEntity()) : null;
    }
    private ContractCombatCreditResolver() { }
}

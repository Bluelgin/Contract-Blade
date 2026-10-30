package com.maidweapon.forge.system.mastery;

import com.maidweapon.common.ContractRulesConfig;
import com.maidweapon.forge.api.ContractCombatCreditResolver;
import com.maidweapon.forge.api.ContractGrowthAttributes;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.compat.TaczCompat;
import com.maidweapon.forge.compat.TripleMagicCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Add to the original native hit, never call hurt a second time or edit permanent weapon data.
 * A per-target owner window shares one bonus across pellets, rapid hits and weapon swaps.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ContractBaubleCombat {
    private record Launch(ContractCombatCreditResolver.Credit credit, String binding, String maid, UUID shooter) { }
    private static final Map<Projectile, Launch> LAUNCHES = new WeakHashMap<>();
    private static final Map<LivingEntity, Map<UUID, Long>> WINDOWS = new WeakHashMap<>();

    /** Optional adapters call this at a verified ordinary weapon launch, never for an exclusive skill. */
    public static void stamp(Projectile shot, ServerPlayer owner, ItemStack carrier) {
        var credit = new ContractCombatCreditResolver.Credit(owner, carrier);
        if (shot.level().isClientSide || shot.getOwner() != owner || !ContractCombatCreditResolver.valid(credit)
                || owner.getMainHandItem() != carrier) return;
        LAUNCHES.put(shot, new Launch(credit,
                com.maidweapon.forge.system.contract.ContractCarrierData.getBindingId(carrier),
                com.maidweapon.forge.system.contract.ContractCarrierData.getBoundMaidUUID(carrier), owner.getUUID()));
    }
    @SubscribeEvent public static void launch(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof Projectile shot)
                || !(shot.getOwner() instanceof ServerPlayer owner)) return;
        ItemStack carrier = owner.getMainHandItem();
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(shot.getType()).toString();
        boolean ordinary = shot instanceof AbstractArrow && (carrier.getItem() instanceof BowItem
                || carrier.getItem() instanceof CrossbowItem);
        ordinary |= id.equals("tacz:bullet") && TaczCompat.isGun(carrier);
        // Only ordinary native slashes. Judgement cuts/summoned swords have their own skill growth.
        ordinary |= id.equals("slashblade:slash_effect") && SlashBladeCompat.isSlashBlade(carrier);
        ordinary |= (id.startsWith("irons_spellbooks:") || id.startsWith("goety:")) && TripleMagicCompat.isMagicCatalyst(carrier);
        if (ordinary) stamp(shot, owner, carrier);
    }
    private static ContractCombatCreditResolver.Credit credit(net.minecraft.world.damagesource.DamageSource source) {
        var scoped = com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.projectile();
        var origin = scoped == null ? source.getDirectEntity() : scoped;
        if (origin instanceof Projectile shot) {
            var record = LAUNCHES.get(shot);
            if (record == null || !(shot.getOwner() instanceof ServerPlayer owner)
                    || !owner.getUUID().equals(record.shooter()) || source.getEntity() != owner
                    || !ContractCombatCreditResolver.valid(record.credit())) return null;
            var carrier = record.credit().carrier();
            // An inventory reference at launch cannot grant power after being dropped or replaced by a copy.
            boolean carried = false;
            for (var stack : owner.getInventory().items) carried |= stack == carrier;
            if (!carried || !record.binding().equals(com.maidweapon.forge.system.contract.ContractCarrierData.getBindingId(carrier))
                    || !record.maid().equals(com.maidweapon.forge.system.contract.ContractCarrierData.getBoundMaidUUID(carrier))) return null;
            return record.credit();
        }
        return scoped == null ? ContractCombatCreditResolver.direct(source) : null;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hurt(LivingHurtEvent e) {
        if (e.getEntity().level().isClientSide || e.getAmount() <= 0 || e.isCanceled()) return;
        var soul = com.maidweapon.forge.system.akatsuki.soul.SoulStrikeContext.current();
        if (soul != null && soul.matches(e.getEntity(), e.getSource())) return;
        var proof = credit(e.getSource());
        if (proof == null || e.getSource().getEntity() != proof.player()) return; // No maid/core actor amplification.
        float bonus = ContractGrowthAttributes.resolve(proof.player(), proof.carrier()).additiveBonus();
        if (bonus <= 0) return;
        var limits = com.maidweapon.forge.compat.DomainTargetCompatibility.inspect(e.getEntity());
        if (limits.protectedPhase() || limits.dpsCap() > 0) return;
        long now = e.getEntity().level().getGameTime();
        var window = WINDOWS.computeIfAbsent(e.getEntity(), target -> new HashMap<>());
        window.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (window.containsKey(proof.player().getUUID())) return;
        float amount = e.getAmount() + bonus;
        if (limits.hitCap() > 0) amount = Math.min(amount, (float) limits.hitCap());
        if (amount <= e.getAmount()) return;
        e.setAmount(amount);
        window.put(proof.player().getUUID(), now + ContractRulesConfig.GROWTH_HIT_WINDOW.get());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        clearOwner(e.getEntity().getUUID());
    }
    @SubscribeEvent public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer player) clearOwner(player.getUUID());
    }
    private static void clearOwner(UUID owner) {
        LAUNCHES.values().removeIf(v -> v.shooter().equals(owner));
        WINDOWS.values().forEach(v -> v.remove(owner));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { LAUNCHES.clear(); WINDOWS.clear(); }
    private ContractBaubleCombat() { }
}

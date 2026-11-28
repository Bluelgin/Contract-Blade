package com.maidweapon.port.fox;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.ability.progression.*;
import com.maidweapon.port.domain.resonance.ResonanceRules;
import com.maidweapon.port.domain.resonance.SpiritCosts;
import com.maidweapon.port.resonance.NativeResonanceService;
import com.maidweapon.port.storage.NativeChannelService;
import com.maidweapon.port.tlm.NativeBaubleCapabilities;
import com.maidweapon.port.oath.NativeOathService;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import static com.maidweapon.port.fox.WhiteFoxGuardRuntime.Action.*;

/** Actual white fox support mechanics for an explicitly granted DEV white origin.
 * Original shrine victory, SlashBlade original-home residency and black synergy
 * remain separate production gates. Never grants production acquisition.
 */
public final class NativeWhiteFoxService {
    private static final ResourceLocation MOVE=ResourceLocation.fromNamespaceAndPath("maid_weapon_port_probe","white_guard");
    private static final Map<ServerPlayer,WhiteFoxGuardRuntime> RUNTIME=new WeakHashMap<>();
    private record Shield(MobEffectInstance effect,int duration){}
    private static final Map<ServerPlayer,Shield> SHIELDS=new WeakHashMap<>();
    private static final Map<ServerPlayer,Map<Holder<MobEffect>,MobEffectInstance>> PASSIVE=new WeakHashMap<>();
    private static final List<Holder<MobEffect>> DISPEL=List.of(MobEffects.POISON,MobEffects.WITHER,
        MobEffects.MOVEMENT_SLOWDOWN,MobEffects.WEAKNESS,MobEffects.BLINDNESS,MobEffects.CONFUSION,
        MobEffects.DIG_SLOWDOWN,MobEffects.HUNGER);
    private record Context(ItemStack carrier,WhiteFoxBlessingGrowth.Profile profile){}
    private static Context hand(ServerPlayer p,ItemStack stack){
        var result=ContractAbilityProgressionService.query(p,stack,WhiteFoxBlessingGrowth.ID);
        return result.status()==ContractAbilityProgressionService.Status.READY
            ?new Context(stack,new WhiteFoxBlessingGrowth.Profile(result.capabilities())):null;
    }
    private static Context context(ServerPlayer p){
        var main=hand(p,p.getMainHandItem());return main!=null?main:hand(p,p.getOffhandItem());
    }
    private static long now(ServerPlayer p){return p.getServer().overworld().getGameTime();}
    private static WhiteFoxGuardRuntime state(ServerPlayer p){return RUNTIME.computeIfAbsent(p,k->new WhiteFoxGuardRuntime());}
    private static int cost(SpiritCosts.Action action){return SpiritCosts.scaledPoints(com.maidweapon.port.config.NativeRules.resonance().maximum(),action.basisPoints);}
    private static boolean affordable(ServerPlayer p,Context c,SpiritCosts.Action action){
        var server=p.getServer();var ref=NativeChannelService.selected(server,p.getUUID(),c.carrier());
        if(ref!=null)return NativeBaubleCapabilities.stored(server,ref)==NativeBaubleCapabilities.Ultimate.ENDLESS
            ||NativeResonanceService.currentStored(server,p.getUUID(),c.carrier())>=cost(action);
        var live=NativeChannelService.selectedLive(server,p.getUUID(),c.carrier());
        return live!=null&&NativeOathService.active(p);
    }
    private static boolean charge(ServerPlayer p,Context c,SpiritCosts.Action action){
        if(hand(p,c.carrier())==null)return false;
        var server=p.getServer();var attempt=NativeResonanceService.newAttempt();
        var live=NativeChannelService.selectedLive(server,p.getUUID(),c.carrier());
        var paid=live==null?NativeResonanceService.payStored(server,p.getUUID(),c.carrier(),cost(action),attempt)
            :NativeResonanceService.payLive(server,p.getUUID(),live,cost(action),attempt);
        return paid==ResonanceRules.Payment.PAID||paid==ResonanceRules.Payment.WAIVED;
    }
    private static void cue(LivingEntity e){
        if(e.level() instanceof net.minecraft.server.level.ServerLevel level)
            level.sendParticles(ParticleTypes.END_ROD,e.getX(),e.getY()+1,e.getZ(),8,.4,.5,.4,.03);
    }
    private static boolean cleanse(LivingEntity e){
        for(var effect:DISPEL)if(e.hasEffect(effect))return e.removeEffect(effect);return false;
    }
    private static boolean needsCleanse(LivingEntity e){return DISPEL.stream().anyMatch(e::hasEffect);}
    private static void movement(ServerPlayer p,double amount){
        var a=p.getAttribute(Attributes.MOVEMENT_SPEED);if(a==null)return;
        var old=a.getModifier(MOVE);if(old!=null&&old.amount()==amount)return;
        if(old!=null)a.removeModifier(MOVE);
        if(amount>0)a.addTransientModifier(new AttributeModifier(MOVE,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static void clearShield(ServerPlayer p){
        var s=SHIELDS.remove(p);if(s!=null&&p.getEffect(MobEffects.ABSORPTION)==s.effect()
            &&s.effect().getAmplifier()==0&&s.effect().getDuration()<=s.duration())p.removeEffect(MobEffects.ABSORPTION);
    }
    private static void shield(ServerPlayer p,long ticks){
        if(p.hasEffect(MobEffects.ABSORPTION))return;
        int duration=(int)ticks;if(p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,duration,0)))
            SHIELDS.put(p,new Shield(p.getEffect(MobEffects.ABSORPTION),duration));
    }
    private static void passive(ServerPlayer p,Holder<MobEffect> effect,boolean enabled){
        var own=PASSIVE.computeIfAbsent(p,k->new HashMap<>());var applied=own.get(effect);var active=p.getEffect(effect);
        boolean ours=active!=null&&active==applied&&active.getAmplifier()==0&&active.getDuration()<=120;
        if(!enabled){if(ours)p.removeEffect(effect);own.remove(effect);if(own.isEmpty())PASSIVE.remove(p);return;}
        if(active!=null&&!ours){own.remove(effect);return;}
        if(active!=null&&active.getDuration()>60)return;
        p.addEffect(new MobEffectInstance(effect,120,0,true,false,true));
        var granted=p.getEffect(effect);if(granted!=null&&granted.getAmplifier()==0)own.put(effect,granted);
    }
    private static void refresh(ServerPlayer p,Context c){
        if(c==null){
            passive(p,MobEffects.MOVEMENT_SPEED,false);passive(p,MobEffects.REGENERATION,false);
            passive(p,MobEffects.DAMAGE_RESISTANCE,false);clearShield(p);movement(p,0);
            var state=RUNTIME.get(p);if(state!=null)state.suspend();return;
        }
        var f=c.profile();
        passive(p,MobEffects.MOVEMENT_SPEED,f.has("blessing_heal"));
        passive(p,MobEffects.REGENERATION,f.has("blessing_lantern"));
        passive(p,MobEffects.DAMAGE_RESISTANCE,f.has("blessing_crisis"));
        movement(p,f.has("blessing_mobility")&&state(p).moving(now(p))?f.number("move_speed"):0);
    }
    private static void crisis(ServerPlayer p,Context c,float health){
        var f=c.profile();
        if(!f.has("blessing_crisis")||health<=0||health>p.getMaxHealth()*f.number("crisis_threshold"))return;
        if(!affordable(p,c,SpiritCosts.Action.WHITE_CRISIS)
            ||!WhiteFoxBlessingCooldownData.get(p.getServer()).consumeCrisis(p.getUUID(),now(p),f.ticks("crisis_cooldown"))
            ||!charge(p,c,SpiritCosts.Action.WHITE_CRISIS))return;
        shield(p,f.ticks("crisis_shield_ticks"));cleanse(p);cue(p);
    }
    public static void tick(PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p.getServer()==null)return;
        var c=context(p);
        if(c==null&&!RUNTIME.containsKey(p)&&!PASSIVE.containsKey(p))return;
        refresh(p,c);if(c==null||!p.isAlive())return;
        var f=c.profile();
        if(f.has("blessing_cleanse")&&needsCleanse(p)&&affordable(p,c,SpiritCosts.Action.WHITE_CLEANSE)
            &&state(p).consume(CLEANSE,now(p),f.ticks("cleanse_cooldown"))
            &&charge(p,c,SpiritCosts.Action.WHITE_CLEANSE)&&cleanse(p))cue(p);
        crisis(p,c,p.getHealth());
    }
    private static ServerPlayer directAttacker(LivingEntity target,net.minecraft.world.damagesource.DamageSource source){
        if(!(source.getEntity() instanceof ServerPlayer p)||source.getDirectEntity()!=p||!p.isAlive()
            ||p==target||p.level()!=target.level()||p.isAlliedTo(target)||target.isAlliedTo(p)
            ||target instanceof TamableAnimal t&&p.getUUID().equals(t.getOwnerUUID()))return null;
        return p;
    }
    public static void beforeDamage(LivingDamageEvent.Pre e){
        if(e.getEntity().level().isClientSide||!(e.getNewDamage()>0)||!Float.isFinite(e.getNewDamage()))return;
        var attacker=directAttacker(e.getEntity(),e.getSource());
        if(attacker!=null){
            var c=hand(attacker,attacker.getMainHandItem());
            // Never amplify unknown mod caps in the probe.
            if(c!=null&&c.profile().has("blessing_edge")&&net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(e.getEntity().getType()).getNamespace().equals("minecraft")
                &&state(attacker).consume(EDGE,now(attacker),c.profile().ticks("edge_cooldown"))){
                e.setNewDamage((float)Math.min(Float.MAX_VALUE,e.getNewDamage()+c.profile().number("edge_damage")));cue(attacker);
            }
        }
        if(!(e.getEntity() instanceof ServerPlayer p)||!p.isAlive()||e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        var c=context(p);if(c==null)return;
        // Pre is BEFORE absorption in NeoForge 1.21.1. Run the old final-health
        // defense at the absorption boundary, after other reduction callbacks.
        // Keep absorption consumption separate from our reduction; never set health.
        e.getContainer().addModifier(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION,
            (container, absorption)->WhiteFoxDamageBoundary.afterAbsorption(container,absorption,damage->{
                var previousShield=p.getEffect(MobEffects.ABSORPTION);
                float defended=defend(p,c,(float)damage);
                // Vanilla consumes the already determined absorption AFTER this
                // callback. A replacement oath shield belongs to the next hit;
                // compensate only our newly installed effect for that pending
                // consumption, so the old shield's cost is not charged twice.
                var nextShield=p.getEffect(MobEffects.ABSORPTION);
                if(nextShield!=null&&nextShield!=previousShield&&absorption>0)
                    p.setAbsorptionAmount(p.getAbsorptionAmount()+absorption);
                return defended;
            }));
    }
    private static float defend(ServerPlayer p,Context c,float amount){
        var f=c.profile();var state=state(p);
        if(f.has("blessing_guard")&&state.consume(GUARD,now(p),f.ticks("guard_cooldown"))){
            amount=f.guardedDamage(amount);cue(p);
            if(f.has("blessing_mobility"))state.move(now(p),f.ticks("move_ticks"));
            if(f.has("blessing_lantern"))shield(p,f.ticks("guard_shield_ticks"));refresh(p,c);
        }
        if(f.has("blessing_oath")&&state.protectedNow(now(p)))amount*=1-(float)f.number("protection_reduction");
        if(amount>=p.getHealth()){
            if(!f.has("blessing_oath")||p.getMainHandItem().is(Items.TOTEM_OF_UNDYING)||p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                ||!WhiteFoxBlessingCooldownData.get(p.getServer()).consume(p.getUUID(),now(p),f.ticks("oath_cooldown")))return amount;
            amount=Math.max(0,p.getHealth()-1);
            if(charge(p,c,SpiritCosts.Action.WHITE_OATH)){
                cleanse(p);clearShield(p);shield(p,f.ticks("oath_shield_ticks"));state.protect(now(p),f.ticks("protection_ticks"));
            }
            p.getServer().overworld().getDataStorage().save();cue(p);
            p.displayClientMessage(Component.translatable("maid_weapon.native.white.rescue"),true);
        }else crisis(p,c,p.getHealth()-amount);
        return amount;
    }
    public static void afterDamage(LivingDamageEvent.Post e){
        if(e.getEntity().level().isClientSide||!(e.getNewDamage()>0)||!Float.isFinite(e.getNewDamage()))return;
        var p=directAttacker(e.getEntity(),e.getSource());if(p==null)return;
        var c=hand(p,p.getMainHandItem());if(c==null)return;var f=c.profile();
        if(f.has("blessing_heal")&&state(p).hit(now(p),(int)f.number("heal_hits"),f.ticks("heal_interval"),p.getHealth()<p.getMaxHealth())
            &&charge(p,c,SpiritCosts.Action.WHITE_HEAL)){
            p.heal((float)f.number("heal_amount"));cue(p);
            if(f.has("blessing_domain"))p.level().getEntitiesOfClass(EntityMaid.class,p.getBoundingBox().inflate(f.number("ally_radius")),
                m->m.isAlive()&&p.getUUID().equals(m.getOwnerUUID())&&m.getHealth()<m.getMaxHealth()
                    &&m.distanceToSqr(p)<=f.number("ally_radius")*f.number("ally_radius")).stream()
                .min(Comparator.comparingDouble(m->m.distanceToSqr(p))).ifPresent(m->{
                    if(charge(p,c,SpiritCosts.Action.WHITE_MAID_HEAL)){m.heal((float)f.number("ally_heal"));cue(m);}
                });
        }
    }
    private static void cleanup(ServerPlayer p){refresh(p,null);RUNTIME.remove(p);}
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)cleanup(p);}
    public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p&&!e.isCanceled())cleanup(p);}
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)cleanup(p);}
    public static void stop(ServerStoppingEvent e){for(var p:new ArrayList<>(RUNTIME.keySet()))cleanup(p);}
    private NativeWhiteFoxService(){}
}

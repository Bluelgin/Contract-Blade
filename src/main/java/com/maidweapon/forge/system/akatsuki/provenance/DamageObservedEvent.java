package com.maidweapon.forge.system.akatsuki.provenance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;
/** Consumers verify issuer identity; constructing this event cannot mint debt. */
public final class DamageObservedEvent extends Event {
 public final DamageObservation observation;public final LivingEntity target;
 public DamageObservedEvent(DamageObservation o,LivingEntity t){observation=o;target=t;}
}

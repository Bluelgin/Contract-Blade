package com.maidweapon.forge.compat;

import com.maidweapon.forge.entity.BlackFoxSwordActorEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;

/** Native Drive/vertical Drive/WaveEdge bridge. No replacement hit or damage implementation. */
public final class BlackFoxCoreNativeBarrage {
    private static Method drive, wave;
    private static final ThreadLocal<BlackFoxSwordActorEntity> LAUNCH = new ThreadLocal<>();
    public static BlackFoxSwordActorEntity launchingActor() { return LAUNCH.get(); }
    public static void fire(BlackFoxSwordActorEntity actor, Vec3 from, Vec3 target, int kind, double damage) {
        try {
            if (drive == null) {
                var resolvedDrive = Class.forName("mods.flammpfeil.slashblade.slasharts.Drive").getMethod("doSlash",LivingEntity.class,
                        float.class,int.class,Vec3.class,boolean.class,double.class,float.class);
                var resolvedWave = Class.forName("mods.flammpfeil.slashblade.slasharts.WaveEdge").getMethod("doSlash",LivingEntity.class,
                        float.class,int.class,Vec3.class,boolean.class,double.class,float.class,float.class,int.class);
                drive = resolvedDrive; wave = resolvedWave;
            }
            actor.setPos(from.add(0,-actor.getBbHeight()*.75,0));
            Vec3 aim = target.subtract(from);
            actor.setYRot((float)(Math.toDegrees(Math.atan2(aim.z,aim.x))-90));
            actor.setXRot((float)-Math.toDegrees(Math.atan2(aim.y,Math.sqrt(aim.x*aim.x+aim.z*aim.z))));
            var previous = LAUNCH.get(); LAUNCH.set(actor);
            try {
                if (kind == 3) wave.invoke(null,actor,90f,60,Vec3.ZERO,false,damage/2,1.25f,1.6f,1);
                else drive.invoke(null,actor,kind == 2 ? -90f : 0f,60,Vec3.ZERO,false,damage,1.5f);
            } finally { if (previous == null) LAUNCH.remove(); else LAUNCH.set(previous); }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Sword core native barrage",error); }
    }
    private BlackFoxCoreNativeBarrage() { }
}

package com.maidweapon.forge.compat.akatsuki;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.maidweapon.common.AkatsukiClientConfig;
import com.maidweapon.common.AkatsukiSwordTimeline;
import com.maidweapon.forge.compat.SlashBladeCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import java.util.*;

/** Strictly client-local, model-scoped overlay. Native AI/damage/equipment remain sole owners. */
public final class AkatsukiSwordplay {
    public static final String MODEL = "akatsuki_winefox:akatsuki";
    private static final TagKey<Item> WEAPONS = TagKey.create(Registries.ITEM, ResourceLocation.parse("maid_weapon:akatsuki_visual_weapons"));
    private static final Map<Mob, Pose> POSES = new WeakHashMap<>();
    private static final float RAD = (float)(Math.PI/180);
    private record Rotation(AnimatedGeoBone bone, float x, float y, float z) {
        void restore() { bone.setRotation(x,y,z); }
    }
    static final class Pose {
        final AkatsukiSwordTimeline timeline = new AkatsukiSwordTimeline();
        final List<Rotation> originals = new ArrayList<>(7);
        AnimatedGeoModel model;
        Item item;
        boolean enabled;
        org.joml.Vector3f readyGrip;
        final org.joml.Vector3f mouth=new org.joml.Vector3f(),grip=new org.joml.Vector3f();
        final org.joml.Quaternionf bladeRotation=new org.joml.Quaternionf();
        final org.joml.Quaternionf sheathRotation=new org.joml.Quaternionf();
        boolean extraction,bladeInSheath;
    }
    public static void restore(GeckoMaidEntity<?> gecko) {
        var pose = POSES.get(gecko.getEntity());
        if (pose == null) return;
        pose.originals.forEach(Rotation::restore); pose.originals.clear(); pose.enabled=false;
    }
    private static boolean eligible(IMaid maid, Mob entity) {
        if (!AkatsukiClientConfig.SIGNATURE_KATANA.get() || maid == null || !MODEL.equals(maid.getModelId())
                || maid.isYsmModel() || entity.isRemoved() || !entity.isAlive() || entity.isInvisible()
                || entity.isUsingItem() || entity.isSleeping() || entity.isSwimming() || entity.isPassenger()
                || maid.isMaidInSittingPose() || maid.isSitInJoyBlock()) return false;
        String task = maid.getTask().getUid().toString();
        if (!task.equals("touhou_little_maid:attack")
                && !task.equals(SlashBladeCompat.getMaidSlashBladeTaskId())) return false;
        var item = entity.getMainHandItem();
        return !item.isEmpty() && !item.isEdible() && (item.getItem() instanceof SwordItem
                || item.getItem() instanceof AxeItem || item.is(WEAPONS) || SlashBladeCompat.isSlashBlade(item));
    }
    public static void apply(GeckoMaidEntity<?> gecko, float partial) {
        Mob entity=gecko.getEntity(); var maid=gecko.getMaid(); var model=gecko.getCurrentModel();
        var pose=POSES.get(entity);
        if (!eligible(maid,entity) || model==null || !supported(model) || !AkatsukiSwordAssets.ensure()) {
            if(pose!=null)pose.timeline.reset(entity.level().getGameTime());
            return;
        }
        if(pose==null){pose=new Pose();POSES.put(entity,pose);}
        if(pose.model!=model || pose.item!=entity.getMainHandItem().getItem()) {
            pose.timeline.reset(entity.level().getGameTime()); pose.model=model; pose.item=entity.getMainHandItem().getItem();pose.readyGrip=null;
        }
        boolean mainSwing=entity.swinging && entity.swingingArm==InteractionHand.MAIN_HAND;
        pose.timeline.update(entity.level().getGameTime(),true,maid.isSwingingArms() || entity.isAggressive()
                || entity.getTarget()!=null,mainSwing,entity.swingTime);
        pose.enabled=true;
        if(pose.timeline.motion()==AkatsukiSwordTimeline.Motion.IDLE){pose.mouth.set(AkatsukiGripPose.HIP);pose.extraction=false;return;} // No replacement idle pose.
        var clip=AkatsukiSwordAssets.clip(pose.timeline.clip());
        float time=Math.min(clip.length(),pose.timeline.age(entity.level().getGameTime(),partial)/20);
        boolean offhandBusy=!entity.getOffhandItem().isEmpty();
        for(var entry:clip.bones().entrySet()) {
            if(offhandBusy && entry.getKey().startsWith("Left"))continue;
            var bone=model.bones().get(entry.getKey());
            if(bone==null)continue;
            pose.originals.add(new Rotation(bone,bone.getRotationX(),bone.getRotationY(),bone.getRotationZ()));
            var value=AkatsukiSwordAssets.sample(entry.getValue(),time); var initial=bone.getInitialSnapshot();
            // Same Bedrock -> Gecko sign/radian conversion used by TLM's rotation evaluator.
            bone.setRotation(initial.rotationValueX-value.x()*RAD,initial.rotationValueY-value.y()*RAD,
                    initial.rotationValueZ+value.z()*RAD);
        }
        AkatsukiGripPose.apply(pose,!offhandBusy,pose.timeline.age(entity.level().getGameTime(),partial));
    }
    private static boolean supported(AnimatedGeoModel model) {
        return model.bones().keySet().containsAll(Set.of("UpperBody","RightArm","RightForeArm",
                "RightHand","RightHandLocator","LeftArm","LeftForeArm","LeftHand","LeftHandLocator"));
    }
    public static boolean replaces(Mob entity) {
        var pose=POSES.get(entity);
        return pose!=null && pose.enabled && pose.item==entity.getMainHandItem().getItem()
                && !entity.isUsingItem() && AkatsukiClientConfig.SIGNATURE_KATANA.get();
    }
    static Pose pose(Mob entity) { return POSES.get(entity); }
    static void clear() { POSES.values().forEach(p->p.originals.forEach(Rotation::restore)); POSES.clear(); AkatsukiSwordAssets.invalidate(); }
    private AkatsukiSwordplay() { }
}

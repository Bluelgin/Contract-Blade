package com.maidweapon.forge.compat.akatsuki;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.*;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.maidweapon.common.AkatsukiSwordTimeline;
import org.joml.*;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Real TLM parser and rig mathematics, without a game window or opening a player save. */
public class AkatsukiRigValidator {
    static void validateHooks() throws Exception {
        String owner="com/github/tartaricacid/touhoulittlemaid/client/";
        ClassNode held=new ClassNode();
        try(var input=AkatsukiRigValidator.class.getClassLoader().getResourceAsStream(owner+"renderer/entity/geckolayer/GeckoLayerMaidHeld.class")) {
            if(input==null)throw new AssertionError("Missing native held layer");
            new ClassReader(input).accept(held,0);
        }
        String descriptor="(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Mob;FFFFFF)V";
        var render=held.methods.stream().filter(m->m.name.equals("render")&&m.desc.equals(descriptor)).findFirst().orElseThrow();
        var stacks=new ArrayList<String>();
        for(var instruction:render.instructions)if(instruction instanceof MethodInsnNode call
                &&call.desc.equals("()Lnet/minecraft/world/item/ItemStack;")) {
            var next=instruction.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();
            if(next instanceof VarInsnNode store&&store.getOpcode()==Opcodes.ASTORE)
                stacks.add(call.name+":"+store.var);
        }
        if(!stacks.equals(List.of("getOffhandItem:11","getMainHandItem:12")))
            throw new AssertionError("Mainhand local selector drift: "+stacks);
        var gecko=new ClassNode();
        try(var input=AkatsukiRigValidator.class.getClassLoader().getResourceAsStream(owner+"entity/GeckoMaidEntity.class")) {
            if(input==null)throw new AssertionError("Missing native Gecko maid");
            new ClassReader(input).accept(gecko,0);
        }
        String poseDescriptor="(Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/core/molang/context/AnimationContext;Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/core/event/predicate/AnimationEvent;)Z";
        if(gecko.methods.stream().noneMatch(m->m.name.equals("setCustomAnimations")&&m.desc.equals(poseDescriptor)))
            throw new AssertionError("Pose hook descriptor drift");
        System.out.println("AKATSUKI_NATIVE_HOOK_SELECTORS_PASS: TLM pose descriptor and mainhand-only local checked; not a game transformation test");
    }
    static final float RAD=(float)(java.lang.Math.PI/180);
    static Matrix4f bone(AnimatedGeoModel model,String name) {
        var stack=new PoseStack();for(var b:AkatsukiSwordVisual.hierarchy(model,name))RenderUtils.prepMatrixForBone(stack,b);
        return new Matrix4f(stack.last().pose());
    }
    public static void main(String[] args) throws Exception {
        validateHooks();
        Path root=Path.of(args[0]), art=root.resolve("art/akatsuki/swordplay");Files.createDirectories(art);
        var raw=Converter.fromJsonString(Files.readString(root.resolve("art/akatsuki/midnight-b-optimized/akatsuki-b-optimized.geo.json")));
        var model=new AnimatedGeoModel(GeoBuilder.getGeoBuilder().constructGeoModel(RawGeometryTree.parseHierarchy(raw)));
        var clips=new HashMap<String,AkatsukiSwordAssets.Clip>();
        var json=JsonParser.parseString(Files.readString(root.resolve("src/main/resources/assets/maid_weapon/akatsuki/swordplay.json"))).getAsJsonObject();
        for(var entry:json.getAsJsonObject("animations").entrySet()) {
            var c=entry.getValue().getAsJsonObject();var bones=new HashMap<String,AkatsukiSwordAssets.Key[]>();
            for(var b:c.getAsJsonObject("bones").entrySet()){
                var keys=new ArrayList<AkatsukiSwordAssets.Key>();
                for(var k:b.getValue().getAsJsonObject().getAsJsonObject("rotation").entrySet()){
                    var a=k.getValue().getAsJsonArray();keys.add(new AkatsukiSwordAssets.Key(Float.parseFloat(k.getKey()),a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()));
                }
                keys.sort(Comparator.comparing(AkatsukiSwordAssets.Key::time));bones.put(b.getKey(),keys.toArray(AkatsukiSwordAssets.Key[]::new));
            }
            clips.put(entry.getKey(),new AkatsukiSwordAssets.Clip(c.get("animation_length").getAsFloat(),bones));
        }
        var field=AkatsukiSwordAssets.class.getDeclaredField("clips");field.setAccessible(true);field.set(null,Map.copyOf(clips));
        Map<String,Matrix4f> original=new HashMap<>();
        for(String name:model.bones().keySet())original.put(name,bone(model,name));
        var rows=new JsonArray();float maxGap=0;
        for(String motion:List.of("idle","draw","ready","cut_1","cut_2","cut_3","sheathe")){
            for(float fraction:new float[]{0,.2f,.4f,.6f,.8f,1}) {
                for(var b:model.bones().values()){var s=b.getInitialSnapshot();b.setRotation(s.rotationValueX,s.rotationValueY,s.rotationValueZ);}
                var pose=new AkatsukiSwordplay.Pose();pose.model=model;
                if(motion.equals("draw")){pose.timeline.update(0,true,true,false,0);}
                if(motion.equals("ready")||motion.equals("sheathe")){pose.timeline.update(0,true,true,false,0);pose.timeline.update(8,true,true,false,0);}
                if(motion.startsWith("cut")){
                    int cut=Integer.parseInt(motion.substring(4));
                    for(int i=0;i<cut;i++){pose.timeline.update(i*2,true,true,true,0);if(i<cut-1)pose.timeline.update(i*2+1,true,true,false,0);}
                }
                if(motion.equals("sheathe"))pose.timeline.update(48,true,false,false,0);
                var clip=clips.get(motion);float age=fraction*(clip==null?0:clip.length()*20);
                if(clip!=null)for(var e:clip.bones().entrySet()){
                    var v=AkatsukiSwordAssets.sample(e.getValue(),age/20);var b=model.bones().get(e.getKey());var s=b.getInitialSnapshot();
                    b.setRotation(s.rotationValueX-v.x()*RAD,s.rotationValueY-v.y()*RAD,s.rotationValueZ+v.z()*RAD);
                }
                AkatsukiGripPose.apply(pose,true,age);
                var hand=AkatsukiGripPose.locator(model,"RightHandLocator").getTranslation(new Vector3f());
                hand=AkatsukiGripPose.locator(model,"UpperBody").invert().transformPosition(hand).mul(16);
                if(pose.extraction){float gap=hand.distance(pose.grip);maxGap=java.lang.Math.max(maxGap,gap);if(gap>.03)throw new AssertionError("Grip gap "+motion+" "+fraction+" "+gap);}
                var row=new JsonObject();row.addProperty("motion",motion);row.addProperty("fraction",fraction);
                row.addProperty("inSheath",pose.bladeInSheath||motion.equals("idle"));
                var matrices=new JsonObject();var angles=new JsonObject();
                for(var e:model.bones().entrySet()){
                    var b=e.getValue();var m=bone(model,e.getKey());
                    float[] values=m.mul(new Matrix4f(original.get(e.getKey())).invert()).get(new float[16]);
                    for(float v:values)if(!Float.isFinite(v))throw new AssertionError("Invalid matrix");
                    matrices.add(e.getKey(),new Gson().toJsonTree(values));
                    var s=b.getInitialSnapshot();angles.add(e.getKey(),new Gson().toJsonTree(new float[]{(b.getRotationX()-s.rotationValueX)/RAD,(b.getRotationY()-s.rotationValueY)/RAD,(b.getRotationZ()-s.rotationValueZ)/RAD}));
                }
                row.add("boneDeltas",matrices);row.add("rotations",angles);
                var body=AkatsukiGripPose.locator(model,"UpperBody");
                var sheath=new Matrix4f(body).translate(new Vector3f(pose.mouth).div(16))
                        .rotate(pose.extraction?pose.sheathRotation:new Quaternionf().rotateX((float)java.lang.Math.toRadians(-70)).rotateZ((float)java.lang.Math.toRadians(-8)));
                var blade=pose.bladeInSheath||motion.equals("idle")?new Matrix4f(sheath):pose.extraction?
                        new Matrix4f(body).translate(new Vector3f(pose.grip).div(16)).rotate(pose.bladeRotation):AkatsukiGripPose.locator(model,"RightHandLocator");
                row.add("bladeMatrix",new Gson().toJsonTree(blade.get(new float[16])));row.add("sheathMatrix",new Gson().toJsonTree(sheath.get(new float[16])));rows.add(row);
            }
        }
        Files.writeString(art.resolve("native-poses.json"),new Gson().toJson(rows));
        System.out.println("AKATSUKI_NATIVE_RIG_PASS: "+rows.size()+" poses, maximum extraction wrist gap="+maxGap+" model pixels; tail/leg geometry unmodified");
    }
}

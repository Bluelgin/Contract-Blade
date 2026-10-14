package com.maidweapon.forge.client;

import com.maidweapon.common.AkatsukiDomainConfig;
import com.maidweapon.forge.network.AkatsukiDomainNetwork.Cue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.*;
import static com.maidweapon.forge.network.AkatsukiDomainNetwork.*;

/** Procedural client mesh: blood moon, boundary, cuts and swords. No entities, health or shaders. */
@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class AkatsukiDomainVisuals {
    private record Domain(Cue cue, long end) {}
    private record Burst(Cue cue, long start) {}
    private static final Map<UUID, Domain> DOMAINS = new LinkedHashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static Object levelIdentity;
    private static final int BLOOD = 0xD50F3C, LIGHT = 0xFFC7D4, DARK = 0x460A22;
    private static void world() {
        var level = Minecraft.getInstance().level;
        if (level != levelIdentity) { DOMAINS.clear(); BURSTS.clear(); levelIdentity = level; }
    }
    public static void accept(Cue p) {
        world(); var level = Minecraft.getInstance().level;
        if (level == null || !Double.isFinite(p.center().lengthSqr()) || !Double.isFinite(p.target().lengthSqr())
                || p.radius() < 0 || p.radius() > 16 || p.ticks() < 0 || p.ticks() > 400
                || p.kind() < 0 || p.kind() > FINAL) return;
        long time = level.getGameTime();
        if (p.kind() == END) { DOMAINS.remove(p.owner()); BURSTS.removeIf(b -> b.cue().owner().equals(p.owner())); return; }
        if (p.kind() == STATE) {
            if (DOMAINS.size() >= 16 && !DOMAINS.containsKey(p.owner())) return;
            DOMAINS.put(p.owner(), new Domain(p, time + p.ticks()));
        } else {
            if (BURSTS.size() >= 96) BURSTS.remove(0);
            BURSTS.add(new Burst(p, time));
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        world(); var level = Minecraft.getInstance().level;
        if (level == null) return;
        long time = level.getGameTime();
        DOMAINS.values().removeIf(d -> d.end() <= time);
        BURSTS.removeIf(b -> time - b.start() > 24);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        world(); var level = Minecraft.getInstance().level;
        if (level == null || DOMAINS.isEmpty() && BURSTS.isEmpty()) return;
        var camera = e.getCamera().getPosition();
        double time = level.getGameTime() + e.getPartialTick();
        int quality = AkatsukiDomainConfig.QUALITY.get();
        double density = AkatsukiDomainConfig.DENSITY.get();
        var pose = e.getPoseStack(); pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
        var matrix = pose.last().pose();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        RenderSystem.depthMask(false); RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var b = Tesselator.getInstance().getBuilder(); b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        try {
            DOMAINS.values().stream().filter(d -> d.cue().center().distanceToSqr(camera) < 4096)
                    .sorted(Comparator.comparingDouble(d -> d.cue().center().distanceToSqr(camera)))
                    .limit(quality == 0 ? 2 : 6).forEach(d -> garden(b, matrix, d, time, e.getPartialTick(), quality));
            BURSTS.stream().filter(v -> v.cue().center().distanceToSqr(camera) < 4096)
                    .limit(quality == 0 ? 12 : 48).forEach(v -> burst(b, matrix, v, time, quality, density));
            BufferUploader.drawWithShader(b.end());
        } finally {
            pose.popPose(); RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }
    private static Vec3 center(Cue cue, float partial) {
        var p = Minecraft.getInstance().level.getPlayerByUUID(cue.owner());
        return p == null ? cue.center() : new Vec3(Mth.lerp(partial, p.xo, p.getX()),
                Mth.lerp(partial, p.yo, p.getY()), Mth.lerp(partial, p.zo, p.getZ()));
    }
    private static void garden(BufferBuilder b, Matrix4f m, Domain d, double time, float partial, int quality) {
        var p = d.cue(); var center = center(p, partial); double radius = p.radius();
        float fade = (float) Math.min(1, Math.max(0, (d.end() - time) / 20));
        int segments = quality == 0 ? 24 : 64;
        boolean complete = (p.features() & 512) != 0;
        // The moon is local to the domain and depth-tested, so it never replaces the global sky.
        var moon = center.add(0, 11.5, 0);
        disk(b, m, moon, complete ? 4.2 : 3.3, segments, BLOOD, fade * .6f);
        ring(b, m, moon.add(0, -.015, 0), complete ? 4.6 : 3.7, .07, segments, LIGHT, fade * .5f);
        disk(b, m, moon.add(1.15, -.025, .2), complete ? 3.2 : 2.5, segments, DARK, fade * .48f);
        ring(b, m, center.add(0, .12, 0), radius, .08, segments, BLOOD, fade * .75f);
        if ((p.features() & 16) != 0) ring(b, m, center.add(0, .2, 0), radius * .88, .025, segments, LIGHT, fade * .4f);
        for (int i=0; i<segments; i++) {
            double a = i * Math.PI * 2 / segments, next = (i+1) * Math.PI * 2 / segments;
            var low = at(center, radius, a, .15); var low2 = at(center, radius, next, .15);
            var high = at(center, radius, a, 7.5); var high2 = at(center, radius, next, 7.5);
            quad(b, m, low, low2, high2, high, BLOOD, fade * (complete ? .065f : .035f));
            if (i % (quality == 0 ? 6 : 8) == 0) {
                var top = at(center, radius, a + .06, 7);
                ribbon(b, m, low, top, .025, BLOOD, fade * .35f);
                if (complete) ribbon(b, m, top, at(center, radius * .45, a+.45, 10), .035, LIGHT, fade * .28f);
            }
        }
        int scars = quality == 0 ? 3 : 8;
        for (int i=0; i<scars; i++) {
            double a = i * Math.PI * 2 / scars + time * .003;
            var start = at(center, radius*.8, a, 2.4 + Math.sin(time*.04+i));
            ribbon(b,m,start,start.add(Math.cos(a+.7)*2,1.2,Math.sin(a+.7)*2),.035,LIGHT,fade*.4f);
        }
    }
    private static void burst(BufferBuilder b, Matrix4f m, Burst burst, double now, int quality, double density) {
        var p = burst.cue(); double age = now-burst.start(), t = Math.min(1, age/18);
        if (age < 0 || age > 24) return;
        float fade = (float)Math.max(0, 1-age/24); var target = p.target();
        var random = new Random(p.owner().getLeastSignificantBits() ^ burst.start());
        switch (p.kind()) {
            case RING -> ring(b,m,p.center().add(0,.7,0),Math.max(.1,t*5),.12,quality==0?24:64,BLOOD,fade);
            case LOCK, ADAPT -> {
                ring(b,m,target.add(0,.6,0),.8,.055,24,LIGHT,fade);
                sword(b,m,target.add(0,2.5,0),new Vec3(0,-1,0),.7,fade);
            }
            case CLOAK -> {
                for(int i=0;i<3;i++) ring(b,m,p.center().add(0,.3+i*.65,0),.85,.08,24,LIGHT,fade*.7f);
            }
            case SWORDS, COUNTER, RAIN, FINAL -> {
                int count = p.kind()==RAIN ? Mth.clamp(p.blades(),8,96) : p.kind()==FINAL ? 24 : 6;
                count = Math.max(2,(int)(count*density*(quality==0?.25:quality==1?.65:1)));
                for(int i=0;i<count;i++) {
                    double a = i*Math.PI*2/count + random.nextDouble()*.3;
                    Vec3 end = p.kind()==RAIN ? target.add((random.nextDouble()-.5)*p.radius()*1.3,0,(random.nextDouble()-.5)*p.radius()*1.3) : target;
                    Vec3 start = p.kind()==RAIN ? end.add(.8,9+random.nextDouble()*4,.4)
                            : at(p.center(),p.kind()==FINAL?p.radius():4,a,3+random.nextDouble()*3);
                    double progress = Math.max(0,Math.min(1,(age-i%5)/14));
                    Vec3 point = start.lerp(end,progress), direction=end.subtract(start).normalize();
                    sword(b,m,point,direction,p.kind()==FINAL?2.7:1.7,fade);
                    ribbon(b,m,point.subtract(direction.scale(1.5)),point,.035,BLOOD,fade*.35f);
                }
            }
            default -> {
                int count = p.kind()==CHAIN?4:2;
                for(int i=0;i<count;i++) {
                    double a=random.nextDouble()*Math.PI;
                    var delta=new Vec3(Math.cos(a)*2,1,Math.sin(a)*2);
                    ribbon(b,m,target.subtract(delta),target.add(delta),.08*(1-t)+.01,LIGHT,fade);
                }
            }
        }
    }
    private static Vec3 at(Vec3 center,double radius,double angle,double y) { return center.add(Math.cos(angle)*radius,y,Math.sin(angle)*radius); }
    private static void disk(BufferBuilder b, Matrix4f m, Vec3 center, double radius,int segments,int color,float alpha) {
        for(int i=0;i<segments;i++) tri(b,m,center,at(center,radius,i*Math.PI*2/segments,0),at(center,radius,(i+1)*Math.PI*2/segments,0),color,alpha);
    }
    private static void ring(BufferBuilder b,Matrix4f m,Vec3 center,double radius,double width,int segments,int color,float alpha) {
        for(int i=0;i<segments;i++) {
            double a=i*Math.PI*2/segments,n=(i+1)*Math.PI*2/segments;
            quad(b,m,at(center,radius,a,0),at(center,radius,n,0),at(center,radius+width,n,0),at(center,radius+width,a,0),color,alpha);
        }
    }
    private static void sword(BufferBuilder b,Matrix4f m,Vec3 tip,Vec3 direction,double length,float alpha) {
        var side=direction.cross(new Vec3(0,1,0)); if(side.lengthSqr()<.01) side=new Vec3(1,0,0); side=side.normalize();
        var base=tip.subtract(direction.scale(length));
        tri(b,m,tip,base.add(side.scale(.13)),base,BLOOD,alpha);
        tri(b,m,tip,base,base.subtract(side.scale(.13)),LIGHT,alpha);
        ribbon(b,m,base.add(side.scale(.25)),base.subtract(side.scale(.25)),.045,BLOOD,alpha);
        ribbon(b,m,base,base.subtract(direction.scale(.4)),.035,LIGHT,alpha*.8f);
    }
    private static void ribbon(BufferBuilder b,Matrix4f m,Vec3 start,Vec3 end,double width,int color,float alpha) {
        var side=end.subtract(start).cross(new Vec3(0,1,0));
        if(side.lengthSqr()<.001) side=new Vec3(1,0,0); side=side.normalize().scale(width);
        quad(b,m,start.add(side),end.add(side),end.subtract(side),start.subtract(side),color,alpha);
    }
    private static void quad(BufferBuilder b,Matrix4f m,Vec3 a,Vec3 c,Vec3 d,Vec3 e,int color,float alpha) {
        tri(b,m,a,c,d,color,alpha); tri(b,m,a,d,e,color,alpha);
    }
    private static void tri(BufferBuilder b,Matrix4f m,Vec3 a,Vec3 c,Vec3 d,int color,float alpha) {
        vertex(b,m,a,color,alpha); vertex(b,m,c,color,alpha); vertex(b,m,d,color,alpha);
    }
    private static void vertex(BufferBuilder b,Matrix4f m,Vec3 v,int color,float alpha) {
        b.vertex(m,(float)v.x,(float)v.y,(float)v.z).color((color>>16)&255,(color>>8)&255,color&255,Mth.clamp((int)(alpha*255),0,255)).endVertex();
    }
    private AkatsukiDomainVisuals() {}
}

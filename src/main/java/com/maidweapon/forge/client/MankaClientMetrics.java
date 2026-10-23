package com.maidweapon.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import java.util.*;

/** Opt-in timings of actual rendered frames, never inferred from a simulation. */
@Mod.EventBusSubscriber(modid="maid_weapon", value=Dist.CLIENT)
public final class MankaClientMetrics {
    private static long start, previous;
    private static String screen = "";
    private static final List<Double> FRAMES=new ArrayList<>();
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if(!Boolean.getBoolean("contractblade.danmaku.acceptance") || event.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance();
        String next=mc.screen==null?"InGame":mc.screen.getClass().getSimpleName();
        if(!screen.equals(next)) {
            screen=next;org.slf4j.LoggerFactory.getLogger("MankaAcceptance").info("MANKA_SCREEN {}",screen);
        }
        if(mc.level==null){start=0;previous=0;FRAMES.clear();return;}
        long now=System.nanoTime();if(start==0)start=now;
        if(previous!=0) FRAMES.add((now-previous)/1e6);previous=now;
        if(now-start<10_000_000_000L || FRAMES.isEmpty())return;
        var sorted=new ArrayList<>(FRAMES);Collections.sort(sorted);
        double p99=sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*.99)));
        var rt=Runtime.getRuntime();
        org.slf4j.LoggerFactory.getLogger("MankaAcceptance").info(
            "MANKA_CLIENT_METRICS averageFPS={} p99frameMs={} p99FPS={} heapMiB={} visualEntries={} particleCount={} particles={}",
            FRAMES.size()*1e9/(now-start),p99,1000/p99,(rt.totalMemory()-rt.freeMemory())/1048576,
            DanmakuWorldVisuals.size(),mc.particleEngine.countParticles(),mc.options.particles().get());
        start=now;FRAMES.clear();
    }
    private MankaClientMetrics() { }
}

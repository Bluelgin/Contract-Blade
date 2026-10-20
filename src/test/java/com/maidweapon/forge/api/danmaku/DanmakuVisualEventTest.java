package com.maidweapon.forge.api.danmaku;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class DanmakuVisualEventTest {
    private static final ResourceLocation WORLD=new ResourceLocation("minecraft:overworld");
    private DanmakuVisualEvent cue(long tick,int form,int phase,Vec3 pos) {
        return new DanmakuVisualEvent(DanmakuVisualEvent.Kind.WAVE,new UUID(1,2),new UUID(3,4),
                WORLD,tick,form,phase,42,pos,180);
    }
    @Test void wireRoundTripStaysSmall() {
        var event=cue(100,3,3,new Vec3(4.5,65,9.5));
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {event.encode(buffer);assertTrue(buffer.readableBytes()<128);
            assertEquals(event,DanmakuVisualEvent.decode(buffer));assertEquals(0,buffer.readableBytes());
        }finally{buffer.release();}
    }
    @Test void staleAndCrossDimensionEventsCannotCreateVisuals() {
        var event=cue(100,3,3,Vec3.ZERO);
        assertTrue(event.valid(WORLD,100));
        assertFalse(event.valid(new ResourceLocation("minecraft:the_nether"),100));
        assertFalse(event.valid(WORLD,181));assertFalse(event.valid(WORLD,19));
    }
    @Test void invalidAtlasIndexAndNonFinitePositionAreRejected() {
        assertFalse(cue(100,3,4,Vec3.ZERO).valid(WORLD,100));
        assertFalse(cue(100,4,0,Vec3.ZERO).valid(WORLD,100));
        assertFalse(cue(100,0,0,new Vec3(Double.NaN,0,0)).valid(WORLD,100));
    }
}

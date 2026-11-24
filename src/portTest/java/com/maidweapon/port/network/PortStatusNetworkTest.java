package com.maidweapon.port.network;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PortStatusNetworkTest {
    @Test void boundedStatusRoundTrip(){
        var b=Unpooled.buffer();
        try{
            var s=new PortStatusNetwork.Status(true,true,850,1000,UUID.randomUUID());
            PortStatusNetwork.Status.CODEC.encode(b,s);
            assertEquals(26,b.readableBytes());
            assertEquals(s,PortStatusNetwork.Status.CODEC.decode(b));
            assertThrows(IllegalArgumentException.class,()->new PortStatusNetwork.Status(false,false,1,1000,null));
            assertThrows(IllegalArgumentException.class,()->new PortStatusNetwork.Status(true,false,1001,1000,UUID.randomUUID()));
            assertThrows(IllegalArgumentException.class,()->new PortStatusNetwork.Status(true,false,1,1000,null));
        }finally{b.release();}
    }
}

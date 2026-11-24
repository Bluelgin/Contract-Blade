package com.maidweapon.port.network;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PortStatusNetworkTest {
    @Test void boundedStatusRoundTrip(){
        var b=Unpooled.buffer();
        try{
            var s=new PortStatusNetwork.Status(true,850,1000);
            PortStatusNetwork.Status.CODEC.encode(b,s);
            assertEquals(9,b.readableBytes());
            assertEquals(s,PortStatusNetwork.Status.CODEC.decode(b));
            assertThrows(IllegalArgumentException.class,()->new PortStatusNetwork.Status(false,1,1000));
            assertThrows(IllegalArgumentException.class,()->new PortStatusNetwork.Status(true,1001,1000));
        }finally{b.release();}
    }
}

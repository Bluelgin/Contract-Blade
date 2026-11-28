package com.maidweapon.port.domain.oath;

/** Pure fixed-point upkeep and original default combat tuning. */
public final class OathRules {
    public static final long SCALE=1_000_000_000L;
    public static final double BURN=.025, PLAYER_DAMAGE=.20, MAID_DAMAGE=.30, PLAYER_DEFENSE=.10,
        BACKLASH_OUT=.10,BACKLASH_IN=.10;
    public static final int BACKLASH_TICKS=3600;
    public record Step(int cost,long remainder){}
    public static Step tick(int maximum,double fraction,long remainder){
        if(maximum<1||maximum>1_000_000||!Double.isFinite(fraction)||fraction<0||fraction>1
            ||remainder<0||remainder>=SCALE)throw new IllegalArgumentException("Invalid oath burn");
        long total=remainder+Math.round(maximum*fraction*SCALE/20.0);
        return new Step((int)(total/SCALE),total%SCALE);
    }
    public static double outgoingPenalty(int levels){return 1-Math.min(.5,Math.max(0,Math.min(5,levels))*BACKLASH_OUT);}
    public static double incomingPenalty(int levels){return 1+Math.min(.5,Math.max(0,Math.min(5,levels))*BACKLASH_IN);}
    private OathRules(){}
}

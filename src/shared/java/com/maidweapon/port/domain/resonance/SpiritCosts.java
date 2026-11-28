package com.maidweapon.port.domain.resonance;

/** Original basis-point prices, shared by both versions. */
public final class SpiritCosts {
    public enum Action {
        BLACK_SUMMON(500), BLACK_UPKEEP(100), BLACK_MODE(0),
        WHITE_CLEANSE(50), WHITE_HEAL(70), WHITE_MAID_HEAL(50),
        WHITE_CRISIS(300), WHITE_OATH(600),
        RED_DOMAIN_NORMAL(15), RED_DOMAIN_RAMPAGE(35), RED_SPACE_CUT(20),
        RED_CORE_BURST(200), RED_LIMIT_BREAK(200), RED_STELLAR_FINISH(200);
        public final int basisPoints;
        Action(int points){basisPoints=points;}
    }
    public static int scaledPoints(int maximum,int basisPoints){
        if(maximum<=0||basisPoints<0||basisPoints>10000)return Integer.MAX_VALUE;
        if(basisPoints==0)return 0;
        return (int)Math.min(Integer.MAX_VALUE,Math.max(1L,((long)maximum*basisPoints+9999)/10000));
    }
    private SpiritCosts(){}
}

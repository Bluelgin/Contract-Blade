package com.maidweapon.port.domain.resonance;

/** Current Forge default sequence. No skipped stages or offline accumulation. */
public final class SpiritVeinRules {
    private static final double[] MULTIPLIERS={1,1.25,1.60,2};
    public enum Upgrade { SUCCESS, ALREADY, SKIPPED, INVALID }
    public static int maxStage(){return MULTIPLIERS.length-1;}
    public static double multiplier(int stage){
        if(stage<0||stage>maxStage())throw new IllegalArgumentException("Invalid spirit vein stage");
        return MULTIPLIERS[stage];
    }
    public static Upgrade evaluate(int current,int requested){
        if(current<0||current>maxStage()||requested<0||requested>maxStage())return Upgrade.INVALID;
        if(requested<=current)return Upgrade.ALREADY;
        return requested==current+1?Upgrade.SUCCESS:Upgrade.SKIPPED;
    }
    private SpiritVeinRules(){}
}

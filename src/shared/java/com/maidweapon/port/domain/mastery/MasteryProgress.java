package com.maidweapon.port.domain.mastery;

/** Immutable default and configurable Forge mastery rules, no platform dependencies. */
public record MasteryProgress(int level,int totalKills,int unlockedTier,int dragonKills,int witherKills,
                              java.util.List<Integer> progress) {
    public static final int MAX_LEVEL=10;
    private static final int[] TIERS={1,2,3,4,5,5,5,5,5};
    public static final MasteryProgress INITIAL=new MasteryProgress(1,0,0,0,0,java.util.Collections.nCopies(11,0));
    public MasteryProgress {
        progress=java.util.List.copyOf(progress);
        if(level<1||level>10||totalKills<0||unlockedTier<0||unlockedTier>5||dragonKills<0||witherKills<0
            ||progress.size()!=11||progress.stream().anyMatch(v->v<0||v>1_000_000))throw new IllegalArgumentException("Invalid mastery");
    }
    public record Rule(int tier,int kills,java.util.Set<String> entities,boolean entityMode){
        public Rule{
            entities=java.util.Set.copyOf(entities);
            if(tier<0||tier>5||kills<1||kills>1_000_000)throw new IllegalArgumentException("Invalid rule");
        }
        boolean matches(int defeated,String id){return entityMode?entities.contains(id):defeated>=tier;}
    }
    private static int inc(int v){return v==Integer.MAX_VALUE?v:v+1;}
    public MasteryProgress credit(int tier,String entityId,Rule custom){
        if(tier<0||tier>5||entityId==null)throw new IllegalArgumentException("Invalid kill");
        int total=inc(totalKills),d=dragonKills,w=witherKills,next=level,unlocked=unlockedTier;
        var counts=new java.util.ArrayList<>(progress);
        if(level<10){
            boolean upgrade=false;
            if(custom!=null){
                if(custom.matches(tier,entityId)){
                    int target=level+1;counts.set(target,Math.min(1_000_000,counts.get(target)+1));
                    upgrade=counts.get(target)>=custom.kills();
                }
            }else if(tier>=TIERS[level-1]){
                boolean dragon=entityId.equals("minecraft:ender_dragon"),wither=entityId.equals("minecraft:wither");
                if(level>=5){if(dragon)d=inc(d);if(wither)w=inc(w);}
                upgrade=switch(level){
                    case 5->dragon;
                    case 6->wither;
                    case 7->d>=3&&w>=3;
                    case 8->(long)d+w>=5;
                    case 9->(long)d+w>=10;
                    default->true;
                };
            }
            if(upgrade){next++;unlocked=Math.max(unlocked,tier);}
        }
        return new MasteryProgress(next,total,unlocked,d,w,counts);
    }
}

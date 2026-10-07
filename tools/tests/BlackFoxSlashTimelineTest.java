import com.maidweapon.forge.system.fox.challenge.BlackFoxSlashTimeline;
import com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline;

public class BlackFoxSlashTimelineTest {
    public static void main(String[] args) {
        int checks = 0;
        for (int position = 0; position <= 100; position++) {
            float previous = 1;
            for (int frame = 0; frame <= 1200; frame++) {
                float age = BlackFoxDimensionTimeline.HIT + frame / 100f;
                float value = BlackFoxSlashTimeline.visibility(age, position / 100f);
                if (!Float.isFinite(value) || value < 0 || value > previous)
                    throw new AssertionError("Visibility must only decrease");
                previous = value;
                checks++;
            }
        }
        if (BlackFoxSlashTimeline.visibility(63, .5f) != 0
                || BlackFoxSlashTimeline.visibility(64, .5f) != 1
                || BlackFoxSlashTimeline.visibility(72, .1f) != 0
                || BlackFoxSlashTimeline.visibility(72, .9f) != 1
                || BlackFoxSlashTimeline.visibility(76, 1) != 0)
            throw new AssertionError("Hit/hold/head/tail/end boundaries");
        for (var layer : BlackFoxSlashTimeline.Layer.values()) {
            for (int position = 0; position <= 100; position++) {
                float previous = 1;
                for (int frame = 0; frame <= 1400; frame++) {
                    float age = BlackFoxDimensionTimeline.HIT + frame/100f;
                    float value = BlackFoxSlashTimeline.visibility(age,position/100f,layer)
                            * BlackFoxSlashTimeline.brightness(age,layer);
                    if(!Float.isFinite(value) || value<0 || value>previous+.000001f)
                        throw new AssertionError("Layer must decay monotonically: " + layer);
                    previous=value; checks++;
                }
            }
            if(BlackFoxSlashTimeline.visibility(64+layer.duration,1,layer)!=0)
                throw new AssertionError("Layer lifetime: " + layer);
        }
        if(BlackFoxSlashTimeline.brightness(68,BlackFoxSlashTimeline.Layer.CORE)!=0
                || BlackFoxSlashTimeline.brightness(68,BlackFoxSlashTimeline.Layer.BAND)<=0
                || BlackFoxSlashTimeline.brightness(73,BlackFoxSlashTimeline.Layer.BAND)!=0
                || BlackFoxSlashTimeline.brightness(73,BlackFoxSlashTimeline.Layer.HALO)<=0
                || BlackFoxSlashTimeline.brightness(76,BlackFoxSlashTimeline.Layer.TRAIL)<=0)
            throw new AssertionError("Layers must end at distinct times");
        System.out.println("BLACK_FOX_SLASH_TIMELINE_PASS: " + checks + " monotonic samples and head/tail boundaries");
    }
}

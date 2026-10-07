import com.maidweapon.forge.system.fox.challenge.BlackFoxFight;
import com.maidweapon.forge.system.fox.challenge.BlackFoxSlashPresentation;

public final class BlackFoxSlashPresentationTest {
    public static void main(String[] args) {
        for(int i=0;i<=1000;i++) {
            float along=i/1000f,angle=BlackFoxSlashPresentation.meleeAngle(along);
            if(Math.cos(angle)<BlackFoxSlashPresentation.MELEE_DOT-1e-6)throw new AssertionError("visible arc outside hit cone");
            if(i>0 && BlackFoxSlashPresentation.arrival(along)<BlackFoxSlashPresentation.arrival((i-1)/1000f))
                throw new AssertionError("sweep reverses");
        }
        if(BlackFoxSlashPresentation.meleeAlpha(8,.1f,1)!=0
                || BlackFoxSlashPresentation.meleeAlpha(8,.5f,1)<=0
                || BlackFoxSlashPresentation.meleeAlpha(8,1,1)!=0)
            throw new AssertionError("t1 must disappear while t2 appears and t3 has not arrived");
        if(BlackFoxSlashPresentation.arrival(.25f)<=BlackFoxSlashPresentation.MELEE_SWEEP*.25f)
            throw new AssertionError("missing slow start");
        for(var skill:BlackFoxFight.Skill.values()) {
            if(!BlackFoxSlashPresentation.supported(skill)) continue;
            float speed=BlackFoxSlashPresentation.speed(skill);
            if(BlackFoxSlashPresentation.visualAge(-1,speed)>=0)throw new AssertionError("premature cut");
            if(BlackFoxSlashPresentation.visualAge(4,speed)!=4)throw new AssertionError("sweep slowed");
            if(BlackFoxSlashPresentation.visualAge(18,speed)>=BlackFoxSlashPresentation.LIFETIME)
                throw new AssertionError("tail still too short");
            if(BlackFoxSlashPresentation.visualAge(24,speed)<BlackFoxSlashPresentation.LIFETIME)
                throw new AssertionError("tail unbounded");
        }
        if(BlackFoxSlashPresentation.roll(BlackFoxFight.Skill.CROSS_CUT,14)
                ==BlackFoxSlashPresentation.roll(BlackFoxFight.Skill.CROSS_CUT,44))
            throw new AssertionError("lost second cut direction");
        System.out.println("BLACK_FOX_SLASH_PRESENTATION_PASS: accelerating local melee sweep, t1/t2 erasure, shared hit cone, bounded heavy tails and distinct cross-cut rolls");
    }
}

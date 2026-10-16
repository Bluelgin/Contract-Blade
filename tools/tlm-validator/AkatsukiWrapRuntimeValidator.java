import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.json.JsonAnimationUtils;

/** Validate the native default+override animation result against the new geometry. */
public class AkatsukiWrapRuntimeValidator {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        var geo=JsonParser.parseString(Files.readString(root.resolve("art/akatsuki/crimson-wrap/akatsuki-crimson-wrap.geo.json"))).getAsJsonObject();
        Set<String> bones=new HashSet<>();
        for(var bone:geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))bones.add(bone.getAsJsonObject().get("name").getAsString());
        var animations=JsonParser.parseString(Files.readString(root.resolve("art/akatsuki/tlm-adaptation/effective-adapted.animation.json"))).getAsJsonObject();
        var parser=new MaidTailValidator.StrictParser(); int clips=0,tracks=0;
        for(var entry:JsonAnimationUtils.getAnimations(animations)) {
            var animation=JsonAnimationUtils.deserializeJsonToAnimation(entry,parser);clips++;
            for(var track:animation.boneAnimations){tracks++;if(!bones.contains(track.boneName))throw new AssertionError("Missing bone: "+track.boneName);}
        }
        if(clips!=185||bones.size()!=179)throw new AssertionError("Unexpected motion or skeleton count");
        System.out.println("AKATSUKI_WRAP_NATIVE_PASS: "+clips+" clips, "+tracks+" tracks, "+parser.expressions+" expressions, "+bones.size()+" bones");
    }
}

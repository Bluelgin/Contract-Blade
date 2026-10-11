import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.MolangParser;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.value.IValue;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.json.JsonAnimationUtils;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.YSMBinding;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.CtrlBinding;

// Syntax validation with the installed TLM shared Molang implementation, not a YSM visual test.
public class TailAnimationValidator {
    static class StrictParser extends MolangParser {
        int expressions;
        StrictParser(){super(Map.of("ysm",YSMBinding.INSTANCE,"ctrl",CtrlBinding.INSTANCE));}
        @Override public IValue parseExpression(String expression){
            expressions++;
            // TLM has no player walk/run controller bindings; these are documented YSM fields.
            // Substitute only in this syntax check, never in the generated model animation.
            try{return parseExpressionUnsafe(expression.replace("ctrl.walk","1").replace("ctrl.run","0"));}
            catch(Exception e){throw new IllegalArgumentException("Invalid Molang: "+expression,e);}
        }
    }
    public static void main(String[] args)throws Exception{
        Path base=Path.of(args[0]);JsonArray results=new JsonArray();
        for(String id:List.of("contract_akatsuki","contract_white_fox","contract_black_fox")){
            Path dir=base.resolve("models").resolve(id);
            var geometry=JsonParser.parseString(Files.readString(dir.resolve("models/main.json"))).getAsJsonObject();
            Set<String> names=new HashSet<>();
            for(var bone:geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))names.add(bone.getAsJsonObject().get("name").getAsString());
            var all=JsonParser.parseString(Files.readString(dir.resolve("animations/main.animation.json"))).getAsJsonObject().getAsJsonObject("animations");
            JsonObject subset=new JsonObject();for(String key:List.of("fly","pre_parallel3"))subset.add(key,all.get(key));
            JsonObject input=new JsonObject();input.add("animations",subset);
            var parser=new StrictParser();int tracks=0;
            for(var entry:JsonAnimationUtils.getAnimations(input)){
                var clip=JsonAnimationUtils.deserializeJsonToAnimation(entry,parser);
                for(var track:clip.boneAnimations){tracks++;if(!names.contains(track.boneName))throw new AssertionError("Unresolved bone: "+track.boneName);}
            }
            JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("expressions",parser.expressions);row.addProperty("tracks",tracks);row.addProperty("strict_syntax_passed",true);results.add(row);
        }
        JsonObject report=new JsonObject();report.addProperty("runtime","Installed TLM 1.5.3 shared Molang syntax parser; ctrl.walk/run substituted by documented boolean stand-ins; not live YSM evaluation");report.add("models",results);
        Files.writeString(base.resolve("tail-native-parser-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println(report);
    }
}

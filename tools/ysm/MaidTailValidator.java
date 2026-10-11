import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.MolangParser;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.value.IValue;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.json.JsonAnimationUtils;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.*;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;

// Strict native TLM validation: no replacement of player controller expressions.
public class MaidTailValidator {
    static class StrictParser extends MolangParser {
        int expressions;
        StrictParser(){super(Map.of("ysm",YSMBinding.INSTANCE,"tlm",TLMBinding.INSTANCE,"ctrl",CtrlBinding.INSTANCE));}
        @Override public IValue parseExpression(String text){
            expressions++;
            try{return parseExpressionUnsafe(text);}
            catch(Exception e){throw new IllegalArgumentException("Invalid maid Molang: "+text,e);}
        }
    }
    public static void main(String[] args)throws Exception{
        Path pack=Path.of(args[0]);JsonArray results=new JsonArray();
        var metadata=JsonParser.parseString(Files.readString(pack.resolve(args.length>2?args[2]:"assets/contract_fox/maid_model.json"))).getAsJsonObject();
        for(var item:metadata.getAsJsonArray("model_list")){
            var model=item.getAsJsonObject();String id=model.get("model_id").getAsString(),name=id.split(":")[1];
            Path geometryPath=pack.resolve("assets/"+id.split(":")[0]+"/models/entity/"+name+".json");
            var raw=Converter.fromJsonString(Files.readString(geometryPath));
            var geometry=GeoBuilder.getGeoBuilder().constructGeoModel(RawGeometryTree.parseHierarchy(raw));
            var animated=new AnimatedGeoModel(geometry);
            if(animated.head()==null||animated.leftHandBones().isEmpty()||animated.rightHandBones().isEmpty())throw new AssertionError("Equipment bones");
            Set<String> names=new HashSet<>();
            var geo=JsonParser.parseString(Files.readString(geometryPath)).getAsJsonObject();
            for(var bone:geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones"))names.add(bone.getAsJsonObject().get("name").getAsString());
            var parser=new StrictParser();int clips=0,tracks=0;
            Map<String,Set<String>> effectiveTracks=new LinkedHashMap<>();
            for(var location:model.getAsJsonArray("animation")){
                Path file=pack.resolve("assets/"+location.getAsString().replace(':','/'));
                var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                for(var entry:JsonAnimationUtils.getAnimations(json)){
                    var clip=JsonAnimationUtils.deserializeJsonToAnimation(entry,parser);clips++;
                    Set<String> clipTracks=new HashSet<>();
                    for(var track:clip.boneAnimations){tracks++;clipTracks.add(track.boneName);}
                    effectiveTracks.put(entry.getKey(),clipTracks); // Native loader: later files replace same-name clips.
                }
            }
            for(var clip:effectiveTracks.entrySet())for(var bone:clip.getValue())if(!names.contains(bone))throw new AssertionError("Missing effective bone "+bone+" in "+clip.getKey());
            JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("clips",clips);row.addProperty("effective_clips",effectiveTracks.size());row.addProperty("tracks",tracks);row.addProperty("expressions",parser.expressions);row.addProperty("native_geometry_and_strict_parser_passed",true);results.add(row);
        }
        JsonObject report=new JsonObject();report.addProperty("runtime","TLM 1.5.3 geometry builder and strict Molang parser; no substitutions");report.addProperty("in_game_visual_verification",false);report.add("models",results);
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println(report);
    }
}

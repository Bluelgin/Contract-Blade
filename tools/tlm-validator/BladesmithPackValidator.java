import java.nio.file.*;
import java.io.*;
import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.CustomModelPack;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.MolangParser;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.value.IValue;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.json.JsonAnimationUtils;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.YSMBinding;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.TLMBinding;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.CtrlBinding;

public class BladesmithPackValidator {
    static class StrictParser extends MolangParser {
        int expressions;
        StrictParser() { super(Map.of("ysm",YSMBinding.INSTANCE,"tlm",TLMBinding.INSTANCE,"ctrl",CtrlBinding.INSTANCE)); }
        @Override public IValue parseExpression(String expression) {
            expressions++;
            try { return parseExpressionUnsafe(expression); }
            catch (Exception e) { throw new IllegalArgumentException("Invalid Molang: " + expression,e); }
        }
    }
    public static void main(String[] args) throws Exception {
        Path pack=Path.of(args[0]);
        Gson gson=new GsonBuilder().registerTypeAdapter(ResourceLocation.class,new ResourceLocation.Serializer()).create();
        CustomModelPack<MaidModelInfo> metadata=gson.fromJson(Files.readString(pack.resolve("assets/contract_bladesmith/maid_model.json")),new TypeToken<CustomModelPack<MaidModelInfo>>(){}.getType());
        metadata.decorate("contract_bladesmith");
        if(metadata.getModelList().size()!=1)throw new IllegalStateException("Expected one registered model");
        for(var model:metadata.getModelList()) {
            if(!model.isGeckoModel())throw new IllegalStateException("Missing Gecko flag");
            var paths=new ArrayList<ResourceLocation>(model.getAnimation());paths.add(model.getModel());paths.add(model.getTexture());
            for(var resource:paths)if(!Files.isRegularFile(pack.resolve("assets/"+resource.getNamespace()+"/"+resource.getPath())))throw new IllegalStateException("Unresolved decorated resource: "+resource);
        }
        JsonArray rows=new JsonArray();
        for (String kind:List.of("travelling_bladesmith")) {
            Path domain=pack.resolve("assets/contract_bladesmith");
            var raw=Converter.fromJsonString(Files.readString(domain.resolve("models/entity/"+kind+".json")));
            var hierarchy=RawGeometryTree.parseHierarchy(raw);
            var geometry=GeoBuilder.getGeoBuilder().constructGeoModel(hierarchy);
            var animated=new AnimatedGeoModel(geometry);
            if (animated.head()==null || animated.leftHandBones().isEmpty() || animated.rightHandBones().isEmpty() || animated.leftWaistBones().isEmpty() || animated.rightWaistBones().isEmpty()) throw new IllegalStateException("Missing native maid equipment bones");
            StrictParser parser=new StrictParser();
            int animations=0;
            for (String label:List.of("main","tac","iss","im","special")) {
                var data=JsonParser.parseString(Files.readString(domain.resolve("animation/"+kind+"."+label+".animation.json"))).getAsJsonObject();
                for (var entry:JsonAnimationUtils.getAnimations(data)) {
                    JsonAnimationUtils.deserializeJsonToAnimation(entry,parser);
                    animations++;
                }
            }
            JsonObject row=new JsonObject();row.addProperty("model",kind);row.addProperty("animations_parsed",animations);row.addProperty("expressions_parsed",parser.expressions);row.addProperty("geometry_builder","passed");row.addProperty("head_hand_and_waist_locators","passed");row.addProperty("animation_deserialization","passed");rows.add(row);
            System.out.println("TLM 1.5.3 runtime parser passed: "+kind+", "+animations+" clips, "+parser.expressions+" expressions");
        }
        JsonObject result=new JsonObject();result.addProperty("runtime","Touhou Little Maid 1.5.3 actual model metadata decoration, geometry builder and animation deserializer");result.addProperty("model_pack_metadata_and_default_paths","passed");result.addProperty("minecraft_client_visual_test",false);result.add("models",rows);
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }
}

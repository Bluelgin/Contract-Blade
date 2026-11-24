package com.maidweapon.port.ability.progression;

import com.maidweapon.port.mastery.NativeMasteryData;
import com.maidweapon.port.storage.NativeChannelService;
import com.maidweapon.port.oath.NativeOathService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Read-only query. A data pack cannot grant origin, entitlement or execution authority. */
public final class ContractAbilityProgressionService {
    public enum Status { UNREGISTERED,DENIED,ENTITLED_WITHOUT_ROUTE,READY }
    public record Result(Status status,int masteryLevel,ContractAbilityFeatureSet capabilities){}
    public static Result query(ServerPlayer p,ItemStack stack,ResourceLocation id){
        var registration=ContractAbilityRegistry.registration(id);
        if(registration==null)return new Result(Status.UNREGISTERED,0,ContractAbilityFeatureSet.EMPTY);
        if(p==null||p.getServer()==null||!p.getServer().isSameThread()||!p.isAlive()||p.isSpectator()
            ||(p.getMainHandItem()!=stack&&p.getOffhandItem()!=stack))return denied();
        try{
            var ref=NativeChannelService.selected(p.getServer(),p.getUUID(),stack);
            if(ref==null){
                var live=NativeChannelService.selectedLive(p.getServer(),p.getUUID(),stack);
                ref=NativeChannelService.displayReference(stack);
                if(live==null||ref==null||!NativeOathService.burning(p.getServer(),p.getUUID(),ref.maid(),ref.binding())
                    ||!NativeOathService.active(p))return denied();
            }
            if(!registration.provider().entitled(p,stack))return denied();
            int level=NativeMasteryData.get(p.getServer()).read(p.getUUID()).level();
            var definition=ContractAbilityRegistry.definition(id);
            return definition==null?new Result(Status.ENTITLED_WITHOUT_ROUTE,level,ContractAbilityFeatureSet.EMPTY)
                :new Result(Status.READY,level,definition.resolve(level));
        }catch(RuntimeException rejected){return denied();}
    }
    private static Result denied(){return new Result(Status.DENIED,0,ContractAbilityFeatureSet.EMPTY);}
    private ContractAbilityProgressionService(){}
}

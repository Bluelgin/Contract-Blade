package com.maidweapon.port.ability.progression;

import com.maidweapon.port.storage.NativeChannelService;
import com.maidweapon.port.init.PortDataTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Original route definitions are available, but only server-registered acquisition
 * may grant a route. No production fox or red origin is registered by this probe.
 */
public final class NativeGrowthBootstrap {
    public static boolean selectedOrigin(ServerPlayer p,ItemStack stack,String id){
        var channels=stack.get(PortDataTypes.CONTRACT_CHANNELS.get());
        return channels!=null&&channels.selectedIntrinsic().filter(r->r.toString().equals(id)).isPresent();
    }
    public static void register(){
        // Deliberately deny production routes until their actual victory/original-home/awakening
        // providers are migrated. Datapacks cannot turn these into authorization.
        ContractAbilityRegistry.register(WhiteFoxBlessingGrowth.ID,(p,s)->false,WhiteFoxBlessingGrowth.FEATURES,
            WhiteFoxBlessingGrowth.PARAMETERS,WhiteFoxBlessingGrowth.fallback());
        ContractAbilityRegistry.register(BlackFoxSwordCoreGrowth.ABILITY,(p,s)->false,BlackFoxSwordCoreGrowth.FEATURES,
            BlackFoxSwordCoreGrowth.PARAMETERS,BlackFoxSwordCoreGrowth.fallback());
        AkatsukiDomainGrowth.register((p,s)->false);
        var id=ResourceLocation.fromNamespaceAndPath("maid_weapon_port_probe","verification_growth");
        var feature=ResourceLocation.fromNamespaceAndPath("maid_weapon_port_probe","verification");
        ContractAbilityRegistry.register(id,(p,s)->selectedOrigin(p,s,"maid_weapon_port_probe:spirit"),java.util.Set.of(feature),
            java.util.Map.of(),new ContractAbilityDefinition(id,java.util.List.of(
                new ContractAbilityMilestone(2,java.util.Set.of(feature),java.util.Map.of()))));
    }
    private NativeGrowthBootstrap(){}
}

package com.maidweapon.port.ability;

import com.maidweapon.port.domain.ability.AbilityEngine;
import com.maidweapon.port.domain.resonance.ResonanceRules.Payment;
import com.maidweapon.port.storage.NativeChannelService;
import com.maidweapon.port.resonance.NativeResonanceService;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/** Server-only adapter; no client-selectable costs or arbitrary executable callbacks over the wire.
 * Probe registry contains only a no-op test skill. Production skills require vetted definitions.
 */
public final class NativeAbilityService {
    public static final String PROBE="maid_weapon_port_probe:verification";
    private static final Map<MinecraftServer,AbilityEngine> ENGINES=new WeakHashMap<>();
    private static AbilityEngine engine(MinecraftServer server) {
        return ENGINES.computeIfAbsent(server, ignored->{
            var e=new AbilityEngine(); e.register(new AbilityEngine.Definition(PROBE,25,20)); return e;
        });
    }
    public static AbilityEngine.Result executeProbe(MinecraftServer server,UUID owner,ItemStack carrier) {
        if(!server.isSameThread() || owner==null || carrier==null) return AbilityEngine.Result.UNAUTHORIZED;
        var ref=NativeChannelService.selected(server,owner,carrier);
        if(ref==null || !owner.equals(ref.owner())) return AbilityEngine.Result.UNAUTHORIZED;
        var e=engine(server);
        long now=server.overworld().getGameTime();
        e.prune(now);
        BooleanSupplier authorized=()->ref.equals(NativeChannelService.selected(server,owner,carrier))
            && NativeResonanceService.currentStored(server,owner,carrier)>=0;
        var attempt=NativeResonanceService.newAttempt();
        return e.execute(PROBE,new AbilityEngine.Identity(owner,ref.maid(),ref.binding()),now,
            authorized,()->true,()->{var payment=NativeResonanceService.payStored(server,owner,carrier,e.definition(PROBE).cost(),attempt);return payment==Payment.PAID||payment==Payment.WAIVED;},
            ()->true);
    }
    private NativeAbilityService(){}
}

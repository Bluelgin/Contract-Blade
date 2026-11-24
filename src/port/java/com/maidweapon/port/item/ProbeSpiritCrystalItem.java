package com.maidweapon.port.item;
import com.maidweapon.port.tlm.NativeIntrinsicSpirits;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import java.util.UUID;
/** Dev-only real creation entry: crystal in one hand, carrier in other; consume on success. */
public final class ProbeSpiritCrystalItem extends Item{
    public ProbeSpiritCrystalItem(){super(new Properties().stacksTo(1));}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(stack);
        if(!(player instanceof ServerPlayer p))return InteractionResultHolder.fail(stack);
        var carrier=p.getItemInHand(hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND);
        if(!NativeIntrinsicSpirits.createForGrant(p,carrier,NativeIntrinsicSpirits.PROBE_ID,UUID.randomUUID()))
            return InteractionResultHolder.fail(stack);
        if(!p.getAbilities().instabuild)stack.shrink(1);
        p.displayClientMessage(Component.translatable("maid_weapon_port_probe.spirit.created"),true);
        return InteractionResultHolder.success(stack);
    }
}

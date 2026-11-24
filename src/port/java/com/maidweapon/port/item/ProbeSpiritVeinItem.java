package com.maidweapon.port.item;

import com.maidweapon.port.domain.resonance.SpiritVeinRules;
import com.maidweapon.port.storage.NativeSpiritVeinData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/** Stage-specific, probe-only upgrade; preserves the old sequential consumption rule. */
public final class ProbeSpiritVeinItem extends Item {
    private final int stage;
    public ProbeSpiritVeinItem(int stage){
        super(new Properties().rarity(Rarity.RARE));
        if(stage<1 || stage>SpiritVeinRules.maxStage())throw new IllegalArgumentException("Invalid stage");
        this.stage=stage;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(stack);
        if(!(player instanceof ServerPlayer serverPlayer)||serverPlayer.getServer()==null||stack.isEmpty())
            return InteractionResultHolder.fail(stack);
        try{
            var data=NativeSpiritVeinData.get(serverPlayer.getServer());
            var result=data.upgrade(player.getUUID(),stage);
            if(result!=SpiritVeinRules.Upgrade.SUCCESS){
                player.displayClientMessage(Component.literal("[Port Probe] Upgrade rejected: "+result),true);
                return InteractionResultHolder.fail(stack);
            }
            if(!player.getAbilities().instabuild)stack.shrink(1);
            player.displayClientMessage(Component.literal("[Port Probe] Spirit vein "+stage+" / x"+SpiritVeinRules.multiplier(stage)),true);
            return InteractionResultHolder.success(stack);
        }catch(RuntimeException rejected){
            return InteractionResultHolder.fail(stack);
        }
    }
}

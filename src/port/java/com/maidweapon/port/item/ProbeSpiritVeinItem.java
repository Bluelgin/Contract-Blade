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
                player.displayClientMessage(Component.translatable("maid_weapon.native.vein.rejected",result),true);
                return InteractionResultHolder.fail(stack);
            }
            if(!player.getAbilities().instabuild)stack.shrink(1);
            player.displayClientMessage(Component.translatable("maid_weapon.native.vein.upgraded",stage,com.maidweapon.port.config.NativeRules.vein(stage)),true);
            level.playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.PLAYERS,1,1+stage*.1f);
            serverPlayer.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,player.getX(),player.getY()+1,player.getZ(),24,.4,.5,.4,.05);
            return InteractionResultHolder.success(stack);
        }catch(RuntimeException rejected){
            return InteractionResultHolder.fail(stack);
        }
    }
}

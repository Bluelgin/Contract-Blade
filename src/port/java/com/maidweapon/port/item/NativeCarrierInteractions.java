package com.maidweapon.port.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.tlm.PortContractLifecycleTrial;
import com.maidweapon.port.storage.NativeChannelService;
import com.maidweapon.port.init.PortDataTypes;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Shared server-authoritative item interactions, used by formal swords and fixtures. */
public final class NativeCarrierInteractions {
    public static InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity entity, InteractionHand hand) {
        if (!(entity instanceof EntityMaid maid)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(player.level() instanceof ServerLevel level)) return InteractionResult.FAIL;
        if(!serverPlayer.isAlive()||serverPlayer.isSpectator())return InteractionResult.FAIL;
        boolean stored = PortContractLifecycleTrial.capture(level, maid,
            player.getUUID(), player.getScoreboardName(), stack);
        serverPlayer.sendSystemMessage(Component.translatable(stored
            ? "maid_weapon.native.carrier.captured"
            : "maid_weapon.native.carrier.capture_failed"));
        return stored ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** Sneak-right-click air converts the ordinary sealed carrier or cycles sealed channels. */
    public static InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(!player.isShiftKeyDown()) {
            if(level.isClientSide)return NativeChannelService.displayReference(stack)==null
                ?InteractionResultHolder.pass(stack):InteractionResultHolder.success(stack);
            if(!(player instanceof ServerPlayer sp)||!sp.isAlive()||sp.isSpectator())
                return InteractionResultHolder.fail(stack);
            var live=NativeChannelService.selectedLive(sp.getServer(),sp.getUUID(),stack);
            if(live==null){
                if(!(stack.getItem() instanceof NativeContractSwordItem)||NativeChannelService.selected(sp.getServer(),sp.getUUID(),stack)==null)return InteractionResultHolder.pass(stack);
                if(stack.has(PortDataTypes.CONTRACT_REFERENCE.get())&&!NativeChannelService.convertExternal(sp.getServer(),sp.getUUID(),stack))return InteractionResultHolder.fail(stack);
                var destination=sp.blockPosition().relative(sp.getDirection());
                var maid=PortContractLifecycleTrial.manifest(sp.serverLevel(),destination,sp.getUUID(),stack);
                sp.displayClientMessage(Component.translatable(maid!=null?"maid_weapon.native.carrier.manifested":"maid_weapon.native.carrier.manifest_failed"),true);
                return maid!=null?InteractionResultHolder.success(stack):InteractionResultHolder.fail(stack);
            }
            boolean recalled=PortContractLifecycleTrial.capture(sp.serverLevel(),live,sp.getUUID(),sp.getScoreboardName(),stack);
            sp.displayClientMessage(Component.translatable(recalled?"maid_weapon.native.carrier.recalled":"maid_weapon.native.carrier.recall_failed"),true);
            return recalled?InteractionResultHolder.success(stack):InteractionResultHolder.fail(stack);
        }
        if(level.isClientSide)return InteractionResultHolder.success(stack);
        if(!(player instanceof ServerPlayer sp)||sp.getServer()==null
            ||!NativeChannelService.cycle(sp.getServer(),player.getUUID(),stack))
            return InteractionResultHolder.fail(stack);
        var channels=stack.get(PortDataTypes.CONTRACT_CHANNELS.get());
        player.displayClientMessage(Component.translatable("maid_weapon.channel.selected",channels.selectedIntrinsic().map(Object::toString).orElse("external")),true);
        return InteractionResultHolder.success(stack);
    }

    public static InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(player.level() instanceof ServerLevel level)) return InteractionResult.FAIL;
        if(!serverPlayer.isAlive()||serverPlayer.isSpectator())return InteractionResult.FAIL;
        // Ordinary captures start with the original single reference. Promote it
        // through the authority-checked adapter before publishing a live lease,
        // so the same carrier retains its identity for HUD, oath and recall.
        var stack=context.getItemInHand();
        if(stack.has(PortDataTypes.CONTRACT_REFERENCE.get())
            &&!NativeChannelService.convertExternal(level.getServer(),player.getUUID(),stack))
            return InteractionResult.FAIL;
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        EntityMaid manifested = PortContractLifecycleTrial.manifest(
            level, pos, serverPlayer.getUUID(), context.getItemInHand());
        serverPlayer.sendSystemMessage(Component.translatable(manifested != null
            ? "maid_weapon.native.carrier.manifested"
            : "maid_weapon.native.carrier.manifest_failed"));
        return manifested != null ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
    private NativeCarrierInteractions() {}
}

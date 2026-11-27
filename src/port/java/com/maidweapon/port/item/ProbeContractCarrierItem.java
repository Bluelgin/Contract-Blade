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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Probe-namespace-only manually testable carrier. NOT a real Contract Blade
 * weapon and NOT compatible with historical player worlds.
 */
public final class ProbeContractCarrierItem extends Item {
    public ProbeContractCarrierItem(Properties props) { super(props); }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity entity, InteractionHand hand) {
        if (!(entity instanceof EntityMaid maid)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(player.level() instanceof ServerLevel level)) return InteractionResult.FAIL;
        if(!serverPlayer.isAlive()||serverPlayer.isSpectator())return InteractionResult.FAIL;
        boolean stored = PortContractLifecycleTrial.capture(level, maid,
            player.getUUID(), player.getScoreboardName(), stack);
        serverPlayer.sendSystemMessage(Component.translatable(stored
            ? "maid_weapon_port_probe.carrier.captured"
            : "maid_weapon_port_probe.carrier.capture_failed"));
        return stored ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** Sneak-right-click air converts the ordinary sealed carrier or cycles sealed channels. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(!player.isShiftKeyDown()) {
            if(level.isClientSide)return NativeChannelService.displayReference(stack)==null
                ?InteractionResultHolder.pass(stack):InteractionResultHolder.success(stack);
            if(!(player instanceof ServerPlayer sp)||!sp.isAlive()||sp.isSpectator())
                return InteractionResultHolder.fail(stack);
            var live=NativeChannelService.selectedLive(sp.getServer(),sp.getUUID(),stack);
            if(live==null)return InteractionResultHolder.pass(stack);
            boolean recalled=PortContractLifecycleTrial.capture(sp.serverLevel(),live,sp.getUUID(),sp.getScoreboardName(),stack);
            sp.displayClientMessage(Component.translatable(recalled?"maid_weapon_port_probe.carrier.recalled":"maid_weapon_port_probe.carrier.recall_failed"),true);
            return recalled?InteractionResultHolder.success(stack):InteractionResultHolder.fail(stack);
        }
        if(level.isClientSide)return InteractionResultHolder.success(stack);
        if(!(player instanceof ServerPlayer sp)||sp.getServer()==null
            ||!NativeChannelService.cycle(sp.getServer(),player.getUUID(),stack))
            return InteractionResultHolder.fail(stack);
        var channels=stack.get(PortDataTypes.CONTRACT_CHANNELS.get());
        player.displayClientMessage(Component.literal("[Port Probe] Channel: "+channels.selectedIntrinsic().map(Object::toString).orElse("external")),true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
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
            ? "maid_weapon_port_probe.carrier.manifested"
            : "maid_weapon_port_probe.carrier.manifest_failed"));
        return manifested != null ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
}

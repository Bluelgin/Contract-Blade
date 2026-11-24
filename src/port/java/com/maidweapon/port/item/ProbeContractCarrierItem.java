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
        boolean stored = PortContractLifecycleTrial.capture(level, maid,
            player.getUUID(), player.getScoreboardName(), stack);
        serverPlayer.sendSystemMessage(Component.literal(stored
            ? "[Port Probe] Snapshot stored and entity removed."
            : "[Port Probe] Capture rejected. Check owner, carrier and authority log."));
        return stored ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** Sneak-right-click air converts the ordinary sealed carrier or cycles sealed channels. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(!player.isShiftKeyDown())return InteractionResultHolder.pass(stack);
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
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        EntityMaid manifested = PortContractLifecycleTrial.manifest(
            level, pos, serverPlayer.getUUID(), context.getItemInHand());
        serverPlayer.sendSystemMessage(Component.literal(manifested != null
            ? "[Port Probe] Candidate entity published; carrier snapshot consumed."
            : "[Port Probe] Manifest rejected; no duplicate spawn authorized."));
        return manifested != null ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
}

package com.maidweapon.forge.item;

import com.maidweapon.forge.system.resonance.ResonanceProgressService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;
import javax.annotation.Nullable;

/** Stage-specific presentation; all upgrade rules and persistence belong to the progress service. */
public final class ResonanceUpgradeItem extends Item {
    private final int stage;
    public ResonanceUpgradeItem(int stage) { super(new Properties().rarity(Rarity.RARE)); this.stage = stage; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        var result = ResonanceProgressService.upgrade(player, stage);
        if (result != ResonanceProgressService.Upgrade.SUCCESS) {
            player.displayClientMessage(Component.translatable("maid_weapon.message.vein." + result.name().toLowerCase(java.util.Locale.ROOT)), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable("maid_weapon.message.vein.success", stage,
                String.format(java.util.Locale.ROOT, "%.2f", ResonanceProgressService.multiplier(player))), false);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1, 1 + stage * .1f);
        ((ServerLevel) level).sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1, player.getZ(), 24, .4, .5, .4, .05);
        return InteractionResultHolder.success(stack);
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("maid_weapon.tooltip.vein", stage));
        tooltip.add(Component.translatable("maid_weapon.tooltip.vein.global"));
    }
}

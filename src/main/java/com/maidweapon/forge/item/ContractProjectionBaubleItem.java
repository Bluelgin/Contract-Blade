package com.maidweapon.forge.item;

import com.maidweapon.forge.system.deployment.ContractProjectionMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Pure item presentation; native TLM registration and runtime effects live elsewhere. */
public final class ContractProjectionBaubleItem extends Item {
    private final ContractProjectionMode mode;
    private final boolean waiver;
    public boolean resonanceWaiver() { return waiver; }


    public ContractProjectionBaubleItem(ContractProjectionMode mode) {
        this(mode, false);
    }

    public ContractProjectionBaubleItem(ContractProjectionMode mode, boolean waiver) {
        super(new Properties().stacksTo(1));
        this.waiver = waiver;
        this.mode = mode;
    }

    public ContractProjectionMode mode() { return mode; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        if (waiver) tooltip.add(Component.translatable("maid_weapon.tooltip.endless_contract"));
        String description = "maid_weapon.tooltip.projection." + mode.name().toLowerCase(java.util.Locale.ROOT);
        tooltip.add(Component.translatable(description + ".flavor"));
        tooltip.add(Component.translatable(description));
        tooltip.add(Component.translatable("maid_weapon.tooltip.projection." + (mode.weapon() ? "auto_work" : "manual_work")));
        tooltip.add(Component.translatable("maid_weapon.tooltip.projection.altar"));
    }
}

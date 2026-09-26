package com.maidweapon.forge.item;

import com.maidweapon.forge.system.PetWeaponSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/** A dedicated carrier for one owned vanilla cat or wolf. */
public final class PetWeaponItem extends SwordItem {
    public enum Kind {
        DOG(EntityType.WOLF, "dog_blade"),
        CAT(EntityType.CAT, "cat_blade");

        private final EntityType<? extends TamableAnimal> type;
        private final String id;

        Kind(EntityType<? extends TamableAnimal> type, String id) {
            this.type = type;
            this.id = id;
        }

        public boolean matches(TamableAnimal animal) {
            return this == DOG ? animal instanceof Wolf : animal instanceof Cat;
        }

        public EntityType<? extends TamableAnimal> type() {
            return type;
        }

        public String id() {
            return id;
        }
    }

    private final Kind kind;

    public PetWeaponItem(Kind kind) {
        super(Tiers.IRON, kind == Kind.DOG ? 2 : 1,
                kind == Kind.DOG ? -2.5f : -1.9f,
                new Properties().stacksTo(1).durability(250));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return !PetWeaponSystem.hasBinding(stack);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount,
                                                    T entity, Consumer<T> onBroken) {
        // A bound carrier must never break while its pet is inside or outside.
        return PetWeaponSystem.hasBinding(stack) ? 0 : amount;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                   InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        return PetWeaponSystem.release(player, stack)
                ? InteractionResultHolder.success(stack)
                : InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (PetWeaponSystem.hasStoredPet(stack)) {
            tooltip.add(Component.translatable("maid_weapon.pet.stored",
                    PetWeaponSystem.petName(stack)));
        } else if (PetWeaponSystem.hasBinding(stack)) {
            tooltip.add(Component.translatable("maid_weapon.pet.deployed",
                    PetWeaponSystem.petName(stack)));
        } else {
            tooltip.add(Component.translatable("maid_weapon.pet.empty." + kind.id()));
        }
        tooltip.add(Component.translatable("maid_weapon.pet.hint"));
    }
}

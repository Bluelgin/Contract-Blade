package com.maidweapon.forge.system.danmaku;

import com.github.tartaricacid.touhoulittlemaid.entity.projectile.EntityDanmaku;
import com.maidweapon.forge.compat.tlm.TlmEntityAdapter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/** Native TLM bullet rendering and damage path, with added contract friendly-fire protection. */
public final class ContractDanmakuProjectile extends EntityDanmaku {
    public ContractDanmakuProjectile(Level world, LivingEntity owner) { super(world, owner); }
    @Override protected void onHitEntity(EntityHitResult hit) {
        var shooter = getOwner();
        if (!(shooter instanceof Player player) || !(hit.getEntity() instanceof LivingEntity victim)
                || !safeTarget(player, victim)) {
            discard();
            return;
        }
        super.onHitEntity(hit); // TLM's normal damage source and armor/iframe behavior.
    }
    public static boolean safeTarget(Player owner, LivingEntity target) {
        if (target == owner || target instanceof Player || TlmEntityAdapter.isMaidEntity(target)
                || owner.isAlliedTo(target) || target.isAlliedTo(owner)) return false;
        if (target instanceof TamableAnimal tame && owner.getUUID().equals(tame.getOwnerUUID())) return false;
        return target.isAlive() && !target.isInvulnerable();
    }
}

package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.world.entity.Entity;

/** Retains the emitting skill after the boss moves on, including delayed projectiles. */
public final class BlackFoxAttackOrigin {
    private static final String KEY = "MaidWeaponBlackFoxOrigin";
    public static void stamp(Entity entity, BlackFoxFight fight) { stamp(entity, fight.skill(), fight.sequence(), fight.parryRound()); }
    public static void stamp(Entity entity, BlackFoxFight.Skill skill, long sequence, int round) {
        var data = new net.minecraft.nbt.CompoundTag();
        data.putString("skill", skill.name()); data.putLong("sequence", sequence); data.putInt("round", round);
        entity.getPersistentData().put(KEY, data);
    }
    public static boolean credit(Entity entity, BlackFoxParryCredit policy) {
        if (!entity.getPersistentData().contains(KEY)) return false;
        var data = entity.getPersistentData().getCompound(KEY);
        try { return policy.accept(BlackFoxFight.Skill.valueOf(data.getString("skill")), data.getLong("sequence"), data.getInt("round")); }
        catch (IllegalArgumentException ignored) { return false; }
    }
    public static boolean sealPulse(Entity entity) {
        return entity.getPersistentData().getCompound(KEY).getString("skill").equals(BlackFoxFight.Skill.SEAL_ARRAY.name());
    }
    public static boolean domainCross(Entity entity) {
        return entity.getPersistentData().getCompound(KEY).getString("skill").equals(BlackFoxFight.Skill.DOMAIN_CROSS.name());
    }
    private BlackFoxAttackOrigin() { }
}

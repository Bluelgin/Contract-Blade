package com.maidweapon.forge.system.deployment;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Restrained owner-only dialogue; no healing, buffs or invented voice assets. */
public final class ContractCompanionDialogue {
    private static final String LAST = "MaidWeaponCompanionSpeech";
    public static void say(Player owner, Entity maid, String moment) {
        long now = owner.getServer().overworld().getGameTime();
        var tag = owner.getPersistentData();
        if (tag.contains(LAST) && now - tag.getLong(LAST) < 200) return;
        owner.displayClientMessage(Component.translatable("maid_weapon.companion.dialogue." + moment,
                maid.getName()), false);
        tag.putLong(LAST, now);
    }
    private ContractCompanionDialogue() { }
}

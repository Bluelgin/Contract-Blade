package com.maidweapon.forge.system.interior.home;

import net.minecraft.nbt.CompoundTag;

/** Only logical state, never a second copy of maid entity NBT. Owned by one plot. */
public final class ContractHomeOfflineState {
    public ContractHomeClock.Mode mode = ContractHomeClock.Mode.MINECRAFT_TIME;
    public String zone = "UTC";
    public boolean guideReceived;
    public long lastSimulatedAt;
    public long activityStartedAt;
    public long slot = Long.MIN_VALUE;
    public long seed;
    public String maidId = "";
    public ContractHomeActivity activity = ContractHomeActivity.IDLE;
    public String target = "";

    public static ContractHomeOfflineState load(CompoundTag tag) {
        var state = new ContractHomeOfflineState();
        try { state.mode = ContractHomeClock.Mode.valueOf(tag.getString("ClockMode")); }
        catch (IllegalArgumentException ignored) { /* Old saves use Minecraft time. */ }
        state.zone = ContractHomeClock.zone(tag.getString("Zone")).getId();
        state.guideReceived = tag.getBoolean("GuideReceived");
        state.lastSimulatedAt = tag.getLong("LastSimulatedAt");
        state.activityStartedAt = tag.getLong("ActivityStartedAt");
        state.slot = tag.contains("Slot") ? tag.getLong("Slot") : Long.MIN_VALUE;
        state.seed = tag.getLong("Seed");
        state.maidId = tag.getString("MaidId");
        try { state.activity = ContractHomeActivity.valueOf(tag.getString("Activity")); }
        catch (IllegalArgumentException ignored) { /* Safe migration. */ }
        state.target = tag.getString("Target");
        return state;
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("ClockMode", mode.name());
        tag.putString("Zone", zone);
        tag.putBoolean("GuideReceived", guideReceived);
        tag.putLong("LastSimulatedAt", lastSimulatedAt);
        tag.putLong("ActivityStartedAt", activityStartedAt);
        tag.putLong("Slot", slot);
        tag.putLong("Seed", seed);
        tag.putString("MaidId", maidId);
        tag.putString("Activity", activity.name());
        tag.putString("Target", target);
        return tag;
    }
}

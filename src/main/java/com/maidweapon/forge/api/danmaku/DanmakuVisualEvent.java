package com.maidweapon.forge.api.danmaku;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Server facts only. No damage, authority or client requests travel in this message. */
public record DanmakuVisualEvent(Kind kind, UUID owner, UUID subject, ResourceLocation dimension,
        long tick, int form, int phase, int entityId, Vec3 position, float yaw) {
    public enum Kind { WAVE, PROJECTILE, HIT, GUARD_START, GUARD_STOP, INTERCEPT, CANCEL }
    public void encode(FriendlyByteBuf b) {
        b.writeEnum(kind); b.writeUUID(owner); b.writeUUID(subject); b.writeResourceLocation(dimension);
        b.writeLong(tick); b.writeVarInt(form); b.writeVarInt(phase); b.writeVarInt(entityId);
        b.writeDouble(position.x); b.writeDouble(position.y); b.writeDouble(position.z); b.writeFloat(yaw);
    }
    public static DanmakuVisualEvent decode(FriendlyByteBuf b) {
        return new DanmakuVisualEvent(b.readEnum(Kind.class), b.readUUID(), b.readUUID(),
                b.readResourceLocation(), b.readLong(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat());
    }
    public boolean valid(ResourceLocation world, long now) {
        return dimension.equals(world) && tick <= now + 80 && tick >= now - 5
                && form >= 0 && form < 4 && phase >= 0 && phase < 4
                && Double.isFinite(position.x) && Double.isFinite(position.y)
                && Double.isFinite(position.z) && Float.isFinite(yaw);
    }
}

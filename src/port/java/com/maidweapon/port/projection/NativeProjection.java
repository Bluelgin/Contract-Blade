package com.maidweapon.port.projection;

import com.maidweapon.port.init.PortDataTypes;
import net.minecraft.world.item.ItemStack;

/** A projection is equipment presentation, never a transferable contract carrier. */
public final class NativeProjection {
    private static volatile boolean ready;
    public static void setup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) { ready = true; }
    public enum Mode {
        NONE, WEAPON, ARMOR, COMBINED;
        public boolean weapon() { return this == WEAPON || this == COMBINED; }
        public boolean armor() { return this == ARMOR || this == COMBINED; }
    }
    public static boolean isCopy(ItemStack stack) {
        return ready && stack != null && !stack.isEmpty() && PortDataTypes.PROJECTION.isBound() && Boolean.TRUE.equals(stack.get(PortDataTypes.PROJECTION.get()));
    }
    public static ItemStack copy(ItemStack source) {
        if (source.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = source.copyWithCount(1);
        copy.remove(PortDataTypes.CONTRACT_REFERENCE.get());
        copy.remove(PortDataTypes.CONTRACT_CHANNELS.get());
        copy.set(PortDataTypes.PROJECTION.get(), true);
        return copy;
    }
    private NativeProjection() { }
}

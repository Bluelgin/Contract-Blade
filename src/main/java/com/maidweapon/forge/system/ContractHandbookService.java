package com.maidweapon.forge.system;

import com.maidweapon.common.MaidWeaponConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Craft-earned tutorial receipt, independent of the maid's personal interior letter. */
@Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID)
public final class ContractHandbookService {
    private static final String RECEIVED = "ContractBladeHandbookReceived";
    private static final String PENDING = "ContractBladeHandbookPending";
    private static final ResourceLocation BOOK = ResourceLocation.parse("maid_weapon:contract_fragments");
    private static final ResourceLocation ALTAR_CRAFT = ResourceLocation.parse("maid_weapon:tutorial_altar_crafted");

    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isHandbook(event.getCrafting())) {
            receipt(player).putBoolean(RECEIVED, true);
            receipt(player).remove(PENDING);
            return;
        }
        var itemId = ForgeRegistries.ITEMS.getKey(event.getCrafting().getItem());
        if (!event.getCrafting().isEmpty() && itemId != null
                && MaidWeaponConstants.MOD_ID.equals(itemId.getNamespace())) earned(player);
    }

    @SubscribeEvent
    public static void altarCrafted(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ALTAR_CRAFT.equals(event.getAdvancement().getId())) earned(player);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 20 == 0 && receipt(player).getBoolean(PENDING)) deliver(player);
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        // Keep only our receipt across respawn; never replace another mod's persisted data.
        CompoundTag old = receipt(event.getOriginal());
        CompoundTag current = receipt(event.getEntity());
        if (old.getBoolean(RECEIVED)) current.putBoolean(RECEIVED, true);
        if (old.getBoolean(PENDING) && !current.getBoolean(RECEIVED)) current.putBoolean(PENDING, true);
    }

    private static void earned(ServerPlayer player) {
        if (receipt(player).getBoolean(RECEIVED)) return;
        // A manually crafted/existing copy counts as received; do not give another.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isHandbook(player.getInventory().getItem(slot))) {
                receipt(player).putBoolean(RECEIVED, true);
                receipt(player).remove(PENDING);
                return;
            }
        }
        // Patchouli is optional. Never create an unregistered or unusable guide item.
        if (createBook().isEmpty()) return;
        receipt(player).putBoolean(PENDING, true);
        deliver(player);
    }

    private static void deliver(ServerPlayer player) {
        CompoundTag state = receipt(player);
        if (state.getBoolean(RECEIVED)) { state.remove(PENDING); return; }
        ItemStack book = createBook();
        if (book.isEmpty() || !player.getInventory().add(book)) return;
        state.putBoolean(RECEIVED, true);
        state.remove(PENDING);
        player.displayClientMessage(Component.translatable("maid_weapon.message.handbook_received"), false);
    }

    private static ItemStack createBook() {
        var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("patchouli:guide_book"));
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        ItemStack book = new ItemStack(item);
        book.getOrCreateTag().putString("patchouli:book", BOOK.toString());
        return book;
    }

    private static boolean isHandbook(ItemStack stack) {
        return !stack.isEmpty() && stack.getTag() != null
                && BOOK.toString().equals(stack.getTag().getString("patchouli:book"))
                && ResourceLocation.parse("patchouli:guide_book").equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    private static CompoundTag receipt(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND))
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private ContractHandbookService() { }
}

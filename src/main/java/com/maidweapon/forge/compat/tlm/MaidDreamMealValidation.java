package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.system.deployment.ContractMaidRuntimeService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Opt-in real-addon feeding, record, wish and contract serialization regression. */
public final class MaidDreamMealValidation {
    public static void run(Level level, Player player) throws ReflectiveOperationException {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("solmaiddream")) return;
        Class<?> maidType = TlmEntityAdapter.maidClass();
        Class<?> records = Class.forName("com.mastermarisa.solmaiddream.data.FoodRecord");
        Class<?> wishes = Class.forName("com.mastermarisa.solmaiddream.data.MaidWish");
        Class<?> keyType = Class.forName("com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey");
        Object wishKey = Class.forName("com.mastermarisa.solmaiddream.init.InitTaskDataKeys").getField("MAID_WISH").get(null);
        var setData = maidType.getMethod("setAndSyncData", keyType, Object.class);
        ItemStack held = player.getMainHandItem().copy();
        ItemStack previous = player.getInventory().getItem(1).copy();
        try {
            for (boolean home : new boolean[]{false, true}) {
                Mob maid = (Mob) maidType.getConstructor(Level.class).newInstance(level);
                TlmEntityAdapter.tame(maid, player);
                maid.moveTo(4.5, 100, 4.5, 0, 0);
                maid.setNoAi(true);
                maidType.getMethod("setHomeModeEnable", boolean.class).invoke(maid, home);
                maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                check(level.addFreshEntity(maid), "real addon maid joined");
                java.util.UUID identity = maid.getUUID();
                ItemStack carrier = new ItemStack(ModItems.MAID_SWORD.get());
                Object wish = wishes.getMethod("getOrCreate", maidType).invoke(null, maid);
                var field = wishes.getDeclaredField("wishes");
                field.setAccessible(true);
                field.set(wish, new java.util.ArrayList<>(java.util.List.of("minecraft:cooked_beef")));
                setData.invoke(maid, wishKey, wish);
                int achieved = wishes.getField("totalAchieved").getInt(wish);
                // Respect the addon's default nutrition threshold; an apple is intentionally rejected.
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF, 3));
                var interaction = new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(
                        player, InteractionHand.MAIN_HAND, maid);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(interaction);
                check(interaction.isCanceled() && maid.isUsingItem(), "addon starts feeding, home=" + home);
                check(player.getMainHandItem().getCount() == 2, "addon consumes exactly one supplied food");
                for (int tick = 0; tick < 40; tick++) {
                    ContractMaidRuntimeService.maintain(player, carrier, maid);
                    maid.tick();
                }
                Object record = records.getMethod("getOrCreate", maidType).invoke(null, maid);
                check((boolean) records.getMethod("isFoodEaten", ItemStack.class).invoke(record, new ItemStack(Items.COOKED_BEEF)),
                        "real addon records completed food, home=" + home);
                Object completed = wishes.getMethod("getOrCreate", maidType).invoke(null, maid);
                check(wishes.getField("totalAchieved").getInt(completed) == achieved + 1,
                        "real addon completes exactly one wish, home=" + home);
                player.getInventory().setItem(1, carrier);
                check(ContractLifecycleService.capture(player, maid, carrier, false), "addon progress captured");
                check(ContractLifecycleService.manifest(player, carrier, false), "addon progress manifested");
                Mob restored = (Mob) ((net.minecraft.server.level.ServerLevel) level).getEntity(identity);
                check(restored != null, "same identity restored");
                Object savedRecord = records.getMethod("getOrCreate", maidType).invoke(null, restored);
                Object savedWish = wishes.getMethod("getOrCreate", maidType).invoke(null, restored);
                check((boolean) records.getMethod("isFoodEaten", ItemStack.class).invoke(savedRecord, new ItemStack(Items.COOKED_BEEF))
                        && wishes.getField("totalAchieved").getInt(savedWish) == achieved + 1,
                        "real addon record and completed wish survive contract round trip");
                restored.discard();
            }
            com.mojang.logging.LogUtils.getLogger().info("[MaidWeapon] MAID_DREAM_MEAL_VALIDATION_PASS");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            player.getInventory().setItem(1, previous);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Maid Dream integration: " + message);
    }

    private MaidDreamMealValidation() { }
}

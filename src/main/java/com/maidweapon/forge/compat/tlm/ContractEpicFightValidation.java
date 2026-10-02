package com.maidweapon.forge.compat.tlm;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.compat.EpicFightCompat;
import com.maidweapon.forge.compat.TripleMagicCompat;
import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.system.MaidCareTaskSystem;
import com.maidweapon.forge.system.deployment.ContractCombatTaskRouter;
import com.maidweapon.forge.system.deployment.ContractMaidRuntimeService;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in isolated development-server fixture; never runs during ordinary play. */
@Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID)
public final class ContractEpicFightValidation {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.epicfight.validation")) return;
        try {
            run(event);
            LogUtils.getLogger().info("[MaidWeapon] EPIC_FIGHT_VALIDATION_PASS");
        } catch (Throwable error) {
            LogUtils.getLogger().error("[MaidWeapon] EPIC_FIGHT_VALIDATION_FAIL", error);
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void run(ServerStartedEvent event) throws ReflectiveOperationException {
        check(EpicFightCompat.isLoaded(), "both optional providers loaded");
        var level = event.getServer().overworld();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString(
                        "a53d8a3d-7f94-438f-82df-60713932b996"), "EpicFightFixture"));
        Mob maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(level);
        TlmEntityAdapter.tame(maid, player);
        maid.moveTo(0.5, 100, 0.5, 0, 0);
        check(level.addFreshEntity(maid), "real maid patch attached");
        ItemStack original = new ItemStack(Items.STICK);
        maid.setItemSlot(EquipmentSlot.MAINHAND, original.copy());
        var baubles = (net.minecraftforge.items.IItemHandlerModifiable)
                maid.getClass().getMethod("getMaidBauble").invoke(maid);
        baubles.setStackInSlot(0, new ItemStack(ModItems.RESONANCE_SWORD_TASSEL.get()));
        try {
            for (var item : new net.minecraft.world.item.Item[]{Items.IRON_SWORD,
                    ModItems.MAID_SWORD.get(), ModItems.MAID_FAST.get(), ModItems.MAID_HEAVY.get()}) {
                ItemStack weapon = new ItemStack(item);
                weapon.getOrCreateTag().putString("FixturePreserved", "contract data");
                TlmEntityAdapter.switchTask(maid, "touhou_little_maid:idle");
                MaidCareTaskSystem.rememberOriginalTask(weapon, maid);
                TripleMagicCompat.equipPhantoms(player, maid, weapon);
                check(TripleMagicCompat.isPhantom(maid.getMainHandItem()), "existing equipment projection reused");
                check(EpicFightCompat.isMeleeWeapon(weapon), "melee capability: " + item);
                check(EpicFightCompat.usesMaidFightTask(weapon, maid), "actual attack motions: " + item);
                ContractCombatTaskRouter.configure(player, weapon, maid);
                check(EpicFightCompat.FIGHT_TASK.equals(TlmEntityAdapter.taskId(maid)), "auto fight task: " + item);
                ContractCombatTaskRouter.configure(player, weapon, maid);
                check(EpicFightCompat.FIGHT_TASK.equals(TlmEntityAdapter.taskId(maid)), "stable repeated routing");
                for (int tick = 0; tick < 200; tick++) {
                    player.tickCount = tick;
                    ContractMaidRuntimeService.maintain(player, weapon, maid);
                    if (!EpicFightCompat.FIGHT_TASK.equals(TlmEntityAdapter.taskId(maid))) {
                        throw new IllegalStateException("combat task oscillated on maintenance tick " + tick);
                    }
                }
                check(true, "200 maintenance ticks remain in Epic Fight");
                ItemStack deployedHand = maid.getMainHandItem().copy();
                maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.APPLE));
                ContractCombatTaskRouter.configure(player, weapon, maid);
                check(EpicFightCompat.FIGHT_TASK.equals(TlmEntityAdapter.taskId(maid)), "temporary food cannot reset combat mode");
                maid.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                ContractCombatTaskRouter.configure(player, weapon, maid);
                check(EpicFightCompat.FIGHT_TASK.equals(TlmEntityAdapter.taskId(maid)), "temporary empty hand cannot reset combat mode");
                maid.setItemSlot(EquipmentSlot.MAINHAND, deployedHand);
                ContractMaidRuntimeService.cleanupBeforeRecall(player, weapon, maid);
                check("touhou_little_maid:idle".equals(TlmEntityAdapter.taskId(maid)), "original task restored");
                check(ItemStack.isSameItemSameTags(original, maid.getMainHandItem()), "original equipment restored");
                check("contract data".equals(weapon.getTag().getString("FixturePreserved")), "source data preserved");
                check(!EpicFightCompat.usesMaidFightTask(weapon, maid), "recall clears the session selection");
            }
            check(!EpicFightCompat.isMeleeWeapon(ItemStack.EMPTY), "empty hand excluded");
            check(!EpicFightCompat.isMeleeWeapon(new ItemStack(Items.BOW)), "bow excluded");
            check(!EpicFightCompat.isMeleeWeapon(new ItemStack(Items.SHIELD)), "shield excluded");
            ItemStack unsupported = new ItemStack(Items.DIAMOND_PICKAXE);
            maid.setItemSlot(EquipmentSlot.MAINHAND, unsupported.copy());
            check(!EpicFightCompat.usesMaidFightTask(unsupported, maid), "unsupported tool excluded");
            ContractCombatTaskRouter.configure(player, unsupported, maid);
            check(MaidCareTaskSystem.ATTACK_TASK.equals(TlmEntityAdapter.taskId(maid)), "ordinary fallback");
        } finally {
            maid.discard();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        LogUtils.getLogger().info("[MaidWeapon] Epic Fight fixture: {}", message);
    }

    private ContractEpicFightValidation() { }
}

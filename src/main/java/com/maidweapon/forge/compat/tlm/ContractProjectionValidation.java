package com.maidweapon.forge.compat.tlm;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.compat.TripleMagicCompat;
import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.system.deployment.ContractCombatTaskRouter;
import com.maidweapon.forge.system.deployment.ContractMaidRuntimeService;
import com.maidweapon.forge.system.deployment.ContractProjectionMode;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;

/** Opt-in native bauble, projection and serialization regression fixture. */
@Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID)
public final class ContractProjectionValidation {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.projection.validation")) return;
        try {
            run(event);
            LogUtils.getLogger().info("[MaidWeapon] PROJECTION_VALIDATION_PASS");
        } catch (Throwable error) {
            LogUtils.getLogger().error("[MaidWeapon] PROJECTION_VALIDATION_FAIL", error);
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void run(ServerStartedEvent event) throws ReflectiveOperationException {
        var level = event.getServer().overworld();
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString(
                        "5141fcab-0b65-453d-8956-d875d0c9719b"), "ProjectionFixture"));
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(40);
        player.getAttribute(Attributes.ARMOR).setBaseValue(40);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        Mob maid = (Mob) TlmEntityAdapter.maidClass().getConstructor(Level.class).newInstance(level);
        TlmEntityAdapter.tame(maid, player);
        maid.moveTo(0.5, 100, 0.5, 0, 0);
        check(level.addFreshEntity(maid), "native maid spawned");
        ItemStack carrier = new ItemStack(ModItems.MAID_SWORD.get());
        ItemStack ownSword = new ItemStack(Items.IRON_SWORD);
        ItemStack ownHelmet = new ItemStack(Items.LEATHER_HELMET);
        maid.setItemSlot(EquipmentSlot.MAINHAND, ownSword.copy());
        maid.setItemSlot(EquipmentSlot.HEAD, ownHelmet.copy());
        double attack = maid.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
        double armor = maid.getAttribute(Attributes.ARMOR).getBaseValue();
        var baubles = (IItemHandlerModifiable) maid.getClass().getMethod("getMaidBauble").invoke(maid);
        var tassel = new ItemStack(ModItems.RESONANCE_SWORD_TASSEL.get());
        var ribbon = new ItemStack(ModItems.GUARDIAN_RIBBON.get());
        var knot = new ItemStack(ModItems.HEARTBOUND_KNOT.get());
        check(baubles.isItemValid(0, tassel) && baubles.isItemValid(0, ribbon), "both baubles accepted by native slots");
        check(baubles.isItemValid(0, knot), "combined knot accepted by native slots");
        validateAltarRecipes(level);
        try {
            TlmEntityAdapter.switchTask(maid, "touhou_little_maid:idle");
            for (int tick = 0; tick < 200; tick++) ContractMaidRuntimeService.maintain(player, carrier, maid);
            check("touhou_little_maid:idle".equals(TlmEntityAdapter.taskId(maid)), "no tassel preserves manual work");
            check(ItemStack.isSameItemSameTags(ownSword, maid.getMainHandItem()), "no bauble preserves weapon");
            check(ItemStack.isSameItemSameTags(ownHelmet, maid.getItemBySlot(EquipmentSlot.HEAD)), "no bauble preserves armor");
            TlmEntityAdapter.switchTask(maid, "touhou_little_maid:attack");
            ContractCombatTaskRouter.configure(player, carrier, maid);
            check("touhou_little_maid:attack".equals(TlmEntityAdapter.taskId(maid)), "router cannot override manual work");
            TlmEntityAdapter.switchTask(maid, "touhou_little_maid:idle");
            baubles.setStackInSlot(0, tassel.copy());
            ContractMaidRuntimeService.maintain(player, carrier, maid);
            check(TripleMagicCompat.isPhantom(maid.getMainHandItem()), "tassel enables weapon projection");
            check(!MaidWeaponItem.hasMaidData(maid.getMainHandItem()), "projection has no duplicate growth contract");
            check(ItemStack.isSameItemSameTags(ownHelmet, maid.getItemBySlot(EquipmentSlot.HEAD)), "tassel does not project armor");
            check(!"touhou_little_maid:idle".equals(TlmEntityAdapter.taskId(maid)), "tassel enables automatic work");
            baubles.setStackInSlot(1, ribbon.copy());
            check(TlmProjectionBaubles.mode(maid) == ContractProjectionMode.WEAPON, "projection channels never stack");
            baubles.setStackInSlot(0, ItemStack.EMPTY);
            ContractMaidRuntimeService.maintain(player, carrier, maid);
            check("touhou_little_maid:idle".equals(TlmEntityAdapter.taskId(maid)), "removing tassel restores borrowed work");
            check(ItemStack.isSameItemSameTags(ownSword, maid.getMainHandItem()), "removing tassel restores original weapon");
            check(TripleMagicCompat.isPhantom(maid.getItemBySlot(EquipmentSlot.HEAD)), "ribbon projects armor only");
            check(maid.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() == attack
                    && maid.getAttribute(Attributes.ARMOR).getBaseValue() == armor, "owner stats are never copied");
            TlmEntityAdapter.switchTask(maid, "touhou_little_maid:attack");
            for (int tick = 0; tick < 200; tick++) ContractMaidRuntimeService.maintain(player, carrier, maid);
            check("touhou_little_maid:attack".equals(TlmEntityAdapter.taskId(maid)), "manual changes remain authoritative");
            baubles.setStackInSlot(1, ItemStack.EMPTY);
            ContractMaidRuntimeService.maintain(player, carrier, maid);
            check(ItemStack.isSameItemSameTags(ownHelmet, maid.getItemBySlot(EquipmentSlot.HEAD)), "removing ribbon restores original armor");

            baubles.setStackInSlot(0, tassel.copy());
            ContractMaidRuntimeService.maintain(player, carrier, maid);
            ItemStack replacement = new ItemStack(Items.DIAMOND_SWORD);
            maid.setItemSlot(EquipmentSlot.MAINHAND, replacement.copy());
            baubles.setStackInSlot(0, ItemStack.EMPTY);
            ContractMaidRuntimeService.maintain(player, carrier, maid);
            check(ItemStack.isSameItemSameTags(replacement, maid.getMainHandItem()), "manual replacement is not overwritten");
            var backpack = (IItemHandlerModifiable) maid.getClass().getMethod("getAvailableBackpackInv").invoke(maid);
            boolean returned = false;
            for (int slot = 0; slot < backpack.getSlots(); slot++) {
                if (ItemStack.isSameItemSameTags(ownSword, backpack.getStackInSlot(slot))) {
                    returned = true;
                    backpack.setStackInSlot(slot, ItemStack.EMPTY);
                    break;
                }
            }
            check(returned, "original weapon returned to backpack without loss");
            maid.setItemSlot(EquipmentSlot.MAINHAND, ownSword.copy());

            TlmEntityAdapter.switchTask(maid, "touhou_little_maid:idle");
            baubles.setStackInSlot(0, knot.copy());
            baubles.setStackInSlot(1, tassel.copy());
            for (int tick = 0; tick < 200; tick++) ContractMaidRuntimeService.maintain(player, carrier, maid);
            check(TlmProjectionBaubles.mode(maid) == ContractProjectionMode.COMBINED,
                    "combined knot takes one active slot without stacking");
            check(TripleMagicCompat.isPhantom(maid.getMainHandItem())
                    && TripleMagicCompat.isPhantom(maid.getItemBySlot(EquipmentSlot.HEAD)), "knot projects both channels");
            check(!"touhou_little_maid:idle".equals(TlmEntityAdapter.taskId(maid)), "knot enables automatic work");
            check(maid.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() == attack
                    && maid.getAttribute(Attributes.ARMOR).getBaseValue() == armor, "knot adds no copied base stats");
            baubles.setStackInSlot(0, ribbon.copy());
            baubles.setStackInSlot(1, knot.copy());
            ContractMaidRuntimeService.maintain(player, carrier, maid);
            check(TlmProjectionBaubles.mode(maid) == ContractProjectionMode.ARMOR
                    && "touhou_little_maid:idle".equals(TlmEntityAdapter.taskId(maid)),
                    "earlier single bauble overrides knot and restores manual work");
            check(ItemStack.isSameItemSameTags(ownSword, maid.getMainHandItem()), "knot to ribbon restores weapon only");
            baubles.setStackInSlot(0, ItemStack.EMPTY);
            baubles.setStackInSlot(1, ItemStack.EMPTY);
            ContractMaidRuntimeService.maintain(player, carrier, maid);
            check(ItemStack.isSameItemSameTags(ownSword, maid.getMainHandItem())
                    && ItemStack.isSameItemSameTags(ownHelmet, maid.getItemBySlot(EquipmentSlot.HEAD)),
                    "removing combined effects restores all original gear");

            // Migrate a live 1.0.7 projection without perpetuating its copied attributes.
            CompoundTag legacyGear = new CompoundTag();
            legacyGear.put("mainhand", ownSword.save(new CompoundTag()));
            maid.getPersistentData().put("MaidWeaponOriginalGear", legacyGear);
            maid.getPersistentData().putDouble("MaidWeaponOriginalAttack", attack);
            maid.getPersistentData().putDouble("MaidWeaponOriginalArmor", armor);
            maid.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20);
            maid.getAttribute(Attributes.ARMOR).setBaseValue(20);
            maid.setItemSlot(EquipmentSlot.MAINHAND, TripleMagicCompat.createProjection(carrier, player));
            ContractMaidRuntimeService.maintain(player, carrier, maid);
            check(ItemStack.isSameItemSameTags(ownSword, maid.getMainHandItem())
                    && maid.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() == attack
                    && maid.getAttribute(Attributes.ARMOR).getBaseValue() == armor, "legacy gear and stats safely restored");

            baubles.setStackInSlot(0, knot.copy());
            player.getInventory().setItem(0, carrier);
            java.util.UUID identity = maid.getUUID();
            check(ContractLifecycleService.capture(player, maid, carrier, false), "capture retains baubles");
            check(ContractLifecycleService.manifest(player, carrier, false), "manifest restores baubles");
            maid = (Mob) level.getEntity(identity);
            check(maid != null && TlmProjectionBaubles.mode(maid) == ContractProjectionMode.COMBINED,
                    "native bauble survives compressed contract round trip");
            ContractMaidRuntimeService.cleanupBeforeRecall(player, carrier, maid);
            check(ItemStack.isSameItemSameTags(ownSword, maid.getMainHandItem()), "recall retains original equipment");
            check(ItemStack.isSameItemSameTags(ownHelmet, maid.getItemBySlot(EquipmentSlot.HEAD)), "recall retains original armor");
        } finally {
            if (maid != null) maid.discard();
        }
    }

    private static void validateAltarRecipes(net.minecraft.server.level.ServerLevel level) {
        for (String name : new String[] {"resonance_sword_tassel", "guardian_ribbon", "heartbound_knot"}) {
            var id = new net.minecraft.resources.ResourceLocation(MaidWeaponConstants.MOD_ID, "altar/" + name);
            var loaded = level.getRecipeManager().byKey(id).orElseThrow();
            check(loaded instanceof com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe,
                    "native altar recipe loaded: " + name);
            var recipe = (com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe) loaded;
            var inventory = new com.github.tartaricacid.touhoulittlemaid.inventory.AltarRecipeInventory();
            for (int slot = 0; slot < 6; slot++) {
                ItemStack[] candidates = recipe.getIngredients().get(slot).getItems();
                check(candidates.length > 0, "registered altar ingredient: " + name + "/" + slot);
                inventory.setItem(5 - slot, candidates[0].copy());
            }
            check(recipe.matches(inventory, level), "altar ingredients match in any order: " + name);
            ItemStack result = recipe.assemble(inventory, level.registryAccess());
            check(result.getCount() == 1 && result.getItem() == net.minecraftforge.registries.ForgeRegistries.ITEMS
                    .getValue(new net.minecraft.resources.ResourceLocation(MaidWeaponConstants.MOD_ID, name)),
                    "altar produces the registered bauble: " + name);
            check(Math.abs(recipe.getPowerCost() - (name.equals("heartbound_knot") ? 0.3f : 0.15f)) < 0.001,
                    "native altar power cost: " + name);
            inventory.setItem(0, new ItemStack(Items.DIRT));
            check(!recipe.matches(inventory, level), "wrong ingredients rejected: " + name);
            var pos = new net.minecraft.core.BlockPos(8, 100, 8);
            recipe.spawnOutputEntity(level, pos, null);
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(2), entity -> entity.getItem().is(result.getItem()));
            check(drops.size() == 1 && drops.get(0).getItem().getCount() == 1, "altar output spawns correctly: " + name);
            drops.forEach(net.minecraft.world.entity.Entity::discard);
        }
    }

    private static void check(boolean value, String label) {
        if (!value) throw new IllegalStateException(label);
        LogUtils.getLogger().info("[MaidWeapon] Projection fixture: {}", label);
    }

    private ContractProjectionValidation() { }
}

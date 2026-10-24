package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.api.EmbeddedSpiritApi;
import com.maidweapon.forge.api.IntrinsicSpiritApi;
import com.maidweapon.forge.compat.fox.FoxModelPackBootstrap;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.deployment.ContractCompanionService;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Explicitly opted-in private-world fixture using the real optional provider and TLM entities. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class AkatsukiModelValidation {
    private static final String ID = PresetSpiritModels.AKATSUKI;
    private static final String MODEL = "touhou_little_maid:hakurei_reimu";

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.akatsuki.validation")) return;
        try {
            run(event.getServer());
            LogUtils.getLogger().info("AKATSUKI_MODEL_NATIVE_PASS");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("AKATSUKI_MODEL_NATIVE_FAIL", failure);
        } finally { event.getServer().halt(false); }
    }

    private static void run(net.minecraft.server.MinecraftServer server) throws Exception {
        check(PresetSpiritModels.modelFor(ID).isEmpty(), "awakening does not assign an unfinished model");
        check(!PresetSpiritModels.isAvailable(AkatsukiModelPackBootstrap.MODEL), "unfinished model is not bundled");

        ServerPlayer owner = FakePlayerFactory.get(server.overworld(), new GameProfile(
                java.util.UUID.fromString("e2364878-32b8-468c-a759-b0dc6db84a15"), "AkatsukiModelFixture"));
        owner.getInventory().clearContent(); owner.moveTo(0.5, 100, 0.5, 0, 0);
        var provider = Class.forName("dev.bladetetra.compat.ContractBladeCompat")
                .getMethod("syncAkatsuki", Player.class, ItemStack.class, boolean.class);
        Entity maid = null;
        ItemStack blade = new ItemStack(Items.IRON_SWORD);
        try {
            check((Boolean) provider.invoke(null, owner, blade, true), "real BladeTetra bridge creates and selects Akatsuki");
            check(MODEL.equals(model(blade)), "fresh awakened contract retains native maid appearance");
            String maidId = ContractCarrierData.getBoundMaidUUID(blade);
            String bindingId = ContractCarrierData.getBindingId(blade);
            owner.getInventory().selected = 0; owner.getInventory().setItem(0, blade);
            CompoundTag initialized = blade.getTag().copy();
            for (int tick = 0; tick < 100; tick++) check((Boolean) provider.invoke(null, owner, blade, true), "provider repeat sync succeeds");
            check(initialized.equals(blade.getTag()) && server.overworld().getEntity(java.util.UUID.fromString(maidId)) == null,
                    "100 provider syncs neither rewrite the complete contract nor auto-spawn a maid");
            ContractCompanionService.toggle(owner);
            maid = server.overworld().getEntity(java.util.UUID.fromString(maidId));
            check(maid != null && MODEL.equals(maid.getClass().getMethod("getModelId").invoke(maid)), "manual manifest uses native maid appearance");
            check(IntrinsicSpiritApi.setIntrinsicSpiritModel(owner, blade, ID, FoxModelPackBootstrap.WHITE_MODEL), "owner can change live skin");
            unmark(blade);
            check((Boolean) provider.invoke(null, owner, blade, true)
                            && FoxModelPackBootstrap.WHITE_MODEL.equals(maid.getClass().getMethod("getModelId").invoke(maid)),
                    "unmarked custom live skin is preserved during migration");
            // Deliberately selecting even the native default after migration is a player choice.
            maid.getClass().getMethod("setModelId", String.class).invoke(maid, "touhou_little_maid:hakurei_reimu");
            check((Boolean) provider.invoke(null, owner, blade, true)
                            && "touhou_little_maid:hakurei_reimu".equals(maid.getClass().getMethod("getModelId").invoke(maid)),
                    "completed one-time migration preserves subsequent player selection of the native default");
            check(InfusedMaidDeploymentSystem.forceRecall(owner, maidId, 0), "native recall retains spirit identity");
            maid = null;
            check(IntrinsicSpiritApi.setIntrinsicSpiritModel(owner, blade, ID, "touhou_little_maid:hakurei_reimu"), "fixture emulates saved legacy skin");
            unmark(blade);
            check((Boolean) provider.invoke(null, owner, blade, false), "provider can select dormant intrinsic channel");
            check(MODEL.equals(archivedModel(blade)), "dormant channel retains native appearance without manifesting");
            check((Boolean) provider.invoke(null, owner, blade, true)
                            && maidId.equals(ContractCarrierData.getBoundMaidUUID(blade))
                            && bindingId.equals(ContractCarrierData.getBindingId(blade)), "channel round trip retains original UUID and binding");
            check(IntrinsicSpiritApi.setIntrinsicSpiritModel(owner, blade, ID, FoxModelPackBootstrap.BLACK_MODEL), "stored owner skin can change");
            unmark(blade);
            check((Boolean) provider.invoke(null, owner, blade, true)
                            && FoxModelPackBootstrap.BLACK_MODEL.equals(model(blade)), "stored custom skin survives first migration");
            ItemStack reloaded = ItemStack.of(blade.save(new CompoundTag()));
            CompoundTag saved = reloaded.getTag().copy();
            check((Boolean) provider.invoke(null, owner, reloaded, true) && saved.equals(reloaded.getTag()), "save/load retains one-time migration marker and skin");

            var external = new ItemStack(Items.DIAMOND_SWORD);
            check(EmbeddedSpiritApi.bindPresetSpirit(owner, external, "fixture:external", "External"), "fixture creates independent external contract");
            CompoundTag externalBefore = com.maidweapon.forge.system.contract.ContractChannelStorage.captureContract(external);
            check((Boolean) provider.invoke(null, owner, external, true) && MODEL.equals(model(external)), "Akatsuki may coexist with an external weapon contract without selecting a custom model");
            check((Boolean) provider.invoke(null, owner, external, false)
                            && externalBefore.equals(com.maidweapon.forge.system.contract.ContractChannelStorage.captureContract(external)),
                    "external contract model and complete payload remain byte-identical after channel round trip");

            var legacy = new ItemStack(Items.GOLDEN_SWORD);
            check(EmbeddedSpiritApi.bindPresetSpirit(owner, legacy, ID, "赤月"), "legacy preset API still works");
            var data = MaidEntityDataCodec.read(legacy.getTag()); data.putString("ModelId", FoxModelPackBootstrap.WHITE_MODEL);
            MaidEntityDataCodec.write(legacy.getTag(), data);
            check((Boolean) provider.invoke(null, owner, legacy, true) && FoxModelPackBootstrap.WHITE_MODEL.equals(model(legacy)), "legacy embedded channel retains its existing custom appearance");
            check(PresetSpiritModels.modelFor("fixture:external").isEmpty(), "unrelated preset identities have no Akatsuki default");
        } finally {
            if (maid != null && !maid.isRemoved()) maid.discard();
            owner.getInventory().clearContent(); ContractCompanionService.disconnect(owner);
        }
    }

    private static void unmark(ItemStack blade) {
        blade.getTag().getCompound("MaidWeaponIntrinsicSpirits").getCompound(ID).remove("DefaultModelApplied");
    }
    private static String model(ItemStack blade) throws Exception { return MaidEntityDataCodec.read(blade.getTag()).getString("ModelId"); }
    private static String archivedModel(ItemStack blade) throws Exception {
        return MaidEntityDataCodec.read(blade.getTag().getCompound("MaidWeaponIntrinsicSpirits").getCompound(ID).getCompound("Contract")).getString("ModelId");
    }
    private static void check(boolean passed, String label) {
        if (!passed) throw new IllegalStateException(label);
        if (!label.equals("provider repeat sync succeeds")) LogUtils.getLogger().info("Akatsuki fixture: {}", label);
    }
    private AkatsukiModelValidation() { }
}

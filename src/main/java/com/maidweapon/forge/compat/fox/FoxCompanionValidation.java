package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.forge.api.IntrinsicSpiritApi;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.menu.MaidInjectorMenu;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.maidweapon.forge.system.deployment.ContractCompanionService;
import com.maidweapon.forge.system.fox.FoxSpiritCompanionService;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.maidweapon.forge.system.fox.FoxSpiritTransferService;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Real TLM entity, manual companion lifecycle and contract-table migration in a private world. */
public final class FoxCompanionValidation {
    public static void run(ServerPlayer owner, ItemStack offering) throws Exception {
        var packDirectory = java.nio.file.Files.createTempDirectory(
                net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get(), ".fox-pack-fixture-");
        var installedPack = packDirectory.resolve("contract-blade-fox-1.0.0.zip");
        try (var supplied = FoxCompanionValidation.class.getResourceAsStream("/modelpacks/contract-fox-1.0.0.zip")) {
            java.nio.file.Files.copy(java.util.Objects.requireNonNull(supplied), installedPack);
        }
        byte[] suppliedPack = java.nio.file.Files.readAllBytes(installedPack);
        FoxModelPackBootstrap.install(packDirectory);
        check(!java.util.Arrays.equals(suppliedPack, java.nio.file.Files.readAllBytes(installedPack)),
                "exact managed original upgrades to the corrected runtime pack");
        try (var files = java.nio.file.Files.list(packDirectory)) {
            var backup = files.filter(path -> path.getFileName().toString().endsWith(".bak"))
                    .findFirst().orElseThrow();
            check(java.util.Arrays.equals(suppliedPack, java.nio.file.Files.readAllBytes(backup)),
                    "managed upgrade keeps a byte-identical recoverable original");
        }
        byte[] originalPack = java.nio.file.Files.readAllBytes(installedPack);
        FoxModelPackBootstrap.install(packDirectory);
        check(java.util.Arrays.equals(originalPack, java.nio.file.Files.readAllBytes(installedPack)),
                "repeated model preparation never overwrites an installed archive");
        var externalPack = packDirectory.resolve("player-supplied-fox.zip");
        java.nio.file.Files.move(installedPack, externalPack);
        FoxModelPackBootstrap.install(packDirectory);
        check(!java.nio.file.Files.exists(installedPack)
                        && java.util.Arrays.equals(originalPack, java.nio.file.Files.readAllBytes(externalPack)),
                "user-installed fox pack wins without duplicate IDs or replacement");
        check(FoxModelPackBootstrap.isRegistered(FoxModelPackBootstrap.WHITE_MODEL)
                        && FoxModelPackBootstrap.isRegistered(FoxModelPackBootstrap.BLACK_MODEL),
                "both supplied fox models registered through TLM's native server loader");
        var white = offering.copy();
        white.getOrCreateTag().remove(FoxSpiritState.ROOT);
        white.getOrCreateTag().remove(FoxSpiritState.ORIGIN);
        white.getOrCreateTag().remove(FoxSpiritState.VACANT);
        var ordinary = white.copy();
        ordinary.getOrCreateTag().remove(FoxSpiritState.OFFERING);
        check(!FoxSpiritCompanionService.initialize(owner, ordinary),
                "ordinary named White Fox does not acquire the shrine spirit");
        FoxSpiritTransferService.claimOffering(owner, white);
        owner.getInventory().clearContent();
        owner.getInventory().selected = 0;
        owner.getInventory().setItem(0, white);
        // Fake players do not load entity-tracking chunks. Use the actual spawn area.
        owner.moveTo(0.5, 100, 0.5, 0, 0);
        check(FoxSpiritCompanionService.initialize(owner, white), "shrine spirit receives a complete native contract");
        check(FoxModelPackBootstrap.WHITE_MODEL.equals(MaidEntityDataCodec.read(white.getTag()).getString("ModelId")),
                "initial contract selects the supplied White Fox model");
        String maidId = ContractCarrierData.getBoundMaidUUID(white);
        String binding = ContractCarrierData.getBindingId(white);
        var uuid = java.util.UUID.fromString(maidId);
        check(uuid.equals(MaidEntityDataCodec.read(white.getTag()).getUUID("UUID")),
                "stored entity and projected contract share the same native UUID");
        Mob maid = null;
        var previousMenu = owner.containerMenu;
        try {
            for (int tick = 0; tick < 100; tick++) {
                FoxSpiritCompanionService.synchronizeInventory(owner);
                ContractCompanionService.tick(owner);
            }
            check(maidId.equals(ContractCarrierData.getBoundMaidUUID(white))
                            && owner.serverLevel().getEntity(uuid) == null,
                    "holding and repeated initialization neither manifest nor duplicate White Fox");
            check(owner.isAlive() && !owner.isSpectator() && owner.containerMenu == owner.inventoryMenu,
                    "fixture owner can request a companion outside menus");
            check(com.maidweapon.forge.system.InfusedMaidDeploymentSystem.isEligibleWeapon(white, owner)
                            && MaidInfusion.containsMaid(white)
                            && FoxSpiritTransferService.authorizesContract(owner, white),
                    "initialized fox contract is eligible and has current authority");
            check(owner.getMainHandItem() == white
                            && !com.maidweapon.forge.system.deployment.ContractTransferSafetyService.isProjectionPhantom(white),
                    "fixture holds the real source weapon, not an equipment projection");
            call(owner);
            maid = (Mob) owner.serverLevel().getEntity(uuid);
            check(maid != null && !MaidInfusion.containsMaid(white), "manual companion key path manifests White Fox");
            check(FoxModelPackBootstrap.WHITE_MODEL.equals(maid.getClass().getMethod("getModelId").invoke(maid)),
                    "live native maid uses the supplied White Fox model");
            maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
            maid.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            check(IntrinsicSpiritApi.setIntrinsicSpiritModel(owner, white, FoxSpiritState.WHITE,
                    FoxModelPackBootstrap.BLACK_MODEL), "owner-selected alternate model can be applied to the live spirit");
            FoxSpiritCompanionService.synchronizeInventory(owner);
            check(FoxModelPackBootstrap.BLACK_MODEL.equals(maid.getClass().getMethod("getModelId").invoke(maid)),
                    "inventory synchronization never overwrites the owner's model choice");

            // Extraction while visible must use the existing recall range and real menu locator.
            BlockPos table = new BlockPos(0, 100, 3);
            owner.serverLevel().setBlock(table, com.maidweapon.forge.init.ModBlocks.MAID_INJECTOR.get().defaultBlockState(), 2);
            var menu = new MaidInjectorMenu(9, owner.getInventory(), table);
            owner.containerMenu = menu;
            owner.getInventory().setItem(0, ItemStack.EMPTY);
            var soulItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("touhou_little_maid:film"));
            menu.getSlot(0).set(white);
            menu.getSlot(1).set(new ItemStack(soulItem));
            maid.moveTo(owner.getX() + 40, owner.getY(), owner.getZ(), 0, 0);
            check(!menu.clickMenuButton(owner, MaidInjectorMenu.INJECT_BUTTON)
                            && menu.getSlot(0).getItem() == white && menu.getSlot(2).getItem().isEmpty()
                            && owner.serverLevel().getEntity(uuid) == maid,
                    "out-of-range extraction leaves the real maid and weapon intact");
            maid.moveTo(owner.getX() + 2, owner.getY(), owner.getZ(), 0, 0);
            var sealed = FoxSpiritTransferValidation.click(menu, owner, white, menu.getSlot(1).getItem());
            check(owner.serverLevel().getEntity(uuid) == null, "nearby visible spirit is recalled before table extraction");
            var moved = FoxSpiritTransferValidation.click(menu, owner, new ItemStack(Items.IRON_SWORD), sealed[1]);
            check(maidId.equals(ContractCarrierData.getBoundMaidUUID(moved[0]))
                            && binding.equals(ContractCarrierData.getBindingId(moved[0])),
                    "native companion and interior binding survive migration");
            var stored = MaidEntityDataCodec.read(moved[0].getTag());
            check(FoxModelPackBootstrap.BLACK_MODEL.equals(stored.getString("ModelId")),
                    "selected model survives actual recall and migration");
            var returnSeal = FoxSpiritTransferValidation.click(menu, owner, moved[0], moved[1]);
            var returned = FoxSpiritTransferValidation.click(menu, owner, sealed[0], returnSeal[1]);
            ItemStack reloaded = ItemStack.of(returned[0].save(new CompoundTag()));
            owner.containerMenu = previousMenu;
            owner.getInventory().setItem(0, reloaded);
            check(FoxSpiritCompanionService.initialize(owner, reloaded)
                            && maidId.equals(ContractCarrierData.getBoundMaidUUID(reloaded))
                            && FoxSpiritTransferService.storyEligible(owner, reloaded),
                    "saved returned blade keeps one companion and its original story gate");
            call(owner);
            maid = (Mob) owner.serverLevel().getEntity(uuid);
            check(maid != null && maid.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.DIAMOND_AXE)
                            && maid.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)
                            && FoxModelPackBootstrap.BLACK_MODEL.equals(maid.getClass().getMethod("getModelId").invoke(maid)),
                    "redeployed native spirit retains real equipment and selected model after save and round trip");
            check(IntrinsicSpiritApi.setIntrinsicSpiritModel(owner, reloaded, FoxSpiritState.WHITE,
                    FoxModelPackBootstrap.WHITE_MODEL), "fixture restores White Fox's appearance");
            call(owner);
            check(MaidInfusion.containsMaid(reloaded) && owner.serverLevel().getEntity(uuid) == null,
                    "manual recall completes without a second entity");
            LogUtils.getLogger().info("FOX_COMPANION_NATIVE_PASS");
        } finally {
            if (maid != null && !maid.isRemoved()) maid.discard();
            owner.containerMenu = previousMenu;
            owner.getInventory().clearContent();
            ContractCompanionService.disconnect(owner);
        }
    }

    private static void call(ServerPlayer owner) {
        ContractCompanionService.disconnect(owner);
        ContractCompanionService.toggle(owner);
    }

    private static void check(boolean passed, String label) {
        if (!passed) throw new IllegalStateException(label);
        LogUtils.getLogger().info("Fox companion fixture: {}", label);
    }

    private FoxCompanionValidation() { }
}

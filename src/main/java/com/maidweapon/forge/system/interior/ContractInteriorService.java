package com.maidweapon.forge.system.interior;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.system.interior.home.ContractHomeRuntime;
import com.maidweapon.forge.system.interior.home.ContractInteriorGuideService;
import net.minecraft.world.entity.Mob;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.system.contract.ContractLifecycleService;
import com.maidweapon.forge.item.ContractInteriorKeyItem;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import com.maidweapon.forge.system.deployment.ContractTransferSafetyService;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Server-side authority for entering, leaving and recovering contract interiors. */
public final class ContractInteriorService {
    public static final ResourceKey<Level> INTERIOR_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(MaidWeaponConstants.MOD_ID, "contract_interior")
    );

    private static final String TAG_RETURN = "MaidWeaponInteriorReturn";
    private static final String TAG_ACTIVE_BINDING = "Binding";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";
    private static final String TAG_Y_ROT = "YRot";
    private static final String TAG_X_ROT = "XRot";
    private static final String TAG_GALLERY = "Gallery";

    public static ItemStack contractForKey(ServerPlayer player, InteractionHand keyHand) {
        ItemStack candidate = keyHand == InteractionHand.MAIN_HAND
                ? player.getOffhandItem()
                : player.getMainHandItem();
        return MaidInfusion.isInfused(candidate) ? candidate : ItemStack.EMPTY;
    }

    /**
     * Resolve the contract participating in first-entry terrain selection.
     *
     * <p>The normal Heart Key layout is preferred, but the held contract is
     * accepted as a fallback for clickable-chat recovery.</p>
     */
    public static ItemStack contractForSelection(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        if (main.getItem() instanceof ContractInteriorKeyItem && MaidInfusion.isInfused(off)) {
            return off;
        }
        if (off.getItem() instanceof ContractInteriorKeyItem && MaidInfusion.isInfused(main)) {
            return main;
        }
        return ItemStack.EMPTY;
    }

    public static boolean enter(ServerPlayer player, ItemStack contract) {
        if (isInside(player)) return false;
        if (contract.isEmpty() || !MaidInfusion.isInfused(contract)) {
            message(player, "maid_weapon.message.interior.need_contract");
            return false;
        }
        if (!MaidWeaponItem.isOwner(contract, player)) {
            message(player, "maid_weapon.message.not_owner");
            return false;
        }
        if (MaidWeaponItem.isContractSuperseded(contract)) {
            message(player, "maid_weapon.message.superseded_contract");
            return false;
        }

        String bindingId = MaidWeaponItem.ensureBindingId(contract);
        String maidId = MaidWeaponItem.getBoundMaidUUID(contract);
        Entity existingMaid = maidId == null ? null
                : ContractWeaponLocator.findManifestedMaid(player, maidId);
        boolean resumeInteriorMaid = existingMaid != null
                && existingMaid.level().dimension().equals(INTERIOR_LEVEL)
                && bindingId.equals(existingMaid.getPersistentData().getString(
                        com.maidweapon.forge.compat.tlm.ContractMaidKeys.ENTITY_BINDING_ID));

        if (!MaidWeaponItem.hasMaidEntityData(contract) && !resumeInteriorMaid) {
            message(player, "maid_weapon.message.interior.recall_first");
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return false;
        ServerLevel interior = server.getLevel(INTERIOR_LEVEL);
        if (interior == null) {
            message(player, "maid_weapon.message.interior.unavailable");
            return false;
        }

        MaidWeaponData data = MaidInfusion.data(contract);
        ContractInteriorProfile profile = ContractInteriorProfile.from(data);
        ContractInteriorSavedData saved = ContractInteriorSavedData.get(server);
        ContractInteriorSavedData.Plot plot = saved.getOrCreate(bindingId);

        if (!plot.hasTerrainTheme()) {
            ContractInteriorSelectionService.prompt(player, contract);
            return true;
        }

        BlockPos origin = origin(plot);
        ContractInteriorTerrainBuilder.ensureGenerated(
                interior,
                origin,
                profile,
                saved,
                bindingId
        );

        saveReturn(player, bindingId);
        player.teleportTo(
                interior,
                origin.getX() + 0.5D,
                origin.getY() + 1.1D,
                origin.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
        player.fallDistance = 0.0F;

        if (!resumeInteriorMaid
                && !ContractLifecycleService.manifest(player, contract, false)) {
            restoreReturn(player);
            clearReturn(player);
            message(player, "maid_weapon.message.interior.maid_restore_failed");
            return false;
        }

        Entity maid = resumeInteriorMaid
                ? existingMaid
                : (maidId == null ? null : ContractWeaponLocator.findManifestedMaid(player, maidId));
        if (maid != null) {
            maid.moveTo(
                    origin.getX() + 2.5D,
                    origin.getY() + 1.1D,
                    origin.getZ() + 0.5D,
                    maid.getYRot(),
                    maid.getXRot()
            );
        }

        attachHome(player, bindingId, maid, saved, plot);
        message(player, "maid_weapon.message.interior.entered");
        return true;
    }

    public static boolean exit(ServerPlayer player) {
        if (!isInside(player)) return false;
        CompoundTag state = returnState(player);

        if (state.getBoolean(TAG_GALLERY)) {
            restoreReturn(player);
            clearReturn(player);
            message(player, "maid_weapon.message.interior.gallery_left");
            return true;
        }

        String bindingId = state.getString(TAG_ACTIVE_BINDING);
        ItemStack contract = findContractByBinding(player, bindingId);
        if (contract.isEmpty()) {
            message(player, "maid_weapon.message.interior.missing_contract");
            return false;
        }

        if (!recallInteriorMaid(player, contract)) {
            message(player, "maid_weapon.message.interior.recall_failed");
            return false;
        }

        restoreReturn(player);
        clearReturn(player);
        message(player, "maid_weapon.message.interior.left");
        return true;
    }

    public static boolean enterGallery(ServerPlayer player, BlockPos target) {
        if (player == null || target == null) return false;

        MinecraftServer server = player.getServer();
        if (server == null) return false;
        ServerLevel interior = server.getLevel(INTERIOR_LEVEL);
        if (interior == null) {
            message(player, "maid_weapon.message.interior.unavailable");
            return false;
        }

        if (isInside(player)) {
            if (!isGallerySession(player)) {
                message(player, "maid_weapon.message.interior.gallery_busy");
                return false;
            }
        } else {
            saveReturn(player, "");
            CompoundTag state = returnState(player);
            state.putBoolean(TAG_GALLERY, true);
            player.getPersistentData().put(TAG_RETURN, state);
        }

        player.teleportTo(
                interior,
                target.getX() + 0.5D,
                target.getY() + 1.1D,
                target.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
        player.fallDistance = 0.0F;
        return true;
    }

    public static boolean isGallerySession(ServerPlayer player) {
        return isInside(player) && returnState(player).getBoolean(TAG_GALLERY);
    }

    public static void prepareForLogout(ServerPlayer player) {
        if (!isInside(player) || isGallerySession(player)) return;
        ItemStack contract = findContractByBinding(
                player,
                returnState(player).getString(TAG_ACTIVE_BINDING)
        );
        if (!contract.isEmpty()) {
            if (!recallInteriorMaid(player, contract)) pauseUncaptured(player, contract);
        } else ContractHomeRuntime.pause(player);
    }

    public static void prepareForDeath(ServerPlayer player) {
        if (!isInside(player)) return;
        ItemStack contract = findContractByBinding(
                player,
                returnState(player).getString(TAG_ACTIVE_BINDING)
        );
        if (!contract.isEmpty()) {
            if (!recallInteriorMaid(player, contract)) pauseUncaptured(player, contract);
        } else ContractHomeRuntime.pause(player);
        clearReturn(player);
    }

    public static void recoverFromVoid(ServerPlayer player) {
        if (!isInside(player)) return;

        CompoundTag state = returnState(player);
        if (state.getBoolean(TAG_GALLERY)) {
            if (player.getY() < 30.0D) {
                BlockPos safe = ContractInteriorGallery.overviewSpawn();
                player.teleportTo(
                        (ServerLevel) player.level(),
                        safe.getX() + 0.5D,
                        safe.getY() + 1.1D,
                        safe.getZ() + 0.5D,
                        player.getYRot(),
                        player.getXRot()
                );
                player.fallDistance = 0.0F;
            }
            return;
        }

        String bindingId = state.getString(TAG_ACTIVE_BINDING);
        if (bindingId.isEmpty()) {
            emergencyReturnToOverworld(player);
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ItemStack contract = findContractByBinding(player, bindingId);
        if (!contract.isEmpty() && (!MaidInfusion.isInfused(contract)
                || !MaidWeaponItem.isOwner(contract, player)
                || MaidWeaponItem.isContractSuperseded(contract))) {
            // A stale/invalid session must not leave a player stranded in the shared
            // interior dimension. Preserve a live resident by pausing it, then return.
            ContractHomeRuntime.pause(player);
            restoreReturn(player);
            clearReturn(player);
            return;
        }

        ContractInteriorSavedData.Plot plot =
                ContractInteriorSavedData.get(server).getOrCreate(bindingId);
        if (!plot.hasTerrainTheme() || plot.generatedStage() == 0) {
            ContractHomeRuntime.pause(player);
            restoreReturn(player);
            clearReturn(player);
            return;
        }

        // Inventory clicks are not lifecycle exits. The creative cursor is only
        // client-side, so the carrier can be temporarily absent on the server.
        // Keep the established session instead of teleporting and closing it.

        // Same-dimension teleport mods do not fire PlayerChangedDimensionEvent.
        // Keep each active binding inside its own horizontal plot so a Waystone or
        // command cannot jump directly into another player's cell.
        boolean escapedPlot = !insidePlot(player.blockPosition(), plot);
        if (player.getY() >= 30.0D && !escapedPlot) return;

        BlockPos origin = origin(plot);
        player.teleportTo(
                (ServerLevel) player.level(),
                origin.getX() + 0.5D,
                origin.getY() + 1.1D,
                origin.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
        player.fallDistance = 0.0F;
        if (escapedPlot && player.getY() >= 30.0D) {
            message(player, "maid_weapon.message.interior.plot_escape_recovered");
        }
    }

    private static void emergencyReturnToOverworld(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(
                overworld,
                spawn.getX() + 0.5D,
                spawn.getY() + 1.0D,
                spawn.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
        player.fallDistance = 0.0F;
        clearReturn(player);
    }

    public static boolean isInside(ServerPlayer player) {
        return player.level().dimension().equals(INTERIOR_LEVEL);
    }

    public static boolean isActiveContract(ServerPlayer player, ItemStack stack) {
        if (!isInside(player) || stack.isEmpty()) return false;
        String active = returnState(player).getString(TAG_ACTIVE_BINDING);
        String binding = MaidWeaponItem.getBindingId(stack);
        return binding != null && !binding.isEmpty() && binding.equals(active);
    }

    /**
     * Emergency authority handoff when the physical active carrier breaks inside
     * its own home. Reuses the same TLM resurrection-film format as generic
     * deployment and commits film delivery before removing the live maid.
     */
    public static boolean recoverDestroyedActiveContract(ServerPlayer player, ItemStack destroyed) {
        if (player == null || destroyed.isEmpty() || isGallerySession(player)
                || !isActiveContract(player, destroyed)
                || !MaidInfusion.isInfused(destroyed)
                || !MaidWeaponItem.isOwner(destroyed, player)) return false;

        String binding = MaidWeaponItem.getBindingId(destroyed);
        String maidId = MaidWeaponItem.getBoundMaidUUID(destroyed);
        Entity maid = maidId == null || maidId.isEmpty()
                ? null : ContractWeaponLocator.findManifestedMaid(player, maidId);

        boolean homeReleased = ContractHomeRuntime.stop(player);
        boolean maidReleased = maid == null
                || homeReleased && ContractHomeRuntime.prepareCapture(maid);

        ItemStack film = maidReleased
                ? TouhouLittleMaidHelper.createEmergencyResurrectionFilm(player, destroyed, maid)
                : ItemStack.EMPTY;
        if (!film.isEmpty() && deliverEmergencyFilm(player, film)) {
            if (maid != null && !maid.isRemoved()) maid.discard();
            restoreReturn(player);
            clearReturn(player);
            player.displayClientMessage(Component.translatable(
                    "maid_weapon.message.carrier_destroyed_film_created"), false);
            return true;
        }

        if (maid != null && !maid.isRemoved()) {
            ContractHomeRuntime.pauseUncaptured(maid, binding);
        }
        restoreReturn(player);
        clearReturn(player);
        player.displayClientMessage(Component.translatable(
                "maid_weapon.message.emergency_film_failed"), false);
        return false;
    }

    private static boolean deliverEmergencyFilm(ServerPlayer player, ItemStack film) {
        ItemStack remainder = film.copy();
        player.getInventory().add(remainder);
        if (remainder.isEmpty()) return true;
        return player.drop(remainder, false) != null;
    }

    private static boolean recallInteriorMaid(ServerPlayer player, ItemStack contract) {
        boolean homeReleased = ContractHomeRuntime.stop(player);
        String maidId = MaidWeaponItem.getBoundMaidUUID(contract);
        if (maidId == null || maidId.isEmpty()) return homeReleased;

        Entity maid = ContractWeaponLocator.findManifestedMaid(player, maidId);
        if (maid == null) {
            return homeReleased && MaidWeaponItem.hasMaidEntityData(contract);
        }
        if (!homeReleased || !ContractHomeRuntime.prepareCapture(maid)) {
            ContractHomeRuntime.pauseUncaptured(maid, MaidWeaponItem.getBindingId(contract));
            return false;
        }
        boolean captured = ContractLifecycleService.capture(player, maid, contract, false);
        if (!captured) ContractHomeRuntime.pauseUncaptured(maid, MaidWeaponItem.getBindingId(contract));
        return captured;
    }

    private static void pauseUncaptured(ServerPlayer player, ItemStack contract) {
        String maidId = MaidWeaponItem.getBoundMaidUUID(contract);
        if (maidId != null) ContractHomeRuntime.pauseUncaptured(
                ContractWeaponLocator.findManifestedMaid(player, maidId), MaidWeaponItem.getBindingId(contract));
    }

    private static ItemStack findContractByBinding(ServerPlayer player, String bindingId) {
        ItemStack carriedByPlayer = findPlayerContractByBinding(player, bindingId);
        if (!carriedByPlayer.isEmpty()) return carriedByPlayer;
        if (bindingId == null || bindingId.isEmpty()) return ItemStack.EMPTY;

        // The active contract may be on the cursor or in an open/modded container.
        // Resolve that real stack instead of creating a recovery copy.
        ItemStack carried = player.containerMenu.getCarried();
        if (!ContractTransferSafetyService.isProjectionPhantom(carried)
                && bindingId.equals(MaidWeaponItem.getBindingId(carried))) return carried;
        for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (!ContractTransferSafetyService.isProjectionPhantom(stack)
                    && bindingId.equals(MaidWeaponItem.getBindingId(stack))) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findPlayerContractByBinding(ServerPlayer player, String bindingId) {
        if (bindingId == null || bindingId.isEmpty()) return ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!ContractTransferSafetyService.isProjectionPhantom(stack)
                    && bindingId.equals(MaidWeaponItem.getBindingId(stack))) return stack;
        }
        ItemStack offhand = player.getOffhandItem();
        if (!ContractTransferSafetyService.isProjectionPhantom(offhand)
                && bindingId.equals(MaidWeaponItem.getBindingId(offhand))) return offhand;
        return ItemStack.EMPTY;
    }

    /**
     * The active home contract must remain recoverable by its owner. A generic
     * container may temporarily receive it while the maid is live; on close, move
     * that exact stack back to the player instead of letting generic deployment
     * capture the interior maid or leaving the owner locked out of the home.
     */
    public static boolean rescueActiveContractFromContainer(ServerPlayer player) {
        return rescueActiveContractFromContainer(player, false);
    }

    public static boolean rescueActiveContractFromContainer(ServerPlayer player, boolean closingMenu) {
        if (!isInside(player) || isGallerySession(player)) return false;
        String binding = returnState(player).getString(TAG_ACTIVE_BINDING);
        if (binding.isEmpty() || !findPlayerContractByBinding(player, binding).isEmpty()) return false;

        ItemStack carried = player.containerMenu.getCarried();
        if (!ContractTransferSafetyService.isProjectionPhantom(carried)
                && binding.equals(MaidWeaponItem.getBindingId(carried))) {
            // Keep normal mouse pickups on the cursor until the menu closes.
            if (!closingMenu) return false;
            ItemStack contract = carried.copy();
            int free = player.getInventory().getFreeSlot();
            if (free >= 0) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                player.getInventory().setItem(free, contract);
            } else {
                int selected = player.getInventory().selected;
                ItemStack displaced = player.getInventory().getItem(selected);
                player.getInventory().setItem(selected, contract);
                player.containerMenu.setCarried(displaced);
            }
            player.containerMenu.broadcastChanges();
            player.displayClientMessage(Component.translatable(
                    "maid_weapon.message.interior.contract_container_blocked"), true);
            return true;
        }

        for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots) {
            if (slot.container == player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (ContractTransferSafetyService.isProjectionPhantom(stack)) continue;
            if (!binding.equals(MaidWeaponItem.getBindingId(stack))) continue;

            ItemStack contract = stack.copy();
            int selected = player.getInventory().selected;
            int free = player.getInventory().getFreeSlot();

            if (free >= 0) {
                slot.set(ItemStack.EMPTY);
                slot.setChanged();
                player.getInventory().setItem(free, contract);
            } else {
                int swap = -1;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack displaced = player.getInventory().getItem(i);
                    if (!displaced.isEmpty() && slot.mayPlace(displaced)) {
                        swap = i;
                        break;
                    }
                }
                if (swap >= 0) {
                    ItemStack displaced = player.getInventory().getItem(swap);
                    slot.set(displaced);
                    slot.setChanged();
                    player.getInventory().setItem(swap, contract);
                } else {
                    // Extremely defensive fallback for restrictive modded slots:
                    // preserve contract authority even if one ordinary inventory
                    // stack must be dropped beside the owner.
                    ItemStack displaced = player.getInventory().getItem(selected);
                    slot.set(ItemStack.EMPTY);
                    slot.setChanged();
                    player.getInventory().setItem(selected, contract);
                    if (!displaced.isEmpty()) player.drop(displaced, false);
                }
            }

            player.containerMenu.broadcastChanges();
            player.displayClientMessage(Component.translatable(
                    "maid_weapon.message.interior.contract_container_blocked"), true);
            return true;
        }
        return false;
    }

    private static BlockPos origin(ContractInteriorSavedData.Plot plot) {
        return new BlockPos(
                ContractInteriorSavedData.originX(plot),
                ContractInteriorTerrainBuilder.ORIGIN_Y,
                ContractInteriorSavedData.originZ(plot)
        );
    }

    /** Owner/binding validation shared by lifecycle resume and Home Clock commands. */
    public static String activeOwnedBinding(ServerPlayer player) {
        if (!isInside(player) || isGallerySession(player)) return "";
        String binding = returnState(player).getString(TAG_ACTIVE_BINDING);
        ItemStack contract = findContractByBinding(player, binding);
        if (contract.isEmpty() || !MaidInfusion.isInfused(contract)
                || !MaidWeaponItem.isOwner(contract, player)
                || MaidWeaponItem.isContractSuperseded(contract) || player.getServer() == null) return "";
        var plot = ContractInteriorSavedData.get(player.getServer()).getOrCreate(binding);
        if (!plot.hasTerrainTheme() || plot.generatedStage() == 0) return "";
        if (!insidePlot(player.blockPosition(), plot)) return "";
        return binding;
    }

    private static boolean insidePlot(BlockPos position, ContractInteriorSavedData.Plot plot) {
        int radius = ContractInteriorTerrainBuilder.radiusForStage(plot.generatedStage());
        double x = Math.abs(position.getX() - ContractInteriorSavedData.originX(plot)) / (double) radius;
        double z = Math.abs(position.getZ() - ContractInteriorSavedData.originZ(plot)) / (double) radius;
        return Math.pow(x, 6) + Math.pow(z, 6) <= 1.0D;
    }

    public static void resumeHome(ServerPlayer player) {
        if (ContractHomeRuntime.attached(player)) return;
        String binding = activeOwnedBinding(player);
        if (binding.isEmpty() || player.getServer() == null) return;
        var saved = ContractInteriorSavedData.get(player.getServer());
        var plot = saved.getOrCreate(binding);
        if (!plot.hasTerrainTheme() || plot.generatedStage() == 0) return;
        ItemStack contract = findContractByBinding(player, binding);
        String maidId = MaidWeaponItem.getBoundMaidUUID(contract);
        Entity maid = maidId == null ? null : ContractWeaponLocator.findManifestedMaid(player, maidId);
        if (maid == null && MaidWeaponItem.hasMaidEntityData(contract)) {
            // No new storage path: the same lifecycle service consumes the same stored authority.
            if (!ContractLifecycleService.manifest(player, contract, false)) return;
            maid = maidId == null ? null : ContractWeaponLocator.findManifestedMaid(player, maidId);
        }
        if (maid == null || maid.level() != player.level() || !binding.equals(
                maid.getPersistentData().getString(com.maidweapon.forge.compat.tlm.ContractMaidKeys.ENTITY_BINDING_ID))) return;
        attachHome(player, binding, maid, saved, plot);
    }

    private static void attachHome(ServerPlayer player, String binding, Entity maid,
                                   ContractInteriorSavedData saved, ContractInteriorSavedData.Plot plot) {
        if (maid instanceof Mob mob) ContractHomeRuntime.start(player, binding, mob, saved, plot);
        ContractInteriorGuideService.give(player, saved, plot);
    }

    /** Commands/portals must not strand a live maid when bypassing the Heart Key exit. */
    public static void leaveUnexpectedly(ServerPlayer player) {
        String binding = returnState(player).getString(TAG_ACTIVE_BINDING);
        if (binding.isEmpty()) return;
        ItemStack contract = findContractByBinding(player, binding);
        if (contract.isEmpty() || !recallInteriorMaid(player, contract)) {
            ContractHomeRuntime.pause(player);
        }
        // The player is already outside the interior. A failed capture keeps the
        // resident frozen under its binding and may be resumed by a later re-entry;
        // the old return marker must not survive as a stale cross-dimension session.
        clearReturn(player);
    }

    private static void saveReturn(ServerPlayer player, String bindingId) {
        CompoundTag state = new CompoundTag();
        state.putString(TAG_ACTIVE_BINDING, bindingId);
        state.putString(TAG_DIMENSION, player.level().dimension().location().toString());
        state.putDouble(TAG_X, player.getX());
        state.putDouble(TAG_Y, player.getY());
        state.putDouble(TAG_Z, player.getZ());
        state.putFloat(TAG_Y_ROT, player.getYRot());
        state.putFloat(TAG_X_ROT, player.getXRot());
        player.getPersistentData().put(TAG_RETURN, state);
    }

    private static void restoreReturn(ServerPlayer player) {
        CompoundTag state = returnState(player);
        MinecraftServer server = player.getServer();
        if (server == null || state.isEmpty()) return;

        ResourceLocation id = ResourceLocation.tryParse(state.getString(TAG_DIMENSION));
        ServerLevel target = id == null ? null
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (target == null) target = server.overworld();

        double x = state.contains(TAG_X) ? state.getDouble(TAG_X) : target.getSharedSpawnPos().getX() + 0.5D;
        double y = state.contains(TAG_Y) ? state.getDouble(TAG_Y) : target.getSharedSpawnPos().getY() + 1.0D;
        double z = state.contains(TAG_Z) ? state.getDouble(TAG_Z) : target.getSharedSpawnPos().getZ() + 0.5D;
        player.teleportTo(
                target, x, y, z,
                state.getFloat(TAG_Y_ROT),
                state.getFloat(TAG_X_ROT)
        );
        player.fallDistance = 0.0F;
    }

    private static CompoundTag returnState(ServerPlayer player) {
        return player.getPersistentData().getCompound(TAG_RETURN);
    }

    private static void clearReturn(ServerPlayer player) {
        player.getPersistentData().remove(TAG_RETURN);
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    private ContractInteriorService() {
    }
}

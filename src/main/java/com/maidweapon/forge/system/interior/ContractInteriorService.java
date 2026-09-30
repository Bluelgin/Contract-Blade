package com.maidweapon.forge.system.interior;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.compat.tlm.ContractMaidLifecycleService;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
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
        BlockPos origin = origin(plot);
        ContractInteriorBuilder.ensureBuilt(interior, origin, profile, saved, bindingId);

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
                && !ContractMaidLifecycleService.manifest(player, contract, false)) {
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
            recallInteriorMaid(player, contract);
        }
    }

    public static void prepareForDeath(ServerPlayer player) {
        if (!isInside(player)) return;
        ItemStack contract = findContractByBinding(
                player,
                returnState(player).getString(TAG_ACTIVE_BINDING)
        );
        if (!contract.isEmpty()) {
            recallInteriorMaid(player, contract);
        }
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
        if (player.getY() >= 30.0D || player.getServer() == null) return;

        ContractInteriorSavedData.Plot plot =
                ContractInteriorSavedData.get(player.getServer()).getOrCreate(bindingId);
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

    private static boolean recallInteriorMaid(ServerPlayer player, ItemStack contract) {
        String maidId = MaidWeaponItem.getBoundMaidUUID(contract);
        if (maidId == null || maidId.isEmpty()) return true;

        Entity maid = ContractWeaponLocator.findManifestedMaid(player, maidId);
        if (maid == null) {
            return MaidWeaponItem.hasMaidEntityData(contract);
        }
        return ContractMaidLifecycleService.capture(player, maid, contract, false);
    }

    private static ItemStack findContractByBinding(ServerPlayer player, String bindingId) {
        if (bindingId == null || bindingId.isEmpty()) return ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (bindingId.equals(MaidWeaponItem.getBindingId(stack))) return stack;
        }
        ItemStack offhand = player.getOffhandItem();
        if (bindingId.equals(MaidWeaponItem.getBindingId(offhand))) return offhand;
        return ItemStack.EMPTY;
    }

    private static BlockPos origin(ContractInteriorSavedData.Plot plot) {
        return new BlockPos(
                ContractInteriorSavedData.originX(plot),
                ContractInteriorBuilder.ORIGIN_Y,
                ContractInteriorSavedData.originZ(plot)
        );
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

package com.maidweapon.forge.system;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.item.PetWeaponItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Cat and wolf contracts deliberately use their own NBT, apart from maid contracts. */
@Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PetWeaponSystem {
    private static final String PET_DATA = "PetWeaponEntity";
    private static final String PET_UUID = "PetWeaponUuid";
    private static final String OWNER_UUID = "PetWeaponOwner";
    private static final String BINDING = "PetWeaponBinding";
    private static final String PET_NAME = "PetWeaponName";
    private static final String ENTITY_BINDING = "MaidWeaponPetBinding";

    public static boolean hasStoredPet(ItemStack weapon) {
        CompoundTag root = weapon.getTag();
        return root != null && root.contains(PET_DATA, Tag.TAG_COMPOUND);
    }

    public static boolean hasBinding(ItemStack weapon) {
        CompoundTag root = weapon.getTag();
        return root != null && root.hasUUID(PET_UUID) && root.hasUUID(OWNER_UUID)
                && root.hasUUID(BINDING);
    }

    public static String petName(ItemStack weapon) {
        CompoundTag root = weapon.getTag();
        return root == null ? "" : root.getString(PET_NAME);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPetInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity().getItemInHand(event.getHand()).getItem()
                instanceof PetWeaponItem) || !(event.getTarget() instanceof TamableAnimal pet)) return;
        ItemStack weapon = event.getEntity().getItemInHand(event.getHand());
        PetWeaponItem item = (PetWeaponItem) weapon.getItem();
        if (!item.kind().matches(pet)) return;

        // Handle the interaction before vanilla's sit/stand action consumes it.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!event.getLevel().isClientSide) capture(event.getEntity(), weapon, pet);
    }

    public static boolean capture(Player player, ItemStack weapon, TamableAnimal pet) {
        if (!(weapon.getItem() instanceof PetWeaponItem item) || !item.kind().matches(pet)
                || !(player.level() instanceof ServerLevel)
                || !player.getUUID().equals(pet.getOwnerUUID())) {
            player.displayClientMessage(Component.translatable("maid_weapon.pet.not_owned"), true);
            return false;
        }
        if (hasStoredPet(weapon)) {
            player.displayClientMessage(Component.translatable("maid_weapon.pet.already_stored"), true);
            return false;
        }

        CompoundTag root = weapon.getTag();
        if (hasBinding(weapon)) {
            if (!root.getUUID(OWNER_UUID).equals(player.getUUID())
                    || !root.getUUID(PET_UUID).equals(pet.getUUID())
                    || !root.getUUID(BINDING).toString().equals(
                    pet.getPersistentData().getString(ENTITY_BINDING))) {
                player.displayClientMessage(Component.translatable("maid_weapon.pet.wrong_pet"), true);
                return false;
            }
        } else if (!pet.getPersistentData().getString(ENTITY_BINDING).isEmpty()) {
            player.displayClientMessage(Component.translatable("maid_weapon.pet.wrong_pet"), true);
            return false;
        }

        CompoundTag data = new CompoundTag();
        pet.saveWithoutId(data);
        data.remove("Pos");
        data.remove("Motion");
        data.remove("Rotation");
        data.remove("Leash");
        data.remove("Passengers");
        if (!validPetData(item, player, pet.getUUID(), data)) {
            player.displayClientMessage(Component.translatable("maid_weapon.pet.store_failed"), true);
            return false;
        }

        UUID binding = hasBinding(weapon) ? root.getUUID(BINDING) : UUID.randomUUID();
        CompoundTag forgeData = data.getCompound("ForgeData");
        forgeData.putString(ENTITY_BINDING, binding.toString());
        data.put("ForgeData", forgeData);
        CompoundTag stored = weapon.getOrCreateTag();
        stored.putUUID(PET_UUID, pet.getUUID());
        stored.putUUID(OWNER_UUID, player.getUUID());
        stored.putUUID(BINDING, binding);
        stored.putString(PET_NAME, pet.getName().getString());
        stored.put(PET_DATA, data);
        pet.discard();
        player.displayClientMessage(Component.translatable("maid_weapon.pet.stored_message",
                petName(weapon)), true);
        return true;
    }

    public static boolean release(Player player, ItemStack weapon) {
        if (!(player.level() instanceof ServerLevel level)
                || !(weapon.getItem() instanceof PetWeaponItem item)) return false;
        if (!hasStoredPet(weapon)) {
            player.displayClientMessage(Component.translatable("maid_weapon.pet.no_pet"), true);
            return false;
        }
        CompoundTag root = weapon.getTag();
        if (!hasBinding(weapon) || !root.getUUID(OWNER_UUID).equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("maid_weapon.pet.not_owner"), true);
            return false;
        }
        UUID petId = root.getUUID(PET_UUID);
        if (findPet(player, petId) != null) {
            player.displayClientMessage(Component.translatable("maid_weapon.pet.already_out"), true);
            return false;
        }
        TamableAnimal pet = item.kind().type().create(level);
        if (pet == null) return false;
        try {
            pet.load(root.getCompound(PET_DATA).copy());
        } catch (RuntimeException exception) {
            player.displayClientMessage(Component.translatable("maid_weapon.pet.release_failed"), true);
            return false;
        }
        if (!pet.getUUID().equals(petId) || !player.getUUID().equals(pet.getOwnerUUID())) {
            player.displayClientMessage(Component.translatable("maid_weapon.pet.release_failed"), true);
            return false;
        }
        pet.getPersistentData().putString(ENTITY_BINDING, root.getUUID(BINDING).toString());
        if (!placeNearOwner(level, player, pet) || !level.addFreshEntity(pet)) {
            player.displayClientMessage(Component.translatable("maid_weapon.pet.no_space"), true);
            return false;
        }
        root.remove(PET_DATA);
        player.displayClientMessage(Component.translatable("maid_weapon.pet.released_message",
                petName(weapon)), true);
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        recallInventoryPets(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        recallInventoryPets(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPetDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof TamableAnimal pet)
                || !(pet instanceof net.minecraft.world.entity.animal.Cat
                || pet instanceof net.minecraft.world.entity.animal.Wolf)
                || pet.getOwnerUUID() == null || pet.getServer() == null) return;
        ServerPlayer owner = pet.getServer().getPlayerList().getPlayer(pet.getOwnerUUID());
        if (owner == null) return;
        ItemStack weapon = findWeapon(owner, pet.getUUID());
        if (weapon.isEmpty()) return;
        event.setCanceled(true);
        pet.setHealth(1.0f);
        capture(owner, weapon, pet);
    }

    private static void recallInventoryPets(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            recallIfPresent(player, player.getInventory().getItem(i));
        }
        recallIfPresent(player, player.getOffhandItem());
    }

    private static void recallIfPresent(Player player, ItemStack weapon) {
        if (!(weapon.getItem() instanceof PetWeaponItem) || !hasBinding(weapon)
                || hasStoredPet(weapon)) return;
        TamableAnimal pet = findPet(player, weapon.getTag().getUUID(PET_UUID));
        if (pet != null) capture(player, weapon, pet);
    }

    private static ItemStack findWeapon(Player player, UUID petId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof PetWeaponItem && hasBinding(stack)
                    && !hasStoredPet(stack)
                    && stack.getTag().getUUID(PET_UUID).equals(petId)
                    && stack.getTag().getUUID(OWNER_UUID).equals(player.getUUID())) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static TamableAnimal findPet(Player player, UUID petId) {
        if (player.getServer() == null) return null;
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity found = level.getEntity(petId);
            if (found instanceof TamableAnimal pet
                    && player.getUUID().equals(pet.getOwnerUUID())) return pet;
        }
        return null;
    }

    private static boolean validPetData(PetWeaponItem item, Player owner, UUID petId,
                                        CompoundTag data) {
        if (!(owner.level() instanceof ServerLevel level)) return false;
        TamableAnimal check = item.kind().type().create(level);
        if (check == null) return false;
        try {
            check.load(data.copy());
            return petId.equals(check.getUUID())
                    && owner.getUUID().equals(check.getOwnerUUID());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean placeNearOwner(ServerLevel level, Player owner,
                                          LivingEntity pet) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 2; y++) {
                    pet.moveTo(owner.getX() + x + 0.5, owner.getY() + y,
                            owner.getZ() + z + 0.5, owner.getYRot(), 0.0f);
                    var feet = pet.blockPosition();
                    var below = feet.below();
                    if (level.getWorldBorder().isWithinBounds(feet)
                            && level.getBlockState(below).isFaceSturdy(level, below,
                                    net.minecraft.core.Direction.UP)
                            && level.noCollision(pet, pet.getBoundingBox())) return true;
                }
            }
        }
        return false;
    }

    private PetWeaponSystem() { }
}

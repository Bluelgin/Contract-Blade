package com.maidweapon.forge.compat;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.compat.TlmReflection;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.api.event.ContractMaidCapturedEvent;
import com.maidweapon.forge.system.MaidAttentionSystem;
import com.maidweapon.forge.system.MaidCareTaskSystem;
import com.maidweapon.forge.system.ContractNbtGuard;
import com.maidweapon.forge.system.ContractNbtAudit;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * TLM 业务逻辑层 — 女仆捕获/释放的核心操作。
 * 所有 TLM 反射调用委托给 {@link TlmReflection}。
 */
public final class TouhouLittleMaidHelper {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TlmReflection TLM = TlmReflection.INSTANCE;
    private static final String TAG_MAID_ENTITY_DATA = "MaidEntityData";
    private static final String TAG_FILM_PROGRESS = "MaidWeaponProgress";
    private static final String TAG_EMERGENCY_FILM_PROGRESS = "MaidWeaponEmergencyProgress";
    public static final String TAG_ENTITY_BINDING_ID = "MaidWeaponBindingId";
    private static final long NBT_NOTICE_COOLDOWN = 200L;
    private static final Map<Player, Long> LAST_NBT_NOTICE = new WeakHashMap<>();

    /** 检查物品是否为女仆武器（普通版或拔刀剑版） */
    private static boolean isMaidWeaponItem(ItemStack stack) {
        return MaidInfusion.isWeapon(stack);
    }

    // ==================== 女仆检查（委托给 TlmReflection） ====================

    public static boolean isMaidEntity(Entity entity) {
        return TLM.isMaidEntity(entity);
    }

    private static Class<?> findMaidEntityClass() {
        return TLM.getMaidEntityClass();
    }

    private static int getMaidFavorability(Entity maid) {
        return TLM.getMaidFavorability(maid);
    }

    private static void setMaidFavorability(Entity maid, int value) {
        TLM.setMaidFavorability(maid, value);
    }

    // ==================== 直接 API 调用（无需反射） ====================

    private static boolean isOwnedByPlayer(Entity entity, Player player) {
        if (!(entity instanceof TamableAnimal tamable)) return false;
        java.util.UUID ownerUUID = tamable.getOwnerUUID();
        if (ownerUUID == null) return false;
        return ownerUUID.equals(player.getUUID());
    }

    public static boolean isOwnedMaid(Entity entity, Player player) {
        return isMaidEntity(entity) && isOwnedByPlayer(entity, player);
    }

    public static int maidFavorability(Entity entity) {
        return isMaidEntity(entity) ? getMaidFavorability(entity) : 0;
    }

    public static CompoundTag captureMaidAppearance(Entity entity) {
        return TLM.captureAppearance(entity);
    }

    public static CompoundTag captureStoredMaidAppearance(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null) return new CompoundTag();
        if (MaidEntityDataCodec.hasData(root)) {
            try {
                return TLM.extractAppearance(MaidEntityDataCodec.read(root));
            } catch (IOException ignored) {
                return new CompoundTag();
            }
        }
        if (root.contains("MaidInfo")) {
            return TLM.extractAppearance(root.getCompound("MaidInfo"));
        }
        return new CompoundTag();
    }

    private static CompoundTag getStoredMaidData(Player player, ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null) return null;
        try {
            // A successful migration replaces the legacy compound only after the
            // compressed candidate has been decoded and verified. On failure the
            // legacy payload remains untouched and read() applies the same limits.
            if (root.contains(MaidEntityDataCodec.LEGACY_DATA, Tag.TAG_COMPOUND)) {
                MaidEntityDataCodec.migrate(root);
            }
            return MaidEntityDataCodec.read(root);
        } catch (IOException exception) {
            LOGGER.error("[MaidWeapon] Stored maid data failed validation for {}",
                    player.getScoreboardName(), exception);
            return null;
        }
    }

    private static void removeStoredMaidData(Player player, ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null) return;
        MaidEntityDataCodec.remove(root);
        stack.setTag(root);
    }

    public static Entity createMaidAppearanceProxy(net.minecraft.world.level.Level level,
                                                   CompoundTag appearance) {
        return TLM.createAppearanceProxy(level, appearance);
    }

    public static String getMaidTaskId(Entity entity) {
        return TLM.getTaskId(entity);
    }

    public static boolean switchMaidTask(Entity entity, String taskId) {
        return TLM.switchTask(entity, taskId);
    }

    public static boolean playMaidIdleVoice(Entity entity) {
        return TLM.playTaskVoice(entity, "touhou_little_maid:idle");
    }

    /** Keeps an auto-deployed combat maid working during both day and night. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean setAllDaySchedule(Entity entity) {
        try {
            Class<?> schedule = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule");
            Object all = Enum.valueOf((Class<? extends Enum>) schedule.asSubclass(Enum.class), "ALL");
            entity.getClass().getMethod("setSchedule", schedule).invoke(entity, all);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static boolean isMaidFilm(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemId.equals("touhou_little_maid:smart_slab_has_maid")
                || itemId.equals("touhou_little_maid:film");
    }

    public static boolean isFilledMaidFilm(ItemStack stack) {
        return isMaidFilm(stack) && stack.getTag() != null
                && stack.getTag().contains("MaidInfo");
    }

    public static boolean isEmptyMaidFilm(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemId.equals("touhou_little_maid:smart_slab_empty")
                || (itemId.equals("touhou_little_maid:film")
                && (stack.getTag() == null || !stack.getTag().contains("MaidInfo")));
    }

    public static ItemStack createEmptyMaidStoreItem(ItemStack filled) {
        String itemId = BuiltInRegistries.ITEM.getKey(filled.getItem()).toString();
        if (itemId.equals("touhou_little_maid:smart_slab_has_maid")) {
            return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(
                    "touhou_little_maid", "smart_slab_empty")));
        }
        ItemStack empty = filled.copy();
        empty.setCount(1);
        empty.setTag(null);
        return empty;
    }

    /** Transfers a maid stored by TLM's film item into a weapon without spawning her. */
    public static boolean infuseFromFilm(Player player, ItemStack film, ItemStack weaponStack) {
        if (!isFilledMaidFilm(film) || !MaidInfusion.isWeapon(weaponStack)
                || MaidInfusion.isInfused(weaponStack)) return false;
        CompoundTag originalWeaponTag = weaponStack.getTag() == null
                ? null : weaponStack.getTag().copy();
        try {
            Class<?> maidClass = findMaidEntityClass();
            if (maidClass == null) return false;
            Entity maid = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());
            loadEntityNBT(maid, film.getTag().getCompound("MaidInfo").copy());
            if (!isOwnedByPlayer(maid, player)) {
                player.displayClientMessage(
                        Component.translatable("maid_weapon.message.film_not_owner"), true);
                return false;
            }

            String bindingId = MaidWeaponItem.ensureBindingId(weaponStack);
            MaidWeaponItem.setContractSuperseded(weaponStack, false);
            maid.getPersistentData().putString(TAG_ENTITY_BINDING_ID, bindingId);
            MaidWeaponItem.setOwner(weaponStack, player);
            MaidWeaponItem.setBoundMaidUUID(weaponStack, maid.getStringUUID());
            if (film.getTag().contains(TAG_FILM_PROGRESS)) {
                weaponStack.getOrCreateTag().put("MaidData",
                        film.getTag().getCompound(TAG_FILM_PROGRESS).copy());
            } else {
                MaidWeaponData data = new MaidWeaponData(maid.getName().getString());
                data.setFavorability(Math.min(MaidWeaponData.MAX_FAVORABILITY,
                        Math.max(0, getMaidFavorability(maid))));
                MaidWeaponItem.setMaidData(weaponStack, data);
            }
            if (!commitStoredMaidData(player, weaponStack, saveEntityNBT(maid))) {
                weaponStack.setTag(originalWeaponTag);
                return false;
            }
            retirePreviousContracts(player, maid.getStringUUID(), weaponStack);
            return true;
        } catch (Exception e) {
            weaponStack.setTag(originalWeaponTag);
            LOGGER.error("[MaidWeapon] Failed to infuse maid film", e);
            return false;
        }
    }

    /** Moves a stored maid and this mod's progression from an infused weapon back into an empty film. */
    public static ItemStack extractMaidToFilm(Player player, ItemStack weapon, ItemStack emptyFilm) {
        if (!MaidInfusion.containsMaid(weapon) || !isEmptyMaidFilm(emptyFilm)
                || !MaidWeaponItem.isOwner(weapon, player)) return ItemStack.EMPTY;
        try {
            CompoundTag weaponTag = weapon.getTag();
            if (weaponTag == null) return ItemStack.EMPTY;
            CompoundTag maidTag = getStoredMaidData(player, weapon);
            if (maidTag == null) return ItemStack.EMPTY;
            Class<?> maidClass = findMaidEntityClass();
            if (maidClass == null) return ItemStack.EMPTY;
            Entity maid = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());
            loadEntityNBT(maid, maidTag);
            if (!isOwnedByPlayer(maid, player)) return ItemStack.EMPTY;
            maid.getPersistentData().remove(TAG_ENTITY_BINDING_ID);

            String emptyId = BuiltInRegistries.ITEM.getKey(emptyFilm.getItem()).toString();
            ItemStack filled = emptyId.equals("touhou_little_maid:smart_slab_empty")
                    ? new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(
                    "touhou_little_maid", "smart_slab_has_maid")))
                    : emptyFilm.copy();
            filled.setCount(1);
            CompoundTag filledTag = filled.getOrCreateTag();
            filledTag.put("MaidInfo", saveEntityNBT(maid));
            filledTag.put(TAG_FILM_PROGRESS, weaponTag.getCompound("MaidData").copy());
            ContractNbtGuard.Result result = ContractNbtGuard.inspect(filledTag);
            if (result.warning()) notifyNbtSize(player, result, null, true);
            removeStoredMaidData(player, weapon);
            return filled;
        } catch (Exception e) {
            LOGGER.error("[MaidWeapon] Failed to extract maid into film", e);
            return ItemStack.EMPTY;
        }
    }

    /**
     * Creates a TLM-compatible resurrection film when a contract carrier is destroyed.
     * Unlike TLM's normal death film, this deliberately retains the maid inventory,
     * baubles, equipment and experience because no tombstone is produced here.
     * The live entity is never discarded by this method.
     */
    public static ItemStack createEmergencyResurrectionFilm(Player player, ItemStack contract,
                                                            Entity liveMaid) {
        if (contract.isEmpty() || !MaidInfusion.isInfused(contract)
                || !MaidWeaponItem.isOwner(contract, player)) return ItemStack.EMPTY;
        try {
            CompoundTag maidData;
            if (liveMaid != null) {
                if (!isOwnedMaid(liveMaid, player)
                        || !MaidWeaponItem.isBoundMaid(contract, liveMaid)) {
                    return ItemStack.EMPTY;
                }
                MaidCareTaskSystem.restoreOriginalTask(contract, liveMaid);
                if (liveMaid instanceof LivingEntity living) {
                    TripleMagicCompat.clearPhantoms(living, contract);
                    TaczCompat.clear(player, living, contract);
                }
                maidData = saveEntityNBT(liveMaid);
            } else {
                maidData = getStoredMaidData(player, contract);
                if (maidData == null) return ItemStack.EMPTY;
            }

            maidData.putString("id", "touhou_little_maid:maid");
            CompoundTag contractTag = contract.getTag();
            CompoundTag progress = contractTag != null
                    ? contractTag.getCompound("MaidData").copy() : new CompoundTag();
            CompoundTag forgeData = maidData.getCompound("ForgeData");
            forgeData.remove(TAG_ENTITY_BINDING_ID);
            if (!progress.isEmpty()) {
                forgeData.put(TAG_EMERGENCY_FILM_PROGRESS, progress.copy());
            }
            maidData.put("ForgeData", forgeData);

            ItemStack film = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(
                    "touhou_little_maid", "film")));
            if (film.isEmpty()) return ItemStack.EMPTY;
            CompoundTag filmTag = film.getOrCreateTag();
            filmTag.put("MaidInfo", maidData);
            if (!progress.isEmpty()) filmTag.put(TAG_FILM_PROGRESS, progress);

            // Decode the completed payload before allowing the caller to remove the maid.
            Class<?> maidClass = findMaidEntityClass();
            if (maidClass == null) return ItemStack.EMPTY;
            Entity verifier = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());
            loadEntityNBT(verifier, filmTag.getCompound("MaidInfo").copy());
            if (!isOwnedByPlayer(verifier, player)) return ItemStack.EMPTY;

            ContractNbtGuard.Result result = ContractNbtGuard.inspect(filmTag);
            if (result.warning()) notifyNbtSize(player, result, null, true);
            return film;
        } catch (Exception exception) {
            LOGGER.error("[MaidWeapon] Failed to create emergency resurrection film", exception);
            return ItemStack.EMPTY;
        }
    }

    private static CompoundTag saveEntityNBT(Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        sanitizeStoredEntity(tag);
        return tag;
    }

    private static void sanitizeStoredEntity(CompoundTag tag) {
        tag.remove("Pos");
        tag.remove("Motion");
        tag.remove("Rotation");
        tag.remove("FallDistance");
        tag.remove("Fire");
        tag.remove("Air");
        tag.remove("OnGround");
        tag.remove("PortalCooldown");
    }

    /**
     * Atomically compresses and commits serialized maid data. A codec failure leaves
     * both the weapon and the live maid untouched.
     */
    private static boolean commitStoredMaidData(Player player, ItemStack weapon,
                                                CompoundTag maidData) {
        CompoundTag candidate = weapon.getTag() == null
                ? new CompoundTag() : weapon.getTag().copy();
        try {
            MaidEntityDataCodec.write(candidate, maidData);
        } catch (IOException exception) {
            LOGGER.error("[MaidWeapon] Refused to discard maid because entity data could not be encoded", exception);
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.contract_nbt_encode_failed"), false);
            return false;
        }
        weapon.setTag(candidate);
        ContractNbtGuard.Result result = ContractNbtGuard.inspect(candidate);
        if (result.warning()) {
            ContractNbtAudit.Report audit = ContractNbtAudit.inspect(weapon);
            LOGGER.warn("[MaidWeapon] Large contract NBT: item={}, tag={}, maid={}, compressed={}, "
                            + "intrinsicArchive={}, externalArchive={}, duplicateProjection={}",
                    ContractNbtGuard.formatBytes(audit.completeItemBytes()),
                    ContractNbtGuard.formatBytes(audit.itemTagBytes()),
                    ContractNbtGuard.formatBytes(audit.currentMaidBytes()),
                    ContractNbtGuard.formatBytes(audit.currentCompressedBytes()),
                    ContractNbtGuard.formatBytes(audit.intrinsicArchiveBytes()),
                    ContractNbtGuard.formatBytes(audit.externalArchiveBytes()),
                    audit.duplicateProjection());
            notifyNbtSize(player, result, ContractNbtGuard.inspect(maidData), false);
        }
        return true;
    }

    public static boolean setStoredMaidHealth(Player player, ItemStack weapon, float health) {
        CompoundTag root = weapon.getTag();
        if (root == null) return false;
        return MaidEntityDataCodec.update(root, data -> data.putFloat("Health", health));
    }

    private static void notifyNbtSize(Player player, ContractNbtGuard.Result result,
                                      ContractNbtGuard.Result rawMaid, boolean force) {
        long now = player.level().getGameTime();
        if (!force && now - LAST_NBT_NOTICE.getOrDefault(player, Long.MIN_VALUE / 2)
                < NBT_NOTICE_COOLDOWN) return;
        LAST_NBT_NOTICE.put(player, now);

        String level = result.warningLevel();
        player.displayClientMessage(Component.translatable(
                "maid_weapon.message.contract_nbt_" + level,
                ContractNbtGuard.formatBytes(result.bytes())), false);
        if (rawMaid == null) return;
        for (ContractNbtGuard.ItemContribution item : rawMaid.largestItems()) {
            player.displayClientMessage(Component.translatable(
                    "maid_weapon.message.contract_nbt_largest",
                    item.itemId(), ContractNbtGuard.formatBytes(item.bytes()), item.path()), false);
        }
    }

    private static void retirePreviousContracts(Player player, String maidId, ItemStack target) {
        boolean retiredAny = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack != target && maidId.equals(MaidWeaponItem.getBoundMaidUUID(stack))) {
                MaidWeaponItem.setContractSuperseded(stack, true);
                retiredAny = true;
            }
        }
        if (retiredAny) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.contract_transferred"), true);
        }
    }

    private static void loadEntityNBT(Entity entity, CompoundTag tag) {
        entity.load(tag);
    }

    private static void tameMaid(Entity maid, Player player) {
        if (maid instanceof TamableAnimal tamable) {
            tamable.tame(player);
        }
    }

    /** Creates a real, serialized TLM maid contract for an addon-defined spirit. */
    public static boolean createPresetSpiritContract(
            Player player,
            ItemStack weaponStack,
            String spiritId,
            String displayName) {
        if (player == null || player.level().isClientSide || weaponStack.isEmpty()
                || !isMaidWeaponItem(weaponStack)
                || MaidWeaponItem.hasMaidData(weaponStack)) {
            return false;
        }
        CompoundTag originalWeaponTag = weaponStack.getTag() == null
                ? null : weaponStack.getTag().copy();
        try {
            Class<?> maidClass = findMaidEntityClass();
            if (maidClass == null) return false;
            Entity maid = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());
            tameMaid(maid, player);
            maid.setCustomName(Component.literal(displayName));
            maid.setCustomNameVisible(false);
            maid.moveTo(player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());

            String bindingId = MaidWeaponItem.ensureBindingId(weaponStack);
            maid.getPersistentData().putString(TAG_ENTITY_BINDING_ID, bindingId);
            maid.getPersistentData().putString("MaidWeaponEmbeddedSpirit", spiritId);
            MaidWeaponItem.setOwner(weaponStack, player);
            MaidWeaponItem.setBoundMaidUUID(weaponStack, maid.getStringUUID());
            MaidWeaponItem.setContractSuperseded(weaponStack, false);

            MaidWeaponData data = new MaidWeaponData(displayName);
            data.setFavorability(MaidWeaponData.MAX_FAVORABILITY / 2);
            data.setResonance(MaidWeaponData.MAX_RESONANCE);
            MaidWeaponItem.setMaidData(weaponStack, data);
            if (!commitStoredMaidData(player, weaponStack, saveEntityNBT(maid))) {
                weaponStack.setTag(originalWeaponTag);
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            weaponStack.setTag(originalWeaponTag);
            LOGGER.error("[MaidWeapon] Failed to create preset spirit {}", spiritId, exception);
            return false;
        }
    }

    // ==================== 核心业务逻辑 ====================

    public static boolean convertMaidToWeapon(Player player, Entity entity, ItemStack weaponStack) {
        return convertMaidToWeapon(player, entity, weaponStack, true);
    }

    public static boolean convertMaidToWeapon(Player player, Entity entity, ItemStack weaponStack,
                                              boolean notifyPlayer) {
        if (!isMaidEntity(entity)) return false;
        if (weaponStack.isEmpty()) return false;
        if (!isMaidWeaponItem(weaponStack)) return false;
        if (MaidWeaponItem.isContractSuperseded(weaponStack)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.superseded_contract"), true);
            return false;
        }

        CompoundTag rootTag = weaponStack.getTag();
        if (MaidWeaponItem.hasMaidEntityData(weaponStack)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.already_bound"), true);
            return false;
        }

        if (!isOwnedByPlayer(entity, player)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.maid_not_tamed"), true);
            return false;
        }

        if (!MaidWeaponItem.isBoundMaid(weaponStack, entity)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.wrong_maid"), true);
            return false;
        }

        String entityBinding = entity.getPersistentData().getString(TAG_ENTITY_BINDING_ID);
        String originalEntityBinding = entityBinding;
        String weaponBinding = MaidWeaponItem.getBindingId(weaponStack);
        if (!entityBinding.isEmpty() && (weaponBinding == null || !entityBinding.equals(weaponBinding))) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.bound_to_other_weapon"), true);
            return false;
        }

        CompoundTag originalWeaponTag = rootTag == null ? null : rootTag.copy();
        weaponBinding = MaidWeaponItem.ensureBindingId(weaponStack);
        entity.getPersistentData().putString(TAG_ENTITY_BINDING_ID, weaponBinding);

        // Temporary contract tasks must not replace the maid's real work mode
        // in the serialized entity data.
        MaidCareTaskSystem.restoreOriginalTask(weaponStack, entity);
        if (entity instanceof LivingEntity living) {
            // Remove combat projections and equipment phantoms before serializing,
            // otherwise copied owner gear would be stored as the maid's own items.
            TripleMagicCompat.clearPhantoms(living, weaponStack);
            TaczCompat.clear(player, living, weaponStack);
        }
        CompoundTag emergencyProgress = entity.getPersistentData().getCompound(
                TAG_EMERGENCY_FILM_PROGRESS).copy();
        CompoundTag maidEntityTag = saveEntityNBT(entity);
        if (!emergencyProgress.isEmpty()) {
            CompoundTag storedForgeData = maidEntityTag.getCompound("ForgeData");
            storedForgeData.remove(TAG_EMERGENCY_FILM_PROGRESS);
            maidEntityTag.put("ForgeData", storedForgeData);
        }

        boolean isFirstCapture = !MaidWeaponItem.hasMaidData(weaponStack);
        MaidWeaponData data;
        if (isFirstCapture) {
            if (emergencyProgress.isEmpty()) {
                data = new MaidWeaponData("");
            } else {
                ItemStack progressCarrier = new ItemStack(
                        net.minecraft.world.item.Items.STICK);
                progressCarrier.getOrCreateTag().put("MaidData", emergencyProgress.copy());
                data = MaidWeaponItem.getMaidData(progressCarrier);
            }
            MaidWeaponItem.setOwner(weaponStack, player);
            MaidWeaponItem.setBoundMaidUUID(weaponStack, entity.getStringUUID());
        } else {
            data = MaidWeaponItem.getMaidData(weaponStack);
        }
        data.setMaidName(entity.getName().getString());
        data.setFavorability(Math.min(MaidWeaponData.MAX_FAVORABILITY,
                Math.max(0, getMaidFavorability(entity))));
        MaidWeaponItem.setMaidData(weaponStack, data);

        if (!commitStoredMaidData(player, weaponStack, maidEntityTag)) {
            weaponStack.setTag(originalWeaponTag);
            if (originalEntityBinding.isEmpty()) {
                entity.getPersistentData().remove(TAG_ENTITY_BINDING_ID);
            } else {
                entity.getPersistentData().putString(TAG_ENTITY_BINDING_ID, originalEntityBinding);
            }
            return false;
        }

        entity.getPersistentData().remove(TAG_EMERGENCY_FILM_PROGRESS);

        entity.discard();
        MaidCareTaskSystem.clearOriginalTask(weaponStack);

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new ContractMaidCapturedEvent(player, entity, weaponStack, isFirstCapture));

        if (notifyPlayer) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.maid_bound", entity.getName().getString()), true);
        }
        return true;
    }

    public static boolean convertWeaponToMaid(Player player, ItemStack weaponStack) {
        return convertWeaponToMaid(player, weaponStack, true);
    }

    public static boolean convertWeaponToMaid(Player player, ItemStack weaponStack,
                                              boolean notifyPlayer) {
        if (weaponStack.isEmpty()) return false;
        if (!isMaidWeaponItem(weaponStack)) return false;
        if (!MaidWeaponItem.hasMaidData(weaponStack)) return false;
        if (MaidWeaponItem.isContractSuperseded(weaponStack)) return false;

        CompoundTag rootTag = weaponStack.getTag();
        if (!MaidWeaponItem.hasMaidEntityData(weaponStack)) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.no_maid_data"), true);
            return false;
        }

        CompoundTag maidEntityTag = getStoredMaidData(player, weaponStack);
        if (maidEntityTag == null) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.contract_nbt_decode_failed"), false);
            return false;
        }

        try {
            Class<?> maidClass = findMaidEntityClass();
            if (maidClass == null) {
                return false;
            }

            Entity maid = (Entity) maidClass
                    .getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());

            loadEntityNBT(maid, maidEntityTag);
            String bindingId = MaidWeaponItem.ensureBindingId(weaponStack);
            maid.getPersistentData().putString(TAG_ENTITY_BINDING_ID, bindingId);
            maid.setPos(player.getX(), player.getY(), player.getZ());
            tameMaid(maid, player);

            if (!player.level().isClientSide && !player.level().addFreshEntity(maid)) {
                LOGGER.warn("[MaidWeapon] Maid entity {} could not be added; keeping weapon data for retry",
                        maid.getUUID());
                return false;
            }

            prepareManifestedMaid(player, weaponStack, maid);
            removeStoredMaidData(player, weaponStack);

            if (notifyPlayer) {
                player.displayClientMessage(
                        Component.translatable("maid_weapon.message.maid_released",
                                maid.getName().getString()), true);
            }
            MaidAttentionSystem.onManifested(player, weaponStack, maid);
            return true;

        } catch (Exception e) {
            LOGGER.error("[MaidWeapon] Failed to restore maid from stored contract; data was retained", e);
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.contract_nbt_restore_failed"), false);
            return false;
        }
    }

    /**
     * 显形后的统一初始化：普通武器自动显形与契约之刃手动显形共用，
     * 保证两种路径都应用作息、装备幻影、法术配置与 TACZ 投影。
     */
    public static void prepareManifestedMaid(Player player, ItemStack weaponStack, Entity maid) {
        if (!(maid instanceof LivingEntity living)) return;
        MaidCareTaskSystem.rememberOriginalTask(weaponStack, living);
        setAllDaySchedule(living);
        TripleMagicCompat.equipPhantoms(player, living, weaponStack);
        TripleMagicCompat.syncMaidSpellLoadout(player, living, weaponStack);
        TaczCompat.maintain(player, living, weaponStack);
    }

    private static boolean createNewMaidFromWeapon(Player player, ItemStack weaponStack) {
        return createNewMaidFromWeapon(player, weaponStack, true);
    }

    private static boolean createNewMaidFromWeapon(Player player, ItemStack weaponStack,
                                                   boolean notifyPlayer) {
        MaidWeaponData data = MaidWeaponItem.getMaidData(weaponStack);
        if (data == null) return false;
        try {
            Class<?> maidClass = findMaidEntityClass();
            if (maidClass == null) return false;
            Entity maid = (Entity) maidClass.getConstructor(net.minecraft.world.level.Level.class)
                    .newInstance(player.level());
            maid.getPersistentData().putString(TAG_ENTITY_BINDING_ID,
                    MaidWeaponItem.ensureBindingId(weaponStack));
            maid.setPos(player.position());
            maid.setCustomNameVisible(true);
            tameMaid(maid, player);
            setMaidFavorability(maid, data.getFavorability());

            if (!player.level().isClientSide && !player.level().addFreshEntity(maid)) return false;

            CompoundTag rootTag = weaponStack.getTag();
            if (rootTag != null) {
                MaidEntityDataCodec.remove(rootTag);
                weaponStack.setTag(rootTag);
            }

            if (notifyPlayer) {
                player.displayClientMessage(
                        Component.translatable("maid_weapon.message.maid_released", data.getMaidName()), true);
            }
            MaidAttentionSystem.onManifested(player, weaponStack, maid);
            return true;
        } catch (Exception e) {
            System.out.println("[MaidWeapon] Failed to create new maid: " + e.getMessage());
            return false;
        }
    }

    /**
     * While manifested, TLM's maid entity is the sole authority for favorability.
     * The weapon only mirrors that value for tooltips, damage and stored-state use.
     */
    public static void syncFavorabilityFromMaid(Entity maid, ItemStack weaponStack) {
        if (!isMaidEntity(maid) || !MaidWeaponItem.hasMaidData(weaponStack)) return;
        MaidWeaponData data = MaidWeaponItem.getMaidData(weaponStack);
        int actual = Math.min(MaidWeaponData.MAX_FAVORABILITY,
                Math.max(MaidWeaponData.MIN_FAVORABILITY, getMaidFavorability(maid)));
        if (data.getFavorability() == actual) return;
        data.setFavorability(actual);
        MaidWeaponItem.setMaidData(weaponStack, data);
    }

    // ==================== 事件入口 ====================

    public static InteractionResult onPlayerInteractWithMaid(Player player, Entity entity, InteractionHand hand) {
        if (!isMaidEntity(entity)) return InteractionResult.PASS;
        ItemStack heldItem = player.getItemInHand(hand);
        if (!isMaidWeaponItem(heldItem)) return InteractionResult.PASS;
        boolean success = convertMaidToWeapon(player, entity, heldItem);
        return success ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    public static InteractionResult onPlayerShiftRightClick(Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);
        LOGGER.info("[MaidWeapon] onPlayerShiftRightClick called, item={} hasMaidData={} isShift={}",
                heldItem.getItem(), MaidWeaponItem.hasMaidData(heldItem), player.isShiftKeyDown());

        if (!isMaidWeaponItem(heldItem)) return InteractionResult.PASS;
        if (!MaidWeaponItem.hasMaidData(heldItem)) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        if (!MaidWeaponItem.isOwner(heldItem, player)) {
            LOGGER.info("[MaidWeapon] Release blocked: not owner");
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.not_owner"), true);
            return InteractionResult.FAIL;
        }

        CompoundTag tag = heldItem.getTag();
        if (!MaidWeaponItem.hasMaidEntityData(heldItem)) {
            LOGGER.info("[MaidWeapon] Release blocked: no MaidEntityData tag. Tags: {}",
                    tag != null ? tag.getAllKeys() : "null");
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.no_maid_data"), true);
            return InteractionResult.PASS;
        }

        LOGGER.info("[MaidWeapon] Calling convertWeaponToMaid...");
        boolean success = convertWeaponToMaid(player, heldItem);
        LOGGER.info("[MaidWeapon] convertWeaponToMaid result: {}", success);
        return success ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private TouhouLittleMaidHelper() {}
}

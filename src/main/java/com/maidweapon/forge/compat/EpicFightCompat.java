package com.maidweapon.forge.compat;

import com.mojang.logging.LogUtils;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.capabilities.Capability;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;

/** Optional EFTLM bridge: the provider owns combat, animations and learned skills. */
public final class EpicFightCompat {
    public static final String FIGHT_TASK = "ef_tlm:fight_mode_task";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> MELEE_CATEGORIES = Set.of(
            "SWORD", "LONGSWORD", "GREATSWORD", "TACHI", "UCHIGATANA", "SPEAR");
    private static final Set<String> NON_MELEE_CATEGORIES = Set.of(
            "NOT_WEAPON", "NONE", "BOW", "CROSSBOW", "SHIELD", "RANGED",
            "PICKAXE", "SHOVEL", "HOE", "FIST");
    private static volatile boolean lookupDone;
    private static Access access;
    private static boolean reportedFailure;
    // Entity-scoped, never serialized into the maid or carrier. A new manifestation
    // must prove support again; transient hand/style changes cannot revoke it.
    private static final Map<Entity, FightSelection> SELECTIONS = new WeakHashMap<>();

    private record FightSelection(net.minecraft.world.item.Item item, String binding) {
        static FightSelection of(ItemStack source) {
            return new FightSelection(source.getItem(), MaidWeaponItem.getBindingId(source));
        }
    }

    public static void clearSelection(Entity maid) {
        SELECTIONS.remove(maid);
    }

    private record Access(Capability<?> itemCapability, Method weaponCategory,
                          Capability<?> entityCapability, Class<?> patchClass,
                          Method motionBuilder, Object fallbackTools, Object fallbackFist,
                          Method findTask, Class<?> taskClass) { }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("epicfight") && ModList.get().isLoaded("ef_tlm");
    }

    /** Capability-based recognition also admits weapon items lacking vanilla damage modifiers. */
    public static boolean isMeleeWeapon(ItemStack stack) {
        if (stack.isEmpty() || !isLoaded()) return false;
        Access api = resolveAccess();
        if (api == null) return false;
        try {
            Optional<?> optional = stack.getCapability(api.itemCapability).resolve();
            if (optional.isEmpty()) return false;
            String category = api.weaponCategory.invoke(optional.get()).toString()
                    .toUpperCase(java.util.Locale.ROOT);
            if (NON_MELEE_CATEGORIES.contains(category)) return false;
            return MELEE_CATEGORIES.contains(category)
                    || stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                    .containsKey(Attributes.ATTACK_DAMAGE);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            reportFailure(error);
            return false;
        }
    }

    /** Check the real maid's equipped weapon/style and provider event overrides, not just a category. */
    public static boolean usesMaidFightTask(ItemStack source, Entity maid) {
        if (!(maid instanceof LivingEntity living) || source.isEmpty() || !isLoaded()) {
            clearSelection(maid);
            return false;
        }
        Access api = resolveAccess();
        if (api == null) return false;
        try {
            Object task = api.findTask.invoke(null, ResourceLocation.tryParse(FIGHT_TASK));
            if (!(task instanceof Optional<?> optional) || optional.isEmpty()
                    || !api.taskClass.isInstance(optional.get())) {
                clearSelection(maid);
                return false;
            }
            Object patch = maid.getCapability(api.entityCapability).resolve().orElse(null);
            if (!api.patchClass.isInstance(patch)) {
                clearSelection(maid);
                return false;
            }
            FightSelection selection = FightSelection.of(source);
            if (selection.equals(SELECTIONS.get(maid))) return true;
            clearSelection(maid);
            if (!isMeleeWeapon(source)
                    || living.getMainHandItem().getItem() != source.getItem()
                    || !isMeleeWeapon(living.getMainHandItem())) return false;
            Object motions = api.motionBuilder.invoke(patch);
            // Released EFTLM 1.3.4 returns generic tool/fist motions for unsupported
            // items instead of null. Those must not masquerade as supported weapons.
            boolean supported = motions != null && motions != api.fallbackTools && motions != api.fallbackFist;
            if (supported) SELECTIONS.put(maid, selection);
            return supported;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            clearSelection(maid);
            reportFailure(error);
            return false;
        }
    }

    private static Access resolveAccess() {
        if (lookupDone) return access;
        synchronized (EpicFightCompat.class) {
            if (lookupDone) return access;
            try {
                Class<?> capabilities = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities");
                Class<?> item = Class.forName("yesman.epicfight.world.capabilities.item.CapabilityItem");
                Class<?> patch = Class.forName("net.EFTLM.EF.Capability.MaidPatch");
                Class<?> task = Class.forName("net.EFTLM.TLM.Task.FightModeTask");
                Class<?> manager = Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager");
                Class<?> defaults = Class.forName("yesman.epicfight.gameasset.MobCombatBehaviors");
                // EFTLM exposes no public support predicate. Its own resolver accounts for
                // category/style mappings and CombatBehaviorsEvent custom weapon registrations.
                Method motions = patch.getDeclaredMethod("getHoldingItemWeaponMotionBuilder");
                motions.setAccessible(true);
                // Do not reflect EpicFightCapabilities methods: their client-only signatures
                // load LocalPlayer on dedicated servers even when looking up a server method.
                access = new Access((Capability<?>) capabilities.getField("CAPABILITY_ITEM").get(null),
                        item.getMethod("getWeaponCategory"),
                        (Capability<?>) capabilities.getField("CAPABILITY_ENTITY").get(null),
                        patch, motions, defaults.getField("HUMANOID_ONEHAND_TOOLS").get(null),
                        defaults.getField("HUMANOID_FIST").get(null),
                        manager.getMethod("findTask", ResourceLocation.class), task);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                reportFailure(error);
            }
            lookupDone = true;
            return access;
        }
    }

    private static void reportFailure(Throwable error) {
        if (reportedFailure) return;
        reportedFailure = true;
        LOGGER.warn("[MaidWeapon] Epic Fight maid bridge unavailable; keeping ordinary combat", error);
    }

    private EpicFightCompat() { }
}

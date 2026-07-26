package com.maidweapon.forge.compat;

import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Optional SlashBlade bridge without a hard compile-time dependency.
 *
 * Contract data is written beside the blade's own root/capability data. The
 * original ItemStack is mutated in place and ItemStack#copy is used by the
 * binding table, so SlashBlade refine, kill count, proud soul, special attack,
 * model and texture state remain owned by SlashBlade and are preserved.
 */
public final class SlashBladeCompat {
    private static final String MOD_ID = "slashblade";
    private static final String TRUE_POWER_MOD_ID = "true_power_of_maid";
    private static final String TRUE_POWER_TASK_CLASS =
            "net.mrqx.slashblade.maidpower.task.TaskSlashBlade";
    private static final String TRUE_POWER_TASK_FALLBACK =
            "true_power_of_maid:slashblade_attack";
    private static final String PHANTOM_BINDING = "MaidWeaponPhantomBinding";
    private static final String PHANTOM_PROGRESS = "MaidWeaponSlashBladeBaseline";
    private static final String PROUD_SOUL = "ProudSoul";
    private static final String KILL_COUNT = "KillCount";
    private static final String REFINE = "Refine";
    private static final String DAMAGE = "Damage";
    private static final String BROKEN = "Broken";
    private static final String[] ITEM_CLASS_NAMES = {
            "mods.flammpfeil.slashblade.item.ItemSlashBlade"
    };

    private static volatile boolean classLookupDone;
    private static volatile Class<?> slashBladeItemClass;
    private static volatile boolean capabilityLookupDone;
    private static volatile Capability<?> bladeStateCapability;
    private static volatile StateAccess stateAccess;
    private static volatile boolean truePowerTaskLookupDone;
    private static volatile String truePowerTaskId;

    private record StateAccess(Method getProudSoul, Method setProudSoul,
                               Method getKillCount, Method setKillCount,
                               Method getRefine, Method setRefine,
                               Method getDamage, Method setDamage,
                               Method getMaxDamage,
                               Method isBroken, Method setBroken) {
    }

    private record BladeProgress(int proudSoul, int killCount, int refine,
                                 int damage, int maxDamage, boolean broken) {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isTruePowerLoaded() {
        return ModList.get().isLoaded(TRUE_POWER_MOD_ID);
    }

    /** Recognizes both current and older 1.20.x blades while keeping SlashBlade optional. */
    public static boolean isSlashBlade(ItemStack stack) {
        if (stack.isEmpty() || !isLoaded()) return false;
        Class<?> itemClass = resolveItemClass();
        if (itemClass != null && itemClass.isInstance(stack.getItem())) return true;

        // Registry fallback keeps the binding table usable if an older build moves
        // the implementation class but retains SlashBlade's stable namespace.
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return MOD_ID.equals(id.getNamespace());
    }

    /** True POWER is preferred only for an actual SlashBlade contract. */
    public static boolean usesTruePowerTask(ItemStack stack) {
        return isTruePowerLoaded() && isSlashBlade(stack);
    }

    /**
     * Reads the task UID from True POWER itself and falls back to its stable
     * 1.20.1 identifier. Reflection keeps this addon loadable without the mod.
     */
    public static String getTruePowerTaskId() {
        if (!truePowerTaskLookupDone) {
            synchronized (SlashBladeCompat.class) {
                if (!truePowerTaskLookupDone) {
                    try {
                        Class<?> task = Class.forName(TRUE_POWER_TASK_CLASS, false,
                                SlashBladeCompat.class.getClassLoader());
                        Object uid = task.getField("UID").get(null);
                        if (uid instanceof ResourceLocation id) {
                            truePowerTaskId = id.toString();
                        }
                    } catch (ReflectiveOperationException | LinkageError ignored) {
                        // Older builds can still use the stable fallback below.
                    }
                    truePowerTaskLookupDone = true;
                }
            }
        }
        return truePowerTaskId == null ? TRUE_POWER_TASK_FALLBACK : truePowerTaskId;
    }

    /**
     * Marks a fresh SlashBlade phantom with the contract identity and a shared
     * progress baseline. The baseline lets the real blade and phantom both
     * change between syncs without either side overwriting the other.
     */
    public static void initializePhantom(ItemStack original, ItemStack phantom) {
        if (!isSlashBlade(original) || !isSlashBlade(phantom)) return;
        String binding = MaidWeaponItem.getBindingId(original);
        if (binding == null || binding.isEmpty()) return;
        phantom.getOrCreateTag().putString(PHANTOM_BINDING, binding);
        BladeProgress progress = readProgress(phantom);
        if (progress != null) writeBaseline(phantom, progress);
    }

    public static boolean isMatchingPhantom(ItemStack original, ItemStack phantom) {
        if (!isSlashBlade(original) || !isSlashBlade(phantom)
                || phantom.getTag() == null) return false;
        String binding = MaidWeaponItem.getBindingId(original);
        return binding != null && !binding.isEmpty()
                && binding.equals(phantom.getTag().getString(PHANTOM_BINDING));
    }

    /**
     * Merges persistent blade progress from both physical views of one contract.
     *
     * <p>For a field with baseline B, real value R and phantom value P, the
     * merged value is R + P - B. This preserves simultaneous player and maid
     * gains/consumption instead of copying one stale state over the other.</p>
     */
    public static boolean syncPhantomProgress(ItemStack original, ItemStack phantom) {
        if (!isMatchingPhantom(original, phantom)) return false;
        BladeProgress real = readProgress(original);
        BladeProgress projected = readProgress(phantom);
        if (real == null || projected == null) return false;

        CompoundTag root = phantom.getOrCreateTag();
        BladeProgress baseline = root.contains(PHANTOM_PROGRESS)
                ? readBaseline(root.getCompound(PHANTOM_PROGRESS), projected.maxDamage())
                : real;

        int proudSoul = mergeNonNegative(real.proudSoul(), projected.proudSoul(),
                baseline.proudSoul(), Integer.MAX_VALUE);
        int killCount = mergeNonNegative(real.killCount(), projected.killCount(),
                baseline.killCount(), Integer.MAX_VALUE);
        int refine = mergeNonNegative(real.refine(), projected.refine(),
                baseline.refine(), Integer.MAX_VALUE);
        int maxDamage = Math.max(0, real.maxDamage());
        int damage = mergeNonNegative(real.damage(), projected.damage(),
                baseline.damage(), maxDamage <= 0 ? Integer.MAX_VALUE : maxDamage);

        boolean phantomBrokenChanged = projected.broken() != baseline.broken();
        boolean broken = phantomBrokenChanged ? projected.broken() : real.broken();
        BladeProgress merged = new BladeProgress(
                proudSoul, killCount, refine, damage, maxDamage, broken);

        if (!writeProgress(original, merged) || !writeProgress(phantom, merged)) return false;
        writeBaseline(phantom, merged);
        return true;
    }

    public static String diagnostics() {
        if (!isLoaded()) return "not loaded; optional registry bridge ready";
        Class<?> itemClass = resolveItemClass();
        String blade = itemClass == null
                ? "loaded; registry fallback active"
                : "loaded; ItemSlashBlade bridge active (" + itemClass.getName() + ")";
        return blade + "; BladeState capability="
                + (resolveBladeStateCapability() == null ? "unavailable" : "ready")
                + "; progress sync=" + progressSyncDiagnostics()
                + "; True POWER=" + (isTruePowerLoaded()
                ? "loaded, task " + getTruePowerTaskId()
                : "not loaded");
    }

    private static Class<?> resolveItemClass() {
        if (classLookupDone) return slashBladeItemClass;
        synchronized (SlashBladeCompat.class) {
            if (classLookupDone) return slashBladeItemClass;
            for (String name : ITEM_CLASS_NAMES) {
                try {
                    slashBladeItemClass = Class.forName(name, false,
                            SlashBladeCompat.class.getClassLoader());
                    break;
                } catch (ClassNotFoundException | LinkageError ignored) {
                    // Try the stable registry namespace below.
                }
            }
            classLookupDone = true;
            return slashBladeItemClass;
        }
    }

    private static Object resolveBladeState(ItemStack stack) {
        Capability<?> capability = resolveBladeStateCapability();
        if (capability == null) return null;
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object state = stack.getCapability((Capability) capability).resolve().orElse(null);
            return state;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Capability<?> resolveBladeStateCapability() {
        if (capabilityLookupDone) return bladeStateCapability;
        synchronized (SlashBladeCompat.class) {
            if (capabilityLookupDone) return bladeStateCapability;
            Class<?> itemClass = resolveItemClass();
            if (itemClass != null) {
                try {
                    Object value = itemClass.getField("BLADESTATE").get(null);
                    if (value instanceof Capability<?> capability) {
                        bladeStateCapability = capability;
                    }
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // Registry recognition remains available without progress sync.
                }
            }
            capabilityLookupDone = true;
            return bladeStateCapability;
        }
    }

    private static BladeProgress readProgress(ItemStack stack) {
        Object state = resolveBladeState(stack);
        if (state == null) return null;
        StateAccess access = resolveStateAccess(state.getClass());
        if (access == null) return null;
        try {
            return new BladeProgress(
                    ((Number) access.getProudSoul().invoke(state)).intValue(),
                    ((Number) access.getKillCount().invoke(state)).intValue(),
                    ((Number) access.getRefine().invoke(state)).intValue(),
                    ((Number) access.getDamage().invoke(state)).intValue(),
                    ((Number) access.getMaxDamage().invoke(state)).intValue(),
                    (Boolean) access.isBroken().invoke(state));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean writeProgress(ItemStack stack, BladeProgress progress) {
        Object state = resolveBladeState(stack);
        if (state == null) return false;
        StateAccess access = resolveStateAccess(state.getClass());
        if (access == null) return false;
        try {
            access.setProudSoul().invoke(state, progress.proudSoul());
            access.setKillCount().invoke(state, progress.killCount());
            access.setRefine().invoke(state, progress.refine());
            access.setDamage().invoke(state, progress.damage());
            access.setBroken().invoke(state, progress.broken());
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static StateAccess resolveStateAccess(Class<?> stateClass) {
        StateAccess cached = stateAccess;
        if (cached != null
                && cached.getProudSoul().getDeclaringClass().isAssignableFrom(stateClass)) {
            return cached;
        }
        synchronized (SlashBladeCompat.class) {
            cached = stateAccess;
            if (cached != null
                    && cached.getProudSoul().getDeclaringClass().isAssignableFrom(stateClass)) {
                return cached;
            }
            try {
                stateAccess = new StateAccess(
                        stateClass.getMethod("getProudSoulCount"),
                        stateClass.getMethod("setProudSoulCount", int.class),
                        stateClass.getMethod("getKillCount"),
                        stateClass.getMethod("setKillCount", int.class),
                        stateClass.getMethod("getRefine"),
                        stateClass.getMethod("setRefine", int.class),
                        stateClass.getMethod("getDamage"),
                        stateClass.getMethod("setDamage", int.class),
                        stateClass.getMethod("getMaxDamage"),
                        stateClass.getMethod("isBroken"),
                        stateClass.getMethod("setBroken", boolean.class));
                return stateAccess;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }

    private static void writeBaseline(ItemStack phantom, BladeProgress progress) {
        CompoundTag baseline = new CompoundTag();
        baseline.putInt(PROUD_SOUL, progress.proudSoul());
        baseline.putInt(KILL_COUNT, progress.killCount());
        baseline.putInt(REFINE, progress.refine());
        baseline.putInt(DAMAGE, progress.damage());
        baseline.putBoolean(BROKEN, progress.broken());
        phantom.getOrCreateTag().put(PHANTOM_PROGRESS, baseline);
    }

    private static BladeProgress readBaseline(CompoundTag tag, int maxDamage) {
        return new BladeProgress(
                Math.max(0, tag.getInt(PROUD_SOUL)),
                Math.max(0, tag.getInt(KILL_COUNT)),
                Math.max(0, tag.getInt(REFINE)),
                Math.max(0, tag.getInt(DAMAGE)),
                Math.max(0, maxDamage),
                tag.getBoolean(BROKEN));
    }

    private static int mergeNonNegative(int real, int phantom, int baseline, int maximum) {
        long merged = (long) real + phantom - baseline;
        return (int) Math.max(0L, Math.min((long) maximum, merged));
    }

    private static String progressSyncDiagnostics() {
        try {
            return BuiltInRegistries.ITEM.stream()
                    .map(ItemStack::new)
                    .filter(SlashBladeCompat::isSlashBlade)
                    .map(SlashBladeCompat::readProgress)
                    .anyMatch(progress -> progress != null) ? "ready" : "unavailable";
        } catch (RuntimeException | LinkageError ignored) {
            return "unavailable";
        }
    }

    private SlashBladeCompat() { }
}

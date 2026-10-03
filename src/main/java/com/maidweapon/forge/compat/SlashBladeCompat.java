package com.maidweapon.forge.compat;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;

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
    private enum MaidTaskProvider {
        TRUE_POWER(
                "true_power_of_maid",
                "net.mrqx.slashblade.maidpower.task.TaskSlashBlade",
                "true_power_of_maid:slashblade_attack",
                "TLM: True POWER"),
        NATIVE_POWER(
                "native_power_of_maid",
                "net.jfrx.slashblade.maidnativepower.task.TaskSlashBlade",
                "native_power_of_maid:slashblade_attack",
                "TLM: Native POWER");

        private final String modId;
        private final String taskClass;
        private final String fallbackTaskId;
        private final String displayName;
        private volatile boolean lookupDone;
        private volatile String taskId;

        MaidTaskProvider(String modId, String taskClass, String fallbackTaskId,
                         String displayName) {
            this.modId = modId;
            this.taskClass = taskClass;
            this.fallbackTaskId = fallbackTaskId;
            this.displayName = displayName;
        }
    }
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
    private static volatile MaidTaskProvider selectedTaskProvider;

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
        return providerLoaded(MaidTaskProvider.TRUE_POWER);
    }

    public static boolean isNativePowerLoaded() {
        return providerLoaded(MaidTaskProvider.NATIVE_POWER);
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

    /** Legacy compatibility predicate retained for addons compiled against beta builds. */
    @Deprecated
    public static boolean usesTruePowerTask(ItemStack stack) {
        return isTruePowerLoaded() && isSlashBlade(stack)
                && taskRegistered(resolveTaskId(MaidTaskProvider.TRUE_POWER));
    }

    /** Legacy mainline task lookup retained for binary/source compatibility. */
    @Deprecated
    public static String getTruePowerTaskId() {
        return resolveTaskId(MaidTaskProvider.TRUE_POWER);
    }

    /** Selects a registered SlashBlade maid task from either supported branch. */
    public static boolean usesMaidSlashBladeTask(ItemStack stack) {
        return isSlashBlade(stack) && activeTaskProvider() != null;
    }

    public static String getMaidSlashBladeTaskId() {
        MaidTaskProvider provider = activeTaskProvider();
        return provider == null ? "" : resolveTaskId(provider);
    }

    public static String getMaidSlashBladeProviderName() {
        MaidTaskProvider provider = activeTaskProvider();
        return provider == null ? "SlashBlade" : provider.displayName;
    }

    private static MaidTaskProvider activeTaskProvider() {
        MaidTaskProvider selected = selectedTaskProvider;
        if (selected != null) return selected;
        // Mainline wins if a broken pack installs both mutually exclusive branches.
        for (MaidTaskProvider provider : MaidTaskProvider.values()) {
            if (providerLoaded(provider) && taskRegistered(resolveTaskId(provider))) {
                selectedTaskProvider = provider;
                return provider;
            }
        }
        return null;
    }

    private static boolean providerLoaded(MaidTaskProvider provider) {
        return ModList.get().isLoaded(provider.modId);
    }

    private static String resolveTaskId(MaidTaskProvider provider) {
        if (!provider.lookupDone) {
            synchronized (provider) {
                if (!provider.lookupDone) {
                    try {
                        Class<?> task = Class.forName(provider.taskClass, false,
                                SlashBladeCompat.class.getClassLoader());
                        Object uid = task.getField("UID").get(null);
                        if (uid instanceof ResourceLocation id) provider.taskId = id.toString();
                    } catch (ReflectiveOperationException | LinkageError ignored) {
                        // Stable 1.20.1 IDs remain available if an implementation moves classes.
                    }
                    provider.lookupDone = true;
                }
            }
        }
        return provider.taskId == null ? provider.fallbackTaskId : provider.taskId;
    }

    private static boolean taskRegistered(String taskId) {
        try {
            ResourceLocation id = ResourceLocation.tryParse(taskId);
            if (id == null) return false;
            Class<?> manager = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager");
            Object result = manager.getMethod("findTask", ResourceLocation.class)
                    .invoke(null, id);
            return result instanceof Optional<?> optional && optional.isPresent();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * Marks a fresh SlashBlade phantom with the contract identity and a shared
     * progress baseline. The baseline lets the real blade and phantom both
     * change between syncs without either side overwriting the other.
     */
    public static void initializePhantom(ItemStack original, ItemStack phantom) {
        if (!isSlashBlade(original) || !isSlashBlade(phantom)) return;
        String binding = ContractCarrierData.getBindingId(original);
        if (binding == null || binding.isEmpty()) return;
        phantom.getOrCreateTag().putString(PHANTOM_BINDING, binding);
        BladeProgress progress = readProgress(phantom);
        if (progress != null) writeBaseline(phantom, progress);
    }

    public static boolean isMatchingPhantom(ItemStack original, ItemStack phantom) {
        if (!isSlashBlade(original) || !isSlashBlade(phantom)
                || phantom.getTag() == null) return false;
        String binding = ContractCarrierData.getBindingId(original);
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
                + "; maid task provider=" + (activeTaskProvider() == null
                ? "none (True POWER=" + isTruePowerLoaded()
                        + ", Native POWER=" + isNativePowerLoaded() + ")"
                : getMaidSlashBladeProviderName() + ", task " + getMaidSlashBladeTaskId());
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

    /** Reads the named blade's state identity, never its player-editable display name. */
    public static boolean isNamedBlade(ItemStack stack, String translationKey) {
        if (!isSlashBlade(stack)) return false;
        Object state = resolveBladeState(stack);
        if (state == null) return false;
        try {
            return translationKey.equals(state.getClass().getMethod("getTranslationKey").invoke(state));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** Read-only progress access; never count contract projections as additional blades. */
    public static int getKillCount(ItemStack stack) {
        if (!isSlashBlade(stack) || stack.hasTag() && stack.getTag().contains(PHANTOM_BINDING)) return 0;
        Object state = resolveBladeState(stack);
        if (state == null) return 0;
        try {
            return Math.max(0, ((Number) state.getClass().getMethod("getKillCount").invoke(state)).intValue());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
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

package com.extendedae_plus.compat;

import appeng.api.upgrades.IUpgradeInventory;
import com.extendedae_plus.init.ModItems;
import com.glodblock.github.extendedae.common.parts.PartExPatternProvider;
import com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 统一管理样板供应器升级槽与扩展样板供应器的扩容页逻辑。
 */
public final class UpgradeSlotCompat {
    private static final String APPFLUX_MOD_ID = "appflux";
    private static final int BASE_PATTERN_PROVIDER_UPGRADE_SLOTS = 2;
    private static final int EXTENDED_PATTERN_PROVIDER_EXTRA_UPGRADE_SLOTS = 3;
    private static final int EXTENDED_PATTERN_PROVIDER_BASE_PAGES = 1;
    private static final int EXTENDED_PATTERN_PROVIDER_SLOTS_PER_PAGE = 36;
    private static final String[] APPFLUX_UPGRADES_FIELD_NAMES = { "af_upgrades", "af_$upgrades" };
    private static final String[] APPFLUX_UPGRADES_CHANGED_METHOD_NAMES = { "af_onUpgradesChanged", "af_$onUpgradesChanged" };

    private static Field patternProviderAppfluxUpgradesField;
    private static boolean patternProviderAppfluxUpgradesFieldResolved;
    private static Method patternProviderAppfluxUpgradesChangedMethod;
    private static boolean patternProviderAppfluxUpgradesChangedMethodResolved;

    private UpgradeSlotCompat() {
    }

    public static boolean isAppfluxPresent() {
        return ModList.get().isLoaded(APPFLUX_MOD_ID);
    }

    public static boolean usesDedicatedUpgradeSlots() {
        return !isAppfluxPresent();
    }

    public static boolean usesAppfluxUpgradeSlots() {
        return isAppfluxPresent();
    }

    public static boolean shouldEnableUpgradeSlots() {
        return usesDedicatedUpgradeSlots();
    }

    public static boolean shouldManageLocalUpgradeInventory() {
        return usesDedicatedUpgradeSlots();
    }

    public static boolean shouldEnableChannelCard() {
        return true;
    }

    public static boolean shouldListenToAppfluxUpgrades() {
        return usesAppfluxUpgradeSlots();
    }

    public static boolean shouldAddUpgradePanelToScreen() {
        return usesDedicatedUpgradeSlots();
    }

    public static int getPatternProviderLocalUpgradeSlots() {
        return getPatternProviderLocalUpgradeSlots(null);
    }

    public static int getPatternProviderLocalUpgradeSlots(Object host) {
        return BASE_PATTERN_PROVIDER_UPGRADE_SLOTS
                + (isExtendedPatternProviderHost(host) ? EXTENDED_PATTERN_PROVIDER_EXTRA_UPGRADE_SLOTS : 0);
    }

    public static int getPatternProviderAppfluxUpgradeSlots() {
        return getPatternProviderAppfluxUpgradeSlots(null);
    }

    public static int getPatternProviderAppfluxUpgradeSlots(Object host) {
        return BASE_PATTERN_PROVIDER_UPGRADE_SLOTS
                + (isExtendedPatternProviderHost(host) ? EXTENDED_PATTERN_PROVIDER_EXTRA_UPGRADE_SLOTS : 0);
    }

    public static boolean isExtendedPatternProviderHost(Object host) {
        return host instanceof TileExPatternProvider || host instanceof PartExPatternProvider;
    }

    public static int getExtendedPatternProviderPatternCapacity() {
        return getExtendedPatternProviderTotalPages() * EXTENDED_PATTERN_PROVIDER_SLOTS_PER_PAGE;
    }

    public static int getExtendedPatternProviderTotalPages() {
        return EXTENDED_PATTERN_PROVIDER_BASE_PAGES + EXTENDED_PATTERN_PROVIDER_EXTRA_UPGRADE_SLOTS;
    }

    public static int getUnlockedExtendedPatternProviderPages(Iterable<ItemStack> upgrades) {
        int expansionCards = 0;
        if (upgrades != null) {
            for (ItemStack stack : upgrades) {
                if (isExtendedPatternProviderExpansionCard(stack)) {
                    expansionCards++;
                }
            }
        }

        expansionCards = Math.min(expansionCards, EXTENDED_PATTERN_PROVIDER_EXTRA_UPGRADE_SLOTS);
        return EXTENDED_PATTERN_PROVIDER_BASE_PAGES + expansionCards;
    }

    public static int getUnlockedExtendedPatternProviderSlots(Iterable<ItemStack> upgrades) {
        return getUnlockedExtendedPatternProviderPages(upgrades) * EXTENDED_PATTERN_PROVIDER_SLOTS_PER_PAGE;
    }

    public static boolean isExtendedPatternProviderExpansionCard(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getItem() == ModItems.EXTENDED_PATTERN_PROVIDER_EXPANSION_CARD_PLUS.get();
    }

    public static IUpgradeInventory getPatternProviderAppfluxUpgrades(Object logicInstance) {
        Field field = resolvePatternProviderAppfluxUpgradesField(logicInstance.getClass());
        if (field == null) {
            return null;
        }

        try {
            Object value = field.get(logicInstance);
            return value instanceof IUpgradeInventory inventory ? inventory : null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    public static boolean setPatternProviderAppfluxUpgrades(Object logicInstance, IUpgradeInventory inventory) {
        Field field = resolvePatternProviderAppfluxUpgradesField(logicInstance.getClass());
        if (field == null) {
            return false;
        }

        try {
            field.set(logicInstance, inventory);
            return true;
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    public static boolean invokePatternProviderAppfluxUpgradesChanged(Object logicInstance) throws ReflectiveOperationException {
        Method method = resolvePatternProviderAppfluxUpgradesChangedMethod(logicInstance.getClass());
        if (method == null) {
            return false;
        }

        method.invoke(logicInstance);
        return true;
    }

    private static Field resolvePatternProviderAppfluxUpgradesField(Class<?> logicClass) {
        if (!patternProviderAppfluxUpgradesFieldResolved) {
            patternProviderAppfluxUpgradesField = findField(logicClass, APPFLUX_UPGRADES_FIELD_NAMES);
            patternProviderAppfluxUpgradesFieldResolved = true;
        }
        return patternProviderAppfluxUpgradesField;
    }

    private static Method resolvePatternProviderAppfluxUpgradesChangedMethod(Class<?> logicClass) {
        if (!patternProviderAppfluxUpgradesChangedMethodResolved) {
            patternProviderAppfluxUpgradesChangedMethod = findMethod(logicClass, APPFLUX_UPGRADES_CHANGED_METHOD_NAMES);
            patternProviderAppfluxUpgradesChangedMethodResolved = true;
        }
        return patternProviderAppfluxUpgradesChangedMethod;
    }

    private static Field findField(Class<?> owner, String[] candidates) {
        Class<?> clazz = owner;
        while (clazz != null && clazz != Object.class) {
            for (String candidate : candidates) {
                try {
                    Field field = clazz.getDeclaredField(candidate);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> owner, String[] candidates) {
        for (String candidate : candidates) {
            try {
                Method method = owner.getDeclaredMethod(candidate);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}

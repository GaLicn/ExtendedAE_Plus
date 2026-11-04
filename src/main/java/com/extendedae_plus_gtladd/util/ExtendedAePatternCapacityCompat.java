package com.extendedae_plus_gtladd.util;

import appeng.api.upgrades.IUpgradeableObject;
import com.extendedae_plus.api.bridge.PatternProviderPageUnlockBridge;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.init.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.gtlcore.gtlcore.config.ConfigHolder;

/**
 * Coordinates the EAEP expansion-card limit with GTLCore's pattern capacity.
 *
 * <p>GTLCore supplies the base per-provider capacity; every EAEP expansion
 * card adds one more multiple of it. With three cards installed the capacity
 * is {@code gtlcore * 4}, which is also the backing inventory size.</p>
 */
public final class ExtendedAePatternCapacityCompat {
    private static final String GTLCORE_MOD_ID = "gtlcore";
    private static final int MAX_EXPANSION_CARDS = 3;

    private ExtendedAePatternCapacityCompat() {
    }

    public static boolean isGtlcoreLoaded() {
        return ModList.get().isLoaded(GTLCORE_MOD_ID);
    }

    /**
     * The base capacity GTLCore configured for one extended pattern provider.
     */
    public static int getGtlcoreBasePatternSlots() {
        if (!isGtlcoreLoaded()) {
            return -1;
        }

        try {
            return Math.max(0, ConfigHolder.INSTANCE.machines.exPatternProvider);
        } catch (Throwable ignored) {
            // The configuration may not be initialised yet.
            return -1;
        }
    }

    /**
     * Number of EAEP expansion cards, clamped to the three supported slots.
     */
    public static int getExpansionCardCount(Object provider) {
        if (!(provider instanceof PatternProviderPageUnlockBridge pageUnlock)
                || !pageUnlock.eap$isExtendedPatternProviderHost()
                || !(provider instanceof IUpgradeableObject upgradeable)) {
            return -1;
        }

        int cards = 0;
        for (ItemStack stack : upgradeable.getUpgrades()) {
            if (stack != null && !stack.isEmpty() && stack.getItem() == ModItems.EXTENDED_PATTERN_PROVIDER_EXPANSION_CARD_PLUS.get()) {
                cards++;
            }
        }

        return Math.min(cards, MAX_EXPANSION_CARDS);
    }

    /**
     * GTLCore capacity multiplied by {@code 1 + cards}: 1x, 2x, 3x, 4x.
     */
    public static int getGtlcoreScaledPatternSlots(int baseSlots, int cards) {
        int clampedCards = Math.max(0, Math.min(cards, MAX_EXPANSION_CARDS));
        long capacity = (long) Math.max(0, baseSlots) * (1L + clampedCards);
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    /**
     * Returns the number of usable pattern slots, or {@code -1} for a
     * provider that is not an EAEP extended pattern provider.
     *
     * <p>Without GTLCore this is EAEP's original card behaviour. With
     * GTLCore installed the configured value becomes the base and each card
     * adds one multiple of it.</p>
     */
    public static int getCardUnlockedPatternSlots(Object provider) {
        int cards = getExpansionCardCount(provider);
        if (cards < 0) {
            return -1;
        }

        int baseSlots = getGtlcoreBasePatternSlots();
        if (baseSlots < 0) {
            return UpgradeSlotCompat.getUnlockedExtendedPatternProviderSlots(
                    ((IUpgradeableObject) provider).getUpgrades());
        }

        return getGtlcoreScaledPatternSlots(baseSlots, cards);
    }

    public static int getCardUnlockedPatternPages(Object provider) {
        int slots = getCardUnlockedPatternSlots(provider);
        if (slots < 0) {
            return -1;
        }

        return Math.max(1, (slots + 35) / 36);
    }

    /**
     * GTLCore now always takes priority over EAEP's own page limit.
     */
    public static boolean shouldUseGtlcoreCapacity(Object provider) {
        return isGtlcoreLoaded() && getCardUnlockedPatternSlots(provider) >= 0;
    }

    /**
     * The backing inventory must hold the largest multiple so cards can be
     * added or removed without recreating the logic.
     */
    public static int getGtlcoreBackingPatternCapacity(int configuredSlots) {
        if (!isGtlcoreLoaded()) {
            return configuredSlots;
        }

        return getGtlcoreScaledPatternSlots(configuredSlots, MAX_EXPANSION_CARDS);
    }
}

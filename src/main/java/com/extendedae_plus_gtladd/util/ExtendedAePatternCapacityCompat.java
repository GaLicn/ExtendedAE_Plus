package com.extendedae_plus_gtladd.util;

import appeng.api.upgrades.IUpgradeableObject;
import com.extendedae_plus.api.bridge.PatternProviderPageUnlockBridge;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import net.minecraftforge.fml.ModList;

/**
 * Coordinates the EAEP expansion-card limit with GTLCore's pattern capacity.
 */
public final class ExtendedAePatternCapacityCompat {
    private static final String GTLCORE_MOD_ID = "gtlcore";

    private ExtendedAePatternCapacityCompat() {
    }

    public static boolean isGtlcoreLoaded() {
        return ModList.get().isLoaded(GTLCORE_MOD_ID);
    }

    /**
     * Returns the number of slots unlocked by EAEP cards, or {@code -1} for a
     * provider that is not an EAEP extended pattern provider.
     */
    public static int getCardUnlockedPatternSlots(Object provider) {
        if (!(provider instanceof PatternProviderPageUnlockBridge pageUnlock)
                || !pageUnlock.eap$isExtendedPatternProviderHost()
                || !(provider instanceof IUpgradeableObject upgradeable)) {
            return -1;
        }

        return UpgradeSlotCompat.getUnlockedExtendedPatternProviderSlots(upgradeable.getUpgrades());
    }

    public static int getCardUnlockedPatternPages(Object provider) {
        int slots = getCardUnlockedPatternSlots(provider);
        if (slots < 0) {
            return -1;
        }

        return Math.max(1, (slots + 35) / 36);
    }

    /**
     * GTLCore may replace EAEP's page limit only after all EAEP expansion
     * cards have been installed.
     */
    public static boolean shouldUseGtlcoreCapacity(Object provider) {
        if (!isGtlcoreLoaded()) {
            return false;
        }

        int cardUnlockedSlots = getCardUnlockedPatternSlots(provider);
        return cardUnlockedSlots >= UpgradeSlotCompat.getExtendedPatternProviderPatternCapacity();
    }

    /**
     * GTLCore's value is EAEP's original one-page slot count. EAEP reserves
     * four pages for its three expansion cards, so the backing inventory must
     * keep that multiplier when GTLCore changes the value.
     */
    public static int getGtlcoreBackingPatternCapacity(int configuredSlots) {
        long capacity = (long) Math.max(0, configuredSlots)
                * UpgradeSlotCompat.getExtendedPatternProviderTotalPages();
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }
}

package com.extendedae_plus_gtladd.mixin.compat.extendedae;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import com.bawnorton.mixinsquared.TargetHandler;
import com.extendedae_plus.api.IExPatternPage;
import com.extendedae_plus.api.bridge.ExPatternProviderMenuPageBridge;
import com.extendedae_plus.mixin.ae2.accessor.PatternProviderMenuAccessor;
import com.extendedae_plus_gtladd.util.ExtendedAePatternCapacityCompat;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * Synchronizes the two page counters used by ExtendedAE Plus and GTLCore.
 *
 * <p>Both mods add their pagination to {@link AEBaseScreen}. The GTLCore page
 * field is accessed reflectively, while its merged handlers are targeted
 * through MixinSquared so this mixin remains harmless when GTLCore is not
 * installed.</p>
 */
@Mixin(value = AEBaseScreen.class, priority = 4000)
public abstract class ExPatternProviderPaginationCompatMixin {

    private static final String GTLCORE_PAGE_FIELD = "gtlcore$exPatternProviderPage";

    @Inject(method = "repositionSlots", at = @At("HEAD"), remap = false)
    private void eap$syncGtlcorePageFromExtendedAe(SlotSemantic semantic, CallbackInfo ci) {
        if (semantic != SlotSemantics.ENCODED_PATTERN) {
            return;
        }

        Object screen = this;
        if (!(screen instanceof GuiExPatternProvider) || !(screen instanceof IExPatternPage page)) {
            return;
        }

        eap$writeGtlcorePage(screen, Math.max(0, page.eap$getCurrentPage()));
    }

    /**
     * GTLCore uses the number of menu slots for its page count. EAEP may have
     * created those slots in advance, so expose only EAEP's currently
     * available pages to GTLCore until all expansion cards are installed.
     */
    @TargetHandler(
            mixin = "org.gtlcore.gtlcore.mixin.extendedae.ExPatternProviderScreenMixin",
            name = "gtlcore$getExPatternProviderMaxPage"
    )
    @Inject(
            method = "@MixinSquared:Handler",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void eap$limitGtlcoreMaxPage(CallbackInfoReturnable<Integer> cir) {
        Object screenObject = this;
        if (!(screenObject instanceof GuiExPatternProvider)
                || !(screenObject instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof ExPatternProviderMenuPageBridge bridge)) {
            return;
        }

        int cardPageLimit = eap$getCardPageLimit(screen);
        if (cardPageLimit >= 0
                && !(screen.getMenu() instanceof PatternProviderMenuAccessor accessor
                && ExtendedAePatternCapacityCompat.shouldUseGtlcoreCapacity(accessor.eap$logic()))) {
            cir.setReturnValue(cardPageLimit);
        } else {
            cir.setReturnValue(Math.max(1, bridge.eap$getAvailablePageCount()));
        }
    }

    @TargetHandler(
            mixin = "org.gtlcore.gtlcore.mixin.extendedae.ExPatternProviderScreenMixin",
            name = "gtlcore$applyExPatternProviderPage"
    )
    @Inject(
            method = "@MixinSquared:Handler",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void eap$syncExtendedAePageFromGtlcore(CallbackInfo ci) {
        Object screenObject = this;
        if (!(screenObject instanceof GuiExPatternProvider)
                || !(screenObject instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof ExPatternProviderMenuPageBridge bridge)) {
            return;
        }

        Integer page = eap$readGtlcorePage(screenObject);
        if (page == null) {
            return;
        }

        int maxPage = eap$getEffectiveMaxPage(screen, bridge);
        int clampedPage = Math.max(0, Math.min(page, maxPage - 1));
        if (clampedPage != page) {
            eap$writeGtlcorePage(screenObject, clampedPage);
        }
        bridge.eap$setPage(clampedPage);
    }

    private static int eap$getEffectiveMaxPage(AbstractContainerScreen<?> screen,
                                                ExPatternProviderMenuPageBridge bridge) {
        int cardPageLimit = eap$getCardPageLimit(screen);
        if (cardPageLimit >= 0
                && (!(screen.getMenu() instanceof PatternProviderMenuAccessor accessor)
                || !ExtendedAePatternCapacityCompat.shouldUseGtlcoreCapacity(accessor.eap$logic()))) {
            return cardPageLimit;
        }

        return Math.max(1, bridge.eap$getAvailablePageCount());
    }

    private static int eap$getCardPageLimit(AbstractContainerScreen<?> screen) {
        if (!(screen.getMenu() instanceof PatternProviderMenuAccessor accessor)) {
            return -1;
        }

        int cardPages = ExtendedAePatternCapacityCompat.getCardUnlockedPatternPages(accessor.eap$logic());
        if (cardPages < 0) {
            return -1;
        }

        if (!(screen.getMenu() instanceof ContainerExPatternProvider container)) {
            return -1;
        }

        int totalSlots = container.getSlots(SlotSemantics.ENCODED_PATTERN).size();
        int totalPages = Math.max(1, (totalSlots + 35) / 36);
        return Math.min(totalPages, cardPages);
    }

    private static void eap$writeGtlcorePage(Object screen, int page) {
        Field field = eap$findGtlcorePageField(screen.getClass());
        if (field == null) {
            return;
        }

        try {
            field.setInt(screen, page);
        } catch (Throwable ignored) {
            // GTLCore is optional and its implementation may change.
        }
    }

    private static Integer eap$readGtlcorePage(Object screen) {
        Field field = eap$findGtlcorePageField(screen.getClass());
        if (field == null) {
            return null;
        }

        try {
            return field.getInt(screen);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field eap$findGtlcorePageField(Class<?> owner) {
        Class<?> current = owner;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(GTLCORE_PAGE_FIELD);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }
}

package com.extendedae_plus.mixin.extendedae.client.gui;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import com.extendedae_plus.api.IExPatternButton;
import com.extendedae_plus.api.IExPatternPage;
import com.extendedae_plus.api.bridge.ExPatternProviderMenuPageBridge;
import com.extendedae_plus.util.ScaleButtonHelper;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import com.glodblock.github.extendedae.network.EPPNetworkHandler;
import com.glodblock.github.glodium.network.packet.CGenericPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@SuppressWarnings({ "AddedMixinMembersNamePattern" })
@Mixin(GuiExPatternProvider.class)
public abstract class GuiExPatternProviderMixin extends PatternProviderScreen<ContainerExPatternProvider>
        implements IExPatternButton, IExPatternPage {
    @Unique
    private static final int SLOTS_PER_PAGE = 36;

    @Unique
    private ActionEPPButton nextPage;

    @Unique
    private ActionEPPButton prevPage;

    @Unique
    private int eap$lastScreenWidth = -1;

    @Unique
    private int eap$lastScreenHeight = -1;

    @Unique
    private int eap$currentPage = 0;

    @Unique
    private int eap$maxPageLocal = 1;

    @Unique
    private List<ActionEPPButton> scaleButtons;

    public GuiExPatternProviderMixin(ContainerExPatternProvider menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void eap$init(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style,
            CallbackInfo ci) {
        this.eap$syncMenuPageState(true);

        this.prevPage = new ActionEPPButton((button) -> this.eap$changePage(-1), Icon.ARROW_LEFT);
        this.nextPage = new ActionEPPButton((button) -> this.eap$changePage(1), Icon.ARROW_RIGHT);
        this.addToLeftToolbar(this.nextPage);
        this.addToLeftToolbar(this.prevPage);

        this.scaleButtons = ScaleButtonHelper.createAndLayout(
                this.leftPos + this.imageWidth,
                this.topPos + 104,
                22,
                ScaleButtonHelper.Side.RIGHT,
                (divide, factor) -> {
                    String action = (divide ? "divide" : "multiply") + factor;
                    EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket(action));
                });
        this.scaleButtons.forEach(this::addRenderableWidget);
    }

    @Override
    public int eap$getCurrentPage() {
        return this.eap$currentPage;
    }

    @Override
    public int eap$getMaxPageLocal() {
        return this.eap$maxPageLocal;
    }

    @Override
    public void eap$updateButtonsLayout() {
        this.eap$syncMenuPageState(false);

        boolean showPageButtons = this.eap$maxPageLocal > 1;
        if (this.nextPage != null) {
            this.nextPage.setVisibility(showPageButtons);
        }
        if (this.prevPage != null) {
            this.prevPage.setVisibility(showPageButtons);
        }

        if (this.scaleButtons != null) {
            for (ActionEPPButton button : this.scaleButtons) {
                if (button != null) {
                    button.setVisibility(true);
                    if (!this.renderables.contains(button)) {
                        this.addRenderableWidget(button);
                    }
                }
            }
        }

        if (this.width != this.eap$lastScreenWidth || this.height != this.eap$lastScreenHeight) {
            this.eap$lastScreenWidth = this.width;
            this.eap$lastScreenHeight = this.height;
            if (this.scaleButtons != null) {
                for (ActionEPPButton button : this.scaleButtons) {
                    if (button != null) {
                        this.removeWidget(button);
                        this.addRenderableWidget(button);
                    }
                }
            }
        }

        if (this.scaleButtons != null && this.scaleButtons.size() >= 6) {
            ScaleButtonHelper.layoutButtons(
                    new ScaleButtonHelper.ScaleButtonSet(
                            this.scaleButtons.get(1),
                            this.scaleButtons.get(0),
                            this.scaleButtons.get(3),
                            this.scaleButtons.get(2),
                            this.scaleButtons.get(5),
                            this.scaleButtons.get(4)),
                    this.leftPos + this.imageWidth,
                    this.topPos + 104,
                    22,
                    ScaleButtonHelper.Side.RIGHT);
        }
    }

    @Unique
    private void eap$changePage(int delta) {
        int maxPage = Math.max(1, this.eap$maxPageLocal);
        int newPage = Math.floorMod(this.eap$currentPage + delta, maxPage);
        this.eap$applyPage(newPage);
    }

    @Unique
    private void eap$applyPage(int page) {
        this.eap$currentPage = Math.max(0, Math.min(page, Math.max(1, this.eap$maxPageLocal) - 1));
        if (this.menu instanceof ExPatternProviderMenuPageBridge bridge) {
            bridge.eap$setPage(this.eap$currentPage);
        }

        this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
        this.repositionSlots(SlotSemantics.STORAGE);
        this.hoveredSlot = null;
    }

    @Unique
    private void eap$syncMenuPageState(boolean forceReposition) {
        int previousPage = this.eap$currentPage;
        int totalPages = Math.max(1,
                (this.menu.getSlots(SlotSemantics.ENCODED_PATTERN).size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);

        if (this.menu instanceof ExPatternProviderMenuPageBridge bridge) {
            this.eap$maxPageLocal = Math.max(1, Math.min(totalPages, bridge.eap$getUnlockedMaxPage()));
            this.eap$currentPage = Math.max(0, Math.min(bridge.eap$getPage(), this.eap$maxPageLocal - 1));
        } else {
            this.eap$maxPageLocal = totalPages;
            this.eap$currentPage = Math.max(0, Math.min(this.eap$currentPage, this.eap$maxPageLocal - 1));
        }

        if (forceReposition || previousPage != this.eap$currentPage) {
            this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
            this.repositionSlots(SlotSemantics.STORAGE);
            this.hoveredSlot = null;
        }
    }
}

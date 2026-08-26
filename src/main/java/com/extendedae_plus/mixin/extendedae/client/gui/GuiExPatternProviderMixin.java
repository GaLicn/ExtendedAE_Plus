package com.extendedae_plus.mixin.extendedae.client.gui;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import com.extendedae_plus.api.IExPatternButton;
import com.extendedae_plus.api.IExPatternPage;
import com.extendedae_plus.api.bridge.ExPatternProviderMenuPageBridge;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.gui.NewIcon;
import com.extendedae_plus.util.ScaleButtonHelper;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import com.glodblock.github.extendedae.network.EPPNetworkHandler;
import com.glodblock.github.glodium.network.packet.CGenericPacket;
import net.minecraft.client.gui.components.Tooltip;
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

    @Unique
    private ActionEPPButton eap$showPatternScalingControlsButton;

    @Unique
    private ActionEPPButton eap$hidePatternScalingControlsButton;

    @Unique
    private boolean eap$patternScalingControlsVisible = true;

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

        this.eap$showPatternScalingControlsButton = new ActionEPPButton(
                button -> this.eap$setPatternScalingControlsVisible(true),
                NewIcon.SHOW_PATTERN_SCALING_CONTROLS);
        this.eap$showPatternScalingControlsButton.setTooltip(Tooltip.create(
                Component.translatable("tooltip.extendedae_plus.show_pattern_scaling_controls")));
        this.eap$hidePatternScalingControlsButton = new ActionEPPButton(
                button -> this.eap$setPatternScalingControlsVisible(false),
                NewIcon.HIDE_PATTERN_SCALING_CONTROLS);
        this.eap$hidePatternScalingControlsButton.setTooltip(Tooltip.create(
                Component.translatable("tooltip.extendedae_plus.hide_pattern_scaling_controls")));
        this.addToLeftToolbar(this.eap$showPatternScalingControlsButton);
        this.addToLeftToolbar(this.eap$hidePatternScalingControlsButton);

        var firstUpgradeSlot = menu.getSlots(SlotSemantics.UPGRADE).get(0);
        var allScaleButtons = ScaleButtonHelper.createAndLayout(
                this.leftPos + firstUpgradeSlot.x + 23,
                this.topPos + firstUpgradeSlot.y-4,
                22,
                ScaleButtonHelper.Side.RIGHT,
                (divide, factor) -> {
                    String action = (divide ? "divide" : "multiply") + factor;
                    EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket(action));
                });
        // 供应器只显示 2 倍和 5 倍控件，10 倍控件仍供其它界面使用。
        this.scaleButtons = List.of(
                allScaleButtons.get(0), allScaleButtons.get(1),
                allScaleButtons.get(2), allScaleButtons.get(3));
        this.scaleButtons.forEach(this::addRenderableWidget);
        this.eap$patternScalingControlsVisible = ModConfig.INSTANCE.extendedPatternProviderShowScalingControls;
        this.eap$applyPatternScalingControlsVisibility();
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
                    button.setVisibility(this.eap$patternScalingControlsVisible);
                    if (!this.renderables.contains(button)) {
                        this.addRenderableWidget(button);
                    }
                }
            }
        }

        this.eap$applyPatternScalingControlsVisibility();

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

        if (this.scaleButtons != null && this.scaleButtons.size() >= 4) {
            var firstUpgradeSlot = this.menu.getSlots(SlotSemantics.UPGRADE).get(0);
            int buttonX = this.leftPos + firstUpgradeSlot.x + 24;
            int buttonY = this.topPos + firstUpgradeSlot.y - 4;
            this.scaleButtons.get(0).setX(buttonX);
            this.scaleButtons.get(0).setY(buttonY);
            this.scaleButtons.get(1).setX(buttonX);
            this.scaleButtons.get(1).setY(buttonY + 22);
            this.scaleButtons.get(2).setX(buttonX);
            this.scaleButtons.get(2).setY(buttonY + 44);
            this.scaleButtons.get(3).setX(buttonX);
            this.scaleButtons.get(3).setY(buttonY + 66);
        }
    }

    @Unique
    private void eap$changePage(int delta) {
        int maxPage = Math.max(1, this.eap$maxPageLocal);
        int newPage = Math.floorMod(this.eap$currentPage + delta, maxPage);
        this.eap$applyPage(newPage);
    }

    @Unique
    private void eap$setPatternScalingControlsVisible(boolean visible) {
        this.eap$patternScalingControlsVisible = visible;
        ModConfig.setExtendedPatternProviderShowScalingControls(visible);
        this.eap$applyPatternScalingControlsVisibility();
    }

    @Unique
    private void eap$applyPatternScalingControlsVisibility() {
        if (this.scaleButtons != null) {
            for (ActionEPPButton button : this.scaleButtons) {
                if (button != null) {
                    button.setVisibility(this.eap$patternScalingControlsVisible);
                }
            }
        }
        if (this.eap$showPatternScalingControlsButton != null) {
            this.eap$showPatternScalingControlsButton.setVisibility(!this.eap$patternScalingControlsVisible);
        }
        if (this.eap$hidePatternScalingControlsButton != null) {
            this.eap$hidePatternScalingControlsButton.setVisibility(this.eap$patternScalingControlsVisible);
        }
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
            this.eap$maxPageLocal = Math.max(1, Math.min(totalPages, bridge.eap$getAvailablePageCount()));
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

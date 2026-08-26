package com.extendedae_plus.mixin.extendedae.client.gui;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import com.extendedae_plus.api.IExPatternButton;
import com.extendedae_plus.api.IExPatternPage;
import com.extendedae_plus.api.bridge.ExPatternProviderMenuPageBridge;
import com.extendedae_plus.client.gui.NewIcon;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.network.ScalePatternsC2SPacket;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiExPatternProvider.class, remap = false)
public abstract class GuiExPatternProviderMixin extends PatternProviderScreen<ContainerExPatternProvider> implements IExPatternButton, IExPatternPage {

    @Unique
    private static final int SLOTS_PER_PAGE = 36; // 每页显示36个槽位
    @Unique private
    ScreenStyle eap$screenStyle;
    // 跟踪上次屏幕尺寸，处理 GUI 缩放/窗口大小变化后按钮丢失问题
    @Unique private int eap$lastScreenWidth = -1;

    // 不再使用右侧 VerticalButtonBar，直接把按钮注册为独立 AE2 小部件
    @Unique private int eap$lastScreenHeight = -1;
    @Unique
    private int eap$currentPage = 0;

    @Unique
    private int eap$maxPageLocal = 1;
    private ActionEPPButton nextPage;


    // 移除手动挪动 Slot 坐标，交由 SlotGridLayout + 原生布局控制
    private ActionEPPButton prevPage;
    private ActionEPPButton x2Button;
    private ActionEPPButton divideBy2Button;
    private ActionEPPButton x5Button;
    private ActionEPPButton divideBy5Button;
    @Unique private ActionEPPButton eap$showPatternScalingControlsButton;
    @Unique private ActionEPPButton eap$hidePatternScalingControlsButton;
    @Unique private boolean eap$patternScalingControlsVisible = true;

    public GuiExPatternProviderMixin(ContainerExPatternProvider menu, Inventory playerInventory, Component title,
                                     ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Unique
    private int getCurrentPage() {
        // 优先使用本地 GUI 维护的页码
        return Math.max(0, this.eap$currentPage % Math.max(1, this.eap$maxPageLocal));
    }

    @Unique
    private int getMaxPage() {
        return this.eap$syncMaxPageState();
    }

    @Unique
    private int eap$syncMaxPageState() {
        int previousPage = this.eap$currentPage;
        int totalSlots = this.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN).size();
        int totalPages = Math.max(1, (totalSlots + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);

        if (this.getMenu() instanceof ExPatternProviderMenuPageBridge bridge) {
            this.eap$maxPageLocal = Math.max(1, Math.min(totalPages, bridge.eap$getAvailablePageCount()));
            this.eap$currentPage = Math.max(0, Math.min(bridge.eap$getPage(), this.eap$maxPageLocal - 1));
        } else {
            this.eap$maxPageLocal = totalPages;
            this.eap$currentPage = Math.max(0, Math.min(this.eap$currentPage, this.eap$maxPageLocal - 1));
        }

        if (previousPage != this.eap$currentPage) {
            try {
                this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
                this.repositionSlots(SlotSemantics.STORAGE);
                this.hoveredSlot = null;
            } catch (Throwable ignored) {
            }
        }
        return this.eap$maxPageLocal;
    }

    // 在构造器返回后初始化按钮与翻页控制
    @Inject(method = "<init>", at = @At("RETURN"))
    private void injectInit(ContainerExPatternProvider menu, Inventory playerInventory, Component title,
                            ScreenStyle style, CallbackInfo ci) {
        this.eap$screenStyle = style;
        this.eap$maxPageLocal = this.eap$syncMaxPageState();
        this.eap$currentPage = 0;

        this.prevPage = new ActionEPPButton((b) -> {
            int currentPage = this.getCurrentPage();
            int maxPage = this.getMaxPage();
            int newPage = (currentPage - 1 + maxPage) % maxPage;
            if (this.getMenu() instanceof ExPatternProviderMenuPageBridge bridge) {
                bridge.eap$setPage(newPage);
            }
            this.eap$currentPage = newPage;
            this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
            this.repositionSlots(SlotSemantics.STORAGE);
            this.hoveredSlot = null;
            this.eap$updatePageSlotActivity();
        }, Icon.ARROW_LEFT
        );

        this.nextPage = new ActionEPPButton((b) -> {
            int currentPage = this.getCurrentPage();
            int maxPage = this.getMaxPage();
            int newPage = (currentPage + 1) % maxPage;
            if (this.getMenu() instanceof ExPatternProviderMenuPageBridge bridge) {
                bridge.eap$setPage(newPage);
            }
            this.eap$currentPage = newPage;
            this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
            this.repositionSlots(SlotSemantics.STORAGE);
            this.hoveredSlot = null;
            this.eap$updatePageSlotActivity();
        }, Icon.ARROW_RIGHT
        );

        this.addToLeftToolbar(this.nextPage);
        this.addToLeftToolbar(this.prevPage);

        this.eap$showPatternScalingControlsButton = new ActionEPPButton(
                b -> this.eap$setPatternScalingControlsVisible(true),
                NewIcon.SHOW_PATTERN_SCALING_CONTROLS
        );
        this.eap$showPatternScalingControlsButton.setTooltip(Tooltip.create(
                Component.translatable("tooltip.extendedae_plus.show_pattern_scaling_controls")
        ));
        this.eap$hidePatternScalingControlsButton = new ActionEPPButton(
                b -> this.eap$setPatternScalingControlsVisible(false),
                NewIcon.HIDE_PATTERN_SCALING_CONTROLS
        );
        this.eap$hidePatternScalingControlsButton.setTooltip(Tooltip.create(
                Component.translatable("tooltip.extendedae_plus.hide_pattern_scaling_controls")
        ));
        this.addToLeftToolbar(this.eap$showPatternScalingControlsButton);
        this.addToLeftToolbar(this.eap$hidePatternScalingControlsButton);

        // 倍增/除法按钮：使用自有 C2S 包发送到服务端执行样板缩放
        this.x2Button = new ActionEPPButton((b) -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new ScalePatternsC2SPacket(ScalePatternsC2SPacket.Operation.MUL2));
        }, NewIcon.MULTIPLY2
        );
        this.x2Button.setVisibility(true);

        this.divideBy2Button = new ActionEPPButton((b) -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new ScalePatternsC2SPacket(ScalePatternsC2SPacket.Operation.DIV2));
        }, NewIcon.DIVIDE2
        );
        this.divideBy2Button.setVisibility(true);

        this.divideBy5Button = new ActionEPPButton((b) -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new ScalePatternsC2SPacket(ScalePatternsC2SPacket.Operation.DIV5));
        }, NewIcon.DIVIDE5
        );
        this.divideBy5Button.setVisibility(true);

        this.x5Button = new ActionEPPButton((b) -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new ScalePatternsC2SPacket(ScalePatternsC2SPacket.Operation.MUL5));
        }, NewIcon.MULTIPLY5
        );
        this.x5Button.setVisibility(true);

        // 注册可渲染按钮
        this.addRenderableWidget(this.divideBy2Button);
        this.addRenderableWidget(this.x2Button);
        this.addRenderableWidget(this.divideBy5Button);
        this.addRenderableWidget(this.x5Button);
        this.eap$patternScalingControlsVisible = ModConfigs.EXTENDED_PATTERN_PROVIDER_SHOW_SCALING_CONTROLS.get();
        this.eap$applyPatternScalingControlsVisibility();
    }

    @Override
    public int eap$getCurrentPage() {
        return this.getCurrentPage();
    }

    @Override
    public void eap$setCurrentPage(int page) {
        this.eap$currentPage = Math.max(0, Math.min(page, Math.max(1, this.eap$maxPageLocal) - 1));
        if (this.getMenu() instanceof ExPatternProviderMenuPageBridge bridge) {
            bridge.eap$setPage(this.eap$currentPage);
        }
        this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
        this.repositionSlots(SlotSemantics.STORAGE);
        this.hoveredSlot = null;
        this.eap$updatePageSlotActivity();
    }

    @Override
    public void eap$updateButtonsLayout() {
        this.eap$syncMaxPageState();

        // 只处理按钮可见性与定位，不再强制 showPage 或挪动 Slot 坐标，避免与原布局/tooltip 冲突
        if (this.nextPage != null && this.prevPage != null) {
            boolean showPageButtons = this.eap$maxPageLocal > 1;
            this.nextPage.setVisibility(showPageButtons);
            this.prevPage.setVisibility(showPageButtons);
        }
        this.eap$applyPatternScalingControlsVisibility();

        // 若从 JEI 配方界面返回后，Screen 的 renderables/children 可能被清空，导致按钮丢失
        // 这里在每帧保证这些按钮存在于渲染列表中（不存在则重新注册）
        try {
            if (this.divideBy2Button != null && !this.renderables.contains(this.divideBy2Button)) {
                this.addRenderableWidget(this.divideBy2Button);
            }
            if (this.x2Button != null && !this.renderables.contains(this.x2Button)) {
                this.addRenderableWidget(this.x2Button);
            }
            if (this.divideBy5Button != null && !this.renderables.contains(this.divideBy5Button)) {
                this.addRenderableWidget(this.divideBy5Button);
            }
            if (this.x5Button != null && !this.renderables.contains(this.x5Button)) {
                this.addRenderableWidget(this.x5Button);
            }
        } catch (Throwable ignored) {
        }

        // 如果屏幕尺寸发生变化（窗口/GUI缩放），重新注册右侧外列的自定义按钮，翻页按钮由左侧工具栏托管
        if (this.width != this.eap$lastScreenWidth || this.height != this.eap$lastScreenHeight) {
            this.eap$lastScreenWidth = this.width;
            this.eap$lastScreenHeight = this.height;
            try {
                if (this.divideBy2Button != null) {
                    this.removeWidget(this.divideBy2Button);
                    this.addRenderableWidget(this.divideBy2Button);
                }
                if (this.x2Button != null) {
                    this.removeWidget(this.x2Button);
                    this.addRenderableWidget(this.x2Button);
                }
                if (this.divideBy5Button != null) {
                    this.removeWidget(this.divideBy5Button);
                    this.addRenderableWidget(this.divideBy5Button);
                }
                if (this.x5Button != null) {
                    this.removeWidget(this.x5Button);
                    this.addRenderableWidget(this.x5Button);
                }
            } catch (Throwable ignored) {
            }
        }

        int spacing = 23;
        // 以升级槽列为锚点，首个倍率按钮向右偏移一个按钮间距。
        var firstUpgradeSlot = this.getMenu().getSlots(SlotSemantics.UPGRADE).getFirst();
        int bx = this.leftPos + firstUpgradeSlot.x + spacing;
        int by = this.topPos + firstUpgradeSlot.y - 1;
        // 翻页按钮交由左侧工具栏布局，无需手动定位
        if (this.divideBy2Button != null) {
            this.divideBy2Button.setX(bx);
            this.divideBy2Button.setY(by);
        }
        if (this.x2Button != null) {
            this.x2Button.setX(bx);
            this.x2Button.setY(by + spacing);
        }
        if (this.divideBy5Button != null) {
            this.divideBy5Button.setX(bx);
            this.divideBy5Button.setY(by + spacing * 2);
        }
        if (this.x5Button != null) {
            this.x5Button.setX(bx);
            this.x5Button.setY(by + spacing * 3);
        }

        // 每帧确保当前页槽位处于启用状态，非当前页禁用
        this.eap$updatePageSlotActivity();
    }

    // 本文件原包含本地样板缩放实现（单机模式）和 ExtendedAE 网络派发，已移除以兼容 1.21.1 与最小可构建集。

    @Unique
    private void eap$setPatternScalingControlsVisible(boolean visible) {
        this.eap$patternScalingControlsVisible = visible;
        ModConfigs.EXTENDED_PATTERN_PROVIDER_SHOW_SCALING_CONTROLS.set(visible);
        this.eap$applyPatternScalingControlsVisibility();
    }

    @Unique
    private void eap$applyPatternScalingControlsVisibility() {
        boolean visible = this.eap$patternScalingControlsVisible;
        if (this.x2Button != null) this.x2Button.setVisibility(visible);
        if (this.divideBy2Button != null) this.divideBy2Button.setVisibility(visible);
        if (this.divideBy5Button != null) this.divideBy5Button.setVisibility(visible);
        if (this.x5Button != null) this.x5Button.setVisibility(visible);

        // 左侧工具栏只保留当前可执行操作对应的图标。
        if (this.eap$showPatternScalingControlsButton != null) {
            this.eap$showPatternScalingControlsButton.setVisibility(!visible);
        }
        if (this.eap$hidePatternScalingControlsButton != null) {
            this.eap$hidePatternScalingControlsButton.setVisibility(visible);
        }
    }


    @Unique
    private void eap$updatePageSlotActivity() {
        try {
            if (!(((Object) this) instanceof GuiExPatternProvider)) return;
            var list = this.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN);
            if (list == null || list.isEmpty()) return;

            int currentPage = this.getCurrentPage();
            int base = currentPage * SLOTS_PER_PAGE;
            int end = Math.min(list.size(), base + SLOTS_PER_PAGE);

            for (int i = 0; i < list.size(); i++) {
                var slot = list.get(i);
                if (slot instanceof AppEngSlot s) {
                    boolean enabled = i >= base && i < end;
                    s.setActive(enabled);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}

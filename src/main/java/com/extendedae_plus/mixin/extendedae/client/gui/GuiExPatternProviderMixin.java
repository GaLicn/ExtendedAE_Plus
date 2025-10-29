package com.extendedae_plus.mixin.extendedae.client.gui;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import com.extendedae_plus.ae.client.gui.NewIcon;
import com.extendedae_plus.api.IExPatternButton;
import com.extendedae_plus.api.IExPatternPage;
import com.extendedae_plus.config.ModConfig;
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

@Mixin(GuiExPatternProvider.class)
public abstract class GuiExPatternProviderMixin extends PatternProviderScreen<ContainerExPatternProvider> implements IExPatternButton, IExPatternPage {
    // 跟踪上次屏幕尺寸，处理 GUI 缩放/窗口大小变化后按钮丢失问题
    @Unique private int eap$lastScreenWidth = -1;
    @Unique private int eap$lastScreenHeight = -1;

    @Unique
    private int eap$currentPage = 0;

    @Unique
    private int eap$maxPageLocal = 1;

    public GuiExPatternProviderMixin(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public int eap$getCurrentPage() {
        // 优先使用本地 GUI 维护的页码
        return Math.max(0, eap$currentPage % Math.max(1, eap$maxPageLocal));
    }

    @Override
    public int eap$getMaxPageLocal() {
        // 优先使用配置倍数
        return this.eap$maxPageLocal;
    }

    public ActionEPPButton nextPage;
    public ActionEPPButton prevPage;
    public ActionEPPButton x2Button;
    public ActionEPPButton divideBy2Button;
    public ActionEPPButton x5Button;
    public ActionEPPButton divideBy5Button;
    public ActionEPPButton x10Button;
    public ActionEPPButton divideBy10Button;
    
    // 在构造器返回后初始化按钮与翻页控制
    @Inject(method = "<init>", at = @At("RETURN"))
    private void injectInit(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        this.eap$maxPageLocal = ModConfig.INSTANCE.pageMultiplier;

        // 翻页按钮（当存在多页时显示；支持仅由配置决定的“空白页”）
        if (eap$maxPageLocal > 1) {
            this.prevPage = new ActionEPPButton((b) -> {
                int currentPage = eap$getCurrentPage();
                int maxPage = Math.max(this.eap$maxPageLocal, eap$getMaxPageLocal());

                // 同步到本地 GUI 页码
                this.eap$currentPage = (currentPage - 1 + maxPage) % maxPage;

                // 强制重排（放在更新本地页码之后，确保布局读取到新页）
                this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
                this.repositionSlots(SlotSemantics.STORAGE);
                this.hoveredSlot = null;
            }, Icon.ARROW_LEFT);

            this.nextPage = new ActionEPPButton((b) -> {
                int currentPage = eap$getCurrentPage();
                int maxPage = Math.max(this.eap$maxPageLocal, eap$getMaxPageLocal());

                // 同步到本地 GUI 页码
                this.eap$currentPage = (currentPage + 1) % maxPage;

                // 强制重排（放在更新本地页码之后，确保布局读取到新页）
                this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
                this.repositionSlots(SlotSemantics.STORAGE);
                this.hoveredSlot = null;
            }, Icon.ARROW_RIGHT);

            // 恢复到 AE2 左侧工具栏
            this.addToLeftToolbar(this.nextPage);
            this.addToLeftToolbar(this.prevPage);
        }

        // 倍增/除法按钮，通过 ExtendedAE 的通用包派发
        this.x2Button = new ActionEPPButton((b) -> {
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("multiply2"));
        }, NewIcon.MULTIPLY2);
        this.x2Button.setVisibility(true);

        this.divideBy2Button = new ActionEPPButton((b) -> {
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("divide2"));
        }, NewIcon.DIVIDE2);
        this.divideBy2Button.setVisibility(true);

        this.x10Button = new ActionEPPButton((b) -> {
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("multiply10"));
        }, NewIcon.MULTIPLY10);
        this.x10Button.setVisibility(true);

        this.divideBy10Button = new ActionEPPButton((b) -> {
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("divide10"));
        }, NewIcon.DIVIDE10);
        this.divideBy10Button.setVisibility(true);

        this.divideBy5Button = new ActionEPPButton((b) -> {
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("divide5"));
        }, NewIcon.DIVIDE5);
        this.divideBy5Button.setVisibility(true);

        this.x5Button = new ActionEPPButton((b) -> {
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("multiply5"));
        }, NewIcon.MULTIPLY5);
        this.x5Button.setVisibility(true);

        // 注册可渲染按钮
        this.addRenderableWidget(this.divideBy2Button);
        this.addRenderableWidget(this.x2Button);
        this.addRenderableWidget(this.divideBy5Button);
        this.addRenderableWidget(this.x5Button);
        this.addRenderableWidget(this.divideBy10Button);
        this.addRenderableWidget(this.x10Button);
    }

    @Override
    public void eap$updateButtonsLayout() {
        // 只处理按钮可见性与定位，不再强制 showPage 或挪动 Slot 坐标，避免与原布局/tooltip 冲突
        if (nextPage != null && prevPage != null) {
            this.nextPage.setVisibility(true);
            this.prevPage.setVisibility(true);
        }
        if (x2Button != null) {
            this.x2Button.setVisibility(true);
        }
        if (divideBy2Button != null) {
            this.divideBy2Button.setVisibility(true);
        }
        if (x10Button != null) {
            this.x10Button.setVisibility(true);
        }
        if (divideBy10Button != null) {
            this.divideBy10Button.setVisibility(true);
        }
        if (divideBy5Button != null) {
            this.divideBy5Button.setVisibility(true);
        }
        if (x5Button != null) {
            this.x5Button.setVisibility(true);
        }

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
            if (this.divideBy10Button != null && !this.renderables.contains(this.divideBy10Button)) {
                this.addRenderableWidget(this.divideBy10Button);
            }
            if (this.x10Button != null && !this.renderables.contains(this.x10Button)) {
                this.addRenderableWidget(this.x10Button);
            }
        } catch (Throwable ignored) {}

        // 如果屏幕尺寸发生变化（窗口/GUI缩放），重新注册右侧外列的自定义按钮，翻页按钮由左侧工具栏托管
        if (this.width != eap$lastScreenWidth || this.height != eap$lastScreenHeight) {
            eap$lastScreenWidth = this.width;
            eap$lastScreenHeight = this.height;
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
                if (this.divideBy10Button != null) {
                    this.removeWidget(this.divideBy10Button);
                    this.addRenderableWidget(this.divideBy10Button);
                }
                if (this.x10Button != null) {
                    this.removeWidget(this.x10Button);
                    this.addRenderableWidget(this.x10Button);
                }
            } catch (Throwable ignored) {}
        }

        // 定位到 GUI 右缘外侧一点（使用绝对屏幕坐标）
        int bx = this.leftPos + this.imageWidth + 1; // 向右平移 1px 到面板外侧
        int by = this.topPos + 50; // 向下偏移25px (从20改为45)
        int spacing = 22;
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
        if (this.divideBy10Button != null) {
            this.divideBy10Button.setX(bx);
            this.divideBy10Button.setY(by + spacing * 4);
        }
        if (this.x10Button != null) {
            this.x10Button.setX(bx);
            this.x10Button.setY(by + spacing * 5);
        }
    }
}
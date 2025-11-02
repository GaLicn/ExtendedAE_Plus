package com.extendedae_plus.mixin.extendedae.client.gui;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import com.extendedae_plus.api.IExPatternButton;
import com.extendedae_plus.api.IExPatternPage;
import com.extendedae_plus.config.ModConfig;
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

@SuppressWarnings({"AddedMixinMembersNamePattern"})
@Mixin(GuiExPatternProvider.class)
public abstract class GuiExPatternProviderMixin extends PatternProviderScreen<ContainerExPatternProvider> implements IExPatternButton, IExPatternPage {
    private static final int SLOTS_PER_PAGE = 36; // 每页显示36个槽位
    // 翻页按钮
    @Unique public ActionEPPButton nextPage;
    @Unique public ActionEPPButton prevPage;
    // 屏幕尺寸跟踪，防止按钮丢失
    @Unique private int eap$lastScreenWidth = -1;
    @Unique private int eap$lastScreenHeight = -1;
    @Unique private int eap$currentPage = 0;
    @Unique private int eap$maxPageLocal = 1;
    // 集合管理的倍增/除法按钮
    @Unique
    private List<ActionEPPButton> scaleButtons;

    public GuiExPatternProviderMixin(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public int eap$getCurrentPage() {
        return Math.max(0, eap$currentPage % Math.max(1, eap$maxPageLocal));
    }

    @Override
    public int eap$getMaxPageLocal() {
        return this.eap$maxPageLocal;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void injectInit(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        this.eap$maxPageLocal = ModConfig.INSTANCE.pageMultiplier;
        int slotPageSize = (this.menu.getSlots(SlotSemantics.ENCODED_PATTERN).size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
        this.eap$maxPageLocal = Math.max(Math.max(1, slotPageSize), this.eap$maxPageLocal);

        // 翻页按钮（左侧工具栏）
        if (eap$maxPageLocal > 1) {
            this.prevPage = new ActionEPPButton((b) -> {
                int currentPage = eap$getCurrentPage();
                int maxPage = this.eap$maxPageLocal;
                this.eap$currentPage = (currentPage - 1 + maxPage) % maxPage;

                // 强制重排（放在更新本地页码之后，确保布局读取到新页）
                this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
                this.repositionSlots(SlotSemantics.STORAGE);
                this.hoveredSlot = null;
            }, Icon.ARROW_LEFT);

            this.nextPage = new ActionEPPButton((b) -> {
                int currentPage = eap$getCurrentPage();
                int maxPage = this.eap$maxPageLocal;
                this.eap$currentPage = (currentPage + 1) % maxPage;

                // 强制重排（放在更新本地页码之后，确保布局读取到新页）
                this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
                this.repositionSlots(SlotSemantics.STORAGE);
                this.hoveredSlot = null;
            }, Icon.ARROW_RIGHT);

            this.addToLeftToolbar(this.nextPage);
            this.addToLeftToolbar(this.prevPage);
        }

        // 使用 ScaleButtonHelper 创建、布局并返回集合
        this.scaleButtons = ScaleButtonHelper.createAndLayout(
                this.leftPos + this.imageWidth,         // baseX 右侧外缘
                this.topPos + 50,                              // baseY
                22,                                            // spacing
                ScaleButtonHelper.Side.RIGHT,                  // 右侧布局
                (divide, factor) -> {          // 点击事件回调
                    String action = (divide ? "divide" : "multiply") + factor;
                    EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket(action));
                }
        );

        // 注册所有倍增/除法按钮
        this.scaleButtons.forEach(this::addRenderableWidget);
    }

    @Override
    public void eap$updateButtonsLayout() {
        // 1. 设置倍增/除法按钮可见性，并确保在 renderables 中
        for (ActionEPPButton b : scaleButtons) {
            if (b != null) {
                b.setVisibility(true);
                if (!this.renderables.contains(b)) this.addRenderableWidget(b);
            }
        }

        // 2. 屏幕尺寸变化时重新注册按钮，防止丢失
        if (this.width != eap$lastScreenWidth || this.height != eap$lastScreenHeight) {
            eap$lastScreenWidth = this.width;
            eap$lastScreenHeight = this.height;
            for (ActionEPPButton b : scaleButtons) {
                if (b != null) {
                    this.removeWidget(b);
                    this.addRenderableWidget(b);
                }
            }
        }

        // 3. 使用工具类统一布局倍增/除法按钮
        if (!scaleButtons.isEmpty()) {
            ScaleButtonHelper.layoutButtons(
                    new ScaleButtonHelper.ScaleButtonSet(
                            scaleButtons.get(1), // multiply2
                            scaleButtons.get(0), // divide2
                            scaleButtons.get(3), // multiply5
                            scaleButtons.get(2), // divide5
                            scaleButtons.get(5), // multiply10
                            scaleButtons.get(4)  // divide10
                    ),
                    this.leftPos + this.imageWidth,
                    this.topPos + 50,
                    22,
                    ScaleButtonHelper.Side.RIGHT
            );
        }
    }
}

package com.extendedae_plus.mixin;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.VerticalButtonBar;
import appeng.menu.SlotSemantics;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import com.extendedae_plus.network.UpdatePagePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(GuiExPatternProvider.class)
public abstract class GuiExPatternProviderMixin extends PatternProviderScreen<ContainerExPatternProvider> {

    @Unique
    ScreenStyle screenStyle;

    @Unique
    private VerticalButtonBar rightToolbar;

    public GuiExPatternProviderMixin(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Unique
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        int maxSlots = this.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN).size();
        if (maxSlots > 36) {
            Font fontRenderer = Minecraft.getInstance().font;

            // 获取当前页码
            int currentPage = getCurrentPage();
            int maxPage = getMaxPage();

            // 获取ae通用界面样式
            int color = screenStyle.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
            guiGraphics.drawString(font, Component.literal("第 " + (currentPage + 1) + "/" + maxPage + " 页"),
                    leftPos + imageWidth / 2 - 30, topPos + 5, color, false);
        }
    }

    @Unique
    public void updateBeforeRender() {
        super.updateBeforeRender();
        try {
            ContainerExPatternProvider menu1 = this.getMenu();

            // 调用showPage方法
            java.lang.reflect.Method showPageMethod = menu1.getClass().getMethod("showPage");
            showPageMethod.invoke(menu1);

            // 获取当前页码和最大页码
            Field fieldPage = menu1.getClass().getDeclaredField("page");
            fieldPage.setAccessible(true);
            Integer page = (Integer) fieldPage.get(menu1);

            Field fieldMaxPage = menu1.getClass().getDeclaredField("maxPage");
            fieldMaxPage.setAccessible(true);
            Integer maxPage = (Integer) fieldMaxPage.get(menu1);

            // 更新按钮可见性
            if (nextPage != null && prevPage != null) {
                this.nextPage.setVisibility(page + 1 < maxPage);
                this.prevPage.setVisibility(page - 1 >= 0);
            }
        } catch (Exception e) {
            // 忽略反射错误
        }
    }

    @Unique
    private int getCurrentPage() {
        try {
            ContainerExPatternProvider menu1 = this.getMenu();
            Field fieldPage = menu1.getClass().getDeclaredField("page");
            fieldPage.setAccessible(true);
            return (Integer) fieldPage.get(menu1);
        } catch (Exception e) {
            return 0;
        }
    }

    @Unique
    private int getMaxPage() {
        try {
            ContainerExPatternProvider menu1 = this.getMenu();
            Field fieldMaxPage = menu1.getClass().getDeclaredField("maxPage");
            fieldMaxPage.setAccessible(true);
            return (Integer) fieldMaxPage.get(menu1);
        } catch (Exception e) {
            return 1;
        }
    }

    public ActionEPPButton nextPage;
    public ActionEPPButton prevPage;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void injectInit(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        this.screenStyle = style;
        this.rightToolbar = new VerticalButtonBar();

        // 前进后退按钮
        this.prevPage = new ActionEPPButton((b) -> {
            int currentPage = getCurrentPage();
            if (currentPage > 0) {
                // 发送网络包更新页码
                // 这里简化处理，直接调用setPage方法
                try {
                    ContainerExPatternProvider menu1 = this.getMenu();
                    java.lang.reflect.Method setPageMethod = menu1.getClass().getMethod("setPage", int.class);
                    setPageMethod.invoke(menu1, currentPage - 1);
                } catch (Exception e) {
                    // 忽略反射错误
                }
            }
        }, Icon.ARROW_LEFT);

        this.nextPage = new ActionEPPButton((b) -> {
            int currentPage = getCurrentPage();
            int maxPage = getMaxPage();
            if (currentPage + 1 < maxPage) {
                try {
                    ContainerExPatternProvider menu1 = this.getMenu();
                    java.lang.reflect.Method setPageMethod = menu1.getClass().getMethod("setPage", int.class);
                    setPageMethod.invoke(menu1, currentPage + 1);
                } catch (Exception e) {
                    // 忽略反射错误
                }
            }
        }, Icon.ARROW_RIGHT);

        this.addToLeftToolbar(this.nextPage);
        this.addToLeftToolbar(this.prevPage);
    }
} 
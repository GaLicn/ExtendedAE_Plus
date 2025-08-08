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
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(GuiExPatternProvider.class)
public abstract class GuiExPatternProviderMixin extends PatternProviderScreen<ContainerExPatternProvider> {

    @Unique
    ScreenStyle screenStyle;

    @Unique
    private VerticalButtonBar rightToolbar;

    @Unique
    private static final int SLOTS_PER_PAGE = 36; // 每页显示36个槽位

    public GuiExPatternProviderMixin(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Unique
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        int maxSlots = this.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN).size();
        // 只有当槽位数超过每页显示数量时才显示翻页信息
        if (maxSlots > SLOTS_PER_PAGE) {
            Font fontRenderer = Minecraft.getInstance().font;

            // 获取当前页码
            int currentPage = getCurrentPage();
            int maxPage = getMaxPage();

                               // 获取ae通用界面样式
                   int color = screenStyle.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
                   // 调整页码显示位置：在"样板"文字的右边
                   guiGraphics.drawString(font, Component.literal("第 " + (currentPage + 1) + "/" + maxPage + " 页"),
                           leftPos + 8 + 50, topPos + 30, color, false);
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

                               // 更新按钮可见性 - 始终显示，支持循环翻页
                   if (nextPage != null && prevPage != null) {
                       this.nextPage.setVisibility(true);
                       this.prevPage.setVisibility(true);
                   }
            
            // 调整槽位位置
            this.adjustSlotPositions(page);
        } catch (Exception e) {
            // 忽略反射错误
        }
    }
    
    @Unique
    private void adjustSlotPositions(int currentPage) {
        try {
            List<Slot> slots = this.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN);
            int totalSlots = slots.size();
            
            if (totalSlots <= SLOTS_PER_PAGE) {
                return; // 不需要翻页
            }
            
            int slot_id = 0;
            for (Slot s : slots) {
                int page_id = slot_id / SLOTS_PER_PAGE;
                
                if (page_id == currentPage) {
                    // 当前页的槽位需要调整位置
                    int slotInPage = slot_id % SLOTS_PER_PAGE;
                    int row = slotInPage / 9;  // 0-3
                    int col = slotInPage % 9;  // 0-8
                    
                    // 计算目标位置（始终在前4行）
                    int x = 8 + col * 18;
                    int y = 42 + row * 18;
                    
                    // 使用反射设置槽位位置，支持混淆环境
                    Field xField = null;
                    Field yField = null;
                    
                    // 尝试不同的字段名（开发环境和生产环境可能不同）
                    String[] xFieldNames = {"x", "field_75262_c"};
                    String[] yFieldNames = {"y", "field_75263_d"};
                    
                    for (String fieldName : xFieldNames) {
                        try {
                            xField = Slot.class.getDeclaredField(fieldName);
                            xField.setAccessible(true);
                            break;
                        } catch (NoSuchFieldException ignored) {}
                    }
                    
                    for (String fieldName : yFieldNames) {
                        try {
                            yField = Slot.class.getDeclaredField(fieldName);
                            yField.setAccessible(true);
                            break;
                        } catch (NoSuchFieldException ignored) {}
                    }
                    
                    if (xField != null && yField != null) {
                        xField.set(s, x);
                        yField.set(s, y);
                    }
                }
                ++slot_id;
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

        // 只有当槽位数超过每页显示数量时才添加翻页按钮
        int maxSlots = this.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN).size();
        if (maxSlots > SLOTS_PER_PAGE) {
            // 前进后退按钮
                               this.prevPage = new ActionEPPButton((b) -> {
                       int currentPage = getCurrentPage();
                       int maxPage = getMaxPage();
                       // 循环翻页：第一页向前翻到最后一页
                       int newPage = (currentPage - 1 + maxPage) % maxPage;
                       try {
                           ContainerExPatternProvider menu1 = this.getMenu();
                           java.lang.reflect.Method setPageMethod = menu1.getClass().getMethod("setPage", int.class);
                           setPageMethod.invoke(menu1, newPage);
                       } catch (Exception e) {
                           // 忽略反射错误
                       }
                   }, Icon.ARROW_LEFT);

                               this.nextPage = new ActionEPPButton((b) -> {
                       int currentPage = getCurrentPage();
                       int maxPage = getMaxPage();
                       // 循环翻页：最后一页向后翻到第一页
                       int newPage = (currentPage + 1) % maxPage;
                       try {
                           ContainerExPatternProvider menu1 = this.getMenu();
                           java.lang.reflect.Method setPageMethod = menu1.getClass().getMethod("setPage", int.class);
                           setPageMethod.invoke(menu1, newPage);
                       } catch (Exception e) {
                           // 忽略反射错误
                       }
                   }, Icon.ARROW_RIGHT);

            this.addToLeftToolbar(this.nextPage);
            this.addToLeftToolbar(this.prevPage);
        }
    }
} 
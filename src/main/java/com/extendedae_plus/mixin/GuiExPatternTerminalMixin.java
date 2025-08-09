package com.extendedae_plus.mixin;

import appeng.client.gui.Icon;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import com.glodblock.github.extendedae.client.gui.GuiWirelessExPAT;
import com.glodblock.github.extendedae.container.ContainerExPatternTerminal;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiExPatternTerminal.class)
public abstract class GuiExPatternTerminalMixin extends AEBaseScreen<ContainerExPatternTerminal> {

    @Unique
    private IconButton toggleSlotsButton;

    @Unique
    private boolean showSlots = true; // 默认显示槽位

    public GuiExPatternTerminalMixin(ContainerExPatternTerminal menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void injectConstructor(ContainerExPatternTerminal menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        // 创建切换槽位显示的按钮
        this.toggleSlotsButton = new IconButton((b) -> {
            System.out.println("ExtendedAE Plus: 按钮被点击，当前showSlots: " + this.showSlots);
            this.showSlots = !this.showSlots; // 开关状态
            System.out.println("ExtendedAE Plus: 切换后showSlots: " + this.showSlots);
            
            // 通过反射调用refreshList方法 - 先尝试当前类，失败后尝试父类
            try {
                java.lang.reflect.Method refreshMethod = null;
                try {
                    // 先尝试在当前类中查找
                    refreshMethod = this.getClass().getDeclaredMethod("refreshList");
                    System.out.println("ExtendedAE Plus: 在当前类中找到refreshList方法: " + this.getClass().getSimpleName());
                } catch (NoSuchMethodException e1) {
                    // 如果当前类没有，尝试在父类中查找
                    try {
                        refreshMethod = this.getClass().getSuperclass().getDeclaredMethod("refreshList");
                        System.out.println("ExtendedAE Plus: 在父类中找到refreshList方法: " + this.getClass().getSuperclass().getSimpleName());
                    } catch (NoSuchMethodException e2) {
                        System.out.println("ExtendedAE Plus: 在当前类和父类中都找不到refreshList方法");
                        throw e2;
                    }
                }
                
                refreshMethod.setAccessible(true);
                refreshMethod.invoke(this);
                System.out.println("ExtendedAE Plus: refreshList调用成功");
            } catch (Exception e) {
                System.out.println("ExtendedAE Plus: 调用refreshList失败: " + e.getMessage());
                e.printStackTrace();
            }
        }) {
            @Override
            protected Icon getIcon() {
                return showSlots ? Icon.PATTERN_ACCESS_HIDE : Icon.PATTERN_ACCESS_SHOW;
            }
        };
        
        // 设置按钮提示文本
        this.toggleSlotsButton.setTooltip(Tooltip.create(Component.translatable("gui.expatternprovider.toggle_slots")));
        
        // 添加到左侧工具栏
        this.addToLeftToolbar(this.toggleSlotsButton);
        
        System.out.println("ExtendedAE Plus: 槽位切换按钮已添加到工具栏，默认显示模式: " + this.showSlots);
    }

    @Inject(method = "refreshList", at = @At("HEAD"), remap = false)
    private void onRefreshListStart(CallbackInfo ci) {
        System.out.println("ExtendedAE Plus: refreshList开始执行 - 显示槽位: " + this.showSlots);
        
        // 更新按钮图标
        if (this.toggleSlotsButton != null) {
            this.toggleSlotsButton.setTooltip(Tooltip.create(Component.translatable(
                this.showSlots ? "gui.expatternprovider.hide_slots" : "gui.expatternprovider.show_slots"
            )));
        }
    }

    @Inject(method = "refreshList", at = @At("TAIL"), remap = false)
    private void onRefreshListEnd(CallbackInfo ci) {
        System.out.println("ExtendedAE Plus: refreshList结束 - showSlots状态: " + this.showSlots);
        
        // 在refreshList结束后，根据showSlots状态过滤SlotsRow
        if (!this.showSlots) {
            try {
                // 通过反射访问rows字段 - 先尝试当前类，失败后尝试父类
                java.lang.reflect.Field rowsField = null;
                try {
                    // 先尝试在当前类中查找
                    rowsField = this.getClass().getDeclaredField("rows");
                    System.out.println("ExtendedAE Plus: 在当前类中找到rows字段: " + this.getClass().getSimpleName());
                } catch (NoSuchFieldException e1) {
                    // 如果当前类没有，尝试在父类中查找
                    try {
                        rowsField = this.getClass().getSuperclass().getDeclaredField("rows");
                        System.out.println("ExtendedAE Plus: 在父类中找到rows字段: " + this.getClass().getSuperclass().getSimpleName());
                    } catch (NoSuchFieldException e2) {
                        System.out.println("ExtendedAE Plus: 在当前类和父类中都找不到rows字段");
                        throw e2;
                    }
                }
                rowsField.setAccessible(true);
                java.util.ArrayList<?> rows = (java.util.ArrayList<?>) rowsField.get(this);
                
                System.out.println("ExtendedAE Plus: 找到rows字段，当前行数: " + rows.size());
                
                // 移除所有SlotsRow，只保留GroupHeaderRow
                int removedCount = 0;
                for (int i = rows.size() - 1; i >= 0; i--) {
                    Object row = rows.get(i);
                    String className = row.getClass().getSimpleName();
                    System.out.println("ExtendedAE Plus: 检查行 " + i + "，类型: " + className);
                    if (className.equals("SlotsRow")) {
                        rows.remove(i);
                        removedCount++;
                        System.out.println("ExtendedAE Plus: 移除行 " + i);
                    }
                }
                
                System.out.println("ExtendedAE Plus: 已隐藏 " + removedCount + " 个槽位行，剩余行数: " + rows.size());
                
                // 强制刷新滚动条
                try {
                    java.lang.reflect.Method resetScrollbarMethod = null;
                    try {
                        // 先尝试在当前类中查找
                        resetScrollbarMethod = this.getClass().getDeclaredMethod("resetScrollbar");
                        System.out.println("ExtendedAE Plus: 在当前类中找到resetScrollbar方法: " + this.getClass().getSimpleName());
                    } catch (NoSuchMethodException e1) {
                        // 如果当前类没有，尝试在父类中查找
                        try {
                            resetScrollbarMethod = this.getClass().getSuperclass().getDeclaredMethod("resetScrollbar");
                            System.out.println("ExtendedAE Plus: 在父类中找到resetScrollbar方法: " + this.getClass().getSuperclass().getSimpleName());
                        } catch (NoSuchMethodException e2) {
                            System.out.println("ExtendedAE Plus: 在当前类和父类中都找不到resetScrollbar方法");
                            throw e2;
                        }
                    }
                    
                    resetScrollbarMethod.setAccessible(true);
                    resetScrollbarMethod.invoke(this);
                    System.out.println("ExtendedAE Plus: 滚动条已重置");
                } catch (Exception e) {
                    System.out.println("ExtendedAE Plus: 重置滚动条失败: " + e.getMessage());
                }
            } catch (Exception e) {
                System.out.println("ExtendedAE Plus: 访问rows字段失败: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("ExtendedAE Plus: showSlots为true，不隐藏槽位行");
        }
    }
} 
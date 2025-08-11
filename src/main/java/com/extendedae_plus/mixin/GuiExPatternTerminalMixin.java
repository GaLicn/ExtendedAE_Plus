package com.extendedae_plus.mixin;

import appeng.client.gui.Icon;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import com.glodblock.github.extendedae.network.EPPNetworkHandler;
import com.glodblock.github.glodium.network.packet.CGenericPacket;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import com.glodblock.github.extendedae.client.gui.GuiWirelessExPAT;
import com.glodblock.github.extendedae.container.ContainerExPatternTerminal;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import appeng.api.crafting.PatternDetailsHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiExPatternTerminal.class)
public abstract class GuiExPatternTerminalMixin extends AEBaseScreen<ContainerExPatternTerminal> {

    @Unique
    private IconButton toggleSlotsButton;

    @Unique
    private boolean showSlots = true; // 默认显示槽位
    
    @Unique
    private long currentlychooicepatterprovider = -1; // 当前选择的样板供应器ID
    
    @Unique
    private static final String UPLOAD_SUCCESS_MESSAGE = "✅ ExtendedAE Plus: 样板快速上传成功！";
    
    @Unique
    private static final String UPLOAD_FAILED_MESSAGE = "❌ ExtendedAE Plus: 样板上传失败，请检查供应器状态";
    
    @Unique
    private static final String NO_PROVIDER_MESSAGE = "ExtendedAE Plus: 请先选择一个样板供应器（点击GroupHeader旁的按钮）";

    public GuiExPatternTerminalMixin(ContainerExPatternTerminal menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
    
    /**
     * 获取当前选择的样板供应器ID
     */
    @Unique
    public long getCurrentlyChoicePatternProvider() {
        return currentlychooicepatterprovider;
    }
    
    /**
     * 设置当前选择的样板供应器ID
     */
    @Unique
    public void setCurrentlyChoicePatternProvider(long id) {
        this.currentlychooicepatterprovider = id;
    }
    
    /**
     * 拦截鼠标点击事件，实现Shift+左键快速上传样板功能
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // 检查是否是左键点击 + Shift键
        if (button == 0 && hasShiftDown()) {
            // 获取点击的槽位
            Slot hoveredSlot = this.getSlotUnderMouse();
            if (hoveredSlot != null && hoveredSlot.container == this.minecraft.player.getInventory()) {
                // 点击的是玩家背包槽位
                ItemStack clickedItem = hoveredSlot.getItem();
                
                // 检查是否是有效的编码样板
                if (!clickedItem.isEmpty() && PatternDetailsHelper.isEncodedPattern(clickedItem)) {
                    // 检查是否选择了样板供应器
                    if (currentlychooicepatterprovider != -1) {
                        // 执行快速上传
                        this.quickUploadPattern(hoveredSlot.getSlotIndex());
                        
                        // 取消默认的点击行为
                        cir.setReturnValue(true);
                    } else {
                        // 显示提示消息：请先选择一个样板供应器
                        if (this.minecraft.player != null) {
                            this.minecraft.player.displayClientMessage(
                                Component.literal("ExtendedAE Plus: 请先选择一个样板供应器（点击GroupHeader旁的按钮）"), 
                                true
                            );
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 快速上传样板到当前选择的供应器
     */
    @Unique
    private void quickUploadPattern(int playerSlotIndex) {
        if (this.minecraft.player != null) {
            // 获取要上传的物品
            ItemStack itemToUpload = this.minecraft.player.getInventory().getItem(playerSlotIndex);
            
            if (!itemToUpload.isEmpty() && PatternDetailsHelper.isEncodedPattern(itemToUpload)) {
                // 通过 ExtendedAE 内置网络系统发送通用动作到服务端
                // 动作: "upload"，参数: 槽位索引(int)、供应器ID(long)
                System.out.println("[EAE+][Client] send upload: slot=" + playerSlotIndex + ", provider=" + currentlychooicepatterprovider);
                EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("upload", playerSlotIndex, currentlychooicepatterprovider));
                
            } else {
                this.minecraft.player.displayClientMessage(
                    Component.literal("❌ ExtendedAE Plus: 无效的样板物品"), 
                    true
                );
            }
        }
    }
    
    /**
     * 重置当前选择的样板供应器ID
     */
    @Unique
    public void resetCurrentlyChoicePatternProvider() {
        this.currentlychooicepatterprovider = -1;
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
                
                // 通过反射访问highlightBtns字段
                java.lang.reflect.Field highlightBtnsField = null;
                try {
                    // 先尝试在当前类中查找
                    highlightBtnsField = this.getClass().getDeclaredField("highlightBtns");
                    System.out.println("ExtendedAE Plus: 在当前类中找到highlightBtns字段: " + this.getClass().getSimpleName());
                } catch (NoSuchFieldException e1) {
                    // 如果当前类没有，尝试在父类中查找
                    try {
                        highlightBtnsField = this.getClass().getSuperclass().getDeclaredField("highlightBtns");
                        System.out.println("ExtendedAE Plus: 在父类中找到highlightBtns字段: " + this.getClass().getSuperclass().getSimpleName());
                    } catch (NoSuchFieldException e2) {
                        System.out.println("ExtendedAE Plus: 在当前类和父类中都找不到highlightBtns字段");
                        throw e2;
                    }
                }
                highlightBtnsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.HashMap<Integer, Object> highlightBtns = (java.util.HashMap<Integer, Object>) highlightBtnsField.get(this);
                
                System.out.println("ExtendedAE Plus: 找到highlightBtns字段，当前按钮数: " + highlightBtns.size());
                
                // 创建新的索引映射
                java.util.HashMap<Integer, Object> newHighlightBtns = new java.util.HashMap<>();
                int newIndex = 0;
                
                // 移除所有SlotsRow，只保留GroupHeaderRow，同时重新映射高亮按钮索引
                for (int i = 0; i < rows.size(); i++) {
                    Object row = rows.get(i);
                    String className = row.getClass().getSimpleName();
                    System.out.println("ExtendedAE Plus: 检查行 " + i + "，类型: " + className);
                    
                    if (className.equals("GroupHeaderRow")) {
                        // 保留GroupHeaderRow，并重新映射对应的高亮按钮
                        @SuppressWarnings("unchecked")
                        java.util.ArrayList<Object> typedRows = (java.util.ArrayList<Object>) rows;
                        typedRows.set(newIndex, row);
                        
                        // 查找原来在这个位置的高亮按钮
                        // 原始代码中，高亮按钮的索引是在添加GroupHeaderRow之后、添加第一个SlotsRow之前设置的
                        // 所以按钮的索引指向的是第一个SlotsRow的位置
                        // 我们需要查找索引为 i+1 的按钮（第一个SlotsRow的位置）
                        if (highlightBtns.containsKey(i + 1)) {
                            Object button = highlightBtns.get(i + 1);
                            newHighlightBtns.put(newIndex, button);
                            System.out.println("ExtendedAE Plus: 重新映射高亮按钮，从索引 " + (i + 1) + " 到 " + newIndex);
                        }
                        
                        newIndex++;
                    } else if (className.equals("SlotsRow")) {
                        System.out.println("ExtendedAE Plus: 移除行 " + i);
                        // 不保留SlotsRow，也不增加newIndex
                    }
                }
                
                // 打印所有原始的高亮按钮索引，帮助调试
                System.out.println("ExtendedAE Plus: 原始高亮按钮索引:");
                for (java.util.Map.Entry<Integer, Object> entry : highlightBtns.entrySet()) {
                    System.out.println("ExtendedAE Plus: 索引 " + entry.getKey() + " -> 按钮对象: " + entry.getValue().getClass().getSimpleName());
                }
                
                // 移除多余的行
                while (rows.size() > newIndex) {
                    rows.remove(rows.size() - 1);
                }
                
                // 更新highlightBtns
                highlightBtns.clear();
                highlightBtns.putAll(newHighlightBtns);
                
                System.out.println("ExtendedAE Plus: 已隐藏槽位行，剩余行数: " + rows.size() + "，重新映射的高亮按钮数: " + newHighlightBtns.size());
                
                // 打印所有重新映射的按钮索引
                for (java.util.Map.Entry<Integer, Object> entry : newHighlightBtns.entrySet()) {
                    System.out.println("ExtendedAE Plus: 高亮按钮映射 - 索引 " + entry.getKey() + " -> 按钮对象: " + entry.getValue().getClass().getSimpleName());
                }
                
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
                System.out.println("ExtendedAE Plus: 访问字段失败: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("ExtendedAE Plus: showSlots为true，不隐藏槽位行");
        }
    }
}
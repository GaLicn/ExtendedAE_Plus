package com.extendedae_plus.mixin.extendedae;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.patternaccess.PatternContainerRecord;
import appeng.client.gui.me.patternaccess.PatternSlot;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.AEBaseMenu;
import com.extendedae_plus.util.GuiUtil;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

@Pseudo
@Mixin(GuiExPatternTerminal.class)
public abstract class GuiExPatternTerminalMixin extends AEBaseScreen<AEBaseMenu> {

    @Unique
    private static final String UPLOAD_SUCCESS_MESSAGE = "✅ ExtendedAE Plus: 样板快速上传成功！";
    @Unique
    private static final String UPLOAD_FAILED_MESSAGE = "❌ ExtendedAE Plus: 样板上传失败，请检查供应器状态";
    @Unique
    private static final String NO_PROVIDER_MESSAGE = "ExtendedAE Plus: 请先选择一个样板供应器（点击GroupHeader旁的按钮）";
    @Unique
    private IconButton eap$toggleSlotsButton;
    @Unique
    private boolean eap$showSlots = false; // 默认显示槽位
    @Unique
    private long eap$currentlyChoicePatterProvider = -1; // 当前选择的样板供应器ID
    @Shadow(remap = false) private AETextField searchOutField;
    @Shadow(remap = false) private AETextField searchInField;
    @Shadow(remap = false) private Set<ItemStack> matchedStack;
    @Shadow(remap = false) private Set<PatternContainerRecord> matchedProvider;

    public GuiExPatternTerminalMixin(AEBaseMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Unique
    private static int eap$withAlpha(int rgb, int alpha255) {
        return ((alpha255 & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * 将 HSV 转换为 RGB（返回 0xRRGGBB，不含 alpha）。
     * h: 0.0~1.0，s: 0.0~1.0，v: 0.0~1.0
     */
    @Unique
    private static int eap$hsvToRgb(float h, float s, float v) {
        if (s <= 0.0f) {
            int g = Math.round(v * 255.0f);
            return (g << 16) | (g << 8) | g;
        }
        float hh = (h - (float) Math.floor(h)) * 6.0f;
        int sector = (int) Math.floor(hh);
        float f = hh - sector;
        float p = v * (1.0f - s);
        float q = v * (1.0f - s * f);
        float t = v * (1.0f - s * (1.0f - f));
        float r, g, b;
        switch (sector) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        int ri = Math.round(r * 255.0f);
        int gi = Math.round(g * 255.0f);
        int bi = Math.round(b * 255.0f);
        return (ri << 16) | (gi << 8) | bi;
    }

    /**
     * 获取当前选择的样板供应器ID
     */
    @Unique
    public long getCurrentlyChoicePatternProvider() {
        return eap$currentlyChoicePatterProvider;
    }

    /**
     * 设置当前选择的样板供应器ID
     */
    @Unique
    public void setCurrentlyChoicePatternProvider(long id) {
        this.eap$currentlyChoicePatterProvider = id;
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
                    if (eap$currentlyChoicePatterProvider != -1) {
                        // 执行快速上传
                        this.eap$quickUploadPattern(hoveredSlot.getSlotIndex());

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
    private void eap$quickUploadPattern(int playerSlotIndex) {
        if (this.minecraft.player != null) {
            // 获取要上传的物品
            ItemStack itemToUpload = this.minecraft.player.getInventory().getItem(playerSlotIndex);

            if (!itemToUpload.isEmpty() && PatternDetailsHelper.isEncodedPattern(itemToUpload)) {
                // 通过反射调用 ExtendedAE 的网络发送（软依赖）
                try {
                    Class<?> EPPNetworkHandlerClass = Class.forName("com.glodblock.github.extendedae.network.EPPNetworkHandler");
                    Object handlerInstance = EPPNetworkHandlerClass.getField("INSTANCE").get(null);

                    Class<?> packetClass = Class.forName("com.glodblock.github.glodium.network.packet.CGenericPacket");
                    Constructor<?> constructor = packetClass.getConstructor(String.class, Object[].class);
                    Object packet = constructor.newInstance("upload", new Object[]{playerSlotIndex, eap$currentlyChoicePatterProvider});

                    Class<?> iMessage = Class.forName("com.glodblock.github.glodium.network.packet.IMessage");
                    Method sendToServer = EPPNetworkHandlerClass.getMethod("sendToServer", iMessage);

                    sendToServer.invoke(handlerInstance, packet);
                } catch (Throwable t) {
                    this.minecraft.player.displayClientMessage(
                            Component.literal("❌ ExtendedAE Plus: 未找到 ExtendedAE 网络支持（可能未安装或版本不兼容）"),
                            true
                    );
                }
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
        this.eap$currentlyChoicePatterProvider = -1;
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void injectConstructor(CallbackInfo ci) {
        // 创建切换槽位显示的按钮
        this.eap$toggleSlotsButton = new IconButton((b) -> {
            this.eap$showSlots = !this.eap$showSlots; // 开关状态

            // 通过反射调用refreshList方法 - 先尝试当前类，失败后尝试父类
            try {
                java.lang.reflect.Method refreshMethod = null;
                try {
                    // 先尝试在当前类中查找
                    refreshMethod = this.getClass().getDeclaredMethod("refreshList");
                } catch (NoSuchMethodException e1) {
                    // 如果当前类没有，尝试在父类中查找
                    try {
                        refreshMethod = this.getClass().getSuperclass().getDeclaredMethod("refreshList");
                    } catch (NoSuchMethodException e2) {
                        throw e2;
                    }
                }

                refreshMethod.setAccessible(true);
                refreshMethod.invoke(this);
            } catch (Exception ignored) {
            }
        }) {
            @Override
            protected Icon getIcon() {
                return eap$showSlots ? Icon.PATTERN_ACCESS_HIDE : Icon.PATTERN_ACCESS_SHOW;
            }
        };

        // 设置按钮提示文本
        this.eap$toggleSlotsButton.setTooltip(Tooltip.create(Component.translatable("gui.expatternprovider.toggle_slots")));

        // 添加到左侧工具栏
        this.addToLeftToolbar(this.eap$toggleSlotsButton);
    }

    @Inject(method = "refreshList", at = @At("HEAD"), remap = false)
    private void onRefreshListStart(CallbackInfo ci) {
        // 更新按钮图标
        if (this.eap$toggleSlotsButton != null) {
            this.eap$toggleSlotsButton.setTooltip(Tooltip.create(Component.translatable(
                    this.eap$showSlots ? "gui.expatternprovider.hide_slots" : "gui.expatternprovider.show_slots"
            )));
        }
    }

    @Inject(method = "refreshList", at = @At("TAIL"), remap = false)
    private void onRefreshListEnd(CallbackInfo ci) {

        // 在refreshList结束后，根据showSlots状态过滤SlotsRow
        if (!this.eap$showSlots) {
            try {
                // 通过反射访问rows字段 - 先尝试当前类，失败后尝试父类
                java.lang.reflect.Field rowsField = null;
                try {
                    // 先尝试在当前类中查找
                    rowsField = this.getClass().getDeclaredField("rows");
                } catch (NoSuchFieldException e1) {
                    // 如果当前类没有，尝试在父类中查找
                    try {
                        rowsField = this.getClass().getSuperclass().getDeclaredField("rows");
                    } catch (NoSuchFieldException e2) {
                        throw e2;
                    }
                }
                rowsField.setAccessible(true);
                java.util.ArrayList<?> rows = (java.util.ArrayList<?>) rowsField.get(this);

                // 通过反射访问highlightBtns字段
                java.lang.reflect.Field highlightBtnsField = null;
                try {
                    // 先尝试在当前类中查找
                    highlightBtnsField = this.getClass().getDeclaredField("highlightBtns");
                } catch (NoSuchFieldException e1) {
                    // 如果当前类没有，尝试在父类中查找
                    try {
                        highlightBtnsField = this.getClass().getSuperclass().getDeclaredField("highlightBtns");
                    } catch (NoSuchFieldException e2) {
                        throw e2;
                    }
                }
                highlightBtnsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.HashMap<Integer, Object> highlightBtns = (java.util.HashMap<Integer, Object>) highlightBtnsField.get(this);

                // 创建新的索引映射
                java.util.HashMap<Integer, Object> newHighlightBtns = new java.util.HashMap<>();
                int newIndex = 0;

                // 移除所有SlotsRow，只保留GroupHeaderRow，同时重新映射高亮按钮索引
                for (int i = 0; i < rows.size(); i++) {
                    Object row = rows.get(i);
                    String className = row.getClass().getSimpleName();

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
                        }

                        newIndex++;
                    } else if (className.equals("SlotsRow")) {
                        // 不保留SlotsRow，也不增加newIndex
                    }
                }

                // 移除多余的行
                while (rows.size() > newIndex) {
                    rows.remove(rows.size() - 1);
                }

                // 更新highlightBtns
                highlightBtns.clear();
                highlightBtns.putAll(newHighlightBtns);

                // 强制刷新滚动条
                try {
                    Method resetScrollbarMethod = null;
                    try {
                        // 先尝试在当前类中查找
                        resetScrollbarMethod = this.getClass().getDeclaredMethod("resetScrollbar");
                    } catch (NoSuchMethodException e1) {
                        // 如果当前类没有，尝试在父类中查找
                        try {
                            resetScrollbarMethod = this.getClass().getSuperclass().getDeclaredMethod("resetScrollbar");
                        } catch (NoSuchMethodException e2) {
                            throw e2;
                        }
                    }

                    resetScrollbarMethod.setAccessible(true);
                    resetScrollbarMethod.invoke(this);
                } catch (Exception ignored) {
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Inject(method = "drawFG", at = @At("TAIL"), remap = false)
    private void eap$afterDrawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        // 调用GuiUtil的通用渲染方法显示样板数量
        GuiUtil.renderPatternAmounts(guiGraphics, this);

        // 原有的搜索高亮逻辑
        // 仅当任一搜索框非空时绘制叠加层（与原版行为保持一致）
        boolean searchActive = (this.searchOutField != null && !this.searchOutField.getValue().isEmpty())
                || (this.searchInField != null && !this.searchInField.getValue().isEmpty());
        if (!searchActive) {
            return;
        }

        // 彩虹色的流转：基于时间在 HSV 色环上循环（4 秒为一周期）
        long now = System.currentTimeMillis();
        final long rainbowPeriodMs = 4000L;
        float hue = (now % rainbowPeriodMs) / (float) rainbowPeriodMs; // 0.0 ~ 1.0
        int rainbowRgb = eap$hsvToRgb(hue, 1.0f, 1.0f);

        for (Slot slot : this.menu.slots) {
            if (!(slot instanceof PatternSlot ps)) {
                continue;
            }

            int sx = slot.x;
            int sy = slot.y;

            boolean isMatchedSlot = this.matchedStack != null && this.matchedStack.contains(slot.getItem());
            boolean isMatchedProvider = false;
            try {
                PatternContainerRecord container = ps.getMachineInv();
                isMatchedProvider = this.matchedProvider != null && this.matchedProvider.contains(container);
            } catch (Throwable ignored) {
            }

            // 依据命中状态选择颜色方案
            int borderColor;
            int backgroundColor;

            if (isMatchedSlot) {
                // 命中槽位：使用彩虹色边框与浅底色（固定透明度，呈现色相流转效果）
                borderColor = eap$withAlpha(rainbowRgb, 0xA0);
                backgroundColor = eap$withAlpha(rainbowRgb, 0x3C);
            } else if (!isMatchedProvider) {
                borderColor = eap$withAlpha(0xFFFFFF, 0x40);
                backgroundColor = eap$withAlpha(0x000000, 0x18);
            } else {
                borderColor = eap$withAlpha(0xFFFFFF, 0x30);
                backgroundColor = eap$withAlpha(0xFFFFFF, 0x14);
            }

            // 绘制 18x18 边框（1px 宽）
            eap$fill(guiGraphics, new Rect2i(sx - 1, sy - 1, 18, 1), borderColor);
            eap$fill(guiGraphics, new Rect2i(sx - 1, sy + 16, 18, 1), borderColor);
            eap$fill(guiGraphics, new Rect2i(sx - 1, sy, 1, 16), borderColor);
            eap$fill(guiGraphics, new Rect2i(sx + 16, sy, 1, 16), borderColor);

            // 绘制 16x16 浅底色（半透明，叠加在槽位上方）
            eap$fill(guiGraphics, new Rect2i(sx, sy, 16, 16), backgroundColor);
        }
    }

    @Unique
    private void eap$fill(GuiGraphics guiGraphics, Rect2i rect, int argb) {
        this.fillRect(guiGraphics, rect, argb);
    }
}
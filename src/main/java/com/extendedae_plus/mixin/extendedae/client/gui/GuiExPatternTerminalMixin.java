package com.extendedae_plus.mixin.extendedae.client.gui;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.patternaccess.PatternContainerRecord;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.AEBaseMenu;
import com.extendedae_plus.config.ModConfig;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.mixin.extendedae.accessor.GuiExPatternTerminalGroupHeaderRowAccessor;
import com.extendedae_plus.network.provider.OpenProviderUiC2SPacket;
import com.extendedae_plus.util.GuiUtil;
import com.glodblock.github.extendedae.client.button.HighlightButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import com.glodblock.github.extendedae.network.EPPNetworkHandler;
import com.glodblock.github.glodium.network.packet.CGenericPacket;
import com.google.common.collect.HashMultimap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Pseudo
@Mixin(value = GuiExPatternTerminal.class)
public abstract class GuiExPatternTerminalMixin extends AEBaseScreen<AEBaseMenu> {
    @Shadow(remap = false) @Final private static int GUI_PADDING_X;
    @Shadow(remap = false) @Final private static int GUI_PADDING_Y;
    @Shadow(remap = false) @Final private static int GUI_HEADER_HEIGHT;
    @Shadow(remap = false) @Final private static int ROW_HEIGHT;
    @Shadow(remap = false) @Final private static int TEXT_MAX_WIDTH;
    @Unique private final Map<Integer, Button> eap$openUIButtons = new HashMap<>();
    @Unique private IconButton eap$toggleSlotsButton;
    @Unique private boolean eap$showSlots = false; // 默认由配置初始化
    @Unique private long eap$currentlyChoicePatterProvider = -1; // 当前选择的样板供应器ID
    @Shadow(remap = false) @Final private AETextField searchOutField;
    @Shadow(remap = false) @Final private AETextField searchInField;
    @Shadow(remap = false) @Final private Set<ItemStack> matchedStack;
    @Shadow(remap = false) @Final private Set<PatternContainerRecord> matchedProvider;
    @Shadow(remap = false) @Final private HashMultimap<PatternContainerGroup, PatternContainerRecord> byGroup;
    @Shadow(remap = false) @Final private HashMap<Long, GuiExPatternTerminal.PatternProviderInfo> infoMap;
    @Shadow(remap = false) @Final private Scrollbar scrollbar;
    @Shadow(remap = false) @Final private ArrayList<?> rows;
    @Shadow(remap = false) private int visibleRows;
    @Shadow(remap = false) @Final private HashMap<Integer, HighlightButton> highlightBtns;

    public GuiExPatternTerminalMixin(AEBaseMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
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
     * 注意：某些整合包的 ExtendedAE 版本不在该类中覆写 mouseClicked，此处设置 require=0 以防止注入失败导致崩溃。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
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
                EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("upload", playerSlotIndex, eap$currentlyChoicePatterProvider));
            } else {
                this.minecraft.player.displayClientMessage(
                        Component.literal("❌ ExtendedAE Plus: 无效的样板物品"),
                        true
                );
            }
        }
    }

    /**
     * 尝试打开指定行对应的样板供应器的 UI。
     * 该方法基于 GroupHeaderRow 获取分组信息，再获取该分组下的第一个 PatternContainerRecord，
     * 并通过 serverId 获取 PatternProviderInfo 来发送 C2S 包打开目标供应器界面。
     *
     * @param rowIndex 要操作的行索引
     */
    @Unique
    private void eap$tryOpenProviderUI(int rowIndex) {
        try {
            // 获取指定行
            Object headerRow = rows.get(rowIndex);
            PatternContainerGroup group = ((GuiExPatternTerminalGroupHeaderRowAccessor) headerRow).Group();
            // 获取该组下的所有 PatternContainerRecord
            Set<PatternContainerRecord> containers = byGroup.get(group);
            if (containers == null || containers.isEmpty()) {
                return; // 分组为空，无供应器
            }

            // 取该组下第一个 PatternContainerRecord
            PatternContainerRecord firstRecord = containers.iterator().next();
            long serverId = firstRecord.getServerId(); // 获取供应器服务器 ID

            // 通过 infoMap 获取供应器位置信息
            GuiExPatternTerminal.PatternProviderInfo patternProviderInfo = infoMap.get(serverId);
            if (patternProviderInfo == null) {
                // 如果没有位置信息，提示玩家
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(
                            Component.literal("未找到该供应器的位置信息，无法打开UI"),
                            true
                    );
                }
                return;
            }

            // 获取位置信息和朝向
            BlockPos pos = patternProviderInfo.pos();
            Direction face = patternProviderInfo.face();
            ResourceKey<Level> playerWorld = patternProviderInfo.playerWorld();

            // 转换为 C2S 包所需类型
            long posLong = pos.asLong();
            ResourceLocation dimStr = playerWorld.location();
            int faceOrd = (face != null) ?
                    face.ordinal() :
                    -1;

            // 发送打开 UI 的 C2S 包
            ModNetwork.CHANNEL.sendToServer(new OpenProviderUiC2SPacket(
                    posLong,
                    dimStr,
                    faceOrd
            ));
        } catch (Throwable ignored) {
            // 静默失败，不影响界面操作
        }
    }

    @Shadow
    private void refreshList() {}

    @Shadow
    private void resetScrollbar() {}

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void injectConstructor(CallbackInfo ci) {
        // 根据配置初始化默认显示/隐藏状态
        this.eap$showSlots = ModConfig.INSTANCE.patternTerminalShowSlotsDefault;
        // 创建切换槽位显示的按钮
        this.eap$toggleSlotsButton = new IconButton((b) -> {
            this.eap$showSlots = !this.eap$showSlots; // 开关状态

            refreshList();
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

    /**
     * 处理屏幕缩放（resize）后按钮位置未更新的问题：
     * - 清理并移除现有的“打开UI”按钮
     * - 尝试重置滚动条并刷新列表
     * 缩放后的下一帧，drawFG 会基于新的 leftPos/topPos 重建与定位按钮
     */
    @Inject(method = "resize", at = @At("TAIL"), remap = false, require = 0)
    private void eap$onResize(Minecraft mc, int width, int height, CallbackInfo ci) {
        try {
            // 移除并清理按钮，避免旧位置残留
            this.eap$openUIButtons.values().forEach(this::removeWidget);
            this.eap$openUIButtons.clear();
            refreshList();
            resetScrollbar();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "init", at = @At("TAIL"), remap = false, require = 0)
    private void eap$onInit(CallbackInfo ci) {
        // 清理旧的打开UI按钮
        this.eap$openUIButtons.values().forEach(this::removeWidget);
        this.eap$openUIButtons.clear();
    }

    @Inject(method = "refreshList", at = @At("HEAD"), remap = false)
    private void onRefreshListStart(CallbackInfo ci) {
        // 更新按钮图标
        if (this.eap$toggleSlotsButton != null) {
            this.eap$toggleSlotsButton.setTooltip(Tooltip.create(Component.translatable(
                    this.eap$showSlots ?
                            "gui.expatternprovider.hide_slots" :
                            "gui.expatternprovider.show_slots"
            )));
        }
        // 清理旧的打开UI按钮
        this.eap$openUIButtons.values().forEach(this::removeWidget);
        this.eap$openUIButtons.clear();
    }

    @Inject(method = "refreshList", at = @At("TAIL"), remap = false)
    private void onRefreshListEnd(CallbackInfo ci) {

        // 在refreshList结束后，根据showSlots状态过滤SlotsRow
        if (!this.eap$showSlots) {
            try {
                // 创建新的索引映射
                HashMap<Integer, HighlightButton> newHighlightBtns = new HashMap<>();
                int newIndex = 0;

                // 移除所有SlotsRow，只保留GroupHeaderRow，同时重新映射高亮按钮索引
                for (int i = 0; i < rows.size(); i++) {
                    Object row = rows.get(i);
                    String className = row.getClass().getSimpleName();

                    if (className.equals("GroupHeaderRow")) {
                        // 保留GroupHeaderRow，并重新映射对应的高亮按钮
                        @SuppressWarnings("unchecked")
                        ArrayList<Object> typedRows = (ArrayList<Object>) rows;
                        typedRows.set(newIndex, row);

                        // 查找原来在这个位置的高亮按钮
                        // 原始代码中，高亮按钮的索引是在添加GroupHeaderRow之后、添加第一个SlotsRow之前设置的
                        // 所以按钮的索引指向的是第一个SlotsRow的位置
                        // 我们需要查找索引为 i+1 的按钮（第一个SlotsRow的位置）
                        if (highlightBtns.containsKey(i + 1)) {
                            HighlightButton button = highlightBtns.get(i + 1);
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
                resetScrollbar();
            } catch (Exception ignored) {
            }
        }
    }

    @Inject(method = "drawFG", at = @At("TAIL"), remap = false)
    private void eap$afterDrawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        // 动态放置/创建每个组标题后的“打开UI”按钮
        try {
            int currentScroll = scrollbar.getCurrentScroll();

            // 先隐藏旧按钮，避免残留
            for (Button b : this.eap$openUIButtons.values()) {
                b.visible = false;
            }

            for (int i = 0; i < visibleRows; i++) {
                int rowIndex = currentScroll + i;
                if (rowIndex < 0 || rowIndex >= rows.size()) {
                    continue;
                }
                Object row = rows.get(rowIndex);
                if (!row.getClass().getSimpleName().equals("GroupHeaderRow")) {
                    continue;
                }

                // 放置按钮：位于名称文本右侧，与原类 choiceButton 锚点相邻，向右偏移 20px
                int bx = this.leftPos + GUI_PADDING_X + TEXT_MAX_WIDTH - 11;
                int by = this.topPos + GUI_PADDING_Y + GUI_HEADER_HEIGHT + i * ROW_HEIGHT - 2;

                Button btn = eap$openUIButtons.get(rowIndex);
                if (btn == null) {
                    btn = Button.builder(Component.literal("UI"), (b) -> {
                        eap$tryOpenProviderUI(rowIndex);
                    }).size(14, 12).build();
                    btn.setTooltip(Tooltip.create(Component.literal("打开该供应器目标容器的界面")));
                    eap$openUIButtons.put(rowIndex, btn);
                    this.addRenderableWidget(btn);
                }
                btn.setPosition(bx, by);
                btn.visible = true;
            }
            // 生产环境移除调试日志
        } catch (Throwable ignored) {
        }

        // 原有的搜索高亮逻辑
        // 仅当任一搜索框非空时绘制叠加层（与原版行为保持一致）
        boolean searchActive = (this.searchOutField != null && !this.searchOutField.getValue().isEmpty())
                || (this.searchInField != null && !this.searchInField.getValue().isEmpty());
        if (!searchActive) {
            return;
        }

        // 使用 GuiUtil 的通用绘制方法绘制槽位高亮（包含彩虹流转效果）
        GuiUtil.drawPatternSlotHighlights(guiGraphics, this.menu.slots, this.matchedStack, this.matchedProvider);
    }
}
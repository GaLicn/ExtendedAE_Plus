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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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

import static com.extendedae_plus.util.GlobalSendMessage.sendPlayerMessage;

@Pseudo
@SuppressWarnings({"AddedMixinMembersNamePattern"})
@Mixin(value = GuiExPatternTerminal.class)
public abstract class GuiExPatternTerminalMixin extends AEBaseScreen<AEBaseMenu> {
    @Shadow(remap = false) @Final private static int GUI_PADDING_X;
    @Shadow(remap = false) @Final private static int GUI_PADDING_Y;
    @Shadow(remap = false) @Final private static int GUI_HEADER_HEIGHT;
    @Shadow(remap = false) @Final private static int ROW_HEIGHT;
    @Shadow(remap = false) @Final private static int TEXT_MAX_WIDTH;

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

    /* ----- eap 自有字段 ----- */
    @Unique private final Map<Integer, Button> openUIButtons = new HashMap<>();
    @Unique private IconButton eap$toggleSlotsButton;
    @Unique private boolean eap$showSlots = false; // 默认由配置初始化
    @Unique private long currentlyChoicePatterProvider = -1; // 当前选择的样板供应器ID

    // 按钮更新/缓存状态，避免每帧重建
    @Unique private boolean buttonsDirty = true; // 当列表或布局变化时置 true
    @Unique private int lastScroll = Integer.MIN_VALUE;
    @Unique private int lastRowsSize = Integer.MIN_VALUE;
    @Unique private int lastVisibleRows = Integer.MIN_VALUE;

    public GuiExPatternTerminalMixin(AEBaseMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    /**
     * 设置当前选择的样板供应器ID
     */
    @Unique
    public void setCurrentlyChoicePatternProvider(long id) {
        this.currentlyChoicePatterProvider = id;
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
                    if (currentlyChoicePatterProvider != -1) {
                        // 执行快速上传
                        this.eap$quickUploadPattern(hoveredSlot.getSlotIndex());
                        // 取消默认的点击行为
                        cir.setReturnValue(true);
                    } else {
                        // 显示提示消息：请先选择一个样板供应器
                        sendPlayerMessage(Component.translatable("extendedae_plus.screen.upload.select_provider_first"));
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
        if (this.minecraft.player == null) return;

        ItemStack itemToUpload = this.minecraft.player.getInventory().getItem(playerSlotIndex);
        if (itemToUpload.isEmpty() || !PatternDetailsHelper.isEncodedPattern(itemToUpload)) {
            sendPlayerMessage(Component.translatable("extendedae_plus.screen.upload.invalid_pattern"));
            return;
        }

        // 直接使用软依赖类发送包（EPPNetworkHandler + CGenericPacket 已在 classpath）
        try {
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("upload", playerSlotIndex, currentlyChoicePatterProvider));
        } catch (Throwable t) {
            // 提示玩家网络支持缺失或版本不兼容
            sendPlayerMessage(Component.translatable("extendedae_plus.screen.upload.no_network_support"));
        }
    }

    /**
     * 尝试打开指定行对应的样板供应器的 UI
     * <p>
     * 说明：
     * - 通过 rows 获取 GroupHeaderRow（使用 Accessor 获取 group）
     * - 通过 byGroup 获取对应 PatternContainerRecord 集合，拿第一个 record 的 serverId
     * - 通过 infoMap 获取 PatternProviderInfo，发送 C2S 包打开目标容器界面
     */
    @Unique
    private void eap$tryOpenProviderUI(int rowIndex) {
        try {
            // 获取指定行
            Object headerRow = rows.get(rowIndex);
            PatternContainerGroup group = ((GuiExPatternTerminalGroupHeaderRowAccessor) headerRow).Group();
            // 获取该组下的所有 PatternContainerRecord
            Set<PatternContainerRecord> containers = byGroup.get(group);
            if (containers == null || containers.isEmpty()) return;

            PatternContainerRecord firstRecord = containers.iterator().next();
            long serverId = firstRecord.getServerId();

            GuiExPatternTerminal.PatternProviderInfo info = infoMap.get(serverId);
            if (info == null) return;

            BlockPos pos = info.pos();
            Direction face = info.face();
            ResourceKey<Level> worldKey;
            try {
                // 先尝试新版字段名 world()
                worldKey = (ResourceKey<Level>) info.getClass().getMethod("world").invoke(info);
            } catch (NoSuchMethodException e) {
                // 兼容旧版字段名 playerWorld()
                worldKey = (ResourceKey<Level>) info.getClass().getMethod("playerWorld").invoke(info);
            }
            if (pos == null || worldKey == null) return;

            ModNetwork.CHANNEL.sendToServer(new OpenProviderUiC2SPacket(
                    pos.asLong(),
                    worldKey.location(),
                    face != null ? face.ordinal() : -1
            ));
        } catch (Exception ignored) {}
    }

    /* ----- Shadow 方法 ----- */
    @Shadow(remap = false) private void refreshList() {}
    @Shadow(remap = false) private void resetScrollbar() {}

    /* ----- 构造注入：创建切换按钮（只设置状态并触发一次 refresh） ----- */
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void injectConstructor(CallbackInfo ci) {
        // 初始化默认显示状态
        this.eap$showSlots = ModConfig.INSTANCE.patternTerminalShowSlotsDefault;

        // 创建切换槽位显示的按钮（只切换状态并触发一次 refresh）
        this.eap$toggleSlotsButton = new IconButton((b) -> {
            this.eap$showSlots = !this.eap$showSlots;
            // 标记需要更新按钮与高亮映射
            this.buttonsDirty = true;
            this.refreshList();
            this.resetScrollbar();
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

    @Inject(method = "init", at = @At("TAIL"), remap = false, require = 0)
    private void eap$onInit(CallbackInfo ci) {
        // 清理旧的打开UI按钮并标记需要重建
        this.openUIButtons.values().forEach(this::removeWidget);
        this.openUIButtons.clear();
        this.buttonsDirty = true;
    }

    @Inject(method = "refreshList", at = @At("HEAD"), remap = false)
    private void onRefreshListStart(CallbackInfo ci) {
        // 更新 toggle 按钮 tooltip 文本
        if (this.eap$toggleSlotsButton != null) {
            this.eap$toggleSlotsButton.setTooltip(Tooltip.create(Component.translatable(
                    this.eap$showSlots ? "gui.expatternprovider.hide_slots" : "gui.expatternprovider.show_slots"
            )));
        }
        // 清理并标记需要重建 UI 按钮（但不在此处做重建）
        this.openUIButtons.values().forEach(this::removeWidget);
        this.openUIButtons.clear();
        this.buttonsDirty = true;
    }

    /**
     * refreshList 完成后，如果不显示 slots，则在 rows 上做“压缩”并尽量复用 highlightBtns 映射。
     * 这个实现会尽量复用已有 highlightBtns 的实例，避免无谓的对象重建。
     */
    @Inject(method = "refreshList", at = @At("TAIL"), remap = false)
    private void onRefreshListEnd(CallbackInfo ci) {
        if (!this.eap$showSlots) {
            try {
                HashMap<Integer, HighlightButton> newHighlightBtns = new HashMap<>();
                int newIndex = 0;

                // 遍历 rows，保留 GroupHeaderRow 并尝试复用 highlightBtns（原 index -> 按钮）
                for (int i = 0; i < rows.size(); i++) {
                    Object row = rows.get(i);
                    String className = row.getClass().getSimpleName();

                    if (className.equals("GroupHeaderRow")) {
                        @SuppressWarnings("unchecked")
                        ArrayList<Object> typedRows = (ArrayList<Object>) rows;
                        typedRows.set(newIndex, row);

                        // 原 highlightBtns 在原实现中是放在 GroupHeaderRow 之后第一个 SlotsRow 的 index（i+1）
                        // 尝试复用原映射中 i+1 的按钮（若存在）
                        if (highlightBtns.containsKey(i + 1)) {
                            HighlightButton button = highlightBtns.get(i + 1);
                            newHighlightBtns.put(newIndex, button);
                        }

                        newIndex++;
                    }
                    // SlotsRow：跳过，不保留
                }

                // 移除多余行
                while (rows.size() > newIndex) {
                    rows.remove(rows.size() - 1);
                }

                // 更新 highlightBtns：清理旧 map 并复用 new map
                highlightBtns.clear();
                highlightBtns.putAll(newHighlightBtns);

                // 强制刷新滚动条（一次）
                this.resetScrollbar();
            } catch (Exception ignored) {
            }
        }
        // 标记按钮需要重建（因为 rows 结构可能已改变）
        this.buttonsDirty = true;
    }

    /**
     * drawFG 优化：仅在需要时创建/移除按钮；每帧只更新可见按钮的位置与可见性。
     */
    @Inject(method = "drawFG", at = @At("TAIL"), remap = false)
    private void eap$afterDrawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        try {
            int currentScroll = scrollbar.getCurrentScroll();
            int rowsSize = rows.size();
            int visRows = this.visibleRows;

            // 当列表或滚动或 visibleRows 发生变化时，重建或清理按钮（按需）
            boolean needFullUpdate = this.buttonsDirty
                    || currentScroll != lastScroll
                    || rowsSize != lastRowsSize
                    || visRows != lastVisibleRows;

            if (needFullUpdate) {
                // 清理已经超出范围或者已不存在的按钮
                openUIButtons.entrySet().removeIf(entry -> {
                    int idx = entry.getKey();
                    if (idx < 0 || idx >= rowsSize) {
                        removeWidget(entry.getValue());
                        return true;
                    }
                    return false;
                });

                // 为当前可见窗口内的 GroupHeaderRow 创建按钮（如果不存在）
                for (int i = 0; i < visRows; i++) {
                    int rowIndex = currentScroll + i;
                    if (rowIndex < 0 || rowIndex >= rowsSize) continue;
                    Object row = rows.get(rowIndex);
                    if (!row.getClass().getSimpleName().equals("GroupHeaderRow")) continue;

                    // 计算按钮位置（与原实现保持一致）
                    int bx = this.leftPos + GUI_PADDING_X + TEXT_MAX_WIDTH - 11;
                    int by = this.topPos + GUI_PADDING_Y + GUI_HEADER_HEIGHT + i * ROW_HEIGHT - 2;

                    Button btn = openUIButtons.get(rowIndex);
                    if (btn == null) {
                        btn = Button.builder(
                                Component.literal("UI"),
                                (b) -> eap$tryOpenProviderUI(rowIndex)
                        ).size(14, 12).build();
                        btn.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.screen.open_provider_ui")));
                        openUIButtons.put(rowIndex, btn);
                        this.addRenderableWidget(btn);
                    }
                    btn.setPosition(bx, by);
                    btn.visible = true;
                }

                // 将不在当前可见窗口内的按钮隐藏
                for (Map.Entry<Integer, Button> e : openUIButtons.entrySet()) {
                    int idx = e.getKey();
                    if (idx < currentScroll || idx >= currentScroll + visRows) {
                        e.getValue().visible = false;
                    }
                }

                // 更新缓存状态
                this.lastScroll = currentScroll;
                this.lastRowsSize = rowsSize;
                this.lastVisibleRows = visRows;
                this.buttonsDirty = false;
            } else {
                // 每帧只更新可见按钮位置（可能因为 leftPos/topPos 动态变化，比如移动窗口）
                for (int i = 0; i < visRows; i++) {
                    int rowIndex = currentScroll + i;
                    Button btn = openUIButtons.get(rowIndex);
                    if (btn == null) continue;
                    int bx = this.leftPos + GUI_PADDING_X + TEXT_MAX_WIDTH - 11;
                    int by = this.topPos + GUI_PADDING_Y + GUI_HEADER_HEIGHT + i * ROW_HEIGHT - 2;
                    btn.setPosition(bx, by);
                    btn.visible = true;
                }
            }
        } catch (Throwable ignored) {
        }

        // 原有的搜索高亮绘制（仅在搜索激活时绘制）
        boolean searchActive = (this.searchOutField != null && !this.searchOutField.getValue().isEmpty())
                || (this.searchInField != null && !this.searchInField.getValue().isEmpty());
        if (!searchActive) {
            return;
        }

        GuiUtil.drawPatternSlotHighlights(guiGraphics, this.menu.slots, this.matchedStack, this.matchedProvider);
    }
}
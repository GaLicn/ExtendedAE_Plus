package com.extendedae_plus.mixin.extendedae.client.gui;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.patternaccess.PatternContainerRecord;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.AEBaseMenu;
import com.extendedae_plus.api.IExPatternTerminalSelection;
import com.extendedae_plus.config.ModConfigs;
import com.extendedae_plus.mixin.extendedae.accessor.GuiExPatternTerminalAccessor;
import com.extendedae_plus.mixin.extendedae.accessor.GuiExPatternTerminalGroupHeaderRowAccessor;
import com.extendedae_plus.network.OpenProviderUiC2SPacket;
import com.glodblock.github.extendedae.client.button.HighlightButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Pseudo
@Mixin(value = GuiExPatternTerminal.class, remap = false)
public abstract class GuiExPatternTerminalMixin extends AEBaseScreen<AEBaseMenu>
        implements IExPatternTerminalSelection {

    @Shadow(remap = false) @Final private static int GUI_PADDING_X;
    @Shadow(remap = false) @Final private static int GUI_PADDING_Y;
    @Shadow(remap = false) @Final private static int GUI_HEADER_HEIGHT;
    @Shadow(remap = false) @Final private static int ROW_HEIGHT;
    @Shadow(remap = false) @Final private static int TEXT_MAX_WIDTH;

    @Unique
    private IconButton eap$toggleSlotsButton;
    @Unique
    private boolean eap$showSlots;
    @Unique
    private long eap$currentlyChoicePatterProvider = -1;
    @Unique
    private final Map<Integer, Button> eap$openUIButtons = new HashMap<>();

    protected GuiExPatternTerminalMixin(AEBaseMenu menu, Inventory playerInventory, Component title,
                                        ScreenStyle style) {
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
        eap$setCurrentlyChoicePatternProvider(id);
    }

    @Override
    @Unique
    public void eap$setCurrentlyChoicePatternProvider(long providerId) {
        eap$currentlyChoicePatterProvider = providerId;
    }

    /**
     * 拦截鼠标点击事件，实现Shift+左键快速上传样板功能
     * 注意：某些整合包的 ExtendedAE 版本不在该类中覆写 mouseClicked，此处设置 require=0 以防止注入失败导致崩溃。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void eap$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !hasShiftDown() || minecraft.player == null) {
            return;
        }

        Slot hoveredSlot = getSlotUnderMouse();
        if (hoveredSlot == null || hoveredSlot.container != minecraft.player.getInventory()) {
            return;
        }

        ItemStack clickedItem = hoveredSlot.getItem();
        if (clickedItem.isEmpty() || !PatternDetailsHelper.isEncodedPattern(clickedItem)) {
            return;
        }

        if (eap$currentlyChoicePatterProvider != -1) {
            eap$quickUploadPattern(hoveredSlot.getSlotIndex());
            cir.setReturnValue(true);
        } else {
            minecraft.player.displayClientMessage(
                    Component.translatable("extendedae_plus.message.provider.select_first"), true);
        }
    }

    /**
     * 快速上传样板到当前选择的供应器
     */
    @Unique
    private void eap$quickUploadPattern(int playerSlotIndex) {
        if (minecraft.player == null) {
            return;
        }

        ItemStack itemToUpload = minecraft.player.getInventory().getItem(playerSlotIndex);
        if (itemToUpload.isEmpty() || !PatternDetailsHelper.isEncodedPattern(itemToUpload)) {
            minecraft.player.displayClientMessage(
                    Component.translatable("extendedae_plus.message.upload.invalid_item"), true);
            return;
        }

        try {
            PacketDistributor.sendToServer(new com.extendedae_plus.network.UploadInventoryPatternToProviderC2SPacket(
                    playerSlotIndex, eap$currentlyChoicePatterProvider));
        } catch (Throwable ignored) {
            minecraft.player.displayClientMessage(
                    Component.translatable("extendedae_plus.message.upload.client_fail"), true);
        }
    }

    @Unique
    private void eap$tryOpenProviderUI(int rowIndex) {
        try {
            GuiExPatternTerminalAccessor terminal = (GuiExPatternTerminalAccessor) (Object) this;
            ArrayList<?> rows = terminal.getRows();
            Object row = rows.get(rowIndex);
            if (!(row instanceof GuiExPatternTerminalGroupHeaderRowAccessor header)) {
                return;
            }

            PatternContainerGroup group = header.eap$getGroup();
            Set<PatternContainerRecord> containers = terminal.eap$getByGroup().get(group);
            if (containers == null || containers.isEmpty()) {
                return;
            }

            PatternContainerRecord record = containers.iterator().next();
            GuiExPatternTerminal.PatternProviderInfo info = terminal.eap$getInfoMap().get(record.getServerId());
            if (info == null || info.pos() == null || info.world() == null) {
                return;
            }

            Direction face = info.face();
            PacketDistributor.sendToServer(new OpenProviderUiC2SPacket(
                    info.pos().asLong(),
                    info.world().location(),
                    face == null ? -1 : face.ordinal()));
        } catch (Throwable ignored) {
        }
    }

    /**
     * 重置当前选择的样板供应器ID
     */
    @Unique
    public void resetCurrentlyChoicePatternProvider() {
        this.eap$currentlyChoicePatterProvider = -1;
    }

    @Inject(method = "<init>(Lcom/glodblock/github/extendedae/container/ContainerExPatternTerminal;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/chat/Component;Lappeng/client/gui/style/ScreenStyle;)V", at = @At("TAIL"), remap = false, require = 0)
    private void eap$injectConstructor(
            com.glodblock.github.extendedae.container.ContainerExPatternTerminal menu,
            Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        try {
            eap$showSlots = ModConfigs.PATTERN_TERMINAL_SHOW_SLOTS_DEFAULT.get();
        } catch (Throwable ignored) {
            eap$showSlots = false;
        }

        eap$toggleSlotsButton = new IconButton(button -> {
            eap$showSlots = !eap$showSlots;
            try {
                ((GuiExPatternTerminalAccessor) (Object) this).eap$refreshList();
            } catch (Throwable ignored) {
            }
        }) {
            @Override
            protected Icon getIcon() {
                return eap$showSlots ? Icon.PATTERN_ACCESS_HIDE : Icon.PATTERN_ACCESS_SHOW;
            }
        };
        eap$toggleSlotsButton.setTooltip(Tooltip.create(
                Component.translatable("gui.expatternprovider.toggle_slots")));
        addToLeftToolbar(eap$toggleSlotsButton);
    }

    /**
     * 处理屏幕缩放（resize）后按钮位置未更新的问题：
     * - 清理并移除现有的“打开UI”按钮
     * - 尝试重置滚动条并刷新列表
     * 缩放后的下一帧，drawFG 会基于新的 leftPos/topPos 重建与定位按钮
     */
    @Inject(method = "resize", at = @At("TAIL"), remap = false, require = 0)
    private void eap$onResize(Minecraft mc, int width, int height, CallbackInfo ci) {
        eap$clearOpenButtons();
        try {
            GuiExPatternTerminalAccessor terminal = (GuiExPatternTerminalAccessor) (Object) this;
            terminal.eap$resetScrollbar();
            terminal.eap$refreshList();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "init", at = @At("TAIL"), remap = false, require = 0)
    private void eap$onInit(CallbackInfo ci) {
        eap$clearOpenButtons();
    }

    @Inject(method = "refreshList", at = @At("HEAD"), remap = false, require = 0)
    private void eap$onRefreshListStart(CallbackInfo ci) {
        if (eap$toggleSlotsButton != null) {
            eap$toggleSlotsButton.setTooltip(Tooltip.create(Component.translatable(
                    eap$showSlots ? "gui.expatternprovider.hide_slots" : "gui.expatternprovider.show_slots")));
        }
        eap$clearOpenButtons();
    }

    @Inject(method = "refreshList", at = @At("TAIL"), remap = false, require = 0)
    private void eap$onRefreshListEnd(CallbackInfo ci) {
        if (eap$showSlots) {
            return;
        }

        try {
            GuiExPatternTerminalAccessor terminal = (GuiExPatternTerminalAccessor) (Object) this;
            @SuppressWarnings("unchecked")
            ArrayList<Object> rows = (ArrayList<Object>) (ArrayList<?>) terminal.getRows();
            HashMap<Integer, HighlightButton> highlightButtons = terminal.eap$getHighlightButtons();
            HashMap<Integer, HighlightButton> rebuiltButtons = new HashMap<>();
            int newIndex = 0;

            for (int index = 0; index < rows.size(); index++) {
                Object row = rows.get(index);
                if (!(row instanceof GuiExPatternTerminalGroupHeaderRowAccessor)) {
                    continue;
                }

                rows.set(newIndex, row);
                HighlightButton button = highlightButtons.get(index + 1);
                if (button != null) {
                    rebuiltButtons.put(newIndex, button);
                }
                newIndex++;
            }

            while (rows.size() > newIndex) {
                rows.removeLast();
            }
            highlightButtons.clear();
            highlightButtons.putAll(rebuiltButtons);
            terminal.eap$resetScrollbar();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "drawFG", at = @At("TAIL"), remap = false, require = 0)
    private void eap$afterDrawFG(GuiGraphics guiGraphics, int offsetX, int offsetY,
                                 int mouseX, int mouseY, CallbackInfo ci) {
        try {
            GuiExPatternTerminalAccessor terminal = (GuiExPatternTerminalAccessor) (Object) this;
            ArrayList<?> rows = terminal.getRows();
            int currentScroll = terminal.getScrollbar().getCurrentScroll();
            int visibleRows = terminal.getVisibleRows();

            for (Button button : eap$openUIButtons.values()) {
                button.visible = false;
            }

            for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
                int rowIndex = currentScroll + visibleIndex;
                if (rowIndex < 0 || rowIndex >= rows.size()) {
                    continue;
                }
                if (!(rows.get(rowIndex) instanceof GuiExPatternTerminalGroupHeaderRowAccessor)) {
                    continue;
                }

                int x = leftPos + GUI_PADDING_X + TEXT_MAX_WIDTH - 11;
                int y = topPos + GUI_PADDING_Y + GUI_HEADER_HEIGHT + visibleIndex * ROW_HEIGHT - 2;
                Button button = eap$openUIButtons.computeIfAbsent(rowIndex, index -> {
                    Button created = Button.builder(Component.literal("UI"), b -> eap$tryOpenProviderUI(index))
                            .size(14, 12)
                            .build();
                    created.setTooltip(Tooltip.create(Component.translatable(
                            "extendedae_plus.tooltip.provider.open_ui")));
                    addRenderableWidget(created);
                    return created;
                });
                button.setPosition(x, y);
                button.visible = true;
            }
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private void eap$clearOpenButtons() {
        eap$openUIButtons.values().forEach(this::removeWidget);
        eap$openUIButtons.clear();
    }
}

package com.extendedae_plus.mixin.extendedae.client.gui;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import com.extendedae_plus.api.IExPatternButton;
import com.extendedae_plus.api.IExPatternPage;
import com.extendedae_plus.client.gui.NewIcon;
import com.extendedae_plus.compat.UpgradeSlotCompat;
import com.extendedae_plus.network.ScalePatternsC2SPacket;
import com.glodblock.github.extendedae.client.button.ActionEPPButton;
import com.glodblock.github.extendedae.client.gui.GuiExPatternProvider;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

@Mixin(value = GuiExPatternProvider.class, remap = false)
public abstract class GuiExPatternProviderMixin extends PatternProviderScreen<ContainerExPatternProvider> implements IExPatternButton, IExPatternPage {

    @Unique
    private static final int SLOTS_PER_PAGE = 36; // 每页显示36个槽位
    @Unique private
    ScreenStyle eap$screenStyle;
    // 跟踪上次屏幕尺寸，处理 GUI 缩放/窗口大小变化后按钮丢失问题
    @Unique private int eap$lastScreenWidth = -1;

    // 不再使用右侧 VerticalButtonBar，直接把按钮注册为独立 AE2 小部件
    @Unique private int eap$lastScreenHeight = -1;
    @Unique
    private int eap$currentPage = 0;

    @Unique
    private int eap$maxPageLocal = 1;
    private ActionEPPButton nextPage;



    // 移除手动挪动 Slot 坐标，交由 SlotGridLayout + 原生布局控制
    private ActionEPPButton prevPage;
    private ActionEPPButton x2Button;
    private ActionEPPButton divideBy2Button;
    private ActionEPPButton x5Button;
    private ActionEPPButton divideBy5Button;
    public GuiExPatternProviderMixin(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Unique
    private static Field eap$findFieldRecursive(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }

    @Unique
    private static void eap$setIntFieldRecursive(Object obj, String name, int value) {
        if (obj == null) return;
        Field f = eap$findFieldRecursive(obj.getClass(), name);
        if (f != null) {
            try { f.setAccessible(true); f.set(obj, value); } catch (Throwable ignored) {}
        }
    }

    @Unique
    private static int eap$getIntFieldRecursive(Object obj, String name, int def) {
        if (obj == null) return def;
        Field f = eap$findFieldRecursive(obj.getClass(), name);
        if (f != null) {
            try {
                f.setAccessible(true);
                Object value = f.get(obj);
                if (value instanceof Integer i) {
                    return i;
                }
            } catch (Throwable ignored) {}
        }
        return def;
    }

    @Unique
    private int getCurrentPage() {
        // 优先使用本地 GUI 维护的页码
        return Math.max(0, this.eap$currentPage % Math.max(1, this.eap$maxPageLocal));
    }

    @Unique
    private int getMaxPage() {
        return this.eap$syncMaxPageState();
    }

    @Unique
    private int eap$getUnlockedPagesFromUpgradeSlots() {
        return UpgradeSlotCompat.getUnlockedExtendedPatternProviderPages(this.getMenu().getSlots(SlotSemantics.UPGRADE).stream()
                .map(net.minecraft.world.inventory.Slot::getItem)
                .toList());
    }

    @Unique
    private int eap$syncMaxPageState() {
        int previousPage = this.eap$currentPage;
        int totalPages = 1;
        try {
            int totalSlots = this.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN).size();
            totalPages = Math.max(1, (int) Math.ceil(totalSlots / (double) SLOTS_PER_PAGE));
        } catch (Throwable ignored) {}

        int syncedUnlockedPages = eap$getIntFieldRecursive(this.getMenu(), "eap$unlockedMaxPage", 0);
        int unlockedPages = syncedUnlockedPages > 0
                ? Math.max(1, Math.min(totalPages, syncedUnlockedPages))
                : Math.max(1, Math.min(totalPages, this.eap$getUnlockedPagesFromUpgradeSlots()));
        this.eap$maxPageLocal = unlockedPages;

        int syncedPage = eap$getIntFieldRecursive(this.getMenu(), "eap$page",
                eap$getIntFieldRecursive(this.getMenu(), "page", this.eap$currentPage));
        this.eap$currentPage = Math.max(0, Math.min(syncedPage, unlockedPages - 1));

        eap$setIntFieldRecursive(this.getMenu(), "eap$unlockedMaxPage", unlockedPages);
        eap$setIntFieldRecursive(this.getMenu(), "maxPage", unlockedPages);
        eap$setIntFieldRecursive(this.getMenu(), "eap$page", this.eap$currentPage);
        eap$setIntFieldRecursive(this.getMenu(), "page", this.eap$currentPage);

        if (previousPage != this.eap$currentPage) {
            try {
                this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
                this.repositionSlots(SlotSemantics.STORAGE);
                this.hoveredSlot = null;
            } catch (Throwable ignored) {}
        }
        return unlockedPages;
    }
    
    // 在构造器返回后初始化按钮与翻页控制
    @Inject(method = "<init>", at = @At("RETURN"))
    private void injectInit(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        this.eap$screenStyle = style;
        // 保留：不再打印菜单类型

        this.eap$maxPageLocal = this.eap$syncMaxPageState();
        this.eap$currentPage = 0;

        this.prevPage = new ActionEPPButton((b) -> {
            int currentPage = this.getCurrentPage();
            int maxPage = this.getMaxPage();
            int newPage = (currentPage - 1 + maxPage) % maxPage;
            try {
                ContainerExPatternProvider menu1 = this.getMenu();
                try {
                    Method setPageMethod = menu1.getClass().getMethod("setPage", int.class);
                    setPageMethod.invoke(menu1, newPage);
                } catch (Throwable ignored2) {}
                eap$setIntFieldRecursive(menu1, "eap$page", newPage);
                eap$setIntFieldRecursive(menu1, "page", newPage);
            } catch (Exception ignored) {}
            this.eap$currentPage = newPage;
            LOGGER.info("[EAP] PrevPage clicked: {} -> {} (max={})", currentPage, newPage, maxPage);
            this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
            this.repositionSlots(SlotSemantics.STORAGE);
            this.hoveredSlot = null;
            this.eap$updatePageSlotActivity();
        }, Icon.ARROW_LEFT);

        this.nextPage = new ActionEPPButton((b) -> {
            int currentPage = this.getCurrentPage();
            int maxPage = this.getMaxPage();
            int newPage = (currentPage + 1) % maxPage;
            try {
                ContainerExPatternProvider menu1 = this.getMenu();
                try {
                    Method setPageMethod = menu1.getClass().getMethod("setPage", int.class);
                    setPageMethod.invoke(menu1, newPage);
                } catch (Throwable ignored2) {}
                eap$setIntFieldRecursive(menu1, "eap$page", newPage);
                eap$setIntFieldRecursive(menu1, "page", newPage);
            } catch (Exception ignored) {}
            this.eap$currentPage = newPage;
            LOGGER.info("[EAP] NextPage clicked: {} -> {} (max={})", currentPage, newPage, maxPage);
            this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
            this.repositionSlots(SlotSemantics.STORAGE);
            this.hoveredSlot = null;
            this.eap$updatePageSlotActivity();
        }, Icon.ARROW_RIGHT);

        this.addToLeftToolbar(this.nextPage);
        this.addToLeftToolbar(this.prevPage);

        // 倍增/除法按钮：使用自有 C2S 包发送到服务端执行样板缩放
        this.x2Button = new ActionEPPButton((b) -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new ScalePatternsC2SPacket(ScalePatternsC2SPacket.Operation.MUL2));
        }, NewIcon.MULTIPLY2);
        this.x2Button.setVisibility(true);

        this.divideBy2Button = new ActionEPPButton((b) -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new ScalePatternsC2SPacket(ScalePatternsC2SPacket.Operation.DIV2));
        }, NewIcon.DIVIDE2);
        this.divideBy2Button.setVisibility(true);

        this.divideBy5Button = new ActionEPPButton((b) -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new ScalePatternsC2SPacket(ScalePatternsC2SPacket.Operation.DIV5));
        }, NewIcon.DIVIDE5);
        this.divideBy5Button.setVisibility(true);

        this.x5Button = new ActionEPPButton((b) -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new ScalePatternsC2SPacket(ScalePatternsC2SPacket.Operation.MUL5));
        }, NewIcon.MULTIPLY5);
        this.x5Button.setVisibility(true);

        // 注册可渲染按钮
        this.addRenderableWidget(this.divideBy2Button);
        this.addRenderableWidget(this.x2Button);
        this.addRenderableWidget(this.divideBy5Button);
        this.addRenderableWidget(this.x5Button);
    }

    @Override
    public int eap$getCurrentPage() {
        return this.getCurrentPage();
    }

    // 页码文本绘制移交给 AEBaseScreenMixin.renderLabels 尾部执行

    // 注意：不再注入 Screen#init，避免混入在某些映射情况下失败导致 TransformerError
    
    @Override
    public void eap$updateButtonsLayout() {
        this.eap$syncMaxPageState();

        // 只处理按钮可见性与定位，不再强制 showPage 或挪动 Slot 坐标，避免与原布局/tooltip 冲突
        if (this.nextPage != null && this.prevPage != null) {
            boolean showPageButtons = this.eap$maxPageLocal > 1;
            this.nextPage.setVisibility(showPageButtons);
            this.prevPage.setVisibility(showPageButtons);
        }
        if (this.x2Button != null) {
            this.x2Button.setVisibility(true);
        }
        if (this.divideBy2Button != null) {
            this.divideBy2Button.setVisibility(true);
        }
        if (this.divideBy5Button != null) {
            this.divideBy5Button.setVisibility(true);
        }
        if (this.x5Button != null) {
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
        } catch (Throwable ignored) {}

        // 如果屏幕尺寸发生变化（窗口/GUI缩放），重新注册右侧外列的自定义按钮，翻页按钮由左侧工具栏托管
        if (this.width != this.eap$lastScreenWidth || this.height != this.eap$lastScreenHeight) {
            this.eap$lastScreenWidth = this.width;
            this.eap$lastScreenHeight = this.height;
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
            } catch (Throwable ignored) {}
        }

        // 定位到 GUI 右缘外侧一点（使用绝对屏幕坐标）
        int bx = this.leftPos + this.imageWidth + 1; // 向右平移 1px 到面板外侧
        int by = this.topPos + 104;
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

        // 每帧确保当前页槽位处于启用状态，非当前页禁用
        this.eap$updatePageSlotActivity();
    }

    // 本文件原包含本地样板缩放实现（单机模式）和 ExtendedAE 网络派发，已移除以兼容 1.21.1 与最小可构建集。
    

    @Unique
    private void eap$updatePageSlotActivity() {
        try {
            if (!(((Object) this) instanceof GuiExPatternProvider)) return;
            var list = this.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN);
            if (list == null || list.isEmpty()) return;

            int currentPage = this.getCurrentPage();
            int base = currentPage * SLOTS_PER_PAGE;
            int end = Math.min(list.size(), base + SLOTS_PER_PAGE);
            int unlockedSlots = Math.min(list.size(), this.eap$maxPageLocal * SLOTS_PER_PAGE);

            for (int i = 0; i < list.size(); i++) {
                var slot = list.get(i);
                if (slot instanceof AppEngSlot s) {
                    boolean enabled = i < unlockedSlots && i >= base && i < end;
                    s.setActive(enabled);
                }
            }
        } catch (Throwable ignored) {}
    }

}

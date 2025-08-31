package com.extendedae_plus.mixin.extendedae.client.gui;

import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import com.extendedae_plus.NewIcon;
import com.extendedae_plus.api.ExPatternButtonsAccessor;
import com.extendedae_plus.config.ModConfigs;
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

import java.lang.reflect.Field;

import static com.extendedae_plus.util.ExtendedAELogger.LOGGER;

@Mixin(GuiExPatternProvider.class)
public abstract class GuiExPatternProviderMixin extends PatternProviderScreen<ContainerExPatternProvider> implements ExPatternButtonsAccessor, com.extendedae_plus.api.ExPatternPageAccessor {

    @Unique
    ScreenStyle eap$screenStyle;

    // 跟踪上次屏幕尺寸，处理 GUI 缩放/窗口大小变化后按钮丢失问题
    @Unique private int eap$lastScreenWidth = -1;
    @Unique private int eap$lastScreenHeight = -1;

    // 不再使用右侧 VerticalButtonBar，直接把按钮注册为独立 AE2 小部件

    @Unique
    private static final int SLOTS_PER_PAGE = 36; // 每页显示36个槽位

    @Unique
    private int eap$currentPage = 0;

    @Unique
    private int eap$maxPageLocal = 1;

    public GuiExPatternProviderMixin(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    
    
    // 移除手动挪动 Slot 坐标，交由 SlotGridLayout + 原生布局控制

    @Unique
    private int getCurrentPage() {
        // 优先使用本地 GUI 维护的页码
        return Math.max(0, eap$currentPage % Math.max(1, eap$maxPageLocal));
    }

    @Unique
    private int getMaxPage() {
        // 优先使用配置倍数
        try {
            int cfg = ModConfigs.PAGE_MULTIPLIER.get();
            if (cfg > 1) return cfg;
        } catch (Throwable ignored) {}
        try {
            ContainerExPatternProvider menu1 = this.getMenu();
            Field fieldMaxPage = eap$findFieldRecursive(menu1.getClass(), "maxPage");
            if (fieldMaxPage != null) {
                fieldMaxPage.setAccessible(true);
                Object v = fieldMaxPage.get(menu1);
                if (v instanceof Integer i) return i;
            }
        } catch (Throwable ignored) {}
        // 回退：用槽位总数计算
        try {
            int totalSlots = this.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN).size();
            return Math.max(1, (int) Math.ceil(totalSlots / (double) SLOTS_PER_PAGE));
        } catch (Throwable ignored) {}
        return 1;
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

    public ActionEPPButton nextPage;
    public ActionEPPButton prevPage;
    public ActionEPPButton x2Button;
    public ActionEPPButton divideBy2Button;
    public ActionEPPButton x5Button;
    public ActionEPPButton divideBy5Button;
    public ActionEPPButton x10Button;
    public ActionEPPButton divideBy10Button;
    
    // 在构造器返回后初始化按钮与翻页控制
    @Inject(method = "<init>", at = @At("RETURN"))
    private void injectInit(ContainerExPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        this.eap$screenStyle = style;
        // 保留：不再打印菜单类型

        // 计算并下发 maxPage（配置优先，其次按槽位总数计算）
        int totalSlots = this.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN).size();
        int cfgPages = 1;
        try { cfgPages = Math.max(1, ModConfigs.PAGE_MULTIPLIER.get()); } catch (Throwable ignored) {}
        int calcPages = Math.max(1, (int) Math.ceil(totalSlots / (double) SLOTS_PER_PAGE));
        int desiredMaxPage = Math.max(cfgPages, calcPages);
        LOGGER.info("[EAP] GuiExPatternProvider init: totalSlots={}, cfgPages={}, calcPages={}, desiredMaxPage={}", totalSlots, cfgPages, calcPages, desiredMaxPage);
        // 更新本地最大页
        this.eap$maxPageLocal = Math.max(1, desiredMaxPage);
        this.eap$currentPage = 0;
        try {
            Field fMax = eap$findFieldRecursive(menu.getClass(), "maxPage");
            if (fMax != null) { fMax.setAccessible(true); fMax.set(menu, desiredMaxPage); }
        } catch (Throwable ignored) {}

        // 翻页按钮（当存在多页时显示；支持仅由配置决定的“空白页”）
        if (desiredMaxPage > 1) {
            this.prevPage = new ActionEPPButton((b) -> {
                int currentPage = getCurrentPage();
                int maxPage = Math.max(this.eap$maxPageLocal, getMaxPage());
                int newPage = (currentPage - 1 + maxPage) % maxPage;
                try {
                    ContainerExPatternProvider menu1 = this.getMenu();
                    // 尝试调用 setPage
                    try {
                        java.lang.reflect.Method setPageMethod = menu1.getClass().getMethod("setPage", int.class);
                        setPageMethod.invoke(menu1, newPage);
                    } catch (Throwable ignored2) {}
                    // 直接写入 page 字段，确保生效
                    Field f = eap$findFieldRecursive(menu1.getClass(), "page");
                    if (f != null) {
                        f.setAccessible(true);
                        f.set(menu1, newPage);
                    }
                } catch (Exception ignored) {}
                // 同步到本地 GUI 页码
                this.eap$currentPage = newPage;
                // 日志与强制重排（放在更新本地页码之后，确保布局读取到新页）
                LOGGER.info("[EAP] PrevPage clicked: {} -> {} (max={})", currentPage, newPage, maxPage);
                this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
                this.repositionSlots(SlotSemantics.STORAGE);
                this.hoveredSlot = null;
            }, Icon.ARROW_LEFT);

            this.nextPage = new ActionEPPButton((b) -> {
                int currentPage = getCurrentPage();
                int maxPage = Math.max(this.eap$maxPageLocal, getMaxPage());
                int newPage = (currentPage + 1) % maxPage;
                try {
                    ContainerExPatternProvider menu1 = this.getMenu();
                    // 尝试调用 setPage
                    try {
                        java.lang.reflect.Method setPageMethod = menu1.getClass().getMethod("setPage", int.class);
                        setPageMethod.invoke(menu1, newPage);
                    } catch (Throwable ignored2) {}
                    // 直接写入 page 字段，确保生效
                    Field f = eap$findFieldRecursive(menu1.getClass(), "page");
                    if (f != null) {
                        f.setAccessible(true);
                        f.set(menu1, newPage);
                    }
                } catch (Exception ignored) {}
                // 同步到本地 GUI 页码
                this.eap$currentPage = newPage;
                // 日志与强制重排（放在更新本地页码之后，确保布局读取到新页）
                LOGGER.info("[EAP] NextPage clicked: {} -> {} (max={})", currentPage, newPage, maxPage);
                this.repositionSlots(SlotSemantics.ENCODED_PATTERN);
                this.repositionSlots(SlotSemantics.STORAGE);
                this.hoveredSlot = null;
            }, Icon.ARROW_RIGHT);

            // 恢复到 AE2 左侧工具栏
            this.addToLeftToolbar(this.nextPage);
            this.addToLeftToolbar(this.prevPage);
        }

        // 倍增/除法按钮，通过 ExtendedAE 的通用包派发
        this.x2Button = new ActionEPPButton((b) -> {
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("multiply2"));
        }, NewIcon.MULTIPLY2);
        this.x2Button.setVisibility(true);

        this.divideBy2Button = new ActionEPPButton((b) -> {
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("divide2"));
        }, NewIcon.DIVIDE2);
        this.divideBy2Button.setVisibility(true);

        this.x10Button = new ActionEPPButton((b) -> {
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("multiply10"));
        }, NewIcon.MULTIPLY10);
        this.x10Button.setVisibility(true);

        this.divideBy10Button = new ActionEPPButton((b) -> {
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("divide10"));
        }, NewIcon.DIVIDE10);
        this.divideBy10Button.setVisibility(true);

        this.divideBy5Button = new ActionEPPButton((b) -> {
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("divide5"));
        }, NewIcon.DIVIDE5);
        this.divideBy5Button.setVisibility(true);

        this.x5Button = new ActionEPPButton((b) -> {
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("multiply5"));
        }, NewIcon.MULTIPLY5);
        this.x5Button.setVisibility(true);

        // 注册可渲染按钮
        this.addRenderableWidget(this.divideBy2Button);
        this.addRenderableWidget(this.x2Button);
        this.addRenderableWidget(this.divideBy5Button);
        this.addRenderableWidget(this.x5Button);
        this.addRenderableWidget(this.divideBy10Button);
        this.addRenderableWidget(this.x10Button);
    }

    @Override
    public int eap$getCurrentPage() {
        return getCurrentPage();
    }

    // 页码文本绘制移交给 AEBaseScreenMixin.renderLabels 尾部执行

    // 注意：不再注入 Screen#init，避免混入在某些映射情况下失败导致 TransformerError
    
    @Override
    public void eap$updateButtonsLayout() {
        // 只处理按钮可见性与定位，不再强制 showPage 或挪动 Slot 坐标，避免与原布局/tooltip 冲突
        if (nextPage != null && prevPage != null) {
            this.nextPage.setVisibility(true);
            this.prevPage.setVisibility(true);
        }
        if (x2Button != null) {
            this.x2Button.setVisibility(true);
        }
        if (divideBy2Button != null) {
            this.divideBy2Button.setVisibility(true);
        }
        if (x10Button != null) {
            this.x10Button.setVisibility(true);
        }
        if (divideBy10Button != null) {
            this.divideBy10Button.setVisibility(true);
        }
        if (divideBy5Button != null) {
            this.divideBy5Button.setVisibility(true);
        }
        if (x5Button != null) {
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
            if (this.divideBy10Button != null && !this.renderables.contains(this.divideBy10Button)) {
                this.addRenderableWidget(this.divideBy10Button);
            }
            if (this.x10Button != null && !this.renderables.contains(this.x10Button)) {
                this.addRenderableWidget(this.x10Button);
            }
        } catch (Throwable ignored) {}

        // 如果屏幕尺寸发生变化（窗口/GUI缩放），重新注册右侧外列的自定义按钮，翻页按钮由左侧工具栏托管
        if (this.width != eap$lastScreenWidth || this.height != eap$lastScreenHeight) {
            eap$lastScreenWidth = this.width;
            eap$lastScreenHeight = this.height;
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
                if (this.divideBy10Button != null) {
                    this.removeWidget(this.divideBy10Button);
                    this.addRenderableWidget(this.divideBy10Button);
                }
                if (this.x10Button != null) {
                    this.removeWidget(this.x10Button);
                    this.addRenderableWidget(this.x10Button);
                }
            } catch (Throwable ignored) {}
        }

        // 定位到 GUI 右缘外侧一点（使用绝对屏幕坐标）
        int bx = this.leftPos + this.imageWidth + 1; // 向右平移 1px 到面板外侧
        int by = this.topPos + 20;
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
        if (this.divideBy10Button != null) {
            this.divideBy10Button.setX(bx);
            this.divideBy10Button.setY(by + spacing * 4);
        }
        if (this.x10Button != null) {
            this.x10Button.setX(bx);
            this.x10Button.setY(by + spacing * 5);
        }
    }

    /**
     * 在服务器端执行样板缩放操作（单机模式）
     */
    @Unique
    private void executePatternScalingOnServer(net.minecraft.server.level.ServerPlayer serverPlayer, String scalingType, double scaleFactor) {
        try {
            // 将实际逻辑切换到服务端主线程执行，避免跨线程访问导致读取到空库存
            serverPlayer.getServer().execute(() -> {
                try {
                    // 直接基于容器槽位操作，完全绕开 PatternProviderLogic 及其内部字段
                    if (!(serverPlayer.containerMenu instanceof com.glodblock.github.extendedae.container.ContainerExPatternProvider exMenu)) {
                        return;
                    }

                    int scaled = 0;
                    int failed = 0;
                    int total = 0;
                    final int scale = (int) Math.round(scaleFactor);
                    final boolean div = !"MULTIPLY".equals(scalingType);

                    java.util.List<net.minecraft.world.inventory.Slot> slots = exMenu.getSlots(appeng.menu.SlotSemantics.ENCODED_PATTERN);
                    for (var slot : slots) {
                        var stack = slot.getItem();
                        if (stack.getItem() instanceof appeng.crafting.pattern.EncodedPatternItem patternItem) {
                            total++;
                            var detail = patternItem.decode(stack, serverPlayer.level(), false);
                            if (detail instanceof appeng.crafting.pattern.AEProcessingPattern process) {
                                var input = process.getSparseInputs();
                                var output = process.getOutputs();

                                // 检查是否可修改（来源：ExtendedAE ContainerPatternModifier.checkModify）
                                if (checkModifyLikeExtendedAE(input, scale, div) && checkModifyLikeExtendedAE(output, scale, div)) {
                                    var mulInput = new appeng.api.stacks.GenericStack[input.length];
                                    var mulOutput = new appeng.api.stacks.GenericStack[output.length];
                                    modifyStacksLikeExtendedAE(input, mulInput, scale, div);
                                    modifyStacksLikeExtendedAE(output, mulOutput, scale, div);
                                    var newPattern = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(mulInput, mulOutput);
                                    if (slot instanceof appeng.menu.slot.AppEngSlot as) {
                                        as.set(newPattern);
                                    } else {
                                        slot.set(newPattern);
                                    }
                                    scaled++;
                                } else {
                                    failed++;
                                }
                            } else {
                                // 非处理样板：跳过
                                failed++;
                            }
                        }
                    }

                    // 构造结果并回显
                    String message;
                    if (scaled == 0) {
                        message = String.format(
                            "ℹ️ ExtendedAE Plus: 样板%s完成，但未处理任何样板。共发现 %d 个样板，失败 %d 个（可能全为合成样板或数量不满足条件）",
                            div ? "除法" : "倍增", total, failed);
                    } else if (failed > 0) {
                        message = String.format("✅ ExtendedAE Plus: 样板%s完成！处理了 %d 个，跳过 %d 个", div ? "除法" : "倍增", scaled, failed);
                    } else {
                        message = String.format("✅ ExtendedAE Plus: 样板%s成功！处理了 %d 个", div ? "除法" : "倍增", scaled);
                    }

                    var minecraft = net.minecraft.client.Minecraft.getInstance();
                    if (minecraft.player != null) {
                        minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
                    }

                } catch (Exception ignored) {
                }
            });

        } catch (Exception ignored) {
        }
    }

    @Unique
    private boolean checkModifyLikeExtendedAE(appeng.api.stacks.GenericStack[] stacks, int scale, boolean div) {
        if (div) {
            for (var stack : stacks) {
                if (stack != null) {
                    if (stack.amount() % scale != 0) {
                        return false;
                    }
                }
            }
        } else {
            for (var stack : stacks) {
                if (stack != null) {
                    long upper = 999999L * stack.what().getAmountPerUnit();
                    if (stack.amount() * scale > upper) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Unique
    private void modifyStacksLikeExtendedAE(appeng.api.stacks.GenericStack[] stacks,
                                            appeng.api.stacks.GenericStack[] des,
                                            int scale,
                                            boolean div) {
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] != null) {
                long amt = div ? stacks[i].amount() / scale : stacks[i].amount() * scale;
                des[i] = new appeng.api.stacks.GenericStack(stacks[i].what(), amt);
            }
        }
    }
    

}
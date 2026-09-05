package com.extendedae_plus.mixin.ae2.client.gui;

import appeng.client.gui.Icon;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.IconButton;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.mixin.ae2.accessor.AEBaseScreenAccessor;
import com.extendedae_plus.mixin.minecraft.accessor.AbstractContainerScreenAccessor;
import com.extendedae_plus.mixin.minecraft.accessor.ScreenAccessor;
import com.extendedae_plus.network.provider.RequestProvidersListC2SPacket;
import com.extendedae_plus.network.provider.ReturnLastPatternC2SPacket;
import com.extendedae_plus.network.upload.EncodeWithShiftFlagC2SPacket;
import com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig;
import static com.extendedae_plus.util.Logger.EAP$LOGGER;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在图样编码终端界面加入一个上传按钮：
 * 点击后把当前“已编码样板”上传到任意可用的样板供应器。
 * 按钮位于 encodePattern 左侧。
 */
@SuppressWarnings({"AddedMixinMembersNamePattern"})
@Mixin(PatternEncodingTermScreen.class)
public abstract class PatternEncodingTermScreenMixin {

    @Unique private IconButton eap$uploadBtn;

    /**
     * GTOCore 魔改版 AE2 将编码按钮保存为字段，不再稳定保留 encodeBtn 局部变量。
     * 在点击入口补发 Shift 状态，避免依赖构造器局部变量表。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void eap$encodeWithShift(double mouseX, double mouseY, int button,
                                     CallbackInfoReturnable<Boolean> cir) {
        var encodeButton = eap$getEncodeButton();
        if (button == 0 && encodeButton != null && encodeButton.isMouseOver(mouseX, mouseY)) {
            ModNetwork.CHANNEL.sendToServer(new EncodeWithShiftFlagC2SPacket(Screen.hasShiftDown()));
            var screen = (PatternEncodingTermScreen<?>) (Object) this;
            screen.getMenu().encode();
            cir.setReturnValue(true);
        }
    }

    @Unique
    private ActionButton eap$getEncodeButton() {
        // 兼容魔改 AE2 的字段按钮，同时允许旧版 AE2 没有该字段时安全回退。
        try {
            Class<?> type = PatternEncodingTermScreen.class;
            while (type != null) {
                try {
                    var field = type.getDeclaredField("encodeBtn");
                    field.setAccessible(true);
                    return (ActionButton) field.get(this);
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
        } catch (Throwable ignored) {
            // 字段不存在时交由 AE2 原生点击逻辑处理。
        }
        return null;
    }

    // 只创建按钮
    @Inject(method = "<init>", at = @At("TAIL"))
    private void eap$createUploadButton(CallbackInfo ci) {
        if (eap$uploadBtn == null) {
            eap$uploadBtn = createUploadButton();
        }
        addButtonToScreen();
    }

    // 每帧更新按钮位置并确保加入屏幕
    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void eap$ensureUploadButton(CallbackInfo ci) {
        if (eap$uploadBtn == null) return;

        updateUploadButtonPosition();
        addButtonToScreen();
    }

    @Unique
    private IconButton createUploadButton() {
        IconButton btn = new IconButton(button -> {
            if (Screen.hasShiftDown()) {
                ModNetwork.CHANNEL.sendToServer(new ReturnLastPatternC2SPacket());
            } else {
                String cachedKey = RecipeTypeNameConfig.peekLastProviderSearchKey();
                String searchKey = eap$getMenuSearchKey();
                if (searchKey == null || searchKey.isBlank()) {
                    searchKey = cachedKey;
                }
                EAP$LOGGER.info("[UploadDebug] upload clicked: shift={}, screen={}, menu={}, cachedKey='{}', menuKey='{}', requestKey='{}'",
                        Screen.hasShiftDown(), getClass().getName(), ((PatternEncodingTermScreen<?>) (Object) this).getMenu().getClass().getName(),
                        cachedKey, eap$getMenuSearchKey(), searchKey);
                ModNetwork.CHANNEL.sendToServer(new RequestProvidersListC2SPacket(searchKey));
            }
        }) {
            private final float eap$scale = 0.75f;

            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partial) {
                if (!this.visible) return;

                var icon = this.getIcon();
                var blitter = icon.getBlitter();
                if (!this.active) blitter.opacity(0.5f);

                this.width = Math.round(16 * eap$scale);
                this.height = Math.round(16 * eap$scale);

                RenderSystem.disableDepthTest();
                RenderSystem.enableBlend();

                if (isFocused()) {
                    guiGraphics.fill(getX() - 1, getY() - 1, getX() + width + 1, getY(), 0xFFFFFFFF);
                    guiGraphics.fill(getX() - 1, getY(), getX(), getY() + height, 0xFFFFFFFF);
                    guiGraphics.fill(getX() + width, getY(), getX() + width + 1, getY() + height, 0xFFFFFFFF);
                    guiGraphics.fill(getX() - 1, getY() + height, getX() + width + 1, getY() + height + 1, 0xFFFFFFFF);
                }

                var pose = guiGraphics.pose();
                pose.pushPose();
                pose.translate(getX(), getY(), 0.0F);
                pose.scale(eap$scale, eap$scale, 1.f);
                if (!this.isDisableBackground()) {
                    Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(0, 0).blit(guiGraphics);
                }

                if (Screen.hasShiftDown()) {
                    pose.pushPose();
                    // Rotate around the center of the 16x16 icon
                    pose.translate(8.0f, 8.0f, 0.0f);
                    pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0f));
                    pose.translate(-8.0f, -8.0f, 0.0f);
                    blitter.dest(0, 0).blit(guiGraphics);
                    pose.popPose();
                } else {
                    blitter.dest(0, 0).blit(guiGraphics);
                }

                pose.popPose();

                RenderSystem.enableDepthTest();
            }

            @Override
            public Rect2i getTooltipArea() {
                return new Rect2i(getX(), getY(), Math.round(16 * eap$scale), Math.round(16 * eap$scale));
            }

            @Override
            public void render(GuiGraphics p_281670_, int p_282682_, int p_281714_, float p_282542_) {
                if (Screen.hasShiftDown()) {
                    this.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.button.return_last_pattern")));
                } else {
                    this.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.button.choose_provider")));
                }
                super.render(p_281670_, p_282682_, p_281714_, p_282542_);
            }

            @Override
            protected Icon getIcon() {
                return Icon.ARROW_UP;
            }
        };
        btn.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.button.choose_provider")));
        return btn;
    }

    @Unique
    private String eap$getMenuSearchKey() {
        // GTOCore 将 EMI 选择的配方类型同步到菜单字段，作为缓存失效时的最终回退。
        try {
            Object menu = ((PatternEncodingTermScreen<?>) (Object) this).getMenu();
            Class<?> type = menu.getClass();
            while (type != null) {
                for (String fieldName : new String[]{"gtocore$recipe", "gto$recipe"}) {
                    try {
                        var field = type.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        Object value = field.get(menu);
                        if (value instanceof String recipe && !recipe.isBlank()) {
                            int separator = recipe.indexOf('/');
                            String key = separator > 0 ? recipe.substring(0, separator) : recipe;
                            return RecipeTypeNameConfig.resolveProviderSearchKey(key);
                        }
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                type = type.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Unique
    private void updateUploadButtonPosition() {
        if (eap$uploadBtn == null) return;

        AbstractContainerScreenAccessor<?> screen = (AbstractContainerScreenAccessor<?>) this;
        try {
            ScreenStyle style = ((AEBaseScreenAccessor<?>) this).eap$getStyle();
            WidgetStyle ws = style.getWidget("encodePattern");

            var bounds = new Rect2i(
                    screen.eap$getLeftPos(),
                    screen.eap$getTopPos(),
                    screen.eap$getImageWidth(),
                    screen.eap$getImageHeight()
            );

            var pos = ws.resolve(bounds);
            int baseW = ws.getWidth() > 0 ? ws.getWidth() : 16;
            int baseH = ws.getHeight() > 0 ? ws.getHeight() : 16;

            int targetW = Math.max(10, Math.round(baseW * 0.75f));
            int targetH = Math.max(10, Math.round(baseH * 0.75f));

            eap$uploadBtn.setWidth(targetW);
            eap$uploadBtn.setHeight(targetH);
            eap$uploadBtn.setX(pos.getX() - targetW);
            eap$uploadBtn.setY(pos.getY());
        } catch (Throwable t) {
            int leftPos = screen.eap$getLeftPos();
            int topPos = screen.eap$getTopPos();
            int imageWidth = screen.eap$getImageWidth();
            eap$uploadBtn.setWidth(12);
            eap$uploadBtn.setHeight(12);
            eap$uploadBtn.setX(leftPos + imageWidth - 14);
            eap$uploadBtn.setY(topPos + 88);
        }
    }

    @Unique
    private void addButtonToScreen() {
        var accessor = (ScreenAccessor) this;
        var renderables = accessor.eap$getRenderables();
        var children = accessor.eap$getChildren();
        if (!renderables.contains(eap$uploadBtn)) renderables.add(eap$uploadBtn);
        if (!children.contains(eap$uploadBtn)) children.add(eap$uploadBtn);
    }
}

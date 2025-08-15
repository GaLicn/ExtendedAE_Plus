package com.extendedae_plus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.Icon;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.AEBaseMenu;

import com.extendedae_plus.network.ModNetwork;
import com.extendedae_plus.network.UploadEncodedPatternToProviderC2SPacket;

/**
 * 在图样编码终端右侧工具栏加入一个上传按钮：
 * 点击后把当前“已编码样板”上传到任意可用的样板供应器（服务端自动选择）。
 */
@Mixin(AEBaseScreen.class)
public abstract class PatternEncodingTermScreenMixin<T extends AEBaseMenu> {

    // 使用 @Shadow 访问 AEBaseScreen 的受保护方法
    @Shadow
    protected abstract <B extends Button> B addToLeftToolbar(B button);

    @Unique
    private boolean extendedae_plus$uploadBtnAdded = false;

    @Inject(method = "init", at = @At("HEAD"))
    private void extendedae_plus$addUploadButton(CallbackInfo ci) {
        // 仅在图样编码终端界面中添加按钮
        if (!(((Object) this) instanceof PatternEncodingTermScreen)) {
            return;
        }
        // 幂等：避免每次 init() 都重复添加按钮
        if (extendedae_plus$uploadBtnAdded) {
            return;
        }
        var uploadBtn = new IconButton(btn -> ModNetwork.CHANNEL
                .sendToServer(new com.extendedae_plus.network.RequestProvidersListC2SPacket())) {
            @Override
            protected Icon getIcon() {
                return Icon.ARROW_UP;
            }
        };
        uploadBtn.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.button.choose_provider")));

        // 直接调用 @Shadow 方法，避免跨包 protected 访问问题
        this.addToLeftToolbar(uploadBtn);
        extendedae_plus$uploadBtnAdded = true;
    }
}

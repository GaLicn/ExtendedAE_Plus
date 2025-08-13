package com.extendedae_plus.mixin;

import com.extendedae_plus.mixin.accessor.AEBaseScreenAccessor;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.glodblock.github.extendedae.network.EPPNetworkHandler;
import com.glodblock.github.glodium.network.packet.CGenericPacket;

/**
 * 在 AE2 的 PatternEncodingTermScreen 界面中，给编码按钮旁添加一个“上传到矩阵”按钮。
 * 客户端点击后，使用 ExtendedAE 的网络通道发送通用数据包到服务端，由容器 Mixin 处理。
 */
@Mixin(PatternEncodingTermScreen.class)
public abstract class GuiPatternEncodingTermMixin<C extends PatternEncodingTermMenu> {

    @Unique
    private IconButton epp$uploadButton;

    // 通过 Accessor 调用 AEBaseScreen#addToLeftToolbar

    // 在构造函数尾部创建按钮实例（仅创建，不添加）
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void epp$onInit(C menu, Inventory playerInventory, Component title, ScreenStyle style, CallbackInfo ci) {
        // 选择一个合适的图标占位（AE2 暂无显式“上传”图标，这里先用 PATTERN_ACCESS_SHOW）
        this.epp$uploadButton = new IconButton(b -> {
            // 直接发送无参动作，服务端容器会根据当前 encoded 槽位处理
            System.out.println("[EAE+][Client] Click upload button -> send CGenericPacket('upload_to_matrix')");
            try {
                var mc = net.minecraft.client.Minecraft.getInstance();
                var cm = mc.player != null ? mc.player.containerMenu : null;
                System.out.println("[EAE+][Client] Current container: " + (cm == null ? "null" : cm.getClass().getName()));
                if (cm instanceof com.glodblock.github.glodium.network.packet.sync.IActionHolder ah) {
                    var keys = ah.getActionMap().keySet();
                    System.out.println("[EAE+][Client] IActionHolder detected. actions=" + keys);
                } else {
                    System.out.println("[EAE+][Client] Current container is NOT IActionHolder");
                }
            } catch (Throwable t) {
                System.out.println("[EAE+][Client] Inspect container failed: " + t);
            }
            EPPNetworkHandler.INSTANCE.sendToServer(new CGenericPacket("upload_to_matrix"));
        }) {
            @Override
            protected Icon getIcon() {
                return Icon.PATTERN_ACCESS_SHOW;
            }
        };
        this.epp$uploadButton.setTooltip(Tooltip.create(Component.translatable("extendedae_plus.upload_to_matrix")));
        System.out.println("[EAE+][Client] Created upload button in PatternEncodingTermScreen ctor tail");
        // 直接在构造尾部加入左侧工具栏，避免目标类未覆写 init 导致的注入不到
        ((AEBaseScreenAccessor) (Object) this).epp$addToLeftToolbar(this.epp$uploadButton);
        System.out.println("[EAE+][Client] Added upload button to left toolbar in ctor tail");
    }

}

package com.extendedae_plus.mixin;

import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.menu.SlotSemantics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.glodblock.github.extendedae.network.EPPNetworkHandler;
import com.glodblock.github.glodium.network.packet.CGenericPacket;
import com.extendedae_plus.mixin.accessor.ScreenAccessor;
import com.extendedae_plus.mixin.accessor.AEBaseScreenAccessor;

/**
 * 在 AE2 的 PatternEncodingTermScreen 界面中，给编码按钮旁添加一个“上传到矩阵”按钮。
 * 客户端点击后，使用 ExtendedAE 的网络通道发送通用数据包到服务端，由容器 Mixin 处理。
 */
@Mixin(PatternEncodingTermScreen.class)
public abstract class GuiPatternEncodingTermMixin<C extends PatternEncodingTermMenu> {

    @Unique
    private IconButton epp$uploadButton;
    @Unique
    private boolean epp$addedWidget = false;
    @Unique
    private boolean epp$usingToolbar = false;

    // 在构造函数尾部创建按钮实例并加入普通部件列表
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
        // 注意：此处不立即添加到 Screen，等到 updateBeforeRender 首帧再添加，避免在构造期调用导致异常
        this.epp$uploadButton.visible = false; // 初始隐藏，等待定位
    }

    // 在每帧渲染前定位按钮到“已编码样板槽位”的左侧
    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false)
    private void epp$positionUploadButton(CallbackInfo ci) {
        try {
            if (this.epp$uploadButton == null) {
                return;
            }

            // 首帧尝试将按钮加入到 Screen 的可渲染部件中
            if (!this.epp$addedWidget) {
                try {
                    ((ScreenAccessor)(Object)this).epp$invokeAddRenderableWidget(this.epp$uploadButton);
                    this.epp$addedWidget = true;
                    this.epp$usingToolbar = false;
                    System.out.println("[EAE+][Client] Added upload button as normal widget in first updateBeforeRender");
                } catch (Throwable t) {
                    System.out.println("[EAE+][Client] addRenderableWidget failed, fallback to left toolbar: " + t);
                    try {
                        ((AEBaseScreenAccessor)(Object)this).epp$addToLeftToolbar(this.epp$uploadButton);
                        this.epp$addedWidget = true;
                        this.epp$usingToolbar = true;
                        this.epp$uploadButton.visible = true;
                        System.out.println("[EAE+][Client] Fallback added to left toolbar");
                    } catch (Throwable t2) {
                        System.out.println("[EAE+][Client] Fallback addToLeftToolbar also failed: " + t2);
                        // 放弃添加，避免死循环
                        this.epp$addedWidget = true;
                    }
                }
            }

            // 仅在“合成模式”下显示按钮（与上传逻辑一致）
            var self = (PatternEncodingTermScreen<?>) (Object) this;
            var menu = (PatternEncodingTermMenu) self.getMenu();
            if (menu == null) {
                return;
            }
            boolean craftingMode = menu.getMode() == EncodingMode.CRAFTING;

            Slot encoded = null;
            for (Slot s : menu.slots) {
                var sem = menu.getSlotSemantic(s);
                if (sem == SlotSemantics.ENCODED_PATTERN) {
                    encoded = s;
                    break;
                }
            }

            // 如果退回到左侧工具栏显示，则不做定位
            if (this.epp$usingToolbar) {
                return;
            }

            if (craftingMode && encoded != null) {
                int slotX = self.getGuiLeft() + encoded.x;
                int slotY = self.getGuiTop() + encoded.y;
                int bw = this.epp$uploadButton.getWidth();
                int bh = this.epp$uploadButton.getHeight();
                int gap = 3;

                this.epp$uploadButton.setX(slotX - gap - bw);
                this.epp$uploadButton.setY(slotY + (18 - bh) / 2);
                this.epp$uploadButton.visible = true;
                // 可选：当槽位有编码图样时启用
                this.epp$uploadButton.active = !encoded.getItem().isEmpty();
            } else {
                this.epp$uploadButton.visible = false;
            }
        } catch (Throwable t) {
            System.out.println("[EAE+][Client] updateBeforeRender positioning failed: " + t);
        }
    }

}

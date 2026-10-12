package com.extendedae_plus.mixin.ae2.menu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.parts.encoding.EncodingMode;
import com.extendedae_plus.api.upload.IPatternEncodeRequestSync;
import com.extendedae_plus.api.upload.IPatternUploadMenu;
import com.extendedae_plus.api.upload.UploadPreferences;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import com.glodblock.github.glodium.network.packet.sync.ActionMap;
import com.glodblock.github.glodium.network.packet.sync.IActionHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给 AE2 的 PatternEncodingTermMenu 增加一个通用动作持有者，实现接收 EPP 的 CGenericPacket 动作。
 */
@Mixin(PatternEncodingTermMenu.class)
public abstract class ContainerPatternEncodingTermMenuMixin implements IActionHolder, IPatternEncodeRequestSync, IPatternUploadMenu {

    @Unique
    private final ActionMap eap$actions = ActionMap.create();

    @Unique
    private Player epp$player;

    // 客户端随「编码」按钮上报的请求（Shift、唯一匹配判定、自动上传偏好），encode() 之后消费。
    @Unique
    private EncodeRequest eap$pendingEncodeRequest;

    @Shadow(remap = false)
    private RestrictedInputSlot encodedPatternSlot;

    @Unique
    @Override
    public void eap$clientSetEncodeRequest(EncodeRequest request) {
        this.eap$pendingEncodeRequest = request;
    }

    @Unique
    @Override
    public EncodeRequest eap$consumeEncodeRequest() {
        EncodeRequest request = this.eap$pendingEncodeRequest;
        this.eap$pendingEncodeRequest = null;
        return request;
    }

    // AE2 终端主构造：PatternEncodingTermMenu(int id, Inventory ip, IPatternTerminalMenuHost host)
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;)V", at = @At("TAIL"), remap = false)
    private void eap$ctorA(int id, Inventory ip, IPatternTerminalMenuHost host, CallbackInfo ci) {
        this.epp$player = ip.player;
        // 不再注册任何上传相关动作
    }

    // AE2 另一个构造：PatternEncodingTermMenu(MenuType, int, Inventory, IPatternTerminalMenuHost, boolean)
    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;Z)V", at = @At("TAIL"), remap = false)
    private void eap$ctorB(MenuType<?> menuType, int id, Inventory ip, IPatternTerminalMenuHost host, boolean bindInventory, CallbackInfo ci) {
        this.epp$player = ip.player;
        // 不再注册任何上传相关动作
    }

    @NotNull
    @Override
    public ActionMap getActionMap() {
        return this.eap$actions;
    }

    // IPatternUploadMenu：暴露编码槽给 EAEP 上传链路
    @Override
    public Slot getEncodedPatternSlot() {
        return this.encodedPatternSlot;
    }

    // 服务器端：在 encode() 执行完毕后自动上传编码槽内的样板。
    // 先尝试客户端判定的供应器唯一直传（命中即结束），未命中再走矩阵/ECO 优先级链；
    // 处理样板矩阵不支持，只可能走供应器直传。
    @Inject(method = "encode", at = @At("TAIL"), remap = false)
    private void eap$serverUploadAfterEncode(CallbackInfo ci) {
        try {
            if (!(this.epp$player instanceof ServerPlayer sp)) {
                return; // 仅服务器执行
            }
            EncodeRequest request = this.eap$consumeEncodeRequest();
            if (request != null && request.shiftDown()) {
                return; // 按下 Shift，不自动上传
            }
            var menu = (PatternEncodingTermMenu) (Object) this;
            boolean craftingLike = menu.getMode() == EncodingMode.CRAFTING
                    || menu.getMode() == EncodingMode.SMITHING_TABLE
                    || menu.getMode() == EncodingMode.STONECUTTING;
            boolean processing = menu.getMode() == EncodingMode.PROCESSING;
            if (!craftingLike && !processing) {
                return; // 只自动上传合成类与处理样板
            }
            if (this.encodedPatternSlot == null) {
                return;
            }
            var stack = this.encodedPatternSlot.getItem();
            if (stack == null || stack.isEmpty()) {
                return; // 没有编码样板
            }
            if (!PatternDetailsHelper.isEncodedPattern(stack)) {
                return; // 不是编码样板
            }
            final Component provider = request == null ? null : request.uniqueProvider();
            final UploadPreferences prefs = request == null ? UploadPreferences.defaults() : request.prefs();
            // 为避免与 AE2 后续同步竞争，切到下一 tick 执行
            sp.server.execute(() -> {
                try {
                    if (provider != null
                            && ExtendedAEPatternUploadUtil.uploadFromEncodingMenuToNamedProvider(sp, this, provider)) {
                        return;
                    }
                    if (processing) {
                        return;
                    }
                    ExtendedAEPatternUploadUtil.uploadFromEncodingMenuByPriority(sp, menu, prefs);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    // 服务器端：在构造样板返回前插入编码玩家的名称
    @Inject(method = "encodePattern", at = @At("TAIL"), remap = false, cancellable = true)
    private void eap$writeEncodePlayerToPattern(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack itemStack = cir.getReturnValue();
        if (itemStack != null && !itemStack.isEmpty()) {
            CustomData.update(DataComponents.CUSTOM_DATA, itemStack, tag -> {
                tag.putString("encodePlayer", this.epp$player.getGameProfile().getName());
            });
            cir.setReturnValue(itemStack);
        }
    }
}

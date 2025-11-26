package com.extendedae_plus.mixin.ae2.menu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.parts.encoding.EncodingMode;
import com.extendedae_plus.api.upload.IPatternEncodingShiftUploadSync;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import com.glodblock.github.glodium.network.packet.sync.ActionMap;
import com.glodblock.github.glodium.network.packet.sync.IActionHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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
 * 注册动作 "upload_to_matrix"：仅上传“合成图样”到 ExtendedAE 装配矩阵。
 */
@Mixin(PatternEncodingTermMenu.class)
public abstract class ContainerPatternEncodingTermMenuMixin implements IActionHolder, IPatternEncodingShiftUploadSync {

    @Unique
    private final ActionMap eap$actions = ActionMap.create();

    @Unique
    private Player epp$player;

    @Unique
    private boolean eap$pendingShiftUpload;

    @Shadow(remap = false)
    private RestrictedInputSlot encodedPatternSlot;

    @Unique
    @Override
    public void eap$clientSetShiftUpload(boolean shiftDown) {
        this.eap$pendingShiftUpload = shiftDown;
    }

    @Unique
    @Override
    public boolean eap$consumeShiftUploadFlag() {
        boolean flag = this.eap$pendingShiftUpload;
        this.eap$pendingShiftUpload = false;
        return flag;
    }

    @Unique
    private void eap$scheduleUploadWithRetry(ServerPlayer sp, PatternEncodingTermMenu menu, int attemptsLeft) {
        sp.server.execute(() -> {
            try {
                if (attemptsLeft < 0) {
                    return;
                }
                var stack = this.encodedPatternSlot != null ? this.encodedPatternSlot.getItem() : net.minecraft.world.item.ItemStack.EMPTY;
                if (stack != null && !stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)) {
                    ExtendedAEPatternUploadUtil.uploadFromEncodingMenuToMatrix(sp, menu);
                } else {
                    // 槽位可能尚未同步到位，继续下一 tick 重试
                    if (attemptsLeft > 0) {
                        this.eap$scheduleUploadWithRetry(sp, menu, attemptsLeft - 1);
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }

    // AE2 终端主构造：PatternEncodingTermMenu(int id, Inventory ip, IPatternTerminalMenuHost host)
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;)V", at = @At("TAIL"), remap = false)
    private void eap$ctorA(int id, net.minecraft.world.entity.player.Inventory ip, appeng.helpers.IPatternTerminalMenuHost host, CallbackInfo ci) {
        this.epp$player = ip.player;
        // 不再注册任何上传相关动作
    }

    // AE2 另一个构造：PatternEncodingTermMenu(MenuType, int, Inventory, IPatternTerminalMenuHost, boolean)
    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;Z)V", at = @At("TAIL"), remap = false)
    private void eap$ctorB(net.minecraft.world.inventory.MenuType<?> menuType, int id, net.minecraft.world.entity.player.Inventory ip, appeng.helpers.IPatternTerminalMenuHost host, boolean bindInventory, CallbackInfo ci) {
        this.epp$player = ip.player;
        // 不再注册任何上传相关动作
    }

    @NotNull
    @Override
    public ActionMap getActionMap() {
        return this.eap$actions;
    }

    // 服务器端：在 encode() 执行完毕后，如果已编码槽位存在样板且当前为“合成模式”，则上传到装配矩阵
    @Inject(method = "encode", at = @At("TAIL"), remap = false)
    private void eap$serverUploadAfterEncode(CallbackInfo ci) {
        try {
            if (!(this.epp$player instanceof ServerPlayer sp)) {
                return; // 仅服务器执行
            }
            if (!this.eap$consumeShiftUploadFlag()) {
                return; // 未按下 Shift，不自动上传
            }
            var menu = (PatternEncodingTermMenu) (Object) this;
            if (menu.getMode() != EncodingMode.CRAFTING
                    && menu.getMode() != EncodingMode.SMITHING_TABLE
                    && menu.getMode() != EncodingMode.STONECUTTING) {
                return; // 只处理合成/锻造台/切石机样板
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
            // 为避免与 AE2 后续同步竞争，切到下一 tick 执行
            sp.server.execute(() -> {
                try {
                    ExtendedAEPatternUploadUtil.uploadFromEncodingMenuToMatrix(sp, menu);
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

package com.extendedae_plus.mixin.ae2.menu;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.glodblock.github.glodium.network.packet.sync.ActionMap;
import com.glodblock.github.glodium.network.packet.sync.IActionHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
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
public abstract class ContainerPatternEncodingTermMenuMixin implements IActionHolder {

    @Unique
    private final ActionMap eap$actions = ActionMap.create();

    @Unique
    private Player epp$player;

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

    // 服务器端：在构造样板返回前插入编码玩家的名称
    @Inject(method = "encodePattern", at = @At("TAIL"), remap = false, cancellable = true)
    private void eap$writeEncodePlayerToPattern(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack itemStack = cir.getReturnValue();
        if (itemStack != null && !itemStack.isEmpty()) {
            CustomData.update(DataComponents.CUSTOM_DATA, itemStack, tag -> tag.putString("encodePlayer", this.epp$player.getGameProfile().getName()));
            cir.setReturnValue(itemStack);
        }
    }
}

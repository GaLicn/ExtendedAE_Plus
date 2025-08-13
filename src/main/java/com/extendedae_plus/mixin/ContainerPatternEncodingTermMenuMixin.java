package com.extendedae_plus.mixin;

import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import com.glodblock.github.glodium.network.packet.sync.IActionHolder;
import com.glodblock.github.glodium.network.packet.sync.Paras;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 给 AE2 的 PatternEncodingTermMenu 增加一个通用动作持有者，实现接收 EPP 的 CGenericPacket 动作。
 * 注册动作 "upload_to_matrix"：仅上传“合成图样”到 ExtendedAE 装配矩阵。
 */
@Mixin(PatternEncodingTermMenu.class)
public abstract class ContainerPatternEncodingTermMenuMixin implements IActionHolder {

    @Unique
    private final Map<String, Consumer<Paras>> actions = createHolder();

    @Unique
    private Player epp$player;

    // AE2 终端主构造：PatternEncodingTermMenu(int id, Inventory ip, IPatternTerminalMenuHost host)
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;)V", at = @At("TAIL"), remap = false)
    private void epp$ctorA(int id, net.minecraft.world.entity.player.Inventory ip, appeng.helpers.IPatternTerminalMenuHost host, CallbackInfo ci) {
        this.epp$player = ip.player;
        // 注册动作：无参，由服务端直接读 encoded 槽位。
        System.out.println("[EAE+][Server] Register action 'upload_to_matrix' for PatternEncodingTermMenu ctorA");
        this.actions.put("upload_to_matrix", p -> {
            try {
                var sp = (ServerPlayer) this.epp$player;
                System.out.println("[EAE+][Server] Handle action 'upload_to_matrix' from " + sp.getGameProfile().getName());
                ExtendedAEPatternUploadUtil.uploadFromEncodingMenuToMatrix(sp, (PatternEncodingTermMenu) (Object) this);
            } catch (Throwable t) {
                System.out.println("[EAE+][Server] Exception in 'upload_to_matrix': " + t);
                t.printStackTrace();
            }
        });
    }

    // AE2 另一个构造：PatternEncodingTermMenu(MenuType, int, Inventory, IPatternTerminalMenuHost, boolean)
    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;Z)V", at = @At("TAIL"), remap = false)
    private void epp$ctorB(net.minecraft.world.inventory.MenuType<?> menuType, int id, net.minecraft.world.entity.player.Inventory ip, appeng.helpers.IPatternTerminalMenuHost host, boolean bindInventory, CallbackInfo ci) {
        this.epp$player = ip.player;
        System.out.println("[EAE+][Server] Register action 'upload_to_matrix' for PatternEncodingTermMenu ctorB");
        this.actions.put("upload_to_matrix", p -> {
            try {
                var sp = (ServerPlayer) this.epp$player;
                System.out.println("[EAE+][Server] Handle action 'upload_to_matrix' from " + sp.getGameProfile().getName());
                ExtendedAEPatternUploadUtil.uploadFromEncodingMenuToMatrix(sp, (PatternEncodingTermMenu) (Object) this);
            } catch (Throwable t) {
                System.out.println("[EAE+][Server] Exception in 'upload_to_matrix': " + t);
                t.printStackTrace();
            }
        });
    }

    @NotNull
    @Override
    public Map<String, Consumer<Paras>> getActionMap() {
        return this.actions;
    }
}

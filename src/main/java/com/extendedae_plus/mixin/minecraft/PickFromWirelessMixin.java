package com.extendedae_plus.mixin.minecraft;

import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.init.ModNetwork;
import com.extendedae_plus.network.PickFromWirelessC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 将取方块请求接管到无线终端逻辑。
 *
 * <p>进入本注入点的路径有三条：普通中键经原版 {@code keyPickItem} 调用
 * {@code Minecraft#pickBlock}；强制下单组合键的鼠标与键盘入口由
 * {@code ForceCraftKeyHandler} 经 {@code MinecraftPickBlockAccessor} 主动调用同一方法。
 * 三条路径共用本处判定，行为不会分叉。</p>
 *
 * <p>不检查客户端是否持有无线合成终端：该判定由服务端权威执行（含 Curios 支持），
 * 以避免整合包环境下的软依赖与槽位问题。</p>
 */
@Mixin(Minecraft.class)
public class PickFromWirelessMixin {
    @Shadow public LocalPlayer player;
    @Shadow public HitResult hitResult;

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void eap$pickFromAeWireless(CallbackInfo ci) {
        // 强制下单标记在任何提前返回之前消费，避免其残留到后续的普通中键点击上。
        boolean forceCraft = ModKeybindings.consumeForceCraft();

        if (this.player == null || this.hitResult == null || this.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        // 仅生存模式
        GameType type = Minecraft.getInstance().gameMode != null ? Minecraft.getInstance().gameMode.getPlayerMode() : null;
        if (type == null || type.isCreative()) {
            return;
        }
        // 强制下单组合键已按下：跳过「背包已有该物品则交还原版」的短路，
        // 由服务端直接打开下单界面，不检查 AE 网络中是否已有该物品。
        BlockHitResult bhr = (BlockHitResult) this.hitResult;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            try {
                BlockState state = level.getBlockState(bhr.getBlockPos());
                if (state != null && !state.isAir()) {
                    ItemStack picked = state.getBlock().getCloneItemStack(state, bhr, level, bhr.getBlockPos(), this.player);
                    if (picked.isEmpty()) {
                        picked = state.getBlock().asItem().getDefaultInstance();
                    }
                    if (!picked.isEmpty() && !forceCraft) {
                        // 若主手已拿同一物品（含标签），则仍然走 AE 拉取逻辑进行补充/合并
                        if (!ItemStack.isSameItemSameTags(picked, this.player.getMainHandItem())) {
                            int slot = this.player.getInventory().findSlotMatchingItem(picked);
                            if (slot != -1) {
                                return; // 交给原版 pickBlock 处理
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                // 若其它模组导致 getCloneItemStack 出异常，放弃拦截，保持原版行为，确保健壮性
                return;
            }
        }

        // 背包没有：发送到服务端处理（从 AE2 网络拉取）并拦截原版
        Vec3 loc = bhr.getLocation();
        ModNetwork.CHANNEL.sendToServer(new PickFromWirelessC2SPacket(bhr.getBlockPos(), bhr.getDirection(), loc, forceCraft));
        ci.cancel();
    }
}

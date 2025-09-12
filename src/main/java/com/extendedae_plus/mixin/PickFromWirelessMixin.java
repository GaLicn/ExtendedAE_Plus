package com.extendedae_plus.mixin;

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
// no client-side WCT gating; server will check presence (including Curios)


@Mixin(Minecraft.class)
public class PickFromWirelessMixin {
    @Shadow public LocalPlayer player;
    @Shadow public HitResult hitResult;

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void eap$pickFromAeWireless(CallbackInfo ci) {
        if (this.player == null || this.hitResult == null || this.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        // 仅生存模式
        GameType type = Minecraft.getInstance().gameMode != null ? Minecraft.getInstance().gameMode.getPlayerMode() : null;
        if (type == null || type.isCreative()) {
            return;
        }
        // 若背包已有该物品，让原版逻辑处理（将该物品切换到主手）
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
                    if (!picked.isEmpty()) {
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

        // 不在客户端检查是否持有无线合成终端，由服务端权威校验（含 Curios 支持），以避免整合包环境下的软依赖与槽位问题
        // 背包没有：发送到服务端处理（从 AE2 网络拉取）并拦截原版
        Vec3 loc = bhr.getLocation();
        ModNetwork.CHANNEL.sendToServer(new PickFromWirelessC2SPacket(bhr.getBlockPos(), bhr.getDirection(), loc));
        ci.cancel();
    }
}

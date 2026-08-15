package com.extendedae_plus.mixin.ae2.accessor;

import appeng.blockentity.grid.AENetworkBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 AE 节点连接面变更通知，供外部矩阵玻璃切换超级结构状态时使用。 */
@Mixin(AENetworkBlockEntity.class)
public interface AENetworkBlockEntityInvoker {

    @Invoker(value = "onGridConnectableSidesChanged", remap = false)
    void eap$refreshGridConnectableSides();
}

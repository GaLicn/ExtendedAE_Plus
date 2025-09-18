package com.extendedae_plus.init;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * 注册 AE2 能力给本模组的方块实体，确保 AE 电缆能识别并连接到我们的 In-World Grid Node。
 */
public final class ModCapabilities {
    private ModCapabilities() {}

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // 为实现了 IInWorldGridNodeHost 的自定义方块实体注册 AE2 的 IN_WORLD_GRID_NODE_HOST 能力
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.WIRELESS_TRANSCEIVER_BE.get(),
                (be, ctx) -> (IInWorldGridNodeHost) be
        );

        // 供应器状态控制器（实现了 IInWorldGridNodeHost）
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.NETWORK_PATTERN_CONTROLLER_BE.get(),
                (be, ctx) -> (IInWorldGridNodeHost) be
        );

        // 并行处理单元（CraftingUnitBlock -> CraftingBlockEntity 实现了 IInWorldGridNodeHost）
        // 未注册该能力时，AE 电缆通过 GridHelper.getNodeHost(...) 无法发现节点，导致节点不入网，
        // 方块虽然能成型并提供并行度，但 getMainNode().isOnline() 为 false，从而显示“设备离线”。
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.EPLUS_CRAFTING_UNIT_BE.get(),
                (be, ctx) -> (IInWorldGridNodeHost) be
        );

        // 如果还有其他实现了 IInWorldGridNodeHost 的方块实体，也在这里一并注册
        // event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, ModBlockEntities.NETWORK_PATTERN_CONTROLLER_BE.get(), (be, ctx) -> (IInWorldGridNodeHost) be);
    }
}

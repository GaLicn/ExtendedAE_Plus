package com.extendedae_plus.init;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import com.extendedae_plus.content.crystal.SuperCrystalAssemblerBlockEntity;
import com.extendedae_plus.content.cutter.SuperCircuitCutterBlockEntity;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.extendedae_plus.content.matrix.CrafterCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.HybridCoreBlockEntity;
import com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.SpeedCorePlusBlockEntity;
import com.extendedae_plus.content.matrix.UploadCoreBlockEntity;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixBlockEntity;

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
        // 标签无线收发器
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.LABELED_WIRELESS_TRANSCEIVER_BE.get(),
                (be, ctx) -> (IInWorldGridNodeHost) be
        );

        // 供应器状态控制器（实现了 IInWorldGridNodeHost）
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.NETWORK_PATTERN_CONTROLLER_BE.get(),
                (be, ctx) -> (IInWorldGridNodeHost) be
        );

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.MIRROR_PATTERN_PROVIDER_BE.get(),
                (be, ctx) -> (IInWorldGridNodeHost) be
        );

        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.MIRROR_PATTERN_PROVIDER_BE.get(),
                (be, ctx) -> be.getLogic().getReturnInv()
        );

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.TAG_INVENTORY_ME_INTERFACE_BE.get(),
                (be, ctx) -> (IInWorldGridNodeHost) be
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.TAG_INVENTORY_ME_INTERFACE_BE.get(),
                (be, ctx) -> be.getItemHandler(ctx)
        );

        // 并行处理单元（CraftingUnitBlock -> CraftingBlockEntity 实现了 IInWorldGridNodeHost）
        // 未注册该能力时，AE 电缆通过 GridHelper.getNodeHost(...) 无法发现节点，导致节点不入网，
        // 方块虽然能成型并提供并行度，但 getMainNode().isOnline() 为 false，从而显示“设备离线”。
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.EPLUS_CRAFTING_UNIT_BE.get(),
                (be, ctx) -> (IInWorldGridNodeHost) be
        );

        // 装配矩阵核心方块（均实现了 IInWorldGridNodeHost）
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ASSEMBLER_MATRIX_CRAFTER_PLUS_BE.get(),
                (be, ctx) -> (CrafterCorePlusBlockEntity) be
        );
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ASSEMBLER_MATRIX_PATTERN_PLUS_BE.get(),
                (be, ctx) -> (PatternCorePlusBlockEntity) be
        );
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ASSEMBLER_MATRIX_HYBRID_PLUS_BE.get(),
                (be, ctx) -> (HybridCoreBlockEntity) be
        );
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ASSEMBLER_MATRIX_SPEED_PLUS_BE.get(),
                (be, ctx) -> (SpeedCorePlusBlockEntity) be
        );
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.UPLOAD_CORE_BE.get(),
                (be, ctx) -> (UploadCoreBlockEntity) be
        );

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.SUPER_ASSEMBLER_MATRIX_FRAME_BE.get(),
                (be, ctx) -> (SuperAssemblerMatrixBlockEntity) be
        );
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.SUPER_ASSEMBLER_MATRIX_WALL_BE.get(),
                (be, ctx) -> (SuperAssemblerMatrixBlockEntity) be
        );
        // 与原版装配矩阵一致，允许存储总线把编码样板输入超级矩阵。
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.SUPER_ASSEMBLER_MATRIX_FRAME_BE.get(),
                (be, side) -> ((SuperAssemblerMatrixBlockEntity) be).getExposedPatternItemHandler(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.SUPER_ASSEMBLER_MATRIX_WALL_BE.get(),
                (be, side) -> ((SuperAssemblerMatrixBlockEntity) be).getExposedPatternItemHandler(side)
        );
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.CRYSTAL_ASSEMBLER_PLUS_BE.get(),
                (be,ctx)->(SuperCrystalAssemblerBlockEntity) be
        );
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.CIRCUIT_CUTTER_PLUS_BE.get(),
                (be,ctx)->(SuperCircuitCutterBlockEntity) be
        );

        // 对齐 EAE 原机：将受输入/输出规则限制的自动化库存暴露给漏斗和物流模组。
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.CRYSTAL_ASSEMBLER_PLUS_BE.get(),
                (be, side) -> ((SuperCrystalAssemblerBlockEntity) be).getExposedItemHandler(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.CIRCUIT_CUTTER_PLUS_BE.get(),
                (be, side) -> ((SuperCircuitCutterBlockEntity) be).getExposedItemHandler(side)
        );

        // 将 AE2 内部电池暴露为 FE 能力，使超级机器可从 FE 网络充电。
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.CRYSTAL_ASSEMBLER_PLUS_BE.get(),
                (be, side) -> ((SuperCrystalAssemblerBlockEntity) be).getEnergyStorage(side)
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.CIRCUIT_CUTTER_PLUS_BE.get(),
                (be, side) -> ((SuperCircuitCutterBlockEntity) be).getEnergyStorage(side)
        );
        // 如果还有其他实现了 IInWorldGridNodeHost 的方块实体，也在这里一并注册
        // event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, ModBlockEntities.NETWORK_PATTERN_CONTROLLER_BE.get(), (be, ctx) -> (IInWorldGridNodeHost) be);
    }
}

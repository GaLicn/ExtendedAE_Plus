package com.extendedae_plus_gtladd.mixin.eaep;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.extendedae_plus.util.uploadPattern.MatrixUploadUtil;
import com.extendedae_plus_gtladd.config.ModConfig;
import com.extendedae_plus_gtladd.util.uploadPattern.GTMatrixUploadUtil;
import net.minecraft.server.level.ServerPlayer;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MatrixUploadUtil.class})
public abstract class MatrixUploadUtilMixin {
    @Inject(
        method = {"uploadFromEncodingMenuToMatrix"},
        at = {@At("HEAD")},
        cancellable = true,
        remap = false
    )
    private static void onUploadStart(ServerPlayer player, PatternEncodingTermMenu menu, CallbackInfo ci) {
        if (ModConfig.INSTANCE.restrictCraftingPatternToMolecular && hasMolecularAssemblerInNetwork(menu)) {
            GTMatrixUploadUtil.uploadFromEncodingMenuToMatrix(player, menu);
            ci.cancel();
        }

    }

    private static boolean hasMolecularAssemblerInNetwork(PatternEncodingTermMenu menu) {
        try {
            IGridNode node = menu.getNetworkNode();
            if (node == null) {
                return false;
            }

            IGrid grid = node.getGrid();
            if (grid == null) {
                return false;
            }

            for(MEMolecularAssemblerIOPartMachine machine : grid.getMachines(MEMolecularAssemblerIOPartMachine.class)) {
                if (machine != null && machine.isFormed() && machine.getMainNode().isActive()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }
}

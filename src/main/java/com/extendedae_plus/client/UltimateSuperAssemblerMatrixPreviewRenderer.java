package com.extendedae_plus.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.matrix.supermatrix.UltimateSuperAssemblerMatrixStructure;
import com.extendedae_plus.init.ModBlocks;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.items.tools.UltimateSuperAssemblerMatrixBuilderItem;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT)
public final class UltimateSuperAssemblerMatrixPreviewRenderer {
    private static final double OUTLINE_INFLATE = 0.002D;

    private UltimateSuperAssemblerMatrixPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterWeather event) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null || !isHoldingBuilder(player)) {
            return;
        }

        var builderStack = player.getMainHandItem().is(ModItems.ULTIMATE_SUPER_ASSEMBLER_MATRIX_BUILDER.get())
                ? player.getMainHandItem() : player.getOffhandItem();
        var selectedOrigin = UltimateSuperAssemblerMatrixBuilderItem.getSelectedOrigin(builderStack);
        var origin = selectedOrigin != null && selectedOrigin.dimension().equals(level.dimension())
                ? selectedOrigin.pos()
                : minecraft.hitResult instanceof BlockHitResult hit
                        ? hit.getBlockPos().relative(hit.getDirection())
                        : UltimateSuperAssemblerMatrixBuilderItem.getAirOrigin(player);

        try (var gizmos = event.getLevelRenderer().collectPerFrameGizmos()) {
            var bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 14,
                    origin.getY() + 16, origin.getZ() + 14).inflate(OUTLINE_INFLATE);
            Gizmos.cuboid(bounds, GizmoStyle.stroke(selectedOrigin == null ? 0xe64dccff : 0xe659ff73));
            Gizmos.cuboid(new AABB(origin).inflate(OUTLINE_INFLATE), GizmoStyle.stroke(0xfffff24d));

            if (InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                    || InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                for (var block : UltimateSuperAssemblerMatrixStructure.getPreviewBlocks(minecraft.getResourceManager())) {
                    var position = origin.offset(block.offset());
                    var color = colorFor(block.block());
                    Gizmos.cuboid(new AABB(position).inflate(OUTLINE_INFLATE), GizmoStyle.stroke(color));
                }
            }
        }
    }

    private static boolean isHoldingBuilder(net.minecraft.world.entity.player.Player player) {
        var builder = ModItems.ULTIMATE_SUPER_ASSEMBLER_MATRIX_BUILDER.get();
        return player.getMainHandItem().is(builder) || player.getOffhandItem().is(builder);
    }

    private static int colorFor(net.minecraft.world.level.block.Block block) {
        if (block == ModBlocks.SUPER_ASSEMBLER_MATRIX_FRAME.get()) {
            return 0xbff2b82e;
        }
        if (block == ModBlocks.SUPER_ASSEMBLER_MATRIX_WALL.get()) {
            return 0xbf38a6ff;
        }
        if (block == ModBlocks.ASSEMBLER_MATRIX_HYBRID_PLUS.get()) {
            return 0xbf4dff6b;
        }
        return 0xbfdb59ff;
    }
}

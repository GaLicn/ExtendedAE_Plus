package com.extendedae_plus.client;

import com.extendedae_plus.content.matrix.supermatrix.UltimateSuperAssemblerMatrixStructure;
import com.extendedae_plus.init.ModBlocks;
import com.extendedae_plus.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** 手持搭建器时绘制终极结构的客户端线框预览。 */
public final class UltimateSuperAssemblerMatrixPreviewRenderer {

    private static final double OUTLINE_INFLATE = 0.002D;

    private UltimateSuperAssemblerMatrixPreviewRenderer() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null || !player.getAbilities().instabuild
                || !(minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                || !isHoldingBuilder(player)) {
            return;
        }

        var origin = hit.getBlockPos().relative(hit.getDirection());
        PoseStack poseStack = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (var block : UltimateSuperAssemblerMatrixStructure.getPreviewBlocks(minecraft.getResourceManager())) {
            var position = origin.offset(block.offset());
            var box = new AABB(position).inflate(OUTLINE_INFLATE);
            if (!event.getFrustum().isVisible(box)) {
                continue;
            }
            var color = colorFor(block.block());
            LevelRenderer.renderLineBox(poseStack, consumer, box, color[0], color[1], color[2], 0.85F);
        }
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static boolean isHoldingBuilder(net.minecraft.world.entity.player.Player player) {
        var builder = ModItems.ULTIMATE_SUPER_ASSEMBLER_MATRIX_BUILDER.get();
        return player.getMainHandItem().is(builder) || player.getOffhandItem().is(builder);
    }

    private static float[] colorFor(net.minecraft.world.level.block.Block block) {
        if (block == ModBlocks.SUPER_ASSEMBLER_MATRIX_FRAME.get()) {
            return new float[] {0.95F, 0.72F, 0.18F};
        }
        if (block == ModBlocks.SUPER_ASSEMBLER_MATRIX_WALL.get()) {
            return new float[] {0.22F, 0.65F, 1.0F};
        }
        if (block == ModBlocks.ASSEMBLER_MATRIX_HYBRID_PLUS.get()) {
            return new float[] {0.30F, 1.0F, 0.42F};
        }
        return new float[] {0.86F, 0.35F, 1.0F};
    }
}

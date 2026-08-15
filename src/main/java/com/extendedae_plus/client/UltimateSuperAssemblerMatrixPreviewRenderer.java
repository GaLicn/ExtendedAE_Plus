package com.extendedae_plus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.extendedae_plus.ExtendedAEPlus;
import com.extendedae_plus.content.matrix.supermatrix.UltimateSuperAssemblerMatrixStructure;
import com.extendedae_plus.init.ModBlocks;
import com.extendedae_plus.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** 手持搭建器时绘制终极结构的客户端线框预览。 */
@EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT)
public final class UltimateSuperAssemblerMatrixPreviewRenderer {

    private static final double OUTLINE_INFLATE = 0.002D;

    private UltimateSuperAssemblerMatrixPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null
                || !(minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                || !isHoldingBuilder(player)) {
            return;
        }

        var builderStack = player.getMainHandItem().is(ModItems.ULTIMATE_SUPER_ASSEMBLER_MATRIX_BUILDER.get())
                ? player.getMainHandItem() : player.getOffhandItem();
        var selectedOrigin = com.extendedae_plus.items.tools.UltimateSuperAssemblerMatrixBuilderItem
                .getSelectedOrigin(builderStack);
        var origin = selectedOrigin != null && selectedOrigin.dimension().equals(minecraft.level.dimension())
                ? selectedOrigin.pos() : hit.getBlockPos().relative(hit.getDirection());
        PoseStack poseStack = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        var bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 14,
                origin.getY() + 16, origin.getZ() + 14).inflate(OUTLINE_INFLATE);
        var boundsColor = selectedOrigin == null ? new float[] {0.30F, 0.80F, 1.0F} : new float[] {0.35F, 1.0F, 0.45F};
        LevelRenderer.renderLineBox(poseStack, consumer, bounds, boundsColor[0], boundsColor[1], boundsColor[2], 0.90F);
        LevelRenderer.renderLineBox(poseStack, consumer, new AABB(origin).inflate(OUTLINE_INFLATE),
                1.0F, 0.95F, 0.30F, 1.0F);

        // 仅在需要核对细节时绘制部件线框，常规预览保持清晰易读。
        if (Screen.hasControlDown()) {
            for (var block : UltimateSuperAssemblerMatrixStructure.getPreviewBlocks(minecraft.getResourceManager())) {
                // 细节线框单独下移，和外框预览保持清晰的层次关系。
                var position = origin.offset(block.offset()).below();
                var box = new AABB(position).inflate(OUTLINE_INFLATE);
                if (!event.getFrustum().isVisible(box)) {
                    continue;
                }
                var color = colorFor(block.block());
                LevelRenderer.renderLineBox(poseStack, consumer, box, color[0], color[1], color[2], 0.70F);
            }
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

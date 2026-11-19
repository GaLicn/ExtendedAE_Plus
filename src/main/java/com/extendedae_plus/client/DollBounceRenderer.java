package com.extendedae_plus.client;

import com.extendedae_plus.content.decor.DollBlock;
import com.extendedae_plus.content.decor.DollBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

public final class DollBounceRenderer implements BlockEntityRenderer<DollBlockEntity> {
    private static final RenderType LAYER = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
    private final BlockRenderDispatcher blocks;

    public DollBounceRenderer(BlockEntityRendererProvider.Context context) {
        blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(DollBlockEntity doll, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        BlockState state = doll.getBlockState();
        if (doll.getLevel() == null) return;

        float squash = state.getValue(DollBlock.BOUNCING) ? doll.getSquash(partialTick) : 0;
        pose.pushPose();
        pose.translate(0.5, 0.08F * squash, 0.5);
        pose.scale(1 + 0.18F * squash, 1 - 0.27F * squash, 1 + 0.12F * squash);
        pose.translate(-0.5, 0, -0.5);
        blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(LAYER), state,
                blocks.getBlockModel(state), 1, 1, 1, light, overlay);
        pose.popPose();
    }
}

package com.extendedae_plus.client;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * 黑洞各通道会改动的渲染状态的快照，绘制前记录，绘制后原样写回。
 *
 * <p>Forge 1.20.1 没有 GlStateBackup，GlStateManager 缓存的状态又是私有的，故此处直接向 GL 查询
 * 混合、深度、面剔除与颜色掩码，写回时经 RenderSystem 设置，GlStateManager 的缓存随之同步。帧缓冲
 * 绑定与视口不在其中，由 {@link BlackHolePixelSprite} 自行保存。</p>
 */
final class BlackHoleGlState {

    private final boolean blend;
    private final int blendSrcRgb;
    private final int blendDstRgb;
    private final int blendSrcAlpha;
    private final int blendDstAlpha;
    private final boolean depthTest;
    private final int depthFunc;
    private final boolean depthMask;
    private final boolean cull;
    private final boolean[] colorMask = new boolean[4];

    private BlackHoleGlState() {
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < colorMask.length; i++) {
                colorMask[i] = mask.get(i) != 0;
            }
        }
    }

    /** 记录当前状态。须在渲染线程调用。 */
    static BlackHoleGlState capture() {
        RenderSystem.assertOnRenderThread();
        return new BlackHoleGlState();
    }

    /** 把记录的状态写回。 */
    void restore() {
        if (blend) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthFunc(depthFunc);
        RenderSystem.depthMask(depthMask);
        if (cull) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.disableCull();
        }
        RenderSystem.colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
    }
}

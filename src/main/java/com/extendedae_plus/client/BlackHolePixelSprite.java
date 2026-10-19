package com.extendedae_plus.client;

import com.extendedae_plus.ExtendedAEPlus;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/**
 * 像素风格黑洞的离屏精灵。
 *
 * <p>黑洞先以每纹素一个像素画像素的分辨率绘制到一张小目标上，再由天空贴片以最近邻采样
 * 贴到天空中。网格固定在精灵上而非屏幕上，故镜头转动、黑洞随星空移动时像素都不会游动；
 * 且着色器的每个片元恰为一个像素画像素，其屏幕空间导数衡量的正是一个像素，抗锯齿与噪声
 * 淡出因此天然落在像素尺度上。头部黑洞另有一张自己的精灵，贴到屏幕上，见
 * BlackHoleLensRenderer。两张精灵大小不同，各为一个实例，着色器则共用。</p>
 *
 * <p>精灵分两遍绘制：第一遍按调色板绘制原始精灵，第二遍清理孤立杂色像素并加上描边与光晕，
 * 二者都需读取相邻纹素，故只能在独立的一遍中完成。</p>
 */
final class BlackHolePixelSprite {

    // 每个阴影半径对应的纹素数，即黑洞在像素画中的尺寸：取 10 时阴影直径 20 像素，天空精灵为
    // 140×80，正中间横过阴影的那条带约 5 像素厚。取值越小像素越粗；改动时须同比例调整
    // BlackHoleSkyRenderer.SHADOW_TANGENT，像素在屏幕上才保持原大小。
    static final int PIXELS_PER_SHADOW_RADIUS = 10;

    @Nullable
    private static ShaderInstance spriteShader;
    @Nullable
    private static ShaderInstance finishShader;

    // 精灵在像平面上的半宽与半高，单位为阴影半径；黑洞中心位于精灵正中。
    private final float halfWidth;
    private final float halfHeight;
    private final int width;
    private final int height;
    @Nullable
    private VertexBuffer quad;
    @Nullable
    private TextureTarget rawTarget;
    @Nullable
    private TextureTarget finishedTarget;
    private boolean resourcesBuildAttempted;

    /**
     * 只记下尺寸，离屏资源在首次绘制时才创建，故可在类初始化时构造。
     *
     * @param halfWidth  精灵在像平面上的半宽，单位为阴影半径
     * @param halfHeight 精灵在像平面上的半高，单位为阴影半径
     */
    BlackHolePixelSprite(float halfWidth, float halfHeight) {
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
        this.width = Math.round(2.0F * halfWidth * PIXELS_PER_SHADOW_RADIUS);
        this.height = Math.round(2.0F * halfHeight * PIXELS_PER_SHADOW_RADIUS);
    }

    static void setSpriteShader(@Nullable ShaderInstance shaderInstance) {
        spriteShader = shaderInstance;
    }

    static void setFinishShader(@Nullable ShaderInstance shaderInstance) {
        finishShader = shaderInstance;
    }

    /**
     * 绘制本帧的精灵并返回承载成品的目标；着色器或离屏资源不可用时返回 null。
     *
     * <p>绘制期间需切换到离屏目标，而 BlackHoleGlState 不含帧缓冲绑定与视口，故本方法自行保存
     * 二者，返回前原样恢复，调用方无需关心。</p>
     *
     * @param view      从何处、朝哪个方向看黑洞
     * @param starField 是否画出精灵自带的、经透镜弯折的星空。天空中的黑洞需要它；头部黑洞盖在
     *                  真实画面之上，背后的画面已由透镜通道扭曲，故不画。
     */
    @Nullable
    TextureTarget draw(float time, BlackHoleView view, boolean starField) {
        ShaderInstance sprite = spriteShader;
        ShaderInstance finish = finishShader;
        if (sprite == null || finish == null || !ensureResources()) {
            return null;
        }
        TextureTarget raw = rawTarget;
        TextureTarget finished = finishedTarget;
        VertexBuffer activeQuad = quad;

        int drawFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try {
            // 两遍都直接写入成品像素，不与目标原有内容混合；alpha 是精灵的遮挡掩码，必须写入。
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.colorMask(true, true, true, true);

            raw.clear(Minecraft.ON_OSX);
            raw.bindWrite(true);
            sprite.safeGetUniform("uTime").set(time);
            view.apply(sprite);
            sprite.safeGetUniform("uStarField").set(starField ? 1.0F : 0.0F);
            activeQuad.bind();
            activeQuad.drawWithShader(new Matrix4f(), new Matrix4f(), sprite);

            finished.clear(Minecraft.ON_OSX);
            finished.bindWrite(true);
            finish.setSampler("uSpriteTex", raw);
            finish.safeGetUniform("uSpriteTexels").set((float) width, (float) height);
            activeQuad.drawWithShader(new Matrix4f(), new Matrix4f(), finish);
            return finished;
        } finally {
            VertexBuffer.unbind();
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    /**
     * 铺满整个精灵的四边形，顶点铺满 ±1，UV 为像平面坐标（单位为阴影半径）。把它缩放到黑洞在
     * 屏幕上的大小，即为贴精灵的贴片，精灵纹素与贴片位置一一对应。{@link #draw} 成功后方可取用。
     */
    @Nullable
    VertexBuffer quad() {
        return quad;
    }

    private boolean ensureResources() {
        if (quad != null && rawTarget != null && finishedTarget != null) {
            return true;
        }
        if (resourcesBuildAttempted) {
            return false;
        }

        resourcesBuildAttempted = true;
        try {
            rawTarget = createTarget();
            finishedTarget = createTarget();
            quad = createQuad();
            return true;
        } catch (RuntimeException exception) {
            if (rawTarget != null) {
                rawTarget.destroyBuffers();
                rawTarget = null;
            }
            if (finishedTarget != null) {
                finishedTarget.destroyBuffers();
                finishedTarget = null;
            }
            EAP$LOGGER.error("Failed to allocate the pixel-art black-hole sprite", exception);
            return false;
        }
    }

    private TextureTarget createTarget() {
        TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        // RenderTarget 默认清屏色为 (1, 1, 1, 0)。精灵是预乘输出，清屏色的 rgb 会被当作发光
        // 叠加到天空上，故必须清为全零。
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        return target;
    }

    private VertexBuffer createQuad() {
        float u = halfWidth;
        float v = halfHeight;
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1.0, -1.0, 0.0).uv(-u, -v).endVertex();
        builder.vertex(1.0, -1.0, 0.0).uv(u, -v).endVertex();
        builder.vertex(1.0, 1.0, 0.0).uv(u, v).endVertex();
        builder.vertex(-1.0, -1.0, 0.0).uv(-u, -v).endVertex();
        builder.vertex(1.0, 1.0, 0.0).uv(u, v).endVertex();
        builder.vertex(-1.0, 1.0, 0.0).uv(-u, v).endVertex();

        VertexBuffer model = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {
            model.bind();
            try {
                model.upload(builder.end());
            } finally {
                VertexBuffer.unbind();
            }
            return model;
        } catch (RuntimeException exception) {
            model.close();
            throw exception;
        }
    }
}

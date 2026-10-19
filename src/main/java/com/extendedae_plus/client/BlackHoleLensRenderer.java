package com.extendedae_plus.client;

import com.extendedae_plus.ExtendedAEPlus;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/**
 * 屏幕空间引力透镜后处理通道。
 *
 * <p>黑洞本体由 {@link BlackHoleHeadRenderer} 在世界中标记位置，本类在场景渲染完成后
 * 以该位置为圆心重采样已渲染的画面，从而让透镜扭曲的是真实地形与实体，而不是着色器
 * 内部程序生成的星空。这是与贴片方案的本质区别：贴片只能画一张正对相机的假图。</p>
 *
 * <p>采样源必须是场景的副本。若直接以主渲染目标的颜色纹理同时作为采样源与写入目标，
 * 即构成同一纹理的帧缓冲反馈回路，结果未定义。故此处先把场景拷贝到临时目标，再对主
 * 渲染目标绘制全屏四边形。</p>
 *
 * <p>黑洞是世界中的立体物体：吸积盘与地面平行，光线自相机的真实位置出发追踪，见
 * {@link BlackHoleView}。绕着它走能看到盘面的各个侧面，盘上的气体留在原处；从下方仰视时
 * 画面上下翻转；离得越近，近侧盘面比远侧显得越大。两个通道都画在黑洞中心的深度上并开启深度
 * 测试，挡在黑洞前方的方块与实体因此保留原样。</p>
 *
 * <p>像素风格下（见 BlackHoleSkyRenderer.USE_PIXELATED_SHADER）头部黑洞与天空中的黑洞同款：
 * 透镜通道只负责扭曲场景，吸积盘与阴影改由一张像素精灵贴在其上，精灵以同一相机绘制；故障撕裂、
 * 以及故障中换成真实黑洞再换回来，也与天空共用 {@link BlackHoleGlitch} 的时间表。精灵不画自带
 * 的星空，因为背后的真实画面已由透镜扭曲。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlackHoleLensRenderer {

    // 视界（阴影）的半径，单位为世界格。吸积盘外缘远在阴影半径的数倍之外，故盘面在屏幕上的
    // 范围要大得多。该常量直接决定黑洞的视觉大小：取 0.25 格时阴影直径约 0.5 格，第三人称
    // 视角下观感接近一枚头部徽记。实际占屏比例随黑洞与相机的距离自动变化。
    private static final float LENS_WORLD_RADIUS = 0.25F;
    // 以黑洞质量 M 计的阴影半径 3√3，与着色器中的 CRITICAL_IMPACT 相同，用于把世界格换算成 M。
    private static final float CRITICAL_IMPACT = 5.19615242F;
    // 相机高出或低于盘面的角度下限。恰好平视时横过阴影的那条带在像素画中不足一个像素，会被清理
    // 掉而只剩一圈，取 2 度时仍留一像素厚。
    private static final float MIN_ELEVATION_DEGREES = 2.0F;
    // 头部像素精灵在像平面上的半边长，单位为阴影半径。相机越近，近侧盘面在画面中越大，此值须
    // 容得下 FADE_END_DISTANCE 处的整个吸积盘。
    private static final float SPRITE_HALF_EXTENT = 6.0F;
    // 相机与黑洞的距离（格）小于 FADE_START_DISTANCE 时黑洞开始淡出，到 FADE_END_DISTANCE 时
    // 完全消失：再近，近侧盘面便会超出精灵的范围，相机也将闯进吸积盘。
    private static final float FADE_START_DISTANCE = 1.6F;
    private static final float FADE_END_DISTANCE = 1.2F;
    // 着色器动画每 10000 秒循环一次，故此处可对时间取模。
    private static final long TIME_WRAP_TICKS = 200_000L;
    // 低于整个精灵的裁剪高度：头部黑洞不会落到地平线以下。
    private static final float NO_HORIZON_CLIP = -1.0E4F;
    // 头部黑洞的像素精灵，与天空的大小不同，故单独一个实例。
    private static final BlackHolePixelSprite SPRITE = new BlackHolePixelSprite(SPRITE_HALF_EXTENT, SPRITE_HALF_EXTENT);

    @Nullable
    private static ShaderInstance shader;
    @Nullable
    private static VertexBuffer quad;
    @Nullable
    private static TextureTarget sceneCopy;
    private static boolean quadBuildAttempted;

    // 本帧是否在头顶绘制黑洞，以及黑洞的世界坐标。每帧由头部渲染器重新标记。
    private static boolean marked;
    private static double holeX;
    private static double holeY;
    private static double holeZ;

    private BlackHoleLensRenderer() {
    }

    static void setShader(@Nullable ShaderInstance shaderInstance) {
        shader = shaderInstance;
    }

    /**
     * 由头部渲染器在实体渲染阶段调用，记录本帧黑洞的世界坐标。
     *
     * <p>标记在下一次场景渲染完成时被消费并清除，因此玩家卸下饰品后不会再残留上一帧的
     * 黑洞影像。</p>
     */
    static void mark(double x, double y, double z) {
        marked = true;
        holeX = x;
        holeY = y;
        holeZ = z;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        // 无论本帧是否绘制，标记都必须清除，避免卸下饰品后影像残留。
        boolean active = marked;
        marked = false;
        if (!active || shader == null || !ensureQuad()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = event.getCamera();
        if (minecraft.level == null || !camera.isInitialized()) {
            return;
        }

        Vector3f relative = new Vector3f(
                (float) (holeX - camera.getPosition().x),
                (float) (holeY - camera.getPosition().y),
                (float) (holeZ - camera.getPosition().z));
        // 相机贴得太近时黑洞淡出直至消失。
        float distance = relative.length();
        float visibility = Mth.clamp(
                (distance - FADE_END_DISTANCE) / (FADE_START_DISTANCE - FADE_END_DISTANCE), 0.0F, 1.0F);
        if (visibility <= 0.0F) {
            return;
        }
        visibility = visibility * visibility * (3.0F - 2.0F * visibility);

        // Forge 1.20.1 在 AFTER_LEVEL 阶段随事件传入的是投影矩阵栈而非视图矩阵栈，故视图矩阵取
        // GameRenderer 写入 RenderSystem 的逆视图旋转之逆，相机的滚转也含在其中。
        Matrix4f modelView = new Matrix4f(new Matrix3f(RenderSystem.getInverseViewRotationMatrix()).invert());
        Matrix4f projection = event.getProjectionMatrix();

        // 世界坐标先转为相机相对坐标，再进入视图空间。
        Vector3f viewPos = modelView.transformPosition(new Vector3f(relative));
        // 视图空间中相机朝向 -Z，故 z 非负时黑洞位于相机后方或与相机重合。
        if (viewPos.z >= -0.05F) {
            return;
        }

        Vector4f center = projection.transform(new Vector4f(viewPos, 1.0F));
        if (center.w <= 0.0F) {
            return;
        }
        float ndcX = center.x / center.w;
        float ndcY = center.y / center.w;
        float ndcZ = center.z / center.w;
        // 完全在视野之外或远于远裁剪面时跳过，避免无谓的全屏拷贝。
        if (Math.abs(ndcX) > 1.5F || Math.abs(ndcY) > 1.5F || ndcZ >= 1.0F) {
            return;
        }

        RenderTarget main = minecraft.getMainRenderTarget();
        float aspect = (float) main.width / Math.max(1.0F, (float) main.height);

        // 像平面取过黑洞中心、平行于屏幕的平面。在视图空间中自中心沿相机右方与上方各偏移一个阴影
        // 半径，投影后与中心的差值便是像平面两轴在屏幕上的样子（横向乘以宽高比，使两向等长）。二者
        // 张成的面积给出阴影在屏幕上的半径，透视与视场角都已计入；求逆则得到屏幕横纵两向在视图空间
        // 中的方向，视角摇晃带来的微小滚转也一并计入，盘面才始终与地面平行。
        Vector4f rightEdge = projection.transform(
                new Vector4f(viewPos.x + LENS_WORLD_RADIUS, viewPos.y, viewPos.z, 1.0F));
        Vector4f upEdge = projection.transform(
                new Vector4f(viewPos.x, viewPos.y + LENS_WORLD_RADIUS, viewPos.z, 1.0F));
        if (rightEdge.w <= 0.0F || upEdge.w <= 0.0F) {
            return;
        }
        float rightX = (rightEdge.x / rightEdge.w - ndcX) * aspect;
        float rightY = rightEdge.y / rightEdge.w - ndcY;
        float upX = (upEdge.x / upEdge.w - ndcX) * aspect;
        float upY = upEdge.y / upEdge.w - ndcY;
        float radiusUv = 0.5F * (float) Math.sqrt(Math.max(rightX * upY - upX * rightY, 0.0F));
        if (radiusUv < 1.0e-4F) {
            return;
        }
        Matrix3f viewToWorld = new Matrix3f(modelView).transpose();
        Vector3f screenRight = viewToWorld.transform(new Vector3f(upY, -rightY, 0.0F).normalize());
        Vector3f screenUp = viewToWorld.transform(new Vector3f(-upX, rightX, 0.0F).normalize());

        // 吸积盘固定在世界水平面内，故黑洞的坐标轴即世界坐标轴。相机恰好与盘面齐平时略微抬离盘面，
        // 见 MIN_ELEVATION_DEGREES。
        Vector3f direction = new Vector3f(relative).div(-distance);
        float minSine = (float) Math.sin(Math.toRadians(MIN_ELEVATION_DEGREES));
        if (Math.abs(direction.y) < minSine) {
            float level = (float) Math.sqrt((1.0F - minSine * minSine)
                    / (direction.x * direction.x + direction.z * direction.z));
            direction.set(direction.x * level, Math.copySign(minSine, direction.y), direction.z * level);
        }
        BlackHoleView view = new BlackHoleView(
                direction, distance * CRITICAL_IMPACT / LENS_WORLD_RADIUS, screenRight, screenUp);

        float time = ((float) (minecraft.level.getGameTime() % TIME_WRAP_TICKS)
                + event.getPartialTick()) / 20.0F;

        TextureTarget copy = ensureSceneCopy(main);
        if (copy == null) {
            return;
        }

        // 与天空中的黑洞同款：像素风格下平时只画像素黑洞，部分故障才换成真实黑洞。
        ShaderInstance composite = BlackHoleSkyRenderer.pixelatedShader();
        BlackHoleGlitch.State glitch = BlackHoleSkyRenderer.USE_PIXELATED_SHADER && composite != null
                ? BlackHoleGlitch.at(time)
                : BlackHoleGlitch.State.SMOOTH;

        BlackHoleGlState state = BlackHoleGlState.capture();
        try {
            // 精灵须在拷贝场景前画好：其绘制会切换帧缓冲，返回前再原样恢复。精灵不可用时退回
            // 真实黑洞，头顶至少还有一个黑洞。
            TextureTarget sprite = null;
            if (glitch.smoothShare() < 1.0F) {
                sprite = SPRITE.draw(time, view, false);
                if (sprite == null) {
                    glitch = BlackHoleGlitch.State.SMOOTH;
                }
            }

            // 先把场景拷贝到临时目标：主渲染目标的颜色与深度纹理不能同时作为采样源与写入目标。
            // 深度用来分辨弯折后的采样点落在黑洞前方还是后方。
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height,
                    0, 0, copy.width, copy.height,
                    GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

            main.bindWrite(true);

            shader.safeGetUniform("uCenter").set(ndcX * 0.5F + 0.5F, ndcY * 0.5F + 0.5F);
            shader.safeGetUniform("uRadius").set(radiusUv);
            shader.safeGetUniform("uAspect").set(aspect);
            shader.safeGetUniform("uPlaneHalfExtent").set(SPRITE_HALF_EXTENT, SPRITE_HALF_EXTENT);
            shader.safeGetUniform("uVisibility").set(visibility);
            shader.safeGetUniform("uTime").set(time);
            view.apply(shader);
            glitch.apply(shader);
            // 自定义 sampler 名不会被 drawWithShader 内部的 Sampler0..11 覆盖。
            shader.setSampler("uSceneTex", copy);
            // 只有远在黑洞后方的景物才经透镜弯折。落在黑洞旁的采样点（如佩戴者的头）会在阴影周围
            // 多画出一份，着色器据深度把这样的采样点拉回；距离以沿视线方向的格数比较。
            shader.setSampler("uSceneDepth", copy.getDepthTextureId());
            shader.safeGetUniform("uInverseProjection").set(new Matrix4f(projection).invert());
            shader.safeGetUniform("uHoleDistance").set(-viewPos.z);
            // 画在黑洞中心的深度上：场景比黑洞更近处深度测试不通过，挡在前方的方块与实体保持原样。
            // 不写入深度，其后的绘制不受影响。
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();

            quad.bind();
            try {
                quad.drawWithShader(new Matrix4f().translate(0.0F, 0.0F, ndcZ), new Matrix4f(), shader);
            } finally {
                VertexBuffer.unbind();
            }

            VertexBuffer spriteQuad = SPRITE.quad();
            if (sprite != null && spriteQuad != null) {
                stampSprite(composite, sprite, spriteQuad, ndcX, ndcY, ndcZ, radiusUv, aspect, time, visibility,
                        glitch);
            }
        } catch (RuntimeException exception) {
            EAP$LOGGER.error("Failed to apply the black-hole lens pass", exception);
        } finally {
            // 恢复进入本通道前的渲染状态，避免影响其后的手部与界面渲染。
            state.restore();
            main.bindWrite(true);
        }
    }

    /**
     * 以预乘混合把像素精灵贴到黑洞所在的屏幕位置，盖在已扭曲的画面上。
     *
     * <p>精灵四边形的顶点铺满 ±1，对应像平面上 ±SPRITE_HALF_EXTENT（单位为阴影半径）；一个阴影
     * 半径在屏幕上纵向占 2 * radiusUv 个 NDC 单位，横向再除以宽高比。按此缩放并平移到黑洞中心后，
     * 精灵与透镜通道的像平面坐标逐点对齐，二者的故障条带也就落在同一行上。深度与透镜通道相同，
     * 沿用其深度测试。</p>
     */
    private static void stampSprite(ShaderInstance composite, TextureTarget sprite, VertexBuffer spriteQuad,
            float ndcX, float ndcY, float ndcZ, float radiusUv, float aspect, float time, float visibility,
            BlackHoleGlitch.State glitch) {
        float shadowRadius = 2.0F * radiusUv;
        Matrix4f modelView = new Matrix4f()
                .translate(ndcX, ndcY, ndcZ)
                .scale(SPRITE_HALF_EXTENT * shadowRadius / aspect, SPRITE_HALF_EXTENT * shadowRadius, 1.0F);

        // 预乘混合：rgb 为自身发出的光，alpha 为对背后画面的遮挡比例。
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        composite.setSampler("uSpriteTex", sprite);
        composite.safeGetUniform("uSpriteTexels").set((float) sprite.width, (float) sprite.height);
        composite.safeGetUniform("uTime").set(time);
        composite.safeGetUniform("uVisibility").set(visibility);
        // 头部黑洞不随地平线落下。
        composite.safeGetUniform("uHorizon").set(0.0F, 0.0F, 1.0F);
        composite.safeGetUniform("uHorizonClip").set(NO_HORIZON_CLIP);
        glitch.apply(composite);

        spriteQuad.bind();
        try {
            spriteQuad.drawWithShader(modelView, new Matrix4f(), composite);
        } finally {
            VertexBuffer.unbind();
        }
    }

    @Nullable
    private static TextureTarget ensureSceneCopy(RenderTarget main) {
        int width = main.width;
        int height = main.height;
        if (width <= 0 || height <= 0) {
            return null;
        }
        try {
            if (sceneCopy == null) {
                sceneCopy = new TextureTarget(width, height, true, false);
            } else if (sceneCopy.width != width || sceneCopy.height != height) {
                sceneCopy.resize(width, height, false);
            }
            // 深度只能在格式相同的帧缓冲之间拷贝：主渲染目标被其它模组加上模板缓冲时，副本也须跟随。
            if (main.isStencilEnabled()) {
                sceneCopy.enableStencil();
            }
            return sceneCopy;
        } catch (RuntimeException exception) {
            if (sceneCopy != null) {
                sceneCopy.destroyBuffers();
                sceneCopy = null;
            }
            EAP$LOGGER.error("Failed to allocate the black-hole lens target", exception);
            return null;
        }
    }

    private static boolean ensureQuad() {
        if (quad != null) {
            return true;
        }
        if (quadBuildAttempted) {
            return false;
        }

        quadBuildAttempted = true;
        try {
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.vertex(-1.0, -1.0, 0.0).endVertex();
            builder.vertex(1.0, -1.0, 0.0).endVertex();
            builder.vertex(1.0, 1.0, 0.0).endVertex();
            builder.vertex(-1.0, 1.0, 0.0).endVertex();
            buffer.bind();
            buffer.upload(builder.end());
            VertexBuffer.unbind();
            quad = buffer;
            return true;
        } catch (RuntimeException exception) {
            EAP$LOGGER.error("Failed to build the black-hole lens quad", exception);
            return false;
        }
    }
}

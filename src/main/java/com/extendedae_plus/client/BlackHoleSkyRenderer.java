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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static com.extendedae_plus.util.Logger.EAP$LOGGER;

/** 在主世界夜空中绘制风格化黑洞，其方位随星空一同转动。 */
@Mod.EventBusSubscriber(modid = ExtendedAEPlus.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlackHoleSkyRenderer {

    // 黑洞的绘制风格：true 为像素风格（先绘制离屏像素精灵，再贴到天空），部分故障会临时切换到
    // 原版平滑风格，见 BlackHoleGlitch；false 则始终为原版平滑风格。
    static final boolean USE_PIXELATED_SHADER = true;

    // 黑洞初始位置在午夜（正北方向）的仰角。随星空转动，故黄昏自东北升起、黎明自西北落下。
    private static final float MIDNIGHT_ELEVATION_DEGREES = 50.0F;
    // 黑洞在星空中的初始位置，处于 LevelRenderer.renderSky 随时角转动的坐标系内。故障会把它移到
    // 别处，见 celestialPosition。
    private static final Vector3f INITIAL_POSITION = new Vector3f(
            -Mth.cos(MIDNIGHT_ELEVATION_DEGREES * Mth.DEG_TO_RAD),
            -Mth.sin(MIDNIGHT_ELEVATION_DEGREES * Mth.DEG_TO_RAD),
            0.0F).normalize();
    // 每经过 BURSTS_PER_JUMP 次故障，黑洞在其中最后一次故障过半时瞬移到天空中的随机位置：方位任意，
    // 仰角在以下范围内取，此后随星空一同转动。
    private static final int BURSTS_PER_JUMP = 2;
    private static final float JUMP_MIN_ELEVATION_DEGREES = 25.0F;
    private static final float JUMP_MAX_ELEVATION_DEGREES = 65.0F;
    // 新位置随星空转动时所能到达的最高仰角的上限。经过天顶附近时盘面相对地平线的滚转无从定义，
    // 盘面会急转，故会越过此仰角的位置弃之重取，取 JUMP_ATTEMPTS 次仍不成则留在原处。
    private static final float JUMP_MAX_CULMINATION_DEGREES = 75.0F;
    private static final int JUMP_ATTEMPTS = 16;

    // 阴影角半径的正切值，约 6.4 度。像素风格下一个像素占 SHADOW_TANGENT /
    // BlackHolePixelSprite.PIXELS_PER_SHADOW_RADIUS，二者须同比例调整，像素在屏幕上才保持原大小。
    private static final float SHADOW_TANGENT = 0.1125F;
    // 吸积盘相对地平线的倾角，以地面视角观察为逆时针方向。
    private static final float DISK_ROLL_DEGREES = 15.0F;
    // 像素黑洞的吸积盘相对视线方向的倾角。角度越大盘面张开越多，正中间横过阴影的那条带越厚，但
    // 带子也越靠下：超过约 14 度后会盖住阴影的下半部，黑洞底部不再露出。带子的厚度改由放大黑洞
    // （SHADOW_TANGENT）来保证。
    private static final float PIXEL_DISK_TILT_DEGREES = 14.0F;
    // 真实黑洞的倾角。过小时盘面正面压成横过阴影的一条细线，看不到吸积盘的正面；过大则正面那条
    // 带又厚又重。与像素黑洞的角度不同，故障切换时两者形状对不上，只会加重故障感。
    private static final float REAL_DISK_TILT_DEGREES = 9.0F;
    // 天空中的黑洞从无穷远处以上述倾角观看。
    private static final BlackHoleView PIXEL_VIEW = BlackHoleView.farAway(PIXEL_DISK_TILT_DEGREES);
    private static final BlackHoleView REAL_VIEW = BlackHoleView.farAway(REAL_DISK_TILT_DEGREES);
    // 天空贴片半宽与半高，单位为阴影半径。必须与 black_hole.fsh 中的 PLANE_HALF_EXTENT 一致；
    // 像素精灵也按此范围绘制，贴片着色器则由精灵的纹素数推得这一范围。
    private static final float PLANE_HALF_WIDTH = 7.0F;
    private static final float PLANE_HALF_HEIGHT = 4.0F;
    private static final BlackHolePixelSprite SPRITE = new BlackHolePixelSprite(PLANE_HALF_WIDTH, PLANE_HALF_HEIGHT);
    // 着色器动画每 10000 秒循环一次，故此处可对时间取模。
    private static final long TIME_WRAP_TICKS = 200_000L;
    // 玩家位于地平线以下时，原版用一块暗色平面遮盖低空：该平面位于相机下方 4 格、边长 512 格。
    // 此常量为该平面边缘在单位距离处对应的世界高度。
    private static final float DARK_SKY_CLIP = -4.0F / 512.0F;
    // 低于整个贴片的裁剪高度，用于仅由地形遮挡黑洞的情形。
    private static final float NO_CLIP = -1.0E4F;

    @Nullable
    private static ShaderInstance smoothShader;
    // 像素风格贴片着色器，把离屏精灵贴到天空上。
    @Nullable
    private static ShaderInstance pixelatedShader;
    @Nullable
    private static VertexBuffer skyQuad;
    private static boolean modelBuildAttempted;

    private static final RandomSource RANDOM = RandomSource.create();
    // 黑洞当前在星空中的位置，与 INITIAL_POSITION 处于同一坐标系，只在故障中改变。
    private static Vector3f celestialPosition = INITIAL_POSITION;
    // 上一次清点到的故障编号，同一次故障只计一次。
    private static int lastBurst = BlackHoleGlitch.NO_BURST;
    // 自上次瞬移以来经过的故障次数。
    private static int burstsSinceJump;

    private BlackHoleSkyRenderer() {
    }

    static void setSmoothShader(@Nullable ShaderInstance shaderInstance) {
        smoothShader = shaderInstance;
    }

    static void setPixelatedShader(@Nullable ShaderInstance shaderInstance) {
        pixelatedShader = shaderInstance;
    }

    /** 供头部黑洞复用同一份贴片着色器，把它自己的像素精灵贴到屏幕上。 */
    @Nullable
    static ShaderInstance pixelatedShader() {
        return pixelatedShader;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Camera camera = event.getCamera();
        if (level == null
                || (USE_PIXELATED_SHADER ? pixelatedShader : smoothShader) == null
                || !camera.isInitialized()
                || level.dimension() != Level.OVERWORLD
                || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL
                || isSkyHidden(minecraft, level, camera)) {
            return;
        }

        // 随星空淡入，随降雨淡出。
        float partialTick = event.getPartialTick();
        float visibility = Mth.clamp(level.getStarBrightness(partialTick) * 2.0F, 0.0F, 1.0F)
                * (1.0F - level.getRainLevel(partialTick));
        if (visibility <= 0.001F || !ensureModel()) {
            return;
        }

        float time = ((float) (level.getGameTime() % TIME_WRAP_TICKS) + partialTick) / 20.0F;
        // 与太阳、月亮和星空一同转动，方式与 LevelRenderer.renderSky 一致。
        float skyAngle = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        jumpOnGlitch(time, skyAngle);
        Vector3f forward = new Vector3f(celestialPosition).rotateX(skyAngle).rotateY(-Mth.HALF_PI);
        // 吸积盘沿 right 轴展开。先取水平方向，再绕视线方向滚转，使盘面整夜保持相对地平线的倾角。
        // rotateAxis 以地面视角观察为顺时针方向，故取负值。
        Vector3f right = new Vector3f(forward).cross(0.0F, 1.0F, 0.0F).normalize()
                .rotateAxis(-DISK_ROLL_DEGREES * Mth.DEG_TO_RAD, forward.x, forward.y, forward.z);
        Vector3f up = new Vector3f(right).cross(forward);

        // 贴片在单位距离处的世界高度与其平面坐标成线性关系。与太阳和月亮相同，仅当原版将低空
        // 压暗时黑洞才被地平线遮挡；其余情况下由地形遮挡，故黑洞自山后升起。
        Vector3f horizon = new Vector3f(right.y * SHADOW_TANGENT, up.y * SHADOW_TANGENT, forward.y);
        float horizonClip = isLowerSkyDark(minecraft, level, partialTick) ? DARK_SKY_CLIP : NO_CLIP;
        if (horizon.z + Math.abs(horizon.x) * PLANE_HALF_WIDTH + Math.abs(horizon.y) * PLANE_HALF_HEIGHT
                <= horizonClip) {
            return;
        }

        // 贴片在单位距离处朝向正前方构建。先旋转到位，再推至天空绘制距离处。AFTER_SKY 阶段随事件
        // 传入的 PoseStack 只含相机旋转，即视图矩阵，原版绘制天空用的也是它。
        Matrix4f orientation = new Matrix4f(new Matrix3f(right, up, new Vector3f(forward).negate()));
        float distance = Mth.clamp(minecraft.options.getEffectiveRenderDistance() * 8.0F, 16.0F, 128.0F);
        Matrix4f modelView = new Matrix4f(event.getPoseStack().last().pose()).mul(orientation).scale(distance);
        render(modelView, event.getProjectionMatrix(), time, visibility, horizon, horizonClip);
    }

    /** 清点经过的故障，每 BURSTS_PER_JUMP 次在故障过半时把黑洞移到天空中的随机位置。 */
    private static void jumpOnGlitch(float time, float skyAngle) {
        int burst = USE_PIXELATED_SHADER ? BlackHoleGlitch.burstPastHalfway(time) : BlackHoleGlitch.NO_BURST;
        if (burst == BlackHoleGlitch.NO_BURST || burst == lastBurst) {
            return;
        }
        lastBurst = burst;
        if (++burstsSinceJump >= BURSTS_PER_JUMP) {
            burstsSinceJump = 0;
            celestialPosition = randomCelestialPosition(skyAngle);
        }
    }

    /**
     * 在当前天空中地平线以上随机取一个方向，换算为星空坐标系中的位置。该位置的 x 分量沿星空转轴，
     * 转动时不变，决定了此后所能到达的最高仰角。
     */
    private static Vector3f randomCelestialPosition(float skyAngle) {
        float minAxial = Mth.cos(JUMP_MAX_CULMINATION_DEGREES * Mth.DEG_TO_RAD);
        for (int attempt = 0; attempt < JUMP_ATTEMPTS; attempt++) {
            float azimuth = RANDOM.nextFloat() * Mth.TWO_PI;
            float elevation = Mth.lerp(RANDOM.nextFloat(), JUMP_MIN_ELEVATION_DEGREES, JUMP_MAX_ELEVATION_DEGREES)
                    * Mth.DEG_TO_RAD;
            // 先取世界坐标中的方向，再按 onRenderLevelStage 中转动的逆序转回星空坐标系。
            Vector3f position = new Vector3f(Mth.cos(elevation) * Mth.sin(azimuth), Mth.sin(elevation),
                    Mth.cos(elevation) * Mth.cos(azimuth)).rotateY(Mth.HALF_PI).rotateX(-skyAngle);
            if (Math.abs(position.x) >= minAxial) {
                return position;
            }
        }
        return celestialPosition;
    }

    /** 原版是否以暗色平面遮盖低空，判定条件与 LevelRenderer.renderSky 一致。 */
    private static boolean isLowerSkyDark(Minecraft minecraft, ClientLevel level, float partialTick) {
        return minecraft.player != null
                && minecraft.player.getEyePosition(partialTick).y < level.getLevelData().getHorizonHeight(level);
    }

    /** 原版跳过天空绘制的全部条件，并额外包含任何流体，使黑洞在水下同样不可见。 */
    private static boolean isSkyHidden(Minecraft minecraft, ClientLevel level, Camera camera) {
        if (camera.getFluidInCamera() != FogType.NONE) {
            return true;
        }
        if (camera.getEntity() instanceof LivingEntity entity
                && (entity.hasEffect(MobEffects.BLINDNESS) || entity.hasEffect(MobEffects.DARKNESS))) {
            return true;
        }
        Vec3 position = camera.getPosition();
        return level.effects().isFoggyAt(Mth.floor(position.x), Mth.floor(position.y))
                || minecraft.gui.getBossOverlay().shouldCreateWorldFog();
    }

    private static void render(Matrix4f modelView, Matrix4f projection, float time, float visibility,
            Vector3f horizon, float horizonClip) {
        VertexBuffer activeQuad = skyQuad;
        if (activeQuad == null) {
            return;
        }
        // 两个黑洞按条带分摊画面：像素风格下平滑黑洞只在故障切换期间出现，本帧完全看不见的那个不画。
        BlackHoleGlitch.State glitch = USE_PIXELATED_SHADER ? BlackHoleGlitch.at(time) : BlackHoleGlitch.State.SMOOTH;

        BlackHoleGlState state = BlackHoleGlState.capture();
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            ShaderInstance pixelated = pixelatedShader;
            if (glitch.smoothShare() < 1.0F && pixelated != null) {
                // 先把黑洞画进离屏精灵，贴片着色器只负责把精灵贴到天空并叠加故障与溶解效果。
                TextureTarget sprite = SPRITE.draw(time, PIXEL_VIEW, true);
                if (sprite != null) {
                    pixelated.setSampler("uSpriteTex", sprite);
                    pixelated.safeGetUniform("uSpriteTexels").set((float) sprite.width, (float) sprite.height);
                    drawQuad(activeQuad, pixelated, modelView, projection, time, visibility, horizon, horizonClip,
                            glitch);
                }
            }

            ShaderInstance smooth = smoothShader;
            if (glitch.smoothShare() > 0.0F && smooth != null) {
                REAL_VIEW.apply(smooth);
                drawQuad(activeQuad, smooth, modelView, projection, time, visibility, horizon, horizonClip, glitch);
            }
        } finally {
            state.restore();
        }
    }

    /** 以预乘混合把天空贴片画到天空上，并写入两个黑洞着色器共有的 uniform。 */
    private static void drawQuad(VertexBuffer quad, ShaderInstance shaderInstance, Matrix4f modelView,
            Matrix4f projection, float time, float visibility, Vector3f horizon, float horizonClip,
            BlackHoleGlitch.State glitch) {
        // 精灵绘制会关闭混合，故混合状态须在每次贴到天空前重新设置。
        RenderSystem.enableBlend();
        // 预乘混合：rgb 为自身发出的光，alpha 为对背景星空的遮挡比例。
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        shaderInstance.safeGetUniform("uTime").set(time);
        shaderInstance.safeGetUniform("uVisibility").set(visibility);
        shaderInstance.safeGetUniform("uHorizon").set(horizon);
        shaderInstance.safeGetUniform("uHorizonClip").set(horizonClip);
        glitch.apply(shaderInstance);
        quad.bind();
        try {
            quad.drawWithShader(modelView, projection, shaderInstance);
        } finally {
            VertexBuffer.unbind();
        }
    }

    private static boolean ensureModel() {
        if (skyQuad != null) {
            return true;
        }
        if (modelBuildAttempted) {
            return false;
        }

        modelBuildAttempted = true;
        try {
            skyQuad = createSkyQuad();
            return true;
        } catch (RuntimeException exception) {
            if (skyQuad != null) {
                skyQuad.close();
                skyQuad = null;
            }
            EAP$LOGGER.error("Failed to build the black-hole sky quad", exception);
            return false;
        }
    }

    /**
     * 在单位球面正前方（-Z 方向）的切平面上构建一个贴片。其 UV 为该平面上以阴影半径为单位的坐标，
     * 使着色器绘制的圆在透视投影下仍保持为圆。
     */
    private static VertexBuffer createSkyQuad() {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
        addVertex(builder, -PLANE_HALF_WIDTH, -PLANE_HALF_HEIGHT);
        addVertex(builder, PLANE_HALF_WIDTH, -PLANE_HALF_HEIGHT);
        addVertex(builder, PLANE_HALF_WIDTH, PLANE_HALF_HEIGHT);
        addVertex(builder, -PLANE_HALF_WIDTH, -PLANE_HALF_HEIGHT);
        addVertex(builder, PLANE_HALF_WIDTH, PLANE_HALF_HEIGHT);
        addVertex(builder, -PLANE_HALF_WIDTH, PLANE_HALF_HEIGHT);

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

    private static void addVertex(BufferBuilder builder, float u, float v) {
        builder.vertex(u * SHADOW_TANGENT, v * SHADOW_TANGENT, -1.0F).uv(u, v).endVertex();
    }
}
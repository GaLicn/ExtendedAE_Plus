#version 150

// 屏幕空间引力透镜：将佩戴吞噬核心时位于玩家头顶的黑洞合成到已渲染场景上，并使其周围的真实画面像素发生弯折。
//
// 本通道是 black_hole.fsh 的后处理对应实现。黑洞本体复用 black_hole_real.glsl；区别仅在背景来源。
// black_hole.fsh 对程序星场采样，因此只能弯曲星场。本通道从 uSceneTex 采样已完成渲染的场景副本，
// 因此同一偏折效果可作用于玩家实际看到的地形、实体和天空。
//
// 黑洞是世界中的实体，其吸积盘与地面平行；光线从相机的真实位置发出，该位置由
// black_hole_geodesic.glsl 的 uniform 指定。本通道在黑洞中心深度绘制，并启用深度测试，
// 因此位于黑洞前方的内容保持未修改。
//
// 选择像素风格黑洞时，本通道仅在显示真实风格黑洞的故障条带内绘制本体，其余区域仅弯曲场景；
// BlackHoleLensRenderer 再将像素风格精灵合成到其上。故障条带、撕裂效果和时间表通过
// black_hole_glitch.glsl 与天空黑洞共用。
//
// 部分 GL 驱动会拒绝包含非 ASCII 字节的 GLSL 源码（包括注释）。加载本资源时会先
// 剥离非 ASCII 字节，再将源码提交给驱动。

in vec2 vScreenUv;

// 已渲染场景；在光线偏折后的坐标处采样。
uniform sampler2D uSceneTex;
// 已渲染场景的深度、绘制时使用的投影矩阵的逆矩阵，以及黑洞中心沿视线轴的距离（方块）。
// 这些数据用于区分黑洞后方与前方的内容。
uniform sampler2D uSceneDepth;
uniform mat4 uInverseProjection;
uniform float uHoleDistance;
// 黑洞中心的屏幕 UV 坐标，原点位于左下角。
uniform vec2 uCenter;
// 阴影半径在屏幕 UV 的 y 轴方向上的长度。
uniform float uRadius;
// 屏幕宽高比（宽度 / 高度），用于保持透镜形状为圆形。
uniform float uAspect;
// 像素风格精灵在像平面上的范围，单位为阴影半径，用于使本通道的故障条带与其上方合成的精灵对齐。
uniform vec2 uPlaneHalfExtent;
// 取 1 时完整显示黑洞；相机接近黑洞时，该值降低，使透镜和黑洞同步淡出。
uniform float uVisibility;

uniform float uTime;

out vec4 fragColor;

// ============================================================================
// 可调参数
//
// 黑洞本体参数在 black_hole_real.glsl 中配置。
// ============================================================================

// ---- 透镜效果 ----
// 透镜偏折在该阴影半径范围内逐渐衰减至零。偏折本身的衰减较慢，因此该参数控制场景弯曲延伸至黑洞
// 周围的距离；超过 LENS_FADE_END 后不再偏移。场景偏移强度由 black_hole_geodesic.glsl 中的
// LENS_STRENGTH 控制。
const float LENS_FADE_START = 2.0;
const float LENS_FADE_END = 4.5;

// ============================================================================
// 固定参数，通常无需调整
// ============================================================================

#define PLANE_HALF_EXTENT uPlaneHalfExtent
// 只有位于黑洞中心后方超过该距离（方块）的场景才会通过透镜弯曲。此项用于排除位于黑洞正下方、
// 与相机距离相近的佩戴者。
const float BEHIND_MARGIN = 1.5;
// 在此距离（方块）范围内逐渐增强弯曲效果，使佩戴者身后的地面不会在效果起点处出现撕裂边界。
const float BEHIND_RAMP = 2.0;
// 当偏折后的采样点并未落在黑洞后方时，将其逐步拉回原像素位置所使用的迭代步数。
const int BEHIND_STEPS = 4;

// 故障条带及共用哈希函数。
#moj_import <extendedae_plus:black_hole_glitch.glsl>
// 相机、光线积分和透镜计算。
#moj_import <extendedae_plus:black_hole_geodesic.glsl>
// 黑洞本体。
#moj_import <extendedae_plus:black_hole_real.glsl>

// 计算屏幕上指定位置处场景的视线方向距离，单位为方块。
float sceneDistance(vec2 uv) {
    float depth = texture(uSceneDepth, uv).r;
    vec4 view = uInverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return -view.z / view.w;
}

void main() {
    // 计算相对黑洞中心的屏幕偏移；按各向同性方式校正后，以像平面上的阴影半径为单位表示。
    vec2 offsetUv = vScreenUv - uCenter;
    vec2 plane = vec2(offsetUv.x * uAspect, offsetUv.y) / max(uRadius, 1.0e-5);

    // 像素风格黑洞已接管的条带不绘制真实风格黑洞；故障期间部分条带会横向撕裂。撕裂范围限制在
    // 精灵覆盖区域内，与天空渲染保持一致，避免精灵以外的地形也产生撕裂。
    float band = glitchBand(plane);
    float shown = showsSmooth(band) ? uVisibility : 0.0;
    vec2 inside = step(abs(plane), PLANE_HALF_EXTENT);
    vec2 q = plane + vec2(glitchTear(band) * uGlitchTexel * inside.x * inside.y, 0.0);
    float rho = length(q);

    vec4 hole = realBlackHole(q, shown > 0.0) * shown;

    // 背景来自真实场景，并在光线偏折后对应的坐标处重新采样。冲量参数为 impact 的光线对应
    // sourceRadius；该值表示光线来源方向。sourceRadius 为负时，光线绕行超过半周并采样屏幕另一侧，
    // 因此阴影周围会形成环状结构。该偏折使世界场景产生可见弯曲。近距离观察时，冲量参数略大于
    // 像平面距离；按冲量参数采样可使环状结构贴近阴影边缘。
    float impact = cameraRay(q).impact;
    float sourceRadius = impact - LENS_STRENGTH * deflectionAngle(impact);
    vec2 skyPoint = q / max(impact, 1.0e-4) * sourceRadius;
    vec2 warpedUv = uCenter + vec2(skyPoint.x / uAspect, skyPoint.y) * uRadius;
    // 仅当场景采样点位于黑洞后方时，才对相应像素进行弯折。位于黑洞正下方的佩戴者保持原样；
    // 若将其弯折，头部局部会被后方场景替换。
    float behind = smoothstep(BEHIND_MARGIN, BEHIND_MARGIN + BEHIND_RAMP,
            sceneDistance(vScreenUv) - uHoleDistance);
    float influence = (1.0 - smoothstep(LENS_FADE_START, LENS_FADE_END, rho)) * uVisibility * behind;
    vec2 sampleUv = clamp(mix(vScreenUv, warpedUv, influence), vec2(0.0), vec2(1.0));
    // 仅显示位于黑洞后方的场景。若偏折后的光线落到黑洞旁的物体（例如佩戴者头部），会在阴影周围
    // 重复绘制该物体。因此将此类采样点逐步拉回原像素位置；最后一次迭代会使采样点回到原处，
    // 使场景保持未修改。
    vec2 pullBack = (sampleUv - vScreenUv) / float(BEHIND_STEPS);
    for (int i = 0; i < BEHIND_STEPS; i++) {
        if (sceneDistance(sampleUv) >= uHoleDistance + BEHIND_MARGIN) {
            break;
        }
        sampleUv -= pullBack;
    }
    vec3 scene = texture(uSceneTex, sampleUv).rgb;

    // 在透镜未遮挡的区域上叠加发出的光。远离黑洞时两个贡献均趋于零，场景保持原样通过。
    fragColor = vec4(toneMap(hole.rgb) + scene * (1.0 - hole.a), 1.0);
}

#version 150

// 主世界夜空中的真实风格黑洞。
//
// vPlane 表示天空切平面上的位置，单位为阴影半径，因此阴影边界为单位圆。黑洞本体参考电影
// Interstellar 中的 Gargantua，由 black_hole_real.glsl 根据 black_hole_geodesic.glsl
// 追踪的光线绘制；此处相机位于无穷远处并以固定角度观察。本文件补充黑洞后方的程序生成星场；
// 星场按照与盘面光线相同的偏折角弯曲，因此邻近黑洞的恒星会被拉伸成弧线，并在光子环外侧形成环状结构。
//
// 输出采用预乘 alpha：rgb 表示发出的光，alpha 表示黑洞对后方天空的遮挡比例。
//
// 选择像素风格黑洞时，本着色器仅在切入与切回的两次故障之间显示；故障期间仅在本着色器接管的
// 条带内显示，并按照与像素风格相同的方式撕裂，详见 black_hole_glitch.glsl。其他时间
// uSmoothShare 为 1 且 uGlitch 为 0，此时显示完整黑洞。
//
// 部分 GL 驱动会拒绝包含非 ASCII 字节的 GLSL 源码（包括注释）。加载本资源时会先
// 剥离非 ASCII 字节，再将源码提交给驱动。

in vec2 vPlane;

uniform float uTime;
uniform float uVisibility;
// 在单位距离处，平面点所对应方向的世界高度：xy 分别表示沿两条轴、每个阴影半径对应的高度，
// z 表示中心处高度。
uniform vec3 uHorizon;
// 当地平线隐藏天空时采用的高度阈值。玩家低于地平线时，原版以暗色平面遮挡低空；其他情况下，
// 该阈值远低于黑洞，仅地形会遮挡黑洞。
uniform float uHorizonClip;

out vec4 fragColor;

// ============================================================================
// 可调参数
//
// 黑洞本体参数在 black_hole_real.glsl 中配置。
// ============================================================================

// ---- 透镜星场 ----
// 每个像素按照光线从远处到达时的方向采样星场；透镜偏折强度由 black_hole_geodesic.glsl
// 中的 LENS_STRENGTH 控制，STAR_* 参数用于配置程序星场。
const float STAR_BRIGHTNESS = 1.5;
// 星场参数：每个阴影半径包含的格点数、含星格点比例、星体核心在格点内的锐度及星体颜色。
// 淡出参数用于在格点接近像素尺寸时降低星场亮度，以避免远处恒星闪烁。
const float STAR_DENSITY = 7.0;
const float STAR_THRESHOLD = 0.965;
const float STAR_CORE_SHARPNESS = 90.0;
const float STAR_FADE_START = 0.35;
const float STAR_FADE_END = 0.75;
const vec3 STAR_COLOR = vec3(0.85, 0.90, 1.0);
// 在天空四边形边界处将弯曲后的天空淡出至透明，以避免与原版星场的接合边缘可见。
const float STAR_SKY_OPACITY = 1.0;

// ---- 取景 ----
// 天空四边形从半范围的该比例位置开始淡出为透明。
const float WINDOW_FADE_START = 0.75;

// ============================================================================
// 固定参数，通常无需调整
// ============================================================================

// 必须与 BlackHoleSkyRenderer.PLANE_HALF_WIDTH / PLANE_HALF_HEIGHT 一致。
const vec2 PLANE_HALF_EXTENT = vec2(7.0, 4.0);

// 故障条带及其共用哈希函数。
#moj_import <extendedae_plus:black_hole_glitch.glsl>
// 相机、光线积分和透镜计算。
#moj_import <extendedae_plus:black_hole_geodesic.glsl>
// 黑洞本体。
#moj_import <extendedae_plus:black_hole_real.glsl>

// 在以阴影半径为单位的天空位置采样程序星场。每个格点根据哈希偏移放置一颗恒星，因此星场固定在
// 天空坐标系中，而非屏幕坐标系。cellsPerPixel 用于在格点接近像素尺寸时淡出，以避免远处恒星闪烁。
float starField(vec2 p, vec2 cellsPerPixel) {
    float detail = 1.0 - smoothstep(STAR_FADE_START, STAR_FADE_END, max(cellsPerPixel.x, cellsPerPixel.y));
    if (detail <= 0.0) {
        return 0.0;
    }
    vec2 grid = p * STAR_DENSITY;
    vec2 cell = floor(grid);
    uvec2 c = uvec2(ivec2(cell) + 32768);
    uint seed = hashUint(c.x ^ hashUint(c.y ^ 101u));
    if (float(seed >> 8u) * (1.0 / 16777216.0) < STAR_THRESHOLD) {
        return 0.0;
    }
    // 根据随机种子的后续两次哈希计算恒星在所属格点内的偏移量。
    vec2 offset = vec2(
            float(hashUint(seed ^ 0x9e3779b9u) >> 8u),
            float(hashUint(seed ^ 0x85ebca6bu) >> 8u)) * (1.0 / 16777216.0);
    vec2 delta = grid - cell - offset;
    return exp(-dot(delta, delta) * STAR_CORE_SHARPNESS) * detail;
}

void main() {
    // 像素风格黑洞已接管的条带不绘制；故障期间部分条带会横向撕裂。撕裂仅移动图像，地平线遮挡仍按
    // 片元的实际位置判定。
    float band = glitchBand(vPlane);
    float shown = showsSmooth(band) ? 1.0 : 0.0;
    vec2 q = vPlane + vec2(glitchTear(band) * uGlitchTexel, 0.0);
    float rho = length(q);
    float height = dot(uHorizon.xy, vPlane) + uHorizon.z;
    float aboveHorizon = clamp((height - uHorizonClip) / max(fwidth(height), 1.0e-6), 0.0, 1.0);

    vec4 hole = realBlackHole(q, aboveHorizon > 0.0 && shown > 0.0);

    // 背景天空按照盘面光线所受的同一偏折角弯曲。冲量参数为 rho 的光线对应天空上的 sourceRadius；
    // 该值小于零时，光线已绕行超过半周并采样屏幕另一侧，因此星场会在阴影周围形成环状结构。
    // 阴影和吸积盘成像均会遮挡该星场。
    float sourceRadius = rho - LENS_STRENGTH * deflectionAngle(rho);
    vec2 skyPoint = q / max(rho, 1.0e-4) * sourceRadius;
    vec2 skyCellsPerPixel = fwidth(skyPoint) * STAR_DENSITY;
    vec3 skyLight = STAR_COLOR * (starField(skyPoint, skyCellsPerPixel) * STAR_BRIGHTNESS * STAR_SKY_OPACITY);

    vec3 color = toneMap(hole.rgb + skyLight * (1.0 - hole.a));
    float occlusion = hole.a;

    float window = 1.0 - smoothstep(WINDOW_FADE_START, 1.0, length(q / PLANE_HALF_EXTENT));
    float fade = window * aboveHorizon * uVisibility * shown;
    color *= fade;
    occlusion *= fade;
    if (occlusion < 0.002 && max(color.r, max(color.g, color.b)) < 0.002) {
        discard;
    }
    fragColor = vec4(color, occlusion);
}

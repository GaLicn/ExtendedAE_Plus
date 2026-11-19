#version 150

// 像素风格黑洞合成通道：将 black_hole_pixelated 与 black_hole_pixelated_finish 生成的
// 精灵合成到天空；头顶黑洞则合成到真实场景。通道使用最近邻采样，使不同分辨率、视场角和
// 观察距离下的每个精灵纹素仍对应清晰的屏幕像素块。
//
// 合成时还添加两种效果：故障层会将连续纹素行横向错移，并为黑洞轮廓添加品红色和青色残影；
// 溶解效果则在黄昏时逐纹素显现，并在降雨时逐纹素消退。所有偏移均为整数纹素，因此不会破坏像素网格。
//
// 部分故障会在像素精灵与 black_hole.fsh 的平滑黑洞之间切换。该通道在平滑黑洞已接管的条带中
// 不绘制像素精灵；条带、撕裂效果和时间表通过 black_hole_glitch.glsl 共用。
//
// vPlane 表示像平面位置，单位为阴影半径；精灵覆盖范围恰为 -PLANE_HALF_EXTENT 至
// +PLANE_HALF_EXTENT。
//
// 输出采用预乘 alpha：rgb 表示发出的光，alpha 表示对后方天空的遮挡比例。
//
// 部分 GL 驱动会拒绝包含非 ASCII 字节的 GLSL 源码（包括注释）。加载本资源时会先
// 剥离非 ASCII 字节，再将源码提交给驱动。

in vec2 vPlane;

// 像素风格精灵及其纹素尺寸。
uniform sampler2D uSpriteTex;
uniform vec2 uSpriteTexels;
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
// ============================================================================

// ---- 故障 ----
// 黑洞偶尔出现条带撕裂效果。故障时机由 BlackHoleGlitch 调度，条带与撕裂由
// black_hole_glitch.glsl 计算。故障期间，轮廓残影分别向两侧偏移指定纹素数，并仅在精灵轮廓
// 以外显示，使用单一颜色。该效果模拟颜色通道错位；直接分离 RGB 通道会产生调色板之外的颜色。
const float GLITCH_GHOST_TEXELS = 2.0;
const vec3 GLITCH_GHOST_WARM = vec3(1.00, 0.16, 0.56);
const vec3 GLITCH_GHOST_COOL = vec3(0.10, 0.85, 1.00);

// ---- 溶解 ----
// 黑洞淡入和淡出时，精灵纹素逐个显现或消失，而非整体降低透明度。显隐顺序由哈希确定，并由
// DISSOLVE_RADIAL_BIAS 向外侧调整，因此阴影和光子环先形成，吸积盘边缘最后出现。
const float DISSOLVE_RADIAL_BIAS = 0.75;

// ============================================================================
// 固定参数，通常无需调整
// ============================================================================

// 精灵范围的半长和半高，单位为阴影半径；其值等于精灵对应方向的半纹素数乘以单个纹素的尺寸。
// 天空精灵和头部精灵的形状不同。
#define PLANE_HALF_EXTENT (0.5 * uSpriteTexels * uGlitchTexel)

#moj_import <extendedae_plus:black_hole_glitch.glsl>

// 按整数纹素坐标读取精灵纹素。texelFetch 可在任意纹理过滤模式下执行精确读取；边界钳制可避免
// 撕裂条带读取透明边界以外的区域。
vec4 spriteTexel(vec2 texel) {
    return texelFetch(uSpriteTex, ivec2(clamp(texel, vec2(0.0), uSpriteTexels - 1.0)), 0);
}

void main() {
    // 当前屏幕像素下方的精灵纹素。
    vec2 texel = floor((vPlane / PLANE_HALF_EXTENT * 0.5 + 0.5) * uSpriteTexels);

    // ---- 故障 ----
    // 平滑黑洞已接管的条带不再绘制像素精灵；故障期间部分条带按整数纹素横向错移。
    float band = glitchBand(vPlane);
    float pixelArt = showsSmooth(band) ? 0.0 : 1.0;
    vec2 source = texel + vec2(glitchTear(band), 0.0);

    vec4 sprite = spriteTexel(source);
    vec3 color = sprite.rgb;
    float occlusion = sprite.a;

    // 残影：在纹素透明的位置，分别向两侧读取轮廓副本并以暖色和冷色显示。残影只增加光照，不遮挡天空。
    if (uGlitch > 0.0 && sprite.a < 0.5) {
        vec2 offset = vec2(GLITCH_GHOST_TEXELS, 0.0);
        if (spriteTexel(source - offset).a >= 0.5) {
            color = GLITCH_GHOST_WARM;
        } else if (spriteTexel(source + offset).a >= 0.5) {
            color = GLITCH_GHOST_COOL;
        }
    }

    // ---- 溶解 ----
    // 可见度超过某纹素对应的顺序阈值后，该纹素开始显示。每个纹素的顺序固定，因此精灵会连续组装，
    // 不会因逐游戏刻的亮度变化而闪烁。
    float outwards = length((texel + 0.5) / uSpriteTexels - 0.5) * 2.0;
    float order = mix(hashCell(texel, 233u), clamp(outwards, 0.0, 1.0), DISSOLVE_RADIAL_BIAS);
    float shown = step(order, uVisibility * 1.001);

    // ---- 地平线 ----
    // 按连续平面坐标而非纹素坐标执行测试，使黑洞在暗色低空之后沿平直边缘显现，避免阶梯状边缘。
    float height = dot(uHorizon.xy, vPlane) + uHorizon.z;
    float aboveHorizon = clamp((height - uHorizonClip) / max(fwidth(height), 1.0e-6), 0.0, 1.0);

    float fade = pixelArt * shown * aboveHorizon;
    color *= fade;
    occlusion *= fade;
    if (occlusion < 0.002 && max(color.r, max(color.g, color.b)) < 0.002) {
        discard;
    }
    fragColor = vec4(color, occlusion);
}

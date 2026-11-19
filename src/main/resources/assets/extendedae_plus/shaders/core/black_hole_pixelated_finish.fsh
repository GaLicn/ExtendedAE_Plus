#version 150

// 像素风格黑洞精灵的收尾通道。该通道以精灵分辨率运行，每个片元对应一个纹素，位于
// 绘制原始精灵的 black_hole_pixelated 与将成品精灵合成到天空的
// black_hole_pixelated_composite 之间。
//
// 该通道执行精灵完成后所需的邻域处理：
//
// - 填补间隙：两个有光纹素之间仅隔一个透明天空或阴影纹素时（例如光子环与吸积盘内缘之间），
//   以相邻有光纹素的颜色填充该间隙。否则，描边会在阴影边缘形成深色孤立点。
// - 清理孤立纹素：若实色纹素的颜色与周围八个纹素均不同，则将其替换为邻域中出现次数最多的颜色，
//   使每种颜色至少以两个相邻纹素构成色块。
// - 描绘边缘：与阴影相邻的有光纹素统一使用明亮边缘色，使孔洞沿整圈与吸积盘区分开，即使邻近
//   气体亮度较低也能保持辨识度。
// - 描边与辉光：将轮廓外侧相邻的透明纹素填为深色描边，再将其外侧一圈填为较淡的辉光色，使亮度
//   分两级衰减后结束，形成精灵自身的辉光。
//
// 输出采用预乘 alpha，与原始精灵一致：rgb 表示发出的光，alpha 表示对后方天空的遮挡比例。
// 描边和辉光仅增加光照，不遮挡天空。
//
// 部分 GL 驱动会拒绝包含非 ASCII 字节的 GLSL 源码（包括注释）。加载本资源时会先
// 剥离非 ASCII 字节，再将源码提交给驱动。

// 原始精灵及其纹素尺寸。
uniform sampler2D uSpriteTex;
uniform vec2 uSpriteTexels;

out vec4 fragColor;

// ============================================================================
// 可调参数
// ============================================================================

// 有光轮廓的描边颜色，以及其外侧较淡辉光环的颜色。将 GLOW_COLOR 设为黑色可仅保留描边。
const vec3 OUTLINE_COLOR = vec3(0.30, 0.06, 0.48);
const vec3 GLOW_COLOR = vec3(0.16, 0.04, 0.30);
// 与阴影相邻的有光纹素所使用的颜色。
const vec3 RIM_COLOR = vec3(1.00, 1.00, 0.94);

// ============================================================================
// 辅助函数
// ============================================================================

// 按整数纹素坐标读取原始精灵纹素；坐标超出精灵范围时返回透明天空。
vec4 rawTexel(vec2 texel) {
    if (any(lessThan(texel, vec2(0.0))) || any(greaterThanEqual(texel, uSpriteTexels))) {
        return vec4(0.0);
    }
    return texelFetch(uSpriteTex, ivec2(texel), 0);
}

bool sameColour(vec4 a, vec4 b) {
    vec4 d = abs(a - b);
    return max(max(d.r, d.g), max(d.b, d.a)) < 0.02;
}

// 会遮挡天空的实色纹素：吸积盘、光子环或黑色阴影。恒星和透明天空只增加光照，因此不参与清理。
bool isSolid(vec4 texel) {
    return texel.a >= 0.5;
}

// 非黑色的实色纹素，即吸积盘或光子环。
bool isLit(vec4 texel) {
    return isSolid(texel) && max(texel.r, max(texel.g, texel.b)) >= 0.05;
}

// 黑色实色纹素，即阴影。
bool isShadow(vec4 texel) {
    return isSolid(texel) && !isLit(texel);
}

// 执行间隙填补和孤立纹素清理后的纹素。若未点亮纹素在上、下、左、右任一方向被有光纹素夹住，
// 则替换为上方或右侧纹素的颜色。若实色纹素与周围八个纹素颜色均不同，则优先采用四个直接相邻
// 纹素中至少有两个相同的颜色；若不存在，则采用左侧纹素的颜色。
vec4 cleanTexel(vec2 texel) {
    vec4 centre = rawTexel(texel);
    vec4 east = rawTexel(texel + vec2(1.0, 0.0));
    vec4 west = rawTexel(texel - vec2(1.0, 0.0));
    vec4 north = rawTexel(texel + vec2(0.0, 1.0));
    vec4 south = rawTexel(texel - vec2(0.0, 1.0));
    if (!isLit(centre)) {
        if (isLit(north) && isLit(south)) {
            return north;
        }
        if (isLit(east) && isLit(west)) {
            return east;
        }
    }
    if (!isSolid(centre)) {
        return centre;
    }
    if (sameColour(centre, east) || sameColour(centre, west)
            || sameColour(centre, north) || sameColour(centre, south)
            || sameColour(centre, rawTexel(texel + vec2(1.0, 1.0)))
            || sameColour(centre, rawTexel(texel + vec2(-1.0, 1.0)))
            || sameColour(centre, rawTexel(texel + vec2(1.0, -1.0)))
            || sameColour(centre, rawTexel(texel + vec2(-1.0, -1.0)))) {
        return centre;
    }
    if (sameColour(east, north) || sameColour(east, south) || sameColour(east, west)) {
        return east;
    }
    if (sameColour(north, south) || sameColour(north, west)) {
        return north;
    }
    if (sameColour(south, west)) {
        return south;
    }
    return west;
}

// 判断清理后的纹素是否属于需要描边的有光轮廓：必须为实色且非黑色，因此阴影本身不会被描边。
float litAt(vec2 texel) {
    return isLit(cleanTexel(texel)) ? 1.0 : 0.0;
}

bool shadowAt(vec2 texel) {
    return isShadow(cleanTexel(texel));
}

void main() {
    vec2 texel = floor(gl_FragCoord.xy);
    vec4 colour = cleanTexel(texel);

    if (isLit(colour) && (shadowAt(texel + vec2(1.0, 0.0)) || shadowAt(texel - vec2(1.0, 0.0))
            || shadowAt(texel + vec2(0.0, 1.0)) || shadowAt(texel - vec2(0.0, 1.0)))) {
        colour = vec4(RIM_COLOR, 1.0);
    }

    // 仅透明纹素使用描边色，使描边位于轮廓外侧，不覆盖轮廓本身或恒星。
    if (!isSolid(colour) && max(colour.r, max(colour.g, colour.b)) < 0.05) {
        float touching = max(max(litAt(texel + vec2(1.0, 0.0)), litAt(texel - vec2(1.0, 0.0))),
                max(litAt(texel + vec2(0.0, 1.0)), litAt(texel - vec2(0.0, 1.0))));
        if (touching > 0.0) {
            colour = vec4(OUTLINE_COLOR, 0.0);
        } else {
            float near = max(max(max(litAt(texel + vec2(2.0, 0.0)), litAt(texel - vec2(2.0, 0.0))),
                    max(litAt(texel + vec2(0.0, 2.0)), litAt(texel - vec2(0.0, 2.0)))),
                    max(max(litAt(texel + vec2(1.0, 1.0)), litAt(texel - vec2(1.0, 1.0))),
                    max(litAt(texel + vec2(1.0, -1.0)), litAt(texel - vec2(1.0, -1.0)))));
            colour = vec4(GLOW_COLOR * near, 0.0);
        }
    }
    fragColor = colour;
}

// 本文件定义两个天空黑洞共用的故障条带，分别对应 black_hole.fsh 中的平滑黑洞
// 和 black_hole_pixelated_composite.fsh 中的像素黑洞。两者按相同的水平条带划分，
// 并沿相同的行产生撕裂，使故障期间逐条带切换时保持画面一致。
//
// 故障时机及是否切换至平滑黑洞由 CPU 端的 BlackHoleGlitch 计算，并通过下列 uniform
// 传入；本文件据此生成条带，并提供两个着色器共用的整数哈希函数。
//
// 导入本文件前，调用方必须声明 uTime 和 PLANE_HALF_EXTENT。
//
// 部分 GL 驱动会拒绝包含非 ASCII 字节的 GLSL 源码（包括注释）。加载本资源时会先
// 剥离非 ASCII 字节，再将源码提交给驱动。

// 故障持续期间为 1，其他时间为 0。
uniform float uGlitch;
// 显示平滑黑洞而非像素黑洞的条带比例。两次故障之间取 0 或 1；故障期间，比例随条带
// 切换逐渐增大或减小。
uniform float uSmoothShare;
// 一个像素精灵纹素对应的阴影半径单位。两种黑洞的条带与撕裂偏移均按整数纹素计算，
// 以保证彼此对齐。
uniform float uGlitchTexel;

// 故障持续期间每秒重新生成图案的次数。
const float GLITCH_FLICKER_RATE = 15.0;
// 撕裂效果将黑洞划分为每组 GLITCH_BAND_TEXELS 行的条带。故障期间，比例为
// GLITCH_TEAR_SHARE 的条带会横向偏移，偏移量最大为 GLITCH_TEAR_TEXELS 个纹素。
const float GLITCH_BAND_TEXELS = 3.0;
const float GLITCH_TEAR_SHARE = 0.4;
const float GLITCH_TEAR_TEXELS = 5.0;

// Chris Wellons 编写的 lowbias32 整数哈希算法，采用公有领域授权。
uint hashUint(uint x) {
    x ^= x >> 16u;
    x *= 0x7feb352du;
    x ^= x >> 15u;
    x *= 0x846ca68bu;
    x ^= x >> 16u;
    return x;
}

float hashCell(vec2 cell, uint seed) {
    // 该偏移量用于在转换为无符号整数之前确保格点坐标为正值。
    uvec2 c = uvec2(ivec2(cell) + 32768);
    return float(hashUint(c.x ^ hashUint(c.y ^ seed)) >> 8u) * (1.0 / 16777216.0);
}

// 计算平面点所在的条带编号；平面坐标以阴影半径为单位。
float glitchBand(vec2 plane) {
    return floor((plane.y + PLANE_HALF_EXTENT.y) / (uGlitchTexel * GLITCH_BAND_TEXELS));
}

// 条带的横向偏移量，以精灵纹素为单位。故障期间，部分条带至少偏移一个纹素，以确保
// 撕裂效果可见；未发生撕裂时偏移量为 0。
float glitchTear(float band) {
    float frame = floor(uTime * GLITCH_FLICKER_RATE);
    float torn = step(hashCell(vec2(band, frame), 227u), GLITCH_TEAR_SHARE) * uGlitch;
    float side = hashCell(vec2(band, frame), 229u) < 0.5 ? -1.0 : 1.0;
    return torn * side * (1.0 + floor(hashCell(vec2(band, frame), 231u) * GLITCH_TEAR_TEXELS));
}

// 判断指定条带是否显示平滑黑洞。每个条带使用独立阈值，并随故障图案重新生成；平滑
// 黑洞的显示比例增大时，条带按随机顺序切换，并在切换期间于两种黑洞之间闪烁。
bool showsSmooth(float band) {
    return hashCell(vec2(band, floor(uTime * GLITCH_FLICKER_RATE)), 241u) < uSmoothShare;
}

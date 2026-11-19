// 真实风格黑洞，相对于像素风格精灵而言。black_hole.fsh 在天空中绘制该黑洞；
// black_hole_lens.fsh 在佩戴 Infinity Core 的玩家头部上方绘制。两个着色器通过本文件
// 共用黑洞实现，区别仅在于黑洞后方的背景。
//
// 外观参考电影 Interstellar 中的黑洞 Gargantua（渲染方式见 James、von Tunzelmann、
// Franklin 与 Thorne 的论文 Class. Quantum Grav. 32 (2015) 065001）：
//
// - 从吸积盘上方略微俯视时，近侧吸积盘形成横过阴影下半部并向两侧延伸的明亮带状结构；
//   远侧吸积盘受引力弯折，在阴影上方形成拱形结构，并在阴影下方形成较窄的拱形结构。
// - 不启用多普勒增亮和颜色偏移。电影为保持吸积盘轮廓清晰而省略这两种效果，因此吸积盘
//   两侧亮度和颜色相同。
// - 内缘气体呈白热状态，向外依次过渡为金色和橙色，并带有环绕黑洞延伸的细密丝状纹理。
// - 吸积盘具有一定厚度，外缘气体足以透出背景。从正面观察（如拱形结构）时，外缘气体较暗；
//   从侧面观察（如带状结构两端）时，光线穿过较长的气体路径，因此带状结构向两侧延伸较远，
//   而拱形结构仍靠近阴影。
// - 黑色阴影边界清晰，并由窄光子环勾勒。
// - 整体添加柔和辉光，以模拟电影镜头眩光产生的 bloom 效果。
//
// 包含本文件的着色器必须先声明 uTime，并在导入本文件前导入 black_hole_glitch.glsl
//（用于哈希函数）和 black_hole_geodesic.glsl。
//
// 部分 GL 驱动会拒绝包含非 ASCII 字节的 GLSL 源码（包括注释）。加载本资源时会先
// 剥离非 ASCII 字节，再将源码提交给驱动。

// ============================================================================
// 可调参数
//
// “阴影半径”指黑洞黑色圆盘的半径。吸积盘半径以黑洞质量 M 为单位；阴影半径约为 5.2 M。
// ============================================================================

// ---- 形状 ----
// 吸积盘内、外半径（M）。内缘位于最内稳定圆轨道，与 Gargantua 的设定一致，因此光子环与
// 阴影上方的光晕之间保留黑色间隔。本吸积盘比像素风格精灵更宽，因此故障切换两种风格时
// 边缘不会完全重合，从而形成额外的风格差异。
const float DISK_INNER_RADIUS = 6.0;
const float DISK_OUTER_RADIUS = 24.0;
// 吸积盘厚度（M）。光线以较小夹角穿过吸积盘时，气体路径长度为 DISK_THICKNESS 除以该夹角
// 的正弦值。增大该值会使侧视时的带状结构相对拱形结构更明亮、延伸更远。
const float DISK_THICKNESS = 0.35;
// 光线在内缘穿过多长的气体路径（M）后，气体会变为不透明。增大该值会提高吸积盘的透光程度。
const float DISK_OPACITY_SCALE = 0.2;
// 气体密度随半径按 (内半径 / r) 的该次幂衰减，因此外侧吸积盘从侧面观察时比从正面观察时
// 更明亮。增大该值会减少正面可见的吸积盘区域。
const float DENSITY_FALLOFF = 1.0;
// 光线与吸积盘相交时采用的最小夹角，以该角的正弦值表示。该值限制恰好侧视吸积盘时光线
// 在气体中的路径长度。
const float MIN_INCIDENCE = 0.03;
// 吸积盘的明亮主体在这两个半径（M）之间结束；其外仅有亮度为主体 WISP_LEVEL 的微弱气体
// 延伸至外半径。从侧面观察时，这些气体构成阴影两侧延伸较远的纤细结构；阴影上方由主体
// 形成的光晕则保持清晰边缘。
const float BODY_FADE_START = 10.0;
const float BODY_FADE_END = 16.0;
const float WISP_LEVEL = 0.3;
// 纤细气体开始淡出的半径占外半径的比例。
const float OUTER_EDGE_START = 0.5;
// 亮度从内缘开始上升的速率。增大该值会使亮度变化边界更锐利。
const float INNER_RISE_SPEED = 3.0;

// ---- 亮度与颜色 ----
// 整体曝光值；色调映射前，所有光照均乘以该值。
const float EXPOSURE = 1.0;
// 吸积盘亮度及其随半径向外衰减的幂指数。增大幂指数会使光照集中于内侧吸积盘，并使阴影
// 上下方的拱形结构更靠近阴影。
const float DISK_BRIGHTNESS = 4.5;
const float DISK_BRIGHTNESS_POWER = 2.2;
// 气体不透明后，吸积盘在内半径和外半径处对背景光线的最大遮挡比例。
const float DISK_OPACITY_INNER = 1.0;
const float DISK_OPACITY_OUTER = 0.95;
// 吸积盘颜色由“热度”控制，热度随半径按 (内半径 / r) 的 HEAT_POWER 次幂向外衰减，并从
// 内缘至外缘经过四种颜色。色调映射会减弱高亮区域的颜色，因此内侧气体会接近白色，只有外侧
// 颜色需要较强的暖色调。
const float HEAT_POWER = 1.0;
const vec3 WHITE_HOT_COLOR = vec3(1.00, 0.95, 0.86);
const vec3 GOLD_COLOR = vec3(1.00, 0.74, 0.42);
const vec3 AMBER_COLOR = vec3(1.00, 0.50, 0.20);
const vec3 EMBER_COLOR = vec3(0.75, 0.26, 0.08);

// ---- 丝状纹理 ----
// 气体纹理由环状单元组成：每单位对数半径包含 *_RADIAL_CELLS 个径向单元，每个单元沿轨道
// 方向在 *_CELLS 个格点范围内平滑变化，因此纹理长度远大于宽度。纹理叠加粗、中、细三个层次。
// 每个环按自身半径对应的轨道速度旋转；内缘每隔 INNER_TURN_SECONDS 秒旋转一周，外侧转速
// 较低，符合开普勒关系，因此各环之间产生相对位移。uTime 每经过 10000 秒回绕；每个环在此
// 期间旋转整数圈，以避免回绕时出现跳变。
const float COARSE_STREAK_CELLS = 4.0;
const float COARSE_STREAK_RADIAL_CELLS = 10.0;
const float STREAK_CELLS = 6.0;
const float STREAK_RADIAL_CELLS = 24.0;
const float FINE_STREAK_CELLS = 11.0;
const float FINE_STREAK_RADIAL_CELLS = 55.0;
const float INNER_TURN_SECONDS = 12.0;
// 各层纹理在内缘和外缘的对比度。内侧气体的纹理足够细密，可在吸积盘正面显示丝状结构；向外
// 纹理间距增大，至边缘时缝隙变暗，使两侧延伸结构逐渐变细。
const float COARSE_STREAK_CONTRAST_INNER = 0.2;
const float COARSE_STREAK_CONTRAST_OUTER = 0.3;
const float STREAK_CONTRAST_INNER = 0.45;
const float STREAK_CONTRAST_OUTER = 0.55;
const float FINE_STREAK_CONTRAST_INNER = 0.3;
const float FINE_STREAK_CONTRAST_OUTER = 0.4;
// 吸积盘在内缘和外缘保留于丝状纹理之间的气体比例。
const float GAP_OPACITY_INNER = 0.6;
const float GAP_OPACITY_OUTER = 0.1;
// 纹理单元接近像素尺寸时，将丝状纹理渐变至其平均值，以避免远处黑洞产生闪烁。这两个参数分别
// 表示渐变起点和终点，单位为每像素包含的纹理单元数。
const float NOISE_FADE_START = 0.4;
const float NOISE_FADE_END = 0.9;

// ---- 光子环 ----
// 光子环的亮度、位置（阴影半径）和最小宽度（阴影半径）。不单独绘制高阶子环，因为它们与主环
// 的距离仅为千分之几个阴影半径，在可用分辨率下远小于一个像素。
const float RING_BRIGHTNESS = 1.6;
const float RING_RADIUS = 1.012;
const float RING_WIDTH = 0.012;
const vec3 RING_COLOR = vec3(1.00, 0.90, 0.75);

// ---- 辉光（替代 bloom 后处理通道）----
const vec3 GLOW_COLOR = vec3(1.00, 0.72, 0.45);
// 阴影周围明亮边缘的亮度和衰减参数，以及范围更宽的第二层辉光及其亮度和衰减参数。该效果
// 保持较弱，以便光子环与拱形结构之间的间隔维持暗色。
const float HALO_BRIGHTNESS = 0.05;
const float HALO_FALLOFF = 12.0;
const float HALO_WIDE_BRIGHTNESS = 0.03;
const float HALO_WIDE_FALLOFF = 1.2;
// 阴影边缘内侧溢光的亮度和衰减参数。增大该值会降低阴影边缘的黑度。
const float SHADOW_SPILL_BRIGHTNESS = 0.03;
const float SHADOW_SPILL_FALLOFF = 24.0;
// 宽范围弱雾光的亮度和衰减参数。
const float HAZE_BRIGHTNESS = 0.02;
const float HAZE_FALLOFF = 0.6;
// 吸积盘上方辉光的亮度、横向半长度，以及侧视时的半高度（阴影半径）。半高度会随吸积盘张开
// 程度增加，增量为吸积盘自身高度乘以 DISK_GLOW_OPENING。侧视时，该辉光沿带状结构形成细长光带。
const float DISK_GLOW_BRIGHTNESS = 0.35;
const float DISK_GLOW_LENGTH = 4.0;
const float DISK_GLOW_HEIGHT = 0.08;
const float DISK_GLOW_OPENING = 0.8;
// 阴影未被吸积盘覆盖的部分所保留的雾光和盘面辉光比例。减小该值会使阴影更暗。
const float GLOW_IN_SHADOW = 0.15;

// ============================================================================
// 固定参数，通常无需调整
// ============================================================================

// uTime 在经过该秒数后回绕。
const float TIME_WRAP_SECONDS = 10000.0;

// p 的极坐标（角度、半径对数）的逐像素变化量。
vec2 polarFootprint(vec2 p) {
    vec2 px = dFdx(p);
    vec2 py = dFdy(p);
    float inverseLengthSquared = 1.0 / max(dot(p, p), 1.0e-4);
    float angle = abs(p.x * px.y - p.y * px.x) + abs(p.x * py.y - p.y * py.x);
    float logRadius = abs(dot(p, px)) + abs(dot(p, py));
    return vec2(angle, logRadius) * inverseLengthSquared;
}

// 计算单个气体环：沿轨道方向使用值噪声，每周包含 cells 个单元，并按该环的轨道速度旋转。
// row 表示该环相对于内缘的编号，rows 表示每单位对数半径所包含的环数；turns 表示以周为单位的极角。
float ringNoise(float turns, float row, float cells, float rows, uint seed) {
    float radius = DISK_INNER_RADIUS * exp(row / rows);
    float period = INNER_TURN_SECONDS * pow(radius / DISK_INNER_RADIUS, 1.5);
    float turnsPerWrap = max(floor(TIME_WRAP_SECONDS / period + 0.5), 1.0);
    // 气体沿极角减小的方向旋转，与像素风格精灵一致，因此噪声采样位置沿轨道方向前移。
    float x = (turns + fract(uTime * (turnsPerWrap / TIME_WRAP_SECONDS))) * cells;
    float cell = floor(x);
    float f = x - cell;
    float x0 = cell - cells * floor(cell / cells);
    float x1 = x0 + 1.0 > cells - 0.5 ? 0.0 : x0 + 1.0;
    return mix(hashCell(vec2(x0, row), seed), hashCell(vec2(x1, row), seed), f * f * (3.0 - 2.0 * f));
}

// 丝状纹理由多个气体环组成，并沿半径方向平滑混合；取值范围为 [0, 1]，平均值为 0.5。
// 每个环整体旋转，因此吸积盘的差速旋转不会使纹理持续缠绕。polar 表示（角度、半径对数）
// 的逐像素变化量。
float filaments(float turns, float logRadius, float cells, float rows, vec2 polar, uint seed) {
    vec2 cellsPerPixel = vec2(polar.x * cells / TAU, polar.y * rows);
    float detail = 1.0 - smoothstep(NOISE_FADE_START, NOISE_FADE_END, max(cellsPerPixel.x, cellsPerPixel.y));
    if (detail <= 0.0) {
        return 0.5;
    }
    float y = logRadius * rows;
    float row = floor(y);
    float f = y - row;
    float value = mix(ringNoise(turns, row, cells, rows, seed), ringNoise(turns, row + 1.0, cells, rows, seed),
            f * f * (3.0 - 2.0 * f));
    return mix(0.5, value, detail);
}

vec3 diskPalette(float heat) {
    vec3 color = mix(EMBER_COLOR, AMBER_COLOR, smoothstep(0.2, 0.4, heat));
    color = mix(color, GOLD_COLOR, smoothstep(0.35, 0.65, heat));
    return mix(color, WHITE_HOT_COLOR, smoothstep(0.6, 1.0, heat));
}

// 单个吸积盘成像的光照（rgb）和不透明度（a）。p 是 viewedPoint 给出的盘面坐标；polar 是
//（角度、半径对数）的逐像素变化量；radiusPixel 是半径的逐像素变化量；incidence 是光线与
// 吸积盘相交角的正弦值。
vec4 diskImage(vec2 p, vec2 polar, float radiusPixel, float incidence) {
    float r = length(p);
    // 落在吸积盘范围内的像素覆盖比例。该值也可减少光子环附近、宽度小于一个像素的成像产生的混叠。
    float span = max(radiusPixel, 1.0e-3);
    float coverage = clamp((min(r + 0.5 * span, DISK_OUTER_RADIUS)
            - max(r - 0.5 * span, DISK_INNER_RADIUS)) / span, 0.0, 1.0);
    if (coverage <= 0.0) {
        return vec4(0.0);
    }

    float closeness = DISK_INNER_RADIUS / r;
    // 纹理按吸积盘自身的角度坐标排列，因此相机绕黑洞移动时，丝状纹理仍固定在盘面上。
    float turns = atan(p.y, p.x) / TAU + viewedFrameTurns();
    float logRadius = log(r / DISK_INNER_RADIUS);
    float outward = smoothstep(0.0, 1.0, logRadius / log(BODY_FADE_END / DISK_INNER_RADIUS));
    float coarse = filaments(turns, logRadius, COARSE_STREAK_CELLS, COARSE_STREAK_RADIAL_CELLS, polar, 59u);
    float medium = filaments(turns, logRadius, STREAK_CELLS, STREAK_RADIAL_CELLS, polar, 71u);
    float fine = filaments(turns, logRadius, FINE_STREAK_CELLS, FINE_STREAK_RADIAL_CELLS, polar, 83u);
    float density = max(1.0
            + 2.0 * mix(COARSE_STREAK_CONTRAST_INNER, COARSE_STREAK_CONTRAST_OUTER, outward) * (coarse - 0.5)
            + 2.0 * mix(STREAK_CONTRAST_INNER, STREAK_CONTRAST_OUTER, outward) * (medium - 0.5)
            + 2.0 * mix(FINE_STREAK_CONTRAST_INNER, FINE_STREAK_CONTRAST_OUTER, outward) * (fine - 0.5), 0.0);

    // 亮度从内缘快速上升至吸积盘明亮主体，随后由纤细结构向外缘渐隐。内缘以内的上升项固定为零：
    // 像素覆盖计算会保留略微落在内缘以内的采样点；若该处上升项为负，不透明度和光照也会变为负值，
    // 从而在光子环中产生缺口。
    float innerRise = 1.0 - exp(-max(r - DISK_INNER_RADIUS, 0.0) * INNER_RISE_SPEED);
    float wisps = mix(WISP_LEVEL, 1.0, 1.0 - smoothstep(BODY_FADE_START, BODY_FADE_END, r));
    float outerFade = 1.0 - smoothstep(DISK_OUTER_RADIUS * OUTER_EDGE_START, DISK_OUTER_RADIUS, r);
    float body = coverage * wisps * outerFade;

    // 光线穿过吸积盘时经过的气体量；纤细结构比主体更薄。该值以气体达到不透明状态时为 1。
    // 较薄的气体按比例发光并允许背景透过；超过 1 后，气体变为不透明，仅显示其表面。
    float path = DISK_THICKNESS / max(incidence, MIN_INCIDENCE) * mix(1.0, 0.5, 1.0 - wisps);
    float gaps = clamp(mix(mix(GAP_OPACITY_INNER, GAP_OPACITY_OUTER, outward), 1.0, density), 0.0, 1.0);
    float opticalDepth = path * gaps * pow(closeness, DENSITY_FALLOFF) / DISK_OPACITY_SCALE;
    float filled = 1.0 - exp(-opticalDepth);

    float brightness = DISK_BRIGHTNESS * pow(closeness, DISK_BRIGHTNESS_POWER) * density * innerRise;
    vec3 light = diskPalette(pow(closeness, HEAT_POWER)) * brightness * body * filled;
    float opacity = body * mix(DISK_OPACITY_OUTER, DISK_OPACITY_INNER, sqrt(closeness)) * filled
            * (0.3 + 0.7 * innerRise);
    return vec4(light, opacity);
}

// 计算黑洞在图像平面点 q（单位为阴影半径）处的真实风格成像，观察相机由
// black_hole_geodesic.glsl 提供。rgb 表示色调映射前的发光量，a 表示对后方天空或场景的
// 遮挡比例。调用方按 1 - a 的权重合成背景。仅当 traced 为 true 时追踪光线，以便调用方跳过
// 不会显示的片元。
vec4 realBlackHole(vec2 q, bool traced) {
    CameraRay camera = cameraRay(q);
    float impact = camera.impact;
    float pixel = max(fwidth(impact), 1.0e-4);

    // 将光线追踪至与吸积盘平面的前两次交点，详见 diskCrossings。只有冲量参数小于吸积盘外缘
    // 限定的上限时，光线才可能与盘面相交。
    float reach = DISK_OUTER_RADIUS / (CRITICAL_IMPACT * sqrt(1.0 - 2.0 / DISK_OUTER_RADIUS));
    vec2 primaryPoint = vec2(1.0e4);
    vec2 secondaryPoint = vec2(1.0e4);
    float primaryIncidence = 1.0;
    float secondaryIncidence = 1.0;
    if (traced && impact < reach) {
        vec3 primary;
        vec3 secondary;
        diskCrossings(camera, primary, secondary, primaryIncidence, secondaryIncidence);
        primaryPoint = viewedPoint(primary);
        secondaryPoint = viewedPoint(secondary);
    }

    // 导数必须在条件分支之外计算。
    vec2 primaryPolar = polarFootprint(primaryPoint);
    vec2 secondaryPolar = polarFootprint(secondaryPoint);
    float primaryRadiusPixel = fwidth(length(primaryPoint));
    float secondaryRadiusPixel = fwidth(length(secondaryPoint));

    vec4 primary = diskImage(primaryPoint, primaryPolar, primaryRadiusPixel, primaryIncidence);
    vec4 secondary = diskImage(secondaryPoint, secondaryPolar, secondaryRadiusPixel, secondaryIncidence);
    float shadow = 1.0 - smoothstep(1.0 - pixel, 1.0 + pixel, impact);

    // 光子环由绕黑洞传播的光线汇聚形成，位置略在阴影外侧。其宽度不低于约一个像素；宽度较小时
    // 保持能量恒定，使光子环逐渐变暗而非产生混叠。
    float ringWidth = max(RING_WIDTH, pixel * 0.8);
    float ringOffset = (impact - RING_RADIUS) / ringWidth;
    vec3 ringLight = RING_COLOR * RING_BRIGHTNESS * exp(-ringOffset * ringOffset) * (RING_WIDTH / ringWidth);

    // 多个成像按由前至后的顺序合成：第一幅在不透明区域遮挡后方内容，第二幅仅显示第一幅未遮挡的
    // 部分。光子环位于最远处，因此亮度会同时受到前两幅成像的衰减。
    vec3 emission = primary.rgb + (1.0 - primary.a) * (secondary.rgb + (1.0 - secondary.a) * ringLight);
    float occlusion = 1.0 - (1.0 - primary.a) * (1.0 - secondary.a) * (1.0 - shadow);

    // 使用解析辉光替代 bloom 后处理通道，包括阴影周围的明亮边缘、宽范围雾光，以及随吸积盘张开
    // 而扩大的盘面辉光。辉光仅少量进入未被吸积盘覆盖的阴影区域，以保持该区域为黑色。
    float edge = abs(impact - 1.0);
    float halo = HALO_BRIGHTNESS * exp(-edge * HALO_FALLOFF) + HALO_WIDE_BRIGHTNESS * exp(-edge * HALO_WIDE_FALLOFF);
    float spill = SHADOW_SPILL_BRIGHTNESS * exp(-max(1.0 - impact, 0.0) * SHADOW_SPILL_FALLOFF);
    float haze = HAZE_BRIGHTNESS * exp(-max(impact - 1.0, 0.0) * HAZE_FALLOFF);
    float opening = abs(uViewDirection.y) * DISK_OUTER_RADIUS / CRITICAL_IMPACT;
    vec2 wing = q / vec2(DISK_GLOW_LENGTH, DISK_GLOW_HEIGHT + DISK_GLOW_OPENING * opening);
    float diskGlow = DISK_GLOW_BRIGHTNESS * exp(-dot(wing, wing));
    float bareShadow = shadow * (1.0 - primary.a);
    emission += GLOW_COLOR * (mix(halo, spill, bareShadow) + (haze + diskGlow) * mix(1.0, GLOW_IN_SHADOW, bareShadow));
    return vec4(emission, occlusion);
}

// 对黑洞光照执行色调映射，因为原版帧缓冲无法存储 HDR 值；随后添加抖动，以减轻柔和辉光中的
// 8 位色阶条带。
vec3 toneMap(vec3 emission) {
    vec3 color = 1.0 - exp(-emission * EXPOSURE);
    uvec2 fragCell = uvec2(gl_FragCoord.xy);
    float dither = float(hashUint(fragCell.x ^ hashUint(fragCell.y)) >> 8u) * (1.0 / 16777216.0) - 0.5;
    return max(color + dither / 255.0, 0.0);
}

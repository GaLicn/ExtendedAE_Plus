#version 150

// 用于夜空的像素风格黑洞精灵。
//
// 本通道以每个像素画纹素对应一个纹素的分辨率，将黑洞绘制到小型离屏目标。
// black_hole_pixelated_finish 随后清理杂散纹素并添加描边和辉光；
// black_hole_pixelated_composite 再以最近邻采样将结果合成到天空并添加故障效果。
// 后两个通道需要读取相邻纹素，本通道不执行这些操作。像素网格绑定于精灵而非屏幕，
// 因此相机转动或黑洞横越天空时，像素位置保持稳定。
//
// 几何与光线积分方式沿用 black_hole.fsh，着色方式则不同：每个纹素从有限的手工调色板
// 中取一种纯色，而非使用色调映射渐变。亮度划分为具有硬边界的色阶。吸积盘朝向观察者的
// 一侧因蓝移使用冷色阶，背离观察者的一侧使用暖色阶，两侧之间使用紫色色阶。此处不添加
// 抖动，每种颜色均按完整色块绘制。
//
// vPlane 表示像平面上的位置，单位为阴影半径，因此从远处观察时阴影边界为单位圆。本通道
// 的每个片元恰为一个纹素，因此 vPlane 对应纹素中心，屏幕空间导数以纹素为单位。光线从
// black_hole_geodesic.glsl 所描述的相机位置发出：天空黑洞使用固定角度下的无穷远相机；
// 头顶黑洞使用近距离的世界相机，其吸积盘与地面平行。
//
// 输出采用预乘 alpha：rgb 表示发出的光，alpha 表示对后方天空的遮挡比例；作为精灵时
// alpha 取 0 或 1。
//
// 部分 GL 驱动会拒绝包含非 ASCII 字节的 GLSL 源码（包括注释）。加载本资源时会先
// 剥离非 ASCII 字节，再将源码提交给驱动。

in vec2 vPlane;

uniform float uTime;
// 取 1 时绘制本通道为天空生成的透镜星场。头顶黑洞合成在真实场景上方，场景已包含恒星
// 和地形，因此传入 0，使场景背景保持可见。
uniform float uStarField;

out vec4 fragColor;

// ============================================================================
// 可调参数
//
// “阴影半径”指黑洞黑色圆盘的半径。吸积盘半径以黑洞质量 M 为单位，阴影半径约为 5.2 M。
// 每个阴影半径对应的纹素数由离屏目标尺寸和 BlackHoleSkyRenderer.PIXELS_PER_SHADOW_RADIUS
// 决定。
// ============================================================================

// ---- 形状 ----
// 吸积盘内、外半径（M）。内半径越小，吸积盘越靠近黑洞；外半径决定吸积盘向两侧延伸的范围。
const float DISK_INNER_RADIUS = 4.5;
const float DISK_OUTER_RADIUS = 18.0;
// 吸积盘从外半径的该比例位置开始淡出。
const float OUTER_EDGE_START = 0.7;
// 远侧吸积盘亮度比例；远侧位于黑洞后方，其光线受引力弯曲至阴影上方和下方。该比例小于 1
// 时，两个弧形结构比近侧更窄且更暗。衰减仅取决于盘面位置，因此会在整个远侧范围内平滑
// 过渡。black_hole.fsh 只降低阴影正上方和正下方的亮度；在像素分辨率下，这会整条删除
// 对应色带并形成缺口。
const float FAR_SIDE_BRIGHTNESS = 0.8;
// 远侧吸积盘第二幅成像（即远侧另一面）的亮度比例。该成像经引力弯曲至阴影下方，可从阴影
// 描边的下部透出。亮度较高时会从下方填充黑色阴影，使其不再呈现孔洞效果；降低亮度可使其
// 在光子环下方仅形成窄边。
const float UNDERSIDE_BRIGHTNESS = 0.45;

// ---- 亮度 ----
// 吸积盘亮度及其随半径向外衰减的幂指数；幂指数越大，内环越亮、外环越暗。
const float DISK_BRIGHTNESS = 5.0;
const float DISK_BRIGHTNESS_POWER = 1.0;
// 多普勒增亮。圆轨道气体的速度为 beta = sqrt(M / (r - 2M))，因此朝向观察者运动的一侧
// 变亮，背离观察者的一侧变暗。BEAMING_POWER 是多普勒因子的指数：相对论下总辐射强度的
// 正确指数为 4，取 2 可使两侧亮度保留在色带范围内；BEAMING_STRENGTH 用于进一步减弱该效果。
const float BEAMING_POWER = 2.0;
const float BEAMING_STRENGTH = 0.45;
// 同时影响吸积盘轮廓的多普勒增亮比例。其余部分仅改变气体亮度和颜色。取 1 时朝向观察者的
// 一侧会比背离观察者的一侧延伸得更远；取 0 时两侧范围相同。
const float SHAPE_BEAMING = 0.35;
// “热度”向外按 HEAT_POWER 次幂衰减。外缘气体亮度最多降低 HEAT_DIM，从而使吸积盘保持为
// 明亮窄带及较暗边缘，而非实心色块。
const float HEAT_POWER = 0.85;
const float HEAT_DIM = 0.45;
// 亮度从内缘开始上升的速率。增大该值会使亮度变化边界更锐利。
const float INNER_RISE_SPEED = 2.5;

// ---- 色带 ----
// 经过色调映射后的亮度达到相应阈值时，纹素提升至调色板的下一色阶。低于 BAND_1 的气体
// 不绘制，因此吸积盘边缘保持硬边，可由后续通道添加描边。
const float BAND_1 = 0.28;
const float BAND_2 = 0.50;
const float BAND_3 = 0.66;
const float BAND_4 = 0.82;
const float BAND_5 = 0.94;

// ---- 调色板 ----
// 每组色阶由外缘深色过渡至接近白色的核心色。冷色阶绘制蓝移的朝向观察者侧，暖色阶绘制
// 背离观察者侧，紫色色阶绘制两者之间沿视线方向运动的气体。色阶由气体运动方向决定，取值
// 从 -1（直接远离观察者）至 1（直接朝向观察者）；方向值在 HUE_VIOLET_HALF_WIDTH 范围内
// 时使用紫色。因此沿吸积盘运动方向会依次经过冷色、紫色、暖色、紫色。此处不使用多普勒因子，
// 因为远侧气体横向运动时，较大区域的亮度会接近同一阈值。
//
// 紫色为主色，冷色与暖色为两侧的强调色，因此紫色色阶覆盖吸积盘的大部分区域。三组色阶
// 共用最深色 RAMP_DEEP，使吸积盘外缘颜色连续。
const vec3 RAMP_DEEP = vec3(0.40, 0.11, 0.66);
const vec3 WARM_2 = vec3(0.92, 0.14, 0.52);
const vec3 WARM_3 = vec3(1.00, 0.45, 0.50);
const vec3 WARM_4 = vec3(1.00, 0.80, 0.62);
const vec3 VIOLET_2 = vec3(0.64, 0.26, 0.96);
const vec3 VIOLET_3 = vec3(0.84, 0.52, 1.00);
const vec3 VIOLET_4 = vec3(0.95, 0.86, 1.00);
const vec3 COOL_2 = vec3(0.18, 0.52, 1.00);
const vec3 COOL_3 = vec3(0.30, 0.88, 1.00);
const vec3 COOL_4 = vec3(0.82, 1.00, 1.00);
const vec3 HOT_CORE = vec3(1.00, 1.00, 0.94);
const float HUE_VIOLET_HALF_WIDTH = 0.45;
// 光子环采用环绕阴影的单一颜色，以勾勒阴影边缘。
const vec3 RING_COLOR = vec3(0.92, 1.00, 1.00);
// 以单纹素绘制经透镜弯曲的恒星，并绘制最亮恒星闪烁时产生的星芒臂。
const vec3 STAR_COLOR = vec3(0.92, 0.96, 1.00);
const vec3 STAR_ARM_COLOR = vec3(0.45, 0.62, 1.00);

// ---- 气体纹理 ----
// 气体团块沿轨道方向包含 DISK_CELLS 个单元，每单位对数半径包含 DISK_RADIAL_CELLS 个单元，
// 并在两个方向平滑插值，因此纹理沿轨道方向的长度大于宽度。径向单元数较少，这是因为接近
// 侧视时吸积盘仅占少量纹素深度。SPIRAL_WINDING 表示每单位对数半径沿轨道方向偏移的单元数，
// 使气体团块排列成拖尾臂状结构。
const float DISK_CELLS = 17.0;
const float DISK_RADIAL_CELLS = 2.8;
const float SPIRAL_WINDING = 3.8;
// 控制气体团块使亮度增减的幅度。团块会移动色带边界，使吸积盘转动时色带发生起伏。增大增益
// 可增强转动表现，但会使色块更小、更密集。
const float DENSITY_BASE = 0.66;
const float DENSITY_GAIN = 0.34;
// 旋转。内缘气体每隔 INNER_TURN_SECONDS 秒旋转一周，角速度向外按 (inner radius / r) 的
// ROTATION_FALLOFF 次幂衰减，使吸积盘整体旋转、内侧快于外侧，并避免不同转速区域之间出现接缝。
const float INNER_TURN_SECONDS = 10.0;
const float ROTATION_FALLOFF = 1.0;
// 吸积盘内侧转速高于外侧，会使盘面纹理逐渐缠绕成更紧密的螺旋。为保持气体团块形状，纹理按
// FLOW_CYCLE_SECONDS 周期循环，并在纹理副本之间交接：接替副本的相位滞后半个周期，两个副本
// 通过交叉淡化衔接，使气体流动连续且不发生跳变。uTime 每经过 10000 秒回绕，因此
// FLOW_CYCLE_SECONDS 必须整除 10000，以避免回绕时纹理跳变。
const float FLOW_CYCLE_SECONDS = 8.0;
// 当纹理单元沿轨道方向接近一个纹素的尺寸时，将纹理渐变为其平均值，以避免透镜将吸积盘压缩成
// 阴影上下的细弧时产生单纹素断裂，由此使细弧保持为连续色带。
const float NOISE_FADE_START = 0.25;
const float NOISE_FADE_END = 0.5;

// ---- 透镜星场 ----
// 背景天空受到与盘面光线相同的偏折，因此靠近黑洞的恒星会被拉伸成弧线。每个纹素根据光线来源
// 方向采样天空；恒星仅点亮其所在天空区域映射到的纹素，因此未受透镜影响时占一个纹素，受拉伸时
// 形成多个纹素组成的弧线。仅绘制距离黑洞不超过 STAR_FIELD_RADIUS（阴影半径）的恒星；更远处
// 透镜效果过弱，由其后的原版恒星显示。恒星弯曲程度由 black_hole_geodesic.glsl 中的
// LENS_STRENGTH 控制。
const float STAR_DENSITY = 4.0;
const float STAR_THRESHOLD = 0.9;
const float STAR_FIELD_RADIUS = 2.6;
// 产生四臂像素星芒的恒星比例及单次闪烁周期。每颗恒星具有独立相位，每周期约五分之一时间处于
// 点亮状态。uTime 每经过 10000 秒回绕，因此 STAR_TWINKLE_SECONDS 必须整除 10000。
const float STAR_SPARKLE_SHARE = 0.35;
const float STAR_TWINKLE_SECONDS = 4.0;
// 纹素仍可覆盖并显示恒星的天空区域最大半径，单位为格点数。阴影边缘附近的透镜会将整片天空压缩
// 至少量纹素，否则这些纹素会连成一个白色团块。
const float STAR_MAX_PATCH = 0.6;

// ============================================================================
// 固定参数，通常无需调整
// ============================================================================

// 与真实风格黑洞共用的相机、光线积分和透镜参数。
#moj_import <extendedae_plus:black_hole_geodesic.glsl>

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

// 沿 x 方向周期重复、周期为 DISK_CELLS 个单元的值噪声。
float orbitNoise(vec2 p, uint seed) {
    vec2 cell = floor(p);
    vec2 f = p - cell;
    vec2 w = f * f * (3.0 - 2.0 * f);
    float x0 = cell.x - DISK_CELLS * floor(cell.x / DISK_CELLS);
    float x1 = x0 + 1.0 > DISK_CELLS - 0.5 ? 0.0 : x0 + 1.0;
    float a = hashCell(vec2(x0, cell.y), seed);
    float b = hashCell(vec2(x1, cell.y), seed);
    float c = hashCell(vec2(x0, cell.y + 1.0), seed);
    float d = hashCell(vec2(x1, cell.y + 1.0), seed);
    return mix(mix(a, b, w.x), mix(c, d, w.x), w.y);
}

// 计算单个盘面点处的气体值，范围为 [0, 1]，平均值为 0.5。turns 为以周为单位的极角，
// logRadius 为半径相对内半径的对数，r 为半径，cellsPerTexel 为每纹素对应的角向单元变化量。
//
// 两个纹理副本均按对应角速度持续旋转，并在其权重降为零时更新为具有新偏移量的副本，因此
// 更新过程不会产生可见跳变。
float flowingGas(float turns, float logRadius, float r, float cellsPerTexel) {
    float detail = 1.0 - smoothstep(NOISE_FADE_START, NOISE_FADE_END, cellsPerTexel);
    if (detail <= 0.0) {
        return 0.5;
    }
    // 吸积盘左侧朝向观察者运动，即沿极角减小方向运动，因此纹理采样位置沿轨道方向前移。
    float turnsPerSecond = pow(DISK_INNER_RADIUS / r, ROTATION_FALLOFF) / INNER_TURN_SECONDS;
    vec2 base = vec2(turns * DISK_CELLS - logRadius * SPIRAL_WINDING, logRadius * DISK_RADIAL_CELLS);
    float cycles = uTime / FLOW_CYCLE_SECONDS;
    float gas = 0.0;
    float weightSquared = 0.0;
    for (int copy = 0; copy < 2; copy++) {
        float phase = cycles + 0.5 * float(copy);
        float age = fract(phase);
        float weight = 1.0 - abs(2.0 * age - 1.0);
        // 每次纹理更新均根据更新计数生成新的角向偏移量。
        float renewal = hashCell(vec2(floor(phase), float(copy)), 59u) * DISK_CELLS;
        vec2 coord = base + vec2(turnsPerSecond * age * FLOW_CYCLE_SECONDS * DISK_CELLS + renewal, 0.0);
        gas += weight * (orbitNoise(coord, 23u + uint(copy) * 18u) - 0.5);
        weightSquared += weight * weight;
    }
    // 对两个噪声值取平均会降低对比度；按权重总和重新缩放可使交接期间的对比度保持稳定。
    return 0.5 + detail * gas / sqrt(max(weightSquared, 1.0e-4));
}

// 计算 p 的极角逐像素变化量。
float angleFootprint(vec2 p) {
    vec2 px = dFdx(p);
    vec2 py = dFdy(p);
    return (abs(p.x * px.y - p.y * px.x) + abs(p.x * py.y - p.y * py.x)) / max(dot(p, p), 1.0e-4);
}

// 判断以 p 为中心、半尺寸为 halfSpan（阴影半径）的天空区域是否包含恒星：包含恒星的纹素取 1，
// 闪烁恒星的星芒臂纹素取 0.5，其他情况取 0。每个占用的格点通过哈希偏移放置一颗恒星。
float starAt(vec2 p, vec2 halfSpan) {
    if (2.0 * max(halfSpan.x, halfSpan.y) * STAR_DENSITY > STAR_MAX_PATCH) {
        return 0.0;
    }
    vec2 home = floor(p * STAR_DENSITY);
    float lit = 0.0;
    // 该采样区域可能延伸至相邻格点。
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            vec2 cell = home + vec2(float(i), float(j));
            if (hashCell(cell, 101u) < STAR_THRESHOLD) {
                continue;
            }
            vec2 star = (cell + vec2(hashCell(cell, 103u), hashCell(cell, 107u))) / STAR_DENSITY;
            vec2 d = abs(star - p);
            lit = max(lit, step(d.x, halfSpan.x) * step(d.y, halfSpan.y));
            // 星芒臂由恒星本体相邻的四个纹素组成，仅在闪烁点亮时绘制。只有透镜未将恒星拉伸时
            // 绘制星芒臂，因为被拉伸的恒星已经形成弧线。
            float phase = fract(uTime / STAR_TWINKLE_SECONDS + hashCell(cell, 109u));
            if (hashCell(cell, 113u) < STAR_SPARKLE_SHARE && phase < 0.2) {
                vec2 arm = abs(d - 2.0 * halfSpan);
                float horizontal = step(arm.x, halfSpan.x) * step(d.y, halfSpan.y);
                float vertical = step(d.x, halfSpan.x) * step(arm.y, halfSpan.y);
                lit = max(lit, 0.5 * max(horizontal, vertical));
            }
        }
    }
    return lit;
}

// 计算单幅吸积盘成像的亮度（经色调映射后，x）、沿视线方向的运动方向（y，-1 表示远离观察者，
// 1 表示朝向观察者），以及纹素是否位于吸积盘上（z）。p 为 viewedPoint 给出的盘面坐标；
// angleTexel 和 radiusTexel 分别为极角与半径的逐纹素变化量；gain 为该成像的亮度比例。
vec3 diskSample(vec2 p, float angleTexel, float radiusTexel, float gain) {
    float r = length(p);
    // 仅使用纹素中心判定纹素是否位于吸积盘内，以形成精灵式硬边。当成像被压缩至不足一个纹素宽时，
    // span 会将判定范围扩展至最多半个纹素，使细弧保持连续，避免断裂。
    float slack = 0.5 * min(radiusTexel, DISK_INNER_RADIUS);
    if (r + slack < DISK_INNER_RADIUS || r - slack > DISK_OUTER_RADIUS) {
        return vec3(0.0);
    }
    r = clamp(r, DISK_INNER_RADIUS, DISK_OUTER_RADIUS);

    float closeness = DISK_INNER_RADIUS / r;
    // 纹理按照吸积盘本身的角度坐标排列，而非图像坐标，因此相机绕黑洞移动时，气体纹理仍固定在盘面上。
    float gas = flowingGas(atan(p.y, p.x) / TAU + viewedFrameTurns(), log(r / DISK_INNER_RADIUS), r,
            angleTexel * DISK_CELLS / TAU);
    float density = DENSITY_BASE + DENSITY_GAIN * (gas - 0.5) * 2.0;

    // 对轨道气体应用多普勒增亮：朝向观察者运动的一侧（此处为左侧）变亮，背离观察者的一侧变暗。
    float motion = sideView();
    float approach = -p.x / r * sqrt(closeness) * motion;
    float beta = clamp(sqrt(1.0 / max(r - 2.0, 0.05)), 0.0, 0.95);
    float gamma = 1.0 / sqrt(1.0 - beta * beta);
    float doppler = 1.0 / (gamma * (1.0 - beta * approach));
    float beaming = mix(1.0, pow(doppler, BEAMING_POWER), BEAMING_STRENGTH);

    float heat = pow(closeness, HEAT_POWER);
    float innerRise = 1.0 - exp(-(r - DISK_INNER_RADIUS) * INNER_RISE_SPEED);
    float outerFade = 1.0 - smoothstep(DISK_OUTER_RADIUS * OUTER_EDGE_START, DISK_OUTER_RADIUS, r);
    float farSide = smoothstep(-0.2, 0.6, -p.y / max(length(p), 1.0e-4)) * motion;
    float radiance = DISK_BRIGHTNESS * pow(closeness, DISK_BRIGHTNESS_POWER) * density
            * mix(HEAT_DIM, 1.0, smoothstep(0.0, 1.0, heat)) * innerRise * outerFade
            * mix(1.0, FAR_SIDE_BRIGHTNESS, farSide) * gain;
    // 吸积盘轮廓仅应用部分多普勒增亮，使朝向观察者的一侧略微扩大而非显著延伸；轮廓内的色带则应用
    // 完整增亮效果，且亮度不低于第一色阶。
    float outline = 1.0 - exp(-radiance * mix(1.0, beaming, SHAPE_BEAMING));
    if (outline < BAND_1) {
        return vec3(0.0);
    }
    return vec3(max(1.0 - exp(-radiance * beaming), BAND_1), -p.x / max(length(p), 1.0e-4) * motion, 1.0);
}

// 单个调色板色阶：ramp 为 0 时使用暖色，1 时使用紫色，2 时使用冷色；step 为 1 时使用三种色阶
// 共用的最深色，为 4 时使用最亮色。
vec3 rampColor(int ramp, int step) {
    if (step <= 1) {
        return RAMP_DEEP;
    }
    if (ramp == 0) {
        return step >= 4 ? WARM_4 : (step == 3 ? WARM_3 : WARM_2);
    }
    if (ramp == 1) {
        return step >= 4 ? VIOLET_4 : (step == 3 ? VIOLET_3 : VIOLET_2);
    }
    return step >= 4 ? COOL_4 : (step == 3 ? COOL_3 : COOL_2);
}

// 为吸积盘纹素选择一种纯色：根据亮度确定色阶，根据运动方向确定颜色组。纹素为空时返回 alpha 0。
vec4 diskColor(vec3 disk) {
    float level = disk.x;
    if (disk.z <= 0.0) {
        return vec4(0.0);
    }
    if (level >= BAND_5) {
        return vec4(HOT_CORE, 1.0);
    }
    int ramp = disk.y < -HUE_VIOLET_HALF_WIDTH ? 0 : (disk.y > HUE_VIOLET_HALF_WIDTH ? 2 : 1);
    int step = level >= BAND_4 ? 4 : (level >= BAND_3 ? 3 : (level >= BAND_2 ? 2 : 1));
    return vec4(rampColor(ramp, step), 1.0);
}

void main() {
    vec2 q = vPlane;
    float rho = length(q);
    float texel = max(fwidth(q.x), 1.0e-4);
    vec2 direction = q / max(rho, 1.0e-4);

    // 将光线追踪至与吸积盘平面的前两次交点，详见 diskCrossings。只有经过吸积盘外缘以内区域的光线
    // 才会与盘面相交，因此其冲量参数存在上限。
    CameraRay camera = cameraRay(q);
    float reach = DISK_OUTER_RADIUS / (CRITICAL_IMPACT * sqrt(1.0 - 2.0 / DISK_OUTER_RADIUS));
    vec2 primaryPoint = vec2(1.0e4);
    vec2 secondaryPoint = vec2(1.0e4);
    if (camera.impact < reach) {
        vec3 primary;
        vec3 secondary;
        diskCrossings(camera, primary, secondary);
        primaryPoint = viewedPoint(primary);
        secondaryPoint = viewedPoint(secondary);
    }

    // 导数必须在条件分支之外计算。
    float primaryAngleTexel = angleFootprint(primaryPoint);
    float secondaryAngleTexel = angleFootprint(secondaryPoint);
    float primaryRadiusTexel = fwidth(length(primaryPoint));
    float secondaryRadiusTexel = fwidth(length(secondaryPoint));
    vec2 skyPoint = direction * (rho - LENS_STRENGTH * deflectionAngle(rho));
    vec2 skyHalfSpan = 0.5 * fwidth(skyPoint);

    vec4 primary = diskColor(diskSample(primaryPoint, primaryAngleTexel, primaryRadiusTexel, 1.0));
    vec4 secondary = diskColor(diskSample(secondaryPoint, secondaryAngleTexel, secondaryRadiusTexel,
            UNDERSIDE_BRIGHTNESS));

    // 按由前至后的顺序合成各层；像素精灵纹素只保留一种颜色，因此最先覆盖的实色层优先。近侧吸积盘
    // 位于阴影前方，阴影遮挡其后方内容，随后为阴影下方的弧形结构、贴近阴影边缘的光子环，以及
    // 弯曲后的恒星。
    vec4 color = vec4(0.0);
    if (primary.a > 0.0) {
        color = primary;
    } else if (camera.impact < 1.0) {
        color = vec4(0.0, 0.0, 0.0, 1.0);
    } else if (secondary.a > 0.0) {
        color = secondary;
    } else if (camera.impact < 1.0 + texel) {
        // 绘制阴影外缘的纹素光环。
        color = vec4(RING_COLOR, 1.0);
    } else if (uStarField > 0.0 && rho < STAR_FIELD_RADIUS) {
        // 恒星只增加光照、不遮挡天空，因此透镜恒星后方的原版星场仍可见。
        float star = starAt(skyPoint, skyHalfSpan);
        color = vec4(star >= 1.0 ? STAR_COLOR : STAR_ARM_COLOR * step(0.25, star), 0.0);
    }

    if (color.a <= 0.0 && max(color.r, max(color.g, color.b)) <= 0.0) {
        discard;
    }
    fragColor = color;
}

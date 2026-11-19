// 本文件提供黑洞周边的光线追踪计算，供 black_hole.fsh、black_hole_lens.fsh
// 以及像素风格黑洞使用的 black_hole_pixelated.fsh 共用。
//
// 黑洞坐标系以质量 M 为长度单位，吸积盘位于 xz 平面。以下 uniform 用于确定
// 该坐标系中的相机位置，所有光线均从相机位置开始追踪。天空黑洞对应固定观测角度
// 下的无穷远相机；头顶黑洞是世界中的对象，其吸积盘与地面平行。观察方向改变时可见
// 吸积盘的不同侧面，盘面气体位置保持不变；从下方观察时画面上下翻转，近侧盘面因
// 透视关系显得比远侧更大。
//
// 图像点 q 以阴影半径为单位，位于经过黑洞中心并由相机 right、up 轴张成的像平面上。
// 从远处观察时，阴影边界在该平面上为单位圆。
//
// 部分 GL 驱动会拒绝包含非 ASCII 字节的 GLSL 源码（包括注释）。加载本资源时会先
// 剥离非 ASCII 字节，再将源码提交给驱动。

// 从黑洞指向相机的方向，以及二者间距（以 M 为单位）；距离为 0 表示相机位于无穷远处。
uniform vec3 uViewDirection;
uniform float uViewDistance;
// 相机的 right 轴和 up 轴。
uniform vec3 uViewRight;
uniform vec3 uViewUp;

// 光线绕黑洞半周所用的积分步数。步数越多，精度越高，计算开销也越大。
const int ORBIT_STEPS = 12;
// 背景绕黑洞偏移的角度，以光线偏折角的倍数表示。该参数控制空间弯曲的视觉强度；
// 值为 0 时背景保持平直。
const float LENS_STRENGTH = 2.5;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;
// 吸积盘距离以黑洞质量为单位，并采用 G = c = 1 的单位制。阴影边界对应的临界冲量
// 参数为 3 * sqrt(3)。
const float CRITICAL_IMPACT = 5.19615242;

// 光线在轨道平面内的状态：x 为 M / r，y 为 x 对光线绕黑洞转过角度的导数，满足
// x'' = 3x^2 - x。
vec2 orbitSlope(vec2 ray) {
    return vec2(ray.y, ray.x * (3.0 * ray.x - 1.0));
}

// 将光线沿黑洞轨道继续推进 angle 弧度。已穿过视界（x > 0.5）或已逃逸（x < 0）的
// 光线保持当前位置。
vec2 advanceOrbit(vec2 ray, float angle) {
    float h = angle / float(ORBIT_STEPS);
    for (int i = 0; i < ORBIT_STEPS; i++) {
        if (ray.x > 0.5 || ray.x < 0.0) {
            break;
        }
        vec2 k1 = orbitSlope(ray);
        vec2 k2 = orbitSlope(ray + 0.5 * h * k1);
        vec2 k3 = orbitSlope(ray + 0.5 * h * k2);
        vec2 k4 = orbitSlope(ray + h * k3);
        ray += h / 6.0 * (k1 + 2.0 * (k2 + k3) + k4);
    }
    return ray;
}

// 位于轨道平面内的相机光线。光线绕黑洞转过 phi 后，其方向为
// axis * cos(phi) + across * sin(phi)，半径由 ray 给出。phi = 0 时光线位于相机处。
// impact 为以阴影半径表示的冲量参数；小于 1 的光线会落入黑洞。
struct CameraRay {
    vec3 axis;
    vec3 across;
    vec2 ray;
    float impact;
};

CameraRay cameraRay(vec2 q) {
    vec3 offset = CRITICAL_IMPACT * (q.x * uViewRight + q.y * uViewUp);
    CameraRay camera;
    camera.axis = uViewDirection;
    if (uViewDistance <= 0.0) {
        // 相机位于无穷远处时，所有光线均与视线平行；光线相对中心的偏移量即为冲量参数。
        float b = length(offset);
        camera.across = offset / max(b, 1.0e-4);
        camera.ray = vec2(0.0, 1.0 / max(b, 0.01 * CRITICAL_IMPACT));
        camera.impact = b / CRITICAL_IMPACT;
        return camera;
    }
    // 相机靠近黑洞时，光线从相机出发并指向像平面上的对应点。静止观测者位于半径 r 处，
    // 光线与径向夹角为 alpha 时，其冲量参数为 r sin(alpha) / sqrt(1 - 2M / r)。
    vec3 heading = normalize(offset - uViewDirection * uViewDistance);
    vec3 sideways = heading - dot(heading, uViewDirection) * uViewDirection;
    float sinAlpha = length(sideways);
    float x = 1.0 / uViewDistance;
    float b = max(uViewDistance * sinAlpha / sqrt(1.0 - 2.0 * x), 0.01 * CRITICAL_IMPACT);
    float slope = sqrt(max(1.0 / (b * b) - x * x * (1.0 - 2.0 * x), 0.0));
    camera.across = sideways / max(sinAlpha, 1.0e-6);
    // 光线朝向黑洞时，会在绕行过程中逐渐接近黑洞。
    camera.ray = vec2(x, dot(heading, uViewDirection) < 0.0 ? slope : -slope);
    camera.impact = b / CRITICAL_IMPACT;
    return camera;
}

// 计算光线绕行 angle 后与吸积盘平面的交点。已落入黑洞的光线按视界半径处理，已远距离
// 逃逸的光线按远处位置处理，使半径在边界附近保持近似连续，并确保两类交点均位于盘面外。
vec3 crossingPoint(CameraRay camera, vec2 ray, float angle) {
    float radius = ray.x >= 0.5 ? 2.0 : (ray.x <= 0.0 ? 1.0e4 : 1.0 / ray.x);
    return radius * (cos(angle) * camera.axis + sin(angle) * camera.across);
}

// 计算光线绕行 angle 后与吸积盘平面的交角正弦值：垂直穿过平面时为 1，掠过平面时
// 接近 0。令 x = M / r，光线方向为 -x' e_r + x e_phi。交点处指向远离黑洞方向的
// e_r 位于盘面内，只有 e_phi 分量离开盘面。
float crossingIncidence(CameraRay camera, vec2 ray, float angle) {
    vec3 onwards = cos(angle) * camera.across - sin(angle) * camera.axis;
    return clamp(abs(ray.x * onwards.y) / max(length(ray), 1.0e-6), 0.0, 1.0);
}

// 追踪相机光线与吸积盘平面的前两次交点。第一次对应朝向相机的一面，包括阴影前方的
// 近侧盘面，以及经引力弯折后覆盖阴影上方的远侧盘面。第二次对应远侧盘面的另一面，
// 该部分弯折至阴影下方。第三阶及更高阶成像仅存在于冲量参数距离临界值约千分之一
// 以内，远小于一个像素，因此不进行追踪。两个 incidence 参数表示光线在对应交点处
// 与盘面的交角。
void diskCrossings(CameraRay camera, out vec3 primary, out vec3 secondary,
        out float primaryIncidence, out float secondaryIncidence) {
    // 首个交点满足 axis.y cos(phi) + across.y sin(phi) = 0，且 phi 位于 0 到 pi 之间。
    // axis.y 是相机相对盘面仰角的正弦值；渲染器保证该值不为 0。
    float axisHeight = camera.axis.y >= 0.0 ? max(camera.axis.y, 1.0e-4) : min(camera.axis.y, -1.0e-4);
    float firstCrossing = 0.5 * PI + atan(camera.across.y / axisHeight);
    vec2 ray = advanceOrbit(camera.ray, firstCrossing);
    primary = crossingPoint(camera, ray, firstCrossing);
    primaryIncidence = crossingIncidence(camera, ray, firstCrossing);
    ray = advanceOrbit(ray, PI);
    secondary = crossingPoint(camera, ray, firstCrossing + PI);
    secondaryIncidence = crossingIncidence(camera, ray, firstCrossing + PI);
}

// 供仅需要交点坐标的调用方使用的重载方法。
void diskCrossings(CameraRay camera, out vec3 primary, out vec3 secondary) {
    float primaryIncidence;
    float secondaryIncidence;
    diskCrossings(camera, primary, secondary, primaryIncidence, secondaryIncidence);
}

// 以盘面内的 (x, z) 表示从黑洞指向相机的水平方向。相机位于黑洞正上方或正下方时，
// 该方向未定义，此时使用相机的 right 轴确定横向方向。
vec2 towardsCamera() {
    vec2 level = uViewDirection.xz;
    float levelLength = length(level);
    if (levelLength > 1.0e-3) {
        return level / levelLength;
    }
    vec2 right = normalize(uViewRight.xz);
    return vec2(-right.y, right.x);
}

// 将盘面点转换为相机观察坐标：x 为视线横向坐标，正方向朝右；y 为朝向相机的坐标。
// 着色计算在该坐标系中进行，以保证朝向相机移动的一侧及弯折至阴影上方的远侧位于
// 预期位置。
vec2 viewedPoint(vec3 point) {
    vec2 towards = towardsCamera();
    return vec2(point.x * towards.y - point.z * towards.x, dot(point.xz, towards));
}

// 计算 viewedPoint 的 x 轴在黑洞坐标系中的极角，单位为周。将此角度加到观察点的
// 极角后，即得到该点在盘面坐标系中的角度，使气体纹理固定于盘面，不随相机移动。
float viewedFrameTurns() {
    vec2 towards = towardsCamera();
    return atan(-towards.x, towards.y) / TAU;
}

// 表示气体轨道运动沿视线方向的分量比例：从侧面观察时为 1，从盘面正上方或正下方
// 观察时降至 0，此时气体运动方向均与视线垂直。该值用于淡化接近侧与远离侧、近侧与
// 远侧之间的差异，因为正视盘面时这些区域不再具有视觉差别。
float sideView() {
    return smoothstep(0.0, 0.5, length(uViewDirection.xz));
}

// 计算冲量参数为 rho 的光线偏折角，其中 rho 以阴影半径为单位。该近似式拟合数值
// Schwarzschild 解。偏折角在接近阴影边界时按对数规律发散，使背景在光子环外侧弯折
// 成闭合环状结构。
float deflectionAngle(float rho) {
    float x = max(rho, 1.0 + 1.0e-4);
    return log(1.0 + 0.638 / (x - 1.0)) + 0.133 / x;
}

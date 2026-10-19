#version 150

// 全屏后处理通道。顶点只给出裁剪空间内的一个四边形，故模型视图与投影矩阵均为单位
// 矩阵；仍按标准管线相乘，使这两个 uniform 在程序中被真实引用，避免驱动将其优化掉后
// 在着色器加载日志里留下“找不到 uniform”的告警。
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

// 屏幕空间坐标，原点在左下角，取值 0 至 1。
out vec2 vScreenUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vScreenUv = Position.xy * 0.5 + 0.5;
}

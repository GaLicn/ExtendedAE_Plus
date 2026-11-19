#version 150

// 收尾通道使用覆盖整个目标的四边形，并按完整纹素处理精灵，因此仅需要顶点位置属性。

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}

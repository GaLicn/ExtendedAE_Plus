#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

// 天空切平面上的位置，单位为阴影半径。
out vec2 vPlane;

void main() {
    vPlane = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}

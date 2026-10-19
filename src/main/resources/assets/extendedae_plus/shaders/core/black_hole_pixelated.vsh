#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

// Position on the sky tangent plane, in units of the shadow radius.
out vec2 vPlane;

void main() {
    vPlane = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}

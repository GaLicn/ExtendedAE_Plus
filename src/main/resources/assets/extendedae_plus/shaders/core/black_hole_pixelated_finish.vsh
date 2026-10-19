#version 150

// Full-target quad for the finishing pass, which works on whole texels of the
// sprite and so needs nothing but the position.

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}

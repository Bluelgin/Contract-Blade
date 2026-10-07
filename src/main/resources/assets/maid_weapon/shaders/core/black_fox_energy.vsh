#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 texCoord;
out vec4 vertexColor;
out vec2 effectData;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
    // Per-quad tick/kind data instead of a mutable global clock uniform: concurrent Bosses remain independent.
    effectData = vec2(float(UV2.x) / 100.0, float(UV2.y));
}

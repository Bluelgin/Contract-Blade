#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord;
in vec4 vertexColor;
in vec2 effectData;
out vec4 fragColor;

void main() {
    float age = effectData.x;
    bool slash = effectData.y > 0.5;
    vec2 uv = texCoord;
    // Only small boundary motion. The committed attack direction and hit geometry do not wobble.
    float bend = sin(uv.y * 31.0 - age * 0.43) * sin(uv.y * 3.141593);
    uv.x += bend * (slash ? 0.0015 : 0.006);
    vec4 mask = texture(Sampler0, uv);
    float flow = slash
        ? pow(max(0.0, sin(uv.x * 11.0 + age * 0.63)), 8.0)
        : pow(max(0.0, sin(uv.y * 29.0 - age * 0.72)), 7.0);
    float pulse = 0.85 + 0.15 * sin(age * 0.28);
    vec3 violet = vec3(0.47, 0.13, 0.95);
    vec3 core = mix(vec3(0.70, 0.38, 1.0), vec3(1.0, 0.85, 1.0), flow);
    vec3 energy = violet * mask.g * (1.75 + flow * 1.1)
        + core * mask.r * (0.38 + flow * 0.70)
        + vec3(1.0, 0.91, 1.0) * mask.b * (0.35 + flow * 0.65);
    // Grand-slash layers select existing mask channels. Kind 0/1 retain all other effects unchanged.
    if (effectData.y > 1.5 && effectData.y < 2.5) {
        energy = vec3(1.0, 0.92, 1.0) * mask.b * 2.3;
    } else if (effectData.y > 2.5 && effectData.y < 3.5) {
        energy = vec3(0.60, 0.20, 1.0) * mask.r * 1.15;
    } else if (effectData.y > 3.5 && effectData.y < 4.5) {
        // Remove the solid interior from the blurred green channel: leave a soft surrounding glow.
        energy = violet * max(0.0, mask.g-mask.r*0.65) * 2.6;
    } else if (effectData.y > 4.5) {
        float streaks = pow(max(0.0,sin(uv.x*67.0+uv.y*21.0)),5.0);
        energy = vec3(0.65,0.28,1.0) * (mask.b+max(0.0,mask.g-mask.r)*0.5) * streaks * 1.6;
    }
    float opacity = vertexColor.a * ColorModulator.a;
    if (mask.a < 0.002 || opacity <= 0.0) discard;
    fragColor = vec4(energy * pulse * vertexColor.rgb * ColorModulator.rgb, opacity);
}

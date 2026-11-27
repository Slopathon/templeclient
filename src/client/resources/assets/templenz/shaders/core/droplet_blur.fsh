#version 330

uniform sampler2D InSampler;

layout(std140) uniform DropletConfig {
    mat4 ViewProj;
    mat4 InvViewProj;
    vec4 Right;
    vec4 Up;
    vec4 Sun;
    vec4 Sky;
    vec4 Screen;
    vec4 Time;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
#ifdef HORIZONTAL
    vec2 stepUv = vec2(Screen.x, 0.0);
#else
    vec2 stepUv = vec2(0.0, Screen.y);
#endif
    vec4 here = texture(InSampler, texCoord);
    float ref = here.g > 0.0 ? here.r : 1e9;
    float reach = here.g > 0.0 ? clamp(Screen.z * 3.0 / max(here.r, 0.5), 2.0, 24.0) : 6.0;
    for (int i = -4; i <= 4; i++) {
        vec4 s = texture(InSampler, texCoord + stepUv * float(i) * reach * 0.25);
        if (here.g <= 0.0 && s.g > 0.0) {
            ref = min(ref, s.r);
        }
    }
    if (ref > 1e8) {
        fragColor = vec4(0.0);
        return;
    }
    float sigma = reach * 0.5;
    float range = Up.w * 2.0 + ref * 0.004;
    float sum = 0.0;
    float weight = 0.0;
    float cover = 0.0;
    float fade = 0.0;
    float total = 0.0;
    for (int i = -16; i <= 16; i++) {
        float o = float(i) * reach / 16.0;
        vec4 s = texture(InSampler, texCoord + stepUv * o);
        float ws = exp(-o * o / (2.0 * sigma * sigma));
        total += ws;
        if (s.g <= 0.0) {
            continue;
        }
        float dd = (s.r - ref) / range;
        float w = ws * exp(-dd * dd);
        sum += s.r * w;
        weight += w;
        cover += s.g * w;
        fade += s.b * w;
    }
    if (weight <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }
    fragColor = vec4(sum / weight, cover / total, fade / weight, 1.0);
}

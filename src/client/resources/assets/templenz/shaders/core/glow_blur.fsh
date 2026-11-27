#version 330

uniform sampler2D InSampler;

layout(std140) uniform GlowConfig {
    vec4 Glow;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
#ifdef HORIZONTAL
    vec2 dir = vec2(texel.x, 0.0);
#else
    vec2 dir = vec2(0.0, texel.y);
#endif
    dir *= 1.5 * Glow.x;
    vec4 sum = texture(InSampler, texCoord);
    float total = 1.0;
    for (int i = 1; i <= 6; i++) {
        float w = exp(-float(i * i) / 18.0);
        sum += (texture(InSampler, texCoord + dir * float(i)) + texture(InSampler, texCoord - dir * float(i))) * w;
        total += 2.0 * w;
    }
    fragColor = sum / total;
}

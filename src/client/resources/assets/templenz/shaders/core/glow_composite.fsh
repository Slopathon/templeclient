#version 330

uniform sampler2D InSampler;

layout(std140) uniform GlowConfig {
    vec4 Glow;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 light = texture(InSampler, texCoord).rgb * Glow.y * 1.3;
    light = 1.0 - exp(-light * 1.6);
    fragColor = vec4(light, 1.0);
}

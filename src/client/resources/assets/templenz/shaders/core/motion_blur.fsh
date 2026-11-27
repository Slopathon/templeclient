#version 330

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    mat4 Reproject;
    vec4 Blur;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 prev = Reproject * vec4(texCoord * 2.0 - 1.0, 0.5, 1.0);
    vec2 velocity = vec2(0.0);
    if (prev.w > 1e-5) {
        velocity = (texCoord - (prev.xy / prev.w * 0.5 + 0.5)) * Blur.x;
        float len = length(velocity);
        if (len > Blur.z) {
            velocity *= Blur.z / len;
        }
    }
    int samples = int(Blur.y);
    vec3 sum = vec3(0.0);
    for (int i = 0; i < samples; i++) {
        float t = float(i) / float(samples - 1) - 0.5;
        sum += texture(InSampler, texCoord + velocity * t).rgb;
    }
    fragColor = vec4(sum / float(samples), 1.0);
}

#version 330

uniform sampler2D SceneSampler;

layout(std140) uniform ResolveConfig {
    vec4 Info;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(SceneSampler, 0));
    vec3 c;
    if (Info.x < 0.5) {
        c = texture(SceneSampler, texCoord).rgb;
    } else {
        vec3 sum = vec3(0.0);
        float weight = 0.0;
        float radius = Info.x * 2.0;
        for (int i = 0; i < 24; i++) {
            float f = (float(i) + 0.5) / 24.0;
            float r = sqrt(f) * radius;
            float a = float(i) * 2.39996;
            vec2 o = vec2(cos(a), sin(a)) * r * texel;
            float w = 1.0 - f * 0.6;
            sum += texture(SceneSampler, texCoord + o).rgb * w;
            weight += w;
        }
        c = sum / weight;
    }
    float grey = dot(c, vec3(0.299, 0.587, 0.114));
    c = max(mix(vec3(grey), c, 1.18), 0.0);
    vec2 v = texCoord - 0.5;
    float vignette = 1.0 - dot(v, v) * Info.z;
    c *= vignette * (1.0 - Info.y);
    fragColor = vec4(c, 1.0);
}

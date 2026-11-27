#version 330

uniform sampler2D CoverSampler;

layout(std140) uniform SongConfig {
    vec4 Shade;
    vec4 Place;
    vec4 Extra;
};

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec2 p = vec2(texCoord.x, 1.0 - texCoord.y);
    vec2 uv = (p - Place.xy) / Place.zw;
    vec3 cover = texture(CoverSampler, clamp(uv, 0.0, 1.0)).rgb;
    vec3 col = mix(Shade.rgb, cover, Extra.x);
    col = mix(col, Shade.rgb, mix(0.2, 0.9, p.y));
    vec2 c = (p - 0.5) * vec2(Extra.z, 1.0);
    col *= 1.0 - 0.28 * smoothstep(0.45, 1.25, length(c));
    col += (hash(gl_FragCoord.xy + fract(Extra.y) * 61.0) - 0.5) / 255.0;
    fragColor = vec4(col, Shade.a);
}

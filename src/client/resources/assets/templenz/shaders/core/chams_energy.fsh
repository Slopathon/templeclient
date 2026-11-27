#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec4 tint;
in vec2 texCoord0;
in float rim;
in float look;
in vec3 viewNormal;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 turn = mat2(0.8, -0.6, 0.6, 0.8);
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = turn * p * 2.03 + 11.7;
        a *= 0.5;
    }
    return v;
}

vec3 stars(vec2 uv, float scale, float t, vec3 hue) {
    vec2 p = uv * scale;
    vec2 cell = floor(p);
    vec2 f = fract(p) - 0.5;
    float h = hash(cell);
    if (h < 0.86) {
        return vec3(0.0);
    }
    vec2 offset = vec2(hash(cell + 3.1), hash(cell + 7.7)) - 0.5;
    float d = length(f - offset * 0.6);
    float size = 0.02 + 0.05 * fract(h * 17.0);
    float twinkle = 0.55 + 0.45 * sin(t * (2.0 + 4.0 * fract(h * 31.0)) + h * 50.0);
    float core = smoothstep(size, 0.0, d);
    float glow = smoothstep(size * 5.0, 0.0, d) * 0.25;
    vec3 c = mix(vec3(1.0), hue, fract(h * 13.0) * 0.7);
    return c * (core + glow) * twinkle;
}

vec3 partner(vec3 c) {
    return clamp(vec3(c.b * 0.9 + 0.1, c.r * 0.5 + 0.2, c.g * 0.6 + 0.5), 0.0, 1.0);
}

void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) {
        discard;
    }
    float t = GameTime * 1200.0;
    vec3 hue = tint.rgb;
#ifdef GLOW
    float edge = 0.1 + 0.9 * pow(clamp(rim, 0.0, 1.0), 2.0);
    float shimmer = 0.9 + 0.1 * sin(t * 2.2 + gl_FragCoord.y * 0.02);
    fragColor = vec4(hue * edge * tint.a * shimmer, 1.0);
    return;
#else
    vec2 uv = (gl_FragCoord.xy - 0.5 * ScreenSize) / ScreenSize.y;
    float r = clamp(rim, 0.0, 1.0);
    vec3 col;
    float alpha = tint.a;
    int kind = int(look + 0.5);
    if (kind == 2) {
        col = vec3(0.004, 0.002, 0.012);
        col += stars(uv + vec2(t * 0.004, 0.0), 26.0, t, hue) * 0.5;
        float swirl = fbm(vec2(atan(viewNormal.y, viewNormal.x) * 2.0 + t * 0.6, r * 4.0 - t * 0.3));
        vec3 hot = mix(hue, vec3(1.0, 0.55, 0.2), 0.35);
        col += hot * pow(r, 2.0) * (0.6 + 1.6 * swirl);
        col += vec3(1.0, 0.9, 0.8) * pow(r, 7.0) * 1.5;
    } else if (kind == 3) {
        col = hue * 0.06;
        float v1 = abs(fbm(uv * 3.2 + vec2(t * 0.08, -t * 0.05)) * 2.0 - 1.0);
        float v2 = abs(fbm(uv * 7.0 - vec2(t * 0.11, t * 0.07) + 4.0) * 2.0 - 1.0);
        float veins = smoothstep(0.07, 0.0, v1) + 0.6 * smoothstep(0.05, 0.0, v2);
        float pulse = 0.7 + 0.3 * sin(t * 2.6 + uv.y * 6.0);
        col += hue * veins * 2.2 * pulse + vec3(1.0) * pow(veins, 3.0) * 0.6;
        col += hue * pow(r, 2.5) * 2.0;
    } else if (kind == 5) {
        // Topographic: a flat bright fill with dark contour lines laid over the skin, so they stay on the body as it moves.
        float h = fbm(texCoord0 * 6.0 + vec2(t * 0.01, 0.0));
        float v = h * 9.0;
        float f = fract(v);
        float w = max(fwidth(v), 1e-4);
        float line = 1.0 - smoothstep(0.0, w * 1.4, min(f, 1.0 - f));
        col = mix(mix(hue * 1.08, hue * 0.82, smoothstep(0.2, 0.8, h)), hue * 0.05, line * 0.95);
        col += hue * pow(r, 3.0) * 0.5;
    } else if (kind == 4) {
        float scan = 0.65 + 0.35 * sin(gl_FragCoord.y * 1.4 - t * 30.0);
        float band = smoothstep(0.08, 0.0, abs(fract(uv.y * 0.6 - t * 0.35) - 0.5));
        float flicker = 0.88 + 0.12 * noise(vec2(t * 18.0, 0.0));
        col = hue * (0.35 + 0.5 * scan) * flicker + hue * band * 0.9 + vec3(1.0) * pow(r, 4.0) * 0.8;
        col += hue * pow(r, 1.5) * 1.3;
        alpha *= 0.55 + 0.35 * pow(r, 1.5) + band * 0.2;
    } else {
        vec2 drift = vec2(t * 0.012, -t * 0.008);
        float n1 = fbm(uv * 2.2 + drift);
        float n2 = fbm(uv * 4.3 - drift * 1.7 + n1 * 1.5);
        col = vec3(0.01, 0.005, 0.03);
        col += mix(hue, partner(hue), n2) * pow(n1, 1.8) * 1.6;
        col += mix(partner(hue), vec3(1.0), 0.3) * pow(n2, 4.0) * 0.8;
        col += stars(uv + drift * 0.5, 14.0, t, hue);
        col += stars(uv + drift * 1.2, 32.0, t * 1.3, partner(hue)) * 0.8;
        col += stars(uv + drift * 2.0, 70.0, t * 0.7, vec3(1.0)) * 0.5;
        col += hue * pow(r, 2.4) * 2.2 + vec3(1.0) * pow(r, 8.0) * 1.2;
    }
    fragColor = vec4(col, alpha);
#endif
}

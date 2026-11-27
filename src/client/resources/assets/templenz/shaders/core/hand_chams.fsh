#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec4 tint;
in vec2 texCoord0;
in float rim;
in float look;
in float speed;
in vec3 viewNormal;
in vec3 viewDir;
in vec3 localPos;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float hash3(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
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

vec3 hsv(float h, float s, float v) {
    vec3 k = clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return v * mix(vec3(1.0), k, s);
}

vec3 partner(vec3 c) {
    return clamp(vec3(c.b * 0.9 + 0.1, c.r * 0.5 + 0.2, c.g * 0.6 + 0.5), 0.0, 1.0);
}

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
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
    float halo = smoothstep(size * 5.0, 0.0, d) * 0.25;
    return mix(vec3(1.0), hue, fract(h * 13.0) * 0.7) * (core + halo) * twinkle;
}

vec3 studio(vec3 r, vec3 hue) {
    float up = r.y;
    vec3 sky = mix(vec3(0.55, 0.62, 0.75), vec3(1.0), smoothstep(0.2, 0.9, up));
    vec3 floorCol = mix(vec3(0.1, 0.09, 0.1), hue * 0.35, 0.5);
    vec3 env = mix(floorCol, sky, smoothstep(-0.15, 0.15, up));
    env += vec3(1.0, 0.85, 0.6) * exp(-abs(up) * 18.0) * 0.8;
    env += vec3(1.4) * smoothstep(0.035, 0.0, abs(r.x - 0.45)) * step(0.0, up);
    env += vec3(0.9) * smoothstep(0.025, 0.0, abs(r.x + 0.55)) * step(0.1, up);
    return env * mix(vec3(1.0), hue, 0.85);
}

vec2 cells(vec3 p, out float id) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    float d1 = 8.0;
    float d2 = 8.0;
    id = 0.0;
    for (int z = -1; z <= 1; z++) {
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                vec3 o = vec3(x, y, z);
                vec3 c = i + o;
                vec3 j = vec3(hash3(c), hash3(c + 19.1), hash3(c + 47.3));
                float d = length(o + j - f);
                if (d < d1) {
                    d2 = d1;
                    d1 = d;
                    id = hash3(c + 3.7);
                } else if (d < d2) {
                    d2 = d;
                }
            }
        }
    }
    return vec2(d1, d2);
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }
    float t = GameTime * 1200.0 * max(speed, 0.0);
    vec3 hue = tint.rgb;
    vec3 N = normalize(viewNormal);
    vec3 V = normalize(viewDir);
    float r = clamp(rim, 0.0, 1.0);
#ifdef GLOW
    float edge = pow(r, 1.6);
    float shimmer = 0.9 + 0.1 * sin(t * 2.2 + gl_FragCoord.y * 0.03);
    fragColor = vec4(hue * edge * tint.a * shimmer, 1.0);
    return;
#else
    vec2 uv = (gl_FragCoord.xy - 0.5 * ScreenSize) / ScreenSize.y;
    vec3 L = normalize(vec3(0.35, 0.85, 0.45));
    float diffuse = max(dot(N, L), 0.0);
    float spec = pow(max(dot(N, normalize(L + V)), 0.0), 48.0);
    int kind = int(look + 0.5);
    vec3 col;
    float alpha = tint.a;

    if (kind == 1) {
        float lum = luma(tex.rgb);
        col = mix(tex.rgb, hue * (0.35 + lum * 1.3), 0.72);
        float sweep = smoothstep(0.09, 0.0, abs(fract(uv.x * 0.5 + uv.y * 0.35 - t * 0.35) - 0.5));
        col += vec3(1.0) * sweep * 0.45 + hue * pow(r, 2.0) * 0.6;
    } else if (kind == 2) {
        float g = 0.5 + 0.5 * sin(uv.x * 3.0 + uv.y * 2.0 - t * 1.4);
        col = mix(hue, partner(hue), g) * (0.65 + 0.35 * diffuse) + pow(r, 2.5) * 0.7 + spec * 0.4;
    } else if (kind == 3) {
        vec3 wheel = hsv(fract(uv.x * 0.7 + uv.y * 0.35 - t * 0.22), 0.75, 1.0);
        col = wheel * (0.6 + 0.4 * diffuse) + vec3(1.0) * pow(r, 3.0) * 0.6 + spec * 0.4;
    } else if (kind == 4) {
        col = hue * 0.4 + vec3(1.0) * pow(r, 2.2) * 0.9 + spec * 1.6;
        float streak = smoothstep(0.03, 0.0, abs(fract(uv.x * 0.8 - uv.y * 0.5 + 0.2) - 0.5)) * 0.35;
        col += streak;
        alpha *= 0.16 + 0.75 * pow(r, 1.8) + spec * 0.6 + streak;
    } else if (kind == 5) {
        vec3 R = normalize(reflect(-V, N) + vec3(uv.x * 0.9, uv.y * 1.6 + 0.15, 0.0));
        col = studio(R, hue) * 1.05 + spec * 1.5 + mix(hue, vec3(1.0), 0.6) * pow(r, 3.0) * 0.8;
    } else if (kind == 6) {
        vec2 flow = vec2(fbm(localPos.xy * 3.0 + vec2(t * 0.35, -t * 0.2)), fbm(localPos.yz * 3.0 - vec2(t * 0.25, t * 0.3)));
        vec3 R = normalize(reflect(-V, normalize(N + vec3(flow - 0.5, 0.0) * 0.9)) + vec3(uv.x * 0.9, uv.y * 1.6 + 0.15, 0.0));
        col = studio(R, hue) * 1.05 + spec * 1.2;
        col *= 0.8 + 0.4 * smoothstep(0.3, 0.7, flow.x);
        col += mix(hue, vec3(1.0), 0.6) * pow(r, 3.0) * 0.7;
    } else if (kind == 7) {
        vec2 drift = vec2(t * 0.012, -t * 0.008);
        float n1 = fbm(uv * 2.2 + drift);
        float n2 = fbm(uv * 4.3 - drift * 1.7 + n1 * 1.5);
        col = vec3(0.01, 0.005, 0.03);
        col += mix(hue, partner(hue), n2) * pow(n1, 1.8) * 1.6;
        col += mix(partner(hue), vec3(1.0), 0.3) * pow(n2, 4.0) * 0.8;
        col += stars(uv + drift * 0.5, 14.0, t, hue);
        col += stars(uv + drift * 1.2, 32.0, t * 1.3, partner(hue)) * 0.8;
        col += hue * pow(r, 2.4) * 2.2 + vec3(1.0) * pow(r, 8.0) * 1.2;
    } else if (kind == 8) {
        col = vec3(0.004, 0.002, 0.012) + stars(uv + vec2(t * 0.004, 0.0), 40.0, t, hue) * 0.9 + hue * 0.03;
        float swirl = fbm(vec2(atan(N.y, N.x) * 2.0 + t * 0.6, r * 4.0 - t * 0.3));
        col += mix(hue, vec3(1.0, 0.55, 0.2), 0.35) * pow(r, 2.0) * (0.6 + 1.6 * swirl) + vec3(1.0, 0.9, 0.8) * pow(r, 7.0) * 1.5;
    } else if (kind == 9) {
        col = hue * 0.06;
        float v1 = abs(fbm(uv * 7.5 + vec2(t * 0.12, -t * 0.08)) * 2.0 - 1.0);
        float v2 = abs(fbm(uv * 15.0 - vec2(t * 0.16, t * 0.1) + 4.0) * 2.0 - 1.0);
        float veins = smoothstep(0.045, 0.0, v1) + 0.6 * smoothstep(0.035, 0.0, v2);
        col += hue * veins * 2.2 * (0.7 + 0.3 * sin(t * 2.6 + uv.y * 6.0)) + vec3(1.0) * pow(veins, 3.0) * 0.6;
        col += hue * pow(r, 2.5) * 2.0;
    } else if (kind == 10) {
        float scan = 0.65 + 0.35 * sin(gl_FragCoord.y * 1.4 - t * 30.0);
        float band = smoothstep(0.08, 0.0, abs(fract(uv.y * 0.6 - t * 0.35) - 0.5));
        float flicker = 0.88 + 0.12 * noise(vec2(t * 18.0, 0.0));
        col = hue * (0.35 + 0.5 * scan) * flicker + hue * band * 0.9 + vec3(1.0) * pow(r, 4.0) * 0.8 + hue * pow(r, 1.5) * 1.3;
        alpha *= 0.55 + 0.35 * pow(r, 1.5) + band * 0.2;
    } else if (kind == 11) {
        vec2 p = vec2(uv.x * 5.0, uv.y * 3.5 - t * 1.8);
        float f = fbm(p + fbm(p * 0.8 + t * 0.4) * 1.3);
        float heat = clamp(f * 1.35 - 0.15 + pow(r, 2.0) * 0.35, 0.0, 1.0);
        vec3 ember = mix(vec3(0.9, 0.12, 0.02), hue, 0.3);
        vec3 flame = mix(vec3(1.0, 0.55, 0.08), hue, 0.2);
        col = mix(vec3(0.08, 0.01, 0.0), ember, smoothstep(0.1, 0.45, heat));
        col = mix(col, flame * 1.3, smoothstep(0.45, 0.75, heat));
        col = mix(col, vec3(1.3, 1.15, 0.85), smoothstep(0.78, 0.98, heat));
    } else if (kind == 12) {
        float flicker = step(0.25, noise(vec2(t * 23.0, 1.7)));
        vec2 p = localPos.xy * 14.0 + localPos.z * 7.0;
        float a1 = abs(fbm(p + vec2(t * 1.3, -t * 0.9)) - 0.5);
        float a2 = abs(fbm(p * 1.7 - vec2(t * 1.7, t * 1.1) + 7.0) - 0.5);
        float arcs = smoothstep(0.022, 0.0, a1) + smoothstep(0.016, 0.0, a2) * 0.7;
        col = hue * 0.08 + (hue * 1.6 + 0.4) * arcs * (0.5 + 0.8 * flicker) + hue * pow(r, 2.0) * 1.2;
    } else if (kind == 13) {
        float id;
        vec2 d = cells(localPos * 7.0, id);
        float facet = 0.45 + 0.55 * id;
        float edgeLine = smoothstep(0.06, 0.0, d.y - d.x);
        float glint = pow(max(0.0, sin(id * 40.0 + t * 1.5)), 24.0);
        col = mix(hue * 0.35, mix(hue, vec3(1.0), 0.45), facet) * (0.6 + 0.5 * diffuse);
        col += vec3(1.0) * (edgeLine * 0.5 + glint * 1.3) + mix(hue, vec3(1.0), 0.6) * pow(r, 2.5) * 0.9 + spec;
        alpha *= 0.82 + 0.18 * facet;
    } else if (kind == 14) {
        vec2 cell = floor(gl_FragCoord.xy / vec2(7.0, 10.0));
        float column = hash(vec2(cell.x, 7.0));
        float fall = fract(cell.y * 0.035 + t * (0.25 + column * 0.45) + column * 10.0);
        float glyph = step(0.45, hash(cell + floor(t * (4.0 + column * 6.0))));
        vec2 inCell = fract(gl_FragCoord.xy / vec2(7.0, 10.0));
        float pixel = step(0.15, inCell.x) * step(0.1, inCell.y);
        float trail = pow(fall, 5.0);
        col = hue * 0.06 + hue * glyph * pixel * trail * 1.6 + vec3(0.9, 1.0, 0.9) * glyph * pixel * step(0.97, fall);
        col += hue * pow(r, 2.5) * 0.8;
    } else if (kind == 15) {
        vec2 size = vec2(textureSize(Sampler0, 0));
        vec2 g = texCoord0 * size;
        vec2 w = fwidth(g) * 1.2;
        vec2 dist = min(fract(g), 1.0 - fract(g));
        float line = max(smoothstep(w.x, 0.0, dist.x), smoothstep(w.y, 0.0, dist.y));
        float pulse = 0.75 + 0.25 * sin(t * 3.0 - uv.y * 8.0);
        col = hue * (0.2 + line * 1.6 * pulse) + vec3(1.0) * line * pow(r, 2.0) * 0.5;
        alpha *= 0.1 + 0.9 * line;
    } else if (kind == 16) {
        float wisp = fbm(uv * 3.0 + vec2(0.0, -t * 0.25));
        col = mix(hue, vec3(1.0), 0.3) * (pow(r, 1.4) * 1.5 + wisp * 0.25);
        alpha *= clamp(0.1 + pow(r, 1.6) * 0.95 + wisp * 0.18, 0.0, 1.0);
    } else if (kind == 18) {
        // Topographic: a flat bright fill crossed by dark contour lines that stay fixed to the model.
        float h = fbm(localPos.xy * 1.5 + localPos.zx * 0.9 + vec2(t * 0.02, 0.0));
        float v = h * 9.0;
        float f = fract(v);
        float w = max(fwidth(v), 1e-4);
        float line = 1.0 - smoothstep(0.0, w * 1.4, min(f, 1.0 - f));
        vec3 fill = mix(hue * 1.08, hue * 0.82, smoothstep(0.2, 0.8, h)) * (0.85 + 0.15 * diffuse);
        col = mix(fill, hue * 0.05, line * 0.95);
    } else if (kind == 17) {
        float band = diffuse > 0.6 ? 1.0 : diffuse > 0.25 ? 0.72 : 0.48;
        col = hue * band + vec3(1.0) * step(0.6, spec) * 0.55;
        col = mix(col, hue * 0.12, smoothstep(0.68, 0.78, r));
    } else {
        col = hue * (0.62 + 0.38 * diffuse) + mix(hue, vec3(1.0), 0.5) * pow(r, 3.0) * 0.35;
    }
    fragColor = vec4(col, clamp(alpha, 0.0, 1.0));
#endif
}

#version 330

uniform sampler2D BackdropSampler;
#ifdef LENS_PASS
uniform sampler2D LayerSampler;
#endif

layout(std140) uniform GlassConfig {
    vec2 OutSize;
    vec2 Mouse;
    float GuiScale;
    float Time;
    float Seed;
    float Stretch;
    float Bezel;
    float Frost;
    float Dim;
    float Chroma;
    float Warp;
    float WarpSize;
    int GlassCount;
    int UiCount;
    vec4 Bounds;
    vec4 Tint;
    float Shine;
    float Saturation;
    float CursorLight;
    float Brightness;
    int Layers;
    ivec4 LayerRange[8];
    vec4 LayerBox[8];
    vec4 HudTintTop;
    vec4 HudTintBottom;
    vec4 HudAccent;
};

layout(std140) uniform GlassShapes {
    vec4 GlassBox[128];
    vec4 GlassProp[128];
};

layout(std140) uniform UiShapes {
    vec4 UiRect[336];
    vec4 UiStyle[336];
    ivec4 UiMeta[336];
};

layout(std140) uniform UiShapesMore {
    vec4 UiRect2[336];
    vec4 UiStyle2[336];
    ivec4 UiMeta2[336];
};

vec4 uiRect(int i) {
    return i < 336 ? UiRect[i] : UiRect2[i - 336];
}

vec4 uiStyle(int i) {
    return i < 336 ? UiStyle[i] : UiStyle2[i - 336];
}

ivec4 uiMeta(int i) {
    return i < 336 ? UiMeta[i] : UiMeta2[i - 336];
}

in vec2 texCoord;

out vec4 fragColor;

float sdBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float nearRadius;

float glassScene(vec2 p, int layer, out float op, out vec2 ctr, out vec2 hs, out float db, out float boost, out float ring) {
    float d = 1e5;
    float best = 1e5;
    db = 1e5;
    op = 0.0;
    boost = 0.0;
    ring = 0.0;
    ctr = vec2(0.0);
    hs = vec2(1.0);
    vec4 nearBox = vec4(0.0, 0.0, -1.0, -1.0);
    ivec4 range = LayerRange[layer];
    for (int i = range.x; i < range.y; i++) {
        vec4 b = GlassBox[i];
        if (any(greaterThan(abs(p - b.xy), b.zw + vec2(10.0 * GuiScale)))) {
            continue;
        }
        float m = min(b.z, b.w);
        float r = min(GlassProp[i].x, m);
        float bz = min(Bezel * GuiScale, m * 0.9);
        float di = sdBox(p - b.xy, b.zw, r);
        float dbi = sdBox(p - b.xy, b.zw, min(max(r, bz * 1.25), m));
        bool overlaps = nearBox.z >= 0.0 && all(lessThanEqual(abs(b.xy - nearBox.xy), b.zw + nearBox.zw + vec2(0.5 * GuiScale)));
        if (overlaps) {
            d = smin(d, di, 6.0 * GuiScale);
            db = smin(db, dbi, 6.0 * GuiScale);
        } else {
            d = min(d, di);
            db = min(db, dbi);
        }
        if (di < best) {
            best = di;
            op = GlassProp[i].y;
            nearRadius = r;
            boost = GlassProp[i].z;
#ifdef LENS_PASS
            ring = GlassProp[i].w;
#endif
            ctr = b.xy;
            hs = b.zw;
            nearBox = b;
        }
    }
    return d;
}

float band(vec2 p, int layer) {
    float o;
    float db;
    float bst;
    float rng;
    vec2 c;
    vec2 h;
    glassScene(p, layer, o, c, h, db, bst, rng);
    return db;
}

float hash(vec2 p) {
    p = fract((p + Seed) * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec2 grad(vec2 i) {
    float h = hash(i) * 6.2831853;
    return vec2(cos(h), sin(h));
}

float gnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return mix(mix(dot(grad(i), f), dot(grad(i + vec2(1.0, 0.0)), f - vec2(1.0, 0.0)), u.x),
               mix(dot(grad(i + vec2(0.0, 1.0)), f - vec2(0.0, 1.0)), dot(grad(i + vec2(1.0, 1.0)), f - vec2(1.0, 1.0)), u.x), u.y);
}

vec2 field(vec2 s, float t) {
    return vec2(gnoise(s + vec2(t * 0.6, -t * 0.35)) + 0.5 * gnoise(s * 1.9 + vec2(-t * 0.45, t * 0.55) + vec2(17.3, 4.1)),
                gnoise(s + vec2(-t * 0.4, t * 0.5) + vec2(31.7, 9.2)) + 0.5 * gnoise(s * 1.9 + vec2(t * 0.5, t * 0.3) + vec2(5.9, 23.4)));
}

vec2 flow(vec2 p) {
    vec2 s = p / (WarpSize * GuiScale) * 0.35;
    vec2 w = field(s, Time);
    w = field(s + w * 0.6, Time);
    return w * 1.2 * Warp * GuiScale;
}

vec3 tap(vec2 px) {
    vec2 uv = vec2(px.x, OutSize.y - px.y) / OutSize;
    vec3 c = texture(BackdropSampler, uv).rgb;
#ifdef LENS_PASS
    vec4 l = texture(LayerSampler, uv);
    c = mix(c, l.rgb, l.a);
#endif
    return c;
}

vec3 glassSample(vec2 p, vec2 edgeOff, vec2 warpOff, float chroma) {
    int n = Frost > 0.25 ? 16 : 1;
    vec3 acc = vec3(0.0);
    for (int i = 0; i < n; i++) {
        float fi = float(i);
        float rr = sqrt((fi + 0.5) / float(n)) * Frost * GuiScale;
        float an = fi * 2.39996;
        vec2 q = p + warpOff + (n == 1 ? vec2(0.0) : vec2(cos(an), sin(an)) * rr);
        acc.r += tap(q + edgeOff * (1.0 + chroma)).r;
        acc.g += tap(q + edgeOff).g;
        acc.b += tap(q + edgeOff * (1.0 - chroma)).b;
    }
    return acc / float(n);
}

// The HUD's glass (x.monolith's glass pass): a refracting bevel, a light frost, a dark shade, a top-to-bottom tint, a little
// extra saturation and a rim lit by the scene just outside the panel. Sizes are in units of 1/540 of the screen height.
vec3 hudFrost(vec2 px, float unit) {
    vec3 acc = tap(px);
    for (int i = 0; i < 8; i++) {
        float an = float(i) * 0.785398 + 0.39;
        acc += tap(px + vec2(cos(an), sin(an)) * 6.0 * unit);
    }
    return acc / 9.0;
}

vec4 hudGlass(vec2 p, vec2 ctr, vec2 hs, float r, float op) {
    float unit = max(OutSize.y / 540.0, 0.5);
    vec2 q = p - ctr;
    float d = sdBox(q, hs, r);
    vec2 g = vec2(sdBox(q + vec2(1.0, 0.0), hs, r) - sdBox(q - vec2(1.0, 0.0), hs, r),
                  sdBox(q + vec2(0.0, 1.0), hs, r) - sdBox(q - vec2(0.0, 1.0), hs, r));
    vec2 n = length(g) > 0.00001 ? normalize(g) : vec2(0.0, -1.0);
    float bevel = max(6.0 * unit, min(min(hs.x, hs.y) * 0.9, 15.0 * unit));
    float edge = 1.0 - clamp(-d / bevel, 0.0, 1.0);
    float lens = pow(edge, 2.2);
    vec2 off = n * lens * bevel;
    vec3 col = vec3(tap(p + off * 0.9).r, tap(p + off).g, tap(p + off * 1.12).b);
    col = mix(col, hudFrost(p + off, unit), 0.25);

    float vy = clamp((p.y - (ctr.y - hs.y)) / max(hs.y * 2.0, 1.0), 0.0, 1.0);
    vec4 tint = mix(HudTintTop, HudTintBottom, vy);
    col = mix(col, vec3(0.01, 0.03, 0.08), HudAccent.w);
    col = mix(col, tint.rgb, tint.a);
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(luma), col, 1.15);
    col += vec3(0.035) * smoothstep(0.55, 0.0, vy);

    float rimWidth = 1.7 * unit;
    float rim = 1.0 - smoothstep(0.0, rimWidth, -d);
    vec3 outside = tap(p + n * (bevel * 1.7 + 3.0 * unit));
    vec3 rimColor = outside * 1.5 + vec3(0.06) + HudAccent.rgb * 0.14;
    float light = dot(n, normalize(vec2(-0.55, -0.83))) * 0.5 + 0.5;
    col = mix(col, rimColor, clamp(rim * (0.30 + 0.70 * light) * 0.9, 0.0, 1.0));
    float rimBack = (1.0 - smoothstep(0.0, rimWidth * 1.5, -d)) * (1.0 - light) * 0.4;
    col = mix(col, outside * 1.25 + vec3(0.04), rimBack);
    col += (outside * 0.4 + HudAccent.rgb * 0.07) * pow(edge, 2.6) * 0.6;
    return vec4(clamp(col, 0.0, 1.0), clamp(0.5 - d, 0.0, 1.0) * op);
}

vec4 shadeGlass(vec2 p, int layer) {
    float op;
    float db;
    float boost;
    float ring;
    vec2 ctr;
    vec2 hs;
    float d = glassScene(p, layer, op, ctr, hs, db, boost, ring);
    float aa = GuiScale;
    if (d >= aa || op <= 0.0) {
        return vec4(0.0);
    }
    if (boost < 0.0) {
        return hudGlass(p, ctr, hs, min(nearRadius, min(hs.x, hs.y)), op);
    }
    float e = GuiScale;
    vec2 n = normalize(vec2(band(p + vec2(e, 0.0), layer) - band(p - vec2(e, 0.0), layer),
                            band(p + vec2(0.0, e), layer) - band(p - vec2(0.0, e), layer)) + 1e-6);
    float bez = max(min(Bezel * GuiScale, min(hs.x, hs.y) * 0.9), 2.0);
    float u = 1.0 - clamp(max(-db, 0.0) / bez, 0.0, 1.0);
    float chroma = min(Chroma * (1.0 + 1.5 * boost) + 0.08 * boost, 0.6);
    float k = min(Stretch, 0.5) * (2.0 / 3.0) / (1.0 + chroma);
    float inner = clamp(-db / max(min(hs.x, hs.y), 1e-3), 0.0, 1.0);
    float rim = 1.0 - inner;
    vec2 q = p;
    vec2 disp = -n * bez * k * u * u * u;
    if (boost > 0.01) {
        q = mix(p, ctr, (0.26 + 0.30 * boost) * (1.0 - rim * rim));
        disp += -n * min(hs.x, hs.y) * (0.5 * boost) * pow(rim, 3.0);
    }
    float wave = 0.0;
    if (ring > 0.0) {
        wave = clamp(d / max(ring * 0.5, 1e-3), -1.0, 1.0);
        disp += -n * ring * 0.85 * (1.0 - wave * wave) * sign(wave + 1e-4);
        q = mix(q, ctr, 0.06 * (1.0 - abs(wave)));
    }
    bool control = min(hs.x, hs.y) < 18.0 * GuiScale;
    vec2 warpAt = control ? p - ctr + vec2(97.0, 41.0) * GuiScale : p;
    vec3 g = glassSample(q, disp, flow(warpAt), chroma);
    float gl = dot(g, vec3(0.299, 0.587, 0.114));
    g = mix(vec3(gl), g, Saturation);
    g = mix(g, (gl * 1.35 + 0.04) * Tint.rgb, Tint.a);
    g *= 1.0 + Brightness;
    g = mix(g, vec3(0.0), Dim) + 0.01;
    vec2 toward = control ? Mouse - 0.5 * OutSize : Mouse - ctr;
    vec2 light = normalize(mix(vec2(-0.7071, -0.7071), normalize(toward + 1e-3), CursorLight));
    if (boost > 0.01) {
        g *= mix(1.08, 0.70, smoothstep(0.5, 1.0, rim));
        vec2 fromCtr = normalize(p - ctr + 1e-4);
        float caustic = pow(max(0.0, dot(fromCtr, -light)), 3.0) * inner * inner;
        g += caustic * 0.40 * boost;
        g += 0.5 * boost * pow(max(0.0, dot(fromCtr, light)), 24.0) * smoothstep(0.15, 0.75, rim);
    }
    g += (1.0 - smoothstep(0.0, 1.2 * GuiScale, max(-d, 0.0))) * 0.55 * Shine * (1.0 + boost) * pow(abs(dot(n, light)), 8.0);
    if (ring > 0.0) {
        float crest = 1.0 - abs(wave);
        g += 0.22 * crest * crest;
    }
    float cover = ring > 0.0
            ? (1.0 - smoothstep(0.75, 1.0, abs(wave))) * op
            : (1.0 - smoothstep(-aa, aa, d)) * op;
    return vec4(g, cover);
}

void main() {
    vec2 p = vec2(texCoord.x * OutSize.x, (1.0 - texCoord.y) * OutSize.y);
    vec3 col = vec3(0.0);
    float alpha = 0.0;

    bool nearGlass = p.x >= Bounds.x && p.y >= Bounds.y && p.x <= Bounds.z && p.y <= Bounds.w;
    for (int layer = 0; layer <= Layers; layer++) {
        vec4 box = LayerBox[layer];
        if (p.x < box.x || p.y < box.y || p.x > box.z || p.y > box.w) {
            continue;
        }
        ivec4 range = LayerRange[layer];
        if (range.y > range.x && nearGlass) {
            vec4 pane = shadeGlass(p, layer);
            if (pane.a > 0.0) {
                float outA = pane.a + alpha * (1.0 - pane.a);
                col = (pane.rgb * pane.a + col * alpha * (1.0 - pane.a)) / max(outA, 1e-5);
                alpha = outA;
            }
        }

        for (int i = range.z; i < range.w; i++) {
            vec4 st = uiStyle(i);
            vec4 r = uiRect(i);
            if (any(greaterThan(abs(p - r.xy), r.zw + vec2(st.z + 2.0)))) {
                continue;
            }
            ivec4 meta = uiMeta(i);
            if (meta.z != 0) {
                vec4 clip = vec4(float(meta.y & 0xFFFF), float((meta.y >> 16) & 0xFFFF),
                                 float(meta.z & 0xFFFF), float((meta.z >> 16) & 0xFFFF));
                if (p.x < clip.x || p.y < clip.y || p.x > clip.z || p.y > clip.w) {
                    continue;
                }
            }
            float d = sdBox(p - r.xy, r.zw, min(st.x, min(r.z, r.w)));
            if (st.y > 0.0) {
                d = abs(d + st.y * 0.5) - st.y * 0.5;
            }
            float soft = st.z > 0.0 ? st.z : 0.75;
            vec4 color = vec4(float((meta.x >> 16) & 255), float((meta.x >> 8) & 255),
                              float(meta.x & 255), float((meta.x >> 24) & 255)) / 255.0;
            // Gradient: meta.w = mode (bits 0-1: 1 vertical, 2 horizontal, 3 diagonal) | alpha/4 (bits 2-7) | rgb (8-31).
            int gmode = meta.w & 3;
            if (gmode != 0) {
                int ga = (meta.w >> 2) & 63;
                vec4 color2 = vec4(float((meta.w >> 24) & 255), float((meta.w >> 16) & 255), float((meta.w >> 8) & 255),
                                   float(ga * 4 + ga / 16)) / 255.0;
                vec2 q = (p - r.xy) / max(r.zw, vec2(1e-3));
                float t = gmode == 1 ? q.y * 0.5 + 0.5 : gmode == 2 ? q.x * 0.5 + 0.5 : (q.x + q.y) * 0.25 + 0.5;
                color = mix(color, color2, clamp(t, 0.0, 1.0));
            }
            float a = color.a * (1.0 - smoothstep(-soft, soft, d));
            if (a <= 0.0) {
                continue;
            }
            float outA = a + alpha * (1.0 - a);
            col = (color.rgb * a + col * alpha * (1.0 - a)) / max(outA, 1e-5);
            alpha = outA;
        }
    }

#ifdef LENS_PASS
    vec4 base = texture(LayerSampler, texCoord);
    float outA = alpha + base.a * (1.0 - alpha);
    vec3 outC = (col * alpha + base.rgb * base.a * (1.0 - alpha)) / max(outA, 1e-5);
    fragColor = vec4(clamp(outC, 0.0, 1.0), outA);
#else
    fragColor = vec4(clamp(col, 0.0, 1.0), alpha);
#endif
}

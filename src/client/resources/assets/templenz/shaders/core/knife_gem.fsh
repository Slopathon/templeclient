#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 lightMapColor;
in vec2 texCoord0;
in vec3 viewNormal;
in vec3 viewPos;
in vec3 localPos;
flat in int finish;
flat in int material;

out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(i), hash3(i + vec3(1, 0, 0)), u.x), mix(hash3(i + vec3(0, 1, 0)), hash3(i + vec3(1, 1, 0)), u.x), u.y),
            mix(mix(hash3(i + vec3(0, 0, 1)), hash3(i + vec3(1, 0, 1)), u.x), mix(hash3(i + vec3(0, 1, 1)), hash3(i + vec3(1, 1, 1)), u.x),
                    u.y), u.z);
}

float fbm3(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.07 + vec3(7.1, 3.3, 5.9);
        a *= 0.5;
    }
    return v;
}

vec3 facets(vec3 p, out float id, out float edge) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    float d1 = 8.0;
    float d2 = 8.0;
    vec3 best = vec3(0.0);
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
                    best = c;
                } else if (d < d2) {
                    d2 = d;
                }
            }
        }
    }
    id = hash3(best + 3.7);
    edge = smoothstep(0.07, 0.0, d2 - d1);
    return vec3(hash3(best + 11.3), hash3(best + 29.9), hash3(best + 5.1)) - 0.5;
}

vec3 studio(vec3 r) {
    float up = r.y;
    vec3 col = mix(vec3(0.03, 0.032, 0.036), vec3(0.32, 0.35, 0.4), smoothstep(-0.45, 0.35, up));
    col += vec3(1.5) * smoothstep(0.62, 0.92, up);
    col += vec3(1.3, 1.2, 1.1) * smoothstep(0.07, 0.0, abs(r.x - 0.55)) * smoothstep(-0.3, 0.2, up);
    col += vec3(0.8, 0.9, 1.15) * smoothstep(0.05, 0.0, abs(r.x + 0.62)) * smoothstep(-0.1, 0.4, up);
    col += vec3(1.0, 0.82, 0.58) * exp(-abs(up + 0.05) * 12.0) * 0.4;
    return col;
}

vec3 bend(vec3 r) {
    vec2 s = viewPos.xy / max(-viewPos.z, 0.05);
    return normalize(r + vec3(s.x * 0.7, s.y * 1.1 + 0.1, 0.0));
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }
    vec3 N = normalize(viewNormal);
    if (!gl_FrontFacing) {
        N = -N;
    }
    vec3 V = normalize(-viewPos);
    vec3 light = lightMapColor.rgb;
    float lit = mix(0.22, 1.0, dot(light, vec3(0.333)));

    float hi = max(tex.r, max(tex.g, tex.b));
    float lo = min(tex.r, min(tex.g, tex.b));
    float sat = hi > 0.001 ? (hi - lo) / hi : 0.0;
    float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float coloured = smoothstep(0.2, 0.42, sat) * smoothstep(0.05, 0.16, hi);
    float steelMask = (1.0 - coloured) * smoothstep(0.08, 0.17, lum);
    float darkMask = max(1.0 - coloured - steelMask, 0.0);
    float gemMask = finish == 2 || finish == 3 ? coloured : 0.0;
    if (finish == 3) {
        gemMask = max(gemMask, darkMask);
        darkMask = 0.0;
    }
    float chromeMask = finish == 4 ? coloured : 0.0;
    float paintMask = finish == 1 ? coloured : 0.0;

    vec3 L0 = normalize(vec3(0.35, 0.85, 0.45));
    vec3 L1 = normalize(vec3(-0.7, 0.35, 0.6));
    float diffuse = 0.45 + 0.55 * max(dot(N, L0), 0.0);

    vec3 Rn = reflect(-V, N);
    float sheen = pow(max(dot(Rn, L0), 0.0), 18.0) * 0.18;
    vec3 handle = tex.rgb * diffuse * light + vec3(sheen) * lit;

    float brush = vnoise(localPos * vec3(0.6, 9.0, 0.6)) - 0.5;
    vec3 Rs = bend(reflect(-V, normalize(N + vec3(brush * 0.04, 0.0, brush * 0.04))));
    float fresS = pow(1.0 - max(dot(N, V), 0.0), 5.0);
    vec3 steelTone = pow(mix(vec3(lum), tex.rgb, 0.5), vec3(0.75)) * 1.2;
    vec3 film = 0.5 + 0.5 * cos(6.2832 * (fresS * 1.4 + brush * 0.3 + vec3(0.0, 0.33, 0.67)));
    vec3 env = studio(Rs);
    env = pow(env, vec3(1.35)) * 1.15;
    vec3 steel = env * mix(steelTone, film, 0.18 * fresS + 0.04) * lit
            + vec3(1.0) * pow(max(dot(Rs, L0), 0.0), 320.0) * 1.4 * lit
            + vec3(0.9, 0.95, 1.0) * pow(max(dot(Rs, L1), 0.0), 90.0) * 0.5 * lit
            + tex.rgb * 0.1 * light;

    float id;
    float edge;
    vec3 tilt = facets(localPos * 2.2, id, edge);
    vec3 Nf = normalize(N + tilt * 0.6);
    vec3 Rf = bend(reflect(-V, Nf));
    float fres = 0.06 + 0.94 * pow(1.0 - max(dot(Nf, V), 0.0), 4.0);
    vec3 hue = finish == 3 ? vec3(0.42, 0.36, 0.55) : (hi > 0.001 ? tex.rgb / hi : vec3(1.0));
    hue = pow(hue, vec3(2.2));
    float depth = finish == 3 ? 0.05 : hi;
    float swirl = fbm3(localPos * 0.35 + vec3(fbm3(localPos * 0.2) * 3.0));
    float bands = 0.5 + 0.5 * sin(swirl * 14.0 + localPos.y * 0.25);
    vec3 body = hue * depth * (0.12 + 0.88 * bands * bands) * (0.65 + 0.5 * id) * (0.35 + 0.65 * diffuse) * light;
    float spread = 0.03;
    vec3 genv = vec3(studio(normalize(Rf + vec3(spread, 0.0, 0.0))).r, studio(Rf).g, studio(normalize(Rf - vec3(spread, 0.0, 0.0))).b);
    vec3 coat = genv * mix(hue, vec3(1.0), 0.35) * fres * 1.25;
    float spark = pow(max(dot(Rf, L0), 0.0), 260.0) * 3.5 + pow(max(dot(Rf, L1), 0.0), 180.0) * 1.8;
    spark *= 0.35 + 0.65 * step(0.4, id);
    vec3 gem = body + hue * depth * 0.08 * light + (coat + vec3(spark) + mix(hue, vec3(1.0), 0.5) * edge * 0.08) * lit;

    vec3 chrome = studio(Rs) * pow(tex.rgb / max(hi, 0.001), vec3(1.2)) * hi * 1.35 * lit
            + mix(tex.rgb, vec3(1.0), 0.6) * pow(max(dot(Rs, L0), 0.0), 90.0) * 1.1 * lit + tex.rgb * 0.1 * light;

    vec3 paint = tex.rgb * diffuse * light + studio(Rn) * 0.12 * lit + vec3(pow(max(dot(Rn, L0), 0.0), 60.0)) * 0.5 * lit;

    vec3 col = handle * darkMask + steel * steelMask + gem * gemMask + chrome * chromeMask + paint * paintMask;

    if (material > 0) {
        float blade = finish == 3 ? 1.0 : 1.0 - darkMask;
        float detail = 0.7 + 0.6 * lum;
        vec3 mat;
        if (material == 5) {
            vec3 dh = vec3(0.3, 0.95, 0.9);
            vec3 dbody = dh * (0.18 + 0.82 * bands * bands) * (0.65 + 0.5 * id) * (0.35 + 0.65 * diffuse) * light * 0.8;
            vec3 denv = vec3(studio(normalize(Rf + vec3(spread, 0.0, 0.0))).r, studio(Rf).g, studio(normalize(Rf - vec3(spread, 0.0, 0.0))).b);
            mat = dbody + (denv * mix(dh, vec3(1.0), 0.45) * fres * 1.3 + vec3(spark) * 1.2 + vec3(0.8, 1.0, 1.0) * edge * 0.12) * lit;
        } else if (material == 1 || material == 2) {
            vec3 tint;
            float grain;
            if (material == 1) {
                grain = 0.5 + 0.5 * sin(localPos.x * 2.2 + fbm3(localPos * vec3(0.15, 1.6, 1.6)) * 6.0);
                tint = mix(vec3(0.42, 0.27, 0.13), vec3(0.72, 0.52, 0.3), grain);
            } else {
                grain = fbm3(localPos * 3.0);
                tint = mix(vec3(0.34, 0.34, 0.35), vec3(0.6, 0.6, 0.6), grain) * (0.85 + 0.3 * step(0.8, hash3(floor(localPos * 6.0))));
            }
            mat = tint * detail * diffuse * light + vec3(pow(max(dot(Rn, L0), 0.0), 24.0)) * 0.12 * lit;
        } else {
            vec3 metal = material == 3 ? vec3(0.78, 0.8, 0.84) : material == 4 ? vec3(1.0, 0.72, 0.22)
                    : material == 6 ? vec3(0.2, 0.18, 0.2) : vec3(0.92, 0.48, 0.3);
            mat = env * metal * detail * lit
                    + mix(metal, vec3(1.0), 0.25) * pow(max(dot(Rs, L0), 0.0), 260.0) * 1.2 * lit
                    + metal * 0.1 * light;
            if (material == 6) {
                mat += vec3(0.25, 0.12, 0.08) * fresS * lit;
            }
        }
        col = mix(col, mat, blade);
    }
    fragColor = apply_fog(vec4(col, 1.0), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart,
            FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

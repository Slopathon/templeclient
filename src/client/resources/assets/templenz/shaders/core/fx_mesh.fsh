#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D SceneSampler;
uniform sampler2D FinishTable;
uniform sampler2D LightSampler;

in vec3 viewPos;
in vec3 viewNormal;
in vec4 baseColor;
in vec2 patternUV;
in vec3 accentColor;
in vec2 lightUV;
in float accentWeight;
in float occlusion;
flat in int finishIndex;
flat in int patternIndex;

out vec4 fragColor;

vec3 shoulder(vec3 c) {
    vec3 over = max(c - 0.8, 0.0);
    return min(c, 0.8) + over / (1.0 + over * 2.5);
}

vec3 studio(vec3 r, vec3 up, vec3 tint) {
    float y = dot(r, up);
    vec3 sky = mix(vec3(0.62, 0.68, 0.8), vec3(1.05, 1.03, 1.0), smoothstep(0.15, 0.85, y));
    vec3 ground = vec3(0.17, 0.155, 0.16);
    vec3 env = mix(ground, sky, smoothstep(-0.2, 0.12, y));
    env += vec3(1.0, 0.86, 0.62) * exp(-abs(y) * 16.0) * 0.55;
    env += vec3(1.3) * smoothstep(0.06, 0.0, abs(r.x - 0.42)) * smoothstep(0.1, 0.4, y);
    env += vec3(0.8) * smoothstep(0.04, 0.0, abs(r.x + 0.5)) * smoothstep(0.2, 0.5, y);
    return env * tint;
}

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
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + 17.1;
        a *= 0.5;
    }
    return v;
}

float cellEdge(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float d1 = 8.0;
    float d2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 o = vec2(x, y);
            vec2 c = o + vec2(hash(i + o), hash(i + o + 31.7)) * 0.8 + 0.1;
            float d = length(c - f);
            if (d < d1) {
                d2 = d1;
                d1 = d;
            } else if (d < d2) {
                d2 = d;
            }
        }
    }
    return d2 - d1;
}

struct Detail {
    float height;
    float strength;
    float cavity;
    float roughAdd;
    float glint;
    float glowMask;
};

Detail pattern(int id, vec2 uv) {
    Detail d = Detail(0.0, 0.0, 0.0, 0.0, 0.0, 1.0);
    if (id == 1) {
        float ring = pow(0.5 + 0.5 * sin(uv.x * 6.2832 * 18.0 + noise(uv * vec2(3.0, 9.0)) * 2.0), 4.0) * (1.0 - uv.x * 0.85);
        float stria = noise(vec2(uv.y * 90.0, uv.x * 5.0)) * 0.7 + noise(vec2(uv.y * 23.0, uv.x * 2.0)) * 0.3;
        d.height = ring * 0.45 + stria * 0.55;
        d.strength = 0.004 * (1.0 - uv.x * 0.6);
        d.cavity = (1.0 - ring) * 0.1 * (1.0 - uv.x) + (1.0 - stria) * 0.1;
        d.roughAdd = (1.0 - uv.x) * 0.2;
    } else if (id == 2) {
        float across = abs(uv.y - 0.5) * 2.0;
        float barb = fract(uv.x * 46.0 + across * 9.0);
        float split = step(0.93, noise(vec2(uv.x * 18.0, uv.y * 3.0)));
        d.height = smoothstep(0.0, 0.3, barb) * (1.0 - smoothstep(0.7, 1.0, barb));
        d.strength = 0.0012;
        d.cavity = (1.0 - d.height) * 0.07 + split * 0.25;
        d.roughAdd = 0.1;
    } else if (id == 3) {
        float n = fbm(uv * vec2(5.0, 7.0));
        float vein = 1.0 - smoothstep(0.0, 0.06, abs(n - 0.5));
        float vein2 = 1.0 - smoothstep(0.0, 0.035, abs(fbm(uv * vec2(13.0, 17.0) + 4.0) - 0.5));
        d.height = vein * 0.8 + vein2 * 0.4 + noise(uv * 90.0) * 0.1;
        d.strength = 0.003;
        d.cavity = -(vein * 0.25 + vein2 * 0.1);
        d.roughAdd = 0.05;
        d.glowMask = clamp(vein + vein2 * 0.6, 0.0, 1.0);
    } else if (id == 4) {
        float strand = noise(vec2(uv.x * 10.0, uv.y * 140.0));
        float clump = noise(vec2(uv.x * 3.0, uv.y * 22.0));
        d.height = strand * 0.7 + clump * 0.3;
        d.strength = 0.004;
        d.cavity = (1.0 - strand) * 0.4;
        d.roughAdd = 0.1;
    } else if (id == 5) {
        float line = noise(vec2(uv.y * 260.0, uv.x * 3.0));
        d.height = line;
        d.strength = 0.0006;
        d.roughAdd = line * 0.12 + fbm(uv * 6.0) * 0.08;
        d.glint = step(0.985, hash(floor(uv * vec2(90.0, 300.0)))) * 0.6;
    } else if (id == 6) {
        float curl = sin(uv.x * 40.0 + sin(uv.y * 11.0 + uv.x * 7.0) * 2.4) * sin(uv.y * 23.0 + cos(uv.x * 13.0) * 1.7);
        float groove = 1.0 - smoothstep(0.0, 0.12, abs(curl));
        d.height = 1.0 - groove;
        d.strength = 0.0035;
        d.cavity = groove * 0.6;
        d.roughAdd = groove * 0.3;
        d.glowMask = groove;
    } else if (id == 7) {
        vec2 g = uv * vec2(28.0, 28.0);
        float sx = sin(g.x * 3.1416);
        float sy = sin(g.y * 3.1416);
        float over = step(0.0, sx * sy);
        float strandH = over > 0.5 ? abs(sin(g.y * 3.1416)) : abs(sin(g.x * 3.1416));
        float fibre = noise(over > 0.5 ? vec2(g.x * 30.0, g.y) : vec2(g.x, g.y * 30.0));
        d.height = strandH * 0.8 + fibre * 0.2;
        d.strength = 0.004;
        d.cavity = (1.0 - strandH) * 0.5;
        d.roughAdd = 0.1;
    } else if (id == 8) {
        float e = cellEdge(uv * 4.0 + vec2(0.0, uv.x * 2.0));
        float plane = 1.0 - smoothstep(0.0, 0.05, e);
        d.height = plane * 0.3;
        d.strength = 0.002;
        d.glint = plane;
    } else if (id == 9) {
        vec2 g = uv * vec2(24.0, 16.0);
        g.x += step(1.0, mod(g.y, 2.0)) * 0.5;
        vec2 f = fract(g) - vec2(0.5, 0.2);
        float dome = 1.0 - clamp(length(f * vec2(1.0, 1.4)) * 1.6, 0.0, 1.0);
        d.height = dome;
        d.strength = 0.004;
        d.cavity = (1.0 - dome) * 0.5;
        d.roughAdd = (1.0 - dome) * 0.2;
    } else if (id == 10) {
        vec2 g = uv * vec2(18.0, 18.0 * 1.1547);
        vec2 a = mod(g, vec2(1.0, 1.732)) - vec2(0.5, 0.866);
        vec2 b = mod(g + vec2(0.5, 0.866), vec2(1.0, 1.732)) - vec2(0.5, 0.866);
        vec2 h = dot(a, a) < dot(b, b) ? a : b;
        vec2 q = abs(h);
        float edge = 0.5 - max(dot(q, vec2(0.866, 0.5)), q.x);
        float line = 1.0 - smoothstep(0.0, 0.05, edge);
        d.height = line;
        d.strength = 0.002;
        d.glint = line * 0.5;
    } else if (id == 11) {
        float n = fbm(uv * vec2(18.0, 24.0));
        float pit = smoothstep(0.62, 0.75, noise(uv * 70.0));
        d.height = n - pit * 0.4;
        d.strength = 0.002;
        d.cavity = pit * 0.3;
        d.roughAdd = 0.05;
    }
    return d;
}

vec3 bump(vec3 N, float h, float strength) {
    vec3 dpdx = dFdx(viewPos);
    vec3 dpdy = dFdy(viewPos);
    float dhdx = dFdx(h) * strength;
    float dhdy = dFdy(h) * strength;
    vec3 r1 = cross(dpdy, N);
    vec3 r2 = cross(N, dpdx);
    float det = dot(dpdx, r1);
    if (abs(det) < 1e-12) {
        return N;
    }
    vec3 grad = sign(det) * (dhdx * r1 + dhdy * r2);
    return normalize(abs(det) * N - grad);
}

float ggx(float nh, float a) {
    float a2 = a * a;
    float d = nh * nh * (a2 - 1.0) + 1.0;
    return a2 / (3.14159 * d * d + 1e-5);
}

void main() {
    vec3 N = normalize(viewNormal);
    vec3 V = normalize(-viewPos);
    if (dot(N, V) < 0.0) {
        N = -N;
    }
    mat3 toView = mat3(ModelViewMat);
    vec3 up = normalize(toView * vec3(0.0, 1.0, 0.0));
    vec3 L = normalize(toView * normalize(vec3(0.35, 1.0, 0.3)));
    vec3 fillDir = normalize(toView * normalize(vec3(-0.6, 0.25, -0.5)));
    vec3 base = baseColor.rgb;
    float glow = clamp(accentWeight, 0.0, 1.0);
    vec4 f0 = texelFetch(FinishTable, ivec2(finishIndex, 0), 0);
    vec4 f1 = texelFetch(FinishTable, ivec2(finishIndex, 1), 0);
    vec3 worldLight = texture(LightSampler, clamp(lightUV / 256.0 + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0))).rgb;
    vec3 here = max(worldLight, vec3(0.06, 0.065, 0.08));
    vec3 colour;

    if (finishIndex < 4) {
        float kind = float(finishIndex);
        vec3 H = normalize(L + V);
        float nv = max(dot(N, V), 0.0);
        float nl = dot(N, L);
        float fresnel = pow(1.0 - nv, 3.0);
        vec3 keyCol = vec3(1.0, 0.96, 0.9);
        vec3 fillCol = vec3(0.55, 0.65, 0.9);
        float skyward = dot(N, up) * 0.5 + 0.5;
        vec3 ambient = mix(vec3(0.24, 0.2, 0.18), vec3(0.34, 0.38, 0.48), skyward);
        float rimLight = pow(1.0 - nv, 2.5) * smoothstep(-0.2, 0.4, dot(N, -L) + 0.3);
        if (kind < 0.5) {
            float wrap = clamp((nl + 0.45) / 1.45, 0.0, 1.0);
            wrap *= wrap;
            float fill = clamp((dot(N, fillDir) + 0.3) / 1.3, 0.0, 1.0) * 0.28;
            float through = pow(clamp(dot(V, -L), 0.0, 1.0), 3.0) * pow(1.0 - nv, 1.5) * 0.45;
            float spec = pow(max(dot(N, H), 0.0), 28.0) * 0.18;
            float sheen = pow(1.0 - nv, 4.0) * 0.22;
            float lightness = 0.25 + 0.75 * clamp(dot(base, vec3(0.299, 0.587, 0.114)) * 1.6, 0.0, 1.0);
            colour = base * (ambient + keyCol * wrap * 0.85 + fillCol * fill)
                   + base * base * through * 1.4
                   + keyCol * spec * lightness + mix(base, vec3(1.0), 0.5) * sheen * lightness
                   + mix(base, vec3(0.8, 0.9, 1.0), 0.4) * rimLight * 0.35 * lightness;
            colour *= mix(vec3(1.0), here * 1.1, 0.85);
        } else if (kind < 1.5) {
            vec3 R = reflect(-V, N);
            vec3 fres = base + (1.0 - base) * pow(1.0 - nv, 5.0);
            vec3 env = studio(R, up, mix(vec3(1.0), base, 0.25));
            float tight = pow(max(dot(N, H), 0.0), 140.0) * 2.4;
            float broad = pow(max(dot(N, H), 0.0), 18.0) * 0.25;
            colour = env * fres * 0.95 + base * ambient * 0.25 + keyCol * (tight + broad) * mix(base, vec3(1.0), 0.6)
                   + rimLight * mix(base, vec3(1.0), 0.5) * 0.3;
            colour *= mix(vec3(1.0), here * 1.1, 0.85);
        } else if (kind > 2.5) {
            float wrap = clamp((nl + 0.45) / 1.45, 0.0, 1.0);
            wrap *= wrap;
            vec3 body = base * (ambient * 1.1 + keyCol * wrap * 0.75);
            float edge = pow(1.0 - nv, 2.2);
            vec3 hot = mix(base, vec3(1.0), 0.7);
            float spec = pow(max(dot(N, H), 0.0), 40.0) * 0.35;
            colour = body * 0.8 + base * 0.5 + hot * edge * 1.5 + hot * spec;
        } else {
            vec2 uv = gl_FragCoord.xy / ScreenSize;
            vec2 bend = N.xy * (0.03 + 0.05 * (1.0 - nv));
            vec3 behind = vec3(texture(SceneSampler, uv - bend * 1.12).r,
                               texture(SceneSampler, uv - bend).g,
                               texture(SceneSampler, uv - bend * 0.88).b);
            float spec = pow(max(dot(N, H), 0.0), 160.0);
            colour = mix(behind * 1.04, behind * base * 1.7, 0.5);
            colour *= mix(1.0, 0.45, smoothstep(0.35, 0.95, fresnel * 2.2));
            vec3 R = reflect(-V, N);
            colour += studio(R, up, vec3(1.0)) * (0.04 + 0.4 * pow(1.0 - nv, 4.0));
            float rim = pow(1.0 - nv, 6.0);
            colour += rim * mix(base, vec3(1.0), 0.7) * 1.1 + spec * 2.2;
            colour += max(nl, 0.0) * 0.05;
        }
        vec3 lit = base * (1.15 + 0.2 * nv) + 0.05;
        colour = mix(colour, lit, glow * 0.75) + base * glow * 0.35;
    } else {
        float rough = f0.r;
        float metal = f0.g;
        float trans = f0.b;
        float sss = f0.a;
        float sheenAmt = f1.r;
        float coat = f1.g;
        float irid = f1.b;

        Detail d = pattern(patternIndex, patternUV);
        if (d.strength > 0.0) {
            N = bump(N, d.height, d.strength);
        }
        rough = clamp(rough + d.roughAdd, 0.03, 1.0);
        vec3 albedo = base * (1.0 - clamp(d.cavity, -0.5, 0.9) * 0.8);
        float ao = occlusion * (1.0 - max(d.cavity, 0.0) * 0.5);

        vec3 H = normalize(L + V);
        float nv = max(dot(N, V), 1e-3);
        float nl = dot(N, L);
        float nh = max(dot(N, H), 0.0);
        float skyward = dot(N, up) * 0.5 + 0.5;

        vec3 key = here * 0.8;
        vec3 ambient = here * mix(vec3(0.34, 0.31, 0.3), vec3(0.44, 0.47, 0.55), skyward) * ao;

        float wrapK = 0.25 + sss * 0.5;
        float wrap = clamp((nl + wrapK) / (1.0 + wrapK), 0.0, 1.0);
        vec3 diffuse = albedo * (1.0 - metal) * (ambient + key * wrap * mix(1.0, ao, 0.5));

        float back = pow(clamp(dot(V, -L), 0.0, 1.0), 2.0) * pow(1.0 - nv, 1.2);
        vec3 through = albedo * albedo * key * back * sss * 1.8 + albedo * here * sss * 0.08;

        vec3 F0 = mix(vec3(0.04), albedo, metal);
        vec3 film = 0.5 + 0.5 * cos(6.2832 * (vec3(0.0, 0.33, 0.67) + nv * 1.8 + patternUV.x * 0.3));
        F0 = mix(F0, film * mix(0.25, 0.9, metal), irid);
        vec3 F = F0 + (1.0 - F0) * pow(1.0 - nv, 5.0);
        float a = rough * rough;
        float k = (rough + 1.0) * (rough + 1.0) / 8.0;
        float G = (nv / (nv * (1.0 - k) + k)) * (max(nl, 0.0) / (max(nl, 0.0) * (1.0 - k) + k));
        vec3 spec = ggx(nh, a) * F * G / (4.0 * nv * max(nl, 1e-3) + 1e-4) * max(nl, 0.0) * key;

        vec3 R = reflect(-V, N);
        vec3 env = studio(R, up, vec3(1.0));
        vec3 envSoft = mix(vec3(0.42, 0.44, 0.5), vec3(0.7, 0.72, 0.78), skyward);
        vec3 reflection = mix(env, envSoft, smoothstep(0.1, 0.8, rough)) * F * here * ao * mix(0.35, 1.0, metal);

        vec3 sheen = mix(albedo, vec3(1.0), 0.4) * pow(1.0 - nv, 4.0) * sheenAmt * (key * 0.7 + ambient);
        float coatF = 0.04 + 0.96 * pow(1.0 - nv, 5.0);
        vec3 coatSpec = (ggx(nh, 0.02) * max(nl, 0.0) * 0.06 * key + studio(R, up, vec3(1.0)) * here * coatF * 0.5) * coat;

        colour = diffuse + through + spec + reflection + sheen + coatSpec;
        colour += here * d.glint * (0.4 + 0.6 * pow(nh, 30.0));

        if (trans > 0.0) {
            vec2 uv = gl_FragCoord.xy / ScreenSize;
            vec2 bendUV = N.xy * (0.02 + 0.04 * (1.0 - nv));
            vec3 behind = vec3(texture(SceneSampler, uv - bendUV * 1.1).r,
                               texture(SceneSampler, uv - bendUV).g,
                               texture(SceneSampler, uv - bendUV * 0.9).b);
            vec3 tinted = behind * mix(vec3(1.0), albedo * 1.6, 0.55) * mix(1.0, 0.55, pow(1.0 - nv, 2.0));
            colour = mix(colour, tinted + spec + reflection * 1.2 + coatSpec, trans);
        }
        float flow = 0.8 + 0.2 * sin(patternUV.x * 9.0 - GameTime * 2400.0);
        float g = smoothstep(0.02, 0.55, glow * d.glowMask);
        vec3 glowCol = accentColor * (1.4 + 0.4 * pow(1.0 - nv, 2.0)) * flow;
        colour = mix(colour, glowCol, g * 0.9) + glowCol * g * 0.6;
    }

    colour = shoulder(colour);
    float dither = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    colour += dither / 255.0;
    fragColor = vec4(colour, baseColor.a);
}

#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec3 lightColor;
in vec2 rawLight;
in vec2 data;
in vec3 worldPos;
in vec3 viewPos;

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
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    return noise(p) * 0.55 + noise(p * 2.03 + 7.1) * 0.3 + noise(p * 4.1 + 2.3) * 0.15;
}

const float FOOT = 0.735;

float sole(vec2 p) {
    vec2 q = abs(p) - vec2(FOOT - 0.05);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 0.05;
}

void main() {
    vec3 snow = vertexColor.rgb;
    float alpha = vertexColor.a;
    vec3 N;
    float shade = 1.0;
    float glintMask = 1.0;
    if (data.x > 4.0) {
        vec2 p = vec2(data.x - 8.0, data.y);
        float d = sole(p);
        float inside = 1.0 - smoothstep(-0.08, 0.06, d);
        float ridge = exp(-pow((d - 0.16) / 0.11, 2.0));
        float wall = smoothstep(-0.45, 0.0, d) * inside;
        vec2 pixel = floor(clamp((p + FOOT) / (2.0 * FOOT), 0.0, 0.999) * 4.0);
        float lugs = hash(pixel + 0.37) * noise(p * 5.0 + 3.1);
        float floorShade = 0.8 - 0.09 * lugs * smoothstep(-0.05, -0.3, d) - 0.14 * wall * (0.55 + 0.45 * p.x);
        shade = mix(1.0, floorShade, inside);
        float a = max(inside, ridge * 0.55);
        if (a * alpha < 0.01) {
            discard;
        }
        alpha *= a;
        snow *= mix(1.0, 1.06, ridge * (1.0 - inside));
        N = vec3(0.0, 1.0, 0.0);
        glintMask = 1.0 - inside;
    } else {
        N = vec3(data.x, sqrt(max(0.0, 1.0 - dot(data, data))), data.y);
        if (N.y > 0.2) {
            vec2 w = worldPos.xz;
            float e = 0.04;
            float h0 = fbm(w * 2.6) + 0.35 * sin(w.x * 5.1 + w.y * 2.3 + fbm(w * 0.7) * 4.0);
            float hx = fbm((w + vec2(e, 0.0)) * 2.6) + 0.35 * sin((w.x + e) * 5.1 + w.y * 2.3 + fbm((w + vec2(e, 0.0)) * 0.7) * 4.0);
            float hz = fbm((w + vec2(0.0, e)) * 2.6) + 0.35 * sin(w.x * 5.1 + (w.y + e) * 2.3 + fbm((w + vec2(0.0, e)) * 0.7) * 4.0);
            N = normalize(N + vec3(h0 - hx, 0.0, h0 - hz) * 2.4);
            float patchy = fbm(w * 1.3 + 11.0);
            alpha = smoothstep(patchy * 0.55, patchy * 0.55 + 0.3, alpha);
        }
    }
    if (alpha < 0.01) {
        discard;
    }

    vec3 sun = normalize(vec3(TextureMat[0].x, max(TextureMat[0].y, -0.2), 0.12));
    float day = TextureMat[0].w;
    float sky = rawLight.y;
    float overcast = TextureMat[1].x;
    float face = mix(0.62, 1.0, clamp(N.y, 0.0, 1.0));
    float dome = 0.8 + 0.2 * clamp(N.y * 1.6 - 0.6, 0.0, 1.0);
    float sunlit = clamp(dot(N, sun), 0.0, 1.0);
    float modelling = dome * mix(1.0, 0.8 + 0.34 * sunlit, day * sky * (1.0 - 0.5 * overcast));
    vec3 lit = lightColor * face * modelling * shade;
    float brightness = dot(lit, vec3(0.299, 0.587, 0.114));
    vec3 tint = mix(vec3(1.0), mix(vec3(0.8, 0.88, 1.0), vec3(1.0), smoothstep(0.35, 0.9, brightness)), day * sky);
    vec3 colour = snow * lit * tint;
    float lum = dot(colour, vec3(0.299, 0.587, 0.114));
    colour = mix(colour, vec3(lum) * vec3(0.92, 0.96, 1.05), 0.4 * (1.0 - day) * sky);

    vec3 V = normalize(-viewPos);
    vec3 Nv = normalize(mat3(ModelViewMat) * N);
    vec3 Sv = normalize(mat3(ModelViewMat) * sun);
    vec2 cellPos = worldPos.xz * 40.0;
    vec2 grain = floor(cellPos);
    float dotShape = smoothstep(0.22, 0.06, length(fract(cellPos) - 0.5));
    float pick = step(0.99, hash(grain + floor(worldPos.y * 40.0))) * dotShape;
    float facing = pow(max(dot(reflect(-Sv, Nv), V), 0.0), 6.0);
    float glint = pick * facing * day * sky * glintMask * (1.0 - smoothstep(12.0, 28.0, sphericalVertexDistance));
    colour += vec3(1.0, 0.98, 0.94) * glint * 1.6;

    fragColor = apply_fog(vec4(colour, alpha), sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

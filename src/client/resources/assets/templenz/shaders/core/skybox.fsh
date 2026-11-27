#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec3 direction;
in vec4 vertexColor;

out vec4 fragColor;

const float PI = 3.14159265;

vec3 hash33(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)),
             dot(p, vec3(269.5, 183.3, 246.1)),
             dot(p, vec3(113.5, 271.9, 124.6)));
    return fract(sin(p) * 43758.5453123);
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float valueNoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), u.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * valueNoise2(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

float hash31(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float valueNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash31(i), hash31(i + vec3(1, 0, 0)), u.x), mix(hash31(i + vec3(0, 1, 0)), hash31(i + vec3(1, 1, 0)), u.x), u.y),
               mix(mix(hash31(i + vec3(0, 0, 1)), hash31(i + vec3(1, 0, 1)), u.x), mix(hash31(i + vec3(0, 1, 1)), hash31(i + vec3(1, 1, 1)), u.x), u.y),
               u.z);
}

float fbm3(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * valueNoise3(p);
        p = p * 2.02 + vec3(17.1, 9.2, 4.7);
        a *= 0.5;
    }
    return v;
}

float fbm3lite(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * valueNoise3(p);
        p = p * 2.02 + vec3(17.1, 9.2, 4.7);
        a *= 0.5;
    }
    return v / 0.875 * 0.97;
}

vec3 stars(vec3 d, float density, float chance, float size, float seconds, float twinkle) {
    vec3 p = d * density;
    float px = max(length(fwidth(p)), 1e-4);
    vec3 total = vec3(0.0);
    {
        vec3 cell = floor(p);
        vec3 h = hash33(cell);
        if (h.x > chance) {
            return total;
        }
        vec3 h2 = hash33(cell + 19.19);
        vec3 centre = normalize(cell + 0.25 + 0.5 * h2) * density;
        float dist = length(p - centre);
        float r = px * size;
        float bright = 0.06 + 0.94 * pow(h.y, 6.0);
        float core = exp(-(dist * dist) / (r * r));
        float halo = exp(-dist / (r * 3.0)) * 0.08 * smoothstep(0.7, 1.0, h.y);
        float flicker = 1.0 - twinkle * 0.45 * (0.5 + 0.5 * sin(seconds * (0.8 + 2.2 * h2.z) + h.z * 6.2832));
        vec3 colour = mix(vec3(0.92, 0.95, 1.0), vec3(1.0, 0.82, 0.48), smoothstep(0.35, 0.95, h.z));
        float inside = 1.0 - smoothstep(0.1, 0.25, dist);
        total += colour * (core + halo) * bright * flicker * inside;
    }
    return total;
}

vec3 starField(vec3 d, float seconds, float twinkle) {
    return stars(d, 38.0, 0.16, 1.3, seconds, twinkle)
         + stars(d, 95.0, 0.09, 1.0, seconds * 1.3, twinkle) * 0.35
         + stars(d, 210.0, 0.05, 0.8, seconds * 0.7, twinkle * 0.5) * 0.15;
}

void auroraPalette(float which, out vec3 low, out vec3 mid, out vec3 high) {
    if (which < 0.5) {
        low = vec3(0.24, 1.0, 0.58); mid = vec3(0.12, 0.84, 0.70); high = vec3(0.55, 0.36, 1.0);
    } else if (which < 1.5) {
        low = vec3(0.30, 0.95, 0.92); mid = vec3(0.18, 0.55, 1.0); high = vec3(0.24, 0.18, 0.75);
    } else if (which < 2.5) {
        low = vec3(1.0, 0.36, 0.64); mid = vec3(0.90, 0.19, 0.35); high = vec3(0.50, 0.08, 0.30);
    } else {
        low = TextureMat[2].rgb; high = TextureMat[3].rgb; mid = mix(low, high, 0.45);
    }
}

float fbmLite(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * valueNoise2(p);
        p = p * 2.03 + 11.7;
        a *= 0.5;
    }
    return v / 0.875;
}

float curtainAt(vec2 p, float t, float blur) {
    float total = 0.0;
    for (int b = 0; b < 2; b++) {
        float fb = float(b);
        float x = p.x * 0.12 + fb * 7.3;
        float centre = (fbmLite(vec2(x * 0.45, t * 0.015 + fb * 4.0)) - 0.5) * 5.0 + sin(x * 0.6 + t * 0.04 + fb * 2.0) * 1.2
                + fb * 2.6 - 1.3;
        float width = 0.10 + 0.08 * valueNoise2(vec2(x * 0.8, t * 0.03 + fb));
        width = max(width, blur * 1.5);
        float dist = (p.y * 0.12 - centre) / width;
        float line = exp(-dist * dist) * (0.14 / width);
        float rays = 0.3 + 0.7 * pow(valueNoise2(vec2(x * 18.0 + t * 0.15, fb * 13.0)), 2.0);
        rays = mix(rays, 0.6, clamp(blur * 6.0, 0.0, 1.0));
        float presence = smoothstep(0.08, 0.5, fbmLite(vec2(x * 0.35 - t * 0.01, fb * 9.0)));
        total += line * rays * presence;
    }
    return total;
}

vec3 aurora(vec3 d, float t, float which) {
    if (d.y < 0.02) {
        return vec3(0.0);
    }
    vec3 low, mid, high;
    auroraPalette(which, low, mid, high);
    vec3 total = vec3(0.0);
    const int STEPS = 7;
    vec2 base = d.xz / d.y;
    float blur = length(fwidth(base)) * 0.12 * 6.0;
    float jitter = hash21(gl_FragCoord.xy);
    for (int i = 0; i < STEPS; i++) {
        float k = (float(i) + jitter) / float(STEPS);
        vec2 p = base * (6.0 + k * 6.5);
        float c = curtainAt(p, t, blur * (1.0 + k));
        vec3 col = k < 0.3 ? mix(low, mid, k / 0.3) : mix(mid, high, (k - 0.3) / 0.7);
        float fall = pow(1.0 - k, 1.6);
        total += col * c * fall;
    }
    total *= 7.0 / float(STEPS);
    return total * smoothstep(0.02, 0.22, d.y);
}

vec3 dirFrom(float azimuthDeg, float elevationDeg) {
    float az = radians(azimuthDeg);
    float el = radians(elevationDeg);
    return vec3(cos(el) * sin(az), sin(el), -cos(el) * cos(az));
}

vec3 milkyWay(vec3 d, vec3 edgeTone, vec3 coreTone, float gain) {
    vec3 axis = normalize(vec3(0.42, 0.62, 0.66));
    float across = dot(d, axis);
    float band = exp(-pow(across * 3.4, 2.0));
    float core = exp(-pow(across * 9.0, 2.0));
    float clouds = fbm3(d * 4.0) * 0.7 + fbm3(d * 13.0 + 3.0) * 0.3;
    float rift = smoothstep(0.45, 0.75, fbm3(d * 7.0 + vec3(9.0, 1.0, 5.0))) * exp(-pow(across * 11.0, 2.0));
    float glow = band * (0.25 + 0.9 * clouds) + core * 0.6 * clouds;
    glow *= 1.0 - 0.85 * rift;
    vec3 colour = mix(edgeTone, coreTone, core);
    return colour * glow * gain;
}

const int CONSTELLATION_STARS = 15;
const vec3 CONSTELLATION[CONSTELLATION_STARS] = vec3[CONSTELLATION_STARS](
    vec3(0.0, 15.0, 1.0),
    vec3(-10.5, 9.0, 0.95),
    vec3(10.5, 9.0, 1.0),
    vec3(-8.5, 6.2, 0.6),
    vec3(8.5, 6.2, 0.7),
    vec3(-2.9, 4.6, 0.65),
    vec3(2.9, 4.6, 0.75),
    vec3(2.7, -4.0, 0.6),
    vec3(2.4, -11.5, 0.7),
    vec3(0.0, -16.0, 0.95),
    vec3(-2.4, -11.5, 0.6),
    vec3(-2.7, -4.0, 0.55),
    vec3(-6.2, 2.4, 0.35),
    vec3(-8.4, -1.6, 0.28),
    vec3(-5.6, -6.0, 0.22)
);
vec3 constellation(vec3 d, float azimuthDeg, float seconds, float twinkle) {
    vec3 f = dirFrom(azimuthDeg, 40.0);
    float facing = dot(d, f);
    if (facing < 0.5) {
        return vec3(0.0);
    }
    vec3 r = normalize(cross(f, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(r, f);
    vec2 p = degrees(vec2(dot(d, r), dot(d, up)) / facing) / 1.3;
    float px = max(length(fwidth(p)), 1e-4);
    vec3 total = vec3(0.0);
    for (int i = 0; i < CONSTELLATION_STARS; i++) {
        vec3 s = CONSTELLATION[i];
        float dist = length(p - s.xy);
        float rad = px * (1.8 + 1.6 * s.z);
        float core = exp(-(dist * dist) / (rad * rad));
        float halo = exp(-dist / (px * 5.0 + 0.12)) * 0.22 * s.z;
        vec2 q = abs(p - s.xy);
        float spikes = s.z > 0.9 ? (exp(-q.y / (px * 0.6)) + exp(-q.x / (px * 0.6))) * exp(-dist / (px * 14.0)) * 0.35 : 0.0;
        float flicker = 1.0 - twinkle * 0.2 * (0.5 + 0.5 * sin(seconds * (1.1 + float(i) * 0.37) + float(i) * 2.3));
        vec3 tone = mix(vec3(0.85, 0.92, 1.0), vec3(1.0, 0.84, 0.55), fract(float(i) * 0.618));
        total += tone * (core * 2.4 + halo + spikes) * (0.35 + 0.65 * s.z) * flicker;
    }
    return total;
}

vec3 nightLayers(vec3 d, float seconds, float twinkle, float auroraStrength, float palette, float constellationAz,
        float constellationMode, float starAmount) {
    if (d.y < -0.03) {
        return vec3(0.0);
    }
    float open = smoothstep(-0.01, 0.03, d.y);
    float haze = smoothstep(0.02, 0.35, d.y);
    vec3 colour = vec3(0.0);
    if (starAmount > 0.0) {
        colour = starField(d, seconds, twinkle) * open * mix(0.35, 1.0, haze) * starAmount;
    }
    if (constellationMode > 0.5) {
        colour += constellation(d, degrees(constellationAz), seconds, twinkle) * open * starAmount;
    }
    if (auroraStrength > 0.001) {
        colour += aurora(d, seconds, palette) * auroraStrength;
    }
    return colour;
}

vec3 stormSky(vec3 d, float t, vec3 tone, vec3 flashTone) {
    vec3 shadow = vec3(0.035, 0.038, 0.050);
    vec3 lit = vec3(0.20, 0.21, 0.25) + tone * 0.10;
    vec3 haze = mix(vec3(0.10, 0.105, 0.125), tone * 0.35, 0.25);
    if (d.y < 0.0) {
        return mix(haze * 0.7, shadow * 0.5, smoothstep(0.0, 0.25, -d.y));
    }
    vec2 p = d.xz / max(d.y, 0.12) * 1.4;
    vec3 q = vec3(p + vec2(t * 0.045, t * 0.015), t * 0.025);
    vec3 warp = vec3(fbm3(q * 0.55), fbm3(q * 0.55 + 5.2), 0.0) - 0.5;
    float density = fbm3(q * 0.85 + warp * 2.2);
    float detail = fbm3(q * 3.2 + warp);
    float cloud = smoothstep(0.30, 0.62, density);
    float thin = 1.0 - smoothstep(0.45, 0.8, density);
    vec3 colour = mix(haze * 0.6, mix(shadow, lit, thin * (0.55 + 0.45 * detail)), cloud);
    float slot = floor(t / 4.2);
    float h = hash21(vec2(slot, 7.3));
    float local = t - slot * 4.2 - h * 2.5;
    float flicker = local > 0.0 ? exp(-local * 7.0) + 0.6 * exp(-max(local - 0.18, 0.0) * 9.0) * step(0.18, local) : 0.0;
    flicker *= step(0.2, h);
    vec3 at = normalize(vec3(sin(h * 40.0), 0.35 + 0.4 * fract(h * 13.7), cos(h * 40.0)));
    float near = exp(-pow(length(d - at) * 2.0, 2.0));
    colour += mix(vec3(1.0), flashTone, 0.5) * flicker * near * (0.35 + 1.6 * cloud * (0.4 + thin));
    colour += flashTone * flicker * 0.08 * cloud;
    float horizon = 1.0 - smoothstep(0.0, 0.3, d.y);
    return mix(colour, haze, horizon);
}

vec3 nebulaSky(vec3 d, float t, vec3 a, vec3 b) {
    float turn = t * 0.004;
    mat2 rot = mat2(cos(turn), -sin(turn), sin(turn), cos(turn));
    vec3 q = vec3(rot * d.xz, d.y) * 2.2;
    vec3 warp = vec3(fbm3lite(q + t * 0.01), fbm3lite(q + 4.1 - t * 0.008), fbm3lite(q + 8.3)) - 0.5;
    float gas = fbm3(q * 1.4 + warp * 2.5);
    float veil = fbm3lite(q * 3.1 - warp * 1.5 + 11.0);
    float dust = smoothstep(0.5, 0.75, fbm3lite(q * 2.3 + 20.0));
    vec3 mid = mix(a, b, 0.5) * vec3(1.0, 0.75, 1.1);
    vec3 colour = vec3(0.006, 0.007, 0.016);
    colour += a * pow(smoothstep(0.30, 0.80, gas), 1.5) * 0.75;
    colour += b * pow(smoothstep(0.40, 0.85, veil), 1.8) * 0.50;
    colour += mid * pow(smoothstep(0.50, 0.90, gas * veil * 1.6), 2.0) * 0.55;
    colour += mix(a, vec3(1.0), 0.5) * pow(smoothstep(0.62, 0.9, gas), 3.0) * 0.6;
    colour *= 1.0 - 0.75 * dust;
    return colour;
}

float worley(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    float best = 8.0;
    for (int z = -1; z <= 1; z++) {
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                vec3 g = vec3(float(x), float(y), float(z));
                vec3 r = g + hash33(i + g) - f;
                best = min(best, dot(r, r));
            }
        }
    }
    return sqrt(best);
}

float isoline(float n, float width) {
    float w = max(width, fwidth(n) * 1.2);
    return 1.0 - smoothstep(0.0, w, abs(n - 0.5));
}

vec3 tendrilSky(vec3 d, float t, vec3 a, vec3 b, bool themed) {
    vec3 deep = themed ? a * 0.06 : vec3(0.075, 0.035, 0.12);
    vec3 glow = themed ? a : vec3(0.62, 0.45, 1.0);
    vec3 hot = themed ? mix(b, vec3(1.0), 0.4) : vec3(0.93, 0.86, 1.0);
    vec3 q = d * 2.1;
    vec3 warp = vec3(fbm3lite(q + vec3(0.0, 0.0, t * 0.020)), fbm3lite(q + vec3(5.2, 1.3, -t * 0.017)),
            fbm3lite(q + vec3(9.1, 4.7, t * 0.013))) - 0.5;
    vec3 p = q + warp * 1.7;
    float cell = worley(p * 1.15 + vec3(0.0, t * 0.025, 0.0));
    float body = pow(smoothstep(0.8, 0.05, cell), 1.2);
    float core = pow(smoothstep(0.45, 0.0, cell), 2.0);
    float haze = fbm3lite(p * 0.8 - t * 0.01);
    vec3 colour = deep + glow * 0.10 * haze;
    colour = mix(colour, glow * (0.55 + 0.45 * haze), body);
    colour += hot * core * 0.55;
    float strands = 0.0;
    float shade = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec3 axis = normalize(vec3(sin(fi * 2.1), cos(fi * 1.3) * 0.6, cos(fi * 2.1)));
        vec3 r = p * (1.5 + fi * 0.9) + warp * (2.2 + fi * 0.8) + vec3(fi * 7.1, fi * 3.3, t * (0.02 + fi * 0.008));
        vec3 s = r + axis * dot(r, axis) * (0.12 - 1.0);
        s *= 1.3;
        float n = valueNoise3(s) * 0.7 + valueNoise3(s * 2.07 + 11.0) * 0.3;
        float line = isoline(n, 0.013 + 0.004 * fi);
        strands = max(strands, line * (1.0 - fi * 0.12));
        shade = max(shade, isoline(n, 0.06 + 0.01 * fi) * 0.25);
    }
    colour = mix(colour, deep * 0.7, max(strands * 0.92, shade));
    colour *= 0.75 + 0.25 * smoothstep(0.9, 0.2, cell);
    return colour;
}

vec3 silkSky(vec3 d, float t, vec3 a, vec3 b, bool themed) {
    vec3 q = d * 1.5;
    vec3 w1 = vec3(fbm3lite(q + vec3(0.0, 0.0, t * 0.03)), fbm3lite(q + vec3(5.2, 1.3, -t * 0.02)), 0.0);
    vec3 w2 = vec3(fbm3lite(q + 3.5 * w1 + vec3(1.7, 9.2, t * 0.025)), fbm3lite(q + 3.5 * w1 + vec3(8.3, 2.8, 0.0)), 0.0);
    float f = fbm3(q + 3.5 * w2);
    vec3 night = themed ? a * 0.05 : vec3(0.035, 0.03, 0.09);
    vec3 cloth = themed ? a * 0.55 : vec3(0.36, 0.12, 0.52);
    vec3 sheen = themed ? b : vec3(0.98, 0.58, 0.42);
    vec3 colour = mix(night, cloth, smoothstep(0.25, 0.75, f));
    colour = mix(colour, sheen * 0.7, smoothstep(0.55, 1.0, length(w2)) * 0.7);
    float fold = sin(f * 22.0 + w2.x * 7.0 + t * 0.15) * 0.5 + 0.5;
    colour += mix(sheen, vec3(1.0), 0.5) * pow(fold, 14.0) * 0.45 * smoothstep(0.35, 0.75, f);
    colour += vec3(0.05, 0.10, 0.20) * smoothstep(0.4, 0.9, w1.x) * f;
    return colour;
}

const float VOID_DIST = 26.0;
const float VOID_IN = 2.9;
const float VOID_OUT = 11.5;
const float VOID_TRACE = 13.5;
const float TAU = 6.2831853;

float pnoise(vec2 p, float period) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float x0 = mod(i.x, period);
    float x1 = mod(i.x + 1.0, period);
    return mix(mix(hash21(vec2(x0, i.y)), hash21(vec2(x1, i.y)), u.x),
               mix(hash21(vec2(x0, i.y + 1.0)), hash21(vec2(x1, i.y + 1.0)), u.x), u.y);
}

float pfbm(vec2 p, float period) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * pnoise(p, period);
        p = p * 2.0 + vec2(0.0, 13.7);
        period *= 2.0;
        a *= 0.5;
    }
    return s;
}

float voidGas(float r, float phi, float seed) {
    float around = phi / TAU;
    float n = pfbm(vec2(around * 22.0, r * 3.1 + seed), 22.0);
    float streak = pnoise(vec2(around * 72.0, r * 13.0 + seed * 3.0), 72.0);
    return n * 0.78 + streak * 0.32;
}

vec4 voidDisk(vec3 p, float r, vec3 dir, float t, vec3 deep, vec3 warm, vec3 white) {
    float phi = atan(p.z, p.x);
    float omega = 1.9 * pow(r, -1.5);
    float cycle = 14.0;
    float ca = fract(t / cycle);
    float cb = fract(t / cycle + 0.5);
    float wa = 1.0 - abs(2.0 * ca - 1.0);
    float n = mix(voidGas(r, phi - omega * cb * cycle, 7.31), voidGas(r, phi - omega * ca * cycle, 0.0), wa);
    float inner = smoothstep(VOID_IN, VOID_IN + 0.45, r);
    float outer = 1.0 - smoothstep(VOID_OUT * 0.42, VOID_OUT, r);
    float fall = pow(VOID_IN / r, 2.1);
    float heat = clamp(pow(VOID_IN / r, 0.9), 0.0, 1.0);
    vec3 c = mix(deep, warm, smoothstep(0.22, 0.62, heat));
    c = mix(c, white, smoothstep(0.64, 1.0, heat));
    vec3 v = vec3(-p.z, 0.0, p.x) / r;
    float beta = min(0.62, sqrt(0.5 / max(r - 1.0, 0.1)));
    float gamma = inversesqrt(1.0 - beta * beta);
    float g = 1.0 / (gamma * (1.0 - beta * dot(v, -dir)));
    float boost = mix(1.0, g * g * g, 0.42);
    c = mix(c, c * vec3(0.92, 0.98, 1.12), clamp((g - 1.0) * 1.5, 0.0, 0.5));
    float well = sqrt(max(0.0, 1.0 - 1.0 / r));
    float density = inner * outer * (0.3 + 0.95 * n);
    float alpha = clamp(density * (0.45 + 0.7 * fall), 0.0, 1.0);
    vec3 light = c * (fall * 3.4 + 0.22) * (0.4 + 1.15 * n) * boost * well;
    return vec4(light, alpha);
}

float voidBend(float theta, float dist) {
    float b = max(dist * sin(theta), 1e-3);
    return (2.0 / b + 2.945 / (b * b)) * 0.5 * (1.0 + cos(theta));
}
vec3 shoulder(vec3 x, float k) {
    vec3 over = max(x - k, 0.0);
    return min(x, vec3(k)) + (1.0 - k) * (1.0 - exp(-over / (1.0 - k)));
}
// anim > 0 is the Black Hole style: the same hole with a faster-swirling disk, a slow wobble of the disk's tilt and a
// gently breathing glow. anim = 0 is the still Void style.
vec3 voidSky(vec3 d, float t, float twinkle, vec3 a, vec3 b, bool themed, float anim) {
    vec3 hole = normalize(vec3(0.38, 0.52, -0.76));
    float wobble = anim * 0.07 * sin(t * 0.21);
    float tip = anim * 0.035 * sin(t * 0.13 + 1.7);
    float breathe = 1.0 + anim * 0.14 * sin(t * 0.9);
    float swirl = t * (1.0 + anim * 5.0);
    // Black Hole views the hole from half the distance, so it fills much more of the sky. The precisely traced region
    // grows with it; its edge (where tracing hands over to the bending approximation) stays inside the view.
    float dist = mix(VOID_DIST, 13.0, anim);
    float trace = min(VOID_TRACE, dist * 0.9);

    vec3 u0 = normalize(cross(hole, vec3(0.0, 1.0, 0.0)));
    vec3 v0 = cross(u0, hole);
    float ROLL = -0.21 + wobble;
    vec3 u = u0 * cos(ROLL) + v0 * sin(ROLL);
    vec3 v = cross(u, hole);
    float ELEVATION = 0.12 + tip;
    vec3 fwd = vec3(0.0, -sin(ELEVATION), cos(ELEVATION));
    vec3 up = vec3(0.0, cos(ELEVATION), sin(ELEVATION));
    float cx = dot(d, u);
    float cy = dot(d, v);
    float cz = dot(d, hole);
    float theta = acos(clamp(cz, -1.0, 1.0));
    float impact = dist * sin(theta);
    vec3 off = d - hole * cz;
    float offLen = length(off);
    vec3 side = offLen > 1e-6 ? off / offLen : u;
    float source = theta - voidBend(theta, dist);
    vec3 deep = themed ? a * 0.75 : vec3(0.78, 0.2, 0.035);
    vec3 warm = themed ? mix(a, b, 0.5) : vec3(1.0, 0.6, 0.24);
    vec3 white = themed ? mix(b, vec3(1.0), 0.6) : vec3(1.0, 0.93, 0.83);
    vec3 disk = vec3(0.0);
    float cover = 0.0;
    float haze = 0.0;
    bool captured = false;
    if (cz > 0.0 && impact < trace) {
        vec3 pos = -fwd * dist;
        vec3 vel = cx * vec3(1.0, 0.0, 0.0) + cy * up + cz * fwd;
        vec3 hv = cross(pos, vel);
        float h2 = dot(hv, hv);
        for (int i = 0; i < 220; i++) {
            float r = length(pos);
            bool away = r > VOID_OUT + 1.0 && dot(pos, vel) > 0.0;
            float dt = clamp(r * (away ? 0.3 : 0.07), 0.02, away ? 30.0 : 2.0);
            vec3 prev = pos;
            float r2 = r * r;
            vel += -1.5 * h2 * pos / (r2 * r2 * r) * dt * 0.5;
            pos += vel * dt;
            float q = length(pos);
            float q2 = q * q;
            vel += -1.5 * h2 * pos / (q2 * q2 * q) * dt * 0.5;
            if (!away && prev.y * pos.y < 0.0) {
                vec3 hit = mix(prev, pos, prev.y / (prev.y - pos.y));
                float hr = length(hit.xz);
                if (hr > VOID_IN && hr < VOID_OUT) {
                    vec4 dk = voidDisk(hit, hr, normalize(vel), swirl, deep, warm, white);
                    disk += (1.0 - cover) * dk.rgb * dk.a;
                    cover += (1.0 - cover) * dk.a;
                }
            }
            float rr = length(pos.xz);
            float shell = smoothstep(VOID_IN * 0.85, VOID_IN + 1.2, rr) * (1.0 - smoothstep(4.5, VOID_OUT, rr));
            haze += away ? 0.0 : (1.0 - cover) * exp(-abs(pos.y) * 2.4) * shell * pow(VOID_IN / max(rr, VOID_IN), 1.6) * dt;
            if (q < 1.0) {
                captured = true;
                break;
            }
            if (cover > 0.985 || q > 400.0 && dot(pos, vel) > 0.0) {
                break;
            }
        }
        if (!captured) {
            vec3 dir = normalize(vel);
            float along = dot(pos, dir);
            vec3 toHole = -(pos - dir * along);
            float bLeft = length(toHole);
            if (bLeft > 1e-4) {
                float rest = (1.0 / bLeft) * (1.0 - along / length(pos));
                dir = normalize(dir * cos(rest) + toHole / bLeft * sin(rest));
            }
            vec3 world = dir.x * u + dot(dir, up) * v + dot(dir, fwd) * hole;
            float traced = atan(dot(world, side), dot(world, hole));
            source = mix(traced, source, smoothstep(trace - 2.5, trace, impact));
        }
    }
    vec3 dl = hole * cos(source) + side * sin(source);
    float stretch = length(fwidth(dl)) / max(length(fwidth(d)), 1e-6);
    vec3 behind = vec3(0.004, 0.004, 0.009) + starField(dl, t, twinkle) * 1.15 * inversesqrt(max(stretch, 1.0))
            + milkyWay(dl, vec3(0.45, 0.5, 0.8), vec3(0.95, 0.82, 0.68), 0.06);
    vec3 colour = captured ? vec3(0.0) : behind * (1.0 - cover);
    colour += disk * breathe + vec3(1.0, 0.62, 0.3) * mix(vec3(1.0), warm, themed ? 0.6 : 0.0) * haze * 0.07 * breathe;
    colour += warm * exp(-theta * 5.0) * 0.035 * smoothstep(0.0, 0.2, theta);
    return shoulder(colour, 0.72);
}
vec3 plasmaSky(vec3 d, float t, vec3 a, vec3 b, bool themed) {
    vec3 cyan = themed ? a : vec3(0.28, 0.82, 1.0);
    vec3 violet = themed ? b : vec3(0.62, 0.30, 1.0);
    vec3 q = d * 2.3 + vec3(0.0, t * 0.015, 0.0);
    vec3 warp = vec3(fbm3lite(q + t * 0.03), fbm3lite(q + 3.1 - t * 0.028), fbm3lite(q + 7.7)) - 0.5;
    vec3 p = q + warp * 1.3;
    float gas = fbm3lite(q * 0.7 - t * 0.01);
    vec3 colour = vec3(0.006, 0.006, 0.02) + mix(violet, cyan, gas) * pow(gas, 2.2) * 0.22;
    float amp = 1.0;
    for (int i = 0; i < 4; i++) {
        float n = valueNoise3(p + vec3(0.0, 0.0, t * (0.05 + float(i) * 0.03)));
        float ridge = 1.0 - abs(2.0 * n - 1.0);
        float line = pow(ridge, 14.0);
        float flicker = 0.7 + 0.3 * sin(t * (3.0 + float(i) * 1.7) + n * 20.0);
        colour += mix(cyan, violet, float(i) / 3.0) * line * amp * flicker * 1.1;
        colour += vec3(1.0) * pow(ridge, 60.0) * amp * 0.6;
        p = p * 2.07 + vec3(3.1, 1.7, 5.3);
        amp *= 0.55;
    }
    return colour;
}

// ---- Sunset: warm horizon, low sun, clouds lit from the side.
vec3 sunsetSky(vec3 d, float t) {
    float h = d.y;
    vec3 sun = normalize(vec3(0.85, 0.07, -0.52));
    vec3 top = vec3(0.09, 0.11, 0.30);
    vec3 mid = vec3(0.80, 0.34, 0.42);
    vec3 low = vec3(1.0, 0.60, 0.24);
    vec3 col = h > 0.0
        ? mix(low, mix(mid, top, smoothstep(0.08, 0.65, h)), smoothstep(0.0, 0.14, h))
        : mix(low * 0.55, vec3(0.08, 0.05, 0.08), smoothstep(0.0, 0.3, -h));
    float s = max(dot(d, sun), 0.0);
    col += vec3(1.0, 0.72, 0.42) * pow(s, 8.0) * 0.55 + vec3(1.0, 0.5, 0.3) * pow(s, 2.0) * 0.2;
    col += vec3(1.0, 0.92, 0.75) * smoothstep(0.9993, 0.9997, s) * 2.5;
    if (h > 0.01) {
        vec2 p = d.xz / (h + 0.08) * 0.9 + vec2(t * 0.01, 0.0);
        float n = fbm(p * 1.2) * 0.7 + fbm(p * 2.7 + 3.0) * 0.3;
        float cloud = smoothstep(0.45, 0.75, n) * smoothstep(0.01, 0.15, h) * (1.0 - smoothstep(0.55, 0.9, h));
        vec3 lit = mix(vec3(1.0, 0.52, 0.38), vec3(1.0, 0.86, 0.6), pow(s, 3.0));
        vec3 shade = vec3(0.32, 0.17, 0.30);
        col = mix(col, mix(shade, lit, 0.3 + 0.7 * pow(s, 1.5)), cloud * 0.88);
    }
    return col;
}

// ---- Galaxy: a spiral galaxy hanging in a deep night sky.
vec3 galaxySky(vec3 d, float t) {
    vec3 col = vec3(0.004, 0.005, 0.013);
    vec3 c = normalize(vec3(-0.3, 0.55, -0.78));
    vec3 u = normalize(cross(c, vec3(0.2, 1.0, 0.1)));
    vec3 v = cross(c, u);
    float along = dot(d, c);
    if (along > 0.0) {
        vec2 q = vec2(dot(d, u), dot(d, v)) / along * 2.4;
        q = mat2(0.8, -0.6, 0.6, 0.8) * q;
        q.y *= 2.3;
        float r = length(q);
        float a = atan(q.y, q.x);
        float arms = 0.5 + 0.5 * sin(a * 2.0 - log(r + 0.03) * 4.5 + t * 0.02);
        float dust = fbm(q * 5.0 + 3.0);
        float glow = exp(-r * 1.5) * (0.25 + 0.75 * pow(arms, 2.0)) * (0.55 + 0.7 * dust);
        float lane = smoothstep(0.55, 0.8, fbm(q * 9.0)) * pow(arms, 3.0) * exp(-r * 2.0);
        col += mix(vec3(1.0, 0.75, 0.55), mix(vec3(0.35, 0.45, 1.0), vec3(0.9, 0.45, 0.9), smoothstep(0.6, 1.6, r)),
                   smoothstep(0.0, 0.7, r)) * glow * 1.7;
        col *= 1.0 - 0.6 * lane;
        col += vec3(1.0, 0.9, 0.75) * exp(-r * 10.0) * 2.2;
    }
    col += milkyWay(d, vec3(0.22, 0.26, 0.48), vec3(0.75, 0.68, 0.88), 0.22);
    return col;
}

// ---- Synthwave: a striped retro sun on a magenta horizon.
vec3 synthSky(vec3 d, float t) {
    vec3 sun = normalize(vec3(0.0, 0.13, -1.0));
    float h = d.y;
    vec3 top = vec3(0.035, 0.01, 0.11), mid = vec3(0.42, 0.05, 0.44), low = vec3(1.0, 0.34, 0.45);
    vec3 col = mix(low, mix(mid, top, smoothstep(0.1, 0.6, h)), smoothstep(-0.02, 0.2, h));
    float ang = acos(clamp(dot(d, sun), -1.0, 1.0));
    float rad = 0.24;
    if (ang < rad) {
        float yy = (d.y - sun.y) / rad;
        float gap = yy < 0.2 ? step(0.0, sin(yy * 34.0 - t * 1.2) + (yy + 1.0) * 0.6 - 0.6) : 1.0;
        vec3 sc = mix(vec3(1.0, 0.18, 0.55), vec3(1.0, 0.88, 0.32), smoothstep(-1.0, 0.9, yy));
        col = mix(col, sc, gap * smoothstep(rad, rad * 0.985, ang));
    }
    col += vec3(1.0, 0.3, 0.6) * exp(-ang * 3.5) * 0.3;
    float line = exp(-pow(h / 0.004, 2.0));
    col += vec3(1.0, 0.4, 0.9) * line * 0.8;
    return col;
}

// ---- Eclipse: a black sun with a bright corona and a diamond-ring flash.
vec3 eclipseSky(vec3 d, float t) {
    vec3 sun = normalize(vec3(0.4, 0.42, -0.8));
    vec3 tx = normalize(cross(sun, vec3(0.0, 1.0, 0.0)));
    vec3 ty = cross(tx, sun);
    float a = acos(clamp(dot(d, sun), -1.0, 1.0));
    float r = 0.075;
    vec3 col = mix(vec3(0.06, 0.07, 0.11), vec3(0.007, 0.009, 0.02), smoothstep(0.0, 0.6, d.y));
    float ang = atan(dot(d, ty), dot(d, tx));
    float streaks = 0.55 + 0.45 * fbm(vec2(ang * 4.0, t * 0.04)) + 0.25 * fbm(vec2(ang * 15.0, 1.0));
    if (a > r) {
        float e = a - r;
        col += vec3(1.0, 0.93, 0.82) * exp(-e * 22.0) * streaks * 1.5;
        col += vec3(0.55, 0.65, 1.0) * exp(-e * 5.0) * 0.22;
        col += vec3(1.0, 0.97, 0.9) * exp(-pow(e / 0.003, 2.0)) * 1.8;
    } else {
        col = vec3(0.0);
    }
    vec3 bead = normalize(sun * cos(r) + normalize(tx + ty) * sin(r));
    float b = acos(clamp(dot(d, bead), -1.0, 1.0));
    col += vec3(1.0, 0.96, 0.88) * (exp(-b * 160.0) * 3.0 + exp(-b * 30.0) * 0.3) * (0.85 + 0.15 * sin(t * 2.0));
    return col;
}

// ---- Daydream: a bright summer sky with soft drifting clouds.
vec3 daydreamSky(vec3 d, float t) {
    float h = d.y;
    vec3 top = vec3(0.20, 0.43, 0.93), hor = vec3(0.74, 0.86, 1.0);
    vec3 sun = normalize(vec3(-0.5, 0.62, 0.6));
    float s = max(dot(d, sun), 0.0);
    if (h <= 0.0) {
        return mix(hor, vec3(0.52, 0.62, 0.76), smoothstep(0.0, 0.3, -h));
    }
    vec3 col = mix(hor, top, smoothstep(0.0, 0.6, h));
    col += vec3(1.0, 0.95, 0.85) * (smoothstep(0.9994, 0.9997, s) * 3.0 + pow(s, 12.0) * 0.22);
    vec2 p = d.xz / (h + 0.1) * 0.7 + vec2(t * 0.012, t * 0.004);
    float n = fbm(p) * 0.65 + fbm(p * 2.3 + 5.0) * 0.35;
    float cloud = smoothstep(0.47, 0.72, n) * smoothstep(0.0, 0.1, h);
    float shade = fbm(p * 1.3 + 0.2);
    vec3 cc = mix(vec3(0.70, 0.76, 0.87), vec3(1.0), clamp(0.35 + 0.6 * smoothstep(0.3, 0.8, shade) + 0.3 * pow(s, 4.0), 0.0, 1.0));
    return mix(col, cc, cloud * 0.95);
}

// ---- Northern Lights: a cold night with strong aurora curtains and the Milky Way.
vec3 northernSky(vec3 d, float t, float which) {
    vec3 col = mix(vec3(0.02, 0.05, 0.09), vec3(0.003, 0.006, 0.02), smoothstep(0.0, 0.7, d.y));
    col += aurora(d, t, which) * 1.5;
    col += milkyWay(d, vec3(0.18, 0.22, 0.35), vec3(0.6, 0.62, 0.75), 0.35);
    return col;
}

// A topographic map over the whole sky: the theme colour, darker towards the horizon, crossed by fine dark contour lines that
// drift very slowly, with every fifth line a little bolder.
vec3 topoSky(vec3 d, float t, vec3 a, vec3 b) {
    vec3 p = d * 1.7 + vec3(t * 0.006, t * 0.002, -t * 0.004);
    float h = fbm3(p) * 0.75 + fbm3(p * 2.3 + 4.1) * 0.25;
    float v = h * 14.0;
    float f = fract(v);
    float w = fwidth(v);
    float line = 1.0 - smoothstep(0.0, w * 1.3, min(f, 1.0 - f));
    float major = step(4.5, mod(floor(v + 0.5), 5.0));
    float bold = 1.0 - smoothstep(0.0, w * 2.2, min(f, 1.0 - f));
    float up = clamp(d.y, -1.0, 1.0);
    vec3 base = mix(a * 0.42, a * 0.95, smoothstep(-0.25, 0.75, up));
    base = mix(base, mix(a, b, 0.5), 0.18 * smoothstep(0.3, 0.7, h));
    base *= 0.9 + 0.1 * h;
    vec3 ink = a * 0.06;
    float amount = max(line * 0.85, major * bold);
    return mix(base, ink, amount);
}

vec3 cubeFace(vec2 uv, vec2 tile) {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 inset = 0.5 / size * vec2(3.0, 2.0);
    uv = clamp(uv, inset, 1.0 - inset);
    return textureLod(Sampler0, (tile + uv) / vec2(3.0, 2.0), 0.0).rgb;
}
vec3 paintedSky(vec3 d) {
    vec3 a = abs(d);
    if (a.y >= a.x && a.y >= a.z) {
        vec2 uv = vec2(d.x, d.y > 0.0 ? d.z : -d.z) / a.y * 0.5 + 0.5;
        return cubeFace(vec2(uv.x, 1.0 - uv.y), d.y > 0.0 ? vec2(1.0, 1.0) : vec2(2.0, 1.0));
    }
    if (a.z >= a.x) {
        vec2 uv = vec2(d.z < 0.0 ? d.x : -d.x, -d.y) / a.z * 0.5 + 0.5;
        return cubeFace(uv, d.z < 0.0 ? vec2(0.0, 0.0) : vec2(2.0, 0.0));
    }
    vec2 uv = vec2(d.x > 0.0 ? d.z : -d.z, -d.y) / a.x * 0.5 + 0.5;
    return cubeFace(uv, d.x > 0.0 ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
}
vec3 pictureSky(vec3 d, float seconds, float twinkle, bool drawStars) {
    vec2 uv = vec2(atan(d.x, -d.z) / (2.0 * PI) + 0.5, acos(clamp(d.y, -1.0, 1.0)) / PI);

    vec2 dx = dFdx(uv);
    vec2 dy = dFdy(uv);
    if (abs(dx.x) > 0.5) dx.x -= sign(dx.x);
    if (abs(dy.x) > 0.5) dy.x -= sign(dy.x);
    vec4 picture = textureGrad(Sampler0, uv, dx, dy);
    vec3 colour = picture.rgb;
    if (drawStars) {
        float open = picture.a * smoothstep(-0.02, 0.06, d.y);
        float haze = smoothstep(0.02, 0.35, d.y);
        colour += starField(d, seconds, twinkle) * open * mix(0.35, 1.0, haze);
    }
    return colour;
}
void main() {
    vec3 d = normalize(direction);
    vec4 setup = TextureMat[1];
    float mode = setup.x;
    float seconds = ModelOffset.x;
    float twinkle = ModelOffset.y;
    vec3 skyA = TextureMat[2].rgb;
    vec3 skyB = TextureMat[3].rgb;
    vec3 colour;
    if (mode < 0.5) {
        colour = pictureSky(d, seconds, twinkle, ModelOffset.z > 0.5);
        colour += aurora(d, seconds, setup.z) * setup.y;
    } else {
        float starAmount = 1.0;
        float auroraAmount = setup.y;
        bool themed = TextureMat[3].w > 0.5;
        if (mode < 3.5) {
            colour = stormSky(d, seconds, skyA, skyB);
            starAmount = 0.0;
            auroraAmount = 0.0;
        } else if (mode < 4.5) {
            colour = nebulaSky(d, seconds, skyA, skyB);
            starAmount = 0.8;
        } else if (mode < 5.5) {
            colour = paintedSky(d);
        } else {
            starAmount = 0.0;
            auroraAmount = 0.0;
            if (mode < 6.5) {
                colour = tendrilSky(d, seconds, skyA, skyB, themed);
            } else if (mode < 7.5) {
                colour = silkSky(d, seconds, skyA, skyB, themed);
            } else if (mode < 8.5) {
                colour = voidSky(d, seconds, twinkle, skyA, skyB, themed, 0.0);
            } else if (mode < 9.5) {
                colour = plasmaSky(d, seconds, skyA, skyB, themed);
            } else if (mode < 10.5) {
                colour = voidSky(d, seconds, twinkle, skyA, skyB, themed, 1.0);
            } else if (mode < 11.5) {
                colour = sunsetSky(d, seconds);
            } else if (mode < 12.5) {
                colour = galaxySky(d, seconds);
                starAmount = 1.0;
            } else if (mode < 13.5) {
                colour = synthSky(d, seconds);
                starAmount = 0.6 * smoothstep(0.15, 0.5, d.y);
            } else if (mode < 14.5) {
                colour = eclipseSky(d, seconds);
                starAmount = 0.6;
            } else if (mode < 15.5) {
                colour = daydreamSky(d, seconds);
            } else if (mode < 16.5) {
                colour = northernSky(d, seconds, setup.z);
                starAmount = 1.0;
            } else {
                colour = topoSky(d, seconds, skyA, skyB);
            }
        }
        if (starAmount > 0.0 || auroraAmount > 0.001 || TextureMat[2].w > 0.5) {
            colour += nightLayers(d, seconds, twinkle, auroraAmount, setup.z, setup.w, TextureMat[2].w, starAmount);
        }
    }
    colour *= ColorModulator.rgb * ColorModulator.a * vertexColor.rgb;
    colour = mix(colour, TextureMat[0].xyz, clamp(TextureMat[0].w, 0.0, 1.0));
    fragColor = vec4(colour, 1.0);
}

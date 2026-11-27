#version 330

// Scene depth as 26.x has it (1 near, 0 for the sky); 1.21.11 stores it the other way round.
//? if >=26 {
#define SCENE_DEPTH(d) (d)
//?} else {
/*#define SCENE_DEPTH(d) (1.0 - (d))
*///?}

// Effects over the finished world that need each pixel's place in it (see WorldPost): puddle reflections traced across
// the screen, shockwave rings rolling over the terrain, anime impact frames around a blast, and the underwater view.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
// Ground height every 2 blocks around the camera (GroundHeights): R = high byte, G = low byte, above the world's bottom,
// 0 = not loaded. The texture wraps: a texel's slot is its coordinate modulo 256.
uniform sampler2D HeightSampler;

layout(std140) uniform PostConfig {
    mat4 ViewProj;      // camera-relative world -> clip
    mat4 InvViewProj;   // clip -> camera-relative world
    vec4 Camera;        // xyz = camera position, w = seconds
    vec4 Wet;           // x = reflection strength (0 = off), y = base reflectance, z = raindrops, w = 1 when clip z is 0..1
    vec4 Impact;        // x = strength (0 = off), y = seed, zw = blast on screen (0..1, y up)
    vec4 ImpactB;       // x = style (0 manga, 1 negative, 2 colour), y = frame, z = aspect
    vec4 ImpactColor;   // rgb = the blast's colour
    vec4 Shock[4];      // xyz = centre relative to the camera, w = radius (< 0 = unused)
    vec4 ShockColor[4]; // rgb = colour, a = strength
    vec4 ShockCfg;      // x = ring width, y = block grid, z = count
    vec4 GroundA;       // x = fog base relative to the camera, y = depth, z = density at the base (0 = off), w = reach
    vec4 GroundB;       // x = wisps, y = drift speed, z = daylight
    vec4 GroundCol;     // rgb = the mist's lit colour
    vec4 SunDir;        // xyz = towards the sun
    vec4 Under;         // x = underwater strength (0 = off), y = caustics, z = daylight
    vec4 GroundMap;     // xy = the height map's lowest texel, z = the world's bottom Y, w = 1 when the fog follows the terrain
    vec4 AtmoA;         // x = start (fraction of reach), y = density, z = opacity (0 = off), w = mist
    vec4 AtmoB;         // x = reach in blocks, y = 1 to tint the sky too
    vec4 AtmoColor[4];  // the gradient's four corners
};

in vec2 texCoord;

out vec4 fragColor;

const float TAU = 6.2831853;

vec3 worldAt(vec2 uv, float depth) {
    float z = Wet.w > 0.5 ? SCENE_DEPTH(depth) : SCENE_DEPTH(depth) * 2.0 - 1.0;
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, z, 1.0);
    return p.xyz / p.w;
}

float hash1(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

// The same raindrop rings the terrain shader draws (wet_ground.fsh), so the reflection ripples with the water.
vec2 raindrops(vec2 p, float t) {
    vec2 total = vec2(0.0);
    for (int layer = 0; layer < 2; layer++) {
        vec2 q = p * (layer == 0 ? 2.2 : 3.4) + float(layer) * 17.3;
        vec2 cell = floor(q);
        vec2 f = fract(q);
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec2 c = cell + vec2(float(i), float(j));
                vec2 h = hash22(c);
                float phase = fract(t * (0.75 + 0.3 * h.y) + h.x * 7.13 + float(layer) * 0.37);
                vec2 d = f - (vec2(float(i), float(j)) + h);
                float r = length(d);
                float x = (r - phase * 0.85) * 16.0;
                float wave = sin(x * 3.14159) * exp(-x * x * 0.5);
                float fade = (1.0 - phase) * (1.0 - phase);
                total += d / max(r, 1e-3) * wave * fade;
            }
        }
    }
    return total * 0.22;
}

// ---- reflections ------------------------------------------------------------------------------------------------

// How reflective the terrain shader marked this pixel: its alpha is 0.25..0.45 there (0.25 = fully), 1 elsewhere.
float wetMark(float a) {
    if (a < 0.235 || a > 0.465) {
        return 0.0;
    }
    return clamp(1.0 - (a - 0.25) / 0.2, 0.0, 1.0);
}

vec3 wetReflection(vec3 scene, vec2 uv, float depth, float mark) {
    vec3 P = worldAt(uv, depth);
    float dist = length(P);
    vec3 V = P / max(dist, 1e-4);
    vec2 tilt = vec2(0.0);
    if (Wet.z > 0.0) {
        float close = 1.0 - smoothstep(18.0, 40.0, dist);
        tilt = raindrops(P.xz + Camera.xz, Camera.w) * Wet.z * close;
    }
    vec3 N = normalize(vec3(tilt.x, 1.0, tilt.y));
    vec3 R = reflect(V, N);
    float fresnel = Wet.y + (1.0 - Wet.y) * pow(1.0 - clamp(dot(N, -V), 0.0, 1.0), 5.0);

    // March the reflected ray across the screen, in growing steps, until it passes behind something drawn.
    float stepLen = max(0.12, dist * 0.015);
    float t = 0.0;
    vec3 hitColor = vec3(0.0);
    float found = 0.0;
    bool wasBehind = false;
    for (int i = 0; i < 64; i++) {
        float prevT = t;
        t += stepLen;
        stepLen = min(stepLen * 1.08, 3.0);
        vec3 Q = P + R * t;
        vec4 c = ViewProj * vec4(Q, 1.0);
        if (c.w <= 0.05) {
            break;
        }
        vec2 s = c.xy / c.w * 0.5 + 0.5;
        if (s.x < 0.0 || s.y < 0.0 || s.x > 1.0 || s.y > 1.0) {
            break;
        }
        float d = SCENE_DEPTH(texture(DepthSampler, s).r);
        if (d <= 0.0) {
            wasBehind = false;
            continue;
        }
        float behind = length(Q) - length(worldAt(s, d));
        // A hit is the ray crossing from in front of a surface to just behind it. Already being behind it last step
        // means the ray is passing behind a nearer object (a trunk, a wall's edge), which it must not "hit".
        bool crossed = behind > 0.0 && !wasBehind && behind < clamp(stepLen * 1.6, 0.35, 2.5);
        wasBehind = behind > 0.0;
        if (crossed) {
            // Narrow it down between the last step in front and this one behind.
            float lo = prevT;
            float hi = t;
            for (int k = 0; k < 6; k++) {
                float mid = (lo + hi) * 0.5;
                vec3 M = P + R * mid;
                vec4 mc = ViewProj * vec4(M, 1.0);
                vec2 ms = mc.xy / mc.w * 0.5 + 0.5;
                float md = SCENE_DEPTH(texture(DepthSampler, ms).r);
                if (md > 0.0 && length(M) > length(worldAt(ms, md))) {
                    hi = mid;
                    s = ms;
                } else {
                    lo = mid;
                }
            }
            vec2 edge = min(s, 1.0 - s);
            found = smoothstep(0.0, 0.08, min(edge.x, edge.y)) * (1.0 - smoothstep(60.0, 120.0, t));
            hitColor = texture(InSampler, s).rgb;
            break;
        }
    }
    if (found <= 0.0 && R.y > 0.0) {
        // Nothing in the way: the reflection is the sky in that direction, if that part of the sky is on screen.
        // Taking it from the direction itself (not wherever the march stopped) keeps it smooth, without speckle.
        vec4 c = ViewProj * vec4(R * 4000.0, 1.0);
        if (c.w > 0.05) {
            vec2 s = c.xy / c.w * 0.5 + 0.5;
            if (s.x >= 0.0 && s.y >= 0.0 && s.x <= 1.0 && s.y <= 1.0 && SCENE_DEPTH(texture(DepthSampler, s).r) <= 0.0) {
                vec2 edge = min(s, 1.0 - s);
                found = smoothstep(0.0, 0.06, min(edge.x, edge.y)) * 0.85;
                hitColor = texture(InSampler, s).rgb;
            }
        }
    }
    float amount = clamp(fresnel * mark * Wet.x * found, 0.0, 0.95);
    // Water darkens what it reflects a little: a mirror of the world, not a copy of it.
    return mix(scene, hitColor * 0.92, amount);
}

// ---- ground fog ---------------------------------------------------------------------------------------------------

float hash3(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float valueNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(i), hash3(i + vec3(1, 0, 0)), u.x), mix(hash3(i + vec3(0, 1, 0)), hash3(i + vec3(1, 1, 0)), u.x), u.y),
               mix(mix(hash3(i + vec3(0, 0, 1)), hash3(i + vec3(1, 0, 1)), u.x), mix(hash3(i + vec3(0, 1, 1)), hash3(i + vec3(1, 1, 1)), u.x), u.y), u.z);
}

// Mist banks: big slow shapes with finer curls on top, drifting with the wind.
float mist(vec3 w, float t) {
    vec3 wind = vec3(1.0, 0.0, 0.35) * GroundB.y * t;
    vec3 q = (w - wind) * vec3(0.045, 0.11, 0.045);
    float n = valueNoise3(q) * 0.6 + valueNoise3(q * 2.3 + vec3(5.2, 1.3, 7.7) + wind * 0.01) * 0.3 + valueNoise3(q * 5.1 + 3.1) * 0.1;
    return n;
}

// The ground's height (world Y) in one height map texel, or a large negative number where it is unknown.
float groundTexel(vec2 t) {
    if (any(lessThan(t, GroundMap.xy)) || any(greaterThanEqual(t, GroundMap.xy + 256.0))) {
        return -1e9;
    }
    vec4 h = texelFetch(HeightSampler, ivec2(mod(t, 256.0)), 0);
    float raw = floor(h.r * 255.0 + 0.5) * 256.0 + floor(h.g * 255.0 + 0.5);
    return raw < 0.5 ? -1e9 : raw + GroundMap.z;
}

// The ground's height under a world position: the lowest of the four nearest texels. Blending them would put fog in
// the air beside a cliff at half the cliff's height; the lowest keeps it on the ground below, and the cliff face hides
// anything under its top anyway.
float groundAt(vec2 xz) {
    vec2 b = floor(xz / 2.0 - 0.5);
    float h00 = groundTexel(b);
    float h10 = groundTexel(b + vec2(1.0, 0.0));
    float h01 = groundTexel(b + vec2(0.0, 1.0));
    float h11 = groundTexel(b + vec2(1.0, 1.0));
    float low = min(min(h00, h10), min(h01, h11));
    return low < -1e8 ? -1e9 : low;
}

float atmoHash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float atmoNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(atmoHash(i), atmoHash(i + vec2(1.0, 0.0)), u.x),
               mix(atmoHash(i + vec2(0.0, 1.0)), atmoHash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float atmoFbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * atmoNoise(p);
        p = p * 2.03 + 11.7;
        a *= 0.5;
    }
    return v;
}

// Thick tinted atmosphere (after x.monolith's fog): a four-colour gradient across the screen, thickening with distance,
// broken up by drifting mist, and laid over the sky.
vec3 atmosphere(vec3 col, vec2 uv, float depth) {
    float t = Camera.w;
    bool sky = depth <= 0.0;
    vec3 P = worldAt(uv, sky ? 1e-7 : depth);
    float dist = length(P);
    vec3 dir = P / max(dist, 1e-4);
    vec3 wp = P + Camera.xyz;
    vec2 drift = vec2(t * 0.35, -t * 0.22);
    float mist = AtmoA.w;
    float n = atmoFbm(wp.xz * 0.045 + drift * 0.1);
    float n2 = atmoFbm(wp.xz * 0.11 - drift * 0.1 + 7.3);
    float cloud = mix(1.0, 0.45 + n * 1.1, mist);
    vec3 fogColor = mix(mix(AtmoColor[0].rgb, AtmoColor[1].rgb, uv.y), mix(AtmoColor[2].rgb, AtmoColor[3].rgb, uv.y), uv.x);
    fogColor *= 0.88 + 0.24 * mix(0.5, n2, mist);
    fogColor += (fogColor * 0.35 + 0.04) * smoothstep(0.1, 0.9, 1.0 - abs(dir.y));
    float amount;
    if (sky) {
        float sheet = atmoFbm(dir.xz / (abs(dir.y) + 0.35) * 1.4 + drift * 0.05);
        amount = AtmoB.y > 0.5 ? AtmoA.z * (0.9 - 0.18 * mist * sheet) : 0.0;
    } else {
        float x = clamp((dist / max(AtmoB.x, 1.0) - AtmoA.x) / max(1.0 - AtmoA.x, 0.001), 0.0, 1.0);
        float low = 1.0 + 0.5 * clamp(-P.y / 40.0, -1.0, 1.0);
        float k = x * AtmoA.y * 6.0 * cloud * low;
        amount = (1.0 - exp(-pow(k, 1.5))) * AtmoA.z;
    }
    return mix(col, fogColor, clamp(amount, 0.0, 1.0));
}

vec3 groundFog(vec3 col, vec2 uv, float depth) {
    // Pixels on the sky look out to the fog's reach; everything else to its own distance.
    vec3 P = worldAt(uv, depth > 0.0 ? depth : 1e-7);
    float sceneDist = length(P);
    vec3 V = P / max(sceneDist, 1e-4);
    float L = depth > 0.0 ? min(sceneDist, GroundA.w) : GroundA.w;
    float H = max(GroundA.y, 0.1);
    // The ray's lowest point: if even that is well above the layer, there is no fog on it.
    float lowest = min(0.0, V.y * L);
    if (lowest - GroundA.x > H * 1.6 || L <= 0.0) {
        return col;
    }
    // Jittered samples, packed toward the camera where detail is visible.
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    // On the terrain the layer is thin and follows the ground up and down, so it needs more steps to stay smooth.
    int N = GroundMap.w > 0.5 ? 32 : 18;
    float tau = 0.0;
    float prevT = 0.0;
    for (int i = 0; i < N; i++) {
        float k = (float(i) + jitter) / float(N);
        float t = L * k * k;
        float dt = t - prevT;
        prevT = t;
        vec3 w = Camera.xyz + V * t;
        // Height above the fog's base: the ground under this point, or the one fixed height.
        float h;
        float under = 1.0;
        if (GroundMap.w > 0.5) {
            float g = groundAt(w.xz);
            if (g < -1e8) {
                // Past the edge of the height map: the fog at its fixed height, as with the map off. Skipping these
                // samples instead left the map's ragged edge drawn across the ground as rows of dashes.
                h = V.y * t - GroundA.x;
            } else {
                h = w.y - g;
                // Only on the surface: none in caves or under roofs, which sit below the ground the height map sees.
                under = smoothstep(-1.5, 0.0, h);
            }
        } else {
            h = V.y * t - GroundA.x;
        }
        if (h > H * 1.6 || under <= 0.0) {
            continue;
        }
        float n = mist(w, Camera.w);
        // The layer has a top: dense on the ground, thinning fast toward Fog depth, and that top surface billows
        // up and down with the mist banks, so it lies in hollows and heaps up in drifts.
        float top = H * (1.0 + (n - 0.5) * 1.1 * GroundB.x);
        float fill = 1.0 - smoothstep(-0.25 * H, top, h);
        float dens = GroundA.z * fill * fill * under;
        if (dens < 1e-4) {
            continue;
        }
        // Wisps inside the layer: thick banks and thin gaps.
        float banks = smoothstep(0.25, 0.7, n);
        dens *= mix(1.0, 0.35 + banks * 1.3, GroundB.x);
        // Fade out toward the reach, so the layer has no hard far edge.
        dens *= 1.0 - smoothstep(GroundA.w * 0.75, GroundA.w, t);
        tau += dens * dt;
    }
    float T = exp(-tau);
    // Light: the mist's colour, glowing warm where you look toward the sun through it.
    float toward = max(dot(V, normalize(SunDir.xyz)), 0.0);
    float glow = (pow(toward, 6.0) * 0.55 + pow(toward, 40.0) * 0.6) * GroundB.z;
    vec3 light = GroundCol.rgb + vec3(1.0, 0.86, 0.66) * glow;
    return col * T + light * (1.0 - T);
}

// ---- underwater -------------------------------------------------------------------------------------------------

// A web of bright lines, as light focused by a rippling surface draws on whatever is below it: the edges between
// cells of a moving cellular pattern, two layers drifting apart so the web keeps changing shape.
float causticLayer(vec2 p, float t) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    float d1 = 8.0;
    float d2 = 8.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 c = vec2(float(i), float(j));
            vec2 h = hash22(cell + c);
            vec2 o = 0.5 + 0.4 * sin(t * (0.6 + 0.5 * h) + h * 6.2831);
            float d = length(c + o - f);
            if (d < d1) {
                d2 = d1;
                d1 = d;
            } else if (d < d2) {
                d2 = d;
            }
        }
    }
    return 1.0 - smoothstep(0.0, 0.16, d2 - d1);
}

float caustics(vec3 w, float t) {
    // Projected along the sunlight, so the pattern lands on walls as well as the floor.
    vec2 p = w.xz - SunDir.xz / max(SunDir.y, 0.25) * w.y;
    float a = causticLayer(p * 0.55, t * 0.9);
    float b = causticLayer(p * 0.55 * 1.37 + 3.1, -t * 0.7);
    return a * b;
}

vec3 underwaterView(vec3 col, vec2 uv, float depth) {
    vec3 P = worldAt(uv, depth > 0.0 ? depth : 1e-7);
    float dist = depth > 0.0 ? length(P) : 64.0;
    vec3 V = P / max(length(P), 1e-4);
    float light = Under.z * 0.85 + 0.15;
    // Water swallows red first, then green: what is far away turns blue-green.
    vec3 absorb = exp(-vec3(0.16, 0.055, 0.04) * min(dist, 48.0) * 0.6);
    vec3 deep = vec3(0.04, 0.2, 0.26) * light;
    col = mix(deep, col, mix(1.0, absorb.g, Under.x * 0.6)) * mix(vec3(1.0), absorb / max(absorb.g, 1e-3), Under.x * 0.35);
    if (Under.y > 0.5 && depth > 0.0) {
        // Caustics on what is seen, strongest on upward faces and close by.
        vec3 dx = dFdx(P);
        vec3 dy = dFdy(P);
        vec3 N = normalize(cross(dy, dx));
        float up = clamp(N.y * 0.75 + 0.35, 0.0, 1.0);
        float c = caustics(P + Camera.xyz, Camera.w);
        col += col * c * up * light * 1.4 * Under.x * exp(-dist * 0.05);
    }
    // Shafts of sunlight: slanted columns of brighter water, sampled along the view ray.
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    vec2 slant = SunDir.xz / max(SunDir.y, 0.3);
    float shafts = 0.0;
    float L = min(dist, 32.0);
    for (int i = 0; i < 8; i++) {
        float t = L * (float(i) + jitter) / 8.0;
        vec3 w = Camera.xyz + V * t;
        vec2 q = (w.xz - slant * w.y) * 0.22;
        float n = valueNoise3(vec3(q, Camera.w * 0.15));
        shafts += smoothstep(0.55, 0.85, n) * exp(-t * 0.06);
    }
    shafts /= 8.0;
    float toward = 0.5 + 0.5 * max(dot(V, normalize(SunDir.xyz)), 0.0);
    col += vec3(0.55, 0.85, 0.9) * shafts * toward * Under.z * Under.x * 0.7;
    return col;
}

// ---- shockwaves --------------------------------------------------------------------------------------------------

vec3 shockwaves(vec3 col, vec2 uv, float depth) {
    if (depth <= 0.0) {
        return col;
    }
    vec3 P = worldAt(uv, depth);
    for (int i = 0; i < 4; i++) {
        if (float(i) >= ShockCfg.z || Shock[i].w < 0.0) {
            break;
        }
        // The crest broadens as it travels, so it still reads as a wave out at the edge of the world.
        float w = max(ShockCfg.x, 0.2) * (1.0 + Shock[i].w / 48.0);
        float strength = ShockColor[i].a;
        float band = length(P - Shock[i].xyz) - Shock[i].w;
        // A bright crest at the front, a soft wash in its wake, nothing ahead of it.
        float crest = exp(-band * band / (w * w));
        float wake = band < 0.0 ? exp(band / (w * 5.0)) * 0.3 : 0.0;
        float glow = (crest + wake) * strength;
        if (glow < 0.002) {
            continue;
        }
        vec3 c = ShockColor[i].rgb;
        if (ShockCfg.y > 0.5) {
            // Block edges light up as the crest passes, so the ring reads as a scan over the world.
            vec3 world = P + Camera.xyz;
            vec3 f = abs(fract(world + 0.5) - 0.5);
            vec3 e = smoothstep(vec3(0.035), vec3(0.0), f);
            float lines = clamp(e.x + e.y + e.z - 1.0, 0.0, 1.0);
            glow += lines * crest * strength * 1.6;
        }
        // Screen-blend so dark ground lights up and bright sky does not blow out.
        vec3 add = c * glow;
        col = 1.0 - (1.0 - col) * (1.0 - clamp(add, 0.0, 1.0));
    }
    return col;
}

// ---- impact frames ------------------------------------------------------------------------------------------------

// Smooth noise around a circle with n cells: the jagged outline of a burst.
float ringNoise(float a, float seed, float n) {
    float x = (a / TAU + 0.5) * n;
    float i = floor(x);
    float f = fract(x);
    float h0 = hash1(mod(i, n) + seed * 31.7);
    float h1 = hash1(mod(i + 1.0, n) + seed * 31.7);
    return mix(h0, h1, f * f * (3.0 - 2.0 * f));
}

vec3 impactFrame(vec3 scene, vec2 uv) {
    float frame = ImpactB.y;
    float seed = Impact.y + frame * 7.31;
    vec2 d = (uv - Impact.zw) * vec2(ImpactB.z, 1.0);
    float r = length(d);
    float a = atan(d.y, d.x);
    // Hard two-tone: the scene thresholded into ink and paper.
    float l = dot(scene, vec3(0.299, 0.587, 0.114));
    float paperMask = smoothstep(0.30, 0.36, l);

    // The burst: a star of uneven spikes, rolled fresh each frame so it boils.
    float spikes = pow(ringNoise(a, seed, 19.0), 3.0) * 0.9 + ringNoise(a, seed + 4.0, 53.0) * 0.25;
    float burstR = (0.07 + 0.05 * hash1(seed)) * (0.7 + spikes);
    float burst = 1.0 - smoothstep(burstR, burstR + 0.004, r);
    // Speed lines rushing into the blast: thin wedges, sparse near it and thick toward the edges.
    float bins = 220.0;
    float bin = floor((a / TAU + 0.5) * bins);
    float pick = hash1(bin + seed * 13.1);
    float across = fract((a / TAU + 0.5) * bins) - 0.5;
    float lineW = 0.08 + 0.42 * smoothstep(0.1, 0.9, r);
    float lineStart = burstR + 0.05 + 0.25 * hash1(bin * 3.1 + seed);
    float lines = step(0.78, pick) * step(abs(across), lineW) * smoothstep(lineStart, lineStart + 0.08, r);
    // Cracks of light: a few bolts out of the burst, each a chain of straight segments that kink at random.
    float bolts = 0.0;
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float base = hash1(fi * 5.7 + seed) * TAU - 3.14159;
        float seg = r * 14.0;
        float k = floor(seg);
        float off0 = (hash1(k + fi * 9.1 + seed) - 0.5) * (k < 0.5 ? 0.0 : 0.05);
        float off1 = (hash1(k + 1.0 + fi * 9.1 + seed) - 0.5) * 0.05;
        float side = mix(off0, off1, fract(seg));
        // Distance from the bolt, across it, in screen units: the angle off its heading times the radius, minus the kink.
        float da = abs((mod(a - base + 3.14159, TAU) - 3.14159) * r - side);
        float reach = 0.25 + 0.5 * hash1(fi * 2.3 + seed);
        float width = mix(0.004, 0.0012, clamp(r / reach, 0.0, 1.0));
        bolts += (1.0 - smoothstep(width * 0.5, width, da)) * step(burstR, r) * (1.0 - smoothstep(reach, reach + 0.05, r));
    }
    bolts = clamp(bolts, 0.0, 1.0);

    vec3 ink = vec3(0.02);
    vec3 paper = vec3(0.97, 0.96, 0.93);
    if (ImpactB.x > 1.5) {
        paper = mix(ImpactColor.rgb, vec3(1.0), 0.15);
    }
    // Frame by frame, like a cut in an anime: a flash, the inverted negative, then the ink drawing.
    bool negative = mod(frame, 2.0) < 0.5;
    if (ImpactB.x > 0.5 && ImpactB.x < 1.5) {
        negative = !negative;
    }
    float pm = negative ? 1.0 - paperMask : paperMask;
    vec3 col = mix(ink, paper, pm);
    float mark = clamp(lines + bolts, 0.0, 1.0);
    col = mix(col, negative ? paper : ink, mark);
    col = mix(col, negative ? ink : paper, burst);
    if (frame < 0.5) {
        // The first frame is almost all flash: just the burst and its bolts in ink on paper.
        col = mix(paper, ink, clamp(burst + bolts, 0.0, 1.0));
    }
    return mix(scene, col, clamp(Impact.x, 0.0, 1.0));
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    float depth = SCENE_DEPTH(texture(DepthSampler, texCoord).r);
    vec3 col = scene.rgb;
    if (Wet.x > 0.0 && depth > 0.0) {
        float mark = wetMark(scene.a);
        if (mark > 0.0) {
            col = wetReflection(col, texCoord, depth, mark);
        }
    }
    if (Under.x > 0.0) {
        col = underwaterView(col, texCoord, depth);
    }
    if (GroundA.z > 0.0) {
        col = groundFog(col, texCoord, depth);
    }
    if (AtmoA.z > 0.0) {
        col = atmosphere(col, texCoord, depth);
    }
    if (ShockCfg.z > 0.0) {
        col = shockwaves(col, texCoord, depth);
    }
    if (Impact.x > 0.0) {
        col = impactFrame(col, texCoord);
    }
    // The wet marks were only for this pass: the frame leaves fully opaque.
    fragColor = vec4(col, 1.0);
}

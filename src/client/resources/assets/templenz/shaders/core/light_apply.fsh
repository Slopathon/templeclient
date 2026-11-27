#version 330

// Scene depth as 26.x has it (1 near, 0 for the sky); 1.21.11 stores it the other way round.
//? if >=26 {
#define SCENE_DEPTH(d) (d)
//?} else {
/*#define SCENE_DEPTH(d) (1.0 - (d))
*///?}

// Realistic Lighting, second pass (see LightingPass): shades the frame with what light_trace.fsh found. The trace is
// noisy (each pixel nudged its ray differently) and may be at half resolution, so it is smoothed here across
// neighbouring pixels, but only ones at much the same depth, so a shadow never bleeds over an edge.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D LightSampler;

layout(std140) uniform LightConfig {
    mat4 ViewProj;
    mat4 InvViewProj;
    vec4 CamGrid;
    vec4 Origin;
    vec4 SunDir;
    vec4 Amount;
    vec4 Extra;        // yz = one texel of the trace, w = 1 to show the trace itself
    vec4 ShadeTint;    // rgb = what full shade multiplies the frame by, a = the size of the sun's glare (1 = full)
    vec4 SunTint;      // rgb = what full sun multiplies the frame by
    vec4 Air;          // x = light shafts, y = dust, z = bloom (each 0 = off), w = the screen's width over its height
    vec4 SunScreen;    // xy = the sun on screen (0..1, y up), z = 1 when it is in front of the camera, w = shaft samples
    vec4 SunColor;     // rgb = the colour of its light, a = lens flare (0 = off)
    vec4 CamWorld;     // xyz = the camera in the world (wrapped), w = seconds
};

in vec2 texCoord;

out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

vec3 viewRay(vec2 uv) {
    float z = CamGrid.w > 0.5 ? 0.002 : -0.996;
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, z, 1.0);
    return normalize(p.xyz / p.w);
}

float distanceAt(vec2 uv, float depth) {
    float z = CamGrid.w > 0.5 ? SCENE_DEPTH(depth) : SCENE_DEPTH(depth) * 2.0 - 1.0;
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, z, 1.0);
    return length(p.xyz / p.w);
}

float hash2(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1.0, 0.0)), u.x), mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), u.x), u.y);
}

// Whether a point in the world stands in a shaft of sunlight, 0..1. The shafts are columns of light running along
// the sun's direction, eight or ten blocks thick with wide shade between, as under broken cloud: the pattern is laid out on
// the plane facing the sun, so it is the same all the way along a ray of light, and it drifts slowly.
float shaftAt(vec3 world) {
    vec3 L = normalize(SunDir.xyz);
    vec3 T = normalize(cross(L, vec3(0.0, 0.0, 1.0)));
    vec3 B = cross(L, T);
    vec2 c = vec2(dot(world, T), dot(world, B)) * 0.07 + CamWorld.w * vec2(0.006, 0.002);
    float n = valueNoise2(c) * 0.75 + valueNoise2(c * 2.3 + 11.0) * 0.25;
    // Few and far between, a seventh of the sky's worth: a shaft is seen against the shade round it, and several one
    // behind another along a view only add up to haze.
    return smoothstep(0.64, 0.72, n);
}

// Motes of dust: specks far too small to see until a shaft of light picks them out, thousands of them, hanging
// almost still. Each is a point in the world on a fine grid, sampled at many depths close to the eye, and each
// shows only as far as it stands in a shaft. Returns how brightly they show along this line of sight.
float dust(vec3 V, float far) {
    float total = 0.0;
    float t = CamWorld.w;
    for (int layer = 0; layer < 12; layer++) {
        float at = 0.3 + float(layer) * 0.36;
        if (at > far) {
            break;
        }
        vec3 world = CamWorld.xyz + V * at;
        vec3 q = world * 42.0 + vec3(t * 0.22, -t * 0.07, t * 0.15) + float(layer) * 13.7;
        vec3 cell = floor(q);
        float pick = hash3(cell);
        if (pick < 0.55) {
            continue;
        }
        // Each mote drifts a little on its own; none of them blinks.
        vec3 centre = 0.5 + 0.22 * vec3(sin(t * 0.31 + pick * 31.0), sin(t * 0.23 + pick * 17.0), cos(t * 0.27 + pick * 23.0));
        float d = length(fract(q) - centre);
        total += smoothstep(0.07, 0.015, d) * (0.4 + 0.6 * fract(pick * 9.7)) * (0.12 + 0.88 * shaftAt(world));
    }
    return total;
}

// How much of the sun's disc is clear of the world, 0..1: the sky shows at depth 0.
float sunClear() {
    if (SunScreen.z < 0.5) {
        return 0.0;
    }
    vec2 c = SunScreen.xy;
    // Fades as the sun leaves the screen rather than cutting off at the edge.
    vec2 edge = smoothstep(vec2(-0.12), vec2(0.03), c) * (1.0 - smoothstep(vec2(0.97), vec2(1.12), c));
    float seen = 0.0;
    for (int i = 0; i < 6; i++) {
        float a = float(i) * 1.0472;
        vec2 s = clamp(c + vec2(cos(a) / Air.w, sin(a)) * 0.012, vec2(0.001), vec2(0.999));
        seen += SCENE_DEPTH(texture(DepthSampler, s).r) <= 0.0 ? 1.0 : 0.0;
    }
    return seen / 6.0 * edge.x * edge.y;
}

float hash1(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

// Smooth noise round a circle: where the shafts fall.
float shaftNoise(float a, float cells, float seed) {
    float x = (a / 6.2831853 + 0.5) * cells;
    float i = floor(x);
    float f = fract(x);
    float u = f * f * (3.0 - 2.0 * f);
    return mix(hash1(mod(i, cells) + seed), hash1(mod(i + 1.0, cells) + seed), u);
}

// The shafts as the eye sees them. Shafts of sunlight are parallel, so in perspective they are lines that fan out
// from the sun and close in again on the point straight opposite it: which shaft a line of sight looks along
// depends only on its direction round the sun's axis. So the pattern is laid out round that axis (bright spokes of
// uneven width with shade between, turning very slowly), and it holds for every way the camera faces: toward the
// sun, across it, or away. About 0..1.6.
float shaftPattern(vec3 V) {
    vec3 L = normalize(SunDir.xyz);
    vec3 T = normalize(cross(L, vec3(0.0, 0.0, 1.0)));
    vec3 B = cross(L, T);
    float a = atan(dot(V, B), dot(V, T)) + CamWorld.w * 0.004;
    float wide = shaftNoise(a, 13.0, 3.0);
    float fine = shaftNoise(a, 41.0, 17.0);
    float thin = shaftNoise(a - CamWorld.w * 0.006, 89.0, 41.0);
    return pow(wide, 1.5) * 1.1 + pow(fine, 3.0) * 0.5 + pow(thin, 4.0) * 0.3;
}

// Beams: walking from this pixel toward the sun on screen, how much open sky lies on the way. Where a tree or a roof
// stands between, the beam is cut, so shafts of light fan out round whatever the sun is behind; and in the open the
// light still comes down in shafts (see shaftPattern).
// Set by beams: how far the sun's own rays reach this pixel (0 with the sun out of view).
float beamsReach = 0.0;

float beams(float jitter) {
    if (SunScreen.z < 0.5) {
        return 0.0;
    }
    vec2 to = SunScreen.xy - texCoord;
    float reach = length(to * vec2(Air.w, 1.0));
    // Only so far across the screen, so the beams stay round the sun.
    vec2 stepv = to * min(1.0, 0.8 / max(reach, 1e-4)) / 24.0;
    float sum = 0.0;
    float weight = 1.0;
    float total = 0.0;
    for (int i = 0; i < 24; i++) {
        vec2 s = texCoord + stepv * (float(i) + jitter);
        if (s.x < 0.0 || s.y < 0.0 || s.x > 1.0 || s.y > 1.0) {
            break;
        }
        sum += (SCENE_DEPTH(texture(DepthSampler, s).r) <= 0.0 ? 1.0 : 0.0) * weight;
        total += weight;
        weight *= 0.96;
    }
    beamsReach = (total > 0.0 ? sum / total : 0.0) * exp(-reach * 0.9);
    return beamsReach;
}

// A six-sided aperture ghost: 1 inside the hexagon of this size round the origin, with a brighter rim.
float hexagon(vec2 p, float size) {
    p = abs(p);
    float d = max(p.x * 0.866025 + p.y * 0.5, p.y) / size;
    return smoothstep(1.0, 0.94, d) * (0.45 + 0.55 * smoothstep(0.75, 1.0, d));
}

// What a camera lens does with the sun in frame: a star of thin rays from the sun, a ring round it, and a row of
// hexagonal ghosts strung along the line from the sun through the middle of the picture, each tinted differently.
vec3 lensFlare(vec2 uv) {
    vec2 aspect = vec2(Air.w, 1.0);
    vec2 sun = (SunScreen.xy - 0.5) * aspect;
    vec2 p = (uv - 0.5) * aspect;
    vec2 d = p - sun;
    // The star and the ring shrink with the sun's size; the ghosts keep their places.
    float r = length(d) / max(ShadeTint.a, 0.05);
    float a = atan(d.y, d.x);
    vec3 flare = vec3(0.0);
    // The star: six long rays and twelve faint ones, the way an aperture's blades spread a point of light.
    float rays = pow(abs(cos(a * 3.0 + 0.4)), 90.0) * exp(-r * 3.2) + pow(abs(cos(a * 6.0 + 1.1)), 60.0) * exp(-r * 7.0) * 0.4;
    flare += SunColor.rgb * rays * 0.9;
    // A thin rainbow halo.
    float ring = exp(-pow((r - 0.34) * 34.0, 2.0)) * 0.1;
    flare += vec3(ring * 1.0, ring * 0.8, ring * 0.6) + vec3(0.0, 0.0, exp(-pow((r - 0.355) * 34.0, 2.0)) * 0.08);
    // The ghosts: place along the sun-to-centre line (1 = the centre, 2 = mirrored across it), size, tint.
    const int N = 7;
    float where[N] = float[N](0.38, 0.62, 0.86, 1.18, 1.45, 1.78, 2.15);
    float size[N] = float[N](0.035, 0.06, 0.022, 0.085, 0.045, 0.12, 0.07);
    vec3 tint[N] = vec3[N](vec3(1.0, 0.75, 0.4), vec3(0.5, 0.9, 0.7), vec3(1.0, 0.5, 0.5), vec3(0.45, 0.65, 1.0),
            vec3(0.9, 0.85, 0.45), vec3(0.6, 0.45, 0.95), vec3(0.4, 0.9, 0.9));
    for (int i = 0; i < N; i++) {
        vec2 centre = sun - sun * where[i];
        flare += tint[i] * hexagon(p - centre, size[i]) * (0.3 - 0.02 * float(i));
    }
    // The further the sun is from the middle, the more of the row is thrown across the frame.
    return flare * (0.55 + 0.45 * smoothstep(0.0, 0.5, length(sun)));
}

// The light in the air, on top of the shaded frame. It is laid on the way light adds on film (each bit of it takes up
// part of what headroom is left), so a bright sky rolls off to white instead of clipping one colour at a time.
vec3 airLight(vec3 colour, float air, float far) {
    vec3 V = viewRay(texCoord);
    vec3 L = normalize(SunDir.xyz);
    float toward = max(dot(V, L), 0.0);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    vec3 add = vec3(0.0);
    float clear = Air.z > 0.0 || SunColor.a > 0.0 || Air.x > 0.0 ? sunClear() : 0.0;
    float lit = air;
    if (Air.x > 0.0) {
        float pattern = shaftPattern(V);
        // How strongly the shafts show this way: most looking up them toward the sun, plainly across them, and again
        // a little where they close in opposite the sun. Only through air the sun reaches, and thin over what is near.
        float away = max(-dot(V, L), 0.0);
        float across = (0.34 + 0.66 * pow(toward, 1.5) + 0.2 * away * away) * air;
        // With the sun in view its rays are also cut by whatever stands in front of it, kept clear of its disc.
        float sunRays = beams(jitter);
        vec2 fromSun = (texCoord - SunScreen.xy) * vec2(Air.w, 1.0);
        sunRays *= smoothstep(0.03, 0.22, length(fromSun));
        float strength = max(across, sunRays * 1.25);
        float shafts = min(pattern, 1.3) * strength;
        lit = max(air * 0.5, shafts);
        // Between the shafts the picture is a shade darker: against a bright sky that, as much as the light, is what
        // makes them show.
        colour *= 1.0 - 0.3 * Air.x * strength * (1.0 - min(pattern, 1.0));
        add += SunColor.rgb * (shafts * 0.75 + air * 0.04) * Air.x;
    }
    if (Air.y > 0.0) {
        // Motes show where the sun reaches the air, and far more of them inside a shaft (see dust).
        add += SunColor.rgb * dust(V, far) * smoothstep(0.02, 0.2, lit) * (0.45 + 0.9 * pow(toward, 2.0)) * Air.y * 0.7;
    }
    if (Air.z > 0.0) {
        // A glow round what is bright in the world (the sky has the sun's own glare): the frame sampled in two rings,
        // keeping its highlights, eased in so the glow has no edge of its own.
        vec3 glow = vec3(0.0);
        for (int i = 0; i < 12; i++) {
            float a = float(i) * 2.39996;
            float r = i < 6 ? 0.008 : 0.022;
            vec2 s = texCoord + vec2(cos(a) / Air.w, sin(a)) * r;
            if (SCENE_DEPTH(texture(DepthSampler, s).r) <= 0.0) {
                continue;
            }
            vec3 c = texture(InSampler, s).rgb;
            float lum = dot(c, vec3(0.299, 0.587, 0.114));
            glow += c * smoothstep(0.55, 0.95, lum);
        }
        add += glow / 12.0 * Air.z * 0.8;
        if (clear > 0.0) {
            // The sun in view: a hot core, a wide soft halo, a streak across, and a veil over the whole picture.
            float size = max(ShadeTint.a, 0.05);
            vec2 d = (texCoord - SunScreen.xy) * vec2(Air.w, 1.0) / size;
            float r = length(d);
            float core = exp(-r * r * 180.0);
            float halo = exp(-r * 5.0) * 0.5 + exp(-r * r * 14.0) * 0.6;
            float streak = exp(-abs(d.y) * 70.0) * exp(-abs(d.x) * 2.0) * 0.3;
            float veil = 0.12 * pow(toward, 3.0) * min(size, 1.0);
            add += SunColor.rgb * (core * 1.4 + halo + streak + veil) * clear * Air.z;
        }
    }
    if (SunColor.a > 0.0 && clear > 0.0) {
        add += lensFlare(texCoord) * clear * SunColor.a;
    }
    return 1.0 - (1.0 - min(colour, vec3(1.0))) * (1.0 - clamp(add, 0.0, 1.0));
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    float depth = SCENE_DEPTH(texture(DepthSampler, texCoord).r);
    if (depth <= 0.0) {
        float air = texture(LightSampler, texCoord).a;
        fragColor = vec4(Extra.w > 0.5 ? vec3(air) : airLight(scene.rgb, air, 1e6), scene.a);
        return;
    }
    vec4 sum = vec4(0.0);
    float total = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 s = texCoord + vec2(float(i), float(j)) * Extra.yz * 1.33;
            float d = SCENE_DEPTH(texture(DepthSampler, s).r);
            if (d <= 0.0) {
                continue;
            }
            // Depth is 1 / distance here, so this compares distances by their ratio.
            float w = exp(-abs(d - depth) / depth * 30.0);
            sum += texture(LightSampler, s) * w;
            total += w;
        }
    }
    vec4 light = total > 1e-4 ? sum / total : texture(LightSampler, texCoord);
    if (Extra.w > 0.5) {
        // For development: the trace itself.
        fragColor = vec4(light.rgb, 1.0);
        return;
    }
    vec3 tint = mix(ShadeTint.rgb, SunTint.rgb, light.r);
    vec3 colour = scene.rgb * mix(vec3(1.0), tint, light.b) * light.g;
    // Richer colour where the sky lights the world: sunlit things glow a little, shade keeps its depth.
    float lum = dot(colour, vec3(0.299, 0.587, 0.114));
    colour = mix(colour, mix(vec3(lum), colour, 1.18) * (0.96 + 0.1 * smoothstep(0.2, 0.8, lum)), light.b * Amount.x);
    if (Air.x > 0.0) {
        // Where a shaft comes down it lays a pool of light on the ground, and the ground between is a touch dimmer:
        // the shafts are seen on the world as well as in the air.
        float far = distanceAt(texCoord, depth);
        float pool = shaftAt(CamWorld.xyz + viewRay(texCoord) * far);
        colour *= mix(1.0, 0.9 + 0.3 * pool, light.r * light.b * Air.x * (1.0 - smoothstep(60.0, 110.0, far)));
    }
    colour = airLight(colour, light.a, distanceAt(texCoord, depth));
    // The alpha may carry Wet Ground's marks for the reflection pass: pass it on.
    fragColor = vec4(colour, scene.a);
}

#version 330

// Scene depth as 26.x has it (1 near, 0 for the sky); 1.21.11 stores it the other way round.
//? if >=26 {
#define SCENE_DEPTH(d) (d)
//?} else {
/*#define SCENE_DEPTH(d) (1.0 - (d))
*///?}

// Vanilla's terrain fragment shader, plus water drawn like a shader pack does it (see RealisticWater). Fragments the
// vertex shader marked as water are shaded against copies of the frame's colour and depth taken just before the
// translucent layer: what lies below is refracted and coloured by how much water the light crosses, the world is
// mirrored by a ray traced across the screen (the sky when nothing is hit), with a sun glint, foam where the water is
// shallow against the shore or anything standing in it, and caustics on the bed. Everything else is vanilla.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
// 26.3 moved the terrain view matrix and atlas size out of the chunk section uniform.
//? if >=26.3 {
/*#moj_import <minecraft:terrainglobals.glsl>
*///?}

uniform sampler2D Sampler0;
uniform sampler2D SceneSampler;
uniform sampler2D SceneDepthSampler;

layout(std140) uniform WaterConfig {
    vec4 Look;      // x = seconds, y = intensity, z = waves, w = world reflections
    vec4 Flags;     // x = foam, y = caustics, z = camera under water, w = 1 when clip z is 0..1
    vec4 Sun;       // xy = towards the sun (east is +x), w = daylight
    vec4 Sky;       // rgb = sky colour, a = rain
    vec4 Camera;    // xyz = camera position (x and z wrapped at 8192)
    mat4 ViewProj;  // camera-relative world -> clip
    mat4 InvViewProj;
};

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 relPos;
in vec4 tint;
in vec3 lightColor;
flat in float waterKind;
flat in vec2 flowDir;

out vec4 fragColor;

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, vec2 texelScreenSize) {
    // Convert our UV back up to texel coordinates and find out how far over we are from the center of each pixel
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;

    // Move our offset closer to the texel center based on texel size on screen
    texelOffset = (texelOffset - 0.5f) * pixelSize / texelScreenSize + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);

    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(source, uv, du, dv);
}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
}

// Rotated Grid Super-Sampling
vec4 sampleRGSS(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);

    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    float maxTexelSize = max(texelScreenSize.x, texelScreenSize.y);

    float minPixelSize = min(pixelSize.x, pixelSize.y);

    float transitionStart = minPixelSize * 1.0;
    float transitionEnd = minPixelSize * 2.0;
    float blendFactor = smoothstep(transitionStart, transitionEnd, maxTexelSize);

    float duLength = length(du);
    float dvLength = length(dv);
    float minDerivative = min(duLength, dvLength);
    float maxDerivative = max(duLength, dvLength);

    float effectiveDerivative = sqrt(minDerivative * maxDerivative);

    float mipLevelExact = max(0.0, log2(effectiveDerivative / minPixelSize));

    float mipLevelLow = floor(mipLevelExact);
    float mipLevelHigh = mipLevelLow + 1.0;
    float mipBlend = fract(mipLevelExact);

    const vec2 offsets[4] = vec2[](
    vec2(0.125, 0.375),
    vec2(-0.125, -0.375),
    vec2(0.375, -0.125),
    vec2(-0.375, 0.125)
    );

    vec4 rgssColorLow = vec4(0.0);
    vec4 rgssColorHigh = vec4(0.0);
    for (int i = 0; i < 4; ++i) {
        vec2 sampleUV = uv + offsets[i] * pixelSize;
        rgssColorLow += textureLod(source, sampleUV, mipLevelLow);
        rgssColorHigh += textureLod(source, sampleUV, mipLevelHigh);
    }
    rgssColorLow *= 0.25;
    rgssColorHigh *= 0.25;

    vec4 rgssColor = mix(rgssColorLow, rgssColorHigh, mipBlend);

    vec4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);

    return mix(nearestColor, rgssColor, blendFactor);
}

// ---- water ---------------------------------------------------------------------------------------------------------

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash22(i).x;
    float b = hash22(i + vec2(1.0, 0.0)).x;
    float c = hash22(i + vec2(0.0, 1.0)).x;
    float d = hash22(i + vec2(1.0, 1.0)).x;
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Value noise and its slope in one go (x = value, yz = slope).
vec3 vnoised(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    vec2 du = 6.0 * f * (1.0 - f);
    float a = hash22(i).x;
    float b = hash22(i + vec2(1.0, 0.0)).x;
    float c = hash22(i + vec2(0.0, 1.0)).x;
    float d = hash22(i + vec2(1.0, 1.0)).x;
    float k = a - b - c + d;
    return vec3(a + (b - a) * u.x + (c - a) * u.y + k * u.x * u.y,
                du * (vec2(b - a, c - a) + k * u.yx));
}

// The swell's slope; the swell itself is in water.vsh: sum of a * sin(dot(p, k) + w * t).
vec2 swellGrad(vec2 p, float t) {
    vec2 g = cos(dot(p, vec2(0.38, 0.21)) + t * 1.1) * 0.5 * vec2(0.38, 0.21);
    g += cos(dot(p, vec2(-0.27, 0.46)) + t * 1.4) * 0.3 * vec2(-0.27, 0.46);
    g += cos(dot(p, vec2(0.71, -0.53)) + t * 2.1) * 0.14 * vec2(0.71, -0.53);
    g += cos(dot(p, vec2(-0.93, -0.88)) + t * 2.9) * 0.06 * vec2(-0.93, -0.88);
    return g;
}

// The slope of small ripples on top of the swell: layers of noise, each drifting at its own speed with the wind or
// the current. Layers finer than a pixel are left out (fine), so distant water stays smooth instead of sparkling.
vec2 rippleGrad(vec2 p, float t, vec2 drift, float fine) {
    vec2 g = vec2(0.0);
    float amp = 0.5;
    float freq = 1.3;
    mat2 turn = mat2(0.8, -0.6, 0.6, 0.8);
    mat2 back = mat2(1.0);
    for (int i = 0; i < 4; i++) {
        float keep = clamp(fine / freq - 1.0, 0.0, 1.0);
        if (keep <= 0.0) {
            break;
        }
        vec3 n = vnoised(p * freq - drift * t * (0.6 + 0.35 * float(i)) + float(i) * 7.31);
        g += back * n.yz * freq * amp * keep;
        p = turn * p;
        back = back * transpose(turn);
        amp *= 0.5;
        freq *= 2.1;
    }
    return g;
}

// Raindrop rings, as on Wet Ground's puddles: a slope to tilt the normal by.
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

// A web of bright lines, as light focused by the rippling surface draws on the bed (the same as WorldPost's).
float causticLayer(vec2 p, float t, float blur) {
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
    return 1.0 - smoothstep(0.0, blur, d2 - d1);
}

// Deeper beds get a softer pattern: the light has spread out more on its way down.
float caustics(vec3 w, float t, float depth) {
    vec2 sun = Sun.xy;
    vec2 p = w.xz - vec2(sun.x, 0.0) / max(sun.y, 0.25) * w.y;
    float blur = 0.22 + 0.12 * depth;
    float a = causticLayer(p * 0.45, t * 0.6, blur);
    float b = causticLayer(p * 0.45 * 1.37 + 3.1, -t * 0.5, blur);
    return mix(a * b, (a + b) * 0.5, 0.35);
}

vec3 worldAt(vec2 uv, float depth) {
    float z = Flags.w > 0.5 ? SCENE_DEPTH(depth) : SCENE_DEPTH(depth) * 2.0 - 1.0;
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, z, 1.0);
    return p.xyz / p.w;
}

// What the frame shows behind the water at a screen point: its camera-relative position (far off on the sky).
vec3 behindAt(vec2 uv, vec3 V) {
    float d = SCENE_DEPTH(texture(SceneDepthSampler, uv).r);
    return d > 0.0 ? worldAt(uv, d) : V * 1024.0;
}

// The world mirrored in the water: the reflected ray is marched across the screen until it passes behind something
// drawn. The ray is straight in clip space too, so each step is one add and one depth fetch; only a candidate hit is
// turned back into a distance. A hit must cross from in front of a surface to behind it (see WorldPost's tracer),
// or rays would "hit" behind tree trunks. found = how much to trust the hit, 0 when there is none.
vec3 traceReflection(vec3 P, vec3 R, float dist, out float found) {
    found = 0.0;
    vec4 c0 = ViewProj * vec4(P, 1.0);
    vec4 dc = ViewProj * vec4(R, 0.0);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float stepLen = max(0.2, dist * 0.02) * (0.6 + 0.8 * jitter);
    float t = 0.0;
    bool wasBehind = false;
    // A near miss behind an edge, kept in case nothing better turns up: falling back to the sky there instead would
    // streak bright lines down the reflection of a wall.
    vec3 nearMiss = vec3(0.0);
    float nearFound = 0.0;
    for (int i = 0; i < 20; i++) {
        float prevT = t;
        t += stepLen;
        stepLen = min(stepLen * 1.26, 8.0);
        vec4 c = c0 + dc * t;
        if (c.w <= 0.05) {
            break;
        }
        vec3 ndc = c.xyz / c.w;
        vec2 s = ndc.xy * 0.5 + 0.5;
        if (s.x < 0.0 || s.y < 0.0 || s.x > 1.0 || s.y > 1.0) {
            break;
        }
        float d = SCENE_DEPTH(texture(SceneDepthSampler, s).r);
        float rayD = Flags.w > 0.5 ? ndc.z : ndc.z * 0.5 + 0.5;
        // Reversed depth: the ray is behind what is drawn when its depth is smaller.
        bool behind = d > 0.0 && rayD < d;
        bool crossed = behind && !wasBehind;
        wasBehind = behind;
        if (crossed) {
            float lo = prevT;
            float hi = t;
            for (int k = 0; k < 4; k++) {
                float mid = (lo + hi) * 0.5;
                vec4 mc = c0 + dc * mid;
                vec3 mn = mc.xyz / mc.w;
                vec2 ms = mn.xy * 0.5 + 0.5;
                float md = SCENE_DEPTH(texture(SceneDepthSampler, ms).r);
                float mr = Flags.w > 0.5 ? mn.z : mn.z * 0.5 + 0.5;
                if (md > 0.0 && mr < md) {
                    hi = mid;
                    s = ms;
                    d = md;
                } else {
                    lo = mid;
                }
            }
            // Too far behind the surface it crossed: the ray went behind an edge, not into a wall.
            float gap = length(P + R * hi) - length(worldAt(s, d));
            vec2 edge = min(s, 1.0 - s);
            if (gap > clamp(stepLen * 1.6, 0.4, 4.0)) {
                if (nearFound <= 0.0) {
                    nearMiss = texture(SceneSampler, s).rgb;
                    nearFound = smoothstep(0.0, 0.08, min(edge.x, edge.y)) * 0.9;
                }
                continue;
            }
            found = smoothstep(0.0, 0.08, min(edge.x, edge.y)) * (1.0 - smoothstep(70.0, 140.0, t));
            return texture(SceneSampler, s).rgb;
        }
    }
    found = nearFound;
    return nearMiss;
}

vec3 skyReflection(vec3 R, float skyLight) {
    vec3 sky = mix(FogColor.rgb, Sky.rgb, smoothstep(0.0, 0.5, R.y));
    vec3 overcast = mix(FogColor.rgb, vec3(0.72, 0.75, 0.8), 0.5);
    return mix(sky, overcast, Sky.a * 0.8) * skyLight;
}

vec4 shadeWater(vec4 vanilla) {
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(SceneSampler, 0));
    float dist = max(length(relPos), 1e-3);
    vec3 V = relPos / dist;
    vec3 Nf = normalize(cross(dFdx(relPos), dFdy(relPos)));
    if (dot(Nf, V) > 0.0) {
        Nf = -Nf;
    }
    bool top = abs(Nf.y) > 0.7;
    bool fromBelow = top && Nf.y < 0.0;
    vec3 world = relPos + Camera.xyz;
    float t = Look.x;
    float detail = 1.0 - smoothstep(24.0, 96.0, dist);
    // How fine a ripple can be here and still cover a few pixels (in waves per block).
    float fine = 0.35 / max(length(fwidth(relPos.xz)), 1e-4);
    float light = max(max(lightColor.r, lightColor.g), lightColor.b);
    vec2 drift = waterKind > 1.5 ? flowDir * 1.6 : vec2(0.35, 0.2);

    vec3 N;
    // Falling water is churned full of air: streaks of white running down it, thinner on the sides of flowing water.
    float white = 0.0;
    if (top) {
        vec2 g = swellGrad(world.xz, t) * 0.07 * Look.z * (1.0 + Sky.a * 0.6);
        vec2 r = rippleGrad(world.xz, t, drift, fine) * (0.05 + 0.04 * Look.z) * detail;
        if (Sky.a > 0.05) {
            r += raindrops(world.xz, t) * Sky.a * (1.0 - smoothstep(16.0, 40.0, dist));
        }
        N = normalize(vec3(-(g.x + r.x), 1.0, -(g.y + r.y)));
        if (fromBelow) {
            N.y = -N.y;
        }
    } else {
        // Sides and falling water: streaks running down the face.
        vec2 across = vec2(dot(world.xz, vec2(Nf.z, -Nf.x)) * 2.5, world.y * 0.9 + t * (waterKind > 2.5 ? 5.0 : 1.5));
        float a = vnoise(across) - vnoise(across + vec2(0.13, 0.0));
        float b = vnoise(across * 1.9 + 4.0) - vnoise(across * 1.9 + vec2(4.0, 4.13));
        N = normalize(Nf + (vec3(Nf.z, 0.0, -Nf.x) * a * 2.0 + vec3(0.0, b, 0.0)) * detail);
        float side = dot(world.xz, vec2(Nf.z, -Nf.x));
        float fall = waterKind > 2.5 ? 3.5 : 1.2;
        float streak = vnoise(vec2(side * 5.0, world.y * 0.7 + t * fall)) * 0.65 + vnoise(vec2(side * 11.0 + 3.0, world.y * 1.6 + t * fall * 1.3)) * 0.35;
        white = waterKind > 2.5 ? 0.12 + 0.38 * smoothstep(0.35, 0.85, streak) : 0.12 * smoothstep(0.6, 0.9, streak);
    }

    // What lies behind the surface, seen through it with a bend that grows with the water's thickness.
    vec3 B = behindAt(uv, V);
    // A side face is a sheet (a waterfall, the edge of a pool): what is behind it is not under that much water.
    float maxThick = top ? 1e4 : 1.0;
    float thick = min(max(length(B) - dist, 0.0), maxThick);
    vec2 bendDir = (ViewProj * vec4(N.x, 0.0, N.z, 0.0)).xy;
    vec2 uvR = clamp(uv + bendDir * 0.03 * clamp(thick * 0.5, 0.0, 1.0) / (1.0 + dist * 0.04), vec2(0.001), vec2(0.999));
    vec3 BR = behindAt(uvR, V);
    if (length(BR) < dist) {
        // The bent sample landed on something in front of the water: look straight through instead.
        uvR = uv;
        BR = B;
    }
    float thickR = min(max(length(BR) - dist, 0.0), maxThick);
    vec3 below = texture(SceneSampler, uvR).rgb;

    // Light crossing water loses red first, then green; the deep is the water's own scattered colour.
    vec3 waterTint = clamp(tint.rgb, 0.0, 1.0);
    vec3 absorbK = mix(vec3(0.38, 0.085, 0.06), (1.0 - waterTint) * 0.4 + 0.03, 0.2);
    float daylight = 0.25 + 0.75 * Sun.w;
    // Real deep water is dark and only faintly coloured; most of what you see on it is reflection.
    vec3 scatter = mix(vec3(0.012, 0.04, 0.05), waterTint * 0.08, 0.2) * light * daylight;
    vec3 T = exp(-absorbK * (fromBelow || Flags.z > 0.5 ? min(thickR, 4.0) : thickR));
    vec3 refr = below * T + scatter * (1.0 - T);
    float shallow = exp(-thickR * 0.3) * (1.0 - exp(-thickR * 2.5)) * Sun.w * (1.0 - Sky.a) * detail;
    if (Flags.y > 0.5 && !fromBelow && top && shallow > 0.02) {
        // Focused sunlight: white light on the bed, absorbed on the way back up like everything else down there.
        float lum = dot(below, vec3(0.3, 0.5, 0.2));
        refr += vec3(1.0, 0.97, 0.9) * lum * T * caustics(BR + Camera.xyz, t, thickR) * shallow * 0.55;
    }

    vec3 R = reflect(V, N);
    float cosI = clamp(dot(N, -V), 0.0, 1.0);
    float F;
    vec3 refl;
    if (!fromBelow) {
        F = 0.02 + 0.98 * pow(1.0 - cosI, 5.0);
        if (R.y < 0.0) {
            R.y = -R.y * 0.3;
        }
        refl = skyReflection(R, light);
        if (Look.w > 0.5 && detail > 0.0 && F > 0.04) {
            float found;
            vec3 hit = traceReflection(relPos, R, dist, found);
            refl = mix(refl, hit * 0.92, found);
        }
    } else {
        // From below, only a window overhead lets light out; past it the surface is a mirror of the water below.
        float sinT2 = 1.769 * (1.0 - cosI * cosI);
        F = sinT2 >= 1.0 ? 1.0 : 0.02 + 0.98 * pow(1.0 - sqrt(1.0 - sinT2), 5.0);
        refl = scatter * 0.9;
        refr = below;
    }
    F = clamp(F, 0.0, 1.0);
    vec3 water = mix(refr, refl, F);
    if (!fromBelow) {
        vec3 sunDir = normalize(vec3(Sun.x, Sun.y, 0.0));
        float s = max(dot(R, sunDir), 0.0);
        water += vec3(1.0, 0.95, 0.85) * (pow(s, 700.0) * 9.0 + pow(s, 45.0) * 0.12) * Sun.w * (1.0 - Sky.a) * light;
    }
    if (white > 0.0) {
        water = mix(water, vec3(0.88, 0.93, 0.95) * light * daylight, white);
        F = max(F, white);
    }
    if (Flags.x > 0.5 && top && !fromBelow && SCENE_DEPTH(texture(SceneDepthSampler, uv).r) > 0.0) {
        // Foam needs moving water: broken patches where a current meets the shore or something standing in it, and
        // only a faint wet line where still water touches the bank.
        float vert = thick * abs(V.y);
        bool moving = waterKind > 1.5;
        float reach = moving ? 0.25 + 0.2 * Look.z : 0.06;
        float edge = 1.0 - smoothstep(0.0, reach, vert);
        float n = vnoise(world.xz * 4.0 - drift * t * 0.8) * 0.6 + vnoise(world.xz * 9.3 + t * 0.4) * 0.4;
        float foam = smoothstep(0.5, 0.85, n * 0.6 + edge * 0.5) * edge * detail * (moving ? 0.7 : 0.25);
        water = mix(water, vec3(0.86, 0.9, 0.92) * light * daylight, foam);
        F = max(F, foam);
    }
    vec3 behind = texture(SceneSampler, uv).rgb;
    vec3 result = mix(mix(behind, vanilla.rgb, vanilla.a), water, Look.y);
    // The frame under this pixel can hold more than the copy did: glass or ice drawn just before, behind the water.
    // Blending lets that difference through, as much as the water lets through: out = result + (frame - copy) * k.
    vec3 Tc = exp(-absorbK * min(thickR, 8.0));
    float k = fromBelow ? 0.0 : dot(Tc, vec3(0.3333)) * (1.0 - F) * 0.9 * Look.y + (1.0 - vanilla.a) * (1.0 - Look.y);
    float a = 1.0 - k;
    return vec4(clamp((result - behind * k) / max(a, 1e-3), 0.0, 1.0), a);
}

void main() {
    vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;
    if (waterKind > 3.5) {
        discard;
    }
    if (waterKind > 0.5) {
        color = shadeWater(color);
    }
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, ChunkVisibility);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

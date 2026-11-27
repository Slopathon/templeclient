#version 330

// Vanilla's terrain fragment shader plus wet ground (see WetGround): upward faces under open sky get damp and darker,
// puddles gather in noise-shaped patches, and puddles reflect the sky with a sun glint and a Fresnel sheen. Raindrops
// ring out across the puddles by bending the reflection normal.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
// 26.3 moved the terrain view matrix and atlas size out of the chunk section uniform.
//? if >=26.3 {
/*#moj_import <minecraft:terrainglobals.glsl>
*///?}

uniform sampler2D Sampler0;

layout(std140) uniform WetConfig {
    vec4 Wet;   // x = seconds, y = puddle coverage 0..1, z = shine 0..1, w = raindrops (0 off, 1..2 heavier in rain)
    vec4 Sun;   // xyz = direction to the sun, w = daylight 0..1
    vec4 Sky;   // rgb = sky colour, a = rain 0..1
    vec4 Mode;  // x = flooded (all open ground is water), y = mark wet pixels for the reflection pass (see WorldPost)
};

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 relPos;
in vec3 worldPos;
in float skyLight;

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

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x), mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

// Puddle shapes: a few octaves, so edges wander instead of looking like blobs on a grid.
float puddleNoise(vec2 p) {
    float n = valueNoise(p) * 0.55 + valueNoise(p * 2.13 + 7.1) * 0.3 + valueNoise(p * 4.7 + 3.3) * 0.15;
    return n;
}

// Raindrop rings: each cell drops one ring at a random moment; the ring expands and fades. Returns a normal tilt.
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

void main() {
    vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    // Only faces that point up, under open sky, and close enough that the detail does not shimmer.
    vec3 n = normalize(cross(dFdx(relPos), dFdy(relPos)));
    if (dot(n, relPos) > 0.0) {
        n = -n;
    }
    float dist = length(relPos);
    float up = smoothstep(0.9, 0.99, n.y);
    // Open sky only: sky light 15 (1.0). Water lowers it by one level per block, so ground under water (14 and below,
    // 0.933) gets no puddles or raindrops; a shore blends over the one block where the two meet.
    float open = smoothstep(0.945, 0.99, skyLight);
    bool flooded = Mode.x > 0.5;
    // Flooded ground is water as far as you can see; puddles fade out where their detail would shimmer.
    float near = flooded ? 1.0 - smoothstep(FogRenderDistanceEnd * 0.8, FogRenderDistanceEnd, dist) : 1.0 - smoothstep(56.0, 110.0, dist);
    float wet = up * open * near;
    float mirror = 0.0;
    if (wet > 0.001) {
        float coverage = Wet.y;
        float pool = wet;
        if (!flooded) {
            float nse = puddleNoise(worldPos.xz * 0.17);
            pool = wet * smoothstep(1.02 - coverage, 1.1 - coverage, nse + 0.06);
        }
        // Damp ground is darker and a little more saturated; standing water darker still.
        vec3 base = color.rgb;
        float lum = dot(base, vec3(0.299, 0.587, 0.114));
        base = mix(base, mix(vec3(lum), base, 1.15), wet * 0.5);
        base *= mix(1.0, 0.8, wet * (0.35 + 0.4 * Sky.a)) * mix(1.0, flooded ? 0.6 : 0.78, pool);

        vec2 tilt = vec2(0.0);
        if (Wet.w > 0.0 && pool > 0.01) {
            float close = 1.0 - smoothstep(18.0, 40.0, dist);
            tilt = raindrops(worldPos.xz, Wet.x) * Wet.w * close * (1.0 + Sky.a);
        }
        vec3 N = normalize(vec3(tilt.x, 1.0, tilt.y));
        vec3 V = normalize(-relPos);
        vec3 R = reflect(-V, N);
        float fresnel = 0.03 + 0.97 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
        // The reflected sky: the horizon takes the fog colour, higher up the sky colour; both dimmed by the local light.
        float light = max(max(vertexColor.r, vertexColor.g), vertexColor.b);
        vec3 skyRefl = mix(FogColor.rgb, Sky.rgb, smoothstep(0.0, 0.5, R.y)) * light;
        float glint = pow(max(dot(R, normalize(Sun.xyz)), 0.0), 220.0) * Sun.w * 4.0;
        float sheen0 = pow(max(dot(R, normalize(Sun.xyz)), 0.0), 18.0) * Sun.w * 0.25;
        // Water darkens what is under it and adds a sheen on top; it never paints over the ground. The sheen is the
        // sky's own colour, only strong at grazing angles, so looking down at a puddle shows the ground through it.
        float amount = (pool * 0.9 + (wet - pool) * 0.2) * Wet.z;
        // Under an overcast or rainy sky there is no sun to glint, so the grey sky itself has to carry the wet look:
        // a brighter, wider sheen over everything wet, strongest on the puddles.
        // The overcast is lit from the whole sky, so it keeps some brightness even when the local light is low: in a
        // dark storm the puddles still read as water (and every lightning flash lights them up through FogColor).
        vec3 overcast = mix(FogColor.rgb, vec3(0.78, 0.8, 0.84), 0.55) * max(light, 0.5);
        skyRefl = mix(skyRefl, overcast, Sky.a);
        vec3 sheen = skyRefl * fresnel * amount * (0.55 + 1.3 * Sky.a) + skyRefl * pool * Sky.a * 0.32 * Wet.z;
        vec3 sun = vec3(1.0, 0.97, 0.9) * (glint * 1.2 + sheen0) * amount * light;
        color.rgb = base * (1.0 - 0.12 * pool) + sheen + sun;
        // How much the reflection pass may mirror here: standing water fully, damp ground a touch.
        mirror = pool + (wet - pool) * 0.12;
    }

    color = mix(FogColor * vec4(1, 1, 1, color.a), color, ChunkVisibility);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    if (Mode.y > 0.5) {
        // Mark the pixel for the reflection pass in the frame's alpha, which nothing else reads: 0.25 (mirror) to 0.45
        // (barely). Unmarked terrain keeps alpha 1, and the pass puts every pixel back to 1 when it is done.
        mirror *= 1.0 - smoothstep(FogRenderDistanceStart, FogRenderDistanceEnd, cylindricalVertexDistance);
        if (mirror > 0.02) {
            fragColor.a = 0.25 + 0.2 * (1.0 - clamp(mirror, 0.0, 1.0));
        }
    }
}

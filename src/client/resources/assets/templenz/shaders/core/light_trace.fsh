#version 330

// Scene depth as 26.x has it (1 near, 0 for the sky); 1.21.11 stores it the other way round.
//? if >=26 {
#define SCENE_DEPTH(d) (d)
//?} else {
/*#define SCENE_DEPTH(d) (1.0 - (d))
#define COARSE_DEPTH
*///?}

// Realistic Lighting, first pass (see LightingPass): for each pixel, whether the sun or the moon reaches it and how
// hemmed in it is, traced through the voxel grid of the blocks around the camera (VoxelGrid).
//
// Out: r = how lit by the sun (1 = fully, already scaled by the shadow strength), g = corner shading (1 = open),
// b = how far the sky reaches here (0 in a cave: the frame is left alone there), a = how much sunlit air lies between
// the eye and this pixel (see sunlitAir).

uniform sampler2D DepthSampler;
// 128 x 64 x 128 voxels, the 64 layers side by side, 8 to a row. r = what stands there (0 nothing, 0.43 part of a
// block, 0.59 leaves, 1 a whole block), g = sky light, b = block light. The grid wraps: a block's slot is its
// coordinate modulo the size.
uniform sampler2D VoxelSampler;

layout(std140) uniform LightConfig {
    mat4 ViewProj;     // camera-relative world -> clip
    mat4 InvViewProj;  // clip -> camera-relative world
    vec4 CamGrid;      // xyz = the camera within the grid, w = 1 when clip z is 0..1
    vec4 Origin;       // xyz = the slot of the grid's lowest corner, w = seconds
    vec4 SunDir;       // xyz = towards the sun or the moon, w = softness 0..1
    vec4 Amount;       // x = shadow strength, y = corner shading strength, z = steps a shadow ray may take, w = corner rays
    vec4 Extra;        // x = 1 for contact shadows, yz = one texel of this pass's output
    vec4 ShadeTint;    // rgb = what full shade multiplies the frame by, a = the size of the sun's glare (1 = full)
    vec4 SunTint;      // rgb = what full sun multiplies the frame by
    vec4 Air;          // x = light shafts, y = dust, z = bloom (each 0 = off), w = the screen's width over its height
    vec4 SunScreen;    // xy = the sun on screen (0..1, y up), z = 1 when it is in front of the camera, w = shaft samples
    vec4 SunColor;     // rgb = the colour of its light, a = lens flare (0 = off)
    vec4 CamWorld;     // xyz = the camera in the world (wrapped), w = seconds
};

in vec2 texCoord;

out vec4 fragColor;

const float TAU = 6.2831853;

vec3 worldAt(vec2 uv, float depth) {
    float z = CamGrid.w > 0.5 ? SCENE_DEPTH(depth) : SCENE_DEPTH(depth) * 2.0 - 1.0;
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, z, 1.0);
    return p.xyz / p.w;
}

bool inside(ivec3 g) {
    return g.x >= 0 && g.y >= 0 && g.z >= 0 && g.x < 128 && g.y < 64 && g.z < 128;
}

// Outside the grid is open air under open sky.
vec4 voxel(ivec3 g) {
    if (!inside(g)) {
        return vec4(0.0, 1.0, 0.0, 1.0);
    }
    ivec3 o = ivec3(Origin.xyz);
    ivec3 s = ivec3((g.x + o.x) & 127, (g.y + o.y) & 63, (g.z + o.z) & 127);
    return texelFetch(VoxelSampler, ivec2((s.y & 7) * 128 + s.x, (s.y >> 3) * 128 + s.z), 0);
}

float hash3(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

// Walks a ray through the grid, a voxel at a time, starting past the voxel it begins in. Returns how much light gets
// through: 0 behind a whole block; leaves let it through in specks, and part-blocks dim it.
float shadowRay(vec3 p, vec3 d, int steps) {
    ivec3 g = ivec3(floor(p));
    ivec3 stp = ivec3(d.x < 0.0 ? -1 : 1, d.y < 0.0 ? -1 : 1, d.z < 0.0 ? -1 : 1);
    vec3 inv = 1.0 / max(abs(d), vec3(1e-4));
    vec3 f = p - floor(p);
    vec3 next = vec3(d.x < 0.0 ? f.x : 1.0 - f.x, d.y < 0.0 ? f.y : 1.0 - f.y, d.z < 0.0 ? f.z : 1.0 - f.z) * inv;
    float light = 1.0;
    for (int i = 0; i < steps; i++) {
        float t;
        if (next.x < next.y && next.x < next.z) {
            t = next.x;
            next.x += inv.x;
            g.x += stp.x;
        } else if (next.y < next.z) {
            t = next.y;
            next.y += inv.y;
            g.y += stp.y;
        } else {
            t = next.z;
            next.z += inv.z;
            g.z += stp.z;
        }
        if (!inside(g)) {
            break;
        }
        float o = voxel(g).r;
        if (o > 0.9) {
            return 0.0;
        }
        if (o > 0.5) {
            // A canopy lets the sun through in specks rather than as a grey wash.
            float gap = hash3(floor((p + d * (t + 0.02)) * 3.0));
            light *= gap < 0.6 ? 0.22 : 0.9;
        } else if (o > 0.3) {
            light *= 0.5;
        }
        if (light < 0.05) {
            return 0.0;
        }
    }
    return light;
}

// How near the first thing along a short ray is, as 1 (touching) to 0 (nothing within reach).
float nearness(vec3 p, vec3 d, float reach) {
    ivec3 g = ivec3(floor(p));
    ivec3 stp = ivec3(d.x < 0.0 ? -1 : 1, d.y < 0.0 ? -1 : 1, d.z < 0.0 ? -1 : 1);
    vec3 inv = 1.0 / max(abs(d), vec3(1e-4));
    vec3 f = p - floor(p);
    vec3 next = vec3(d.x < 0.0 ? f.x : 1.0 - f.x, d.y < 0.0 ? f.y : 1.0 - f.y, d.z < 0.0 ? f.z : 1.0 - f.z) * inv;
    for (int i = 0; i < 5; i++) {
        float t;
        if (next.x < next.y && next.x < next.z) {
            t = next.x;
            next.x += inv.x;
            g.x += stp.x;
        } else if (next.y < next.z) {
            t = next.y;
            next.y += inv.y;
            g.y += stp.y;
        } else {
            t = next.z;
            next.z += inv.z;
            g.z += stp.z;
        }
        if (t > reach) {
            break;
        }
        float o = voxel(g).r;
        if (o > 0.3) {
            return (1.0 - t / reach) * (o > 0.9 ? 1.0 : 0.5);
        }
    }
    return 0.0;
}

// Sky and block light at a point, blended between the voxels around it, leaving out the ones inside blocks (they
// hold no light of their own).
vec2 lightAt(vec3 p) {
    vec3 q = p - 0.5;
    ivec3 b = ivec3(floor(q));
    vec3 f = q - floor(q);
    vec2 sum = vec2(0.0);
    float total = 0.0;
    for (int i = 0; i < 8; i++) {
        ivec3 c = ivec3(i & 1, (i >> 1) & 1, (i >> 2) & 1);
        vec4 v = voxel(b + c);
        vec3 w3 = mix(1.0 - f, f, vec3(c));
        float w = w3.x * w3.y * w3.z * (v.r > 0.9 ? 0.0 : 1.0);
        sum += v.gb * w;
        total += w;
    }
    return total > 1e-3 ? sum / total : voxel(ivec3(floor(p))).gb;
}

// How much of the air along a line of sight the sun reaches, 0..1: at a few points along it, a short ray toward the
// sun asks whether something stands close in the way (short is enough: a beam is cut by what is near it).
float sunlitAir(vec3 V, float far, float jitter) {
    int samples = int(SunScreen.w);
    vec3 L = normalize(SunDir.xyz);
    float reach = min(far, 44.0);
    float sum = 0.0;
    for (int i = 0; i < samples; i++) {
        vec3 q = CamGrid.xyz + V * reach * (float(i) + jitter) / float(samples);
        ivec3 g = ivec3(floor(q));
        if (!inside(g)) {
            sum += 1.0;
            continue;
        }
        vec4 v = voxel(g);
        if (v.r > 0.9) {
            continue;
        }
        // No light where the sky does not reach at all (a cave's ceiling may be further off than the short ray).
        sum += shadowRay(q, L, 12) * smoothstep(0.0, 0.4, v.g);
    }
    // A view cut short by something near has less air in it to glow.
    return sum / float(samples) * smoothstep(0.0, 28.0, reach);
}

void main() {
    float depth = SCENE_DEPTH(texture(DepthSampler, texCoord).r);
    float n1 = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float n2 = fract(n1 * 7.137 + 0.371);
    if (depth <= 0.0) {
        float air = 0.0;
        if (Air.x > 0.0) {
            air = sunlitAir(normalize(worldAt(texCoord, 0.002)), 40.0, n1);
        }
        fragColor = vec4(1.0, 1.0, 1.0, air);
        return;
    }
    vec3 P = worldAt(texCoord, depth);
    float dist = length(P);
    float air = Air.x > 0.0 ? sunlitAir(P / max(dist, 1e-4), dist, n1) : 0.0;

    // Which way the surface faces, from where its neighbours on screen lie: across each axis, the neighbour nearer in
    // depth is the one on the same surface.
    vec2 px = 1.0 / vec2(textureSize(DepthSampler, 0));
    float dl = SCENE_DEPTH(texture(DepthSampler, texCoord - vec2(px.x, 0.0)).r);
    float dr = SCENE_DEPTH(texture(DepthSampler, texCoord + vec2(px.x, 0.0)).r);
    float dd = SCENE_DEPTH(texture(DepthSampler, texCoord - vec2(0.0, px.y)).r);
    float du = SCENE_DEPTH(texture(DepthSampler, texCoord + vec2(0.0, px.y)).r);
    vec3 ax = abs(dr - depth) < abs(dl - depth) ? worldAt(texCoord + vec2(px.x, 0.0), dr) - P : P - worldAt(texCoord - vec2(px.x, 0.0), dl);
    vec3 ay = abs(du - depth) < abs(dd - depth) ? worldAt(texCoord + vec2(0.0, px.y), du) - P : P - worldAt(texCoord - vec2(0.0, px.y), dd);
    vec3 N = normalize(cross(ax, ay));
    if (dot(N, P) > 0.0) {
        N = -N;
    }

    // Shadows fade out before the grid's edge, so there is no line where it ends.
    vec3 pg = P + CamGrid.xyz;
#ifdef COARSE_DEPTH
    // Depth stored near-to-far keeps little precision in the distance: rows of ground share one value, and the facing
    // worked out from them points at the camera instead of up, which shaded every block edge along such a row as a wall
    // turned from the sun (dark lines across the ground). The grid knows better: on a block's top face, it faces up.
    if (abs(fract(pg.y) - 0.5) > 0.38 && voxel(ivec3(floor(pg - vec3(0.0, 0.5, 0.0)))).r > 0.3
            && voxel(ivec3(floor(pg + vec3(0.0, 0.5, 0.0)))).r < 0.3) {
        N = vec3(0.0, 1.0, 0.0);
    }
#endif
    float reach = max(abs(P.x), abs(P.z));
    float fade = (1.0 - smoothstep(44.0, 60.0, reach)) * smoothstep(0.0, 4.0, pg.y) * (1.0 - smoothstep(58.0, 63.0, pg.y));
    if (fade <= 0.0) {
        fragColor = vec4(1.0, 1.0, 1.0, air);
        return;
    }
    // A block's face lies on the grid. Anything else (a blade of grass, a plant, a mob, the top of a slab) is too thin
    // or too finely shaped for its facing to be trusted: it is lit as if it faced the sky.
    vec3 off = abs(fract(pg) - 0.5);
    vec3 an = abs(N);
    float across = an.x > an.y && an.x > an.z ? off.x : an.y > an.z ? off.y : off.z;
    // And a block's face has the block behind it. Without this, a blade of grass that happens to stand on the line
    // between two blocks passes for a face, turned from the sun, and every such line across a lawn goes dark.
    bool block = across > 0.46 - dist * 0.002 && voxel(ivec3(floor(pg - N * 0.5))).r > 0.3;
    if (!block) {
        N = vec3(0.0, 1.0, 0.0);
    }
    vec3 start = pg + N * 0.03;
    vec2 lit = lightAt(pg + N * 0.5);

    // n1 and n2 are interleaved gradient noise: a different nudge for each pixel, evened out by the second pass.

    vec3 L = normalize(SunDir.xyz);
    float vis = 1.0;
    if (Amount.x > 0.0) {
        float facing = dot(N, L);
        if (facing <= 0.0 && block) {
            vis = 0.0;
        } else {
            vec3 T = normalize(cross(L, vec3(0.0, 0.0, 1.0)));
            vec3 B = cross(L, T);
            float a = n1 * TAU;
            vec3 Lj = normalize(L + (T * cos(a) + B * sin(a)) * sqrt(n2) * SunDir.w * 0.05);
            vis = shadowRay(start, Lj, int(Amount.z)) * (block ? smoothstep(0.0, 0.12, facing) : 1.0);
            // Only on block faces: on a lawn every blade would shade its neighbours and the noise shows as streaks.
            if (Extra.x > 0.5 && block && vis > 0.05 && dist < 40.0) {
                // Contact shadows: what the grid does not hold (players, mobs, plants, blades of grass) still shades the
                // ground right beside it, found by walking a short way toward the light across the screen.
                vec3 from = P + N * 0.05;
                for (int i = 0; i < 8; i++) {
                    vec3 Q = from + Lj * 1.1 * (float(i) + n2) / 8.0;
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
                        continue;
                    }
                    float behind = length(Q) - length(worldAt(s, d));
                    if (behind > 0.04 && behind < 0.35) {
                        vis *= 0.4;
                        break;
                    }
                }
            }
        }
    }

    float open = 1.0;
    int rays = int(Amount.w);
    if (rays > 0) {
        vec3 T = normalize(abs(N.y) < 0.9 ? cross(N, vec3(0.0, 1.0, 0.0)) : cross(N, vec3(1.0, 0.0, 0.0)));
        vec3 B = cross(N, T);
        float sum = 0.0;
        for (int i = 0; i < rays; i++) {
            float a = (float(i) + n1) / float(rays) * TAU;
            vec3 d = normalize(N * 0.6 + (T * cos(a) + B * sin(a)) * 0.8);
            sum += nearness(start, d, 3.5);
        }
        open = 1.0 - Amount.y * 0.6 * sum / float(rays);
    }

    // Torchlight fills a shadow in; where the sky does not reach, the frame is left as it is.
    float shadow = (1.0 - vis) * Amount.x * fade * (1.0 - 0.5 * lit.y);
    float sky = mix(1.0, smoothstep(0.1, 0.8, lit.x), fade);
    fragColor = vec4(1.0 - shadow, mix(1.0, open, fade), sky, air);
}

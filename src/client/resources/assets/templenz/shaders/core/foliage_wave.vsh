#version 330

// Vanilla's terrain vertex shader plus wind. The chunk builder stores a sway weight in the alpha of each foliage
// vertex's colour (see WavingFoliage): 254 leaves, 253 vines, 150..240 plants (weight = (a - 150) / 50, rising with
// height so a plant's base stays put). Trees carry their height above the trunk's bottom instead: trunks 100..149
// (half a block per step), their leaves 2..99 (a quarter block per step). Everything else keeps alpha 255.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
// 26.3 moved the terrain view matrix and atlas size out of the chunk section uniform.
//? if >=26.3 {
/*#moj_import <minecraft:terrainglobals.glsl>
*///?}
#moj_import <minecraft:projection.glsl>
#moj_import <templenz:sample_lightmap.glsl>

layout(std140) uniform WaveConfig {
    // x = seconds (already scaled by speed), y = strength, z = gustiness 0..1
    vec4 Wind;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
// For Wet Ground's fragment shader; vanilla's ignores them.
out vec3 relPos;
out vec3 worldPos;
out float skyLight;
// 26.3: vanilla's terrain fragment shader fades new chunks in by this rather than reading the uniform itself.
//? if >=26.3 {
/*out float chunkVisibility;
*///?}

// Slow gusts: a broad band of stronger wind rolling across the world along the wind direction.
float gust(vec2 world, float t) {
    float band = sin(dot(world, vec2(0.021, 0.013)) - t * 0.55) * 0.5 + 0.5;
    float swell = sin(t * 0.23 + world.x * 0.004) * 0.5 + 0.5;
    return mix(1.0, 0.35 + 1.25 * band * (0.6 + 0.4 * swell), Wind.z);
}

vec3 sway(vec3 world, float t, float leaf) {
    // Phase varies across the world so neighbouring trees move out of step, but smoothly, so touching blocks share
    // their shared corners' motion and no gaps open.
    float phase = dot(world, vec3(0.37, 0.19, 0.61));
    float g = gust(world.xz, t);
    // Main sway, mostly downwind (+x, +z), plus a quicker flutter in every direction; leaves flutter more.
    float main = sin(t * 1.35 + phase) * 0.55 + sin(t * 0.77 + phase * 0.6) * 0.45;
    vec3 d;
    d.x = main * 0.9 + sin(t * 3.9 + phase * 2.3) * (0.12 + 0.18 * leaf);
    d.z = main * 0.6 + cos(t * 3.3 + phase * 1.9) * (0.12 + 0.18 * leaf);
    d.y = sin(t * 2.7 + phase * 1.4) * 0.12 * leaf;
    // Downwind lean that grows with the gust.
    d.xz += vec2(0.35, 0.22) * (g - 0.6);
    return d * g;
}

// A whole tree bending: slow, mostly downwind, growing faster than linearly with height so the base stays planted.
// The phase changes slowly across the ground, so a trunk and its crown bend together.
vec3 treeBend(vec3 world, float t, float h) {
    float phase = dot(world.xz, vec2(0.045, 0.031));
    float g = gust(world.xz, t);
    float s = sin(t * 0.85 + phase) * 0.6 + sin(t * 0.53 + phase * 1.7) * 0.4;
    vec2 d = vec2(0.9, 0.6) * s * 0.6 + vec2(0.35, 0.22) * (g - 0.4);
    float amount = pow(h, 1.5) * 0.0045 * g;
    return vec3(d.x * amount, -abs(amount) * 0.08, d.y * amount);
}

void main() {
    int code = int(Color.a * 255.0 + 0.5);
    float weight = 0.0;
    float leaf = 0.0;
    if (code == 254) {
        weight = 0.075;
        leaf = 1.0;
    } else if (code == 253) {
        weight = 0.05;
        leaf = 1.0;
    } else if (code >= 150 && code <= 250) {
        weight = float(code - 150) / 50.0 * 0.13;
    }
    float tree = -1.0;
    if (code >= 100 && code <= 149) {
        tree = float(code - 100) * 0.5;
    } else if (code >= 2 && code <= 99) {
        tree = float(code - 2) * 0.25;
        weight = 0.075;
        leaf = 1.0;
    }
    vec4 color = Color;
    if (code >= 2 && code <= 254) {
        color.a = 1.0;
    }

    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    if (weight > 0.0) {
        vec3 world = Position + vec3(ChunkPosition);
        pos += sway(world, Wind.x, leaf) * weight * Wind.y;
    }
    if (tree >= 0.0) {
        pos += treeBend(Position + vec3(ChunkPosition), Wind.x, tree) * Wind.y;
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    //? if >=26.3 {
    /*float fullyVisible = 16.0;
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((length(pos) - fullyVisible) / fullyVisible, 0.0, 1.0));
    *///?}

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    relPos = pos;
    worldPos = Position + vec3(ChunkPosition);
    skyLight = float(UV2.y) / 240.0;
}

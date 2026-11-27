#version 330

// Vanilla's terrain vertex shader plus rolling waves for water. The chunk builder stores the kind of water in the
// alpha of each water vertex's colour (see RealisticWater): 224 still, 192..223 flowing (direction in 32 steps), 160
// falling. Everything else keeps vanilla's alpha and passes through untouched.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
// 26.3 moved the terrain view matrix and atlas size out of the chunk section uniform.
//? if >=26.3 {
/*#moj_import <minecraft:terrainglobals.glsl>
*///?}
#moj_import <minecraft:projection.glsl>
#moj_import <templenz:sample_lightmap.glsl>

layout(std140) uniform WaterConfig {
    vec4 Look;      // x = seconds, y = intensity, z = waves, w = world reflections
    vec4 Flags;     // x = foam, y = caustics, z = camera under water, w = 1 when clip z is 0..1
    vec4 Sun;       // xy = towards the sun (east is +x), w = daylight
    vec4 Sky;       // rgb = sky colour, a = rain
    vec4 Camera;    // xyz = camera position (x and z wrapped at 8192)
    mat4 ViewProj;  // camera-relative world -> clip
    mat4 InvViewProj;
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
out vec3 relPos;
out vec4 tint;
out vec3 lightColor;
flat out float waterKind;  // 0 not water, 1 still, 2 flowing, 3 falling, 4 hidden (droplets stand in for it)
flat out vec2 flowDir;

// The swell: a few long waves from different directions, small enough to stay inside the block.
float swell(vec2 p, float t) {
    float h = sin(dot(p, vec2(0.38, 0.21)) + t * 1.1) * 0.5;
    h += sin(dot(p, vec2(-0.27, 0.46)) + t * 1.4) * 0.3;
    h += sin(dot(p, vec2(0.71, -0.53)) + t * 2.1) * 0.14;
    h += sin(dot(p, vec2(-0.93, -0.88)) + t * 2.9) * 0.06;
    return h;
}

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    int tag = int(Color.a * 255.0 + 0.5);
    vec4 color = Color;
    waterKind = 0.0;
    flowDir = vec2(0.0);
    if (tag == 224) {
        waterKind = 1.0;
    } else if (tag >= 192 && tag < 224) {
        waterKind = 2.0;
        float a = float(tag - 192) / 32.0 * 6.2831853;
        flowDir = vec2(cos(a), sin(a));
    } else if (tag == 160) {
        waterKind = 3.0;
    } else if (tag == 150) {
        waterKind = 4.0;
    }
    if (waterKind > 0.0) {
        color.a = 1.0;
        // Only the surface rolls: vertices at the bottom of a block (the foot of a side face) stay put.
        if (Look.z > 0.0 && fract(Position.y) > 0.02 && waterKind < 2.5) {
            vec2 world = pos.xz + Camera.xz;
            float amp = 0.07 * Look.z * Look.y * (1.0 + Sky.a * 0.6);
            pos.y += (swell(world, Look.x) - 0.5) * amp;
        }
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    lightColor = sample_lightmap(Sampler2, UV2).rgb;
    vertexColor = color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    relPos = pos;
    tint = color;
}

#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <templenz:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 tint;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out float facing;
out float shade;

void main() {
#ifdef SHELL
    vec3 pos = Position + normalize(Normal) * (float(UV1.x) / 1000.0);
    ivec2 overlayAt = ivec2(0, UV1.y);
#else
    vec3 pos = Position;
    ivec2 overlayAt = UV1;
#endif
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
#ifdef DEPTH_NUDGE
    gl_Position.z *= 1.0015;
#endif

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    tint = Color;
    lightMapColor = sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, overlayAt, 0);
    texCoord0 = UV0;
    vec3 n = normalize(mat3(ModelViewMat) * Normal);
    facing = abs(n.z);
    shade = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, vec4(1.0)).r;
}

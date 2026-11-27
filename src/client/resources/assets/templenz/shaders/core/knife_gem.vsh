#version 330

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

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 lightMapColor;
out vec2 texCoord0;
out vec3 viewNormal;
out vec3 viewPos;
out vec3 localPos;
flat out int finish;
flat out int material;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    lightMapColor = sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    viewNormal = mat3(ModelViewMat) * Normal;
    viewPos = view.xyz;
    localPos = (Color.rgb * 255.0 - 128.0) * 0.5;
    finish = UV1.x;
    material = UV1.y;
}

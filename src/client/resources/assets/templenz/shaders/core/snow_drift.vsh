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
out vec4 vertexColor;
out vec3 lightColor;
out vec2 rawLight;
out vec2 data;
out vec3 worldPos;
out vec3 viewPos;

void main() {
    vec3 local = Position;
    if (UV1.x == 2) {
        vertexColor = Color;
        data = UV0 + vec2(8.0, 0.0);
    } else {
        float built = Color.a * 0.06;
        float since = clamp(UV0.x * TextureMat[1].y, -built, UV0.y);
        local.y += since;
        vertexColor = vec4(Color.rgb, smoothstep(0.0, 0.03, built + since));
        data = Normal.xz;
    }
    vec3 pos = local + ModelOffset;
    vec4 view = ModelViewMat * vec4(pos, 1.0);
    gl_Position = ProjMat * view;
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    lightColor = sample_lightmap(Sampler2, UV2).rgb;
    rawLight = vec2(UV2) / 240.0;
    worldPos = local + TextureMat[3].xyz;
    viewPos = view.xyz;
}

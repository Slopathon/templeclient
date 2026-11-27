#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
in float LineWidth;

out vec3 viewPos;
out vec3 viewNormal;
out vec4 baseColor;
out vec2 patternUV;
out vec3 accentColor;
out vec2 lightUV;
out float accentWeight;
out float occlusion;
flat out int finishIndex;
flat out int patternIndex;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    viewPos = view.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    baseColor = Color;
    patternUV = UV0;

    int fp = UV1.x & 0xFFFF;
    int ao = UV1.y & 0xFFFF;
    finishIndex = fp & 0xFF;
    patternIndex = (fp >> 8) & 0xFF;
    accentWeight = float(ao & 0xFF) / 255.0;
    occlusion = float((ao >> 8) & 0xFF) / 255.0;

    int rgb = int(LineWidth + 0.5);
    accentColor = vec3(float((rgb >> 16) & 0xFF), float((rgb >> 8) & 0xFF), float(rgb & 0xFF)) / 255.0;

    lightUV = vec2(UV2);
}

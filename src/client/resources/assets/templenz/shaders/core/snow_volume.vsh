#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec3 rel;
out float shellDistance;
out float shellSpan;

void main() {
    float i = UV0.x;
    float d = TextureMat[0].x * pow(TextureMat[0].y, i);
    float next = d * TextureMat[0].y;
    rel = Position * d;
    shellDistance = d;
    shellSpan = next - d;
    gl_Position = ProjMat * ModelViewMat * vec4(rel, 1.0);
}

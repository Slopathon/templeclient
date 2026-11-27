#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 tint;
out vec2 texCoord0;
out float rim;
out float look;
out float speed;
out vec3 viewNormal;
out vec3 viewDir;
out vec3 localPos;

void main() {
#ifdef GLOW
    vec3 pos = Position + normalize(Normal) * (float(UV1.x) / 1000.0);
    look = 0.0;
    speed = 1.0;
#else
    vec3 pos = Position;
    look = float(UV1.x);
    speed = float(UV1.y) / 100.0;
#endif
    vec4 view = ModelViewMat * vec4(pos, 1.0);
    gl_Position = ProjMat * view;
#ifdef DEPTH_NUDGE
    gl_Position.z *= 1.0015;
#endif
    tint = Color;
    texCoord0 = UV0;
    vec3 n = normalize(mat3(ModelViewMat) * Normal);
    viewNormal = n;
    viewDir = normalize(-view.xyz);
    localPos = pos;
    rim = 1.0 - abs(dot(n, viewDir));
}

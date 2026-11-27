#version 330

layout(std140) uniform SceneConfig {
    mat4 ViewProj;
    mat4 InvViewProj;
    vec4 CameraPos;
    vec4 SunDir;
    vec4 KeyDir;
    vec4 SkyTop;
    vec4 SkyHorizon;
    vec4 SunColor;
    vec4 FogColor;
    vec4 Accent;
    vec4 Params;
    vec4 Pointer;
    vec4 Click;
    vec4 Shooting[4];
    mat4 Letters[12];
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

out vec3 worldPos;
out vec3 worldNormal;
out vec2 texCoord;
out vec3 tint;
flat out int kind;

void main() {
    int id = int(Color.a * 255.0 + 0.5);
    vec3 p = Position;
    vec3 n = Normal;
    if (id < 16) {
        mat4 m = Letters[id];
        p = (m * vec4(p, 1.0)).xyz;
        n = mat3(m) * n;
    } else if (id == 252) {
        float t = Params.x * 1.6 + p.x * 0.35 + p.z * 0.23;
        p.x += sin(t) * 0.07 * Params.y;
        p.z += cos(t * 0.8) * 0.05 * Params.y;
    } else if (id == 253) {
        p.y += sin(Params.x * 1.3 + p.x * 0.9 + p.z * 0.7) * 0.025;
    }
    worldPos = p;
    worldNormal = n;
    texCoord = UV0;
    tint = Color.rgb;
    kind = id;
    gl_Position = ViewProj * vec4(p, 1.0);
}

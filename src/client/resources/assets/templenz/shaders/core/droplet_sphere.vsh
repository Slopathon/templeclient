#version 330

layout(std140) uniform DropletConfig {
    mat4 ViewProj;
    mat4 InvViewProj;
    vec4 Right;
    vec4 Up;
    vec4 Sun;
    vec4 Sky;
    vec4 Screen;
    vec4 Time;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec3 billboard;
out vec3 centre;
out vec2 corner;
out float fade;

void main() {
    vec3 pos = Position + (Right.xyz * UV0.x + Up.xyz * UV0.y) * Up.w * 1.1;
    gl_Position = ViewProj * vec4(pos, 1.0);
    billboard = pos;
    centre = Position;
    corner = UV0 * 1.1;
    fade = Color.a;
}

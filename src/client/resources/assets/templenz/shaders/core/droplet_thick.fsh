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

in vec3 billboard;
in vec3 centre;
in vec2 corner;
in float fade;

out vec4 fragColor;

void main() {
    float r2 = dot(corner, corner);
    if (r2 > 1.0) {
        discard;
    }
    fragColor = vec4(2.0 * sqrt(1.0 - r2) * Up.w * fade * 0.39, 0.0, 0.0, 1.0);
}

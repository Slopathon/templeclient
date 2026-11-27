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
    vec3 toCamera = -normalize(centre);
    vec3 surface = billboard + toCamera * sqrt(1.0 - r2) * Up.w;
    vec4 clip = ViewProj * vec4(surface, 1.0);
    float z = clip.z / clip.w;
    gl_FragDepth = Right.w > 0.5 ? z : z * 0.5 + 0.5;
    fragColor = vec4(length(surface), 1.0, fade, 1.0);
}

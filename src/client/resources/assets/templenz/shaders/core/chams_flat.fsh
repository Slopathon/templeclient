#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 tint;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in float facing;
in float shade;

out vec4 fragColor;

void main() {
    float shape = texture(Sampler0, texCoord0).a;
    if (shape < 0.1) {
        discard;
    }
    vec3 colour = tint.rgb;
#ifdef SHELL
    colour = mix(colour, colour * lightMapColor.rgb, 0.05) * mix(1.0, shade, 0.02);
    fragColor = vec4(mix(overlayColor.rgb, colour, clamp(overlayColor.a + 0.6, 0.0, 1.0)), tint.a);
    return;
#endif
    colour = mix(colour, min(colour * 1.12 + 0.025, vec3(1.0)), (1.0 - facing) * 0.3);
    colour *= mix(1.0, shade, 0.18);
    colour = mix(colour, colour * lightMapColor.rgb, 0.12);
    colour = mix(overlayColor.rgb, colour, clamp(overlayColor.a + 0.6, 0.0, 1.0));
    fragColor = vec4(colour, tint.a);
}

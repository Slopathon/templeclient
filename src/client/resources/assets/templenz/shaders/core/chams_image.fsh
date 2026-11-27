#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;
uniform sampler2D PictureSampler;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 tint;
in vec4 lightMapColor;
in float hurt;
in float strength;
in vec2 texCoord0;
in float facing;
in float shade;

out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) {
        discard;
    }
    ivec2 size = textureSize(PictureSampler, 0);
    float imageAspect = float(size.x) / max(float(size.y), 1.0);
    float screenAspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    if (screenAspect > imageAspect) {
        uv.y = (uv.y - 0.5) * imageAspect / screenAspect + 0.5;
    } else {
        uv.x = (uv.x - 0.5) * screenAspect / imageAspect + 0.5;
    }
    uv.y = 1.0 - uv.y;

    vec3 image = texture(PictureSampler, uv).rgb;
    float lum = dot(image, vec3(0.2126, 0.7152, 0.0722));
    vec3 hued = tint.rgb * lum * 1.45;
    hued = mix(hued, vec3(1.0), smoothstep(0.72, 1.0, lum) * 0.45);
    vec3 colour = mix(image, clamp(hued, 0.0, 1.0), strength);
    colour = mix(colour, min(colour * 1.3 + 0.05, vec3(1.0)), (1.0 - facing) * 0.4);
    colour *= mix(1.0, shade, 0.12);
    colour = mix(colour, colour * lightMapColor.rgb, 0.15);
    colour = mix(colour, vec3(1.0, 0.25, 0.25), clamp(hurt * 1.5, 0.0, 0.45));
    fragColor = vec4(colour, tint.a);
}

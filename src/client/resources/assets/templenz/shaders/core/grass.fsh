#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

layout(std140) uniform GrassConfig {
    vec4 Wind;
    vec4 Sun;
    vec4 Sky;
    vec4 Wetness;
    vec4 Fade;
    vec4 Thin;
    vec4 ThinB;
    vec4 Share;
    vec4 Pusher[12];
    vec4 PusherS[12];
};

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec3 bladeColor;
in vec3 lightColor;
in vec3 bladeNormal;
in vec3 relPos;
in float along;
in float skyLight;
in float pressed;
in float acrossBlade;
in float coverage;

out vec4 fragColor;

void main() {
    vec3 V = normalize(-relPos);
    vec3 N = normalize(bladeNormal);
    if (dot(N, V) < 0.0) {
        N = -N;
    }
    vec3 sun = normalize(Sun.xyz);
    float rain = Sky.a;
    float day = Sun.w;
    float direct = skyLight * skyLight * (1.0 - 0.8 * rain) * mix(0.18, 1.0, day);

    vec3 albedo = bladeColor * mix(0.46, 1.0, smoothstep(0.0, 0.85, along));
    albedo = mix(albedo, albedo * vec3(1.16, 1.1, 0.78), smoothstep(0.55, 1.0, along) * 0.3);
    albedo *= 1.0 + 0.1 * pressed;

    float open = smoothstep(0.945, 0.99, skyLight);
    float wet = Wetness.x * open;
    albedo *= mix(1.0, 0.72, wet);

    float facing = max(dot(N, sun), 0.0);
    vec3 colour = albedo * lightColor * mix(1.0, 0.8 + 0.3 * facing, direct);
    float through = pow(max(dot(-V, sun), 0.0), 3.0) * along * direct;
    colour += albedo * lightColor * vec3(0.95, 1.0, 0.4) * through * 0.5;

    float shine = Wind.w * (1.0 + 1.5 * wet) * (1.0 - smoothstep(8.0, 26.0, length(relPos)));
    vec3 H = normalize(sun + V);
    float spec = pow(max(dot(N, H), 0.0), 26.0) * smoothstep(0.1, 0.7, along);
    colour += mix(vec3(1.0, 0.97, 0.86), vec3(0.75, 0.82, 1.0), 1.0 - day) * spec * shine * direct * 0.6;
    float rim = pow(1.0 - abs(dot(N, V)), 3.0) * along;
    float light = max(max(lightColor.r, lightColor.g), lightColor.b);
    colour += mix(FogColor.rgb, Sky.rgb, 0.6) * rim * shine * 0.14 * skyLight * light;

    float edge = 1.0 - smoothstep(1.0 - fwidth(acrossBlade) * 1.2, 1.0, abs(acrossBlade));
    float alpha = clamp(edge * coverage, 0.0, 1.0);
    if (alpha < 0.02) {
        discard;
    }
    fragColor = apply_fog(vec4(colour, alpha), sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

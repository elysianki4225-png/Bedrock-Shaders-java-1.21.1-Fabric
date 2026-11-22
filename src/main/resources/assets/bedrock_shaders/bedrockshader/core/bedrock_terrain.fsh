#version 150

#moj_import <bedrock_global.glsl>
#moj_import <common_lighting.glsl>


uniform sampler2D Sampler0;
uniform float BedrockMaterialGloss;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 worldNormal;
in vec4 lightMapColor;
in vec3 worldPos;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (texel.a < 0.1) {
        discard;
    }
    vec3 albedo = texel.rgb * ColorModulator.rgb;
    vec3 normal = normalize(worldNormal * 2.0 - 1.0);
    vec3 lit = bs_shade_surface(albedo, normal, vertexColor, lightMapColor, BedrockMaterialGloss);

    if (BedrockWaterline > 0.01) {
        float under = smoothstep(BedrockWaterSurfaceHeight - 0.1, BedrockWaterSurfaceHeight + 0.4, worldPos.y);
        lit += bs_caustics(worldPos.xz, BedrockGameTime) * (1.0 - under) * BedrockWaterline * albedo * 0.6;
        lit *= mix(vec3(0.55, 0.78, 1.0), vec3(1.0), under);
    }

    lit = bs_apply_fog(lit, vertexDistance);
    fragColor = vec4(pow(bs_saturate(lit), vec3(1.0 / 2.2)) * ColorModulator.a, texel.a * vertexColor.a);
}

#version 150

#moj_import <bedrock_global.glsl>
#moj_import <common_lighting.glsl>
#moj_import <bedrock_translucent.glsl>


uniform sampler2D Sampler0;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 worldNormal;
in vec3 worldPos;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    vec4 color = texel * vertexColor * ColorModulator;
    if (color.a < 0.02) {
        discard;
    }

    // Vanilla normalises the block normal into 0..1 when it is put in the vertex
    // stream, so decode it back to -1..1 before shading.
    vec3 normal = normalize(worldNormal * 2.0 - 1.0);
    float upFacing = smoothstep(0.55, 0.95, normal.y);

    vec3 lit = color.rgb;
    if (upFacing > 0.01) {
        // Bedrock water: wave normals, fresnel reflection and a sun glint.
        vec3 waterNormal = bs_water_normal(worldPos, normal);
        vec3 v = normalize(-(BedrockViewMat * vec4(0.0, 0.0, 0.0, 1.0)).xyz);
        vec3 h = normalize(bs_sunDir() + v);
        float spec = pow(max(dot(waterNormal, h), 0.0), 220.0) * BS_SUN_I;

        float fresnel = mix(0.03, 0.92, pow(1.0 - max(dot(waterNormal, v), 0.0), 4.0));
        vec3 reflectWorld = mat3(BedrockInvViewMat) * reflect(-v, waterNormal);
        vec3 reflected = bs_sky_radiance(reflectWorld);

        vec3 water = mix(color.rgb * BedrockWaterColorLinear * 2.4, reflected,
                fresnel * BedrockWaterReflection * upFacing);
        water += BedrockSunColorLinear * spec * upFacing;
        color.rgb = mix(color.rgb, water, upFacing * 0.85);
    }

    vec3 litResult = bs_apply_fog(color.rgb, vertexDistance);
    // Linear output; see the note in bedrock_terrain.fsh.
    fragColor = vec4(max(litResult, vec3(0.0)), color.a);
}

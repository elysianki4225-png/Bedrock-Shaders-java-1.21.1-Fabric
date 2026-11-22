#version 400

#moj_import <bedrock_global.glsl>
#moj_import <common_lighting.glsl>
#moj_import <bedrock_water.glsl>


uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 lightMapColor;
in vec3 worldPos;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (texel.a < 0.05) {
        discard;
    }
    vec3 flatNormal = vec3(0.0, 1.0, 0.0);
    vec3 n = bs_water_normal(worldPos, flatNormal);
    vec3 v = normalize(-(BedrockViewMat * vec4(0.0, 0.0, 0.0, 1.0)).xyz);

    float skyLight = lightMapColor.r;
    float shore = smoothstep(0.0, 2.4, skyLight);

    vec3 sunDir = bs_sunDir();
    vec3 h = normalize(sunDir + v);
    float NdotH = max(dot(n, h), 0.0);
    float spec = pow(NdotH, 240.0) * BS_SUN_I;

    float fresnel = pow(1.0 - max(dot(n, v), 0.0), 4.0);
    fresnel = mix(0.02, 1.0, fresnel);

    vec3 reflectDir = reflect(-v, n);
    vec3 reflectWorld = mat3(BedrockInvViewMat) * reflectDir;
    float hit = bs_reflection_march(worldPos, reflectWorld, 20.0);
    vec3 reflected = bs_sky_radiance(reflectWorld);
    if (hit > 0.0) {
        vec4 clip = ProjMat * (BedrockViewMat * vec4(worldPos + reflectWorld * 1.5, 1.0));
        vec2 uv = (clip.xy / clip.w) * 0.5 + 0.5;
        reflected = mix(reflected, texture(BedrockSceneSampler, uv).rgb, hit);
    }

    vec4 clipSelf = ProjMat * (BedrockViewMat * vec4(worldPos, 1.0));
    vec2 screenUV = (clipSelf.xy / clipSelf.w) * 0.5 + 0.5;
    vec2 distort = bs_water_wave(worldPos.xz) * BedrockWaterRefraction * 4.0;
    vec3 refracted = texture(BedrockSceneSampler, clamp(screenUV + distort, vec2(0.001), vec2(0.999))).rgb;

    vec3 base = mix(refracted * BedrockWaterColorLinear, BedrockWaterColorLinear * texel.rgb, 0.35);
    base = mix(BedrockWaterSurfaceColorLinear, base, 0.35 + 0.65 * shore);
    base += bs_water_caustics(worldPos.xz) * BedrockSunColorLinear * 0.35;

    vec3 water = mix(base, reflected, fresnel * BedrockWaterReflection);
    water += BedrockSunColorLinear * spec * 0.6;
    water = mix(water, BedrockWaterSurfaceColorLinear, (1.0 - BedrockWaterTransparency) * 0.5);

    water = bs_apply_fog(water, vertexDistance);
    fragColor = vec4(pow(bs_saturate(water), vec3(1.0 / 2.2)), 1.0);
}


// ---------------------------------------------------------------------------
//  Shared surface lighting used by blocks, entities and water.
//  Vanilla supplies shading through vertex colour * lightmap; we keep that as
//  the diffuse base and layer Bedrock's directional sun/moon response on top.
// ---------------------------------------------------------------------------

uniform float BedrockWaterline;
uniform float BedrockWaterSurfaceHeight;

vec3 bs_shade_surface(vec3 albedo, vec3 normal, vec4 vertexColor, vec4 lightMap, float gloss) {
    if (gloss < 0.01) {
        return albedo * vertexColor.rgb * lightMap.rgb;
    }
    vec3 n = normalize(normal);
    vec3 v = normalize(-(BedrockViewMat * vec4(0.0, 0.0, 0.0, 1.0)).xyz);
    vec3 l = bs_sunDir();
    vec3 m = bs_moonDir();

    float NdotL = max(dot(n, l), 0.0);
    float NdotM = max(dot(n, m), 0.0);
    float NdotV = max(dot(n, v), 1.0e-4);
    vec3 h = normalize(l + v);
    float NdotH = max(dot(n, h), 0.0);
    float VdotH = max(dot(v, h), 0.0);

    vec3 sunColor = vec3(1.0, 0.78, 0.55) * BS_SUN_I;
    vec3 moonColor = vec3(0.55, 0.68, 1.0) * BS_MOON_I;
    vec3 lightColor = sunColor + moonColor + vec3(0.06, 0.08, 0.12);

    float roughness = mix(0.85, 0.18, gloss);
    float D = bs_distribution_ggx(NdotH, roughness);
    float G = bs_geometry_smith(NdotV, NdotL, roughness);
    vec3 F = bs_fresnel_schlick(VdotH, vec3(0.04));
    vec3 spec = (D * G / (4.0 * NdotV * NdotL + 1.0e-4)) * F * NdotL * lightColor * gloss;

    vec3 ambient = bs_sky_radiance(n) * 0.11;
    vec3 diffuse = (sunColor * NdotL + moonColor * NdotM) * 0.55 + ambient;
    return albedo * vertexColor.rgb * lightMap.rgb * diffuse + spec + ambient * albedo * 0.08;
}

// Animated caustics projected onto submerged terrain.
float bs_caustics(vec2 p, float time) {
    p *= 0.65;
    float a = sin(p.x * 2.1 + time * 0.9) + sin(p.y * 2.7 - time * 0.7);
    float b = sin((p.x + p.y) * 1.9 + time * 0.5) + sin((p.x - p.y) * 2.3 - time * 1.1);
    return pow(bs_saturate(1.0 - abs(a * b) * 0.22), 3.0);
}

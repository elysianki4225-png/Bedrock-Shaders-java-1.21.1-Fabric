#version 400

uniform sampler2D Sampler0;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 worldNormal;
in float materialGloss;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (texel.a < 0.02) {
        discard;
    }
    vec4 color = texel * vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    vec3 n = normalize(worldNormal * 2.0 - 1.0);
    vec3 l = bs_sunDir();
    float NdotL = max(dot(n, l), 0.0);
    vec3 sunColor = vec3(1.0, 0.78, 0.55) * BS_SUN_I;
    float ambient = 0.35 + 0.65 * max(l.y, 0.0);
    vec3 lit = color.rgb * lightMapColor.rgb * (sunColor * (0.35 + 0.65 * NdotL) + ambient * 0.35);

    // Light basalt/glint style highlights on shiny materials (armour, amethyst...).
    if (materialGloss > 0.01) {
        vec3 v = normalize(-(BedrockViewMat * vec4(0.0, 0.0, 0.0, 1.0)).xyz);
        vec3 h = normalize(l + v);
        float spec = pow(max(dot(n, h), 0.0), 48.0) * BS_SUN_I * materialGloss;
        lit += sunColor * spec;
    }

    lit = bs_apply_fog(lit, vertexDistance);
    fragColor = vec4(pow(bs_saturate(lit), vec3(1.0 / 2.2)), color.a);
}

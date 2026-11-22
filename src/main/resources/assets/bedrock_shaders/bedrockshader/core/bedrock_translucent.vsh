#version 150

#moj_import <bedrock_global.glsl>
#moj_import <common_lighting.glsl>
#moj_import <bedrock_translucent.glsl>


in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform vec3 ChunkOffset;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 worldNormal;
out vec3 worldPos;

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    worldPos = pos;
    vertexDistance = bs_fog_distance(pos, FogShape);
    vertexColor = Color;
    texCoord0 = UV0;
    worldNormal = Normal;
}

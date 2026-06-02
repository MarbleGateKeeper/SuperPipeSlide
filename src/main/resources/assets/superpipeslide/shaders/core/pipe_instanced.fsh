#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

layout(std140) uniform PipeRenderState {
    vec4 PipeRenderStateData;
    vec4 PipeRenderCameraData;
    vec4 PipeExternalLightingData;
    mat4 PipeShadowViewProjection;
};

#if !defined(SHADOW_PASS) && !defined(EMISSIVE)
uniform sampler2D Sampler2;
uniform sampler2D PipeShadowSampler;
uniform sampler2D PipeShadowWithPipesSampler;
#endif

#ifndef SHADOW_PASS
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifndef EMISSIVE
in vec3 pipeShadowPosition;
in vec3 pipeNormal;
#endif
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif

#ifndef EMISSIVE
in vec4 lightMapColor;
in vec2 lightMapUv;
#endif
#endif

in vec2 texCoord0;

out vec4 fragColor;

#if !defined(SHADOW_PASS) && !defined(EMISSIVE)
const float SUPERPIPESLIDE_LIGHTMAP_MAX = 240.0;

float superpipeslide_shadow_compare(sampler2D shadowSampler, vec2 uv, float currentDepth, float bias) {
    return currentDepth <= texture(shadowSampler, uv).r + bias ? 1.0 : 0.0;
}

float superpipeslide_pipe_shadow_compare(vec2 uv, float currentDepth, float bias) {
    float worldDepth = texture(PipeShadowSampler, uv).r;
    float depthWithPipes = texture(PipeShadowWithPipesSampler, uv).r;
    bool hasPipeOccluder = depthWithPipes + bias < worldDepth;
    return !hasPipeOccluder || currentDepth <= depthWithPipes + bias ? 1.0 : 0.0;
}

float superpipeslide_bilinear_shadow_compare_with_texel(sampler2D shadowSampler, vec2 uv, float currentDepth, float bias, vec2 size, vec2 texel) {
    vec2 texelPosition = uv * size - 0.5;
    vec2 base = floor(texelPosition);
    vec2 blend = fract(texelPosition);
    vec2 uv00 = (base + 0.5) / size;
    float s00 = superpipeslide_shadow_compare(shadowSampler, uv00, currentDepth, bias);
    float s10 = superpipeslide_shadow_compare(shadowSampler, uv00 + vec2(texel.x, 0.0), currentDepth, bias);
    float s01 = superpipeslide_shadow_compare(shadowSampler, uv00 + vec2(0.0, texel.y), currentDepth, bias);
    float s11 = superpipeslide_shadow_compare(shadowSampler, uv00 + texel, currentDepth, bias);
    return mix(mix(s00, s10, blend.x), mix(s01, s11, blend.x), blend.y);
}

float superpipeslide_soft_shadow_compare(sampler2D shadowSampler, vec2 uv, float currentDepth, float bias) {
    vec2 size = vec2(textureSize(shadowSampler, 0));
    vec2 texel = 1.0 / size;
    vec2 offsetA = vec2(1.6, 0.55) * texel;
    vec2 offsetB = vec2(-0.55, 1.6) * texel;
    float lit = superpipeslide_bilinear_shadow_compare_with_texel(shadowSampler, uv, currentDepth, bias, size, texel) * 0.4;
    lit += superpipeslide_bilinear_shadow_compare_with_texel(shadowSampler, uv + offsetA, currentDepth, bias, size, texel) * 0.15;
    lit += superpipeslide_bilinear_shadow_compare_with_texel(shadowSampler, uv - offsetA, currentDepth, bias, size, texel) * 0.15;
    lit += superpipeslide_bilinear_shadow_compare_with_texel(shadowSampler, uv + offsetB, currentDepth, bias, size, texel) * 0.15;
    lit += superpipeslide_bilinear_shadow_compare_with_texel(shadowSampler, uv - offsetB, currentDepth, bias, size, texel) * 0.15;
    return lit;
}

float superpipeslide_bilinear_pipe_shadow_compare(vec2 uv, float currentDepth, float bias) {
    vec2 size = vec2(textureSize(PipeShadowWithPipesSampler, 0));
    vec2 texelPosition = uv * size - 0.5;
    vec2 base = floor(texelPosition);
    vec2 blend = fract(texelPosition);
    vec2 uv00 = (base + 0.5) / size;
    vec2 texel = 1.0 / size;
    float s00 = superpipeslide_pipe_shadow_compare(uv00, currentDepth, bias);
    float s10 = superpipeslide_pipe_shadow_compare(uv00 + vec2(texel.x, 0.0), currentDepth, bias);
    float s01 = superpipeslide_pipe_shadow_compare(uv00 + vec2(0.0, texel.y), currentDepth, bias);
    float s11 = superpipeslide_pipe_shadow_compare(uv00 + texel, currentDepth, bias);
    return mix(mix(s00, s10, blend.x), mix(s01, s11, blend.x), blend.y);
}

vec4 superpipeslide_shadow_clip(vec3 shadowPosition) {
    vec4 shadowClip = PipeShadowViewProjection * vec4(shadowPosition, 1.0);
    if (PipeExternalLightingData.w > 0.0) {
        float vertexRadius = length(shadowClip.xy);
        float distortFactor = vertexRadius * PipeExternalLightingData.w + (1.0 - PipeExternalLightingData.w);
        shadowClip.xy /= distortFactor;
    }
    shadowClip.z *= 0.2;
    return shadowClip;
}

float superpipeslide_shadow_factor(vec3 shadowPosition, vec3 normal) {
    if (PipeRenderStateData.z < 0.5) {
        return 1.0;
    }

    vec3 biasedShadowPosition = shadowPosition + normalize(normal) * PipeExternalLightingData.z;
    vec4 shadowClip = superpipeslide_shadow_clip(biasedShadowPosition);
    if (shadowClip.w <= 0.0001) {
        return 1.0;
    }

    vec3 shadowNdc = shadowClip.xyz / shadowClip.w;
    vec2 shadowUv = shadowNdc.xy * 0.5 + 0.5;
    if (any(lessThan(shadowUv, vec2(0.0))) || any(greaterThan(shadowUv, vec2(1.0)))) {
        return 1.0;
    }

    float currentDepth = PipeRenderStateData.w >= 0.5 ? shadowNdc.z : shadowNdc.z * 0.5 + 0.5;
    float lit = superpipeslide_soft_shadow_compare(PipeShadowSampler, shadowUv, currentDepth, PipeExternalLightingData.y);

    vec4 pipeShadowClip = superpipeslide_shadow_clip(shadowPosition);
    if (pipeShadowClip.w > 0.0001) {
        vec3 pipeShadowNdc = pipeShadowClip.xyz / pipeShadowClip.w;
        vec2 pipeShadowUv = pipeShadowNdc.xy * 0.5 + 0.5;
        if (!any(lessThan(pipeShadowUv, vec2(0.0))) && !any(greaterThan(pipeShadowUv, vec2(1.0)))) {
            float pipeCurrentDepth = PipeRenderStateData.w >= 0.5 ? pipeShadowNdc.z : pipeShadowNdc.z * 0.5 + 0.5;
            float pipeBias = max(PipeExternalLightingData.y, 0.0005);
            lit = min(lit, superpipeslide_bilinear_pipe_shadow_compare(pipeShadowUv, pipeCurrentDepth, pipeBias));
        }
    }
    return mix(1.0 - PipeExternalLightingData.x, 1.0, lit);
}

vec4 superpipeslide_shadowed_lightmap(float shadowFactor) {
    if (shadowFactor >= 0.999) {
        return lightMapColor;
    }
    vec2 shadowedLightMapUv = clamp(vec2(lightMapUv.x, lightMapUv.y * shadowFactor), vec2(0.0), vec2(SUPERPIPESLIDE_LIGHTMAP_MAX));
    return sample_lightmap(Sampler2, ivec2(shadowedLightMapUv + vec2(0.5)));
}
#endif

void main() {
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

#ifdef SHADOW_PASS
    fragColor = vec4(1.0);
#else
#ifndef EMISSIVE
    float shadowFactor = lightMapUv.y <= 0.5 ? 1.0 : superpipeslide_shadow_factor(pipeShadowPosition, pipeNormal);
#endif
#ifdef PER_FACE_LIGHTING
    vec4 faceVertexColor = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
#else
    vec4 faceVertexColor = vertexColor;
#endif

#ifdef DISSOLVE
    if (faceVertexColor.a < texture(DissolveMaskSampler, texCoord0).a) {
        discard;
    }
    faceVertexColor.a = 1.0;
#endif

    color *= faceVertexColor * ColorModulator;
#ifndef EMISSIVE
    color *= superpipeslide_shadowed_lightmap(shadowFactor);
#endif

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#endif
}

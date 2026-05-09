#version 330

uniform sampler2D InSampler;

layout(std140) uniform FoldTraversal {
    vec4 Params0;    // intensity, phaseProgress, approachProgress, life
    vec4 Params1;    // speed, phaseCode, dimensionFold, waitingForCommit
    vec4 Params2;    // timeSeconds, seed01, aspect, cancel
    vec4 FoldFrame;  // focusUv.xy, axisUv.xy
    vec4 FoldState;  // foldAmount, exitBlend, focusVisible, focusFront
    vec4 ColorA;     // primary rgb, chroma scale
    vec4 ColorB;     // secondary rgb, vignette scale
};

in vec2 texCoord;

out vec4 fragColor;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float easeOut(float value) {
    float t = saturate(value);
    return 1.0 - pow(1.0 - t, 3.0);
}

float hash11(float p) {
    return fract(sin(p * 127.1) * 43758.5453123);
}

vec2 aspectFromUv(vec2 uv, float aspect) {
    vec2 p = uv - vec2(0.5);
    p.x *= max(aspect, 0.001);
    return p;
}

vec2 uvFromAspect(vec2 p, float aspect) {
    vec2 uv = p;
    uv.x /= max(aspect, 0.001);
    return uv + vec2(0.5);
}

void main() {
    float rawIntensity = Params0.x;
    float intensity = saturate(rawIntensity / 1.72);
    if (intensity <= 0.001) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    float phaseProgress = saturate(Params0.y);
    float approachProgress = saturate(Params0.z);
    float life = saturate(Params0.w);
    float speed = saturate(Params1.x);
    float phase = Params1.y;
    float dimensionFold = saturate(Params1.z);
    float waitingForCommit = saturate(Params1.w);
    float timeSeconds = Params2.x;
    float seed = Params2.y;
    float aspect = max(Params2.z, 0.001);
    float cancel = saturate(Params2.w);
    vec2 focusUv = clamp(FoldFrame.xy, vec2(0.035), vec2(0.965));
    vec2 axisUv = FoldFrame.zw;
    float foldAmount = saturate(FoldState.x);
    float exitBlend = saturate(FoldState.y);
    float focusVisible = saturate(FoldState.z);
    vec3 primary = ColorA.rgb;
    vec3 secondary = ColorB.rgb;
    float chromaScale = ColorA.a;
    float vignetteScale = ColorB.a;

    vec2 focus = aspectFromUv(focusUv, aspect);
    vec2 uvp = aspectFromUv(texCoord, aspect);
    vec2 axis = vec2(axisUv.x * aspect, axisUv.y);
    if (dot(axis, axis) < 0.0001) {
        axis = vec2(0.0, -1.0);
    }
    axis = normalize(axis);
    vec2 normal = vec2(-axis.y, axis.x);

    vec2 local = uvp - focus;
    float along = dot(local, axis);
    float lateral = dot(local, normal);
    float absAlong = abs(along);
    float absLateral = abs(lateral);

    float contact = (phase > 0.5 && phase < 1.5) ? 1.0 : 0.0;
    float tunnel = (phase > 1.5 && phase < 3.5) ? 1.0 : 0.0;
    float exit = (phase > 3.5 && phase < 5.5) ? 1.0 : 0.0;
    float approach = 1.0 - max(contact, max(tunnel, exit));
    float activeFold = saturate(max(foldAmount, approachProgress * approach) * life);
    float kindBoost = 1.0 + (1.0 - dimensionFold) * 0.30 + dimensionFold * 0.18;
    float power = saturate(intensity * (0.38 + activeFold * 0.82) * kindBoost);

    float foldLine = exp(-absAlong * (5.4 + dimensionFold * 2.0));
    float broadField = smoothstep(1.35, 0.0, absAlong) * smoothstep(1.10, 0.0, absLateral * 0.68);
    float hinge = saturate((foldLine * 0.74 + broadField * 0.46) * power);
    float edge = smoothstep(0.62, 1.35, length(uvp));
    float flow = timeSeconds * (0.68 + speed * 0.72) + seed * 19.17;

    float compress = power * (0.18 + activeFold * 0.30 + tunnel * 0.08 + waitingForCommit * 0.05);
    float directionSign = mix(-1.0, 1.0, exitBlend);
    float side = sign(along + 0.0001);
    float foldedAlong = along * (1.0 - compress * (0.55 + broadField * 0.95));
    foldedAlong -= directionSign * hinge * (0.09 + speed * 0.045);

    float pageBend = sin(lateral * (6.5 + dimensionFold * 2.0) + flow * 2.2) * hinge * (0.026 + speed * 0.016);
    float splitShear = side * hinge * (0.065 + speed * 0.035 + dimensionFold * 0.020);
    float lateralScale = 1.0 + hinge * (0.16 + dimensionFold * 0.10);
    float foldedLateral = lateral * lateralScale + splitShear + pageBend;

    vec2 warpedAspect = focus + axis * foldedAlong + normal * foldedLateral;
    warpedAspect += axis * sin(lateral * 9.0 - flow * 2.8) * broadField * power * 0.018;
    warpedAspect += normal * sin(along * 11.0 + flow * 2.1 + seed * 6.283) * broadField * power * 0.014;

    vec2 uvMain = clamp(uvFromAspect(warpedAspect, aspect), vec2(0.001), vec2(0.999));
    vec2 layerOffset = (axis * (0.035 + speed * 0.025) + normal * sin(flow) * 0.012) * hinge * (0.55 + dimensionFold * 0.30);
    vec2 uvLayerA = clamp(uvFromAspect(warpedAspect - layerOffset, aspect), vec2(0.001), vec2(0.999));
    vec2 uvLayerB = clamp(uvFromAspect(focus + axis * (along * (1.0 - compress * 1.55) + directionSign * hinge * 0.12) + normal * (lateral - splitShear * 0.65), aspect), vec2(0.001), vec2(0.999));

    float chroma = power * chromaScale * (0.0020 + hinge * 0.0065 + edge * 0.0022);
    vec2 chromaAxis = normalize(axis + normal * 0.22);
    vec4 baseSample = texture(InSampler, uvMain);
    vec3 base = vec3(
            texture(InSampler, clamp(uvMain + chromaAxis * chroma, vec2(0.001), vec2(0.999))).r,
            baseSample.g,
            texture(InSampler, clamp(uvMain - chromaAxis * chroma, vec2(0.001), vec2(0.999))).b
    );
    vec3 layerA = texture(InSampler, uvLayerA).rgb;
    vec3 layerB = texture(InSampler, uvLayerB).rgb;

    float layerMix = saturate(hinge * (0.28 + dimensionFold * 0.12) + broadField * power * 0.10);
    vec3 color = mix(base, layerA, layerMix);
    color = mix(color, layerB, hinge * (0.16 + dimensionFold * 0.12));

    float crease = foldLine * power * (0.42 + contact * 0.22 + tunnel * 0.18 + waitingForCommit * 0.20);
    float creaseNoise = hash11(floor((lateral + flow * 0.035) * 18.0 + seed * 53.0));
    float fracture = smoothstep(0.48, 0.92, creaseNoise) * foldLine * power * (dimensionFold * 0.34 + 0.08);
    color += secondary * crease * 0.18;
    color += primary * fracture * 0.20;

    float foldShadow = broadField * power * vignetteScale * (0.18 + activeFold * 0.20);
    color *= 1.0 - foldShadow * (0.46 + edge * 0.34);
    color += primary * edge * power * (0.035 + dimensionFold * 0.018);
    color += secondary * focusVisible * hinge * 0.030;
    color = mix(color, primary, cancel * power * (1.0 - easeOut(phaseProgress)) * 0.18);

    fragColor = vec4(color, baseSample.a);
}

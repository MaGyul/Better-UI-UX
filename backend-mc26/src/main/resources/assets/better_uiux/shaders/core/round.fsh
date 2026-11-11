#version 330
#extension GL_ARB_separate_shader_objects : require

// 둥근 사각형 셰이더(부드러운 가장자리). 사각형 전체에 UV 0..1 을 입혀 그리고, 크기는 화면 좌표와 UV 의 미분 비율로 구한다.
// 파이프라인마다 정수 정의: RTL/RTR/RBR/RBL(네 모서리 반지름, GUI 픽셀), THICK(0 이 아니면 안쪽으로 이 두께의 고리만), BLUR(0 이 아니면 바깥 그림자:
// 그려진 사각형은 실제 모양보다 BLUR 만큼 사방으로 크다), GRAD(0 이 아니면 Sampler0 가 가로 1줄 색 띠: 1 세로, 2 가로, 3 방사형, 4 원뿔).
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec2 guiPos;

layout(location = 0) out vec4 fragColor;

// 점 p(사각형 가운데 기준, y 는 아래로 증가)가 있는 쪽 모서리의 반지름. shrink 만큼 줄인다(테두리 안쪽 모양용).
float cornerRadius(vec2 p, float shrink) {
    float r = p.x < 0.0 ? (p.y < 0.0 ? float(RTL) : float(RBL)) : (p.y < 0.0 ? float(RTR) : float(RBR));
    return max(r - shrink, 0.0);
}

float roundBox(vec2 p, vec2 halfSize, float shrink) {
    float radius = cornerRadius(p, shrink);
    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    vec2 size = vec2(dFdx(guiPos.x) / dFdx(texCoord0.x), dFdy(guiPos.y) / dFdy(texCoord0.y));
    float pixel = max(abs(dFdx(guiPos.x)), 1e-4);
    vec2 p = (texCoord0 - 0.5) * size;
    vec2 shape = size - 2.0 * float(BLUR);
    float dist = roundBox(p, shape * 0.5, 0.0);
    float coverage;
#if BLUR > 0
    coverage = 1.0 - smoothstep(-float(BLUR), float(BLUR), dist);
#else
    coverage = clamp(0.5 - dist / pixel, 0.0, 1.0);
#if THICK > 0
    float inner = roundBox(p, shape * 0.5 - float(THICK), float(THICK));
    coverage *= clamp(0.5 + inner / pixel, 0.0, 1.0);
#endif
#endif
#if GRAD == 0
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
#else
    vec2 e = (texCoord0 - 0.5) * 2.0;
#if GRAD == 1
    float t = texCoord0.y;
#elif GRAD == 2
    float t = texCoord0.x;
#elif GRAD == 3
    float t = length(e) * 0.70710678;
#else
    float t = fract(atan(e.x, -e.y) * 0.15915494 + 1.0);
#endif
    vec4 color = texture(Sampler0, vec2(clamp(t, 0.0, 1.0), 0.5)) * vertexColor;
#endif
    color.a *= coverage;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}

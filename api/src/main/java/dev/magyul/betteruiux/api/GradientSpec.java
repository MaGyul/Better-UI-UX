package dev.magyul.betteruiux.api;

/**
 * 셰이더로 그리는 그라디언트의 모든 매개변수.
 *
 * @param mode      0 위→아래, 1 왼쪽→오른쪽, 2 방사형, 3 원뿔(위에서 시계방향), 4 각도(angleDeg)가 있는 선형
 * @param colors    정지점 색(argb), stops 와 같은 길이
 * @param stops     정지점 위치(0..1, 오름차순). 반복 그라디언트는 첫~끝 정지점 구간을 0..1 로 늘려 둔다.
 * @param angleDeg  mode 4 의 방향(0 = 위쪽, 시계방향, 도)
 * @param repeating 반복 여부(repStart/repLen 이 한 주기)
 * @param shape     방사형 모양: 0 타원, 1 원
 * @param size      방사형 크기: 0 farthest-corner, 1 closest-corner, 2 farthest-side, 3 closest-side
 * @param cx        중심 가로 위치(0..1), cy 세로 위치
 * @param fromDeg   원뿔의 시작 각도(도)
 */
public record GradientSpec(int mode, int[] colors, float[] stops, int angleDeg, boolean repeating, float repStart, float repLen,
                           int shape, int size, float cx, float cy, float fromDeg) {
    public GradientSpec(int mode, int[] colors, float[] stops) {
        this(mode, colors, stops, 0, false, 0f, 1f, 0, 0, 0.5f, 0.5f, 0f);
    }
}

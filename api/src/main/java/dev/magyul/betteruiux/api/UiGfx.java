package dev.magyul.betteruiux.api;

/**
 * 자체 렌더 API. 마인크래프트(GuiGraphics/Blaze3D)를 몰라도 UI를 그릴 수 있게 하는 최소 명령 집합.
 * 좌표는 GUI scale이 적용된 "GUI 픽셀" 단위, 색상은 0xAARRGGBB.
 * 백엔드(버전별)가 이 인터페이스를 구현한다. {@link DrawList} 는 명령을 기록만 하는 구현이다.
 */
public interface UiGfx {
    void fillRect(int x, int y, int w, int h, int argb);

    /** 위→아래 세로 그라데이션 사각형. */
    void fillGradient(int x, int y, int w, int h, int topArgb, int bottomArgb);

    /** texture 는 "namespace:path" 형태 식별자. (u,v,regionW,regionH)는 텍스처(texW x texH) 안의 영역. */
    void drawTexture(String texture, int x, int y, int w, int h,
                     float u, float v, int regionW, int regionH, int texW, int texH, int argb);

    void drawText(String text, int x, int y, int argb, boolean shadow);

    /**
     * 기울임/글꼴을 바꿔 그리는 글자. 지원하지 않는 구현은 기본 {@link #drawText} 로 대신 그린다.
     * @param font {@code minecraft:uniform} 같은 글꼴 id(null 이면 기본)
     */
    default void drawStyledText(String text, int x, int y, int argb, boolean shadow, boolean italic, String font) {
        drawText(text, x, y, argb, shadow);
    }

    /** 이후 그리기를 사각형 안으로 제한한다. 중첩 가능, {@link #popClip()} 으로 복원. */
    void pushClip(int x, int y, int w, int h);

    void popClip();

    /**
     * 이후 그리기에 변환을 적용한다: 이동(tx,ty) → (originX,originY) 기준 확대(scale)·회전(rotateDeg, 시계방향).
     * 중첩 가능, {@link #popTransform()} 으로 복원. 입력(히트 테스트)에는 반영되지 않는다.
     */
    void pushTransform(float tx, float ty, float scale, float rotateDeg, float originX, float originY);

    void popTransform();

    /**
     * 지금까지 그려진 것(월드, 앞서 그린 배경 레이어 등)을 블러 처리한다. 이후에 그리는 것은 블러되지 않는다.
     * 게임의 "메뉴 배경 블러" 옵션이 꺼져 있으면 아무 일도 하지 않을 수 있다.
     */
    void blurBackground();

    /** 아이템 아이콘(16x16)을 그린다. {@code itemId} 는 {@code minecraft:diamond} 형식, count 가 2 이상이면 개수도 표시한다. */
    void drawItem(String itemId, int x, int y, int count);

    /** 이번 프레임의 마우스 포인터 모양을 요청한다. 한 프레임에 여러 번 호출하면 마지막 요청이 적용된다. */
    void requestCursor(Cursor cursor);
}

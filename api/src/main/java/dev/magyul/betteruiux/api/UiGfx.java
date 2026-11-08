package dev.magyul.betteruiux.api;

/**
 * 자체 렌더 API. 마인크래프트(GuiGraphics/Blaze3D)를 몰라도 UI를 그릴 수 있게 하는 최소 명령 집합.
 * 좌표는 GUI scale이 적용된 "GUI 픽셀" 단위, 색상은 0xAARRGGBB.
 * 백엔드(버전별)가 이 인터페이스를 구현한다. {@link DrawList} 는 명령을 기록만 하는 구현이다.
 */
public interface UiGfx {
    void fillRect(int x, int y, int w, int h, int argb);

    /** texture 는 "namespace:path" 형태 식별자. (u,v,regionW,regionH)는 텍스처(texW x texH) 안의 영역. */
    void drawTexture(String texture, int x, int y, int w, int h,
                     float u, float v, int regionW, int regionH, int texW, int texH, int argb);

    void drawText(String text, int x, int y, int argb, boolean shadow);

    /** 이후 그리기를 사각형 안으로 제한한다. 중첩 가능, {@link #popClip()} 으로 복원. */
    void pushClip(int x, int y, int w, int h);

    void popClip();
}

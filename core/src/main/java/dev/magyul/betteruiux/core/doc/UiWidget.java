package dev.magyul.betteruiux.core.doc;

import dev.magyul.betteruiux.api.UiGfx;

/**
 * 모드가 {@link UiWidgets#register} 로 등록하는 커스텀 태그(예: {@code <minimap/>})의 구현.
 * 인스턴스는 XML 노드 하나당 하나씩 만들어져 노드와 함께 유지된다(재레이아웃/핫리로드 후에도 상태 유지는 노드가 같은 동안).
 */
public interface UiWidget {
    /** 프레임마다 전달되는 정보. */
    record Frame(int mouseX, int mouseY, boolean hovered, long nowMillis, UiElement element) {}

    /** width 가 auto 일 때 쓰는 내재 너비(패딩 제외). */
    default int intrinsicWidth() { return 0; }

    default int intrinsicHeight() { return 0; }

    /** 배경/테두리를 그린 뒤 호출된다. (x,y,w,h)는 요소의 최종 사각형. */
    void paint(UiGfx gfx, int x, int y, int w, int h, Frame frame);
}

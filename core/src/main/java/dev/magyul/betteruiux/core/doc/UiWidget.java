package dev.magyul.betteruiux.core.doc;

import dev.magyul.betteruiux.api.Cursor;
import dev.magyul.betteruiux.api.UiGfx;

/**
 * 위젯 구현: 모드가 {@link UiWidgets#register} 로 등록하는 커스텀 태그(예: {@code <minimap/>})와
 * 내장 입력 태그({@code checkbox slider textfield ...})가 같은 인터페이스를 쓴다.
 * 인스턴스는 XML 노드 하나당 하나씩 만들어져 노드와 함께 유지된다(재레이아웃/핫리로드 후에도 상태 유지는 노드가 같은 동안).
 *
 * <p>입력 메서드는 모두 기본 구현이 "처리하지 않음"이라, 그리기만 하는 위젯은 {@link #paint} 만 구현하면 된다.
 */
public interface UiWidget {
    /** 프레임마다 전달되는 정보. */
    record Frame(int mouseX, int mouseY, boolean hovered, long nowMillis, UiElement element) {}

    /** width 가 auto 일 때 쓰는 내재 너비(패딩 제외). */
    default int intrinsicWidth() { return 0; }

    default int intrinsicHeight() { return 0; }

    /** 배경/테두리를 그린 뒤 호출된다. (x,y,w,h)는 요소의 최종 사각형. */
    void paint(UiGfx gfx, int x, int y, int w, int h, Frame frame);

    /** 자식까지 모두 그린 뒤 호출된다(스크롤 막대처럼 자식 위에 얹히는 것). */
    default void paintOver(UiGfx gfx, int x, int y, int w, int h, Frame frame) { }

    /** true 면 자식을 레이아웃이 배치한다(scroll, tabs 같은 컨테이너). 아니면 자식이 있어도 무시되는 리프. */
    default boolean container() { return false; }

    // ---- 입력 ----

    /** @return 이 위젯이 눌림을 처리했으면 true(이후 드래그/뗌이 이 위젯으로 온다). */
    default boolean mousePressed(UiElement el, int x, int y, int button) { return false; }

    default void mouseDragged(UiElement el, int x, int y) { }

    default void mouseReleased(UiElement el, int x, int y, int button) { }

    /** @param amount 양수면 휠 위로. @return 처리했으면 true. */
    default boolean mouseScrolled(UiElement el, int x, int y, double amount) { return false; }

    /** 포커스가 있을 때의 키. */
    default boolean keyPressed(UiElement el, UiKey key, boolean ctrl, boolean shift) { return false; }

    default boolean charTyped(UiElement el, char c) { return false; }

    /** true 면 눌렀을 때/Tab 으로 포커스를 받는다. */
    default boolean focusable() { return false; }

    default void focusChanged(UiElement el, boolean focused) { }

    /** 위에 마우스가 있을 때의 포인터 모양(null 이면 스타일/기본값을 따른다). */
    default Cursor cursor() { return null; }

    // ---- 팝업(드롭다운) ----

    /** true 면 문서가 이 위젯의 팝업을 맨 위에 그리고 입력을 먼저 준다. */
    default boolean popupOpen() { return false; }

    default void paintPopup(UiGfx gfx, Frame frame) { }

    /** 팝업이 열려 있을 때의 눌림. 처리했으면(팝업 안) true. 어느 쪽이든 문서가 팝업을 닫는다. */
    default boolean popupPressed(UiElement el, int x, int y) { return false; }

    default void closePopup() { }

    /** 팝업이 열려 있는 동안의 키(↑/↓ 로 고르기, Enter 선택, Esc 닫기). 처리했으면 true. */
    default boolean popupKey(UiElement el, UiKey key) { return false; }
}

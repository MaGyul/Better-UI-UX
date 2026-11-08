package dev.magyul.betteruiux.core.doc;

/** 문서(위젯)가 받는 키. 게임의 키 코드와 무관하다. 글자 입력은 charTyped 로 따로 온다. */
public enum UiKey {
    ENTER, ESCAPE, BACKSPACE, DELETE, LEFT, RIGHT, UP, DOWN, HOME, END, TAB,
    /** Ctrl 과 함께 쓰는 단축키용 글자. */
    A, C, V, X,
    OTHER
}

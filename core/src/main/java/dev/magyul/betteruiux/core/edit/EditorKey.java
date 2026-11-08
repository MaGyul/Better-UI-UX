package dev.magyul.betteruiux.core.edit;

/** 에디터가 쓰는 키(게임의 키 코드와 무관한 자체 목록). 백엔드가 게임 키 코드를 이것으로 바꿔서 넘긴다. 글자 입력은 charTyped 로 따로 온다. */
public enum EditorKey {
    ENTER, ESCAPE, BACKSPACE, DELETE, LEFT, RIGHT, UP, DOWN, HOME, END, TAB,
    /** Ctrl 과 함께 쓰는 단축키용 글자. */
    Z, Y, S, D,
    OTHER
}

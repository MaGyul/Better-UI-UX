package dev.magyul.betteruiux.core.xml;

/** 파싱 실패. 에디터/핫리로드 에러 오버레이에 줄 번호를 보여주기 위해 line을 보관한다. */
public final class UiParseException extends Exception {
    private final int line;

    public UiParseException(String message, int line) {
        super(message);
        this.line = line;
    }

    public int line() { return line; }
}

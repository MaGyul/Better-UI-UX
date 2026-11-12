package dev.magyul.betteruiux.core.sync;

/** 프로토콜 위반(형식 오류, 상한 초과, 검증 실패). 받는 쪽은 그 메시지/번들을 버리고 서버 기능을 끈다. */
public final class SyncException extends Exception {
    public SyncException(String message) { super(message); }
}

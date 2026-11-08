package dev.magyul.betteruiux.core.script;

/**
 * 스크립트 실행 환경. 호스트와는 <b>JSON 글자</b>로만 대화한다(객체/함수/Java 참조는 경계를 넘지 못한다).
 * 구현체(예: GraalJS)는 이 인터페이스 뒤에 숨는다 — 나중에 별도 스레드/프로세스로 옮겨도 프로토콜은 그대로다.
 */
public interface ScriptRuntime extends AutoCloseable {
    /** 스크립트 소스를 불러온다. 문법 오류 등은 {@link ScriptException}. */
    void load(String name, String source) throws ScriptException;

    /**
     * 호스트 → 스크립트 메시지(JSON 객체 글자)를 보내고, 스크립트가 만든 요청 배열(JSON 글자)을 받는다.
     * 메시지: {@code {"type":"init"}} 또는 {@code {"type":"call","fn":"buy","event":{...},"state":{...}}}.
     * 응답: {@code [{"op":"setText","target":"#t","value":"..."}, ...]}. 호출할 함수가 없으면 빈 배열.
     */
    String call(String messageJson) throws ScriptException;

    @Override
    void close();

    /** 스크립트 쪽 오류(문법, 실행 중 예외, 시간/메모리 한도 초과). 메시지는 사용자에게 경고로 보인다. */
    class ScriptException extends Exception {
        public ScriptException(String message) { super(message); }

        public ScriptException(String message, Throwable cause) { super(message, cause); }
    }
}

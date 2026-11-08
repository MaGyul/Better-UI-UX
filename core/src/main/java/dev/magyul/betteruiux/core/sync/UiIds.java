package dev.magyul.betteruiux.core.sync;

/** UI/액션 id 규칙: 영문, 숫자, {@code _ . : -} 만, 1~128자. 파일 경로나 명령으로 쓰일 수 있는 문자는 막는다. */
public final class UiIds {
    private UiIds() {}

    public static boolean valid(String id) {
        return id != null && !id.isEmpty() && id.length() <= SyncLimits.MAX_ID && id.matches("[A-Za-z0-9_.:\\-]+");
    }
}

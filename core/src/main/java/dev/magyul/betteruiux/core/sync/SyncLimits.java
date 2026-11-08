package dev.magyul.betteruiux.core.sync;

/** 서버 동기화 프로토콜의 상한. 넘는 입력은 모두 거부한다(서버든 클라이언트든 상대를 믿지 않는다). */
public final class SyncLimits {
    private SyncLimits() {}

    /** 프로토콜 버전. 다르면 서버 기능을 끈다(연결은 유지). */
    public static final int PROTOCOL = 1;

    /** 메시지 하나의 최대 바이트. 청크(16KB)와 매니페스트가 들어갈 만큼. */
    public static final int MAX_MESSAGE = 128 * 1024;
    public static final int CHUNK = 16 * 1024;

    public static final long MAX_BUNDLE_BYTES = 16L * 1024 * 1024;
    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    public static final int MAX_FILES = 256;

    public static final int MAX_STATE_KEYS = 256;
    public static final int MAX_VALUE = 4096;
    public static final int MAX_ID = 128;
    public static final int MAX_PATH = 256;
    public static final int MAX_PARAMS = 32;
    public static final int MAX_ARGS = 16;
    public static final int MAX_NEED = MAX_FILES;
}

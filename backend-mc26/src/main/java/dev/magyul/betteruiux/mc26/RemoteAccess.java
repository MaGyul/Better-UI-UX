package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiConfig;
import dev.magyul.betteruiux.core.net.RemoteException;
import dev.magyul.betteruiux.core.net.RemoteMedia;
import java.io.IOException;
import java.nio.file.Path;

/**
 * 문서의 {@code <image src="https://...">} / {@code <video src="https://...">} 를 받아 오는 입구. 로더의 백그라운드 스레드에서만 부른다.
 *
 * <p>설정 {@code [media] remote}: {@code "pinned"}(기본) = {@code #sha256=} 핀이 있는 주소만, {@code "on"} = 핀 없는 주소도, {@code "off"} = 원격 미디어 끔.
 * 주소 규칙/안전 검사/캐시는 {@link RemoteMedia} 참고. 서버가 보낸 화면의 주소도 같은 설정을 따른다(서버가 내 IP 로 임의 사이트를 찌르지 못하도록 기본은 핀 필수).
 */
final class RemoteAccess {
    private static final long MAX_IMAGE = 8L * 1024 * 1024, MAX_VIDEO = 64L * 1024 * 1024;
    private static volatile RemoteMedia media;
    private static volatile Path baseDir;

    private RemoteAccess() {}

    static void init(Path configBaseDir) {
        baseDir = configBaseDir;
        media = RemoteMedia.withDefaults(configBaseDir.resolve("remote-cache"));
    }

    /** 테스트(자동 시험)에서 가짜 네트워크를 끼울 수 있게. */
    static void setMedia(RemoteMedia m) { media = m; }

    static byte[] fetch(String src, RemoteMedia.Kind kind) throws IOException {
        RemoteMedia m = media;
        Path base = baseDir;
        if (m == null || base == null) throw new IOException("원격 미디어가 초기화되지 않았습니다");
        String mode = UiConfig.load(base.resolve("config.toml")).get("media.remote");
        if (mode == null || mode.isBlank()) mode = "pinned";
        if (mode.equals("off")) throw new IOException("원격 미디어가 꺼져 있습니다(config.toml [media] remote)");
        try {
            return m.fetch(src, kind, mode.equals("on"), kind == RemoteMedia.Kind.IMAGE ? MAX_IMAGE : MAX_VIDEO);
        } catch (RemoteException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}

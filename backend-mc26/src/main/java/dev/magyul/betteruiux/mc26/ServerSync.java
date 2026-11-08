package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.sync.Bundle;
import dev.magyul.betteruiux.core.sync.ServerSyncCore;
import dev.magyul.betteruiux.core.sync.SyncCodec;
import dev.magyul.betteruiux.core.sync.SyncException;
import dev.magyul.betteruiux.core.sync.SyncMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 서버 쪽 글루(전용 서버/통합 서버 공통). 클라이언트 클래스를 참조하지 않는다. 로더 모듈이 패킷 송수신과 접속/종료/틱 이벤트를 이어 준다.
 * 실제 규칙(검증, 제한)은 {@link ServerSyncCore} 에 있다.
 */
public final class ServerSync {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");
    private static final ServerSyncCore CORE = new ServerSyncCore(0);
    private static final Map<UUID, ServerSyncCore.ServerSession> SESSIONS = new ConcurrentHashMap<>();
    private static volatile BiConsumer<ServerPlayer, SyncPayload> sender = (p, m) -> {};

    private ServerSync() {}

    public static ServerSyncCore core() { return CORE; }

    /** 로더가 패킷 전송 방법을 알려 준다. */
    public static void setSender(BiConsumer<ServerPlayer, SyncPayload> s) { sender = s; }

    /** 서버가 배포할 번들을 {@code <baseDir>/server-ui/} 에서 읽는다(없으면 비어 있음). */
    public static void loadBundle(Path baseDir) {
        Path dir = baseDir.resolve("server-ui");
        Map<String, byte[]> files = new LinkedHashMap<>();
        if (Files.isDirectory(dir)) {
            try (var s = Files.walk(dir)) {
                for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                    String rel = dir.relativize(p).toString().replace('\\', '/');
                    String why = Bundle.checkPath(rel);
                    if (why != null) {
                        LOG.warn("[better-uiux] server-ui/{} 은(는) 배포하지 않습니다: {}", rel, why);
                        continue;
                    }
                    files.put(rel, Files.readAllBytes(p));
                }
            } catch (IOException e) {
                LOG.warn("[better-uiux] server-ui 를 읽지 못했습니다: {}", e.toString());
            }
        }
        try {
            CORE.setBundle(files);
            LOG.info("[better-uiux] 서버 UI 번들: 파일 {}개, 해시 {}", files.size(), CORE.bundle().manifest().hash().substring(0, 8));
        } catch (SyncException e) {
            LOG.warn("[better-uiux] 서버 UI 번들이 올바르지 않아 배포하지 않습니다: {}", e.getMessage());
            try { CORE.setBundle(Map.of()); } catch (SyncException ignored) { }
        }
    }

    public static void onJoin(ServerPlayer p) {
        SESSIONS.put(p.getUUID(), CORE.connect(p.getUUID().toString(), m -> send(p, m), System::nanoTime));
    }

    public static void onDisconnect(ServerPlayer p) { SESSIONS.remove(p.getUUID()); }

    public static ServerSyncCore.ServerSession session(ServerPlayer p) { return SESSIONS.get(p.getUUID()); }

    /** 서버 스레드에서 매 틱: 번들 조각을 조금씩 보낸다. */
    public static void tick() {
        for (var s : SESSIONS.values()) s.tick();
    }

    public static void onPayload(ServerPlayer p, SyncPayload payload) {
        var s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        try {
            s.onMessage(SyncCodec.decode(payload.data()));
        } catch (SyncException e) {
            s.protocolViolation(e.getMessage());
            LOG.debug("[better-uiux] {} 의 잘못된 동기화 패킷: {}", p.getName().getString(), e.getMessage());
        }
    }

    private static void send(ServerPlayer p, SyncMessage m) {
        try {
            sender.accept(p, new SyncPayload(SyncCodec.encode(m)));
        } catch (SyncException e) {
            LOG.warn("[better-uiux] 동기화 메시지를 만들지 못했습니다: {}", e.getMessage());
        }
    }
}

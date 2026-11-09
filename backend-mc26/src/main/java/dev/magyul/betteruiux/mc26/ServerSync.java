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
    private static final Map<UUID, ServerPlayer> PLAYERS = new ConcurrentHashMap<>();
    private static volatile Path baseDir;

    static { CORE.setPublisher(new Publisher()); }

    private ServerSync() {}

    public static ServerSyncCore core() { return CORE; }

    /** 로더가 패킷 전송 방법을 알려 준다. */
    public static void setSender(BiConsumer<ServerPlayer, SyncPayload> s) { sender = s; }

    /** 서버가 배포할 번들을 {@code <baseDir>/server-ui/} 에서 읽는다(없으면 비어 있음). */
    public static void loadBundle(Path base) {
        baseDir = base;
        dev.magyul.betteruiux.core.doc.UiConfig cfg = dev.magyul.betteruiux.core.doc.UiConfig.load(base.resolve("config.toml"));
        CORE.setPolicy("local-first".equals(cfg.get("server.policy")) ? 1 : 0);                 // 서버가 클라이언트에 요청하는 정책(기본 server-first)
        Path dir = base.resolve("server-ui");
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
        PLAYERS.put(p.getUUID(), p);
        SESSIONS.put(p.getUUID(), CORE.connect(p.getUUID().toString(), m -> send(p, m), System::nanoTime));
    }

    public static void onDisconnect(ServerPlayer p) {
        SESSIONS.remove(p.getUUID());
        PLAYERS.remove(p.getUUID());
    }

    public static ServerSyncCore.ServerSession session(ServerPlayer p) { return SESSIONS.get(p.getUUID()); }

    public static ServerSyncCore.ServerSession sessionById(String playerId) {
        try { return SESSIONS.get(UUID.fromString(playerId)); } catch (IllegalArgumentException e) { return null; }
    }

    /** 플레이어에게 화면을 연다(그 플레이어 클라이언트에 이 모드와 서버 기능이 켜져 있을 때만). @return 열었으면 true */
    public static boolean open(ServerPlayer p, String id, Map<String, Object> params) throws SyncException {
        var s = session(p);
        return s != null && s.open(id, params);
    }

    public static boolean close(ServerPlayer p, String id) {
        var s = session(p);
        return s != null && s.close(id);
    }

    /** 플레이어별 값을 설정한다(문서에서 {@code {server.키}} 로 읽는다). */
    public static void set(ServerPlayer p, String key, Object value) throws SyncException {
        var s = session(p);
        if (s != null) s.set(key, value);
    }

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

    /**
     * 에디터 발행을 받는 쪽. 권한: 권한 레벨 4(소유자) **이고**, 설정 {@code [server] publishers = "이름,이름"} 이 비어 있지 않으면 그 목록에 있는 플레이어만.
     * 파일은 {@code server-ui/} 에 임시 파일을 거쳐 저장하고(경로는 번들 검사를 이미 통과한 것), 접속 중인 모든 클라이언트에 새 목록을 알린다.
     */
    private static final class Publisher implements ServerSyncCore.Publisher {
        @Override
        public boolean mayPublish(String playerId) {
            ServerSyncCore.ServerSession s = sessionById(playerId);
            ServerPlayer p = s == null ? null : PLAYERS.get(UUID.fromString(playerId));
            if (p == null || !net.minecraft.commands.Commands.LEVEL_OWNERS.check(p.permissions())) return false;
            Path base = baseDir;
            if (base == null) return false;
            String list = dev.magyul.betteruiux.core.doc.UiConfig.load(base.resolve("config.toml")).get("server.publishers");
            if (list == null || list.isBlank()) return true;
            for (String n : list.split(",")) if (n.trim().equalsIgnoreCase(p.getName().getString())) return true;
            return false;
        }

        @Override
        public void publish(String playerId, Map<String, byte[]> files) throws SyncException {
            Path base = baseDir;
            if (base == null) throw new SyncException("서버 UI 폴더를 알 수 없습니다");
            Path dir = base.toAbsolutePath().normalize().resolve("server-ui");   // 상대 경로면 startsWith 비교가 어긋나므로 절대 경로로
            Map<String, byte[]> all = new LinkedHashMap<>(CORE.bundle().files());
            try {
                Files.createDirectories(dir);
                for (var e : files.entrySet()) {
                    Path target = dir.resolve(e.getKey()).normalize();
                    if (!target.startsWith(dir)) throw new SyncException("경로가 올바르지 않습니다: " + e.getKey());
                    Files.createDirectories(target.getParent());
                    Path tmp = Files.createTempFile(target.getParent(), ".pub", ".tmp");
                    Files.write(tmp, e.getValue());
                    Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    all.put(e.getKey(), e.getValue());
                }
            } catch (IOException e) {
                throw new SyncException("저장하지 못했습니다: " + e.getMessage());
            }
            CORE.setBundle(all);
            ServerPlayer p = PLAYERS.get(UUID.fromString(playerId));
            LOG.info("[better-uiux] {} 이(가) 서버 UI 를 발행했습니다: {}", p == null ? playerId : p.getName().getString(), files.keySet());
            for (var s : SESSIONS.values()) s.resync();
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

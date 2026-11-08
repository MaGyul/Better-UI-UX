package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.sync.Bundle;
import dev.magyul.betteruiux.core.sync.ClientSyncCore;
import dev.magyul.betteruiux.core.sync.SyncCodec;
import dev.magyul.betteruiux.core.sync.SyncException;
import dev.magyul.betteruiux.core.sync.SyncMessage;
import dev.magyul.betteruiux.core.sync.SyncMessage.BundleManifest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 클라이언트 쪽 글루. 서버별 번들 캐시를 디스크에 두고, 코어({@link ClientSyncCore})가 하는 일을 마인크래프트에 연결한다. */
public final class ClientSync {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");
    private static Path baseDir;
    private static volatile ClientSyncCore core;
    private static volatile Consumer<SyncPayload> sender = p -> {};
    private static volatile boolean serverFeaturesDisabled;

    private ClientSync() {}

    public static void init(Path base) { baseDir = base; }

    public static void setSender(Consumer<SyncPayload> s) { sender = s; }

    public static ClientSyncCore core() { return core; }

    /** 서버 기능이 켜져 있는가(서버에 모드가 있고 인사가 끝남). */
    public static boolean active() { ClientSyncCore c = core; return c != null && c.active(); }

    /** 서버에 접속했을 때(렌더 스레드). 서버에 채널이 없으면 아무것도 하지 않는다 — 로더가 확인해서 부른다. */
    public static void onJoin() {
        serverFeaturesDisabled = false;
        var server = Minecraft.getInstance().getCurrentServer();
        String key = server != null ? server.ip : "singleplayer";
        DiskCache cache = new DiskCache(baseDir.resolve("server-cache").resolve(sanitize(key)));
        core = new ClientSyncCore(ClientSync::send, cache, new ClientSyncCore.Listener() {
            public void onBundleReady(BundleManifest m, int policy) {
                LOG.info("[better-uiux] 서버 UI 번들 준비됨: 파일 {}개, 해시 {}", m.files().size(), m.hash().substring(0, 8));
            }
            public void onOpenUi(String id, Map<String, Object> params) { LOG.info("[better-uiux] 서버가 화면을 열었습니다: {}", id); }
            public void onCloseUi(String id) { LOG.info("[better-uiux] 서버가 화면을 닫았습니다: {}", id); }
            public void onState(Map<String, Object> values) { }
            public void onDisabled(String reason) {
                serverFeaturesDisabled = true;
                LOG.warn("[better-uiux] 서버 기능을 끕니다: {}", reason);
            }
        });
        core.connected();
        LOG.info("[better-uiux] 서버 동기화 인사를 보냈습니다");
    }

    public static void onDisconnect() { core = null; }

    public static void onPayload(SyncPayload payload) {
        ClientSyncCore c = core;
        if (c == null) return;
        try {
            c.onMessage(SyncCodec.decode(payload.data()));
        } catch (SyncException e) {
            c.protocolViolation("해독할 수 없는 패킷: " + e.getMessage());
        }
    }

    private static void send(SyncMessage m) {
        try {
            sender.accept(new SyncPayload(SyncCodec.encode(m)));
        } catch (SyncException e) {
            LOG.warn("[better-uiux] 동기화 메시지를 만들지 못했습니다: {}", e.getMessage());
        }
    }

    static String sanitize(String s) {
        String r = s.replaceAll("[^A-Za-z0-9._-]", "_");
        return r.isEmpty() || r.startsWith(".") ? "_" + r : r;
    }

    /** {@code server-cache/<서버>/files/**} + {@code bundle.hash}. 파일은 임시 파일을 거쳐 옮기고, 해시는 마지막에 쓴다. */
    static final class DiskCache implements ClientSyncCore.BundleCache {
        private final Path dir;

        DiskCache(Path dir) { this.dir = dir; }

        private Path files() { return dir.resolve("files"); }

        public String bundleHash() {
            try {
                return Files.readString(dir.resolve("bundle.hash"), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                return "";
            }
        }

        public Map<String, byte[]> fileHashes() {
            Map<String, byte[]> out = new HashMap<>();
            Path f = files();
            if (!Files.isDirectory(f)) return out;
            try (var s = Files.walk(f)) {
                for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                    out.put(f.relativize(p).toString().replace('\\', '/'), Bundle.sha256(Files.readAllBytes(p)));
                }
            } catch (IOException e) {
                out.clear();
            }
            return out;
        }

        public void store(BundleManifest manifest, Map<String, byte[]> newFiles) {
            try {
                Path f = files();
                Files.createDirectories(f);
                Set<String> keep = new HashSet<>();
                for (var e : manifest.files()) keep.add(e.path());
                for (var e : newFiles.entrySet()) {
                    Path target = f.resolve(e.getKey()).normalize();
                    if (!target.startsWith(f)) continue;                         // 경로는 이미 검증됐지만 한 번 더 막는다
                    Files.createDirectories(target.getParent());
                    Path tmp = Files.createTempFile(target.getParent(), ".dl", ".tmp");
                    Files.write(tmp, e.getValue());
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                try (var s = Files.walk(f)) {
                    for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                        if (!keep.contains(f.relativize(p).toString().replace('\\', '/'))) Files.delete(p);
                    }
                }
                Files.writeString(dir.resolve("bundle.hash"), manifest.hash(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.warn("[better-uiux] 서버 UI 캐시를 저장하지 못했습니다: {}", e.toString());
            }
        }
    }
}

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
import dev.magyul.betteruiux.core.doc.UiConfig;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.doc.UiDocumentLoader;
import dev.magyul.betteruiux.core.doc.UiElement;
import dev.magyul.betteruiux.core.doc.UiSources;
import dev.magyul.betteruiux.core.xml.UiParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 클라이언트 쪽 글루. 서버별 번들 캐시를 디스크에 두고, 코어({@link ClientSyncCore})가 하는 일을 마인크래프트에 연결한다. */
public final class ClientSync {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");
    private static Path baseDir;
    private static volatile ClientSyncCore core;
    private static volatile Consumer<SyncPayload> sender = p -> {};
    private static volatile boolean serverFeaturesDisabled;
    private static volatile Path cacheFiles;
    private static volatile Map<String, Object> latestState = Map.of();
    private static final java.util.List<String> PENDING_OPENS = new java.util.ArrayList<>();

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
        cacheFiles = cache.files();
        latestState = Map.of();
        PENDING_OPENS.clear();
        core = new ClientSyncCore(ClientSync::send, cache, new ClientSyncCore.Listener() {
            public void onBundleReady(BundleManifest m, int policy) {
                LOG.info("[better-uiux] 서버 UI 번들 준비됨: 파일 {}개, 해시 {}", m.files().size(), m.hash().substring(0, 8));
                for (String id : java.util.List.copyOf(PENDING_OPENS)) openServerUi(id);      // 받는 중이라 미뤄 둔 화면
                PENDING_OPENS.clear();
            }
            public void onOpenUi(String id, Map<String, Object> params) { openServerUi(id); }
            public void onCloseUi(String id) {
                if (Minecraft.getInstance().gui.screen() instanceof UiScreen s && s.isServerUi() && s.uiId().equals(id)) Minecraft.getInstance().gui.setScreen(null);
            }
            public void onState(Map<String, Object> values) {
                latestState = values;
                UiDocument cur = UiApi.current();
                if (cur != null) bindState(cur);                                             // 새로 생긴 키도 바로 쓸 수 있게
            }
            public void onDisabled(String reason) {
                serverFeaturesDisabled = true;
                LOG.warn("[better-uiux] 서버 기능을 끕니다: {}", reason);
            }
        });
        core.connected();
        LOG.info("[better-uiux] 서버 동기화 인사를 보냈습니다");
    }

    public static void onDisconnect() {
        core = null;
        latestState = Map.of();
        PENDING_OPENS.clear();
        if (Minecraft.getInstance().gui.screen() instanceof UiScreen s && s.isServerUi()) Minecraft.getInstance().gui.setScreen(null);
    }

    /** 서버 값({@code {server.키}})을 문서에 연결한다. 키가 새로 생길 때마다 다시 부른다. */
    static void bindState(UiDocument doc) {
        for (String key : latestState.keySet()) {
            if (!key.matches("[A-Za-z0-9_.\\-]+")) continue;
            doc.bind("server." + key, () -> latestState.getOrDefault(key, ""));
        }
        // 문서가 쓰는데 서버가 아직 보내지 않은 {server.키} 는 {server.키} 글자 그대로 보이지 않게 빈 값으로 둔다.
        java.util.Set<String> used = new java.util.HashSet<>();
        collectServerKeys(doc.root(), used);
        for (String name : used) {
            String key = name.substring(7);
            if (!latestState.containsKey(key)) doc.bind(name, () -> latestState.getOrDefault(key, ""));
        }
    }

    private static final java.util.regex.Pattern SERVER_REF = java.util.regex.Pattern.compile("\\{(server\\.[A-Za-z0-9_.\\-]+)\\}");

    private static void collectServerKeys(dev.magyul.betteruiux.core.xml.UiNode n, java.util.Set<String> out) {
        for (String v : n.attributes().values()) scan(v, out);
        scan(n.text(), out);
        for (var c : n.children()) collectServerKeys(c, out);
    }

    private static void scan(String s, java.util.Set<String> out) {
        if (s == null || s.indexOf("{server.") < 0) return;
        var m = SERVER_REF.matcher(s);
        while (m.find() && out.size() < 256) out.add(m.group(1));
    }

    /** 서버가 연 화면을 번들 캐시에서 읽어 연다. 번들을 아직 받는 중이면 끝난 뒤에 연다. */
    static void openServerUi(String id) {
        Minecraft mc = Minecraft.getInstance();
        Path dir = cacheFiles;
        if (dir == null || !UiSources.validId(id)) {
            UiOpener.say(mc, "서버가 잘못된 화면 id 를 보냈습니다: " + id);
            return;
        }
        if (!Files.isRegularFile(dir.resolve(id + ".xml"))) {
            if (PENDING_OPENS.size() < 8 && !PENDING_OPENS.contains(id)) PENDING_OPENS.add(id);
            LOG.info("[better-uiux] '{}' 은(는) 번들을 받는 중이라 잠시 뒤에 엽니다", id);
            return;
        }
        // 스크립트는 기본적으로 꺼 둔다: 서버가 보낸 화면이 클라이언트에서 코드를 돌리게 하지 않는다(명령은 어떤 경우에도 못 쓴다).
        boolean allowScripts = UiConfig.load(baseDir.resolve("config.toml")).bool("server.allowScripts", false);
        UiScreen.Options options = new UiScreen.Options(true, false, false, allowScripts, java.util.List.of(), true);
        McTextMetrics metrics = new McTextMetrics(mc.font);
        try {
            UiDocument doc = UiDocumentLoader.load(dir, id, metrics);
            UiApi.fireOpened(id, doc);
            mc.gui.setScreen(new UiScreen(Component.literal(id), id, dir, metrics, doc, null, options));
        } catch (java.io.IOException | UiParseException e) {
            UiOpener.say(mc, "서버 화면 '" + id + "' 을(를) 열 수 없습니다: " + e.getMessage());
        }
    }

    /** {@code onClick="server:액션"} 이 눌렸다: {@code arg-이름="값"} 속성을 인자로 모아 서버에 보낸다. */
    static void sendUiAction(String uiId, UiElement el, String actionId) {
        ClientSyncCore c = core;
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        for (var e : el.node().attributes().entrySet()) {
            String k = e.getKey();
            if (k.length() > 4 && k.regionMatches(true, 0, "arg-", 0, 4)) args.put(k.substring(4), guess(e.getValue()));
        }
        if (c == null || !c.sendAction(uiId, actionId, args)) el.document().warn("서버 동작을 보낼 수 없습니다: '" + actionId + "'");
    }

    private static Object guess(String v) {
        if (v.equals("true") || v.equals("false")) return Boolean.parseBoolean(v);
        try { return Long.parseLong(v); } catch (NumberFormatException ignored) { }
        try {
            double d = Double.parseDouble(v);
            if (!Double.isNaN(d) && !Double.isInfinite(d) && v.matches("-?\\d+\\.\\d+")) return d;
        } catch (NumberFormatException ignored) { }
        return v;
    }

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

        Path files() { return dir.resolve("files"); }

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

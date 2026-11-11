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
    private static final int MAX_C2S = 32_000;
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
            public void onPublishResult(int stage, boolean ok, String message) {
                if (stage == 0 && ok) return;                                                  // 승인: 조치 없음(조각을 보내는 중)
                Minecraft mc = Minecraft.getInstance();
                UiOpener.say(mc, ok ? "서버 발행 완료: " + message : "서버 발행 실패: " + message);
                LOG.info("[better-uiux] publish stage={} ok={} {}", stage, ok, message);
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
        if (ServerSelfTest.enabled()) ServerSelfTest.start();
        LOG.info("[better-uiux] 서버 동기화 인사를 보냈습니다");
    }

    static Object stateValue(String key) { return latestState.get(key); }

    static Path baseDir() { return baseDir; }

    static Path cacheFiles() { return cacheFiles; }

    /** 클라이언트 틱마다(렌더 스레드): 발행 중인 조각을 조금씩 보낸다. */
    public static void tick() {
        ClientSyncCore c = core;
        if (c != null) c.tick();
    }

    /** 서버가 연 화면이 닫혔다(어떤 이유로든): 서버에 알려 그 화면의 동작을 더 받지 않게 한다. */
    static void screenClosed(String id) {
        ClientSyncCore c = core;
        if (c != null) c.closedUi(id);
    }

    private static final java.util.regex.Pattern REF = java.util.regex.Pattern.compile(
            "<(?:include)\\b[^>]*?\\bsrc\\s*=\\s*\"([^\"]+)\"|<link\\b[^>]*?\\bhref\\s*=\\s*\"([^\"]+)\"");

    /**
     * {@code ui/<id>.uiml} 과 그 문서가 쓰는 스타일시트/include 파일을 서버로 발행한다(권한은 서버가 확인한다).
     * @return 시작했으면 true
     */
    public static boolean publishUi(String id) {
        Minecraft mc = Minecraft.getInstance();
        ClientSyncCore c = core;
        if (c == null || !c.active()) {
            UiOpener.say(mc, "서버 기능이 켜져 있지 않아 발행할 수 없습니다(서버에도 이 모드가 필요합니다)");
            return false;
        }
        if (!UiSources.validId(id)) {
            UiOpener.say(mc, "잘못된 UI id: " + id);
            return false;
        }
        Path ui = baseDir.resolve("ui");
        Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        try {
            collect(ui, id + ".uiml", files, 0);
            if (c.publish(files)) UiOpener.say(mc, "서버로 발행하는 중: " + String.join(", ", files.keySet()));
            return true;
        } catch (dev.magyul.betteruiux.core.sync.SyncException | java.io.IOException e) {
            UiOpener.say(mc, "발행할 수 없습니다: " + e.getMessage());
            return false;
        }
    }

    private static void collect(Path ui, String file, Map<String, byte[]> out, int depth)
            throws java.io.IOException, dev.magyul.betteruiux.core.sync.SyncException {
        if (out.containsKey(file) || depth > 5) return;
        if (!UiSources.validName(file)) throw new dev.magyul.betteruiux.core.sync.SyncException("파일 이름이 올바르지 않습니다: " + file);
        Path p = ui.resolve(file);
        if (!Files.isRegularFile(p)) {
            if (depth == 0) throw new java.io.FileNotFoundException(file + " (config/better-uiux/ui/)");
            return;                                                                    // 기본 제공 파일(include/스타일)은 서버에도 있다고 보고 건너뛴다
        }
        byte[] data = Files.readAllBytes(p);
        out.put(file, data);
        if (!file.endsWith(".uiml")) return;
        var m = REF.matcher(new String(data, StandardCharsets.UTF_8));
        while (m.find()) {
            String ref = m.group(1) != null ? m.group(1) + (m.group(1).endsWith(".uiml") ? "" : ".uiml") : m.group(2);
            collect(ui, ref, out, depth + 1);
        }
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
        if (!Files.isRegularFile(dir.resolve(id + ".uiml"))) {
            if (PENDING_OPENS.size() < 8 && !PENDING_OPENS.contains(id)) PENDING_OPENS.add(id);
            LOG.info("[better-uiux] '{}' 은(는) 번들을 받는 중이라 잠시 뒤에 엽니다", id);
            return;
        }
        // 정책: 같은 id 의 로컬 파일(config/better-uiux/ui/<id>.uiml)이 있을 때 어느 쪽을 열까.
        //  - 기본(local-first): 내 파일이 이긴다 — 플레이어가 서버 화면을 자기 취향대로 꾸밀 수 있다(서버 동작/값은 그대로 동작).
        //  - 서버가 server-first 를 요청해도, 내가 설정 server.allowOverride=true 로 허락한 경우에만 서버 파일이 이긴다.
        UiConfig cfg0 = UiConfig.load(baseDir.resolve("config.toml"));
        ClientSyncCore c0 = core;
        boolean serverWins = c0 != null && c0.policy() == 0 && cfg0.bool("server.allowOverride", false);
        Path localUi = baseDir.resolve("ui");
        if (!serverWins && Files.isRegularFile(localUi.resolve(id + ".uiml"))) {
            dir = localUi;
            LOG.info("[better-uiux] 서버 화면 '{}': 로컬 파일을 우선 사용합니다(local-first)", id);
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
            byte[] data = SyncCodec.encode(m);
            if (data.length > MAX_C2S) {                                              // 클라이언트→서버 패킷 상한(로더/바닐라 32KB)
                LOG.warn("[better-uiux] 서버로 보내기에 메시지가 너무 큽니다({} 바이트)", data.length);
                return;
            }
            sender.accept(new SyncPayload(data));
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
                Path f = files().toAbsolutePath().normalize();                  // 상대/비정규 경로여도 startsWith 비교가 맞도록
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

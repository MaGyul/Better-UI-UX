package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.mcv.McvHost;
import dev.magyul.betteruiux.core.script.CommandPolicy;
import dev.magyul.betteruiux.core.script.ScriptHost;
import dev.magyul.betteruiux.core.script.ScriptRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UI 문서에 JavaScript 스크립트를 연결한다. 스크립트 런타임(GraalJS)은 선택 모듈({@code script-graal})이라
 * 클래스패스에 없으면 {@code <script>} 는 무시되고 경고만 나온다. 설정({@code scripting.enabled})이 꺼져 있으면 아무것도 하지 않는다.
 */
final class ScriptBridge {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");
    private static final String RUNTIME_CLASS = "dev.magyul.betteruiux.script.graal.GraalScriptRuntime";

    private ScriptBridge() {}

    /**
     * 게임이 켜질 때(스크립트가 켜져 있고 런타임 모드가 있을 때만) 백그라운드에서 GraalJS 엔진을 미리 데워 둔다.
     * 낮은 우선순위의 데몬 스레드라 게임 로딩을 거의 방해하지 않고, 스크립트를 안 쓰면 아무 일도 하지 않는다.
     */
    static void prewarm(Path baseDir) {
        configBaseDir = baseDir;
        try {
            if (!dev.magyul.betteruiux.core.doc.UiConfig.load(baseDir.resolve("config.toml")).bool("scripting.enabled", false)) return;
            Class.forName(RUNTIME_CLASS, false, ScriptBridge.class.getClassLoader());   // 설치되어 있는지만 확인(초기화는 하지 않는다)
        } catch (ClassNotFoundException | LinkageError | RuntimeException e) {
            return;
        }
        LOADER.execute(() -> {
            long t0 = System.nanoTime();
            try {
                Class.forName(RUNTIME_CLASS).getMethod("prewarm").invoke(null);
                LOG.info("[better-uiux] script runtime warmed up in {} ms", (System.nanoTime() - t0) / 1_000_000);
            } catch (ReflectiveOperationException | LinkageError e) {
                LOG.debug("[better-uiux] script prewarm skipped: {}", e.toString());
            }
        });
    }

    /** 화면이 닫히거나 문서가 바뀔 때 부르는 핸들. 런타임이 준비되기 전에 닫아도 안전하다. */
    static final class Attached {
        private volatile boolean closed;
        private volatile ScriptHost host;
        private final java.util.concurrent.ExecutorService scriptThread = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "better-uiux-script");
            t.setDaemon(true);
            return t;
        });
        /** {@code <mc-viewer>} 가 있는 문서에서만 만들어진다. 위젯이 이 월드들을 그린다. */
        volatile McvHost mcv;
        volatile UiDocument doc;

        void close() {
            closed = true;
            if (doc != null) McViewerRegistry.remove(doc);
            ScriptHost h = host;
            host = null;
            if (h != null) h.close();
            scriptThread.shutdown();
            if (mcv != null) mcv.clear();
        }

        /** 월드 틱 같은 주기 신호를 스크립트의 onTick 으로 보낸다(스크립트가 바쁘면 그 틱은 버려진다). */
        void call(String fn, java.util.Map<String, Object> event) {
            ScriptHost h = host;
            if (h != null) h.callFunction(fn, event);
        }

        void tick(int world, long tick) {
            ScriptHost h = host;
            if (h != null) h.tick(tick, world);
        }
    }

    // mc-viewer 문서의 스크립트 한도: 큰 월드를 만들 수 있게 기본(300ms/50만 문장)보다 넉넉하게. 전용 스레드라 프레임은 멈추지 않는다.
    // config.toml 의 [scripting] maxCallMillis / maxStatements / maxHostCalls 로 바꿀 수 있고, 범위를 벗어난 값은 가까운 한계로 맞춘다.
    static final long DEFAULT_MCV_STATEMENTS = 5_000_000L, DEFAULT_MCV_MILLIS = 1_500L, DEFAULT_MCV_HOST_CALLS = 200_000L;
    private static volatile Path configBaseDir;

    /** config.toml 의 숫자 설정. 없거나 숫자가 아니면 기본값, 범위 밖이면 {min,max} 로 맞춘다. */
    static long configNumber(String key, long def, long min, long max) {
        Path dir = configBaseDir;
        if (dir == null) return def;
        String v = dev.magyul.betteruiux.core.doc.UiConfig.load(dir.resolve("config.toml")).get(key);
        if (v == null) return def;
        try {
            return Math.max(min, Math.min(max, Long.parseLong(v.trim())));
        } catch (NumberFormatException e) {
            LOG.warn("[better-uiux] config {} 은(는) 숫자여야 합니다: {}", key, v);
            return def;
        }
    }

    private static final java.util.concurrent.ExecutorService LOADER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "better-uiux-script-loader");
        t.setDaemon(true);
        return t;
    });

    /**
     * 문서에 스크립트를 연결한다. GraalJS 는 처음 켜는 데 1~2초 걸리므로 <b>백그라운드 스레드</b>에서 만들어 화면은 바로 띄우고,
     * 준비가 끝나면 렌더 스레드에서 스크립트를 불러와 {@code onInit} 을 부른다(그 전에 누른 스크립트 버튼은 "준비 중" 경고만 낸다).
     *
     * @return 핸들(스크립트가 없거나 꺼져 있으면 null)
     */
    static Attached attach(UiDocument doc, Path userDir, UiScreen.Options options) {
        if (doc.queryAll("script").isEmpty()) return null;
        if (!options.scripting()) {
            doc.warn("스크립트가 꺼져 있습니다(config.toml 의 [scripting] enabled = true)");
            return null;
        }
        Attached handle = new Attached();
        doc.setScriptDispatcher((el, fn) -> doc.warn("스크립트를 준비하는 중입니다… 잠시 뒤에 다시 눌러 주세요"));
        Minecraft mc = Minecraft.getInstance();
        CommandPolicy policy = options.scriptCommands().isEmpty() ? CommandPolicy.denyAll() : CommandPolicy.allowing(Set.copyOf(options.scriptCommands()));
        boolean hasViewer = !doc.queryAll("mc-viewer").isEmpty();
        long mcvStatements = configNumber("scripting.maxStatements", DEFAULT_MCV_STATEMENTS, 10_000L, 100_000_000L);
        long mcvMillis = configNumber("scripting.maxCallMillis", DEFAULT_MCV_MILLIS, 100L, 30_000L);
        int mcvCalls = (int) configNumber("scripting.maxHostCalls", DEFAULT_MCV_HOST_CALLS, 100L, 5_000_000L);
        McvHost mcvHost = hasViewer ? new McvHost(mcvCalls) : null;
        handle.mcv = mcvHost;
        handle.doc = doc;
        if (mcvHost != null) McViewerRegistry.put(doc, mcvHost, new McViewerRegistry.ScriptEvents() {
            @Override public void tick(int world, long tick) { handle.tick(world, tick); }

            @Override public void call(String fn, java.util.Map<String, Object> event) { handle.call(fn, event); }
        });
        LOADER.execute(() -> {
            ScriptRuntime runtime;
            try {
                Class<?> cls = Class.forName(RUNTIME_CLASS);
                runtime = (ScriptRuntime) (hasViewer
                        ? cls.getDeclaredConstructor(long.class, long.class).newInstance(mcvStatements, mcvMillis)
                        : cls.getDeclaredConstructor().newInstance());
                if (mcvHost != null) runtime.setHostCallback(mcvHost::invoke);
            } catch (ReflectiveOperationException | LinkageError e) {
                Throwable cause = e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null ? e.getCause() : e;
                boolean missing = cause instanceof ClassNotFoundException || cause instanceof NoClassDefFoundError;
                LOG.warn("[better-uiux] script runtime unavailable", cause);
                mc.execute(() -> doc.warn(missing ? "스크립트 런타임(script-graal)이 설치되어 있지 않습니다"
                        : "스크립트 런타임을 시작하지 못했습니다: " + cause));
                return;
            }
            ScriptRuntime rt = runtime;
            mc.execute(() -> {
                if (handle.closed) {                                   // 준비되기 전에 화면이 닫혔다
                    rt.close();
                    return;
                }
                ScriptHost host = new ScriptHost(doc, rt, policy, ScriptBridge::runCommand);
                host.setLogSink(line -> LOG.info("[better-uiux][script] {}", line));
                host.setThreading(handle.scriptThread, mc);          // 스크립트는 전용 스레드, 문서 변경은 렌더 스레드(mc.execute)
                host.loadScripts(name -> read(userDir, name));
                host.init();
                handle.host = host;
            });
        });
        return handle;
    }

    /** 플레이어 본인 권한으로 명령어를 실행한다(월드 안에 있을 때만). */
    private static void runCommand(String line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;
        mc.player.connection.sendCommand(line);
    }

    private static final java.util.regex.Pattern SCRIPT_SRC = java.util.regex.Pattern.compile("<script[^>]*\\ssrc\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final java.util.regex.Pattern FUNCTION = java.util.regex.Pattern.compile("function\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");

    /** 문서가 불러오는 스크립트들의 함수 이름을 {@code script:이름} 으로 모은다(에디터의 onClick/onChange 자동 완성용). */
    static java.util.List<String> functionActions(String xml, Path userDir) {
        java.util.Set<String> out = new java.util.TreeSet<>();
        java.util.regex.Matcher m = SCRIPT_SRC.matcher(xml);
        while (m.find()) {
            String src = read(userDir, m.group(1));
            if (src == null) continue;
            java.util.regex.Matcher f = FUNCTION.matcher(src);
            while (f.find()) if (!f.group(1).equals("onInit")) out.add("script:" + f.group(1));
        }
        return new java.util.ArrayList<>(out);
    }

    /** 사용자 폴더, 없으면 기본 제공 리소스에서 스크립트를 읽는다. 폴더 밖으로 나가는 이름은 거부한다. */
    static String read(Path userDir, String name) {
        if (name.isBlank() || name.length() > 128 || name.contains("..") || name.startsWith("/") || name.startsWith("\\")
                || !name.matches("[A-Za-z0-9_./-]+")) return null;
        try {
            Path f = userDir.resolve(name).normalize();
            if (f.startsWith(userDir.normalize()) && Files.isRegularFile(f)) return Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("[better-uiux] cannot read script {}: {}", name, e.toString());
        }
        try (InputStream in = ScriptBridge.class.getResourceAsStream("/assets/better_uiux/ui/" + name)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}

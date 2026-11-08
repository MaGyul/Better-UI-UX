package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiDocument;
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

    /** 화면이 닫히거나 문서가 바뀔 때 부르는 핸들. 런타임이 준비되기 전에 닫아도 안전하다. */
    static final class Attached {
        private volatile boolean closed;
        private ScriptHost host;

        void close() {
            closed = true;
            if (host != null) {
                host.close();
                host = null;
            }
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
        LOADER.execute(() -> {
            ScriptRuntime runtime;
            try {
                runtime = (ScriptRuntime) Class.forName(RUNTIME_CLASS).getDeclaredConstructor().newInstance();
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

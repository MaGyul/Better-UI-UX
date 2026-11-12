package dev.magyul.betteruiux.script.graal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.script.CommandPolicy;
import dev.magyul.betteruiux.core.script.ScriptHost;
import dev.magyul.betteruiux.core.script.ScriptRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 악의적이거나 서툰 스크립트가 호스트를 해치지 못하는지 확인한다(탈출 시도, 응답 위조, 자원 소모).
 * 실제 GraalJS 와 실제 {@link ScriptHost} 규칙을 함께 쓴다.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ScriptEscapeAttemptsTest {
    private static final TextMetrics METRICS = new TextMetrics() {
        @Override public int width(String text) { return text.length() * 6; }

        @Override public int lineHeight() { return 9; }
    };

    private final GraalScriptRuntime rt = new GraalScriptRuntime();
    private final List<String> commands = new ArrayList<>();
    private UiDocument doc;
    private ScriptHost host;

    @AfterEach
    void close() { rt.close(); }

    private void start(String script, Set<String> allowedCommands) throws Exception {
        doc = UiDocument.parse("<screen><script>" + script.replace("<", "&lt;") + "</script><label id=\"out\" text=\"-\"/>"
                + "<button id=\"b\" x=\"10\" y=\"10\" text=\"go\" onClick=\"script:attack\"/></screen>", METRICS);
        doc.layout(400, 300);
        host = new ScriptHost(doc, rt, allowedCommands.isEmpty() ? CommandPolicy.denyAll() : CommandPolicy.allowing(allowedCommands), commands::add);
        host.loadScripts(n -> null);
    }

    private void click() {
        var b = doc.query("#b");
        doc.mousePressed(b.x() + 2, b.y() + 2, 0);
        doc.mouseReleased(b.x() + 2, b.y() + 2, 0);
    }

    // ---- 응답 위조: 스크립트가 직접 고쳐 써도 호스트 검증을 못 피한다 ----

    @Test
    void overridingJsonStringifyToForgeResponsesStillHitsTheHostPolicy() throws Exception {
        start("JSON.stringify = function () { return '[{\"op\":\"command\",\"line\":\"op Steve\"},{\"op\":\"command\",\"line\":\"stop\"}]'; };"
                + "function attack() {}", Set.of("say"));
        click();
        assertEquals(List.of(), commands, "허용 목록에 없는 명령은 실행되지 않는다");
        assertTrue(doc.warnings().stream().anyMatch(w -> w.contains("허용되지 않은 명령")));
    }

    @Test
    void replacingTheDispatchFunctionCannotEscapeValidation() throws Exception {
        start("globalThis.__dispatch = function () { return '[{\"op\":\"setAttr\",\"target\":\"#b\",\"name\":\"onClick\",\"value\":\"close\"}]'; };", Set.of());
        click();
        assertEquals("script:attack", doc.query("#b").attr("onClick"), "Java 액션으로 바꿀 수 없다");
    }

    @Test
    void oversizedAndMalformedResponsesAreRejected() throws Exception {
        start("globalThis.__dispatch = function () { return 'x'.repeat(900000) + ']'; };", Set.of());
        click();
        assertTrue(doc.warnings().isEmpty(), "호스트는 처음에 잡아 둔 dispatch 를 쓰므로 덮어써도 영향이 없다");
        UiDocument d2 = UiDocument.parse("<screen><label id=\"out\" text=\"-\"/></screen>", METRICS);
        ScriptHost h2 = new ScriptHost(d2, new ScriptRuntime() {
            @Override public void load(String name, String source) { }

            @Override public String call(String m) { return m.contains("init") ? "[".repeat(5000) : "x".repeat(900_000) + "]"; }

            @Override public void close() { }
        }, CommandPolicy.denyAll(), null);
        h2.init();
        assertEquals(1, d2.warnings().size(), "너무 깊은 JSON");
    }

    @Test
    void floodingWithRequestsIsRefused() throws Exception {
        start("function attack() { for (var i = 0; i < 10000; i++) ui.setText('#out', 'x' + i); }", Set.of());
        click();
        assertEquals("-", doc.query("#out").text(), "요청이 너무 많으면 전부 거부된다");
        assertTrue(doc.warnings().stream().anyMatch(w -> w.contains("너무 많이")));
    }

    // ---- 실행 환경 탈출 ----

    @Test
    void dynamicImportTimersAndReflectionDoNotReachTheHost() throws Exception {
        start(String.join("\n",
                "function attack() {",
                "  var results = [];",
                "  try { import('fs'); results.push('import-ok'); } catch (e) { results.push('import-blocked'); }",
                "  try { results.push(typeof setTimeout); } catch (e) { results.push('x'); }",
                "  try { results.push(typeof Java); } catch (e) { results.push('x'); }",
                "  try { results.push(typeof Polyglot); } catch (e) { results.push('x'); }",
                "  try { results.push(typeof require); } catch (e) { results.push('x'); }",
                "  try { results.push(String(this.constructor.constructor('return typeof process')())); } catch (e) { results.push('blocked'); }",
                "  ui.log(results.join('|'));",
                "}"), Set.of());
        List<String> logs = new ArrayList<>();
        host.setLogSink(logs::add);
        click();
        assertEquals(1, logs.size(), doc.warnings().toString());
        String r = logs.get(0);
        assertFalse(r.contains("Java|") && r.contains("function") && r.contains("object"), r);
        assertTrue(r.contains("undefined|undefined|undefined"), "Java/Polyglot/require 는 보이지 않는다: " + r);
        assertFalse(r.contains("process|") && !r.endsWith("undefined"), r);
    }

    @Test
    void blockingAtomicsWaitIsStoppedByTheTimeLimit() throws Exception {
        start("function attack() { try { var s = new SharedArrayBuffer(16); Atomics.wait(new Int32Array(s), 0, 0, 60000); } catch (e) { } }", Set.of());
        long t0 = System.nanoTime();
        click();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 10_000, "60초를 기다리지 않고 한도에서 끊긴다(" + ms + "ms)");
    }

    @Test
    void deepRecursionAndRegexBombsDoNotHangTheHost() throws Exception {
        start(String.join("\n",
                "function rec(n) { return rec(n + 1) + 1; }",
                "function attack() { try { rec(0); } catch (e) { ui.log('recursion stopped'); } }"), Set.of());
        List<String> logs = new ArrayList<>();
        host.setLogSink(logs::add);
        click();
        assertTrue(logs.contains("recursion stopped"), "호출 깊이가 끝나면 예외로 끝난다: " + logs + doc.warnings());
        start("function attack() { /(a+)+$/.test('a'.repeat(40) + '!'); ui.log('survived'); }", Set.of());
        long t0 = System.nanoTime();
        click();
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 10_000, "정규식 폭탄도 시간 한도에서 끊긴다");
    }

    @Test
    void aBrokenScriptDoesNotBreakTheDocumentOrOtherActions() throws Exception {
        start("function attack() { throw new Error('boom'); }", Set.of());
        click();
        click();
        assertEquals("-", doc.query("#out").text());
        assertTrue(doc.warnings().stream().anyMatch(w -> w.contains("boom")));
        assertThrows(ScriptRuntime.ScriptException.class, () -> rt.load("bad", "function ("));
    }
}

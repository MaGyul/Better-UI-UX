package dev.magyul.betteruiux.script.graal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.core.script.CommandPolicy;
import dev.magyul.betteruiux.core.script.Json;
import dev.magyul.betteruiux.core.script.ScriptHost;
import dev.magyul.betteruiux.core.script.ScriptRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 진짜 GraalJS 로 샌드박스와 JSON 경계를 확인한다. */
class GraalScriptRuntimeTest {
    private static final TextMetrics METRICS = new TextMetrics() {
        @Override public int width(String text) { return text.length() * 6; }

        @Override public int lineHeight() { return 9; }
    };

    private final GraalScriptRuntime rt = new GraalScriptRuntime();

    @AfterEach
    void close() { rt.close(); }

    private String call(String fn) throws Exception {
        return rt.call("{\"type\":\"call\",\"fn\":\"" + fn + "\",\"event\":{\"id\":\"b\"},\"state\":{}}");
    }

    @Test
    void functionsCollectRequestsAsJson() throws Exception {
        rt.load("t.js", "function hi(ev) { ui.setText('#out', 'hello ' + ev.id); ui.addClass('#out', 'x'); ui.command('say hi'); }");
        Object ops = Json.parse(call("hi"));
        assertEquals("[{\"op\":\"setText\",\"target\":\"#out\",\"value\":\"hello b\"},{\"op\":\"addClass\",\"target\":\"#out\",\"name\":\"x\"},"
                + "{\"op\":\"command\",\"line\":\"say hi\"}]", Json.write(ops));
        assertEquals("[]", call("missing"), "없는 함수는 빈 배열");
    }

    @Test
    void initCallsOnInitWithTheState() throws Exception {
        rt.load("t.js", "function onInit(state) { ui.setVar('seen', Object.keys(state).join(',')); }");
        String out = rt.call("{\"type\":\"init\",\"state\":{\"a\":{},\"b\":{}}}");
        assertTrue(out.contains("\"value\":\"a,b\""), out);
    }

    @Test
    void syntaxAndRuntimeErrorsAreScriptExceptions() throws Exception {
        assertThrows(ScriptRuntime.ScriptException.class, () -> rt.load("bad.js", "function ("));
        rt.load("t.js", "function boom() { throw new Error('nope'); }");
        ScriptRuntime.ScriptException e = assertThrows(ScriptRuntime.ScriptException.class, () -> call("boom"));
        assertTrue(e.getMessage().contains("nope"));
    }

    @Test
    void scriptsCannotTouchJavaFilesProcessesOrTheEnvironment() throws Exception {
        rt.load("t.js", String.join("\n",
                "function tryHost() { Java.type('java.lang.System').exit(1); }",
                "function tryIo() { return load('/etc/passwd'); }",
                "function tryRequire() { return require('fs'); }",
                "function tryProcess() { return process.env.PATH; }",
                "function tryJavaGlobal() { return Java.type('java.io.File'); }"));
        for (String fn : List.of("tryHost", "tryIo", "tryRequire", "tryProcess", "tryJavaGlobal")) {
            assertThrows(ScriptRuntime.ScriptException.class, () -> call(fn), fn);
        }
        // 위의 시도들이 컨텍스트를 망가뜨리지 않았다
        rt.load("ok.js", "function fine() { ui.setText('#a', 'ok'); }");
        assertTrue(call("fine").contains("ok"));
    }

    @Test
    void infiniteLoopsAreStoppedAndTheRuntimeKeepsWorking() throws Exception {
        rt.load("t.js", "function spin() { while (true) {} } function fine() { ui.log('alive'); }");
        long t0 = System.nanoTime();
        ScriptRuntime.ScriptException e = assertThrows(ScriptRuntime.ScriptException.class, () -> call("spin"));
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 5_000, "한도 안에 끝난다");
        assertTrue(e.getMessage().contains("한도"), e.getMessage());
        assertTrue(call("fine").contains("alive"), "중단 뒤에도 다음 호출은 동작한다");
    }

    @Test
    void hugeStringsAreRefused() throws Exception {
        rt.load("t.js", "function big() { var s = 'x'; for (var i = 0; i < 40; i++) s = s + s; ui.log(s.length); }");
        assertThrows(ScriptRuntime.ScriptException.class, () -> call("big"));
    }

    @Test
    void closedRuntimesRefuseCalls() throws Exception {
        rt.close();
        assertThrows(ScriptRuntime.ScriptException.class, () -> call("x"));
    }

    // ---- 문서까지 이어서 ----

    @Test
    void endToEndButtonClickRunsJavaScriptAndEditsTheDocument() throws Exception {
        UiDocument d = UiDocument.parse("<screen><script>"
                + "var clicks = 0;"
                + "function onInit(state) { ui.setText('#count', 'clicks: 0'); }"
                + "function clicked(ev, state) { clicks++; ui.setText('#count', 'clicks: ' + clicks + ' by ' + ev.id + ' name=' + state.name.value);"
                + "  ui.setVar('accent', clicks > 1 ? '#FFFF0000' : '#FF00FF00'); ui.toggleClass('#count', 'hot', clicks > 1); ui.command('say clicked ' + clicks); ui.log('x'); }"
                + "</script>"
                + "<label id=\"count\" text=\"-\"/><textfield id=\"name\" value=\"Steve\" x=\"10\" y=\"60\" width=\"80\"/>"
                + "<button id=\"go\" x=\"10\" y=\"100\" text=\"Go\" onClick=\"script:clicked\"/></screen>", METRICS);
        d.layout(400, 300);
        List<String> commands = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        ScriptHost host = new ScriptHost(d, rt, CommandPolicy.allowing(Set.of("say")), commands::add);
        host.setLogSink(logs::add);
        host.loadScripts(n -> null);
        host.init();
        assertEquals("clicks: 0", d.query("#count").text());
        var go = d.query("#go");
        d.mousePressed(go.x() + 2, go.y() + 2, 0);
        d.mouseReleased(go.x() + 2, go.y() + 2, 0);
        d.mousePressed(go.x() + 2, go.y() + 2, 0);
        d.mouseReleased(go.x() + 2, go.y() + 2, 0);
        assertEquals("clicks: 2 by go name=Steve", d.query("#count").text());
        assertTrue(d.query("#count").hasClass("hot"));
        d.layout(400, 300);
        assertEquals("#FFFF0000", d.variable("accent"));
        assertEquals(List.of("say clicked 1", "say clicked 2"), commands);
        assertEquals(List.of("x", "x"), logs);
        assertEquals(List.of(), d.warnings());
        host.close();
    }

    @Test
    void runtimesShareOneEngineButNotTheirState() throws Exception {
        GraalScriptRuntime other = new GraalScriptRuntime();
        try {
            rt.load("a.js", "var secret = 'A'; function who() { ui.log(typeof globalThis.secret + ':' + secret); }");
            other.load("b.js", "function who() { ui.log(typeof globalThis.secret); }");
            assertTrue(call("who").contains("string:A"));
            String b = other.call("{\"type\":\"call\",\"fn\":\"who\",\"event\":{},\"state\":{}}");
            assertTrue(b.contains("undefined"), "다른 런타임의 전역은 보이지 않는다: " + b);
        } finally {
            other.close();
        }
        assertTrue(call("who").contains("string:A"), "다른 런타임을 닫아도 영향이 없다");
    }

    @Test
    void prewarmMakesTheNextRuntimeStartFast() {
        GraalScriptRuntime.prewarm();
        long t0 = System.nanoTime();
        GraalScriptRuntime fresh = new GraalScriptRuntime();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        fresh.close();
        assertTrue(ms < 2_000, "예열 뒤 런타임 생성 시간: " + ms + "ms");
    }
}

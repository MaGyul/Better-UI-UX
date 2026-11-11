package dev.magyul.betteruiux.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.doc.UiElement;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** JSON 경계, 요청 검증/실행, 명령어 정책. 런타임은 가짜(람다)라서 GraalJS 없이 호스트 쪽 규칙만 확인한다. */
class ScriptHostTest {
    /** 호스트가 보낸 메시지를 기록하고, 정해 둔 응답을 돌려주는 가짜 런타임. */
    private static final class FakeRuntime implements ScriptRuntime {
        final List<String> messages = new ArrayList<>();
        final List<String> loaded = new ArrayList<>();
        Function<String, String> responder = m -> "[]";
        boolean closed;

        @Override public void load(String name, String source) { loaded.add(name + ":" + source.strip()); }

        @Override public String call(String messageJson) throws ScriptException {
            messages.add(messageJson);
            return responder.apply(messageJson);
        }

        @Override public void close() { closed = true; }
    }

    private final FakeRuntime rt = new FakeRuntime();
    private final List<String> commands = new ArrayList<>();

    private UiDocument doc(String body) throws Exception {
        UiDocument d = UiDocument.parse("<screen>" + body + "</screen>", new FixedMetrics());
        d.layout(400, 300);
        return d;
    }

    private ScriptHost host(UiDocument d, CommandPolicy policy) {
        return new ScriptHost(d, rt, policy, commands::add);
    }

    private static void click(UiDocument d, UiElement el) {
        d.mousePressed(el.x() + 2, el.y() + 2, 0);
        d.mouseReleased(el.x() + 2, el.y() + 2, 0);
    }

    // ---- JSON ----

    @Test
    void jsonRoundTripsDataTypesAndRejectsBadInput() {
        Object v = Json.parse("{\"a\":[1,2.5,true,null,\"x\\n\\u0041\"],\"b\":{\"c\":\"d\"}}");
        assertEquals("{\"a\":[1,2.5,true,null,\"x\\nA\"],\"b\":{\"c\":\"d\"}}", Json.write(v));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{} extra"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[".repeat(Json.MAX_DEPTH + 5)));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"" + "x".repeat(Json.MAX_LENGTH) + "\""));
    }

    // ---- 이벤트 → 스크립트 → 요청 ----

    @Test
    void buttonClickSendsACallMessageWithStateAndAppliesTheResponse() throws Exception {
        UiDocument d = doc("<label id=\"out\" text=\"-\"/><textfield id=\"name\" value=\"Steve\" x=\"10\" y=\"60\" width=\"80\"/>"
                + "<button id=\"b\" x=\"10\" y=\"100\" text=\"Go\" onClick=\"script:buy\"/>");
        host(d, CommandPolicy.denyAll());
        rt.responder = m -> "[{\"op\":\"setText\",\"target\":\"#out\",\"value\":\"bought\"}]";
        click(d, d.query("#b"));
        assertEquals(1, rt.messages.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> msg = (Map<String, Object>) Json.parse(rt.messages.get(0));
        assertEquals("call", msg.get("type"));
        assertEquals("buy", msg.get("fn"));
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) msg.get("state");
        assertTrue(state.containsKey("name") && state.containsKey("out"), "id 가 있는 요소의 값이 상태로 간다");
        assertEquals("bought", d.query("#out").text());
    }

    @Test
    void changeEventsOfInputWidgetsReachTheScript() throws Exception {
        UiDocument d = doc("<checkbox id=\"c\" x=\"10\" y=\"10\" text=\"S\" onChange=\"script:toggled\"/>");
        host(d, CommandPolicy.denyAll());
        click(d, d.query("#c"));
        assertTrue(rt.messages.get(0).contains("\"fn\":\"toggled\""));
        assertTrue(rt.messages.get(0).contains("\"checked\":true"));
    }

    @Test
    void scriptsInTheDocumentAreLoadedAndInitIsSent() throws Exception {
        UiDocument d = doc("<script src=\"shop.js\"/><script>var x = 1;</script><label id=\"l\" text=\"a\"/>");
        ScriptHost h = host(d, CommandPolicy.denyAll());
        h.loadScripts(name -> name.equals("shop.js") ? "function buy(){}" : null);
        assertEquals(List.of("shop.js:function buy(){}", "(인라인):var x = 1;"), rt.loaded);
        h.init();
        assertTrue(rt.messages.get(0).contains("\"type\":\"init\""));
    }

    @Test
    void missingScriptFilesBecomeWarnings() throws Exception {
        UiDocument d = doc("<script src=\"nope.js\"/>");
        host(d, CommandPolicy.denyAll()).loadScripts(n -> null);
        assertTrue(d.warnings().stream().anyMatch(w -> w.contains("nope.js")));
    }

    // ---- 허용 연산 ----

    @Test
    void allowedElementOperationsWork() throws Exception {
        UiDocument d = doc("<panel id=\"p\" x=\"10\" y=\"10\" width=\"50\" height=\"30\"/><label id=\"l\" text=\"a\"/>");
        ScriptHost h = host(d, CommandPolicy.denyAll());
        h.apply("[{\"op\":\"setAttr\",\"target\":\"#p\",\"name\":\"tooltip\",\"value\":\"hi\"},"
                + "{\"op\":\"addClass\",\"target\":\"#p\",\"name\":\"hot\"},"
                + "{\"op\":\"setStyle\",\"target\":\"#p\",\"name\":\"opacity\",\"value\":\"0.5\"},"
                + "{\"op\":\"append\",\"target\":\"#p\",\"xml\":\"<label id=\\\"kid\\\" text=\\\"new\\\"/>\"},"
                + "{\"op\":\"setVar\",\"name\":\"accent\",\"value\":\"#00FF00FF\"},"
                + "{\"op\":\"remove\",\"target\":\"#l\"}]");
        assertEquals("hi", d.query("#p").attr("tooltip"));
        assertTrue(d.query("#p").hasClass("hot"));
        assertEquals("0.5", d.query("#p").style("opacity") == null ? "0.5" : d.query("#p").attr("style").contains("0.5") ? "0.5" : "?");
        assertEquals("new", d.query("#kid").text());
        d.layout(400, 300);
        assertEquals("#00FF00FF", d.variable("accent"));
        assertNull(d.query("#l"));
        assertEquals(List.of(), d.warnings());
    }

    // ---- 거부되는 요청(보안) ----

    private List<String> warningsAfter(String body, String response) throws Exception {
        UiDocument d = doc(body);
        host(d, CommandPolicy.denyAll()).apply(response);
        return d.warnings();
    }

    @Test
    void unknownOperationsAndBadTargetsAreRejected() throws Exception {
        List<String> w = warningsAfter("<label id=\"l\" text=\"a\"/>",
                "[{\"op\":\"exec\",\"target\":\"#l\"},{\"op\":\"setText\",\"target\":\"##\",\"value\":\"x\"},{\"op\":\"setText\",\"value\":\"x\"},42]");
        assertEquals(4, w.size(), w.toString());
    }

    @Test
    void eventAttributesCanOnlyPointAtScripts() throws Exception {
        UiDocument d = doc("<button id=\"b\" text=\"x\" onClick=\"script:ok\"/>");
        ScriptHost h = host(d, CommandPolicy.denyAll());
        h.apply("[{\"op\":\"setAttr\",\"target\":\"#b\",\"name\":\"onClick\",\"value\":\"close\"}]");
        assertEquals("script:ok", d.query("#b").attr("onClick"), "등록된 Java 액션을 가리키게 바꿀 수 없다");
        assertEquals(1, d.warnings().size());
        h.apply("[{\"op\":\"setAttr\",\"target\":\"#b\",\"name\":\"onClick\",\"value\":\"script:other\"}]");
        assertEquals("script:other", d.query("#b").attr("onClick"));
    }

    @Test
    void appendRejectsScriptsIncludesAndNonScriptEvents() throws Exception {
        UiDocument d = doc("<panel id=\"p\"/>");
        ScriptHost h = host(d, CommandPolicy.denyAll());
        h.apply("[{\"op\":\"append\",\"target\":\"#p\",\"xml\":\"<script src=\\\"x.js\\\"/>\"},"
                + "{\"op\":\"append\",\"target\":\"#p\",\"xml\":\"<include src=\\\"x\\\"/>\"},"
                + "{\"op\":\"append\",\"target\":\"#p\",\"xml\":\"<column><button onClick=\\\"close\\\"/></column>\"},"
                + "{\"op\":\"append\",\"target\":\"#p\",\"xml\":\"<column><button onClick=\\\"script:ok\\\"/></column>\"}]");
        assertEquals(3, d.warnings().size(), d.warnings().toString());
        assertEquals(1, d.query("#p").children().size());
    }

    @Test
    void srcAndTemplateAttributesAreOffLimits() throws Exception {
        List<String> w = warningsAfter("<image id=\"i\" src=\"a\"/>", "[{\"op\":\"setAttr\",\"target\":\"#i\",\"name\":\"src\",\"value\":\"b\"}]");
        assertEquals(1, w.size());
    }

    @Test
    void tooManyOperationsAreRejectedAsAWhole() throws Exception {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i <= ScriptHost.MAX_OPS; i++) sb.append(i == 0 ? "" : ",").append("{\"op\":\"setVar\",\"name\":\"a\",\"value\":\"b\"}");
        List<String> w = warningsAfter("<label/>", sb.append("]").toString());
        assertEquals(1, w.size());
        assertTrue(w.get(0).contains("너무 많이"));
    }

    @Test
    void malformedResponsesAndRuntimeErrorsBecomeWarnings() throws Exception {
        UiDocument d = doc("<button id=\"b\" x=\"10\" y=\"10\" text=\"x\" onClick=\"script:f\"/>");
        host(d, CommandPolicy.denyAll());
        rt.responder = m -> "not json";
        click(d, d.query("#b"));
        rt.responder = m -> "{\"op\":\"x\"}";
        click(d, d.query("#b"));
        rt.responder = m -> { throw new RuntimeException("boom"); };
        click(d, d.query("#b"));
        assertEquals(3, d.warnings().size(), d.warnings().toString());
    }

    @Test
    void invalidFunctionNamesAreNotSentToTheRuntime() throws Exception {
        UiDocument d = doc("<button id=\"b\" x=\"10\" y=\"10\" text=\"x\" onClick=\"script:a.b;evil()\"/>");
        host(d, CommandPolicy.denyAll());
        click(d, d.query("#b"));
        assertTrue(rt.messages.isEmpty());
        assertEquals(1, d.warnings().size());
    }

    @Test
    void withoutAHostScriptActionsJustWarn() throws Exception {
        UiDocument d = doc("<button id=\"b\" x=\"10\" y=\"10\" text=\"x\" onClick=\"script:f\"/>");
        click(d, d.query("#b"));
        assertEquals(1, d.warnings().size());
    }

    // ---- 명령어 ----

    @Test
    void commandsAreDeniedByDefaultAndFilteredByThePolicy() throws Exception {
        UiDocument d = doc("<label/>");
        ScriptHost denied = host(d, CommandPolicy.denyAll());
        denied.apply("[{\"op\":\"command\",\"line\":\"say hi\"}]");
        assertTrue(commands.isEmpty());
        assertEquals(1, d.warnings().size());

        UiDocument d2 = doc("<label/>");
        ScriptHost h = host(d2, CommandPolicy.allowing(Set.of("say", "give")));
        h.apply("[{\"op\":\"command\",\"line\":\"/say hi\"},{\"op\":\"command\",\"line\":\"give @s minecraft:apple 1\"},"
                + "{\"op\":\"command\",\"line\":\"op Steve\"},{\"op\":\"command\",\"line\":\"say a\\nstop\"}]");
        assertEquals(List.of("say hi", "give @s minecraft:apple 1"), commands, "허용된 것만, 앞의 / 는 뗀다");
        assertEquals(2, d2.warnings().size(), "op 와 줄바꿈 명령은 거부");
    }

    @Test
    void commandRateAndPerCallLimitsApply() {
        long[] now = {0};
        CommandPolicy p = new CommandPolicy(Set.of("say"), 2, 3, () -> now[0]);
        assertNull(p.check("say 1"));
        assertNull(p.check("say 2"));
        assertNull(p.check("say 3"));
        assertTrue(p.check("say 4").contains("자주"), "초당 3개 초과");
        now[0] += 1_100_000_000L;
        assertNull(p.check("say 5"), "1초가 지나면 다시 허용");
        assertTrue(p.check("x".repeat(CommandPolicy.MAX_LINE + 1)).contains("깁"));
        assertFalse(CommandPolicy.normalize(" /say hi ").startsWith("/"));
    }

    @Test
    void perCallCommandLimitStopsExtraCommands() throws Exception {
        UiDocument d = doc("<label/>");
        ScriptHost h = new ScriptHost(d, rt, new CommandPolicy(Set.of("say"), 2, 100, System::nanoTime), commands::add);
        h.apply("[{\"op\":\"command\",\"line\":\"say 1\"},{\"op\":\"command\",\"line\":\"say 2\"},{\"op\":\"command\",\"line\":\"say 3\"}]");
        assertEquals(List.of("say 1", "say 2"), commands);
        assertEquals(1, d.warnings().size());
    }
}

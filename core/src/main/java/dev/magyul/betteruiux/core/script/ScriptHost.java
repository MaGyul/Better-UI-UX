package dev.magyul.betteruiux.core.script;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.doc.UiElement;
import dev.magyul.betteruiux.core.xml.UiNode;
import dev.magyul.betteruiux.core.xml.UiParseException;
import dev.magyul.betteruiux.core.xml.UiParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 문서와 스크립트 런타임을 잇는다. 스크립트는 문서를 직접 만지지 못하고, JSON 메시지로 <b>요청</b>만 돌려준다.
 * 호스트가 요청을 하나씩 검증한 뒤 실행하므로, 스크립트가 할 수 있는 일은 아래 연산으로 한정된다:
 *
 * <ul>
 *   <li>XML 요소: {@code setAttr removeAttr setText addClass removeClass toggleClass setStyle append remove}</li>
 *   <li>CSS 변수: {@code setVar}</li>
 *   <li>마인크래프트 명령어: {@code command} ({@link CommandPolicy} 허용 목록에 있을 때만)</li>
 * </ul>
 *
 * 문서의 {@code onClick="script:fn"} / {@code onChange="script:fn"} 이 이 호스트로 연결된다.
 */
public final class ScriptHost {
    public static final int MAX_OPS = 64, MAX_STRING = 4096, MAX_TARGETS = 200, MAX_STATE = 256;
    private static final Set<String> BAD_TAGS = Set.of("script", "include", "use", "template", "link");
    private static final Set<String> EVENT_ATTRS = Set.of("onclick", "onchange", "onsubmit");

    private final UiDocument doc;
    private final ScriptRuntime runtime;
    private final CommandPolicy policy;
    private final Consumer<String> commandSink;
    private boolean broken;

    /**
     * @param commandSink 허용된 명령 한 줄(앞의 / 없이)을 실제로 실행하는 쪽(백엔드가 연결). null 이면 명령은 모두 거부된다.
     */
    public ScriptHost(UiDocument doc, ScriptRuntime runtime, CommandPolicy policy, Consumer<String> commandSink) {
        this.doc = doc;
        this.runtime = runtime;
        this.policy = policy == null ? CommandPolicy.denyAll() : policy;
        this.commandSink = commandSink;
        doc.setScriptDispatcher(this::dispatch);
    }

    /** 문서의 {@code <script src="..."/>} 와 본문 스크립트를 모두 불러온다. @param loader 이름 → 소스(없으면 null) */
    public void loadScripts(Function<String, String> loader) {
        List<UiElement> scripts = doc.queryAll("script");
        for (UiElement el : scripts) {
            String src = el.attr("src");
            String text;
            String name;
            if (src != null && !src.isBlank()) {
                name = src.trim();
                text = loader.apply(name);
                if (text == null) {
                    doc.warn("스크립트 '" + name + "' 을(를) 찾을 수 없습니다");
                    continue;
                }
            } else {
                name = "(인라인)";
                text = el.node().text();
            }
            if (text == null || text.isBlank()) continue;
            try {
                runtime.load(name, text);
            } catch (ScriptRuntime.ScriptException e) {
                doc.warn("스크립트 '" + name + "': " + e.getMessage());
                broken = true;
            }
        }
    }

    /** 스크립트에 {@code init} 메시지를 보낸다(문서가 열린 직후 한 번). */
    public void init() {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "init");
        msg.put("state", snapshot());
        send(msg);
    }

    /** {@code script:fn} 액션이 눌렸을 때 문서가 부른다. */
    void dispatch(UiElement source, String fn) {
        if (!fn.matches("[A-Za-z_$][A-Za-z0-9_$]{0,63}")) {
            doc.warn("스크립트 함수 이름이 올바르지 않습니다: " + fn);
            return;
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "call");
        msg.put("fn", fn);
        msg.put("event", describe(source));
        msg.put("state", snapshot());
        send(msg);
    }

    private static Map<String, Object> describe(UiElement el) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", el.id());
        m.put("tag", el.tag());
        m.put("text", el.text());
        m.put("value", el.attr("value"));
        m.put("checked", "true".equalsIgnoreCase(el.attr("checked")));
        return m;
    }

    /** id 가 있는 요소들의 현재 값(데이터만). 스크립트는 이것으로 상태를 읽는다. */
    private Map<String, Object> snapshot() {
        Map<String, Object> state = new LinkedHashMap<>();
        for (UiElement el : doc.queryAll("*")) {
            if (el.id() == null || state.size() >= MAX_STATE) continue;
            state.put(el.id(), describe(el));
        }
        return state;
    }

    private void send(Map<String, Object> message) {
        if (broken) return;
        String response;
        try {
            response = runtime.call(Json.write(message));
        } catch (ScriptRuntime.ScriptException e) {
            doc.warn("스크립트 오류: " + e.getMessage());
            return;
        } catch (RuntimeException e) {
            doc.warn("스크립트 호출 실패: " + e);
            return;
        }
        apply(response);
    }

    // ---- 요청 검증 + 실행 ----

    /** 스크립트가 돌려준 요청 배열(JSON)을 검증해 실행한다. 거부된 요청은 경고로 남기고 나머지는 계속 진행한다. */
    void apply(String responseJson) {
        Object parsed;
        try {
            parsed = Json.parse(responseJson == null || responseJson.isBlank() ? "[]" : responseJson);
        } catch (IllegalArgumentException e) {
            doc.warn("스크립트 응답이 JSON 이 아닙니다: " + e.getMessage());
            return;
        }
        if (!(parsed instanceof List<?> ops)) {
            doc.warn("스크립트 응답은 요청 배열이어야 합니다");
            return;
        }
        if (ops.size() > MAX_OPS) {
            doc.warn("스크립트가 한 번에 요청을 너무 많이 보냈습니다(" + ops.size() + " > " + MAX_OPS + ")");
            return;
        }
        int commands = 0;
        for (Object o : ops) {
            if (!(o instanceof Map<?, ?> raw)) {
                doc.warn("스크립트 요청은 객체여야 합니다");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> op = (Map<String, Object>) raw;
            String name = str(op.get("op"));
            if ("command".equals(name)) {
                if (++commands > policy.maxPerCall()) {
                    doc.warn("한 번에 실행할 수 있는 명령 수를 넘었습니다");
                    continue;
                }
            }
            String error = execute(name, op);
            if (error != null) doc.warn("스크립트 요청 거부(" + name + "): " + error);
        }
    }

    private static String str(Object o) { return o instanceof String s ? s : null; }

    private static boolean bad(String s, int max) { return s == null || s.length() > max; }

    private String execute(String name, Map<String, Object> op) {
        if (name == null) return "op 가 없습니다";
        switch (name) {
            case "setVar" -> {
                String var = str(op.get("name")), value = op.get("value") == null ? null : str(op.get("value"));
                if (bad(var, 64) || !var.matches("(--)?[A-Za-z][A-Za-z0-9_-]*")) return "변수 이름이 올바르지 않습니다";
                if (value != null && value.length() > MAX_STRING) return "값이 너무 깁니다";
                doc.setVariable(var, value);
                return null;
            }
            case "command" -> {
                String line = str(op.get("line"));
                String why = policy.check(line);
                if (why != null) return why;
                if (commandSink == null) return "명령 실행이 연결되지 않았습니다";
                commandSink.accept(CommandPolicy.normalize(line));
                return null;
            }
            default -> { }
        }
        // 나머지는 모두 요소 대상
        String selector = str(op.get("target"));
        if (bad(selector, 200)) return "target 선택자가 없거나 너무 깁니다";
        List<UiElement> targets;
        try {
            targets = doc.queryAll(selector);
        } catch (IllegalArgumentException e) {
            return "잘못된 선택자: " + selector;
        }
        if (targets.size() > MAX_TARGETS) return "대상이 너무 많습니다(" + targets.size() + ")";
        if (targets.isEmpty()) return null;                       // 대상이 없으면 조용히 넘어간다(DOM 이 바뀌었을 수 있다)
        String attr = str(op.get("name")), value = op.get("value") == null ? null : str(op.get("value"));
        if (value != null && value.length() > MAX_STRING) return "값이 너무 깁니다";
        switch (name) {
            case "setAttr", "removeAttr" -> {
                if (bad(attr, 64) || !attr.matches("[A-Za-z][A-Za-z0-9_-]*")) return "속성 이름이 올바르지 않습니다";
                String lower = attr.toLowerCase(Locale.ROOT);
                if (lower.equals("src") || lower.equals("template")) return "이 속성은 스크립트가 바꿀 수 없습니다: " + attr;
                if (name.equals("setAttr") && EVENT_ATTRS.contains(lower) && (value == null || !value.startsWith("script:"))) {
                    return "이벤트 속성은 'script:' 로 시작하는 값만 넣을 수 있습니다";
                }
                for (UiElement el : targets) el.setAttr(attr, name.equals("removeAttr") ? null : value);
            }
            case "setText" -> {
                if (value == null) return "value 가 없습니다";
                for (UiElement el : targets) el.setText(value);
            }
            case "addClass", "removeClass", "toggleClass" -> {
                if (bad(attr, 64) || !attr.matches("[A-Za-z_][A-Za-z0-9_-]*")) return "클래스 이름이 올바르지 않습니다";
                boolean on = !Boolean.FALSE.equals(op.get("on"));
                for (UiElement el : targets) {
                    if (name.equals("addClass")) el.addClass(attr);
                    else if (name.equals("removeClass")) el.removeClass(attr);
                    else el.toggleClass(attr, on);
                }
            }
            case "setStyle" -> {
                if (bad(attr, 64) || !attr.matches("[A-Za-z-][A-Za-z0-9_-]*")) return "스타일 이름이 올바르지 않습니다";
                for (UiElement el : targets) el.setStyle(attr, value);
            }
            case "append" -> {
                String xml = str(op.get("xml"));
                if (bad(xml, MAX_STRING)) return "xml 이 없거나 너무 깁니다";
                String why = checkXml(xml);
                if (why != null) return why;
                for (UiElement el : targets) {
                    try {
                        el.appendChild(xml);
                    } catch (UiParseException e) {
                        return "xml 오류: " + e.getMessage();
                    }
                }
            }
            case "remove" -> {
                for (UiElement el : targets) {
                    if (el.parent() == null) return "루트는 지울 수 없습니다";
                }
                for (UiElement el : targets) el.remove();
            }
            default -> {
                return "알 수 없는 연산";
            }
        }
        return null;
    }

    /** 추가하려는 XML 이 스크립트/파일 포함 같은 위험한 요소를 담지 않았는지 본다. */
    private static String checkXml(String xml) {
        UiNode root;
        try {
            root = UiParser.parse(xml);
        } catch (UiParseException e) {
            return "xml 오류: " + e.getMessage();
        }
        List<UiNode> stack = new ArrayList<>(List.of(root));
        while (!stack.isEmpty()) {
            UiNode n = stack.remove(stack.size() - 1);
            if (BAD_TAGS.contains(n.tag())) return "추가할 수 없는 요소: <" + n.tag() + ">";
            for (Map.Entry<String, String> a : n.attributes().entrySet()) {
                String k = a.getKey().toLowerCase(Locale.ROOT);
                if (EVENT_ATTRS.contains(k) && !a.getValue().startsWith("script:")) return "이벤트 속성은 'script:' 로 시작해야 합니다";
            }
            stack.addAll(n.children());
        }
        return null;
    }

    public void close() { runtime.close(); }
}

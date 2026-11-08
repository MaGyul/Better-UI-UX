package dev.magyul.betteruiux.core.doc;

import dev.magyul.betteruiux.core.xml.UiNode;
import dev.magyul.betteruiux.core.xml.UiParseException;
import dev.magyul.betteruiux.core.xml.UiParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 문서 조립 단계: 레이아웃/스타일 전에 XML 트리를 확장한다.
 * <ul>
 *   <li>{@code <include src="card" title="Hi">...</include>} — 다른 파일을 그 자리에 끼워 넣는다.
 *       {@code src} 는 {@code card} 또는 {@code card.xml}. 나머지 속성은 매개변수가 된다.
 *       불러온 파일의 루트가 {@code fragment}/{@code screen} 이면 그 자식들만, 아니면 루트 요소 자체를 넣는다.</li>
 *   <li>{@code <template id="item" text="기본값"> ... </template>} + {@code <use template="item" text="Hi"/>} —
 *       같은 문서 안에서 반복해서 쓰는 조각. template 의 속성(id 제외)은 매개변수 기본값.</li>
 *   <li>매개변수: 속성값과 텍스트의 {@code ${이름}}, {@code ${이름:기본값}} 가 치환된다.</li>
 *   <li>{@code <slot/>}: include/use 요소의 자식(내용)이 들어갈 자리. slot 안의 내용은 비었을 때의 기본값.</li>
 * </ul>
 * 문제는 예외가 아니라 경고로 모은다(해당 요소만 빠지고 문서는 계속 표시된다).
 */
public final class UiExpander {
    private static final int MAX_DEPTH = 8;
    private static final Pattern PARAM = Pattern.compile("\\$\\{([A-Za-z0-9_.\\-]+)(?::([^}]*))?}");

    private final Path userDir;
    private final List<String> warnings;
    private final Set<String> reportedMissing = new LinkedHashSet<>();

    private UiExpander(Path userDir, List<String> warnings) {
        this.userDir = userDir;
        this.warnings = warnings;
    }

    /** @param userDir null 이면 기본 제공(클래스패스) 파일만 include 할 수 있다. */
    public static void expand(UiNode root, Path userDir, List<String> warnings) {
        UiExpander x = new UiExpander(userDir, warnings);
        x.expandIncludes(root, new ArrayDeque<>());
        x.expandTemplates(root);
    }

    // ---- include ----

    private void expandIncludes(UiNode tree, Deque<String> stack) {
        for (UiNode inc : find(tree, "include")) {
            if (inc.parent() == null) {
                warnings.add("include: 문서의 루트 요소로는 쓸 수 없습니다");
                continue;
            }
            String src = inc.attr("src");
            if (src == null || src.isBlank()) {
                warnings.add("include: src 속성이 없습니다");
                inc.detach();
                continue;
            }
            String file = src.endsWith(".xml") ? src : src + ".xml";
            if (stack.contains(file) || stack.size() >= MAX_DEPTH) {
                warnings.add("include '" + src + "': " + (stack.contains(file) ? "순환 참조" : "너무 깊게 중첩됨"));
                inc.detach();
                continue;
            }
            UiNode included = load(file, src);
            if (included == null) {
                inc.detach();
                continue;
            }
            stack.push(file);
            UiNode holder = wrap(included);
            expandIncludes(holder, stack);
            stack.pop();

            Map<String, String> params = new HashMap<>(inc.attributes());
            params.remove("src");
            substitute(holder, params, "include '" + src + "'");
            fillSlots(holder, inc.children());
            replace(inc, new ArrayList<>(holder.children()));
        }
    }

    private UiNode load(String file, String src) {
        try {
            Optional<String> xml = UiSources.loadFile(userDir, file);
            if (xml.isEmpty()) {
                warnings.add("include '" + src + "' 를 찾을 수 없습니다");
                return null;
            }
            return UiParser.parse(xml.get());
        } catch (UiParseException e) {
            warnings.add("include '" + src + "' XML 오류 (줄 " + e.line() + ")");
        } catch (IOException e) {
            warnings.add("include '" + src + "' 읽기 실패: " + e.getMessage());
        }
        return null;
    }

    /** 불러온 문서의 루트가 fragment/screen 이면 자식들만, 아니면 루트 자체를 담은 임시 컨테이너. */
    private static UiNode wrap(UiNode root) {
        if (root.tag().equals("fragment") || root.tag().equals("screen")) return root;
        UiNode holder = new UiNode("fragment");
        holder.append(root);
        return holder;
    }

    // ---- template / use ----

    private void expandTemplates(UiNode root) {
        Map<String, UiNode> templates = new HashMap<>();
        for (UiNode t : find(root, "template")) {
            String id = t.attr("id");
            t.detach();
            if (id == null || id.isBlank()) {
                warnings.add("template: id 속성이 없습니다");
            } else {
                if (templates.put(id, t) != null) warnings.add("template '" + id + "' 이(가) 중복 정의되었습니다");
            }
        }
        for (int pass = 0; pass < MAX_DEPTH; pass++) {
            List<UiNode> uses = find(root, "use");
            if (uses.isEmpty()) return;
            for (UiNode use : uses) expandUse(use, templates);
        }
        for (UiNode leftover : find(root, "use")) {
            warnings.add("use: 템플릿이 너무 깊게 중첩되었거나 서로를 참조합니다");
            leftover.detach();
        }
    }

    private void expandUse(UiNode use, Map<String, UiNode> templates) {
        String id = use.attr("template");
        UiNode t = id == null ? null : templates.get(id);
        if (t == null) {
            warnings.add("use: 템플릿 '" + id + "' 을(를) 찾을 수 없습니다");
            use.detach();
            return;
        }
        UiNode holder = new UiNode("fragment");
        for (UiNode c : t.children()) holder.append(c.copy());
        Map<String, String> params = new HashMap<>(t.attributes());
        params.remove("id");
        for (Map.Entry<String, String> e : use.attributes().entrySet()) {
            if (!e.getKey().equals("template")) params.put(e.getKey(), e.getValue());
        }
        substitute(holder, params, "template '" + id + "'");
        fillSlots(holder, use.children());
        replace(use, new ArrayList<>(holder.children()));
    }

    // ---- 공통 ----

    private static List<UiNode> find(UiNode tree, String tag) {
        List<UiNode> out = new ArrayList<>();
        collect(tree, tag, out);
        return out;
    }

    private static void collect(UiNode n, String tag, List<UiNode> out) {
        for (UiNode c : n.children()) {
            if (c.tag().equals(tag)) out.add(c);
            collect(c, tag, out);
        }
    }

    /** target 을 같은 위치의 replacements 로 바꾼다. */
    private static void replace(UiNode target, List<UiNode> replacements) {
        UiNode parent = target.parent();
        if (parent == null) return;
        int idx = parent.children().indexOf(target);
        target.detach();
        for (int i = 0; i < replacements.size(); i++) parent.insert(idx + i, replacements.get(i));
    }

    /**
     * 트리의 모든 속성값/텍스트에서 ${이름[:기본값]} 을 치환한다.
     * 안에 든 {@code <template>} 정의는 건드리지 않는다(그 매개변수는 나중에 use 할 때 채워진다).
     */
    private void substitute(UiNode n, Map<String, String> params, String where) {
        for (Map.Entry<String, String> e : n.attributes().entrySet()) e.setValue(subst(e.getValue(), params, where));
        n.setText(subst(n.text(), params, where));
        for (UiNode c : n.children()) {
            if (!c.tag().equals("template")) substitute(c, params, where);
        }
    }

    private String subst(String s, Map<String, String> params, String where) {
        if (s == null || !s.contains("${")) return s;
        Matcher m = PARAM.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String v = params.get(m.group(1));
            if (v == null) v = m.group(2);
            if (v == null) {
                if (reportedMissing.add(where + "/" + m.group(1))) {
                    warnings.add(where + ": 매개변수 '" + m.group(1) + "' 이(가) 없습니다");
                }
                v = "";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(v));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** holder 안의 {@code <slot/>} 을 content 의 복사본으로 바꾼다. content 가 비어 있으면 slot 의 기본 내용을 쓴다. */
    private static void fillSlots(UiNode holder, List<UiNode> content) {
        for (UiNode slot : find(holder, "slot")) {
            List<UiNode> repl = new ArrayList<>();
            List<UiNode> source = content.isEmpty() ? slot.children() : content;
            for (UiNode c : source) repl.add(c.copy());
            replace(slot, repl);
        }
    }
}

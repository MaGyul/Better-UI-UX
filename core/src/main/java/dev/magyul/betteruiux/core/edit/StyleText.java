package dev.magyul.betteruiux.core.edit;

import dev.magyul.betteruiux.core.edit.PropertyModel.Kind;
import dev.magyul.betteruiux.core.edit.PropertyModel.PropRow;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.core.style.Selector;
import java.util.ArrayList;
import java.util.List;

/**
 * 문서 안 {@code <style>} 블록의 규칙을 <b>원본 텍스트 위치와 함께</b> 읽고 고친다(주석·들여쓰기는 그대로 둔다).
 * 외부 {@code <link>} 스타일시트는 다루지 않는다. 파서는 {@link dev.magyul.betteruiux.core.style.Stylesheet} 와 같이 관대하다.
 */
final class StyleText {
    private StyleText() {}

    /** 선언 하나: {@code 속성: 값} 의 값 범위와 선언 전체({@code 속성: 값;}) 범위(절대 위치). */
    record Decl(String prop, int valueStart, int valueEnd, int declStart, int declEnd) {}

    /** 규칙 하나: 선택자 글자, 본문({@code { }} 안쪽) 범위, 선언들. */
    record Rule(String selector, int bodyStart, int bodyEnd, List<Decl> decls, String media) {
        Rule(String selector, int bodyStart, int bodyEnd, List<Decl> decls) { this(selector, bodyStart, bodyEnd, decls, null); }
    }

    /** 문서의 모든 {@code <style>} 규칙을 문서 순서로(@keyframes 등 @규칙 제외). */
    static List<Rule> rules(XmlSource src) {
        List<Rule> out = new ArrayList<>();
        scanAll(src, out, null);
        return out;
    }

    /** 문서 안 {@code <style>} 의 {@code @keyframes} 블록들(문서 순서). */
    static List<Kf> keyframes(XmlSource src) {
        List<Kf> out = new ArrayList<>();
        scanAll(src, null, out);
        return out;
    }

    private static void scanAll(XmlSource src, List<Rule> out, List<Kf> kfOut) {
        for (XmlSource.Element el : src.all()) {
            if (!el.tag.equals("style") || el.selfClosing() || el.endTagStart < 0) continue;
            int from = el.startTagEnd, to = el.endTagStart;
            String content = src.text().substring(from, to);
            String lead = content.stripLeading();
            if (lead.startsWith("<![CDATA[")) {                       // CDATA 안쪽만
                int s = from + content.indexOf("<![CDATA[") + 9;
                int e = src.text().lastIndexOf("]]>", to);
                if (e < s) continue;
                from = s;
                to = e;
            }
            scan(src.text(), from, to, out, kfOut, null);
        }
    }

    /** 외부 스타일시트(.uicss) 글 전체의 규칙들. */
    static List<Rule> cssRules(String css) {
        List<Rule> out = new ArrayList<>();
        scan(css, 0, css.length(), out, null, null);
        return out;
    }

    /** 외부 스타일시트 글 전체의 {@code @keyframes} 블록들. */
    static List<Kf> cssKeyframes(String css) {
        List<Kf> out = new ArrayList<>();
        scan(css, 0, css.length(), null, out, null);
        return out;
    }

    /** {@code @keyframes 이름 { ... }}: 본문({@code { }} 안쪽) 범위와 지점들(from / 50% / to 를 선택자로 가진 규칙). */
    record Kf(String name, int bodyStart, int bodyEnd, List<Rule> stops) {}

    private static void scan(String text, int from, int to, List<Rule> out, List<Kf> kfOut, String media) {
        char[] m = mask(text, from, to);
        int i = from;
        while (i < to) {
            int open = indexOf(m, '{', i, to);
            if (open < 0) break;
            int close = matching(m, open, to);
            if (close < 0) break;
            String selector = new String(m, i, open - i).trim();
            if (!selector.isEmpty() && !selector.startsWith("@")) {
                if (out != null) addRule(m, text, selector, open + 1, close, media, out, 0);
            } else if (selector.startsWith("@media") || selector.startsWith("@supports")) {
                // 조건 블록 안의 규칙도 편집 대상. @media 의 조건은 규칙에 붙여 두었다가 패널에서 지금 켜져 있는지 보여 준다.
                scan(text, open + 1, close, out, kfOut, selector.startsWith("@media") ? selector.substring(6).trim() : media);
            } else if (kfOut != null && selector.startsWith("@keyframes")) {
                List<Rule> stops = new ArrayList<>();
                scan(text, open + 1, close, stops, null, null);
                kfOut.add(new Kf(selector.substring("@keyframes".length()).trim(), open + 1, close, stops));
            }
            i = close + 1;
        }
    }

    /**
     * 규칙 하나를 목록에 더한다. 본문에 CSS 중첩 규칙({@code &:hover { }}, {@code .child { }}, {@code @media { }})이 있으면 부모의 선언만 부모 규칙으로 두고,
     * 중첩 규칙은 부모와 합친 선택자(예: {@code a:hover})의 규칙으로 따로 더한다(위치는 원문 그대로라 값 고치기·선언 추가가 중첩 블록 안에서 일어난다).
     */
    private static void addRule(char[] m, String text, String selector, int bodyStart, int bodyEnd, String media, List<Rule> out, int depth) {
        dev.magyul.betteruiux.core.style.NestedCss.Split sp =
                dev.magyul.betteruiux.core.style.NestedCss.split(new String(m, bodyStart, bodyEnd - bodyStart), bodyStart);
        if (sp.nested().isEmpty() || depth > 8) {
            out.add(new Rule(selector, bodyStart, bodyEnd, decls(m, bodyStart, bodyEnd), media));
            return;
        }
        char[] masked = m.clone();                                          // 부모의 선언을 읽을 때 중첩 블록은 빈칸으로 가린다
        for (dev.magyul.betteruiux.core.style.NestedCss.Nested nst : sp.nested()) {
            for (int k = nst.preludeStart(); k <= nst.bodyEnd() && k < masked.length; k++) if (masked[k] != (char) 10) masked[k] = ' ';
        }
        List<Decl> own = decls(masked, bodyStart, bodyEnd);
        if (!own.isEmpty()) out.add(new Rule(selector, bodyStart, bodyEnd, own, media));
        for (dev.magyul.betteruiux.core.style.NestedCss.Nested nst : sp.nested()) {
            String pre = nst.prelude(), low = pre.toLowerCase();
            if (low.startsWith("@media")) {
                out.add(new Rule(selector, nst.bodyStart(), nst.bodyEnd(), decls(m, nst.bodyStart(), nst.bodyEnd()), pre.substring(6).trim()));
            } else if (!pre.startsWith("@") && !pre.isEmpty()) {
                addRule(m, text, dev.magyul.betteruiux.core.style.NestedCss.combine(selector, pre), nst.bodyStart(), nst.bodyEnd(), media, out, depth + 1);
            }
        }
    }

    /** 주석({@code /* *}{@code /})을 같은 길이의 공백으로 바꾼 사본(위치를 그대로 쓰려고). */
    private static char[] mask(String text, int from, int to) {
        char[] m = text.toCharArray();
        int i = from;
        while (i < to - 1) {
            if (m[i] == '/' && m[i + 1] == '*') {
                int e = text.indexOf("*/", i + 2);
                int end = e < 0 || e + 2 > to ? to : e + 2;
                for (int k = i; k < end; k++) if (m[k] != '\n') m[k] = ' ';
                i = end;
            } else {
                i++;
            }
        }
        return m;
    }

    private static int indexOf(char[] m, char c, int from, int to) {
        for (int i = from; i < to; i++) if (m[i] == c) return i;
        return -1;
    }

    private static int matching(char[] m, int open, int to) {
        int depth = 0;
        for (int i = open; i < to; i++) {
            if (m[i] == '{') depth++;
            else if (m[i] == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static List<Decl> decls(char[] m, int from, int to) {
        List<Decl> out = new ArrayList<>();
        int start = from, paren = 0;
        char quote = 0;
        for (int i = from; i <= to; i++) {
            char c = i < to ? m[i] : ';';
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '(') paren++;
            else if (c == ')' && paren > 0) paren--;
            else if (c == ';' && paren == 0) {
                addDecl(m, start, i, i < to ? i + 1 : i, out);
                start = i + 1;
            }
        }
        return out;
    }

    private static void addDecl(char[] m, int s, int e, int declEnd, List<Decl> out) {
        int colon = -1;
        for (int i = s; i < e; i++) if (m[i] == ':') { colon = i; break; }
        if (colon < 0) return;
        String prop = new String(m, s, colon - s).trim();
        if (prop.isEmpty()) return;
        int vs = colon + 1, ve = e;
        while (vs < ve && Character.isWhitespace(m[vs])) vs++;
        while (ve > vs && Character.isWhitespace(m[ve - 1])) ve--;
        int ds = s;
        while (ds < e && Character.isWhitespace(m[ds])) ds++;
        out.add(new Decl(prop, vs, ve, ds, declEnd));
    }

    // ---- 선택한 요소에 적용되는 규칙 ----

    /** 이 요소에 맞는 규칙들(문서 순서)과 그 번호(= {@link #rules} 목록의 위치). */
    static List<Integer> matching(XmlSource src, LayoutBox box) { return matchingIn(rules(src), box); }

    static List<Integer> matchingIn(List<Rule> rules, LayoutBox box) {
        List<Integer> hits = new ArrayList<>();
        for (int n = 0; n < rules.size(); n++) {
            for (String one : Selector.splitList(rules.get(n).selector())) {
                try {
                    Selector parsed = Selector.parse(one.trim());
                    if (parsed.pseudoElement() == null && parsed.matches(box)) {
                        hits.add(n);
                        break;
                    }
                } catch (IllegalArgumentException e) {
                    // 잘못된 선택자는 건너뛴다
                }
            }
        }
        return hits;
    }

    static final String KEY = "css:", ADD_KEY = "css+:";

    /** 속성 패널에 붙일 "스타일 규칙" 구역의 행들(맞는 규칙이 없으면 빈 목록). */
    static List<PropRow> propRows(LayoutBox box, XmlSource src) { return propRows(box, src, c -> true); }

    /** {@code mediaActive}: {@code @media} 조건 글자가 지금 화면 크기에서 맞는지(패널에 켜짐/꺼짐으로 표시한다). */
    static List<PropRow> propRows(LayoutBox box, XmlSource src, java.util.function.Predicate<String> mediaActive) {
        List<Rule> rules = rules(src);
        List<PropRow> rows = new ArrayList<>();
        List<Integer> hits = matching(src, box);
        rows.add(PropertyModel.header("스타일 규칙"));
        for (int n : hits) ruleRows(rows, src.text(), rules.get(n), "§rule" + n, KEY + n + ":", ADD_KEY + n, mediaActive);
        rows.add(new PropRow("+ 새 규칙",NEW_KEY + selectorFor(box), "", true));   // 맞는 규칙이 없어도 첫 규칙을 만들 수 있다
        return rows;
    }

    private static void ruleRows(List<PropRow> rows, String text, Rule r, String headerKey, String declPrefix, String addKey,
                                 java.util.function.Predicate<String> mediaActive) {
        String label = r.selector();
        if (r.media() != null) label += "  @media " + r.media() + (mediaActive.test(r.media()) ? "  [적용 중]" : "  [꺼짐]");
        rows.add(new PropRow(label, headerKey, "", false, Kind.HEADER, List.of()));
        for (Decl d : r.decls()) rows.add(new PropRow(d.prop(), declPrefix + d.prop(), text.substring(d.valueStart(), d.valueEnd()), true));
        rows.add(new PropRow("+ 추가", addKey, "", true));
    }

    static final String NEW_KEY = "css*:";

    // ---- 외부 스타일시트({@code <link href="x.uicss"/>}) ----

    static final String X_KEY = "xcss:", X_ADD_KEY = "xcss+:", X_NEW_KEY = "xcss*:";

    /** 문서가 불러오는 외부 스타일시트 하나: 파일 이름과 그 내용. */
    record Sheet(String href, String text) {}

    /** 문서의 {@code <link>} 가 가리키는 스타일시트 파일 이름들(문서 순서, 중복 없음). */
    static List<String> linkedSheets(XmlSource src) {
        List<String> out = new ArrayList<>();
        for (XmlSource.Element el : src.all()) {
            if (!el.tag.equals("link")) continue;
            XmlSource.Attr rel = el.attr("rel"), href = el.attr("href");
            String r = rel == null ? null : src.text().substring(rel.valueStart, rel.valueEnd);
            if (href == null || (r != null && !r.equals("style") && !r.equals("stylesheet"))) continue;
            String h = src.text().substring(href.valueStart, href.valueEnd);
            if (!h.isEmpty() && !out.contains(h)) out.add(h);
        }
        return out;
    }

    /** 외부 스타일시트마다 "스타일시트: 파일" 구역(맞는 규칙이 없으면 새 규칙 칸만). */
    static List<PropRow> externalRows(LayoutBox box, List<Sheet> sheets) { return externalRows(box, sheets, c -> true); }

    static List<PropRow> externalRows(LayoutBox box, List<Sheet> sheets, java.util.function.Predicate<String> mediaActive) {
        List<PropRow> rows = new ArrayList<>();
        for (Sheet sh : sheets) {
            List<Rule> rules = cssRules(sh.text());
            rows.add(PropertyModel.header("스타일시트: " + sh.href()));
            for (int n : matchingIn(rules, box)) {
                ruleRows(rows, sh.text(), rules.get(n), "§xrule" + sh.href() + ":" + n, X_KEY + sh.href() + ":" + n + ":", X_ADD_KEY + sh.href() + ":" + n, mediaActive);
            }
            rows.add(new PropRow("+ 새 규칙", X_NEW_KEY + sh.href() + ":" + selectorFor(box), "", true));
        }
        return rows;
    }

    /** 외부 스타일시트용 키({@code xcss:파일:규칙:속성} 등)에서 파일 이름을 꺼낸다(아니면 null). */
    static String externalHref(String key) {
        if (isKeyframeKey(key)) {
            String sheet = keyframeSheet(key);
            return INLINE_SHEET.equals(sheet) ? null : sheet;
        }
        for (String p : new String[] {X_KEY, X_ADD_KEY, X_NEW_KEY}) {
            if (key.startsWith(p)) {
                int c = key.indexOf(':', p.length());
                return c < 0 ? null : key.substring(p.length(), c);
            }
        }
        return null;
    }

    /** 외부 스타일시트 {@code css} 에 키가 가리키는 수정을 반영한 새 글. 잘못된 입력이면 IllegalArgumentException. */
    static String applyExternal(String css, String key, String raw) {
        String href = externalHref(key);
        if (href == null) throw new IllegalArgumentException("알 수 없는 키: " + key);
        if (isKeyframeKey(key)) return applyKeyframe(css, cssKeyframes(css), key, raw);
        String prefix = key.startsWith(X_KEY) ? X_KEY : key.startsWith(X_ADD_KEY) ? X_ADD_KEY : X_NEW_KEY;
        String rest = key.substring(prefix.length() + href.length() + 1);
        List<Rule> rules = cssRules(css);
        if (prefix.equals(X_NEW_KEY)) {
            if (raw == null || raw.isBlank()) return css;
            String[] d = parseDecl(raw);
            String sep = css.isEmpty() || css.endsWith("\n") ? "" : "\n";
            return css + sep + rest + " { " + d[0] + ": " + d[1] + "; }\n";
        }
        if (prefix.equals(X_ADD_KEY)) {
            if (raw == null || raw.isBlank()) return css;
            String[] d = parseDecl(raw);
            return setDeclIn(css, rules, Integer.parseInt(rest), d[0], d[1]);
        }
        int colon = rest.indexOf(':');
        return setDeclIn(css, rules, Integer.parseInt(rest.substring(0, colon)), rest.substring(colon + 1), raw);
    }

    // ---- @keyframes ----

    static final String KF_KEY = "kf:", KF_ADD_KEY = "kf+:", KF_NEW_KEY = "kf*:", INLINE_SHEET = "~";

    static boolean isKeyframeKey(String key) { return key.startsWith(KF_KEY) || key.startsWith(KF_ADD_KEY) || key.startsWith(KF_NEW_KEY); }

    /** 키가 가리키는 스타일시트: {@link #INLINE_SHEET} 이면 문서 안 {@code <style>}, 아니면 외부 파일 이름. */
    static String keyframeSheet(String key) {
        String p = key.startsWith(KF_KEY) ? KF_KEY : key.startsWith(KF_ADD_KEY) ? KF_ADD_KEY : KF_NEW_KEY;
        int c = key.indexOf(':', p.length());
        return key.substring(p.length(), c);
    }

    /** 애니메이션 이름 {@code name} 의 {@code @keyframes} 를 "키프레임" 구역으로(없으면 빈 목록). */
    static List<PropRow> keyframeRows(String name, String sheet, String text, List<Kf> kfs) {
        List<PropRow> rows = new ArrayList<>();
        for (int k = 0; k < kfs.size(); k++) {
            Kf kf = kfs.get(k);
            if (!kf.name().equals(name)) continue;
            rows.add(PropertyModel.header("키프레임: " + name + (INLINE_SHEET.equals(sheet) ? "" : " (" + sheet + ")")));
            for (int i = 0; i < kf.stops().size(); i++) {
                String at = sheet + ":" + k + ":" + i;
                ruleRows(rows, text, kf.stops().get(i), "§kf" + at, KF_KEY + at + ":", KF_ADD_KEY + at, c -> true);
            }
            rows.add(new PropRow("+ 새 지점", KF_NEW_KEY + sheet + ":" + k, "", true));
        }
        return rows;
    }

    /** 키가 가리키는 키프레임 편집을 {@code text} 에 반영한 새 글. 잘못된 입력이면 IllegalArgumentException. */
    static String applyKeyframe(String text, List<Kf> kfs, String key, String raw) {
        String sheet = keyframeSheet(key);
        String p = key.startsWith(KF_KEY) ? KF_KEY : key.startsWith(KF_ADD_KEY) ? KF_ADD_KEY : KF_NEW_KEY;
        String[] f = key.substring(p.length() + sheet.length() + 1).split(":", 3);
        int k = Integer.parseInt(f[0]);
        if (k < 0 || k >= kfs.size()) throw new IllegalArgumentException("키프레임이 없습니다");
        Kf kf = kfs.get(k);
        if (p.equals(KF_NEW_KEY)) {
            String s = raw == null ? "" : raw.trim();
            if (s.isEmpty()) return text;
            int sp = s.indexOf(' ');
            String label = sp < 0 ? s : s.substring(0, sp);
            if (sp < 0 || !label.matches("from|to|[0-9]+([.][0-9]+)?%")) throw new IllegalArgumentException("지점 속성: 값 형태로 입력하세요 (예: 50% opacity: 0.5)");
            String[] d = parseDecl(s.substring(sp + 1));
            for (int i = 0; i < kf.stops().size(); i++) {                       // 이미 있는 지점이면 그 지점에 덧붙인다
                if (kf.stops().get(i).selector().equalsIgnoreCase(label)) return setDeclIn(text, kf.stops(), i, d[0], d[1]);
            }
            return addStop(text, kf, label, d[0], d[1]);
        }
        int stop = Integer.parseInt(f[1]);
        if (p.equals(KF_ADD_KEY)) {
            if (raw == null || raw.isBlank()) return text;
            String[] d = parseDecl(raw);
            return setDeclIn(text, kf.stops(), stop, d[0], d[1]);
        }
        return setDeclIn(text, kf.stops(), stop, f[2], raw);
    }

    /** {@code @keyframes} 본문 끝에 지점 하나를 덧붙인다(들여쓰기는 첫 지점에 맞춘다). */
    static String addStop(String t, Kf kf, String label, String prop, String value) {
        String stop = label + " { " + prop + ": " + value + " }";
        String body = t.substring(kf.bodyStart(), kf.bodyEnd());
        int last = body.length();
        while (last > 0 && Character.isWhitespace(body.charAt(last - 1))) last--;
        int at = kf.bodyStart() + last;
        if (body.contains("\n")) {
            String indent = kf.stops().isEmpty() ? "    " : indentOf(t, kf.stops().get(0).bodyStart() - 1);
            return t.substring(0, at) + "\n" + indent + stop + t.substring(at);
        }
        return t.substring(0, at) + " " + stop + t.substring(at);
    }

    /** {@code 속성: 값} 글을 {속성, 값} 으로 쪼갠다. 형식이 틀리면 IllegalArgumentException. */
    static String[] parseDecl(String raw) {
        String s = raw == null ? "" : raw.trim();
        int c = s.indexOf(':');
        if (c < 0) c = s.indexOf(' ');
        if (c <= 0) throw new IllegalArgumentException("속성: 값 형태로 입력하세요");
        String prop = s.substring(0, c).trim(), value = s.substring(c + 1).trim();
        if (!prop.matches("[A-Za-z-][A-Za-z0-9-]*") || value.isEmpty()) throw new IllegalArgumentException("속성: 값 형태로 입력하세요");
        return new String[] {prop, value};
    }

    /** 새 규칙의 선택자: id 가 있으면 #id, 없으면 첫 class, 둘 다 없으면 태그 이름. */
    static String selectorFor(LayoutBox box) {
        String id = box.id();
        if (id != null && !id.isBlank()) return "#" + id.trim();
        String cls = box.node.attr("class");
        if (cls != null && !cls.isBlank()) return "." + cls.trim().split("\\s+")[0];
        return box.tag();
    }

    /** 새 규칙을 만든다: 기존 {@code <style>} 끝에 덧붙이고, 없으면 루트 요소 맨 앞에 {@code <style>} 블록을 만든다. */
    static String addRule(XmlSource src, String selector, String declaration) {
        String s = declaration == null ? "" : declaration.trim();
        int c = s.indexOf(':');
        if (c <= 0) c = s.indexOf(' ');
        if (c <= 0) throw new IllegalArgumentException("속성: 값 형태로 입력하세요");
        String prop = s.substring(0, c).trim(), value = s.substring(c + 1).trim();
        if (!prop.matches("[A-Za-z-][A-Za-z0-9-]*") || value.isEmpty()) throw new IllegalArgumentException("속성: 값 형태로 입력하세요");
        String t = src.text();
        String rule = selector + " { " + prop + ": " + value + "; }";
        for (XmlSource.Element el : src.all()) {
            if (!el.tag.equals("style") || el.selfClosing() || el.endTagStart < 0) continue;
            int at = el.endTagStart;
            int cdata = t.lastIndexOf("]]>", at);
            if (cdata >= el.startTagEnd) at = cdata;
            int ls = at;
            while (ls > el.startTagEnd && t.charAt(ls - 1) != '\n' && Character.isWhitespace(t.charAt(ls - 1))) ls--;   // 닫는 태그 앞 들여쓰기는 그대로 둔다
            String indent = t.substring(ls, at);
            if (ls > el.startTagEnd && t.charAt(ls - 1) == '\n') {
                return t.substring(0, ls) + indent + "  " + rule + "\n" + t.substring(ls);
            }
            return t.substring(0, at) + " " + rule + " " + t.substring(at);
        }
        XmlSource.Element root = src.root();
        if (root.selfClosing()) throw new IllegalArgumentException("루트 요소가 비어 있어 스타일을 넣을 수 없어요");
        String block = "\n  <style>\n    " + rule + "\n  </style>";
        return t.substring(0, root.startTagEnd) + block + t.substring(root.startTagEnd);
    }

    // ---- 고치기 ----

    /**
     * 규칙 {@code ruleNo} 의 선언 {@code prop} 값을 바꾼다. 값이 비었으면(null/빈 문자열) 선언을 지운다. 없는 속성이면 규칙 끝에 덧붙인다.
     *
     * @throws IllegalArgumentException 규칙이 없을 때
     */
    static String setDecl(XmlSource src, int ruleNo, String prop, String value) { return setDeclIn(src.text(), rules(src), ruleNo, prop, value); }

    /** {@link #setDecl} 의 일반형: 규칙 목록과 그 글을 직접 받는다(문서 안 &lt;style&gt; 이든 외부 파일이든). */
    static String setDeclIn(String t, List<Rule> rules, int ruleNo, String prop, String value) {
        if (ruleNo < 0 || ruleNo >= rules.size()) throw new IllegalArgumentException("규칙이 없습니다: " + ruleNo);
        Rule r = rules.get(ruleNo);
        boolean remove = value == null || value.isBlank();
        for (Decl d : r.decls()) {
            if (!d.prop().equalsIgnoreCase(prop)) continue;
            if (!remove) return t.substring(0, d.valueStart()) + value.trim() + t.substring(d.valueEnd());
            int s = d.declStart(), e = d.declEnd();
            while (s > r.bodyStart() && t.charAt(s - 1) != '\n' && Character.isWhitespace(t.charAt(s - 1))) s--;   // 줄 들여쓰기까지
            if (s > r.bodyStart() && t.charAt(s - 1) == '\n') s--;                                                    // 줄 하나를 통째로
            else if (e < r.bodyEnd() && Character.isWhitespace(t.charAt(e))) e++;
            return t.substring(0, s) + t.substring(e);
        }
        if (remove) return t;                                                                                        // 지울 것이 없다
        String body = t.substring(r.bodyStart(), r.bodyEnd());
        int last = body.length();
        while (last > 0 && Character.isWhitespace(body.charAt(last - 1))) last--;
        int at = r.bodyStart() + last;
        boolean needSemi = last > 0 && body.charAt(last - 1) != ';';
        String line = prop + ": " + value.trim() + ";";
        String insert;
        if (body.contains("\n")) {
            String indent = r.decls().isEmpty() ? "      " : indentOf(t, r.decls().get(0).declStart());
            insert = (needSemi ? ";" : "") + "\n" + indent + line;
        } else {
            insert = (needSemi ? ";" : "") + (last > 0 ? " " : " ") + line;
            if (last == 0) at = r.bodyStart();
        }
        return t.substring(0, at) + insert + t.substring(at);
    }

    private static String indentOf(String t, int pos) {
        int s = pos;
        while (s > 0 && t.charAt(s - 1) != '\n') s--;
        StringBuilder sb = new StringBuilder();
        for (int i = s; i < pos && Character.isWhitespace(t.charAt(i)); i++) sb.append(t.charAt(i));
        return sb.toString();
    }

    /** 속성 패널 키({@code css:규칙:속성}, {@code css+:규칙})로 값을 반영한다. 잘못된 입력이면 IllegalArgumentException. */
    /**
     * 규칙 편집(키 {@code css:...}/{@code xcss:...} 등)으로 방금 쓴 선언을 검사해 경고 문장을 돌려준다(없으면 null).
     * 추가/새 규칙은 입력한 {@code 속성: 값} 에서, 기존 선언의 값 고치기는 키 끝의 속성 이름에서 속성을 읽는다.
     */
    /** 규칙에 선언을 더하는 칸의 키(추가 / 새 규칙, 인라인 / 외부 시트)인가. 속성 이름·값 자동 완성이 붙는다. */
    static boolean isRuleAddKey(String key) {
        return key != null && (key.startsWith(ADD_KEY) || key.startsWith(NEW_KEY) || key.startsWith(X_ADD_KEY) || key.startsWith(X_NEW_KEY));
    }

    static String lint(String key, String raw) {
        if (raw == null || raw.isBlank() || isKeyframeKey(key)) return null;
        try {
            if (key.startsWith(ADD_KEY) || key.startsWith(NEW_KEY) || key.startsWith(X_ADD_KEY) || key.startsWith(X_NEW_KEY)) {
                for (dev.magyul.betteruiux.core.style.Stylesheet.Declaration d : dev.magyul.betteruiux.core.style.Stylesheet.parseDeclarations(raw)) {
                    String w = dev.magyul.betteruiux.core.style.Stylesheet.lint(d.prop(), d.value());
                    if (w != null) return w;
                }
                return null;
            }
            int colon = key.lastIndexOf(':');
            return colon < 0 ? null : dev.magyul.betteruiux.core.style.Stylesheet.lint(key.substring(colon + 1), raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String apply(XmlSource src, String key, String raw) {
        if (isKeyframeKey(key)) return applyKeyframe(src.text(), keyframes(src), key, raw);
        if (key.startsWith(NEW_KEY)) {
            if (raw == null || raw.isBlank()) return src.text();
            return addRule(src, key.substring(NEW_KEY.length()), raw);
        }
        if (key.startsWith(ADD_KEY)) {
            int n = Integer.parseInt(key.substring(ADD_KEY.length()));
            if (raw == null || raw.isBlank()) return src.text();
            String[] d = parseDecl(raw);
            return setDecl(src, n, d[0], d[1]);
        }
        String rest = key.substring(KEY.length());
        int colon = rest.indexOf(':');
        return setDecl(src, Integer.parseInt(rest.substring(0, colon)), rest.substring(colon + 1), raw);
    }
}

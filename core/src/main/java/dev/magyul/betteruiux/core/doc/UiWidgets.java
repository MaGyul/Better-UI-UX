package dev.magyul.betteruiux.core.doc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** 커스텀 위젯 태그 레지스트리. 내장 태그(label, button, ...)는 덮어쓸 수 없다. */
public final class UiWidgets {
    private static final java.util.Set<String> BUILTIN = java.util.Set.of(
            "screen", "hud", "panel", "row", "column", "stack", "label", "button", "image", "spacer",
            "style", "link", "template", "use", "include", "slot", "fragment", "script", "video");
    private static final Map<String, Function<UiElement, UiWidget>> FACTORIES = new ConcurrentHashMap<>();

    private UiWidgets() {}

    /** @throws IllegalArgumentException 태그가 내장 태그와 같거나 형식이 올바르지 않을 때 */
    public static void register(String tag, Function<UiElement, UiWidget> factory) {
        if (!tag.matches("[a-z][a-z0-9-]*") || BUILTIN.contains(tag)) {
            throw new IllegalArgumentException("사용할 수 없는 위젯 태그: " + tag);
        }
        FACTORIES.put(tag, factory);
    }

    public static void unregister(String tag) { FACTORIES.remove(tag); }

    static boolean has(String tag) { return FACTORIES.containsKey(tag); }

    /** 노드에 붙은 위젯을 돌려주고, 없으면 만들어 붙인다. 등록되지 않은 태그면 null. */
    static UiWidget instance(UiElement element) {
        Function<UiElement, UiWidget> f = FACTORIES.get(element.tag());
        if (f == null) return null;
        Object existing = element.node().userData();
        if (existing instanceof UiWidget w) return w;
        UiWidget w = f.apply(element);
        element.node().setUserData(w);
        return w;
    }
}

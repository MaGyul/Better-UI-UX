package dev.magyul.betteruiux.core.doc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * XML {@code onClick="액션id"} 로 호출되는 액션 레지스트리. 내장: {@code close}.
 * 모드는 {@link #register} 로 자기 액션(예: {@code mymod:buy})을 등록한다.
 */
public final class UiActions {
    /** 액션이 호출될 때 전달되는 정보. */
    public record Context(UiDocument document, UiElement source, String action) {}

    private static final Map<String, Consumer<Context>> ACTIONS = new ConcurrentHashMap<>();

    static {
        ACTIONS.put("close", ctx -> ctx.document().close());
    }

    private UiActions() {}

    public static void register(String id, Consumer<Context> handler) { ACTIONS.put(id, handler); }

    public static void unregister(String id) { ACTIONS.remove(id); }

    public static boolean isRegistered(String id) { return ACTIONS.containsKey(id); }

    /** 등록된 액션 id 들(이름순). 에디터의 {@code onClick} 자동 완성에 쓴다. */
    public static java.util.List<String> registeredIds() {
        java.util.List<String> ids = new java.util.ArrayList<>(ACTIONS.keySet());
        java.util.Collections.sort(ids);
        return ids;
    }

    /** @return 등록된 액션이 있어 실행했으면 true */
    static boolean dispatch(Context ctx) {
        Consumer<Context> h = ACTIONS.get(ctx.action());
        if (h == null) return false;
        h.accept(ctx);
        return true;
    }
}

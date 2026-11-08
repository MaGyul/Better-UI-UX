package dev.magyul.betteruiux.mc26;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XML {@code onClick="..."} 액션 레지스트리. 내장: {@code close}.
 * 모드는 {@link #register} 로 자기 액션(예: {@code mymod:buy})을 등록한다. (Java API 의 시작점)
 */
public final class UiActions {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");
    private static final Map<String, Consumer<Screen>> ACTIONS = new ConcurrentHashMap<>();

    static {
        ACTIONS.put("close", Screen::onClose);
        ACTIONS.put("hello", s -> UiOpener.say(net.minecraft.client.Minecraft.getInstance(), "Hello from XML!"));
    }

    private UiActions() {}

    public static void register(String id, Consumer<Screen> handler) { ACTIONS.put(id, handler); }

    static void dispatch(String action, Screen screen) {
        Consumer<Screen> h = ACTIONS.get(action);
        if (h == null) {
            LOG.warn("[better-uiux] unknown action '{}'", action);
            return;
        }
        h.accept(screen);
    }
}

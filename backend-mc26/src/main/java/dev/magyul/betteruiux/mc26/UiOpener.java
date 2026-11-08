package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.doc.UiSources;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** id 로 UI XML 을 찾아 Screen 으로 연다. 로더(Fabric/NeoForge)의 명령/키바인드가 호출하는 공통 진입점. */
public final class UiOpener {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");

    private UiOpener() {}

    /** 개발용: JVM 속성 -Dbetteruiux.autoopen=<id> 가 있으면 시작 직후 해당 화면을 연다. */
    public static void autoOpenIfRequested(Path userDir) {
        String id = System.getProperty("betteruiux.autoopen");
        if (id != null && !id.isBlank()) open(id, userDir);
    }

    /** @param userDir {@code <gameDir>/config/better-uiux/ui} */
    public static void open(String id, Path userDir) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try {
                Optional<String> xml = UiSources.load(userDir, id);
                if (xml.isEmpty()) {
                    say(mc, "UI '" + id + "' not found (config/better-uiux/ui/" + id + ".xml)");
                    return;
                }
                UiDocument doc = UiDocument.parse(xml.get(), new McTextMetrics(mc.font));
                mc.gui.setScreen(new UiScreen(Component.literal(id), doc));
            } catch (UiParseException e) {
                say(mc, "UI '" + id + "' XML error (line " + e.line() + "): " + e.getMessage());
            } catch (IOException e) {
                say(mc, "UI '" + id + "' read error: " + e.getMessage());
            }
        });
    }

    static void say(Minecraft mc, String msg) {
        LOG.warn("[better-uiux] {}", msg);
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[better-uiux] " + msg));
    }
}

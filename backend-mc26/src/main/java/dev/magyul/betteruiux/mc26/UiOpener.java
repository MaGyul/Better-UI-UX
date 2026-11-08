package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiConfig;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.doc.UiDocumentLoader;
import dev.magyul.betteruiux.core.doc.UiSources;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** id 로 UI 문서를 찾아 Screen 으로 연다. 로더(Fabric/NeoForge)의 명령/키바인드가 호출하는 공통 진입점. */
public final class UiOpener {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");

    private UiOpener() {}

    /** 개발용: JVM 속성 -Dbetteruiux.autoopen=&lt;id&gt; 가 있으면 해당 화면을 연다. */
    public static void autoOpenIfRequested(Path baseDir) {
        String id = System.getProperty("betteruiux.autoopen");
        if (id != null && !id.isBlank()) open(id, baseDir);
    }

    /** @param baseDir {@code <gameDir>/config/better-uiux} (설정 파일 {@code config.toml}, UI 폴더 {@code ui/}) */
    public static void open(String id, Path baseDir) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            Path userDir = baseDir.resolve("ui");
            boolean hotReload = UiConfig.load(baseDir.resolve("config.toml")).bool("hotReload.enabled", true);
            McTextMetrics metrics = new McTextMetrics(mc.font);
            if (!UiSources.validId(id)) {
                say(mc, "잘못된 UI id: " + id);
                return;
            }
            try {
                UiDocument doc = UiDocumentLoader.load(userDir, id, metrics);
                UiApi.fireOpened(id, doc);
                mc.gui.setScreen(new UiScreen(Component.literal(id), id, userDir, metrics, doc, null, hotReload));
            } catch (NoSuchFileException e) {
                say(mc, "UI '" + id + "' 를 찾을 수 없습니다 (config/better-uiux/ui/" + id + ".xml)");
            } catch (UiParseException e) {
                // 문법 오류여도 화면은 열어 오류 띠를 보여준다. 파일을 고쳐 저장하면 핫리로드로 바로 나타난다.
                String msg = "XML 오류 (줄 " + e.line() + "): " + e.getMessage();
                LOG.warn("[better-uiux] '{}': {}", id, msg);
                mc.gui.setScreen(new UiScreen(Component.literal(id), id, userDir, metrics, null, msg, hotReload));
            } catch (IOException e) {
                say(mc, "UI '" + id + "' 읽기 실패: " + e.getMessage());
            }
        });
    }

    static void say(Minecraft mc, String msg) {
        LOG.warn("[better-uiux] {}", msg);
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[better-uiux] " + msg));
    }
}

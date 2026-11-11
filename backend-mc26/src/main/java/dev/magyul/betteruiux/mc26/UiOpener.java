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

    /** 개발용: JVM 속성 -Dbetteruiux.autoopen=&lt;id&gt; 가 있으면 해당 화면을 연다(-Dbetteruiux.autoedit=true 면 에디터까지). */
    public static void autoOpenIfRequested(Path baseDir) {
        String id = System.getProperty("betteruiux.autoopen");
        if (id != null && !id.isBlank()) open(id, baseDir, Boolean.getBoolean("betteruiux.autoedit"));
    }

    private static java.util.List<String> commaList(String s) {
        if (s == null || s.isBlank()) return java.util.List.of();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String part : s.split(",")) if (!part.isBlank()) out.add(part.trim());
        return out;
    }

    /** @param baseDir {@code <gameDir>/config/better-uiux} (설정 파일 {@code config.toml}, UI 폴더 {@code ui/}) */
    public static void open(String id, Path baseDir) { open(id, baseDir, false); }

    /** @param editor true 면 화면이 열리자마자 인게임 에디터를 켠다(설정에서 에디터가 꺼져 있으면 안내만 한다). */
    public static void open(String id, Path baseDir, boolean editor) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            Path userDir = baseDir.resolve("ui");
            UiConfig config = UiConfig.load(baseDir.resolve("config.toml"));
            UiScreen.Options options = new UiScreen.Options(
                    config.bool("hotReload.enabled", true), config.bool("editor.enabled", false), editor,
                    config.bool("scripting.enabled", false), commaList(config.get("scripting.commands")));
            McTextMetrics metrics = new McTextMetrics(mc.font);
            if (!UiSources.validId(id)) {
                say(mc, "잘못된 UI id: " + id);
                return;
            }
            try {
                UiDocument doc = UiDocumentLoader.load(userDir, id, metrics);
                UiApi.fireOpened(id, doc);
                mc.gui.setScreen(new UiScreen(Component.literal(id), id, userDir, metrics, doc, null, options));
            } catch (NoSuchFileException e) {
                String unsaved = NewDocs.get(userDir, id);        // 에디터에서 새로 만들었지만 아직 저장하지 않은 화면
                if (unsaved == null) {
                    say(mc, "UI '" + id + "' 를 찾을 수 없습니다 (config/better-uiux/ui/" + id + ".xml)");
                    return;
                }
                try {
                    UiDocument doc = UiDocumentLoader.loadText(userDir, unsaved, metrics);
                    mc.gui.setScreen(new UiScreen(Component.literal(id), id, userDir, metrics, doc, null, options));
                } catch (IOException | UiParseException ex) {
                    say(mc, "UI '" + id + "' 읽기 실패: " + ex.getMessage());
                }
            } catch (UiParseException e) {
                // 문법 오류여도 화면은 열어 오류 띠를 보여준다. 파일을 고쳐 저장하면 핫리로드로 바로 나타난다.
                String msg = "XML 오류 (줄 " + e.line() + "): " + e.getMessage();
                LOG.warn("[better-uiux] '{}': {}", id, msg);
                mc.gui.setScreen(new UiScreen(Component.literal(id), id, userDir, metrics, null, msg, options));
            } catch (IOException e) {
                say(mc, "UI '" + id + "' 읽기 실패: " + e.getMessage());
            }
        });
    }

    /** 열린 화면이 하나도 없는 빈 에디터를 연다(/uiux editor): 안에서 File 로 편집할 화면을 고르거나 새로 만든다. 에디터가 꺼져 있으면 안내만 한다. */
    public static void openBlankEditor(Path baseDir) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            Path userDir = baseDir.resolve("ui");
            UiConfig config = UiConfig.load(baseDir.resolve("config.toml"));
            if (!config.bool("editor.enabled", false)) {
                say(mc, "에디터가 꺼져 있습니다 (config/better-uiux/config.toml 의 [editor] enabled = true)");
                return;
            }
            UiScreen.Options options = new UiScreen.Options(
                    config.bool("hotReload.enabled", true), true, true,
                    config.bool("scripting.enabled", false), commaList(config.get("scripting.commands")));
            McTextMetrics metrics = new McTextMetrics(mc.font);
            mc.gui.setScreen(new UiScreen(Component.literal("editor"), UiScreen.BLANK_ID, userDir, metrics, null, null, options));
        });
    }

    static void say(Minecraft mc, String msg) {
        LOG.warn("[better-uiux] {}", msg);
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[better-uiux] " + msg));
    }
}

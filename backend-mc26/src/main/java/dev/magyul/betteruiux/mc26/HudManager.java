package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiConfig;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.doc.UiDocumentLoader;
import dev.magyul.betteruiux.core.doc.UiFileWatcher;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XML 로 정의한 HUD 를 게임 화면(인게임)에 그린다. 입력은 받지 않는 읽기 전용 화면이라 마우스/키 처리가 없다.
 *
 * <p>설정({@code config.toml}): {@code [hud] ids = "hud_demo,my_hud"} — 켤 문서 id 들({@code config/better-uiux/ui/<id>.uiml} 또는 기본 제공).
 * 값은 {@link DataSources} 의 {@code {player.health}} 같은 바인딩으로 채우고, 파일을 고치면 바로 다시 불러온다(핫리로드).
 * 로더(Fabric/NeoForge)가 자기 HUD 훅에서 {@link #render} 를 부른다.
 */
public final class HudManager {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");

    private static Path baseDir;
    private static List<String> ids = List.of();
    private static final Map<String, UiDocument> DOCS = new LinkedHashMap<>();
    private static UiFileWatcher watcher;
    private static boolean loadedConfig;
    private static int lastW = -1, lastH = -1;

    private HudManager() {}

    /** 설정 폴더({@code config/better-uiux})를 알려 준다. */
    static void init(Path configBaseDir) { baseDir = configBaseDir; }

    private static void ensureConfig() {
        if (loadedConfig || baseDir == null) return;
        loadedConfig = true;
        UiConfig cfg = UiConfig.load(baseDir.resolve("config.toml"));
        String raw = cfg.get("hud.ids");
        List<String> list = new ArrayList<>();
        if (raw != null) for (String part : raw.split(",")) if (!part.isBlank()) list.add(part.trim());
        ids = list;
        if (!ids.isEmpty()) {
            try {
                watcher = UiFileWatcher.start(baseDir.resolve("ui"));
            } catch (IOException e) {
                LOG.warn("[better-uiux] hud hot reload disabled: {}", e.toString());
            }
            LOG.info("[better-uiux] hud documents: {}", ids);
        }
    }

    private static void reloadAll() {
        Minecraft mc = Minecraft.getInstance();
        McTextMetrics metrics = new McTextMetrics(mc.font);
        Path userDir = baseDir.resolve("ui");
        for (String id : ids) {
            try {
                UiDocument doc = UiDocumentLoader.load(userDir, id, metrics);
                UiApi.fireOpened(id, doc);                       // 데이터 소스 바인딩과 모드의 훅을 적용한다
                doc.layout(lastW, lastH);
                DOCS.put(id, doc);
                for (String w : doc.warnings()) LOG.warn("[better-uiux] hud '{}': {}", id, w);
            } catch (IOException | dev.magyul.betteruiux.core.xml.UiParseException | RuntimeException e) {
                LOG.warn("[better-uiux] hud '{}' 을(를) 불러오지 못했습니다(이전 것 유지): {}", id, e.toString());
            }
        }
    }

    /** 로더의 HUD 훅에서 매 프레임 부른다. 켜 둔 HUD 문서가 없으면 아무것도 하지 않는다. */
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        ensureConfig();
        if (ids.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;               // F1 로 HUD 를 숨기면 게임이 HUD 훅 자체를 부르지 않는다
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        boolean changed = watcher != null && watcher.pollChanged(300);
        if (DOCS.isEmpty() || changed) {
            lastW = w;
            lastH = h;
            reloadAll();
        } else if (w != lastW || h != lastH) {
            lastW = w;
            lastH = h;
            for (UiDocument d : DOCS.values()) d.layout(w, h);
        }
        McGfx gfx = new McGfx(graphics, mc.font);
        for (UiDocument d : DOCS.values()) d.paint(gfx, -1, -1);
    }
}

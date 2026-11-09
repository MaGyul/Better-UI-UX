package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiActions;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.doc.UiElement;
import dev.magyul.betteruiux.core.doc.UiWidget;
import dev.magyul.betteruiux.core.doc.UiWidgets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 모드가 쓰는 공개 API(클라이언트). XML 은 구조와 스타일, 로직은 Java 로 나눈다.
 *
 * <pre>{@code
 * UiApi.registerAction("mymod:buy", ctx -> ...);             // XML: <button onClick="mymod:buy"/>
 * UiApi.registerWidget("minimap", el -> new MinimapWidget()); // XML: <minimap/>
 * UiApi.onDocumentOpened((id, doc) -> {                       // 화면이 열릴 때(핫리로드 포함)마다
 *     if (id.equals("shop")) doc.bind("coins", () -> player.coins());
 * });
 * UiApi.open("shop");
 * }</pre>
 */
public final class UiApi {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");
    private static final List<BiConsumer<String, UiDocument>> OPENED = new CopyOnWriteArrayList<>();
    private static volatile Path baseDir;

    private UiApi() {}

    /** 로더 진입점이 설정 폴더({@code config/better-uiux})를 알려준다. */
    public static void init(Path configBaseDir) {
        baseDir = configBaseDir;
        HudManager.init(configBaseDir);
        RemoteAccess.init(configBaseDir);
        ExternalImages.init(configBaseDir);
        ExternalVideos.init(configBaseDir);
        BackendBuiltins.register();
        ScriptBridge.prewarm(configBaseDir);
    }

    /** 화면을 연다(렌더 스레드로 넘겨 실행). id 는 {@code config/better-uiux/ui/<id>.xml} 또는 기본 제공 파일. */
    public static void open(String id) {
        requireInit();
        UiOpener.open(id, baseDir);
    }

    /** 화면을 연 뒤 인게임 에디터를 켠다. 설정({@code editor.enabled})에서 에디터가 꺼져 있으면 안내 메시지만 보여준다. */
    public static void edit(String id) {
        requireInit();
        UiOpener.open(id, baseDir, true);
    }

    public static void registerAction(String id, Consumer<UiActions.Context> handler) { UiActions.register(id, handler); }

    public static void registerWidget(String tag, Function<UiElement, UiWidget> factory) { UiWidgets.register(tag, factory); }

    /** 문서가 만들어질 때마다(처음 열 때와 핫리로드 때) 호출된다. 바인딩/이벤트 핸들러 설정에 쓴다. */
    public static void onDocumentOpened(BiConsumer<String, UiDocument> listener) { OPENED.add(listener); }

    /** 지금 열려 있는 UI 문서(없으면 null). */
    public static UiDocument current() {
        return Minecraft.getInstance().gui.screen() instanceof UiScreen s ? s.document() : null;
    }

    /**
     * 모든 문서에서 {@code {이름}} 으로 쓸 수 있는 읽기 전용 값을 추가한다(내장: fps, player.health, world.day ...).
     * 값은 문서가 그려질 때마다 {@code value} 를 읽는다. 이름은 {@code [A-Za-z0-9_.-]} 만 가능하다.
     */
    public static void registerDataSource(String name, java.util.function.Supplier<?> value) { DataSources.register(name, value); }

    static void fireOpened(String id, UiDocument doc) {
        DataSources.bindTo(doc);
        ClientSync.bindState(doc);                                                         // {server.키}
        doc.setImageSizer(src -> ExternalVideos.handles(src) ? ExternalVideos.size(src, doc::requestRelayout)
                : ExternalImages.size(src, doc::requestRelayout));                        // file: 이미지/영상은 원래 크기로 놓는다                                     // 내장/등록된 데이터 소스 먼저(모드의 훅이 같은 이름을 덮어쓸 수 있다)
        for (BiConsumer<String, UiDocument> l : OPENED) {
            try {
                l.accept(id, doc);
            } catch (RuntimeException e) {
                LOG.warn("[better-uiux] onDocumentOpened 리스너 오류 ('{}'): {}", id, e.toString());
            }
        }
    }

    private static void requireInit() {
        if (baseDir == null) throw new IllegalStateException("UiApi.init() 이 호출되지 않았습니다");
    }
}

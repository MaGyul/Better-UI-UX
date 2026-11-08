package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.core.doc.AtomicFiles;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.doc.UiDocumentLoader;
import dev.magyul.betteruiux.core.doc.UiSources;
import dev.magyul.betteruiux.core.edit.EditorController;
import dev.magyul.betteruiux.core.edit.EditorHost;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/**
 * 에디터와 게임 사이의 연결. 이 클래스는 에디터를 처음 켤 때 로드되므로, 설정에서 에디터가 꺼져 있으면
 * 에디터 코드는 메모리에 올라오지 않는다.
 *
 * <p>편집 대상 텍스트는 사용자 폴더의 파일이 있으면 그것, 없으면 기본 제공(클래스패스) 파일이다. 저장은 항상 사용자 폴더에 하므로
 * 기본 제공 화면을 처음 저장하면 그 복사본이 "내 버전"이 되어 기본 제공본을 덮어쓴다(원본은 건드리지 않는다).
 */
final class EditorBackend {
    private EditorBackend() {}

    static EditorController create(String id, Path userDir, TextMetrics metrics, UiScreen screen, int width, int height)
            throws IOException, UiParseException {
        String text = UiSources.load(userDir, id).orElseThrow(() -> new NoSuchFileException(id + ".xml"));
        EditorHost host = new EditorHost() {
            @Override public UiDocument load(String xml) throws UiParseException {
                try {
                    UiDocument d = UiDocumentLoader.loadText(userDir, xml, metrics);
                    d.setOnClose(screen::onClose);
                    UiApi.fireOpened(id, d);   // 모드의 바인딩/이벤트 설정도 편집 화면에 똑같이 적용한다
                    return d;
                } catch (IOException e) {
                    throw new UiParseException("읽기 실패: " + e.getMessage(), -1);
                }
            }

            @Override public void save(String xml) throws IOException {
                AtomicFiles.write(userDir.resolve(id + ".xml"), xml);
            }

            @Override public String title() { return id + ".xml"; }

            @Override public void requestExit() { screen.exitEditor(); }

            /** 채팅창과 같은 시스템 클립보드(Ctrl+C/X/V). */
            @Override public java.util.List<String> suggestions(String key) {
                return key.equals("onClick") ? dev.magyul.betteruiux.core.doc.UiActions.registeredIds() : java.util.List.of();
            }

            @Override public String clipboard() { return Minecraft.getInstance().keyboardHandler.getClipboard(); }

            @Override public void setClipboard(String text) { Minecraft.getInstance().keyboardHandler.setClipboard(text); }

            /** 게임 화면에서는 선택한 요소로 화면이 부드럽게 미끄러진다. */
            @Override public boolean panAnimation() { return true; }

            /**
             * 26.x 는 SDL 로 입력을 받는다. SDL 은 "텍스트 입력 시작" 을 호출해야 글자(와 IME 조합)를 보내 주므로,
             * 기본 텍스트 위젯이 포커스를 받을 때 하는 것과 똑같이 TextInputManager 를 켜고 끈다.
             */
            @Override public void textInput(boolean active, int x, int y, int w, int h) {
                var manager = Minecraft.getInstance().textInputManager();
                if (Boolean.getBoolean("betteruiux.debug")) {
                    org.slf4j.LoggerFactory.getLogger("better_uiux").info("[better-uiux] text input {} ({},{} {}x{})", active ? "ON" : "OFF", x, y, w, h);
                }
                if (active) {
                    manager.setTextInputArea(x, y, x + w, y + h);
                    manager.startTextInput(screen);
                } else {
                    manager.stopTextInput(screen);
                }
            }
        };
        return new EditorController(host, metrics, text, width, height);
    }
}

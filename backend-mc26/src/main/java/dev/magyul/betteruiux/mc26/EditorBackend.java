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

    /** 이미지/영상 파일 후보: config/better-uiux/images 안의 png/jpg/bmp/gif/mp4 를 "file:이름" 으로. */
    private static java.util.List<String> imageFiles(Path userDir) {
        Path dir = userDir.getParent().resolve("images");
        if (!java.nio.file.Files.isDirectory(dir)) return java.util.List.of();
        try (var s = java.nio.file.Files.list(dir)) {
            return s.filter(java.nio.file.Files::isRegularFile).map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase().matches(".*[.](png|jpe?g|bmp|gif|mp4|m4v|mov)"))
                    .sorted().map(n -> "file:" + n).toList();
        } catch (IOException e) {
            return java.util.List.of();
        }
    }

    static EditorController create(String id, Path userDir, TextMetrics metrics, UiScreen screen, int width, int height)
            throws IOException, UiParseException {
        boolean blank = id.isEmpty();   // 빈 에디터(/uiux editor): 열린 화면 없이 파일을 고르는 목록만 보인다
        String text = blank ? "<screen/>" : UiSources.load(userDir, id).or(() -> java.util.Optional.ofNullable(NewDocs.get(userDir, id)))
                .orElseThrow(() -> new NoSuchFileException(id + ".xml"));
        EditorHost host = new EditorHost() {
            @Override public boolean isBlank() { return blank; }

            @Override public UiDocument load(String xml) throws UiParseException {
                try {
                    UiDocument d = UiDocumentLoader.loadText(userDir, xml, metrics);
                    d.setOnClose(screen::onClose);
                    if (!blank) UiApi.fireOpened(id, d);   // 모드의 바인딩/이벤트 설정도 편집 화면에 똑같이 적용한다
                    return d;
                } catch (IOException e) {
                    throw new UiParseException("읽기 실패: " + e.getMessage(), -1);
                }
            }

            @Override public void save(String xml) throws IOException {
                if (blank) throw new IOException("저장할 화면이 없습니다");
                AtomicFiles.write(userDir.resolve(id + ".xml"), xml);
                NewDocs.remove(userDir, id);                      // 새 화면은 처음 저장할 때 비로소 파일이 된다
            }

            @Override public String title() {
                if (blank) return "(열린 화면 없음)";
                boolean unsavedNew = NewDocs.has(userDir, id) && !java.nio.file.Files.exists(userDir.resolve(id + ".xml"));
                return id + ".xml" + (unsavedNew ? " (새 파일 - 저장 전)" : "");
            }

            @Override public boolean canPublish() { return !blank && ClientSync.active(); }

            @Override public void publish() { ClientSync.publishUi(id); }

            /** 패널/툴바 접힘 상태는 config.toml 의 [editor] 에 저장해 다음에도 유지한다. */
            @Override public java.util.Map<String, String> loadPrefs() {
                var cfg = dev.magyul.betteruiux.core.doc.UiConfig.load(userDir.getParent().resolve("config.toml"));
                java.util.Map<String, String> m = new java.util.HashMap<>();
                for (String k : new String[] {"leftCollapsed", "rightCollapsed", "toolbarHidden"}) {
                    String v = cfg.get("editor." + k);
                    if (v != null) m.put(k, v);
                }
                return m;
            }

            @Override public void savePrefs(java.util.Map<String, String> prefs) {
                java.util.Map<String, String> changes = new java.util.LinkedHashMap<>();
                prefs.forEach((k, v) -> changes.put("editor." + k, v));
                try {
                    dev.magyul.betteruiux.core.doc.UiConfig.update(userDir.getParent().resolve("config.toml"), changes);
                } catch (IOException ignored) {
                    // 저장하지 못해도 이번 실행에는 영향이 없다
                }
            }

            @Override public void requestExit() { screen.exitEditor(); }

            /** 사용자 폴더의 .xml 화면들(지금 것 제외). */
            @Override public java.util.List<String> screens() {
                try (var s = java.nio.file.Files.list(userDir)) {
                    return s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".xml"))
                            .map(n -> n.substring(0, n.length() - 4)).filter(n -> !n.equals(id) && UiSources.validId(n)).sorted().toList();
                } catch (IOException e) {
                    return java.util.List.of();
                }
            }

            @Override public void switchTo(String other) { screen.switchEditor(other); }

            /** &lt;link&gt; 스타일시트: 사용자 폴더 파일이 있으면 그것, 없으면 기본 제공 파일. 쓰기는 항상 사용자 폴더에(기본 제공본은 건드리지 않는다). */
            @Override public String readFile(String name) {
                try {
                    return UiSources.loadFile(userDir, name).orElse(null);
                } catch (IOException e) {
                    return null;
                }
            }

            @Override public void writeFile(String name, String text) throws IOException {
                if (!UiSources.validName(name)) throw new IOException("올바르지 않은 파일 이름: " + name);
                AtomicFiles.write(userDir.resolve(name), text);
            }

            @Override public String createScreen(int kind) throws IOException {
                String body = kind == 1 ? """
                        <screen use="blur,dim">
                          <panel anchor="center" width="200" height="100" padding="8" background="#181A24E0">
                            <column gap="6" width="fill" height="fill">
                              <label text="제목" />
                              <button text="닫기" onClick="close" />
                            </column>
                          </panel>
                        </screen>
                        """ : """
                        <screen>
                        </screen>
                        """;
                for (int n = 1; n < 1000; n++) {
                    String nid = "new_" + n;
                    if (java.nio.file.Files.exists(userDir.resolve(nid + ".xml"))) continue;
                    NewDocs.put(userDir, nid, body);              // 파일은 만들지 않는다: 저장하기 전에 닫으면 아무것도 남지 않는다
                    java.nio.file.Files.deleteIfExists(userDir.getParent().resolve("autosave").resolve(nid + ".xml"));   // 지난번 새 화면의 임시 저장본이 복구 대화상자로 뜨지 않게
                    return nid;
                }
                throw new IOException("new_1..new_999 가 모두 사용 중입니다");
            }

            /** 채팅창과 같은 시스템 클립보드(Ctrl+C/X/V). */
            @Override public java.util.List<String> suggestions(String key) {
                return switch (key) {
                    case "onClick", "onChange", "onSubmit" -> {
                        java.util.List<String> all = new java.util.ArrayList<>(dev.magyul.betteruiux.core.doc.UiActions.registeredIds());
                        String text = diskText();
                        try {
                            if (text == null) text = UiSources.load(userDir, id).orElse("");
                        } catch (IOException ignored) {
                            text = "";
                        }
                        all.addAll(ScriptBridge.functionActions(text, userDir));   // script:함수 도 후보에 넣는다
                        yield all;
                    }
                    case "src" -> imageFiles(userDir);
                    case "item" -> net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream()
                            .map(Object::toString).sorted().toList();
                    default -> java.util.List.of();
                };
            }

            /** 저장 파일이 에디터 밖에서 바뀌었는지 확인하는 데 쓴다(없으면 null). */
            @Override public String diskText() {
                try {
                    Path f = userDir.resolve(id + ".xml");
                    return java.nio.file.Files.exists(f) ? java.nio.file.Files.readString(f) : null;
                } catch (IOException e) {
                    return null;
                }
            }

            /** 저장 전 편집 내용은 config/better-uiux/autosave/<id>.xml 에 임시로 남긴다(저장하거나 정상 종료하면 지운다). */
            private Path autosaveFile() { return userDir.getParent().resolve("autosave").resolve(id + ".xml"); }

            @Override public void autosave(String text) {
                if (blank) return;
                try {
                    AtomicFiles.write(autosaveFile(), text);
                } catch (IOException ignored) {
                    // 임시 저장은 실패해도 편집에는 영향이 없다
                }
            }

            @Override public String readAutosave() {
                if (blank) return null;
                try {
                    Path f = autosaveFile();
                    return java.nio.file.Files.exists(f) ? java.nio.file.Files.readString(f) : null;
                } catch (IOException e) {
                    return null;
                }
            }

            @Override public void clearAutosave() {
                if (blank) return;
                try {
                    java.nio.file.Files.deleteIfExists(autosaveFile());
                } catch (IOException ignored) {
                    // 남아 있어도 다음에 복구 여부를 한 번 더 물을 뿐이다
                }
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

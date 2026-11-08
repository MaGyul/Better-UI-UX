package dev.magyul.betteruiux.core.doc;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.core.style.Stylesheet;
import dev.magyul.betteruiux.core.xml.UiNode;
import dev.magyul.betteruiux.core.xml.UiParseException;
import dev.magyul.betteruiux.core.xml.UiParser;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * id → {@link UiDocument}. XML 을 읽고, 문서 안의 {@code <style>} 블록과 {@code <link rel="style" href="x.uicss"/>} 를
 * 문서 순서대로 모아 스타일시트로 만든다. 핫리로드는 이 로더를 다시 호출하는 것으로 구현된다.
 */
public final class UiDocumentLoader {
    private UiDocumentLoader() {}

    /**
     * @throws NoSuchFileException XML 파일이 없을 때
     * @throws UiParseException    XML 문법 오류(줄 번호 포함)
     */
    public static UiDocument load(Path userDir, String id, TextMetrics text) throws IOException, UiParseException {
        Optional<String> xml = UiSources.load(userDir, id);
        if (xml.isEmpty()) throw new NoSuchFileException(id + ".xml");
        return loadText(userDir, xml.get(), text);
    }

    /**
     * XML 텍스트로 문서를 만든다(id 로 파일을 찾는 {@link #load} 와 같은 과정). 인게임 에디터가 편집 중인 텍스트를
     * 파일 저장 없이 곧바로 문서로 만들 때 쓴다. {@code userDir} 는 include/스타일시트를 찾는 곳.
     */
    public static UiDocument loadText(Path userDir, String xml, TextMetrics text) throws IOException, UiParseException {
        UiNode root = UiParser.parse(xml);

        List<Stylesheet> sheets = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        UiExpander.expand(root, userDir, warnings);   // include / template / use / slot
        collect(root, userDir, sheets, warnings);
        for (Stylesheet s : sheets) warnings.addAll(s.warnings());
        return new UiDocument(root, text, sheets, warnings);
    }

    private static void collect(UiNode n, Path userDir, List<Stylesheet> sheets, List<String> warnings) throws IOException {
        switch (n.tag()) {
            case "style" -> sheets.add(Stylesheet.parse(n.text()));
            case "link" -> {
                String rel = n.attr("rel");
                String href = n.attr("href");
                if ((rel == null || rel.equals("style") || rel.equals("stylesheet")) && href != null) {
                    Optional<String> css = UiSources.loadFile(userDir, href);
                    if (css.isPresent()) sheets.add(Stylesheet.parse(css.get()));
                    else warnings.add("스타일시트 '" + href + "' 를 찾을 수 없습니다");
                }
            }
            default -> { }
        }
        for (UiNode c : n.children()) collect(c, userDir, sheets, warnings);
    }
}

package dev.magyul.betteruiux.core.edit;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;

/** 에디터가 바깥(게임/파일 시스템)에 부탁하는 일. 마인크래프트 백엔드가 구현한다. */
public interface EditorHost {
    /**
     * XML 텍스트로 문서를 만든다(include/스타일 로드, 모드의 onDocumentOpened 훅 적용 포함).
     * 레이아웃은 에디터가 호출하므로 하지 않아도 된다.
     *
     * @throws UiParseException XML 문법 오류
     */
    UiDocument load(String text) throws UiParseException;

    /** 텍스트를 저장한다(원자적 쓰기). */
    void save(String text) throws IOException;

    /** 도구 모음에 표시할 이름(예: demo.xml). */
    String title();
}

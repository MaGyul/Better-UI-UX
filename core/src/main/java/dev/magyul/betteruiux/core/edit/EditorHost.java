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

    /**
     * 속성 칸 편집을 시작/끝낼 때 호출된다. 게임이 SDL 기반이면 글자 입력(IME 포함)을 받기 위해 "텍스트 입력 시작"을
     * 따로 켜야 하므로, 백엔드가 여기서 켜고 끈다(켜지 않으면 키 이벤트만 오고 글자는 들어오지 않는다).
     *
     * @param active (x,y,w,h) 입력 칸의 화면 위치(조합 중인 글자 창을 띄울 위치). active 가 false 면 무시.
     */
    default void textInput(boolean active, int x, int y, int w, int h) { }

    /**
     * 속성 칸의 자동 완성 후보. {@code key} 가 {@code onClick} 이면 등록된 이벤트(액션) id 목록을 돌려준다.
     * 후보가 없으면 빈 목록.
     */
    default java.util.List<String> suggestions(String key) { return java.util.List.of(); }

    /** 시스템 클립보드 읽기(Ctrl+V). 지원하지 않으면 빈 문자열. */
    default String clipboard() { return ""; }

    /** 시스템 클립보드 쓰기(Ctrl+C / Ctrl+X). */
    default void setClipboard(String text) { }

    /** true 면 선택한 요소를 가운데로 옮길 때 화면이 부드럽게 미끄러진다(게임 화면용). 테스트는 즉시 이동. */
    default boolean panAnimation() { return false; }
}

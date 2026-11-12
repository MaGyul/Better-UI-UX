package dev.magyul.betteruiux.api;

/** 레이아웃이 글자 크기를 알기 위한 SPI. 백엔드는 게임 폰트로, 테스트는 고정폭으로 구현한다. */
public interface TextMetrics {
    int width(String text);

    /** 글꼴({@code minecraft:uniform} 같은 id, null 이면 기본)을 바꿨을 때의 폭. 글꼴을 구분하지 않는 구현은 기본 폭을 돌려준다. */
    default int width(String text, String font) { return width(text); }

    int lineHeight();
}

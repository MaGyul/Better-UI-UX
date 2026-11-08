package dev.magyul.betteruiux.api;

/** 레이아웃이 글자 크기를 알기 위한 SPI. 백엔드는 게임 폰트로, 테스트는 고정폭으로 구현한다. */
public interface TextMetrics {
    int width(String text);

    int lineHeight();
}

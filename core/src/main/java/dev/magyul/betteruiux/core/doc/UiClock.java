package dev.magyul.betteruiux.core.doc;

/** 애니메이션 시간원. 기본은 시스템 시계이고, 테스트는 수동 시계로 바꿔 시간을 제어한다. */
@FunctionalInterface
public interface UiClock {
    UiClock SYSTEM = () -> System.nanoTime() / 1_000_000L;

    long nowMillis();
}

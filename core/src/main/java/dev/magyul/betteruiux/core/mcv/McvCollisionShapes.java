package dev.magyul.betteruiux.core.mcv;

/**
 * 블록의 충돌 모양을 알려 주는 공급자. 코어는 마인크래프트를 모르므로 백엔드가 바닐라의 충돌 모양을 채워 준다.
 * 공급자가 없으면 공기가 아닌 블록은 모두 꽉 찬 큐브로 취급한다.
 */
@FunctionalInterface
public interface McvCollisionShapes {
    /** 블록 안 좌표(0~1)의 상자들 {minX,minY,minZ,maxX,maxY,maxZ}. 통과할 수 있는 블록(물·꽃 등)은 빈 배열. */
    double[][] boxes(McvBlock block);
}

package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.mcv.McvBlock;
import dev.magyul.betteruiux.core.mcv.McvCollisionShapes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * 물리를 켠 엔티티가 쓰는 블록 충돌 모양: 바닐라의 {@code BlockState.getCollisionShape} 을 그대로 쓴다(계단·반블록·울타리 높이 등).
 * 월드에 의존하지 않도록 빈 블록 getter 로 물어보고, 그래서 위치/이웃에 따라 달라지는 모양은 기본 상태 모양이 된다.
 * 블록 상태마다 한 번만 계산해 캐시한다(렌더 스레드에서만 쓴다).
 */
final class McViewerCollision implements McvCollisionShapes {
    static final McViewerCollision INSTANCE = new McViewerCollision();
    private static final double[][] FULL = {{0, 0, 0, 1, 1, 1}}, NONE = {};

    private final Map<McvBlock, double[][]> cache = new HashMap<>();

    @Override
    public double[][] boxes(McvBlock block) {
        double[][] cached = cache.get(block);
        if (cached == null) {
            cached = compute(block);
            cache.put(block, cached);
        }
        return cached;
    }

    private static double[][] compute(McvBlock block) {
        BlockState state = McViewerPip.toState(block);
        if (state == null) return NONE;                                       // 모르는 블록은 그리지 않으므로 막지도 않는다
        try {
            List<AABB> boxes = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).toAabbs();
            double[][] out = new double[boxes.size()][];
            for (int i = 0; i < out.length; i++) {
                AABB b = boxes.get(i);
                out[i] = new double[] {b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ};
            }
            return out;
        } catch (RuntimeException e) {
            return FULL;                                                      // 월드가 필요한 모양은 꽉 찬 큐브로 본다
        }
    }
}

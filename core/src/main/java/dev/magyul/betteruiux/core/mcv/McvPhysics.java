package dev.magyul.betteruiux.core.mcv;

import java.util.ArrayList;
import java.util.List;

/**
 * 엔티티의 중력과 블록 충돌(바닐라 {@code Entity.move} 와 같은 순서: 세로 → 가로, 계단 오르기 0.6).
 * 월드 블록에서 충돌 상자를 모아 축마다 잘라 낸다. 유체 부력·밀기·넉백은 없다.
 */
final class McvPhysics {
    static final double GRAVITY = 0.08, DRAG = 0.98, STEP_HEIGHT = 0.6, JUMP = 0.42, FLOOR = -64;
    private static final double EPS = 1e-7;

    private McvPhysics() {}

    /** 충돌 상자(월드 좌표). */
    private record Box(double x0, double y0, double z0, double x1, double y1, double z1) {}

    /** 이동 결과(새 위치와 어느 축이 막혔는지). */
    static final class Move {
        double x, y, z;
        boolean hitX, hitY, hitZ;
    }

    /** 위치 (x,y,z)(발 밑 중앙)의 상자가 (dx,dy,dz) 만큼 가려 할 때 막히는 만큼 잘라 낸 결과. */
    static Move move(McvWorld w, double x, double y, double z, double width, double height, double dx, double dy, double dz) {
        double half = width / 2;
        double minX = Math.min(x - half, x - half + dx), maxX = Math.max(x + half, x + half + dx);
        double minY = Math.min(y, y + dy), maxY = Math.max(y + height, y + height + dy);
        double minZ = Math.min(z - half, z - half + dz), maxZ = Math.max(z + half, z + half + dz);
        List<Box> boxes = collect(w, minX, minY, minZ, maxX, maxY, maxZ);
        Move m = new Move();
        double ody = dy, odx = dx, odz = dz;
        // 세로 먼저
        for (Box b : boxes) dy = clipY(b, x - half, y, z - half, x + half, y + height, z + half, dy);
        y += dy;
        for (Box b : boxes) dx = clipX(b, x - half, y, z - half, x + half, y + height, z + half, dx);
        x += dx;
        for (Box b : boxes) dz = clipZ(b, x - half, y, z - half, x + half, y + height, z + half, dz);
        z += dz;
        m.x = x;
        m.y = y;
        m.z = z;
        m.hitX = Math.abs(dx - odx) > EPS;
        m.hitY = Math.abs(dy - ody) > EPS;
        m.hitZ = Math.abs(dz - odz) > EPS;
        return m;
    }

    private static double clipY(Box b, double ax0, double ay0, double az0, double ax1, double ay1, double az1, double dy) {
        if (ax1 <= b.x0 + EPS || ax0 >= b.x1 - EPS || az1 <= b.z0 + EPS || az0 >= b.z1 - EPS) return dy;
        if (dy > 0 && ay1 <= b.y0 + EPS) dy = Math.min(dy, b.y0 - ay1);
        else if (dy < 0 && ay0 >= b.y1 - EPS) dy = Math.max(dy, b.y1 - ay0);
        return dy;
    }

    private static double clipX(Box b, double ax0, double ay0, double az0, double ax1, double ay1, double az1, double dx) {
        if (ay1 <= b.y0 + EPS || ay0 >= b.y1 - EPS || az1 <= b.z0 + EPS || az0 >= b.z1 - EPS) return dx;
        if (dx > 0 && ax1 <= b.x0 + EPS) dx = Math.min(dx, b.x0 - ax1);
        else if (dx < 0 && ax0 >= b.x1 - EPS) dx = Math.max(dx, b.x1 - ax0);
        return dx;
    }

    private static double clipZ(Box b, double ax0, double ay0, double az0, double ax1, double ay1, double az1, double dz) {
        if (ax1 <= b.x0 + EPS || ax0 >= b.x1 - EPS || ay1 <= b.y0 + EPS || ay0 >= b.y1 - EPS) return dz;
        if (dz > 0 && az1 <= b.z0 + EPS) dz = Math.min(dz, b.z0 - az1);
        else if (dz < 0 && az0 >= b.z1 - EPS) dz = Math.max(dz, b.z1 - az0);
        return dz;
    }

    private static List<Box> collect(McvWorld w, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        List<Box> out = new ArrayList<>();
        McvCollisionShapes shapes = w.collisionShapes();
        for (int bx = (int) Math.floor(minX); bx <= (int) Math.floor(maxX); bx++) {
            for (int by = (int) Math.floor(minY); by <= (int) Math.floor(maxY); by++) {
                for (int bz = (int) Math.floor(minZ); bz <= (int) Math.floor(maxZ); bz++) {
                    McvBlock block = w.getBlock(bx, by, bz);
                    if (block.isAir()) continue;
                    double[][] local = shapes == null ? FULL : shapes.boxes(block);
                    for (double[] s : local) out.add(new Box(bx + s[0], by + s[1], bz + s[2], bx + s[3], by + s[4], bz + s[5]));
                }
            }
        }
        return out;
    }

    private static final double[][] FULL = {{0, 0, 0, 1, 1, 1}};

    /**
     * 물리 한 틱: 원하는 가로 이동 (dx,dz) 와 세로 속도를 적용해 엔티티 위치를 갱신한다.
     * 땅에 서 있고 가로로 막히면 {@link #STEP_HEIGHT} 까지 계단을 올라간다.
     */
    static void step(McvEntity e, McvWorld w, double dx, double dz) {
        double width = e.bbWidth() > 0 ? e.bbWidth() : 0.6, height = e.bbHeight() > 0 ? e.bbHeight() : 1.8;
        boolean wasGround = e.onGround();
        double vy = e.fallSpeed();
        Move m = move(w, e.x(), e.y(), e.z(), width, height, dx, vy, dz);
        if (wasGround && vy <= 0 && (m.hitX || m.hitZ) && (dx != 0 || dz != 0)) {
            // 계단 오르기: 올라가서 → 가로로 가고 → 내려온다. 가로로 더 멀리 갔을 때만 채택한다.
            Move up = move(w, e.x(), e.y(), e.z(), width, height, 0, STEP_HEIGHT, 0);
            Move over = move(w, up.x, up.y, up.z, width, height, dx, 0, dz);
            Move down = move(w, over.x, over.y, over.z, width, height, 0, -(up.y - e.y()) + Math.min(0, vy), 0);
            double flatStep = Math.hypot(down.x - e.x(), down.z - e.z()), flatPlain = Math.hypot(m.x - e.x(), m.z - e.z());
            if (flatStep > flatPlain + EPS) {
                m = down;
                m.hitY = true;                                              // 계단 위에 서 있다
            }
        }
        e.applyPhysics(m.x, m.y, m.z, m.hitY && vy <= 0, m.hitY);
    }
}

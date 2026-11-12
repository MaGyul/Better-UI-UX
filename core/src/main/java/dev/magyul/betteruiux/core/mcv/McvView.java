package dev.magyul.betteruiux.core.mcv;

/**
 * 뷰어 카메라의 수학: 월드 좌표 ↔ 화면(GUI) 좌표, 그리고 화면의 한 점에서 월드로 쏘는 광선(블록 맞히기).
 * 렌더러({@code McViewerPip})의 변환과 <b>같은 식</b>이어야 한다:
 * 화면 = 위젯 중심 + scale × (−x″, −y″),  (x″,y″,z″) = Rx(−pitch) · Ry(yaw) · (p − 중심).
 * 정사영이라 깊이(z″)는 화면 위치에 영향이 없고, 보는 사람은 z″ 가 작은 쪽에 있다.
 * 카메라가 원근({@link McvCamera.Projection#PERSPECTIVE})이면 같은 회전으로 기저(오른쪽·위·앞)를 만들고 눈 위치에서 시야각(fov)으로 투영한다.
 */
public final class McvView {
    private McvView() { }

    /** 카메라가 도는 중심(보기 영역이 있으면 그 중심, 없으면 월드 중심). */
    public static double[] center(McvWorld w) {
        int[] va = w.viewArea();
        if (va != null) return new double[] {(va[0] + va[3] + 1) / 2.0, (va[1] + va[4] + 1) / 2.0, (va[2] + va[5] + 1) / 2.0};
        return new double[] {w.sizeX() / 2.0, w.sizeY() / 2.0, w.sizeZ() / 2.0};
    }

    /** 월드 좌표 → {화면 x, 화면 y, 깊이(작을수록 가깝다)}. 아직 그려진 적이 없으면 null. */
    public static double[] project(McvWorld w, McvViewer v, double x, double y, double z) {
        McvViewer.Viewport vp = v.viewport();
        if (vp == null) return null;
        if (v.camera().projection() == McvCamera.Projection.PERSPECTIVE) return projectPerspective(w, v, vp, x, y, z);
        double[] c = center(w);
        double[] q = rotate(v.camera(), x - c[0], y - c[1], z - c[2]);
        return new double[] {vp.x() + vp.w() / 2 - vp.scale() * q[0], vp.y() + vp.h() / 2 - vp.scale() * q[1], q[2]};
    }

    /** 월드 좌표 → 회전된(카메라) 좌표 (x″, y″, z″). */
    private static double[] rotate(McvCamera cam, double x, double y, double z) {
        double yaw = Math.toRadians(cam.yaw()), pitch = Math.toRadians(-cam.pitch());
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double x1 = x * cy + z * sy, z1 = -x * sy + z * cy;            // Ry(yaw)
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double y2 = y * cp - z1 * sp, z2 = y * sp + z1 * cp;           // Rx(−pitch)
        return new double[] {x1, y2, z2};
    }

    // ---- 원근(perspective) ----

    /** 카메라 기저(월드 좌표): {오른쪽, 위, 앞(보는 방향)}. 정사영과 같은 회전을 쓴다: 화면 오른쪽 = −X″, 위 = Y″, 앞 = Z″. */
    private static double[][] basis(McvCamera cam) { return basis(cam.yaw(), cam.pitch()); }

    /** {@link #basis(McvCamera)} 를 yaw/pitch(도)로 직접 구한다(렌더러가 카메라 객체 없이 쓴다). */
    public static double[][] basis(float yaw, float pitch) {
        double[] x = unrotate(yaw, pitch, 1, 0, 0), y = unrotate(yaw, pitch, 0, 1, 0), z = unrotate(yaw, pitch, 0, 0, 1);
        return new double[][] {{-x[0], -x[1], -x[2]}, y, z};
    }

    /** 위젯 영역의 가로/세로 비(영역을 모르면 1). */
    private static double aspect(McvViewer.Viewport vp) {
        return vp == null || vp.h() <= 0 ? 1.0 : vp.w() / vp.h();
    }

    /** 월드(또는 보기 영역)를 감싸는 구의 반지름. */
    public static double radius(McvWorld w) {
        int[] va = w.viewArea();
        double dx = va == null ? w.sizeX() : va[3] - va[0] + 1, dy = va == null ? w.sizeY() : va[4] - va[1] + 1, dz = va == null ? w.sizeZ() : va[5] - va[2] + 1;
        return 0.5 * Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 원근 카메라의 눈 위치. 스크립트가 {@code camera.setPosition} 으로 정했으면 그 자리(yaw/pitch 는 보는 방향),
     * 아니면 중심을 둘러싼 구가 화면에 꼭 들어오도록 중심에서 보는 방향의 반대쪽으로 물러난 자리다(zoom 이 클수록 가깝다).
     */
    public static double[] eye(McvWorld w, McvViewer v) {
        McvCamera cam = v.camera();
        if (cam.positioned()) return new double[] {cam.x(), cam.y(), cam.z()};
        double[] f = basis(cam)[2], c = center(w);
        double half = Math.toRadians(cam.fov()) / 2;
        double tanHalf = Math.tan(half) * Math.min(1.0, aspect(v.viewport()));        // 좁은 쪽 시야에 구가 들어오게
        double dist = radius(w) / Math.sin(Math.atan(tanHalf)) / cam.zoom();
        return new double[] {c[0] - f[0] * dist, c[1] - f[1] * dist, c[2] - f[2] * dist};
    }

    private static double[] projectPerspective(McvWorld w, McvViewer v, McvViewer.Viewport vp, double x, double y, double z) {
        McvCamera cam = v.camera();
        double[][] b = basis(cam);
        double[] e = eye(w, v);
        double rx = x - e[0], ry = y - e[1], rz = z - e[2];
        double depth = rx * b[2][0] + ry * b[2][1] + rz * b[2][2];
        if (depth <= 1e-6) return null;                                 // 카메라 뒤(또는 바로 위): 화면에 없다
        double t = Math.tan(Math.toRadians(cam.fov()) / 2), a = aspect(vp);
        double ndcX = (rx * b[0][0] + ry * b[0][1] + rz * b[0][2]) / (depth * t * a), ndcY = (rx * b[1][0] + ry * b[1][1] + rz * b[1][2]) / (depth * t);
        return new double[] {vp.x() + vp.w() / 2 + ndcX * vp.w() / 2, vp.y() + vp.h() / 2 - ndcY * vp.h() / 2, depth};
    }

    private static double[] rayPerspective(McvWorld w, McvViewer v, McvViewer.Viewport vp, double sx, double sy) {
        McvCamera cam = v.camera();
        if (vp.w() <= 0 || vp.h() <= 0) return null;
        double[][] b = basis(cam);
        double[] e = eye(w, v);
        double t = Math.tan(Math.toRadians(cam.fov()) / 2), a = aspect(vp);
        double ndcX = (sx - (vp.x() + vp.w() / 2)) / (vp.w() / 2), ndcY = -(sy - (vp.y() + vp.h() / 2)) / (vp.h() / 2);
        double[] d = new double[3];
        for (int i = 0; i < 3; i++) d[i] = b[2][i] + b[0][i] * ndcX * t * a + b[1][i] * ndcY * t;
        double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        return new double[] {e[0], e[1], e[2], d[0] / len, d[1] / len, d[2] / len};
    }

    /** 카메라 좌표 → 월드 방향(회전의 역). */
    private static double[] unrotate(McvCamera cam, double x, double y, double z) { return unrotate(cam.yaw(), cam.pitch(), x, y, z); }

    private static double[] unrotate(float yawDeg, float pitchDeg, double x, double y, double z) {
        double yaw = Math.toRadians(-yawDeg), pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double y1 = y * cp - z * sp, z1 = y * sp + z * cp;             // Rx(pitch)
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        return new double[] {x * cy + z1 * sy, y1, -x * sy + z1 * cy};  // Ry(−yaw)
    }

    /** 화면의 한 점을 지나는 광선: {원점 x,y,z, 방향 x,y,z}. 방향은 길이 1, 원점은 월드 밖 먼 곳(보는 사람 쪽)이다. */
    public static double[] ray(McvWorld w, McvViewer v, double sx, double sy) {
        McvViewer.Viewport vp = v.viewport();
        if (vp == null) return null;
        if (v.camera().projection() == McvCamera.Projection.PERSPECTIVE) return rayPerspective(w, v, vp, sx, sy);
        if (vp.scale() <= 0) return null;
        double[] c = center(w);
        double qx = -(sx - (vp.x() + vp.w() / 2)) / vp.scale();
        double qy = -(sy - (vp.y() + vp.h() / 2)) / vp.scale();
        double[] p0 = unrotate(v.camera(), qx, qy, 0);
        double[] d = unrotate(v.camera(), 0, 0, 1);                    // 보는 방향(+z″)
        double far = 2.0 * Math.sqrt((double) w.sizeX() * w.sizeX() + (double) w.sizeY() * w.sizeY() + (double) w.sizeZ() * w.sizeZ()) + 2;
        return new double[] {c[0] + p0[0] - d[0] * far, c[1] + p0[1] - d[1] * far, c[2] + p0[2] - d[2] * far, d[0], d[1], d[2]};
    }

    /** 광선이 처음 맞힌 블록. {@code face} 는 광선이 들어온 면(up/down/north/south/east/west, 마인크래프트 방향). */
    public record Hit(int x, int y, int z, String face, McvBlock block, McvEntity entity, double t) {
        /** 블록을 맞혔다. */
        public boolean isBlock() { return block != null; }

        /** 엔티티를 맞혔다({@code x,y,z} 는 맞은 지점이 있는 칸). */
        public boolean isEntity() { return entity != null; }
    }

    /**
     * 화면의 (sx, sy)에 보이는 것(블록 또는 엔티티) 중 가장 가까운 것을 찾는다. 엔티티는 충돌 상자로 맞힌다
     * (백엔드가 크기를 알려 준, 곧 실제로 그려진 엔티티만).
     */
    public static Hit raycast(McvWorld w, McvViewer v, double sx, double sy) {
        double[] r = ray(w, v, sx, sy);
        if (r == null) return null;
        Hit block = raycastBlock(w, r);
        Hit entity = raycastEntity(w, r);
        if (entity != null && (block == null || entity.t() < block.t())) return entity;
        return block;
    }

    private static Hit raycastEntity(McvWorld w, double[] r) {
        Hit best = null;
        for (McvEntity e : w.entities()) {
            double bw = e.bbWidth(), bh = e.bbHeight();
            if (bw <= 0 || bh <= 0 || !w.inView(e.x(), e.y(), e.z())) continue;
            double[] min = {e.x() - bw / 2, e.y(), e.z() - bw / 2}, max = {e.x() + bw / 2, e.y() + bh, e.z() + bw / 2};
            double tmin = 0, tmax = Double.POSITIVE_INFINITY;
            int axis = -1;
            boolean miss = false;
            for (int a = 0; a < 3 && !miss; a++) {
                double o = r[a], d = r[3 + a];
                if (Math.abs(d) < 1e-12) {
                    if (o < min[a] || o > max[a]) miss = true;
                    continue;
                }
                double t1 = (min[a] - o) / d, t2 = (max[a] - o) / d;
                if (t1 > t2) {
                    double t = t1;
                    t1 = t2;
                    t2 = t;
                }
                if (t1 > tmin) {
                    tmin = t1;
                    axis = a;
                }
                if (t2 < tmax) tmax = t2;
                if (tmin > tmax) miss = true;
            }
            if (miss || (best != null && tmin >= best.t())) continue;
            int dir = axis < 0 ? 0 : r[3 + axis] > 0 ? 1 : -1;
            best = new Hit((int) Math.floor(r[0] + r[3] * tmin), (int) Math.floor(r[1] + r[4] * tmin), (int) Math.floor(r[2] + r[5] * tmin),
                    face(axis, dir), null, e, tmin);
        }
        return best;
    }

    /**
     * 화면의 (sx, sy)에 보이는 블록을 찾는다. 보기 영역 밖 블록은 그려지지 않으므로 건너뛴다.
     * 모양과 상관없이 블록 칸 전체를 맞는 것으로 본다(계단·판자도 한 칸). 아무것도 없으면 null.
     */
    private static Hit raycastBlock(McvWorld w, double[] r) {
        double[] o = {r[0], r[1], r[2]}, d = {r[3], r[4], r[5]};
        double[] size = {w.sizeX(), w.sizeY(), w.sizeZ()};
        double tmin = 0, tmax = Double.POSITIVE_INFINITY;
        int enterAxis = -1;
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-12) {
                if (o[a] < 0 || o[a] > size[a]) return null;
                continue;
            }
            double t1 = (0 - o[a]) / d[a], t2 = (size[a] - o[a]) / d[a];
            if (t1 > t2) {
                double t = t1;
                t1 = t2;
                t2 = t;
            }
            if (t1 > tmin) {
                tmin = t1;
                enterAxis = a;
            }
            if (t2 < tmax) tmax = t2;
        }
        if (tmin > tmax) return null;
        int[] cell = new int[3];
        int[] step = new int[3];
        double[] tNext = new double[3], tDelta = new double[3];
        for (int a = 0; a < 3; a++) {
            double p = o[a] + d[a] * (tmin + 1e-9);
            cell[a] = Math.max(0, Math.min((int) size[a] - 1, (int) Math.floor(p)));
            step[a] = d[a] > 0 ? 1 : d[a] < 0 ? -1 : 0;
            if (step[a] == 0) {
                tNext[a] = Double.POSITIVE_INFINITY;
                tDelta[a] = Double.POSITIVE_INFINITY;
            } else {
                tNext[a] = ((step[a] > 0 ? cell[a] + 1 : cell[a]) - o[a]) / d[a];
                tDelta[a] = Math.abs(1.0 / d[a]);
            }
        }
        int lastAxis = enterAxis, lastDir = enterAxis < 0 ? 0 : step[enterAxis];
        double tEnter = tmin;
        int limit = (int) (size[0] + size[1] + size[2]) + 3;
        for (int i = 0; i < limit; i++) {
            McvBlock b = w.getBlock(cell[0], cell[1], cell[2]);
            if (!b.isAir() && w.inView(cell[0] + 0.5, cell[1] + 0.5, cell[2] + 0.5)) {
                return new Hit(cell[0], cell[1], cell[2], face(lastAxis, lastDir), b, null, tEnter);
            }
            int a = tNext[0] <= tNext[1] && tNext[0] <= tNext[2] ? 0 : tNext[1] <= tNext[2] ? 1 : 2;
            if (tNext[a] > tmax || step[a] == 0) return null;
            tEnter = tNext[a];
            cell[a] += step[a];
            if (cell[a] < 0 || cell[a] >= size[a]) return null;
            tNext[a] += tDelta[a];
            lastAxis = a;
            lastDir = step[a];
        }
        return null;
    }

    /** 광선이 +방향으로 가다 들어온 면은 그 축의 음의 쪽 면이다. */
    private static String face(int axis, int dir) {
        if (axis < 0) return "up";
        return switch (axis) {
            case 0 -> dir > 0 ? "west" : "east";
            case 1 -> dir > 0 ? "down" : "up";
            default -> dir > 0 ? "north" : "south";
        };
    }
}

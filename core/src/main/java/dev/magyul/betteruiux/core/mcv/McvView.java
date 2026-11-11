package dev.magyul.betteruiux.core.mcv;

/**
 * 뷰어 카메라의 수학: 월드 좌표 ↔ 화면(GUI) 좌표, 그리고 화면의 한 점에서 월드로 쏘는 광선(블록 맞히기).
 * 렌더러({@code McViewerPip})의 변환과 <b>같은 식</b>이어야 한다:
 * 화면 = 위젯 중심 + scale × (−x″, −y″),  (x″,y″,z″) = Rx(−pitch) · Ry(yaw) · (p − 중심).
 * 정사영이라 깊이(z″)는 화면 위치에 영향이 없고, 보는 사람은 z″ 가 작은 쪽에 있다.
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
    public static double[] project(McvWorld w, double x, double y, double z) {
        McvWorld.Viewport vp = w.viewport();
        if (vp == null) return null;
        double[] c = center(w);
        double[] q = rotate(w.camera(), x - c[0], y - c[1], z - c[2]);
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

    /** 카메라 좌표 → 월드 방향(회전의 역). */
    private static double[] unrotate(McvCamera cam, double x, double y, double z) {
        double yaw = Math.toRadians(-cam.yaw()), pitch = Math.toRadians(cam.pitch());
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double y1 = y * cp - z * sp, z1 = y * sp + z * cp;             // Rx(pitch)
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        return new double[] {x * cy + z1 * sy, y1, -x * sy + z1 * cy};  // Ry(−yaw)
    }

    /** 화면의 한 점을 지나는 광선: {원점 x,y,z, 방향 x,y,z}. 방향은 길이 1, 원점은 월드 밖 먼 곳(보는 사람 쪽)이다. */
    public static double[] ray(McvWorld w, double sx, double sy) {
        McvWorld.Viewport vp = w.viewport();
        if (vp == null || vp.scale() <= 0) return null;
        double[] c = center(w);
        double qx = -(sx - (vp.x() + vp.w() / 2)) / vp.scale();
        double qy = -(sy - (vp.y() + vp.h() / 2)) / vp.scale();
        double[] p0 = unrotate(w.camera(), qx, qy, 0);
        double[] d = unrotate(w.camera(), 0, 0, 1);                    // 보는 방향(+z″)
        double far = 2.0 * Math.sqrt((double) w.sizeX() * w.sizeX() + (double) w.sizeY() * w.sizeY() + (double) w.sizeZ() * w.sizeZ()) + 2;
        return new double[] {c[0] + p0[0] - d[0] * far, c[1] + p0[1] - d[1] * far, c[2] + p0[2] - d[2] * far, d[0], d[1], d[2]};
    }

    /** 광선이 처음 맞힌 블록. {@code face} 는 광선이 들어온 면(up/down/north/south/east/west, 마인크래프트 방향). */
    public record Hit(int x, int y, int z, String face, McvBlock block) { }

    /**
     * 화면의 (sx, sy)에 보이는 블록을 찾는다. 보기 영역 밖 블록은 그려지지 않으므로 건너뛴다.
     * 모양과 상관없이 블록 칸 전체를 맞는 것으로 본다(계단·판자도 한 칸). 아무것도 없으면 null.
     */
    public static Hit raycast(McvWorld w, double sx, double sy) {
        double[] r = ray(w, sx, sy);
        if (r == null) return null;
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
        int limit = (int) (size[0] + size[1] + size[2]) + 3;
        for (int i = 0; i < limit; i++) {
            McvBlock b = w.getBlock(cell[0], cell[1], cell[2]);
            if (!b.isAir() && w.inView(cell[0] + 0.5, cell[1] + 0.5, cell[2] + 0.5)) {
                return new Hit(cell[0], cell[1], cell[2], face(lastAxis, lastDir), b);
            }
            int a = tNext[0] <= tNext[1] && tNext[0] <= tNext[2] ? 0 : tNext[1] <= tNext[2] ? 1 : 2;
            if (tNext[a] > tmax || step[a] == 0) return null;
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

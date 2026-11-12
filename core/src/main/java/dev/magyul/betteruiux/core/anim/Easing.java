package dev.magyul.betteruiux.core.anim;

/** 이징 함수: 진행도 0..1 → 보간 비율. {@code linear ease ease-in ease-out ease-in-out cubic-bezier(a,b,c,d) steps(n[, jump]) step-start step-end spring}. */
@FunctionalInterface
public interface Easing {
    double apply(double t);

    Easing LINEAR = t -> t;

    /** 감쇠 진동으로 목표를 살짝 넘었다가 안착한다. 끝(t=1)에서 정확히 1 이 되도록 보정한다. */
    Easing SPRING = t -> t >= 1 ? 1 : t <= 0 ? 0 : 1 - Math.exp(-6 * t) * Math.cos(12 * t);

    /** 알 수 없는 이름은 linear. */
    static Easing parse(String s) {
        if (s == null) return LINEAR;
        s = s.trim().toLowerCase();
        return switch (s) {
            case "linear" -> LINEAR;
            case "ease" -> bezier(0.25, 0.1, 0.25, 1.0);
            case "ease-in" -> bezier(0.42, 0, 1.0, 1.0);
            case "ease-out" -> bezier(0, 0, 0.58, 1.0);
            case "ease-in-out" -> bezier(0.42, 0, 0.58, 1.0);
            case "spring" -> SPRING;
            case "step-start" -> steps(1, "jump-start");
            case "step-end" -> steps(1, "jump-end");
            default -> s.startsWith("cubic-bezier(") && s.endsWith(")") ? parseBezier(s)
                    : s.startsWith("steps(") && s.endsWith(")") ? parseSteps(s) : LINEAR;
        };
    }

    /** 이징 이름/함수인지(애니메이션 단축 속성 파싱용). */
    static boolean isEasingToken(String s) {
        s = s.toLowerCase();
        return s.equals("linear") || s.equals("ease") || s.equals("ease-in") || s.equals("ease-out")
                || s.equals("ease-in-out") || s.equals("spring") || s.startsWith("cubic-bezier(")
                || s.startsWith("steps(") || s.equals("step-start") || s.equals("step-end");
    }

    private static Easing parseSteps(String s) {
        String[] p = s.substring("steps(".length(), s.length() - 1).split(",");
        try {
            int n = Integer.parseInt(p[0].trim());
            String jump = p.length > 1 ? p[1].trim() : "jump-end";
            if (n < 1 || p.length > 2) return LINEAR;
            return steps(n, switch (jump) {
                case "start", "jump-start" -> "jump-start";
                case "end", "jump-end" -> "jump-end";
                case "jump-none" -> n >= 2 ? "jump-none" : "jump-end";
                case "jump-both" -> "jump-both";
                default -> "jump-end";
            });
        } catch (NumberFormatException e) {
            return LINEAR;
        }
    }

    /** CSS steps(): 값이 계단식으로 바뀐다. 끝(t=1)에서는 항상 1. */
    static Easing steps(int n, String jump) {
        return t -> {
            if (t >= 1) return 1;
            if (t < 0) t = 0;
            double k = Math.floor(n * t);
            double v = switch (jump) {
                case "jump-start" -> (k + 1) / n;
                case "jump-none" -> k / (n - 1);
                case "jump-both" -> (k + 1) / (n + 1);
                default -> k / n;
            };
            return Math.min(1, v);
        };
    }

    private static Easing parseBezier(String s) {
        String[] p = s.substring("cubic-bezier(".length(), s.length() - 1).split(",");
        if (p.length != 4) return LINEAR;
        try {
            return bezier(Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()),
                    Double.parseDouble(p[2].trim()), Double.parseDouble(p[3].trim()));
        } catch (NumberFormatException e) {
            return LINEAR;
        }
    }

    /** CSS cubic-bezier(x1,y1,x2,y2): x 에 대한 t 를 이분법으로 풀고 y 를 돌려준다. */
    static Easing bezier(double x1, double y1, double x2, double y2) {
        return x -> {
            if (x <= 0) return 0;
            if (x >= 1) return 1;
            double lo = 0, hi = 1, t = x;
            for (int i = 0; i < 32; i++) {
                t = (lo + hi) / 2;
                double cx = 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t;
                if (cx < x) lo = t;
                else hi = t;
            }
            return 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t;
        };
    }
}

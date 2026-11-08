package dev.magyul.betteruiux.core.anim;

/**
 * {@code animation: 이름 1s ease-in-out 200ms infinite alternate forwards} 단축 속성.
 * 시간 값이 두 개면 첫째는 지속시간, 둘째는 지연. 반복은 숫자 또는 {@code infinite}.
 */
public record AnimationSpec(String name, double durationMs, double delayMs, Easing easing,
                            double iterations, Direction direction, Fill fill) {
    public enum Direction { NORMAL, REVERSE, ALTERNATE, ALTERNATE_REVERSE }

    /** 지연 중/종료 후에도 첫/마지막 프레임 값을 유지할지. */
    public enum Fill { NONE, FORWARDS, BACKWARDS, BOTH }

    public static AnimationSpec parse(String value) {
        if (value == null || value.isBlank() || value.trim().equalsIgnoreCase("none")) return null;
        String name = null;
        Easing easing = Easing.LINEAR;
        Double duration = null, delay = null;
        double iterations = 1;
        Direction dir = Direction.NORMAL;
        Fill fill = Fill.NONE;
        for (String tok : Timing.tokens(value)) {
            String t = tok.toLowerCase();
            if (Timing.isTime(t)) {
                if (duration == null) duration = Timing.millis(t);
                else delay = Timing.millis(t);
            } else if (Easing.isEasingToken(t)) {
                easing = Easing.parse(t);
            } else if (t.equals("infinite")) {
                iterations = Double.POSITIVE_INFINITY;
            } else if (Timing.isNumber(t)) {
                iterations = Math.max(0, Double.parseDouble(t));
            } else if (t.equals("normal")) {
                dir = Direction.NORMAL;
            } else if (t.equals("reverse")) {
                dir = Direction.REVERSE;
            } else if (t.equals("alternate")) {
                dir = Direction.ALTERNATE;
            } else if (t.equals("alternate-reverse")) {
                dir = Direction.ALTERNATE_REVERSE;
            } else if (t.equals("forwards")) {
                fill = Fill.FORWARDS;
            } else if (t.equals("backwards")) {
                fill = Fill.BACKWARDS;
            } else if (t.equals("both")) {
                fill = Fill.BOTH;
            } else if (name == null) {
                name = tok;
            }
        }
        if (name == null || duration == null || duration <= 0) return null;
        return new AnimationSpec(name, duration, delay == null ? 0 : delay, easing, iterations, dir, fill);
    }

    /**
     * 시작 후 경과 시간 → 키프레임 진행도(0..1). 아직 시작 전이거나 끝난 뒤 값을 유지하지 않으면 -1.
     */
    public double progressAt(double elapsedMs) {
        double t = elapsedMs - delayMs;
        if (t < 0) return (fill == Fill.BACKWARDS || fill == Fill.BOTH) ? directed(0, 0) : -1;
        double total = durationMs * iterations;
        if (t >= total) {
            if (fill != Fill.FORWARDS && fill != Fill.BOTH) return -1;
            // 끝난 뒤 유지하는 값 = 마지막 반복의 끝 지점(반복 횟수가 소수면 그 비율 지점)
            int lastIter = (int) Math.max(0, Math.ceil(iterations) - 1);
            double local = Math.min(1.0, iterations - lastIter);
            return directed(lastIter, local);
        }
        int iter = (int) (t / durationMs);
        double local = (t - iter * durationMs) / durationMs;
        return directed(iter, local);
    }

    /** 반복 번호와 반복 내 진행도에 방향을 적용한다. */
    private double directed(int iter, double local) {
        boolean reversed = switch (direction) {
            case NORMAL -> false;
            case REVERSE -> true;
            case ALTERNATE -> iter % 2 == 1;
            case ALTERNATE_REVERSE -> iter % 2 == 0;
        };
        return reversed ? 1 - local : local;
    }

    public boolean isFinishedAt(double elapsedMs) {
        return Double.isFinite(iterations) && elapsedMs - delayMs >= durationMs * iterations;
    }
}

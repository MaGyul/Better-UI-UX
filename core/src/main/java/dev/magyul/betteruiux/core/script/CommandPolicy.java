package dev.magyul.betteruiux.core.script;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * 스크립트가 실행하려는 마인크래프트 명령어를 걸러내는 정책. 기본은 <b>아무 명령도 허용하지 않는다</b> —
 * 모드팩/서버가 허용 목록(첫 단어, 예: {@code say}, {@code give})을 명시적으로 켜야 한다.
 * 한 번의 호출당 개수, 초당 개수, 줄 길이에 상한이 있고 줄바꿈 같은 제어 문자는 거부한다.
 */
public final class CommandPolicy {
    public static final int MAX_LINE = 256;

    private final Set<String> allowed = new HashSet<>();
    private final int maxPerCall, maxPerSecond;
    private final LongSupplier nanoClock;
    private long windowStart;
    private int windowCount;

    public CommandPolicy(Set<String> allowedRoots, int maxPerCall, int maxPerSecond, LongSupplier nanoClock) {
        for (String r : allowedRoots) allowed.add(r.toLowerCase(Locale.ROOT).trim());
        this.maxPerCall = maxPerCall;
        this.maxPerSecond = maxPerSecond;
        this.nanoClock = nanoClock;
        this.windowStart = nanoClock.getAsLong();
    }

    /** 허용 명령이 하나도 없는 정책(기본). */
    public static CommandPolicy denyAll() { return new CommandPolicy(Set.of(), 0, 0, System::nanoTime); }

    public static CommandPolicy allowing(Set<String> roots) { return new CommandPolicy(roots, 8, 20, System::nanoTime); }

    public int maxPerCall() { return maxPerCall; }

    /** 명령 한 줄을 검사한다. @return 거부 사유(허용이면 null). 허용되면 초당 한도에 한 번 센다. */
    public synchronized String check(String line) {
        if (line == null || line.isBlank()) return "빈 명령";
        if (line.length() > MAX_LINE) return "명령이 너무 깁니다(" + line.length() + ")";
        for (int k = 0; k < line.length(); k++) {
            if (Character.isISOControl(line.charAt(k))) return "명령에 제어 문자가 있습니다";
        }
        String t = line.strip();
        if (t.startsWith("/")) t = t.substring(1);
        String root = t.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!allowed.contains(root)) return "허용되지 않은 명령: " + root;
        long now = nanoClock.getAsLong();
        if (now - windowStart >= 1_000_000_000L) {
            windowStart = now;
            windowCount = 0;
        }
        if (windowCount >= maxPerSecond) return "명령을 너무 자주 실행합니다";
        windowCount++;
        return null;
    }

    /** 실행할 명령 문자열({@code /} 제거). */
    public static String normalize(String line) {
        String t = line.strip();
        return t.startsWith("/") ? t.substring(1) : t;
    }
}

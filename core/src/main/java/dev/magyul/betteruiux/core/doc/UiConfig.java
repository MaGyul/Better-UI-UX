package dev.magyul.betteruiux.core.doc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 간단한 TOML 부분집합 설정({@code [section]} + {@code key = value}). 키는 {@code section.key} 로 조회한다.
 * 파일이 없으면 기본 파일을 만든다. JVM 속성({@code -Dbetteruiux.<section>.<key>})이 파일보다 우선한다 —
 * 모드팩 제작자가 플레이어 설정과 무관하게 값을 고정할 수 있다.
 */
public final class UiConfig {
    public static final String DEFAULT_TEXT = """
            # Better UI/UX 설정

            [hotReload]
            # UI 파일(xml/uicss) 변경을 감시해 열려 있는 화면에 즉시 반영한다. 모드팩 배포 시 false 권장.
            enabled = true

            [editor]
            # 인게임 에디터(개발 중). 모드팩 배포 시 false 권장.
            enabled = false
            """;

    private final Map<String, String> values;

    private UiConfig(Map<String, String> values) { this.values = values; }

    public static UiConfig load(Path file) {
        if (!Files.exists(file)) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_TEXT, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // 쓰기 실패 시 기본값으로 동작
            }
        }
        try {
            return new UiConfig(parse(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return new UiConfig(parse(DEFAULT_TEXT));
        }
    }

    static Map<String, String> parse(String text) {
        Map<String, String> m = new HashMap<>();
        String section = "";
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            int hash = value.indexOf(" #");
            if (hash >= 0) value = value.substring(0, hash).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            m.put(section.isEmpty() ? key : section + "." + key, value);
        }
        return m;
    }

    public String get(String key) {
        String sys = System.getProperty("betteruiux." + key.toLowerCase());
        return sys != null ? sys : values.get(key);
    }

    public boolean bool(String key, boolean def) {
        String v = get(key);
        return v == null ? def : v.equalsIgnoreCase("true");
    }
}

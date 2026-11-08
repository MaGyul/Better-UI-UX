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

            [scripting]
            # UI 문서의 <script> (JavaScript, 선택 모듈 script-graal 필요). 신뢰할 수 있는 UI 만 쓰세요.
            enabled = false
            # 스크립트가 실행할 수 있는 마인크래프트 명령어의 첫 단어(쉼표로 구분). 비워 두면 명령은 모두 거부됩니다.
            commands = ""
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

    /**
     * 설정 파일의 값을 바꾼다(없으면 만든다). 다른 줄과 주석은 그대로 두고, 키는 {@code section.key} 형식이다.
     * 에디터가 패널 접힘 같은 사용자 상태를 저장하는 데 쓴다.
     */
    public static void update(Path file, Map<String, String> changes) throws IOException {
        String text = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : DEFAULT_TEXT;
        Files.createDirectories(file.getParent());
        AtomicFiles.write(file, applyChanges(text, changes));
    }

    static String applyChanges(String text, Map<String, String> changes) {
        java.util.List<String> lines = new java.util.ArrayList<>(java.util.Arrays.asList(text.split("\\R", -1)));
        for (Map.Entry<String, String> e : changes.entrySet()) {
            int dot = e.getKey().indexOf('.');
            String section = dot < 0 ? "" : e.getKey().substring(0, dot), key = dot < 0 ? e.getKey() : e.getKey().substring(dot + 1);
            String newLine = key + " = " + e.getValue();
            String current = "";
            int sectionStart = section.isEmpty() ? 0 : -1, sectionEnd = lines.size(), found = -1;
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i).trim();
                if (l.startsWith("[") && l.endsWith("]")) {
                    current = l.substring(1, l.length() - 1).trim();
                    if (current.equals(section) && sectionStart < 0) sectionStart = i + 1;
                    else if (sectionStart >= 0 && i >= sectionStart && sectionEnd == lines.size() && !section.isEmpty()) sectionEnd = i;
                    continue;
                }
                if (current.equals(section) && sectionStart >= 0 && i >= sectionStart && l.startsWith(key)
                        && l.substring(key.length()).trim().startsWith("=")) {
                    found = i;
                }
            }
            if (found >= 0) {
                lines.set(found, newLine);
            } else if (sectionStart >= 0) {
                int at = Math.min(sectionEnd, lines.size());
                while (at > sectionStart && lines.get(at - 1).trim().isEmpty()) at--;
                lines.add(at, newLine);
            } else {
                while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) lines.remove(lines.size() - 1);
                lines.add("");
                lines.add("[" + section + "]");
                lines.add(newLine);
                lines.add("");
            }
        }
        return String.join("\n", lines);
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

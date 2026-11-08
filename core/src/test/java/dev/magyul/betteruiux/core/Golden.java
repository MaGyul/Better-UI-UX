package dev.magyul.betteruiux.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 골든(스냅샷) 테스트 도우미: 드로우 리스트를 글자로 풀어 {@code src/test/resources/golden/<이름>.txt} 와 비교한다.
 * 의도한 모양 변경이면 {@code ./gradlew :core:test -Pgolden.update=true} 로 파일을 다시 만든 뒤 diff 를 검토해 커밋한다.
 */
public final class Golden {
    private Golden() {}

    public static String render(DrawList list) {
        return list.commands().stream().map(DrawCommand::toString).collect(Collectors.joining("\n")) + "\n";
    }

    public static void assertMatches(String name, DrawList list) {
        Path file = Path.of("src/test/resources/golden/" + name + ".txt");
        String actual = render(list);
        try {
            if (Boolean.parseBoolean(System.getProperty("golden.update", "false"))) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, actual, StandardCharsets.UTF_8);
                return;
            }
            if (!Files.exists(file)) fail("골든 파일이 없습니다: " + file + " (만들려면 -Pgolden.update=true)");
            List<String> expected = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> got = List.of(actual.split("\n", -1)).subList(0, actual.split("\n", -1).length - 1);
            int n = Math.min(expected.size(), got.size());
            for (int i = 0; i < n; i++) {
                if (!expected.get(i).equals(got.get(i))) {
                    fail(name + " " + (i + 1) + "번째 명령이 다릅니다\n  기대: " + expected.get(i) + "\n  실제: " + got.get(i));
                }
            }
            assertEquals(expected.size(), got.size(), name + ": 명령 개수가 다릅니다");
        } catch (IOException e) {
            fail(e);
        }
    }
}

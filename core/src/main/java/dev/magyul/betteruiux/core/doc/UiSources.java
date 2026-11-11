package dev.magyul.betteruiux.core.doc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * UI 원본 파일 조회. 우선순위: ① 사용자 폴더({@code config/better-uiux/ui/<name>}) ② 클래스패스 기본 제공
 * ({@code assets/better_uiux/ui/<name>}). 사용자 파일이 있으면 기본 제공본을 덮어쓴다.
 */
public final class UiSources {
    private UiSources() {}

    /** 파일 이름은 영문/숫자/_/-/. 만 허용하고 경로 구분자·".." 는 거부한다(경로 탈출 방지). */
    public static boolean validName(String name) {
        return name != null && name.matches("[a-zA-Z0-9_\\-.]{1,64}") && !name.contains("..");
    }

    /** XML 문서 id(확장자 없음) 검증. */
    public static boolean validId(String id) { return validName(id); }

    /** {@code <id>.uiml} 을 읽는다. */
    public static Optional<String> load(Path userDir, String id) throws IOException {
        if (!validId(id)) return Optional.empty();
        return loadFile(userDir, id + ".uiml");
    }

    /** 확장자를 포함한 파일 이름({@code demo.uicss} 등)으로 읽는다. */
    public static Optional<String> loadFile(Path userDir, String fileName) throws IOException {
        if (!validName(fileName)) return Optional.empty();
        if (userDir != null) {
            Path p = userDir.resolve(fileName);
            if (Files.isRegularFile(p)) return Optional.of(Files.readString(p, StandardCharsets.UTF_8));
        }
        try (InputStream in = UiSources.class.getClassLoader()
                .getResourceAsStream("assets/better_uiux/ui/" + fileName)) {
            if (in == null) return Optional.empty();
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}

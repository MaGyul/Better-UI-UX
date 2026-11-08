package dev.magyul.betteruiux.core.doc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * UI XML 원본 조회. 우선순위: ① 사용자 폴더({@code config/better-uiux/ui/<id>.xml}) ② 클래스패스 기본 제공
 * ({@code assets/better_uiux/ui/<id>.xml}). 사용자 파일이 있으면 기본 제공본을 덮어쓴다.
 */
public final class UiSources {
    private UiSources() {}

    /** id 는 영문/숫자/_/-/. 만 허용(경로 탈출 방지). */
    public static boolean validId(String id) {
        return id != null && id.matches("[a-zA-Z0-9_\\-.]{1,64}") && !id.contains("..");
    }

    public static Optional<String> load(Path userDir, String id) throws IOException {
        if (!validId(id)) return Optional.empty();
        if (userDir != null) {
            Path p = userDir.resolve(id + ".xml");
            if (Files.isRegularFile(p)) return Optional.of(Files.readString(p, StandardCharsets.UTF_8));
        }
        try (InputStream in = UiSources.class.getClassLoader()
                .getResourceAsStream("assets/better_uiux/ui/" + id + ".xml")) {
            if (in == null) return Optional.empty();
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}

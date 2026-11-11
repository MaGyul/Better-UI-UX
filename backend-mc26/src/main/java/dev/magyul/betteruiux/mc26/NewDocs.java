package dev.magyul.betteruiux.mc26;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 에디터에서 새로 만든 화면의 "아직 파일이 아닌" 원문. File &gt; 새 화면은 파일을 바로 만들지 않고 여기에 틀을 두며,
 * 처음 저장할 때 비로소 파일이 생긴다(변경 없이 닫거나 다른 화면으로 가면 아무 파일도 남지 않는다).
 */
final class NewDocs {
    private static final Map<String, String> PENDING = new ConcurrentHashMap<>();

    private NewDocs() {}

    private static String key(Path userDir, String id) { return userDir.toAbsolutePath().normalize() + "|" + id; }

    static void put(Path userDir, String id, String text) { PENDING.put(key(userDir, id), text); }

    /** 저장 전인 새 화면의 원문(없으면 null). */
    static String get(Path userDir, String id) { return PENDING.get(key(userDir, id)); }

    static boolean has(Path userDir, String id) { return PENDING.containsKey(key(userDir, id)); }

    static void remove(Path userDir, String id) { PENDING.remove(key(userDir, id)); }
}

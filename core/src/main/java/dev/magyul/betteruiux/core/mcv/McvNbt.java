package dev.magyul.betteruiux.core.mcv;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * 마인크래프트 NBT(구조물 파일 등)를 읽는 작은 파서와, 값을 SNBT 글자로 쓰는 도구. 마인크래프트에 의존하지 않는다.
 * 값은 Java 객체로 읽힌다: compound → {@code Map<String,Object>}, list → {@code List<Object>},
 * 숫자는 Byte/Short/Integer/Long/Float/Double, 글자는 String, 배열은 byte[]/int[]/long[].
 * 악의적인 파일을 막으려고 압축 해제 크기, 깊이, 요소 수에 상한이 있다.
 */
public final class McvNbt {
    public static final int MAX_COMPRESSED = 2 * 1024 * 1024, MAX_DECOMPRESSED = 16 * 1024 * 1024, MAX_DEPTH = 64, MAX_ELEMENTS = 4_000_000;

    private McvNbt() { }

    /** gzip 으로 압축된(또는 압축 안 된) NBT 파일 바이트를 읽어 루트 compound 를 돌려준다. */
    public static Map<String, Object> read(byte[] file) {
        if (file == null || file.length == 0) throw new IllegalArgumentException("NBT 파일이 비어 있습니다");
        if (file.length > MAX_COMPRESSED) throw new IllegalArgumentException("NBT 파일이 너무 큽니다(" + MAX_COMPRESSED + " 바이트 초과)");
        byte[] raw = file;
        if (file.length > 2 && (file[0] & 0xFF) == 0x1F && (file[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(file))) {
                raw = in.readNBytes(MAX_DECOMPRESSED + 1);
            } catch (IOException e) {
                throw new IllegalArgumentException("압축을 풀 수 없습니다: " + e.getMessage());
            }
            if (raw.length > MAX_DECOMPRESSED) throw new IllegalArgumentException("압축을 풀면 너무 큽니다(" + MAX_DECOMPRESSED + " 바이트 초과)");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
            int type = in.readUnsignedByte();
            if (type != 10) throw new IllegalArgumentException("NBT 루트는 compound 여야 합니다");
            in.readUTF();                                              // 루트 이름(보통 빈 글자)
            int[] budget = {MAX_ELEMENTS};
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) readValue(in, 10, 0, budget);
            return root;
        } catch (IOException e) {
            throw new IllegalArgumentException("NBT 가 올바르지 않습니다: " + e.getMessage());
        }
    }

    private static Object readValue(DataInputStream in, int type, int depth, int[] budget) throws IOException {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("NBT 가 너무 깊습니다");
        if (--budget[0] < 0) throw new IllegalArgumentException("NBT 요소가 너무 많습니다");
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                int n = length(in, budget, 1);
                byte[] a = new byte[n];
                in.readFully(a);
                return a;
            }
            case 8: return in.readUTF();
            case 9: {
                int elem = in.readUnsignedByte();
                int n = length(in, budget, 1);
                if (n > 0 && (elem < 1 || elem > 12)) throw new IllegalArgumentException("알 수 없는 NBT 목록 종류: " + elem);
                List<Object> list = new ArrayList<>(Math.min(n, 1024));
                for (int i = 0; i < n; i++) list.add(readValue(in, elem, depth + 1, budget));
                return list;
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int t = in.readUnsignedByte();
                    if (t == 0) return map;
                    String key = in.readUTF();
                    map.put(key, readValue(in, t, depth + 1, budget));
                }
            }
            case 11: {
                int n = length(in, budget, 4);
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = in.readInt();
                return a;
            }
            case 12: {
                int n = length(in, budget, 8);
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = in.readLong();
                return a;
            }
            default: throw new IllegalArgumentException("알 수 없는 NBT 종류: " + type);
        }
    }

    private static int length(DataInputStream in, int[] budget, int weight) throws IOException {
        int n = in.readInt();
        if (n < 0) throw new IllegalArgumentException("NBT 길이가 올바르지 않습니다");
        budget[0] -= n / Math.max(1, 8 / weight);
        if (budget[0] < 0) throw new IllegalArgumentException("NBT 요소가 너무 많습니다");
        return n;
    }

    // ---- SNBT 쓰기(블록 엔티티 NBT 를 글자로 보관한다) ----

    private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_.+-]+");

    /** 값을 SNBT(마인크래프트 명령어 형식 글자)로 쓴다. */
    public static String toSnbt(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                String k = String.valueOf(e.getKey());
                if (BARE_KEY.matcher(k).matches()) sb.append(k);
                else quote(sb, k);
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                write(sb, l.get(i));
            }
            sb.append(']');
        } else if (v instanceof String s) {
            quote(sb, s);
        } else if (v instanceof Byte b) {
            sb.append(b).append('b');
        } else if (v instanceof Short s) {
            sb.append(s).append('s');
        } else if (v instanceof Integer i) {
            sb.append(i);
        } else if (v instanceof Long l) {
            sb.append(l).append('L');
        } else if (v instanceof Float f) {
            sb.append(f).append('f');
        } else if (v instanceof Double d) {
            sb.append(d).append('d');
        } else if (v instanceof byte[] a) {
            sb.append("[B;");
            for (int i = 0; i < a.length; i++) sb.append(i > 0 ? "," : "").append(a[i]).append('b');
            sb.append(']');
        } else if (v instanceof int[] a) {
            sb.append("[I;");
            for (int i = 0; i < a.length; i++) sb.append(i > 0 ? "," : "").append(a[i]);
            sb.append(']');
        } else if (v instanceof long[] a) {
            sb.append("[L;");
            for (int i = 0; i < a.length; i++) sb.append(i > 0 ? "," : "").append(a[i]).append('L');
            sb.append(']');
        } else {
            throw new IllegalArgumentException("쓸 수 없는 NBT 값: " + v);
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\');
            sb.append(c);
        }
        sb.append('"');
    }
}

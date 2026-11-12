package dev.magyul.betteruiux.core.sync;

import dev.magyul.betteruiux.core.sync.SyncMessage.Action;
import dev.magyul.betteruiux.core.sync.SyncMessage.BundleChunk;
import dev.magyul.betteruiux.core.sync.SyncMessage.BundleManifest;
import dev.magyul.betteruiux.core.sync.SyncMessage.BundleNeed;
import dev.magyul.betteruiux.core.sync.SyncMessage.ClientHello;
import dev.magyul.betteruiux.core.sync.SyncMessage.CloseUi;
import dev.magyul.betteruiux.core.sync.SyncMessage.FileEntry;
import dev.magyul.betteruiux.core.sync.SyncMessage.OpenUi;
import dev.magyul.betteruiux.core.sync.SyncMessage.PublishBegin;
import dev.magyul.betteruiux.core.sync.SyncMessage.PublishResult;
import dev.magyul.betteruiux.core.sync.SyncMessage.UiClosed;
import dev.magyul.betteruiux.core.sync.SyncMessage.ServerHello;
import dev.magyul.betteruiux.core.sync.SyncMessage.StateClear;
import dev.magyul.betteruiux.core.sync.SyncMessage.StateSet;
import dev.magyul.betteruiux.core.sync.SyncMessage.StateSnapshot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link SyncMessage} 의 이진 인코더/디코더. 첫 바이트가 종류, 이어서 필드. 모든 길이는 상한({@link SyncLimits})을 먼저 검사하고
 * 읽기 때문에, 악의적인 메시지(거대한 길이 값 등)가 메모리를 먹지 못한다. 끝에 남는 바이트가 있으면 거부한다.
 */
public final class SyncCodec {
    private SyncCodec() {}

    private static final int CLIENT_HELLO = 1, SERVER_HELLO = 2, MANIFEST = 3, NEED = 4, CHUNK = 5, OPEN = 6, CLOSE = 7,
            STATE_SET = 8, STATE_CLEAR = 9, SNAPSHOT = 10, ACTION = 11, UI_CLOSED = 12, PUBLISH_BEGIN = 13, PUBLISH_RESULT = 14;
    private static final int T_STRING = 0, T_LONG = 1, T_DOUBLE = 2, T_BOOL = 3;

    // ---- 인코딩 ----

    public static byte[] encode(SyncMessage m) throws SyncException {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            switch (m) {
                case ClientHello h -> {
                    out.writeByte(CLIENT_HELLO);
                    out.writeInt(h.protocol());
                    str(out, h.bundleHash(), 128);
                }
                case ServerHello h -> {
                    out.writeByte(SERVER_HELLO);
                    out.writeInt(h.protocol());
                    str(out, h.bundleHash(), 128);
                    out.writeByte(h.policy());
                }
                case BundleManifest b -> {
                    out.writeByte(MANIFEST);
                    writeManifest(out, b);
                }
                case BundleNeed n -> {
                    out.writeByte(NEED);
                    if (n.paths().size() > SyncLimits.MAX_NEED) throw new SyncException("요청이 너무 많습니다");
                    out.writeShort(n.paths().size());
                    for (String p : n.paths()) str(out, p, SyncLimits.MAX_PATH);
                }
                case BundleChunk c -> {
                    out.writeByte(CHUNK);
                    str(out, c.path(), SyncLimits.MAX_PATH);
                    out.writeInt(c.index());
                    out.writeInt(c.total());
                    if (c.data().length > SyncLimits.CHUNK) throw new SyncException("조각이 너무 큽니다");
                    out.writeInt(c.data().length);
                    out.write(c.data());
                }
                case OpenUi o -> {
                    out.writeByte(OPEN);
                    str(out, o.id(), SyncLimits.MAX_ID);
                    map(out, o.params(), SyncLimits.MAX_PARAMS);
                }
                case CloseUi c -> {
                    out.writeByte(CLOSE);
                    str(out, c.id(), SyncLimits.MAX_ID);
                }
                case StateSet s -> {
                    out.writeByte(STATE_SET);
                    str(out, s.key(), SyncLimits.MAX_ID);
                    value(out, s.value());
                }
                case StateClear s -> {
                    out.writeByte(STATE_CLEAR);
                    str(out, s.key(), SyncLimits.MAX_ID);
                }
                case StateSnapshot s -> {
                    out.writeByte(SNAPSHOT);
                    map(out, s.values(), SyncLimits.MAX_STATE_KEYS);
                }
                case UiClosed c -> {
                    out.writeByte(UI_CLOSED);
                    str(out, c.id(), SyncLimits.MAX_ID);
                }
                case PublishBegin p -> {
                    out.writeByte(PUBLISH_BEGIN);
                    if (p.manifest().files().size() > SyncLimits.MAX_PUBLISH_FILES) throw new SyncException("발행 파일이 너무 많습니다");
                    writeManifest(out, p.manifest());
                }
                case PublishResult r -> {
                    out.writeByte(PUBLISH_RESULT);
                    out.writeByte(r.stage());
                    out.writeBoolean(r.ok());
                    str(out, r.message(), 512);
                }
                case Action a -> {
                    out.writeByte(ACTION);
                    str(out, a.uiId(), SyncLimits.MAX_ID);
                    str(out, a.actionId(), SyncLimits.MAX_ID);
                    map(out, a.args(), SyncLimits.MAX_ARGS);
                }
            }
            byte[] result = bytes.toByteArray();
            if (result.length > SyncLimits.MAX_MESSAGE) throw new SyncException("메시지가 너무 큽니다(" + result.length + ")");
            return result;
        } catch (IOException e) {
            throw new SyncException("인코딩 실패: " + e.getMessage());
        }
    }

    private static void writeManifest(DataOutputStream out, BundleManifest b) throws IOException, SyncException {
        str(out, b.hash(), 128);
        if (b.files().size() > SyncLimits.MAX_FILES) throw new SyncException("파일이 너무 많습니다");
        out.writeShort(b.files().size());
        for (FileEntry f : b.files()) {
            str(out, f.path(), SyncLimits.MAX_PATH);
            if (f.sha256().length != 32) throw new SyncException("해시 길이가 올바르지 않습니다");
            out.write(f.sha256());
            out.writeLong(f.size());
        }
    }

    private static BundleManifest readManifest(DataInputStream in, int maxFiles) throws IOException, SyncException {
        String hash = str(in, 128);
        int n = in.readUnsignedShort();
        if (n > maxFiles) throw new SyncException("파일이 너무 많습니다(" + n + ")");
        List<FileEntry> files = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String path = str(in, SyncLimits.MAX_PATH);
            byte[] sha = new byte[32];
            in.readFully(sha);
            files.add(new FileEntry(path, sha, in.readLong()));
        }
        return new BundleManifest(hash, files);
    }

    private static void str(DataOutputStream out, String s, int max) throws IOException, SyncException {
        if (s == null) throw new SyncException("문자열이 null 입니다");
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length > max) throw new SyncException("문자열이 너무 깁니다(" + b.length + " > " + max + ")");
        out.writeShort(b.length);
        out.write(b);
    }

    private static void value(DataOutputStream out, Object v) throws IOException, SyncException {
        if (v instanceof String s) {
            out.writeByte(T_STRING);
            str(out, s, SyncLimits.MAX_VALUE);
        } else if (v instanceof Boolean b) {
            out.writeByte(T_BOOL);
            out.writeBoolean(b);
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) throw new SyncException("숫자가 올바르지 않습니다");
            out.writeByte(T_DOUBLE);
            out.writeDouble(d);
        } else if (v instanceof Number n) {
            out.writeByte(T_LONG);
            out.writeLong(n.longValue());
        } else {
            throw new SyncException("지원하지 않는 값 형식: " + (v == null ? "null" : v.getClass().getSimpleName()));
        }
    }

    private static void map(DataOutputStream out, Map<String, Object> m, int max) throws IOException, SyncException {
        if (m.size() > max) throw new SyncException("항목이 너무 많습니다(" + m.size() + " > " + max + ")");
        out.writeShort(m.size());
        for (Map.Entry<String, Object> e : m.entrySet()) {
            str(out, e.getKey(), SyncLimits.MAX_ID);
            value(out, e.getValue());
        }
    }

    // ---- 디코딩 ----

    public static SyncMessage decode(byte[] data) throws SyncException {
        if (data == null || data.length == 0) throw new SyncException("빈 메시지");
        if (data.length > SyncLimits.MAX_MESSAGE) throw new SyncException("메시지가 너무 큽니다(" + data.length + ")");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        try {
            int type = in.readUnsignedByte();
            SyncMessage m = switch (type) {
                case CLIENT_HELLO -> new ClientHello(in.readInt(), str(in, 128));
                case SERVER_HELLO -> new ServerHello(in.readInt(), str(in, 128), in.readUnsignedByte());
                case MANIFEST -> readManifest(in, SyncLimits.MAX_FILES);
                case NEED -> {
                    int n = in.readUnsignedShort();
                    if (n > SyncLimits.MAX_NEED) throw new SyncException("요청이 너무 많습니다(" + n + ")");
                    List<String> paths = new ArrayList<>();
                    for (int i = 0; i < n; i++) paths.add(str(in, SyncLimits.MAX_PATH));
                    yield new BundleNeed(paths);
                }
                case CHUNK -> {
                    String path = str(in, SyncLimits.MAX_PATH);
                    int index = in.readInt(), total = in.readInt(), len = in.readInt();
                    if (len < 0 || len > SyncLimits.CHUNK || len > in.available()) throw new SyncException("조각 길이가 올바르지 않습니다(" + len + ")");
                    byte[] chunk = new byte[len];
                    in.readFully(chunk);
                    yield new BundleChunk(path, index, total, chunk);
                }
                case OPEN -> new OpenUi(str(in, SyncLimits.MAX_ID), map(in, SyncLimits.MAX_PARAMS));
                case CLOSE -> new CloseUi(str(in, SyncLimits.MAX_ID));
                case STATE_SET -> new StateSet(str(in, SyncLimits.MAX_ID), value(in));
                case STATE_CLEAR -> new StateClear(str(in, SyncLimits.MAX_ID));
                case SNAPSHOT -> new StateSnapshot(map(in, SyncLimits.MAX_STATE_KEYS));
                case ACTION -> new Action(str(in, SyncLimits.MAX_ID), str(in, SyncLimits.MAX_ID), map(in, SyncLimits.MAX_ARGS));
                case UI_CLOSED -> new UiClosed(str(in, SyncLimits.MAX_ID));
                case PUBLISH_BEGIN -> new PublishBegin(readManifest(in, SyncLimits.MAX_PUBLISH_FILES));
                case PUBLISH_RESULT -> new PublishResult(in.readUnsignedByte(), in.readBoolean(), str(in, 512));
                default -> throw new SyncException("알 수 없는 메시지 종류: " + type);
            };
            if (in.available() > 0) throw new SyncException("메시지 끝에 남는 바이트가 있습니다");
            return m;
        } catch (IOException e) {
            throw new SyncException("메시지가 잘렸거나 형식이 틀립니다");
        }
    }

    private static String str(DataInputStream in, int max) throws IOException, SyncException {
        int len = in.readUnsignedShort();
        if (len > max) throw new SyncException("문자열이 너무 깁니다(" + len + " > " + max + ")");
        if (len > in.available()) throw new SyncException("문자열이 잘렸습니다");
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static Object value(DataInputStream in) throws IOException, SyncException {
        int t = in.readUnsignedByte();
        return switch (t) {
            case T_STRING -> str(in, SyncLimits.MAX_VALUE);
            case T_LONG -> in.readLong();
            case T_DOUBLE -> {
                double d = in.readDouble();
                if (Double.isNaN(d) || Double.isInfinite(d)) throw new SyncException("숫자가 올바르지 않습니다");
                yield d;
            }
            case T_BOOL -> in.readBoolean();
            default -> throw new SyncException("알 수 없는 값 형식: " + t);
        };
    }

    private static Map<String, Object> map(DataInputStream in, int max) throws IOException, SyncException {
        int n = in.readUnsignedShort();
        if (n > max) throw new SyncException("항목이 너무 많습니다(" + n + " > " + max + ")");
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) out.put(str(in, SyncLimits.MAX_ID), value(in));
        return out;
    }
}

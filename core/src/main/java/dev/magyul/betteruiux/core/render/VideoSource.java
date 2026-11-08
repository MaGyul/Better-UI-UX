package dev.magyul.betteruiux.core.render;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.model.Picture;

/**
 * mp4(H.264) 영상 해독. 순수 Java({@code JCodec}) 라 네이티브가 필요 없고 마인크래프트와도 무관하다.
 * 프레임은 {@code 0xAARRGGBB} 배열로 돌려주며, 큰 영상은 정수 배율로 줄여서(가장 긴 변이 {@code maxSide} 이하) 업로드 비용을 낮춘다.
 *
 * <p>소리는 지원하지 않는다. 한 인스턴스는 한 스레드에서만 쓴다.
 */
public final class VideoSource implements AutoCloseable {
    /** 영상 정보. fps 는 (프레임 수 / 길이) 로 구한 평균값이다. */
    public record Info(int width, int height, double fps, double durationSec) {}

    /** 줄인 크기의 프레임. */
    public record Frame(int width, int height, int[] argb) {}

    public static final int MAX_BYTES = 128 * 1024 * 1024, MAX_SIDE = 4096;

    private final byte[] data;
    private final int maxSide;
    private FrameGrab grab;
    private Info info;
    private int step = 1;

    private VideoSource(byte[] data, int maxSide) {
        this.data = data;
        this.maxSide = Math.max(16, maxSide);
    }

    /** @param maxSide 해독 결과의 가장 긴 변 상한(원본이 더 크면 정수 배율로 줄인다) */
    public static VideoSource open(byte[] data, int maxSide) throws IOException {
        if (data == null || data.length == 0) throw new IOException("빈 파일");
        if (data.length > MAX_BYTES) throw new IOException("영상 파일이 너무 큽니다(" + data.length / (1024 * 1024) + "MB > " + MAX_BYTES / (1024 * 1024) + "MB)");
        VideoSource v = new VideoSource(data, maxSide);
        v.rewind();
        return v;
    }

    public Info info() { return info; }

    /** 처음으로 되돌린다(반복 재생). */
    public void rewind() throws IOException {
        try {
            grab = FrameGrab.createFrameGrab(ByteBufferSeekableByteChannel.readFromByteBuffer(ByteBuffer.wrap(data)));
            var meta = grab.getVideoTrack().getMeta();
            Picture first = grab.getNativeFrame();
            if (first == null) throw new IOException("프레임이 없는 영상");
            int w = first.getCroppedWidth(), h = first.getCroppedHeight();       // H.264 는 16 의 배수로 채워 저장하므로 잘라낸 크기를 쓴다
            if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE) throw new IOException("영상 크기를 지원하지 않습니다(" + w + "x" + h + ")");
            step = Math.max(1, (int) Math.ceil(Math.max(w, h) / (double) maxSide));
            double duration = meta.getTotalDuration();
            int frames = meta.getTotalFrames();
            double fps = duration > 0 && frames > 0 ? frames / duration : 30.0;
            info = new Info((w + step - 1) / step, (h + step - 1) / step, fps, duration);
            pending = first;                                       // 크기를 알아내려고 읽은 첫 프레임을 다음에 돌려준다
        } catch (IOException e) {
            throw e;
        } catch (Exception | LinkageError e) {
            throw new IOException("영상을 열 수 없습니다(H.264 mp4 만 지원): " + e.getMessage(), e);
        }
    }

    private Picture pending;

    /** 다음 프레임. 끝이면 null. */
    public Frame next() throws IOException {
        try {
            Picture p = pending != null ? pending : grab.getNativeFrame();
            pending = null;
            return p == null ? null : convert(p);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("영상 프레임을 해독하지 못했습니다: " + e.getMessage(), e);
        }
    }

    /** YUV420 → ARGB(+정수 배율 축소). 가로 1280 이상이면 BT.709, 아니면 BT.601 (제한 범위). */
    private Frame convert(Picture p) {
        int sw = p.getCroppedWidth(), sh = p.getCroppedHeight();
        var crop = p.getCrop();
        int cropX = crop == null ? 0 : crop.getX(), cropY = crop == null ? 0 : crop.getY();
        int w = (sw + step - 1) / step, h = (sh + step - 1) / step;
        byte[] yPlane = p.getPlaneData(0), uPlane = p.getPlaneData(1), vPlane = p.getPlaneData(2);
        int yStride = p.getPlaneWidth(0), cStride = p.getPlaneWidth(1);
        int chromaShiftX = p.getPlaneWidth(1) * 2 >= yStride ? 1 : 0, chromaShiftY = p.getPlaneHeight(1) * 2 >= p.getPlaneHeight(0) ? 1 : 0;
        boolean hd = sw >= 1280;
        double kr = hd ? 0.2126 : 0.299, kb = hd ? 0.0722 : 0.114, kg = 1 - kr - kb;
        int[] out = new int[w * h];
        for (int oy = 0; oy < h; oy++) {
            int sy = Math.min(sh - 1, oy * step) + cropY;
            for (int ox = 0; ox < w; ox++) {
                int sx = Math.min(sw - 1, ox * step) + cropX;
                // JCodec 은 샘플을 부호 있는 바이트(값 - 128)로 담는다
                int y = (yPlane[sy * yStride + sx] + 128) & 0xFF;
                int ci = (sy >> chromaShiftY) * cStride + (sx >> chromaShiftX);
                int u = ((uPlane[ci] + 128) & 0xFF) - 128, v = ((vPlane[ci] + 128) & 0xFF) - 128;
                double yy = 1.164 * (y - 16);
                double r = yy + 2 * (1 - kr) * 255.0 / 224.0 * v;
                double b = yy + 2 * (1 - kb) * 255.0 / 224.0 * u;
                double g = yy - (2 * (1 - kr) * kr / kg) * 255.0 / 224.0 * v - (2 * (1 - kb) * kb / kg) * 255.0 / 224.0 * u;
                out[oy * w + ox] = 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
            }
        }
        return new Frame(w, h, out);
    }

    private static int clamp(double v) { return v < 0 ? 0 : v > 255 ? 255 : (int) (v + 0.5); }

    @Override
    public void close() {
        grab = null;
        pending = null;
    }
}

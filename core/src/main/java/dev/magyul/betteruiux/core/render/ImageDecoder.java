package dev.magyul.betteruiux.core.render;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;

/**
 * 외부 이미지(png/jpg/bmp/gif) 해독. 마인크래프트와 무관한 순수 Java 라서 단위 테스트가 된다.
 * 결과는 프레임마다 {@code 0xAARRGGBB} 배열이다(정지 이미지는 프레임 하나). GIF 는 프레임 합성(처리 방식, 위치)과 지연 시간을 반영한다.
 *
 * <p>상한: 파일 8MB, 한 변 4096px, 프레임 256개, (가로×세로×프레임) 16M 픽셀(약 64MB). 넘으면 {@link IOException}.
 */
public final class ImageDecoder {
    public static final int MAX_BYTES = 8 * 1024 * 1024, MAX_SIDE = 4096, MAX_FRAMES = 256;
    public static final long MAX_PIXEL_FRAMES = 16_000_000L;

    private ImageDecoder() {}

    /** @param frames 프레임별 ARGB(길이 width*height), @param delaysMs 프레임별 표시 시간(ms, 정지 이미지는 0) */
    public record Decoded(int width, int height, List<int[]> frames, int[] delaysMs) {
        public boolean animated() { return frames.size() > 1; }

        public long totalDurationMs() {
            long t = 0;
            for (int d : delaysMs) t += d;
            return t;
        }

        /** 시작 후 {@code elapsedMs} 에 보여 줄 프레임 번호(정지 이미지는 0). */
        public int frameAt(long elapsedMs) {
            long total = totalDurationMs();
            if (!animated() || total <= 0) return 0;
            long t = Math.floorMod(elapsedMs, total);
            for (int i = 0; i < delaysMs.length; i++) {
                if (t < delaysMs[i]) return i;
                t -= delaysMs[i];
            }
            return frames.size() - 1;
        }
    }

    public static Decoded decode(byte[] data) throws IOException {
        if (data == null || data.length == 0) throw new IOException("빈 파일");
        if (data.length > MAX_BYTES) throw new IOException("파일이 너무 큽니다(" + data.length / 1024 + "KB > " + MAX_BYTES / 1024 + "KB)");
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("지원하지 않는 이미지 형식(png, jpg, bmp, gif 만 지원)");
            ImageReader reader = readers.next();
            reader.setInput(in, false, false);
            try {
                return "gif".equalsIgnoreCase(reader.getFormatName()) ? decodeGif(reader) : decodeStill(reader);
            } finally {
                reader.dispose();
            }
        }
    }

    private static void checkSize(int w, int h, int frames) throws IOException {
        if (w <= 0 || h <= 0) throw new IOException("이미지 크기가 올바르지 않습니다");
        if (w > MAX_SIDE || h > MAX_SIDE) throw new IOException("이미지가 너무 큽니다(" + w + "x" + h + " > " + MAX_SIDE + ")");
        if ((long) w * h * frames > MAX_PIXEL_FRAMES) throw new IOException("이미지(프레임 포함)가 너무 큽니다");
    }

    private static Decoded decodeStill(ImageReader reader) throws IOException {
        checkSize(reader.getWidth(0), reader.getHeight(0), 1);
        BufferedImage img = reader.read(0);
        return new Decoded(img.getWidth(), img.getHeight(), List.of(argb(img)), new int[] {0});
    }

    private static int[] argb(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = new int[w * h];
        img.getRGB(0, 0, w, h, px, 0, w);
        return px;
    }

    private static Decoded decodeGif(ImageReader reader) throws IOException {
        int count = reader.getNumImages(true);
        if (count <= 0) throw new IOException("프레임이 없는 GIF");
        if (count > MAX_FRAMES) throw new IOException("GIF 프레임이 너무 많습니다(" + count + " > " + MAX_FRAMES + ")");
        int w = 0, h = 0;
        try {
            IIOMetadata sm = reader.getStreamMetadata();
            if (sm != null) {
                Node root = sm.getAsTree("javax_imageio_gif_stream_1.0");
                Node d = child(root, "LogicalScreenDescriptor");
                if (d != null) {
                    w = intAttr(d, "logicalScreenWidth", 0);
                    h = intAttr(d, "logicalScreenHeight", 0);
                }
            }
        } catch (RuntimeException ignored) {
            // 메타데이터가 없으면 첫 프레임 크기를 쓴다
        }
        if (w <= 0 || h <= 0) {
            w = reader.getWidth(0);
            h = reader.getHeight(0);
        }
        checkSize(w, h, count);
        BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        List<int[]> frames = new ArrayList<>(count);
        int[] delays = new int[count];
        String prevDisposal = "none";
        int prevX = 0, prevY = 0, prevW = 0, prevH = 0;
        BufferedImage saved = null;
        for (int i = 0; i < count; i++) {
            BufferedImage frame = reader.read(i);
            IIOMetadata md = reader.getImageMetadata(i);
            int left = 0, top = 0, delay = 10;
            String disposal = "none";
            if (md != null) {
                Node root = md.getAsTree("javax_imageio_gif_image_1.0");
                Node desc = child(root, "ImageDescriptor");
                if (desc != null) {
                    left = intAttr(desc, "imageLeftPosition", 0);
                    top = intAttr(desc, "imageTopPosition", 0);
                }
                Node gce = child(root, "GraphicControlExtension");
                if (gce != null) {
                    delay = intAttr(gce, "delayTime", 10);
                    disposal = attr(gce, "disposalMethod", "none");
                }
            }
            // 앞 프레임의 처리 방식을 먼저 적용한다
            Graphics2D g = canvas.createGraphics();
            try {
                if ("restoreToBackgroundColor".equals(prevDisposal)) {
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(prevX, prevY, prevW, prevH);
                } else if ("restoreToPrevious".equals(prevDisposal) && saved != null) {
                    g.setComposite(AlphaComposite.Src);
                    g.drawImage(saved, 0, 0, null);
                }
                g.setComposite(AlphaComposite.SrcOver);
                if ("restoreToPrevious".equals(disposal)) saved = copy(canvas);
                g.drawImage(frame, left, top, null);
            } finally {
                g.dispose();
            }
            frames.add(argb(canvas));
            delays[i] = delay <= 0 ? 100 : Math.max(20, delay * 10);
            prevDisposal = disposal;
            prevX = left;
            prevY = top;
            prevW = frame.getWidth();
            prevH = frame.getHeight();
        }
        return new Decoded(w, h, frames, delays);
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = c.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return c;
    }

    private static Node child(Node parent, String name) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) if (name.equals(n.getNodeName())) return n;
        return null;
    }

    private static String attr(Node n, String name, String def) {
        Node a = n.getAttributes() == null ? null : n.getAttributes().getNamedItem(name);
        return a == null ? def : a.getNodeValue();
    }

    private static int intAttr(Node n, String name, int def) {
        try {
            return Integer.parseInt(attr(n, name, Integer.toString(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}

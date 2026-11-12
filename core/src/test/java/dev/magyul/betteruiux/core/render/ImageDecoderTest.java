package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;

class ImageDecoderTest {
    private static BufferedImage solid(int w, int h, Color c) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** 프레임마다 지연(1/100초)과 처리 방식을 지정해 애니메이션 GIF 를 만든다. */
    private static byte[] gif(List<BufferedImage> frames, int delayCs, String disposal) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (BufferedImage f : frames) {
                ImageWriteParam param = writer.getDefaultWriteParam();
                IIOMetadata md = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(f), param);
                String format = md.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) md.getAsTree(format);
                IIOMetadataNode gce = child(root, "GraphicControlExtension");
                gce.setAttribute("disposalMethod", disposal);
                gce.setAttribute("delayTime", Integer.toString(delayCs));
                md.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(f, null, md), param);
            }
            writer.endWriteSequence();
        }
        writer.dispose();
        return bytes.toByteArray();
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) if (root.item(i).getNodeName().equals(name)) return (IIOMetadataNode) root.item(i);
        IIOMetadataNode n = new IIOMetadataNode(name);
        root.appendChild(n);
        return n;
    }

    @Test
    void decodesAPngIntoArgbPixels() throws Exception {
        BufferedImage img = solid(4, 3, new Color(255, 0, 0, 255));
        img.setRGB(1, 1, 0x8000FF00);
        ImageDecoder.Decoded d = ImageDecoder.decode(png(img));
        assertEquals(4, d.width());
        assertEquals(3, d.height());
        assertFalse(d.animated());
        assertEquals(0xFFFF0000, d.frames().get(0)[0]);
        assertEquals(0x8000FF00, d.frames().get(0)[1 * 4 + 1], "알파가 보존된다");
        assertEquals(0, d.frameAt(99999));
    }

    @Test
    void decodesAnimatedGifFramesAndDelays() throws Exception {
        List<BufferedImage> frames = List.of(solid(6, 6, Color.RED), solid(6, 6, Color.GREEN), solid(6, 6, Color.BLUE));
        ImageDecoder.Decoded d = ImageDecoder.decode(gif(frames, 5, "none"));
        assertTrue(d.animated());
        assertEquals(3, d.frames().size());
        assertEquals(6, d.width());
        assertEquals(0xFFFF0000, d.frames().get(0)[0] | 0xFF000000);
        assertEquals(0xFF00FF00 & 0xFFFFFF, d.frames().get(1)[0] & 0xFFFFFF);
        assertEquals(0x0000FF, d.frames().get(2)[0] & 0xFFFFFF);
        assertEquals(50, d.delaysMs()[0], "5/100초 = 50ms");
        assertEquals(150, d.totalDurationMs());
        assertEquals(0, d.frameAt(10));
        assertEquals(1, d.frameAt(60));
        assertEquals(2, d.frameAt(120));
        assertEquals(0, d.frameAt(160), "끝나면 처음부터 반복");
    }

    @Test
    void tooShortGifDelaysAreRaisedToAWatchableMinimum() throws Exception {
        ImageDecoder.Decoded d = ImageDecoder.decode(gif(List.of(solid(3, 3, Color.RED), solid(3, 3, Color.BLUE)), 0, "none"));
        assertTrue(d.delaysMs()[0] >= 20, "지연 0 은 브라우저처럼 늘린다: " + d.delaysMs()[0]);
    }

    @Test
    void rejectsGarbageOversizedAndUnsupportedInput() throws Exception {
        assertThrows(IOException.class, () -> ImageDecoder.decode(new byte[0]));
        assertThrows(IOException.class, () -> ImageDecoder.decode("not an image".getBytes()));
        assertThrows(IOException.class, () -> ImageDecoder.decode(new byte[ImageDecoder.MAX_BYTES + 1]));
        IOException big = assertThrows(IOException.class, () -> ImageDecoder.decode(png(new BufferedImage(ImageDecoder.MAX_SIDE + 1, 2, BufferedImage.TYPE_INT_ARGB))));
        assertTrue(big.getMessage().contains("너무 큽니다"));
    }

    @Test
    void manyLargeFramesAreRejectedByThePixelBudget() throws Exception {
        List<BufferedImage> frames = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) frames.add(solid(1500, 1500, new Color(i * 20, 0, 0)));   // 10 * 2.25M = 22.5M > 16M
        byte[] data = gif(frames, 5, "none");
        if (data.length <= ImageDecoder.MAX_BYTES) {
            IOException e = assertThrows(IOException.class, () -> ImageDecoder.decode(data));
            assertTrue(e.getMessage().contains("너무 큽니다"));
        }
    }
}

package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VideoSourceTest {
    private static byte[] clip() throws IOException {
        return Files.readAllBytes(Path.of("src/test/resources/video/test.mp4"));
    }

    @Test
    void readsInfoAndAllFramesAsArgb() throws Exception {
        try (VideoSource v = VideoSource.open(clip(), 4096)) {
            assertEquals(160, v.info().width());
            assertEquals(96, v.info().height());
            assertEquals(15.0, v.info().fps(), 1.0);
            assertEquals(2.0, v.info().durationSec(), 0.3);
            int count = 0;
            Set<Integer> distinct = new HashSet<>();
            VideoSource.Frame f;
            int[] first = null;
            while ((f = v.next()) != null) {
                assertEquals(160 * 96, f.argb().length);
                assertEquals(0xFF, f.argb()[0] >>> 24);
                if (first == null) first = f.argb().clone();
                for (int px : f.argb()) distinct.add(px & 0xFFFFFF);
                count++;
            }
            assertEquals(30, count, "2초 x 15fps");
            assertTrue(distinct.size() > 50, "컬러 테스트 화면이라 색이 다양하다: " + distinct.size());
        }
    }

    @Test
    void colorsAreDecodedCorrectlyNotJustNonBlack() throws Exception {
        try (VideoSource v = VideoSource.open(clip(), 4096)) {
            int[] px = v.next().argb();
            // testsrc2 의 위쪽에는 흰색/노랑/청록/초록/자홍/빨강/파랑 막대가 있다: 한 줄 안에서 빨강 우세, 초록 우세, 파랑 우세 픽셀이 모두 있어야 한다.
            boolean red = false, green = false, blue = false;
            for (int p : px) {
                int r = p >> 16 & 0xFF, g = p >> 8 & 0xFF, b = p & 0xFF;
                red |= r > 180 && g < 90 && b < 90;
                green |= g > 180 && r < 90 && b < 90;
                blue |= b > 180 && r < 90 && g < 90;
            }
            assertTrue(red && green && blue, "빨강/초록/파랑이 모두 나온다: " + red + green + blue);
        }
    }

    @Test
    void rewindStartsOverAndLargeVideosAreScaledDown() throws Exception {
        try (VideoSource v = VideoSource.open(clip(), 80)) {
            assertEquals(80, v.info().width(), "가장 긴 변이 80 이하가 되도록 1/2 로 줄인다");
            assertEquals(48, v.info().height());
            VideoSource.Frame first = v.next();
            assertEquals(80 * 48, first.argb().length);
            while (v.next() != null) { }
            assertNull(v.next());
            v.rewind();
            VideoSource.Frame again = v.next();
            assertNotNull(again);
            assertTrue(java.util.Arrays.equals(first.argb(), again.argb()), "되감으면 같은 첫 프레임");
        }
    }

    @Test
    void rejectsGarbageAndOversizedFiles() {
        assertThrows(IOException.class, () -> VideoSource.open(new byte[0], 100));
        assertThrows(IOException.class, () -> VideoSource.open("not a video at all, just text".getBytes(), 100));
        assertThrows(IOException.class, () -> VideoSource.open(new byte[VideoSource.MAX_BYTES + 1], 100));
    }
}

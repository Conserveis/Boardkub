package th.ac.kmitl.connect;

import org.junit.jupiter.api.Test;
import th.ac.kmitl.connect.exception.ApiException;
import th.ac.kmitl.connect.image.ImageProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    static byte[] image(int w, int h, String format, int type) throws Exception {
        BufferedImage img = new BufferedImage(w, h, type);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, format, out));
        return out.toByteArray();
    }

    @Test
    void largePhotoIsShrunkToTheMaximumSide() throws Exception {
        ImageProcessor.Result r = processor.process(image(3200, 2000, "jpg", BufferedImage.TYPE_INT_RGB));
        assertEquals("image/jpeg", r.contentType());
        assertEquals(ImageProcessor.MAX_OUTPUT_SIDE, r.width());
        assertEquals(1000, r.height());
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(r.bytes()));
        assertEquals(1600, back.getWidth());
    }

    @Test
    void smallImageKeepsItsSize() throws Exception {
        ImageProcessor.Result r = processor.process(image(400, 300, "png", BufferedImage.TYPE_INT_RGB));
        assertEquals(400, r.width());
        assertEquals(300, r.height());
        assertEquals("jpg", r.extension());   // opaque PNG is stored as JPEG
    }

    @Test
    void transparentPngStaysPng() throws Exception {
        ImageProcessor.Result r = processor.process(image(200, 200, "png", BufferedImage.TYPE_INT_ARGB));
        assertEquals("image/png", r.contentType());
    }

    @Test
    void outputCarriesNoExifSegment() throws Exception {
        // A JPEG with an APP1 "Exif" segment inserted right after the SOI marker
        byte[] jpeg = image(300, 200, "jpg", BufferedImage.TYPE_INT_RGB);
        byte[] exifPayload = "Exif\0\0GPS-SECRET".getBytes(StandardCharsets.ISO_8859_1);
        int len = exifPayload.length + 2;
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(jpeg, 0, 2);
        withExif.write(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) (len >> 8), (byte) len});
        withExif.write(exifPayload);
        withExif.write(jpeg, 2, jpeg.length - 2);

        byte[] out = processor.process(withExif.toByteArray()).bytes();
        String raw = new String(out, StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("Exif"), "EXIF must be removed");
        assertFalse(raw.contains("GPS-SECRET"));
    }

    @Test
    void nonImageIsRejected() {
        byte[] fake = "<?php echo 'hi'; ?>".getBytes(StandardCharsets.UTF_8);
        ApiException e = assertThrows(ApiException.class, () -> processor.process(fake));
        assertEquals(400, e.getStatus().value());
    }

    @Test
    void gifIsRejected() throws Exception {
        byte[] gif = image(50, 50, "gif", BufferedImage.TYPE_INT_RGB);
        assertThrows(ApiException.class, () -> processor.process(gif));
    }
}

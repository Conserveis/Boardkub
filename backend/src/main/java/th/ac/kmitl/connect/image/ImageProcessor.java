package th.ac.kmitl.connect.image;

import org.springframework.stereotype.Component;
import th.ac.kmitl.connect.exception.ApiException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * Checks and re-encodes an uploaded image.
 * <ul>
 *   <li>The format is detected from the file's bytes, not from its name or Content-Type.</li>
 *   <li>Only JPEG and PNG are accepted; huge dimensions are rejected before decoding
 *       (protects against "decompression bomb" files).</li>
 *   <li>The image is redrawn from its pixels and saved again, which drops all metadata —
 *       including EXIF GPS coordinates that phone photos carry.</li>
 *   <li>The longest side is reduced to {@value #MAX_OUTPUT_SIDE}px to save storage and bandwidth.</li>
 * </ul>
 */
@Component
public class ImageProcessor {

    public static final int MAX_INPUT_SIDE = 8000;
    public static final long MAX_INPUT_PIXELS = 40_000_000L;
    public static final int MAX_OUTPUT_SIDE = 1600;
    private static final float JPEG_QUALITY = 0.85f;

    public record Result(byte[] bytes, String contentType, String extension, int width, int height) {}

    public Result process(byte[] input) {
        BufferedImage src;
        String format;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw ApiException.badRequest("ไฟล์นี้ไม่ใช่รูปภาพที่รองรับ (รองรับ JPG และ PNG)");
            }
            ImageReader reader = readers.next();
            try {
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
                    throw ApiException.badRequest("รองรับเฉพาะรูป JPG และ PNG");
                }
                reader.setInput(in, true, true);   // ignore metadata while reading
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_INPUT_SIDE || h > MAX_INPUT_SIDE
                        || (long) w * h > MAX_INPUT_PIXELS) {
                    throw ApiException.badRequest("รูปมีขนาดใหญ่เกินไป (ไม่เกิน " + MAX_INPUT_SIDE + " พิกเซลต่อด้าน)");
                }
                src = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest("อ่านไฟล์รูปไม่ได้ ไฟล์อาจเสียหาย");
        }

        boolean keepPng = format.equals("png") && src.getColorModel().hasAlpha();
        BufferedImage out = resize(src, keepPng);
        byte[] bytes = keepPng ? writePng(out) : writeJpeg(out);
        return keepPng
                ? new Result(bytes, "image/png", "png", out.getWidth(), out.getHeight())
                : new Result(bytes, "image/jpeg", "jpg", out.getWidth(), out.getHeight());
    }

    /** Redraws onto a fresh image (no metadata) and scales the longest side down if needed. */
    private BufferedImage resize(BufferedImage src, boolean keepAlpha) {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, (double) MAX_OUTPUT_SIDE / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));

        BufferedImage dst = new BufferedImage(nw, nh,
                keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            if (!keepAlpha) {           // JPEG has no transparency: use a white background
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, nw, nh);
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private byte[] writeJpeg(BufferedImage img) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(out);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(img, null, null), p);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot encode JPEG", e);
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }

    private byte[] writePng(BufferedImage img) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", bos);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot encode PNG", e);
        }
        return bos.toByteArray();
    }
}

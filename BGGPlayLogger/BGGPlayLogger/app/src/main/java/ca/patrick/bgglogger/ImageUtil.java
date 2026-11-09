package ca.patrick.bgglogger;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class ImageUtil {
    private ImageUtil() {}

    /** Decodes the photo (rotation from EXIF is applied automatically) and shrinks it so the longest side is maxDim. */
    static Bitmap loadScaled(ContentResolver cr, Uri uri, int maxDim) throws IOException {
        ImageDecoder.Source source = ImageDecoder.createSource(cr, uri);
        return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            int longest = Math.max(w, h);
            if (longest > maxDim) {
                float k = maxDim / (float) longest;
                decoder.setTargetSize(Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)));
            }
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
    }

    static byte[] toJpeg(Bitmap bmp, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
    }
}

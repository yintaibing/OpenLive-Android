package io.agora.util;

import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;

public class BitmapUtils {
    public static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    public static void saveBitmap(File file, Bitmap bitmap) {
        if (file == null || bitmap == null || bitmap.isRecycled()) {
            return;
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception ee) {
                ee.printStackTrace();
            }
        }
    }
}

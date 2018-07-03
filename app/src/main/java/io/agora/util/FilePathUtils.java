package io.agora.util;

import android.os.Environment;

import java.io.File;

public class FilePathUtils {
    public static File getPhotoDir() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
    }

    public static File getSystemVideoDir() {
        File DCIM = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

        File videoDir = null;
        File cameraDir = null;
        for (File f : DCIM.listFiles()) {
            if (f.isDirectory()) {
                if ("Video".equalsIgnoreCase(f.getName())) {
                    videoDir = f;
                } else if ("Camera".equalsIgnoreCase(f.getName())) {
                    cameraDir = f;
                }
            }
        }
        if (videoDir != null) {
            return videoDir;
        }
        if (cameraDir != null) {
            return cameraDir;
        }

        cameraDir = new File(DCIM, "Camera");
        cameraDir.mkdirs();
        return cameraDir;
    }
}

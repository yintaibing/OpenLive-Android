package io.agora.util;

import java.io.File;

public class FileUtils {

    public static void deleteFile(File file) {
        if (file != null) {
            file.delete();
        }
    }
}

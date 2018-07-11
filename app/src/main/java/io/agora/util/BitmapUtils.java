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

    public static int convertByteToInt(byte data){

        int heightBit = (int) ((data>>4) & 0x0F);
        int lowBit = (int) (0x0F & data);

        return heightBit * 16 + lowBit;
    }

    public static int[] convertByteToColor(byte[] data){
        int size = data.length;
        if (size == 0){
            return null;
        }

        int arg = 0;
        if (size % 3 != 0){
            arg = 1;
        }

        int []color = new int[size / 3 + arg];
        int red, green, blue;

        if (arg == 0){
            for(int i = 0; i < color.length; ++i){
                red = convertByteToInt(data[i * 3]);
                green = convertByteToInt(data[i * 3 + 1]);
                blue = convertByteToInt(data[i * 3 + 2]);

                color[i] = (red << 16) | (green << 8) | blue | 0xFF000000;
            }
        }else{
            for(int i = 0; i < color.length - 1; ++i){
                red = convertByteToInt(data[i * 3]);
                green = convertByteToInt(data[i * 3 + 1]);
                blue = convertByteToInt(data[i * 3 + 2]);
                color[i] = (red << 16) | (green << 8) | blue | 0xFF000000;
            }

            color[color.length - 1] = 0xFF000000;
        }

        return color;
    }

    public static Bitmap decodeFrameToBitmap(byte[] frame, int w, int h)
    {
        int []colors = convertByteToColor(frame);
        if (colors == null){
            return null;
        }
        Bitmap bmp = Bitmap.createBitmap(colors, w, h,Bitmap.Config.ARGB_8888);
        return bmp;
    }
}

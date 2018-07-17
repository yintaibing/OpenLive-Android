package com.zhenai.android.utils.record_screen;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

public class VideoEncodeConfig extends MediaEncodeConfig {
    public int width;
    public int height;
    public int dpi;
    public int bitrate;
    public int framerate;
    public int iframeInterval;
    public MediaCodecInfo.CodecProfileLevel codecProfileLevel;

    public Rect mCropRegion;
    public Bitmap mWaterMark;
    public int mWaterMarkX, mWaterMarkY;

    public VideoEncodeConfig(int width, int height, int dpi, int bitrate, int framerate,
                             int iframeInterval, String mimeType, String codecName,
                             MediaCodecInfo.CodecProfileLevel codecProfileLevel) {
        super(mimeType, codecName);
        this.width = width;
        this.height = height;
        this.dpi = dpi;
        this.bitrate = bitrate;
        this.framerate = framerate;
        this.iframeInterval = iframeInterval;
        this.codecName = codecName;
        this.mimeType = mimeType;
        this.codecProfileLevel = codecProfileLevel;
    }

    public void setCropRegion(Rect mCropRegion) {
        this.mCropRegion = mCropRegion;
    }

    public void setWaterMark(Bitmap bitmap, int mWaterMarkX, int mWaterMarkY) {
        mWaterMark = bitmap;
        this.mWaterMarkX = mWaterMarkX;
        this.mWaterMarkY = mWaterMarkY;
    }

    @Override
    public MediaFormat toMediaFormat() {
        MediaFormat format = MediaFormat.createVideoFormat(mimeType,
                getOutputWidth(), getOutputHeight());
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iframeInterval);
        if (codecProfileLevel != null && codecProfileLevel.profile != 0 &&
                codecProfileLevel.level != 0) {
            format.setInteger("profile", codecProfileLevel.profile);// KEY_PROFILE
            format.setInteger("level", codecProfileLevel.level);// KEY_LEVEL
        }
        // maybe useful
        // format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 10_000_000);
        return format;
    }

    public int getOutputWidth() {
        int outputWidth;
        if (mCropRegion != null) {
            outputWidth = Math.min(mCropRegion.width(), width);
        } else {
            outputWidth = width;
        }
        if (outputWidth % 2 != 0) {
            outputWidth--;
        }
        return outputWidth;
    }

    public int getOutputHeight() {
        int outputHeight;
        if (mCropRegion != null) {
            outputHeight = Math.min(mCropRegion.height(), height);
        } else {
            outputHeight = height;
        }
        if (outputHeight % 2 != 0) {
            outputHeight--;
        }
        return outputHeight;
    }
}

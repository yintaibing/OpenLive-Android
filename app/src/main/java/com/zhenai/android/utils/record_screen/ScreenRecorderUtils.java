package com.zhenai.android.utils.record_screen;

import android.media.MediaCodec;

public class ScreenRecorderUtils {
    public static boolean hasCodecConfigFlag(MediaCodec.BufferInfo bufferInfo) {
        return (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
    }

    public static boolean hasEosFlag(MediaCodec.BufferInfo bufferInfo) {
        return (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
    }
}

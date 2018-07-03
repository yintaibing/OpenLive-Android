package com.zhenai.android.utils.record_screen;

import android.media.MediaFormat;

public abstract class MediaEncodeConfig {
    public String mimeType;
    public String codecName;

    public MediaEncodeConfig(String mimeType, String codecName) {
        this.mimeType = mimeType;
        this.codecName = codecName;
    }

    public abstract MediaFormat toMediaFormat();
}

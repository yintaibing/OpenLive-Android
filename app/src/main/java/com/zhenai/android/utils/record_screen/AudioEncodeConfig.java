package com.zhenai.android.utils.record_screen;

import android.media.MediaFormat;

public class AudioEncodeConfig extends MediaEncodeConfig {
    public int bitrate;
    public int sampleRate;
    public int channelCount;
    public int profile;

    public AudioEncodeConfig(int bitrate, int sampleRate, int channelCount, int profile,
                             String mimeType, String codecName) {
        super(mimeType, codecName);
        this.bitrate = bitrate;
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        this.codecName = codecName;
        this.profile = profile;
    }

    @Override
    public MediaFormat toMediaFormat() {
        MediaFormat format = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, profile);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        return format;
    }
}

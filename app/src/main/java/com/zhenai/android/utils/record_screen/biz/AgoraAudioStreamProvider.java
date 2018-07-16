package com.zhenai.android.utils.record_screen.biz;

import android.media.MediaCodec;
import android.support.annotation.NonNull;

import com.zhenai.android.utils.record_screen.MediaEncodeConfig;
import com.zhenai.android.utils.record_screen.MediaStreamProvider;

import io.agora.rtc.plugin.rawdata.AgoraRawDataSimplePlugin;
import io.agora.rtc.plugin.rawdata.MediaDataAudioObserver;

public class AgoraAudioStreamProvider extends MediaStreamProvider implements
        MediaDataAudioObserver {
    public AgoraAudioStreamProvider(@NonNull MediaEncodeConfig config) {
        super(config);
    }

    private void listenAgora(boolean register) {
        AgoraRawDataSimplePlugin plugin = AgoraRawDataSimplePlugin.getInstance();
        if (register) {
            plugin.addAudioObserver(this);
            plugin.init();
        } else {
            plugin.removeAudioObserver(this);
        }
    }

    @Override
    protected void onCodecCreated(MediaCodec mediaCodec) {
    }

    @Override
    protected void onCodecConfigured(MediaCodec mediaCodec) {

    }

    @Override
    protected void onCodecStarted(MediaCodec mediaCodec) {
        listenAgora(true);
    }

    @Override
    public boolean isVideoStreamProvider() {
        return false;
    }

    @Override
    public void stopInternal() {
        super.stopInternal();
        listenAgora(false);
        mOnFirstAgoraAudioFrameListener = null;
    }

    @Override
    public void onRecordAudioFrame(byte[] data, int videoType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {
//        mux(data, bufferLength);
    }

    @Override
    public void onPlaybackAudioFrame(byte[] data, int videoType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {

    }

    @Override
    public void onPlaybackAudioFrameBeforeMixing(byte[] data, int videoType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {

    }

    @Override
    public void onMixedAudioFrame(byte[] data, int videoType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {
        if (mFirstAudioFrame) {
            mFirstAudioFrame = false;
            if (mOnFirstAgoraAudioFrameListener != null) {
                mOnFirstAgoraAudioFrameListener.onFirstAgoraAudioFrame();
            }
        }
        mux(data, bufferLength, mPtsCounter.newAudioPts(),
                false, true);
    }

    private boolean mFirstAudioFrame = true;
    private OnFirstAgoraAudioFrameListener mOnFirstAgoraAudioFrameListener;

    public void setOnFirstAgoraAudioFrameListener(OnFirstAgoraAudioFrameListener l) {
        this.mOnFirstAgoraAudioFrameListener = l;
    }

    public interface OnFirstAgoraAudioFrameListener {
        void onFirstAgoraAudioFrame();
    }
}

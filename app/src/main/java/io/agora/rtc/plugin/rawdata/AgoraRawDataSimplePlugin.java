package io.agora.rtc.plugin.rawdata;

import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 简化版声网裸数据插件
 *
 * @author yintaibing
 */
public class AgoraRawDataSimplePlugin implements MediaPreProcessing.ProgressCallback {
    private ByteBuffer mBufferAudioMix;
    private CopyOnWriteArrayList<MediaDataAudioObserver> mAudioObservers;

    private boolean mInited;

    private static AgoraRawDataSimplePlugin sInstance;

    public static AgoraRawDataSimplePlugin getInstance() {
        if (sInstance == null) {
            synchronized (AgoraRawDataSimplePlugin.class) {
                if (sInstance == null) {
                    sInstance = new AgoraRawDataSimplePlugin();
                }
            }
        }
        return sInstance;
    }

    private AgoraRawDataSimplePlugin() {
        mAudioObservers = new CopyOnWriteArrayList<>();
    }

    public void init() {
        synchronized (this) {
            if (!mInited) {
                mInited = true;

                if (mBufferAudioMix == null) {
                    mBufferAudioMix = ByteBuffer.allocateDirect(2048);
                    MediaPreProcessing.setAudioMixByteBUffer(mBufferAudioMix);
                }
                MediaPreProcessing.setCallback(this);
            }
        }
    }

    // 在RetEngine销毁后再调用
    public void destroy() {
        mInited = false;

        removeAllObservers();

        if (mBufferAudioMix != null) {
            mBufferAudioMix.clear();
        }

        MediaPreProcessing.releasePoint();
    }

    public void addAudioObserver(MediaDataAudioObserver observer) {
        mAudioObservers.add(observer);
    }

    public void removeAudioObserver(MediaDataAudioObserver observer) {
        mAudioObservers.remove(observer);
    }

    public void removeAllObservers() {
        mAudioObservers.clear();
    }

    @Override
    public void onCaptureVideoFrame(int uid, int frameType, int width, int height, int bufferLength, int yStride, int uStride, int vStride, int rotation, long renderTimeMs) {

    }

    @Override
    public void onRenderVideoFrame(int uid, int frameType, int width, int height, int bufferLength, int yStride, int uStride, int vStride, int rotation, long renderTimeMs) {

    }

    @Override
    public void onRecordAudioFrame(int videoType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {
//        for (MediaDataAudioObserver observer : mAudioObservers) {
//            observer.onRecordAudioFrame(mBufferAudioMix.array(), videoType, samples, bytesPerSample, channels, samplesPerSec, renderTimeMs, bufferLength);
//        }
    }

    @Override
    public void onPlaybackAudioFrame(int videoType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {
//        for (MediaDataAudioObserver observer : mAudioObservers) {
//            observer.onPlaybackAudioFrame(byteBufferAudioPlay.array(), videoType, samples, bytesPerSample, channels, samplesPerSec, renderTimeMs, bufferLength);
//        }
    }

    @Override
    public void onPlaybackAudioFrameBeforeMixing(int videoType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {
//        for (MediaDataAudioObserver observer : mAudioObservers) {
//            observer.onPlaybackAudioFrameBeforeMixing(byteBufferBeforeAudioMix.array(), videoType, samples, bytesPerSample, channels, samplesPerSec, renderTimeMs, bufferLength);
//        }
    }

    @Override
    public void onMixedAudioFrame(int videoType, int samples, int bytesPerSample, int channels, int samplesPerSec, long renderTimeMs, int bufferLength) {
        for (MediaDataAudioObserver observer : mAudioObservers) {
            observer.onMixedAudioFrame(mBufferAudioMix.array(), videoType, samples, bytesPerSample, channels, samplesPerSec, renderTimeMs, bufferLength);
        }
    }
}

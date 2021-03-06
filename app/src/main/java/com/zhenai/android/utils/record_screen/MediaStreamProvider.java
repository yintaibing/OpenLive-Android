package com.zhenai.android.utils.record_screen;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public abstract class MediaStreamProvider {
    protected static final String TAG = "MediaStreamProvider";

    protected MediaEncodeConfig mConfig;
    private MediaCodec mMediaCodec;
    private MediaMuxerWrapper mMuxerWrapper;

    protected AtomicBoolean mQuit = new AtomicBoolean(false);
    protected volatile int mMuxerTrackIndex = -1;

    public MediaStreamProvider(@NonNull MediaEncodeConfig config) {
        mConfig = config;
    }

    public void setMuxerWrapper(MediaMuxerWrapper muxerWrapper) {
        this.mMuxerWrapper = muxerWrapper;
    }

    public MediaMuxerWrapper getMuxerWrapper() {
        return mMuxerWrapper;
    }

    public void prepare() {
        MediaFormat format = mConfig.toMediaFormat();
        try {
            mMediaCodec = //isVideoStreamProvider() ? MediaCodec.createByCodecName(mConfig.codecName) :
                    MediaCodec.createEncoderByType(mConfig.mimeType);
        } catch (IOException e) {
            Log.e(TAG, "prepare异常", e);
            return;
        }

        onCodecCreated(mMediaCodec);
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        onCodecConfigured(mMediaCodec);
        mMediaCodec.start();
        Log.e(TAG, "started media codec: " + mConfig.codecName);
        onCodecStarted(mMediaCodec);
    }

    public void addMuxerTrack(MediaFormat mediaFormat) {
        if (mediaFormat != null && mMuxerTrackIndex < 0 && mMuxerWrapper != null) {
            mMuxerTrackIndex = mMuxerWrapper.addTrack(this, mediaFormat);
        }
    }
    public abstract boolean isVideoStreamProvider();

    protected abstract void onCodecCreated(MediaCodec mediaCodec);

    protected abstract void onCodecConfigured(MediaCodec mediaCodec);

    protected abstract void onCodecStarted(MediaCodec mediaCodec);

    public MediaCodec getMediaCodec() {
        return mMediaCodec;
    }

    public int getMuxerTrackIndex() {
        return mMuxerTrackIndex;
    }

    public void mux(int outputIndex, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        MediaMuxerWrapper muxer = mMuxerWrapper;
        MediaCodec codec = mMediaCodec;
        if (codec == null || muxer == null) {
            return;
        }

        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            addMuxerTrack(codec.getOutputFormat());
        } else if (outputIndex >= 0 && bufferInfo != null) {
            muxer.writeSampleData(this, outputIndex, bufferInfo);

            if (hasEosFlag(bufferInfo)) {
                mQuit.set(true);
            }
        }

        if (mQuit.get()) {
            stopMediaCodec();
            releaseCodecAndMuxer();
        }
    }

    public void mux(byte[] byteBuffer, int length) {
        MediaMuxerWrapper muxer = mMuxerWrapper;
        MediaCodec codec = mMediaCodec;
        if (codec == null || muxer == null) {
            return;
        }

        int inputIndex = codec.dequeueInputBuffer(-1);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
            if (inputBuffer != null) {
                inputBuffer.clear();
                inputBuffer.put(byteBuffer, 0, length);

                if (length > 0) {
                    codec.queueInputBuffer(inputIndex, 0, length, 0,
                            mQuit.get() ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                }
            }
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputIndex;
        ByteBuffer outputBuffer;
        while (!mQuit.get()) {
            outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0);
            if (outputIndex >= 0) {
                muxer.writeSampleData(this, outputIndex, bufferInfo);

                if (hasEosFlag(bufferInfo)) {
                    mQuit.set(true);
                    break;
                }
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                addMuxerTrack(codec.getOutputFormat());
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            }
        }

        if (mQuit.get()) {
            stopMediaCodec();
            releaseCodecAndMuxer();
        }
    }

    protected boolean hasEosFlag(MediaCodec.BufferInfo bufferInfo) {
        return (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
    }

    public void stop() {
        mQuit.set(true);
    }

    private void stopMediaCodec() {
        MediaCodec codec = mMediaCodec;
        if (codec != null) {
            try {
                codec.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void release() {
//        mMuxerTrackIndex = -1;
    }

    private void releaseCodecAndMuxer() {
        mMediaCodec = null;
        mMuxerWrapper = null;
    }
}

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
    protected static final String TAG = MediaStreamProvider.class.getSimpleName();

    private MediaEncodeConfig mConfig;
    private MediaCodec mMediaCodec;
    private MediaMuxerWrapper mMuxerWrapper;
    protected PresentationTimeCounter mPtsCounter;

    protected AtomicBoolean mQuit = new AtomicBoolean(false);
    protected volatile int mMuxerTrackIndex = -1;

    public MediaStreamProvider(@NonNull MediaEncodeConfig config) {
        mConfig = config;
    }

    public void setMuxerWrapper(MediaMuxerWrapper muxerWrapper) {
        this.mMuxerWrapper = muxerWrapper;
    }

    public void setPtsCounter(PresentationTimeCounter mPtsCounter) {
        this.mPtsCounter = mPtsCounter;
    }

    public void prepare() {
        MediaFormat format = mConfig.toMediaFormat();
        try {
            mMediaCodec = MediaCodec.createEncoderByType(mConfig.mimeType);
        } catch (IOException e) {
            Log.e(TAG, "prepare异常", e);
            return;
        }

        onCodecCreated(mMediaCodec);
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        onCodecConfigured(mMediaCodec);
        mMediaCodec.start();
        Log.e(TAG, "started media codec: " + mMediaCodec.getName());
        onCodecStarted(mMediaCodec);
    }

    public void addMuxerTrack(MediaFormat codecOutputFormat) {
        if (codecOutputFormat != null && mMuxerTrackIndex < 0 && mMuxerWrapper != null) {
            mMuxerTrackIndex = mMuxerWrapper.addTrack(codecOutputFormat, isVideoStreamProvider());
        }
    }

    public abstract boolean isVideoStreamProvider();

    protected abstract void onCodecCreated(MediaCodec mediaCodec);

    protected abstract void onCodecConfigured(MediaCodec mediaCodec);

    protected abstract void onCodecStarted(MediaCodec mediaCodec);

    protected long newPresentationTime() {
        return mPtsCounter != null ? mPtsCounter.newPresentationTimeUs() : 0L;
    }

    public MediaCodec getMediaCodec() {
        return mMediaCodec;
    }

    public MediaEncodeConfig getConfig() {
        return mConfig;
    }

    public int getMuxerTrackIndex() {
        return mMuxerTrackIndex;
    }

    public void mux(int outputIndex,
                    MediaCodec.BufferInfo bufferInfo,
                    boolean isVideo,
                    boolean resetPresentationTime) {
        if (mQuit.get()) {
            stopInternal();
            return;
        }
        MediaMuxerWrapper muxer = mMuxerWrapper;
        MediaCodec codec = mMediaCodec;
        if (codec == null || muxer == null) {
            return;
        }

        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            addMuxerTrack(codec.getOutputFormat());
        } else if (outputIndex >= 0 && bufferInfo != null) {
            if (!ScreenRecorderUtils.hasCodecConfigFlag(bufferInfo)) {
                // BUFFER_FLAG_CODEC_CONFIG,已手动configure了codec，不再需要此buffer，
                // 否则可能导致chrome无法播放
                muxer.writeSampleData(mMuxerTrackIndex,
                        codec.getOutputBuffer(outputIndex), bufferInfo,
                        isVideo, resetPresentationTime);
            }
            codec.releaseOutputBuffer(outputIndex, false);

            if (ScreenRecorderUtils.hasEosFlag(bufferInfo)) {
                mQuit.set(true);
            }
        }

        if (mQuit.get()) {
            stopInternal();
        }
    }

//    public void mux(boolean endOfStream) {
//        MediaCodec codec = mMediaCodec;
//        MediaMuxerWrapper muxer = mMuxerWrapper;
//        if (codec == null || muxer == null) {
//            return;
//        }
//
//        if (endOfStream) {
//            codec.signalEndOfInputStream();
//        }
//
//        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
//        while (!mQuit.get()) {
//            int outputIndex = codec.dequeueOutputBuffer(info, 10000L);
//            if (outputIndex >= 0) {
//                if (!ScreenRecorderUtils.hasCodecConfigFlag(info)) {
//                    // BUFFER_FLAG_CODEC_CONFIG,已手动configure了codec，不再需要此buffer，
//                    // 否则可能导致chrome无法播放
//                    muxer.doWriteSampleData(this, outputIndex, info);
//                } else {
//                    // 未被处理的buffer，直接释放掉
//                    codec.releaseOutputBuffer(outputIndex, false);
//                }
//            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                // -1,INFO_TRY_AGAIN_LATER,稍后再试
//                if (!endOfStream) {
//                    break;
//                }
//            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                // -2,INFO_OUTPUT_FORMAT_CHANGED,格式变化,添加轨道
//                addMuxerTrack(codec.getOutputFormat());
//            }
//        }
//    }

    public void mux(byte[] byteBuffer, int length,
                    long presentationTime,
                    boolean isVideo,
                    boolean resetPresentationTime) {
        if (mQuit.get()) {
            stopInternal();
            return;
        }
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

                codec.queueInputBuffer(inputIndex, 0, length, presentationTime,
                        mQuit.get() || length <= 0 ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
            }
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputIndex;
        while (!mQuit.get()) {
            outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0);
            if (outputIndex >= 0) {
                if (!ScreenRecorderUtils.hasCodecConfigFlag(bufferInfo)) {
                    // BUFFER_FLAG_CODEC_CONFIG,已手动configure了codec，不再需要此buffer，
                    // 否则可能导致chrome无法播放
                    muxer.writeSampleData(getMuxerTrackIndex(),
                            codec.getOutputBuffer(outputIndex), bufferInfo,
                            isVideo, resetPresentationTime);
                }
                codec.releaseOutputBuffer(outputIndex, false);

                if (ScreenRecorderUtils.hasEosFlag(bufferInfo)) {
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
            stopInternal();
        }
    }

    public void enqueue(byte[] byteBuffer, int length,
                        long presentationTime) {
        MediaCodec codec = mMediaCodec;
        if (codec == null) {
            return;
        }

        int inputIndex = codec.dequeueInputBuffer(-1);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
            if (inputBuffer != null) {
                inputBuffer.clear();
                inputBuffer.put(byteBuffer, 0, length);

                codec.queueInputBuffer(inputIndex, 0, length, presentationTime,
                        mQuit.get() || length <= 0 ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
            }
        }
    }

    public void stop() {
        mQuit.set(true);
    }

    protected void stopInternal() {
        MediaCodec codec = mMediaCodec;
        if (codec != null) {
            try {
                codec.stop();
                Log.e(TAG, "stop codec");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mMediaCodec = null;
        mMuxerWrapper = null;
    }
}

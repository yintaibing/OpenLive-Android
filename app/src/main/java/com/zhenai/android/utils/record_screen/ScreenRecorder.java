package com.zhenai.android.utils.record_screen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ScreenRecorder {
    private static final String TAG = "ScreenRecorder";
    private static final int STATE_DEFAULT = 0;
    private static final int STATE_RECORDING = 1;
    private static final int STATE_STOPPED = 2;

    private File mOutputFile;
    private PtsCounter mPtsCounter;
    private MediaMuxerWrapper mMuxerWrapper;
    private ArrayList<MediaStreamProvider> mMediaStreamProviders;
    private AtomicInteger mState = new AtomicInteger(STATE_DEFAULT);

    public ScreenRecorder(File outputFile) {
        mOutputFile = outputFile;
        if (outputFile == null || !outputFile.exists() || outputFile.isDirectory()) {
            throw new IllegalArgumentException("illegal output file");
        }
        mPtsCounter = new PtsCounter(0);
        mMediaStreamProviders = new ArrayList<>(1);
    }

    public void addScreenStreamProvider(MediaProjection mediaProjection,
                                        VideoEncodeConfig videoEncodeConfig) {
        if (mediaProjection == null || videoEncodeConfig == null) {
            return;
        }

        ScreenStreamProvider screenStreamProvider = new ScreenStreamProvider(
                mediaProjection, videoEncodeConfig);
        addMediaStreamProvider(screenStreamProvider);
    }

    public void addMediaStreamProvider(MediaStreamProvider provider) {
        if (provider.isVideoStreamProvider()) {
            for (MediaStreamProvider p : mMediaStreamProviders) {
                if (p.isVideoStreamProvider()) {
                    throw new IllegalArgumentException("只能添加1个视频provider");
                }
            }
        }
        // 为了方便查找，将video provider放在0
        mMediaStreamProviders.add(
                provider.isVideoStreamProvider() ? 0 : mMediaStreamProviders.size(), provider);
        Log.e(TAG, "added media stream provider: " + provider.getClass().getSimpleName());
    }

    public void start() {
        if (mState.get() != STATE_DEFAULT) {
            throw new IllegalStateException("state should be STATE_DEFAULT");
        }

        mState.set(STATE_RECORDING);

        try {
            mMuxerWrapper = new MediaMuxerWrapper(mOutputFile, mMediaStreamProviders.size());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        for (MediaStreamProvider provider : mMediaStreamProviders) {
            provider.setMuxerWrapper(mMuxerWrapper);
            provider.setPtsCounter(mPtsCounter);
            provider.prepare();
        }
    }

    public void stop() {
        if (mState.get() == STATE_RECORDING) {
            stopProviders();
            signalEndOfStream();
        }
        release();
    }

    public boolean isRecording() {
        return mState.get() == STATE_RECORDING;
    }

    private void stopProviders() {
        for (MediaStreamProvider provider : mMediaStreamProviders) {
            provider.stop();
        }
    }

    private void signalEndOfStream() {
//        MediaCodec.BufferInfo eos = new MediaCodec.BufferInfo();
//        ByteBuffer buffer = ByteBuffer.allocate(0);
//        eos.set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//        for (MediaStreamProvider provider : mMediaStreamProviders) {
//            mMuxerWrapper.writeSampleDataJust(provider.getMuxerTrackIndex(), buffer, eos);
//        }
    }

    private void release() {
        for (MediaStreamProvider provider : mMediaStreamProviders) {
            provider.release();
        }
        mMediaStreamProviders.clear();

        if (mState.get() == STATE_RECORDING) {
            mState.set(STATE_STOPPED);
            if (mMuxerWrapper != null) {
                mMuxerWrapper.stop();
                mMuxerWrapper = null;
            }
        }
    }

    public static void requestRecordScreen(Activity activity, int requestCode) {
        if (!isAfterLollipop()) {
            return;
        }
        MediaProjectionManager mpm = (MediaProjectionManager) activity.getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);
        if (mpm != null) {
            activity.startActivityForResult(mpm.createScreenCaptureIntent(), requestCode);
        }
    }

    public static MediaProjection getMediaProjection(Activity activity, int resultCode, Intent data) {
        if (isAfterLollipop()) {
            MediaProjectionManager mpm = (MediaProjectionManager) activity.getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE);
            if (mpm != null) {
                return mpm.getMediaProjection(resultCode, data);
            }
        }
        return null;
    }

    public static boolean isAfterLollipop() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }
}

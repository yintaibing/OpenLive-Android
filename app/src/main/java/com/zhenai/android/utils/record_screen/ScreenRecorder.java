package com.zhenai.android.utils.record_screen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.Log;

import com.zhenai.android.utils.record_screen.biz.AgoraAudioStreamProvider;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class ScreenRecorder {
    private static final String TAG = "ScreenRecorder";
    private static final int STATE_DEFAULT = 0;
    private static final int STATE_RECORDING = 1;
    private static final int STATE_STOPPED = 2;

    private File mOutputFile;
    private MediaMuxerWrapper mMuxerWrapper;
    private ScreenStreamProvider mScreenStreamProvider;
    private AgoraAudioStreamProvider mAgoraAudioStreamProvider;
    private AtomicInteger mState = new AtomicInteger(STATE_DEFAULT);

    public ScreenRecorder(File outputFile) {
        mOutputFile = outputFile;
        if (outputFile == null || !outputFile.exists() || outputFile.isDirectory()) {
            throw new IllegalArgumentException("illegal output file");
        }
    }

    public void setScreenStream(MediaProjection mediaProjection,
                                VideoEncodeConfig videoEncodeConfig) {
        mScreenStreamProvider = new ScreenStreamProvider(mediaProjection, videoEncodeConfig);
    }

    public void setAgoraAudioStream(AudioEncodeConfig audioEncodeConfig) {
        mAgoraAudioStreamProvider = new AgoraAudioStreamProvider(audioEncodeConfig);
    }

    public void start() {
        if (mState.get() != STATE_DEFAULT) {
            throw new IllegalStateException("state should be STATE_DEFAULT");
        }
        if (mScreenStreamProvider == null && mAgoraAudioStreamProvider == null) {
            throw new IllegalStateException("no provider added");
        }

        mState.set(STATE_RECORDING);

        try {
            mMuxerWrapper = new MediaMuxerWrapper(mOutputFile,
                    mScreenStreamProvider != null,
                    mAgoraAudioStreamProvider != null);
            mMuxerWrapper.setStateCallback(new MediaMuxerWrapper.StateCallback() {
                @Override
                public void onStart() {
                    Log.e(TAG, "MediaMuxerWrapper.onStart");
                }

                @Override
                public void onStop() {
                    Log.e(TAG, "MediaMuxerWrapper.onStop");
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if (mScreenStreamProvider != null) {
            mScreenStreamProvider.setMuxerWrapper(mMuxerWrapper);
            mScreenStreamProvider.prepare();
        }
        if (mAgoraAudioStreamProvider != null) {
            mAgoraAudioStreamProvider.setMuxerWrapper(mMuxerWrapper);
            mAgoraAudioStreamProvider.setOnFirstAgoraAudioFrameListener(new AgoraAudioStreamProvider.OnFirstAgoraAudioFrameListener() {
                @Override
                public void onFirstAgoraAudioFrame() {
                    if (mScreenStreamProvider != null) {
                        mScreenStreamProvider.signalCreateVirtualDisplay();
//                        mScreenStreamProvider.createVirtualDisplay();
                    }
                }
            });
            mAgoraAudioStreamProvider.prepare();
        }
    }

    public void stop() {
        if (isRecording()) {
            if (mScreenStreamProvider != null) {
                mScreenStreamProvider.stop();
            }
            if (mAgoraAudioStreamProvider != null) {
                mAgoraAudioStreamProvider.stop();
            }
            mMuxerWrapper.writeEndOfStream();
            mMuxerWrapper.stop();
        }
        mState.set(STATE_STOPPED);
    }

    public boolean isRecording() {
        return mState.get() == STATE_RECORDING;
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

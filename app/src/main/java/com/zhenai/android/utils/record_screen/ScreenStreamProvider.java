package com.zhenai.android.utils.record_screen;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import com.zhenai.android.utils.record_screen.gl.EGLRender;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ScreenStreamProvider extends MediaStreamProvider {
    private static final int MSG_PREPARE = 1;
    private static final int MSG_START_VIRTUAL_DISPLAY = 2;
    private static final int MSG_RENDER = 3;

    private MediaProjection mMediaProjection;
    private Surface mCodecSurface;
    private VirtualDisplay mVirtualDisplay;

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Surface mVirtualDisplayOutputSurface;
    private EGLRender mEGLRender;

    private boolean mWaitReceiveScreenStream;

    public ScreenStreamProvider(@NonNull MediaProjection mediaProjection,
                                @NonNull VideoEncodeConfig config) {
        super(config);
        mMediaProjection = mediaProjection;
    }

    @Override
    public void prepare() {
        mHandlerThread = new HandlerThread("ScreenStreamProvider");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_PREPARE:// prepare
                        ScreenStreamProvider.super.prepare();
                        break;

                    case MSG_START_VIRTUAL_DISPLAY:
                        createVirtualDisplay();
                        break;

                    case MSG_RENDER:// update texture and draw
                        if (!mQuit.get()) {
                            mEGLRender.updateTexAndDraw();
                        }
                        break;
                }
            }
        };
        mHandler.obtainMessage(MSG_PREPARE).sendToTarget();
    }

    @Override
    public boolean isVideoStreamProvider() {
        return true;
    }

    @Override
    protected void onCodecCreated(MediaCodec mediaCodec) {
        mediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                mux(index, info, true, true);
            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                e.printStackTrace();
            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                addMuxerTrack(format);
            }
        });
    }

    @Override
    protected void onCodecConfigured(MediaCodec mediaCodec) {
        mCodecSurface = mediaCodec.createInputSurface();
        Log.e(TAG, "created surface for codec: " + mediaCodec.getName());
    }

    @Override
    protected void onCodecStarted(MediaCodec mediaCodec) {
        final VideoEncodeConfig config = (VideoEncodeConfig) getConfig();

        mEGLRender = new EGLRender(mCodecSurface, config.width, config.height, config.framerate);
        mEGLRender.setupRenders(config.mCropRegion, config.mWaterMark,
                config.mWaterMarkX, config.mWaterMarkY);
        mEGLRender.setSurfaceTextureListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                if (mHandler != null) {
                    mHandler.obtainMessage(MSG_RENDER).sendToTarget();
                }
            }
        });

        mVirtualDisplayOutputSurface = mEGLRender.getDecodeSurface();

//        createVirtualDisplay();// 等待声网调signalCreateVirtualDisplay
        if (!mWaitReceiveScreenStream) {
            createVirtualDisplay();
        }
    }

    public void waitReceiveScreenStream() {
        mWaitReceiveScreenStream = true;
    }

    public void signalReceiveScreenStream() {
        if (mHandler != null) {
            mHandler.obtainMessage(MSG_START_VIRTUAL_DISPLAY).sendToTarget();
        }
    }

    private void createVirtualDisplay() {
        VideoEncodeConfig config = (VideoEncodeConfig) getConfig();
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenStreamProvider",
                config.width, config.height, config.dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mVirtualDisplayOutputSurface, null, null);
        Log.e(TAG, "created virtual display: " + mVirtualDisplay);
    }

    @Override
    protected void stopInternal() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        if (mHandlerThread != null) {
            mHandlerThread.getLooper().quitSafely();
            mHandlerThread = null;
            mHandler = null;
        }
        if (mEGLRender != null) {
            mEGLRender.stop();
        }
        super.stopInternal();
    }
}

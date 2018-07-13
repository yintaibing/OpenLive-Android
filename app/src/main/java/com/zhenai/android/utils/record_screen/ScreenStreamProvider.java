package com.zhenai.android.utils.record_screen;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
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

import com.zhenai.android.utils.record_screen.copy.EGLRender;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ScreenStreamProvider extends MediaStreamProvider {
    private MediaProjection mMediaProjection;
    private Surface mCodecSurface;
    private VirtualDisplay mVirtualDisplay;

    private HandlerThread mHandlerThread;
    private Handler mHandler;
//    private ImageReader mImageReader;
    private SurfaceTexture mSurfaceTexture;
    private Surface mVirtualDisplayOutputSurface;
//    private ScreenStreamCropper mCropper;
    private EGLRender mEGLRender;

    public ScreenStreamProvider(@NonNull MediaProjection mediaProjection,
                                @NonNull VideoEncodeConfig config) {
        super(config);
        mMediaProjection = mediaProjection;
//        mCropper = new ScreenStreamCropper();

        mHandlerThread = new HandlerThread("cropper");
    }

    @Override
    public void prepare() {
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        ScreenStreamProvider.super.prepare();
                        break;

                    case 2:
//                        mCropper.destroyGL();
                        break;

                    case 3:
//                        mCropper.render(newPts());
                        break;
                }
            }
        };
        mHandler.sendMessageDelayed(mHandler.obtainMessage(1), 1000L);
    }

    @Override
    public boolean isVideoStreamProvider() {
        return true;
    }

    @Override
    protected void onCodecCreated(MediaCodec mediaCodec) {
        final VideoEncodeConfig config = (VideoEncodeConfig) mConfig;

//        mCropper.w = config.width;
//        mCropper.h = config.height;

        mediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                mux(index, info);
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
//        mCropper.setmOutputSurface(mCodecSurface);
        Log.e(TAG, "created surface for codec: " + mediaCodec.getName());
    }

    @Override
    protected void onCodecStarted(MediaCodec mediaCodec) {
        final VideoEncodeConfig config = (VideoEncodeConfig) mConfig;

        mEGLRender = new EGLRender(mCodecSurface, config.width, config.height, config.framerate);
        mEGLRender.setCallBack(new EGLRender.onFrameCallBack() {
            @Override
            public void onUpdate() {
                mux(false);
            }

            @Override
            public void onCutScreen(Bitmap bitmap) {

            }
        });

//        mCropper.createGL();
//        mSurfaceTexture = new SurfaceTexture(mCropper.getmTextureID());
//        mSurfaceTexture.setDefaultBufferSize(config.width, config.height);
//        mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
//            @Override
//            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//                mCropper.makeCurrent(true);
//                surfaceTexture.updateTexImage();
//                mCropper.render(newPts());
//            }
//        }, mHandler);

//        mVirtualDisplayOutputSurface = new Surface(mSurfaceTexture);
        mVirtualDisplayOutputSurface = mEGLRender.getDecodeSurface();

        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenStreamProvider",
                config.width, config.height, config.dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mVirtualDisplayOutputSurface, null, null);
        mEGLRender.start();
        Log.e(TAG, "created virtual display: " + mVirtualDisplay);
    }

    @Override
    protected void stopInternal() {
        super.stopInternal();
        mHandler.sendMessage(mHandler.obtainMessage(2));
    }

    @Override
    public void release() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        super.release();
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
//        mVirtualDisplayOutputSurface.release();
//        mSurfaceTexture.release();
    }
}

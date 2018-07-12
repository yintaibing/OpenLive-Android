package com.zhenai.android.utils.record_screen;

import android.annotation.TargetApi;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ScreenStreamProvider extends MediaStreamProvider {
    private MediaProjection mMediaProjection;
    private Surface mCodecSurface;
    private VirtualDisplay mVirtualDisplay;

    private ImageReader mImageReader;
    private ScreenStreamCropper mCropper;

    public ScreenStreamProvider(@NonNull MediaProjection mediaProjection,
                                @NonNull VideoEncodeConfig config) {
        super(config);
        mMediaProjection = mediaProjection;
        mCropper = new ScreenStreamCropper();
        mCropper.start();
        mCropper.initHandler();
    }

//    @Override
//    public void prepare() {
//        mCropper.mHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                Log.e("ScreenStreamProvider", "prepare thread="+Thread.currentThread().getName());
//                ScreenStreamProvider.super.prepare();
//            }
//        });
//    }

    @Override
    public boolean isVideoStreamProvider() {
        return true;
    }

    @Override
    protected void onCodecCreated(MediaCodec mediaCodec) {
        final VideoEncodeConfig config = (VideoEncodeConfig) mConfig;

        mCropper.w = config.width;
        mCropper.h = config.height;

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
        mCropper.mOutputSurface = mCodecSurface;
        Log.e(TAG, "created surface for codec: " + mediaCodec.getName());
    }

    @Override
    protected void onCodecStarted(MediaCodec mediaCodec) {
        final VideoEncodeConfig config = (VideoEncodeConfig) mConfig;

        mCropper.signalPrepare();

        mImageReader = ImageReader.newInstance(config.width, config.height,
                PixelFormat.RGBA_8888, 1);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireNextImage();
                Image.Plane[] planes = image.getPlanes();
                if (planes != null && planes.length > 0) {
                    ByteBuffer byteBuffer = planes[0].getBuffer();
                    long pts = newPts();
                    mCropper.signalRender(byteBuffer, pts);
                }

                image.close();
            }
        }, mCropper.mHandler);

        Surface imageReaderSurface = mImageReader.getSurface();
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenStreamProvider",
                config.width, config.height, config.dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                imageReaderSurface, null, null);
        Log.e(TAG, "created virtual display: " + mVirtualDisplay);
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
        mCropper.signalStop();
    }
}

package com.zhenai.android.utils.record_screen;

import android.annotation.TargetApi;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ScreenStreamProvider extends MediaStreamProvider {
    private MediaProjection mMediaProjection;
    private Surface mSurface;
    private VirtualDisplay mVirtualDisplay;

    public ScreenStreamProvider(@NonNull MediaProjection mediaProjection,
                                @NonNull VideoEncodeConfig config) {
        super(config);
        mMediaProjection = mediaProjection;
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
                mux(index, codec.getOutputBuffer(index), info);
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
        mSurface = mediaCodec.createInputSurface();
        Log.e(TAG, "created surface for codec: " + mediaCodec.getName());
    }

    @Override
    protected void onCodecStarted(MediaCodec mediaCodec) {
        VideoEncodeConfig config = (VideoEncodeConfig) mConfig;
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenStreamProvider",
                config.width, config.height, config.dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mSurface, null, null);
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
    }
}

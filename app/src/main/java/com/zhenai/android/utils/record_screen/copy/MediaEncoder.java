package com.zhenai.android.utils.record_screen.copy;

import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import com.zhenai.android.utils.record_screen.ScreenRecorderUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by ryan on 2017/2/23 0023.
 */

public class MediaEncoder extends Thread {


    private final String TAG = "MediaEncoder";

    private final String mime_type = MediaFormat.MIMETYPE_VIDEO_AVC;


    private DisplayManager displayManager;
    private MediaProjection projection;
    private MediaCodec mEncoder;
    private VirtualDisplay virtualDisplay;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private EGLRender eglRender;
    private Surface surface;


    //屏幕相关
    private int screen_width;
    private int screen_height;
    private int screen_dpi;


    //编码参数相关
    private int frame_bit = 2000000;//2MB
    private int frame_rate = 20;//这里指的是Mediacodec30张图为1组 ，并不是视屏本身的FPS
    private int frame_internal = 1;//关键帧间隔 一组加一个关键帧
    private final int TIMEOUT_US = 10000;
    private int video_fps = 30;
    private byte[] sps=null;
    private byte[] pps=null;


    private MediaMuxer muxer;


    private OnScreenCallBack onScreenCallBack;

    public void setOnScreenCallBack(OnScreenCallBack onScreenCallBack) {
        this.onScreenCallBack = onScreenCallBack;
    }

    public interface OnScreenCallBack {
        void onScreenInfo(byte[] bytes);
        void onCutScreen(Bitmap bitmap);
    }

    public MediaEncoder(MediaProjection projection, int screen_width, int screen_height, int screen_dpi) {
        this.projection = projection;
        initScreenInfo(screen_width, screen_height, screen_dpi);
    }

    public MediaEncoder(DisplayManager displayManager, int screen_width, int screen_height, int screen_dpi) {
        this.displayManager = displayManager;
        initScreenInfo(screen_width, screen_height, screen_dpi);
    }

    private void initScreenInfo(int screen_width, int screen_height, int screen_dpi) {
        this.screen_width = screen_width;
        this.screen_height = screen_height;
        this.screen_dpi = screen_dpi;
    }

    /**
     * 设置视频FPS
     *
     * @param fps
     */
    public MediaEncoder setVideoFPS(int fps) {
        video_fps = fps;
        return this;
    }

    /**
     * 设置视屏编码采样率
     *
     * @param bit
     */
    public MediaEncoder setVideoBit(int bit) {
        frame_bit = bit;
        return this;
    }

    @Override
    public void run() {
        super.run();
        try {
            muxer = new MediaMuxer(generateOutputFile().getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            prepareEncoder();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        if (projection != null) {
            virtualDisplay = projection.createVirtualDisplay("screen", screen_width, screen_height, screen_dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, eglRender.getDecodeSurface(), null, null);
        } else {
            virtualDisplay = displayManager.createVirtualDisplay("screen", screen_width, screen_height, screen_dpi,
                    eglRender.getDecodeSurface(), DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
        }
        startRecordScreen();
        release();
    }


    /**
     * 初始化编码器
     */
    private void prepareEncoder() throws IOException {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(mime_type, screen_width, screen_height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, frame_bit);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frame_rate);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, frame_internal);
        mEncoder = MediaCodec.createEncoderByType(mime_type);
        mEncoder.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                if (!ScreenRecorderUtils.hasCodecConfigFlag(info)) {
                    muxer.writeSampleData(mVideoTrackIndex, codec.getOutputBuffer(index), info);
                }
                codec.releaseOutputBuffer(index, false);
                Log.e("MediaEncoder", "releaseOutputBuffer "+index);
            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                e.printStackTrace();
            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                mVideoTrackIndex = muxer.addTrack(codec.getOutputFormat());
                muxer.start();
                Log.e("MediaEncoder","started muxer");
            }
        });
        mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = mEncoder.createInputSurface();
        eglRender = new EGLRender(surface, screen_width, screen_height, video_fps, null);
        eglRender.setCallBack(new EGLRender.onFrameCallBack() {
            @Override
            public void onUpdate() {
//                startEncode();
            }

            @Override
            public void onCutScreen(Bitmap bitmap) {
                if (onScreenCallBack != null) {
                    onScreenCallBack.onCutScreen(bitmap);
                }
            }
        });
        mEncoder.start();
    }
    /**
     * 开始录屏
     */
    private void startRecordScreen() {
        eglRender.start();
        release();
    }

    private void startEncode() {
        ByteBuffer[] byteBuffers = null;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            byteBuffers = mEncoder.getOutputBuffers();
        }
        int index = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            resetOutputFormat();
        } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
//            Log.d("---", "retrieving buffers time out!");
//            try {
//                // wait 10ms
//                Thread.sleep(10);
//            } catch (InterruptedException e) {
//            }
        } else if (index >= 0) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                encodeToVideoTrack(byteBuffers[index]);
            } else {
                encodeToVideoTrack(mEncoder.getOutputBuffer(index));
            }
            mEncoder.releaseOutputBuffer(index, false);
        }
    }

    private void encodeToVideoTrack(ByteBuffer encodeData) {

//        ByteBuffer encodeData = mEncoder.getOutputBuffer(index);
        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
            mBufferInfo.size = 0;
        }
        if (mBufferInfo.size == 0) {
            Log.d(TAG, "info.size == 0, drop it.");
            encodeData = null;
        } else {
            Log.d(TAG, "got buffer, info: size=" + mBufferInfo.size
                    + ", presentationTimeUs=" + mBufferInfo.presentationTimeUs
                    + ", offset=" + mBufferInfo.offset);
        }
        if (encodeData != null) {
            encodeData.position(mBufferInfo.offset);
            encodeData.limit(mBufferInfo.offset + mBufferInfo.size);
            muxer.writeSampleData(mVideoTrackIndex, encodeData, mBufferInfo);//写入文件

//            byte[] bytes;
//            if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
//                //todo 关键帧上添加sps,和pps信息
//                bytes = new byte[mBufferInfo.size + sps.length + pps.length];
//                System.arraycopy(sps, 0, bytes, 0, sps.length);
//                System.arraycopy(pps, 0, bytes, sps.length, pps.length);
//                encodeData.get(bytes, sps.length + pps.length, mBufferInfo.size);
//            } else {
//                bytes = new byte[mBufferInfo.size];
//                encodeData.get(bytes, 0, mBufferInfo.size);
//            }
//            if (onScreenCallBack != null) {
//                onScreenCallBack.onScreenInfo(bytes);
//            }
            Log.e("---", "send:" + mBufferInfo.size +"\tflag:" + mBufferInfo.flags);
        }
    }

    private int mVideoTrackIndex;

    private void resetOutputFormat() {
        MediaFormat newFormat = mEncoder.getOutputFormat();
        Log.i(TAG, "output format changed.\n new format: " + newFormat.toString());
        getSpsPpsByteBuffer(newFormat);
        Log.i(TAG, "started media muxer, videoIndex=" + mVideoTrackIndex);
    }


    /**
     * 获取编码SPS和PPS信息
     * @param newFormat
     */
    private void getSpsPpsByteBuffer(MediaFormat newFormat) {
        sps = newFormat.getByteBuffer("csd-0").array();
        pps = newFormat.getByteBuffer("csd-1").array();
    }

    public void stopScreen() {
        if (eglRender != null) {
            eglRender.stop();
        }
    }

    public void release() {
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
    }
    public void cutScreen(){
        eglRender.cutScreen();
    }


    private File generateOutputFile() {
        File dir = getDir();
        if (!dir.exists()) {
            dir.mkdir();
        }
        String mOriginOutputFileName = String.valueOf(System.currentTimeMillis());
        File outputFile = new File(dir, mOriginOutputFileName + ".mp4");
        try {
            outputFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return outputFile;
    }

    private File getDir() {
//        return FilePathUtils.getSystemVideoDir();
        return new File(Environment.getExternalStorageDirectory(), "ScreenRecorder");
    }

}

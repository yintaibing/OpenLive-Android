package com.zhenai.android.utils.record_screen.biz;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.ThumbnailUtils;
import android.media.projection.MediaProjection;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Toast;

import com.zhenai.android.utils.record_screen.AudioEncodeConfig;
import com.zhenai.android.utils.record_screen.ScreenRecorder;
import com.zhenai.android.utils.record_screen.VideoEncodeConfig;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.agora.util.BitmapUtils;
import io.agora.util.FilePathUtils;
import io.agora.util.FileUtils;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

/**
 * 录屏管理类
 *
 * @author yintaibing
 */

public class RecordScreenManager implements RecordScreenLayout.OnOperationListener {
    private static final int REQUEST_CODE_RECORD_SCREEN = 81;
    public static final int REQUEST_CODE_PUBLISH_LONG_VIDEO = 82;

    private final int RESOLUTION = 576;//VideoParam.VIDEO_RESOLUTION_540P;

    private RecordScreenLayout mRecordLayout;
    private ProgressDialog mProgressDialog;
    private RecordScreenPreviewDialog mPreviewDialog;

    private ScreenRecorder mScreenRecorder;
    private String mOriginOutputFileName;
    private File mOriginOutputFile;
    private File mEditedOutputFile;
    private File mCoverImageFile;
    private VideoEncodeConfig mOriginVideoConfig;
    private LiveLongVideoConfig mEditedVideoConfig;
    private float mScaleFactor = 1f;

    private int mMinRecordSec = 5, mMaxRecordSec = 60;
    private int mCurrentRecordedSec;
    private Subscription mCountTimeSubscription;

    public RecordScreenManager(RecordScreenLayout recordScreenLayout) {
        mRecordLayout = recordScreenLayout;
        mRecordLayout.setOnOperationListener(this);
    }

    public void setRecordSecParams(int min, int max) {
        mMinRecordSec = min;
        mMaxRecordSec = max;
    }

    @Override
    public void onClickStart(Activity activity) {
        ScreenRecorder.requestRecordScreen(activity, REQUEST_CODE_RECORD_SCREEN);
    }

    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_RECORD_SCREEN) {
            // 申请录屏的MediaProject返回
            if (!ScreenRecorder.isAfterLollipop()) {
                return;
            }
            MediaProjection projection = ScreenRecorder.getMediaProjection(activity, resultCode, data);
            if (projection != null) {
                int width, height;
                DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                if (dm.widthPixels <= RESOLUTION) {
                    width = dm.widthPixels;
                    height = dm.heightPixels;
                } else {
                    width = RESOLUTION;
                    mScaleFactor = (float) width / (float) dm.widthPixels;
                    // w / dm.w = h / dm.h
                    height = (int) (dm.heightPixels * mScaleFactor);
                }
                mOriginVideoConfig = new VideoEncodeConfig(
                        width,
                        height,
                        1,
                        800000,
                        15,
                        1,
                        MediaFormat.MIMETYPE_VIDEO_AVC,
                        //"OMX.MTK.VIDEO.ENCODER.AVC",
                        "OMX.google.h264.encoder",
                        null);
                AudioEncodeConfig audioEncodeConfig = new AudioEncodeConfig(
                        512000,
                        32000,
                        1,
                        MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                        MediaFormat.MIMETYPE_AUDIO_AAC,
                        "OMX.google.aac.encoder");

                mOriginOutputFile = generateOutputFile(activity);
                mScreenRecorder = new ScreenRecorder(mOriginOutputFile);
                mScreenRecorder.addScreenStreamProvider(projection, mOriginVideoConfig);
                mScreenRecorder.addMediaStreamProvider(new AgoraAudioStreamProvider(
                        audioEncodeConfig));
                mScreenRecorder.start();

                // 更新录制栏
//                mRecordLayout.setRecordParams(mMinRecordSec, mMaxRecordSec);
                mRecordLayout.notifyRecordStarted();

                // 开始计时
                mCountTimeSubscription = Observable.interval(1L, TimeUnit.SECONDS)
                        .take(mMaxRecordSec + 1)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<Long>() {
                            @Override
                            public void onCompleted() {
                                onClickFinish(getActivity());
                            }

                            @Override
                            public void onError(Throwable e) {
                                e.printStackTrace();
                            }

                            @Override
                            public void onNext(Long aLong) {
                                if (!isRecording() || aLong == null) {
                                    return;
                                }
                                mCurrentRecordedSec = aLong.intValue() + 1;
                                if (mRecordLayout != null) {
                                    mRecordLayout.setProgress(mCurrentRecordedSec, mCurrentRecordedSec);
                                }
                            }
                        });
            }
        } else if (requestCode == REQUEST_CODE_PUBLISH_LONG_VIDEO) {
            // 发布动态页返回
            if (resultCode == Activity.RESULT_OK) {
//                ToastUtils.toast(activity, R.string.moment_publish_success_toast);
                releasePreviewDialog();
                if (mRecordLayout != null && mRecordLayout.getVisibility() == View.VISIBLE) {
                    mRecordLayout.hide();
                }
            }
        }
    }

    @Override
    public void onClickFinish(Activity activity) {
        if (!isRecording()) {
            return;
        }

        stopRecorderAndLayout();

        if (hasRecordedLongEnough()) {
            handleOriginVideoFile(activity);
        } else {
            Toast.makeText(activity, "至少需要录" + mMinRecordSec + "秒", Toast.LENGTH_SHORT).show();
            deleteFiles();
        }
    }

    @Override
    public void onClickCancel(Activity activity) {
        mCurrentRecordedSec = 0;
        stopRecorderAndLayout();
        if (mRecordLayout != null) {
            mRecordLayout.hide();
        }
        deleteFiles();
    }

    public void onActivityPause(Activity activity) {
        if (mPreviewDialog != null && mPreviewDialog.isShowing()) {
            mPreviewDialog.onPause();
        }
        if (isRecording()) {
            stopRecorderAndLayout();
            Toast.makeText(activity, "录制已停止", Toast.LENGTH_SHORT).show();
            if (!hasRecordedLongEnough()) {
                mCurrentRecordedSec = 0;
                deleteFiles();
            }
        }
    }

    public void onActivityResume(Activity activity) {
        if (mPreviewDialog != null && mPreviewDialog.isShowing()) {
            mPreviewDialog.onResume();
        }
        if (hasRecordedLongEnough()) {
            stopRecorderAndLayout();
            handleOriginVideoFile(activity);
        }
    }

    public boolean onActivityBackPress(Activity activity) {
        if (isRecording()) {
            onClickCancel(activity);
            return true;
        }
        if ((mRecordLayout != null && mRecordLayout.getVisibility() == View.VISIBLE)) {
            mRecordLayout.hide();
            return true;
        }
        return false;
    }

    public void destroy() {
        releasePreviewDialog();
        if (mProgressDialog != null) {
            if (mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            mProgressDialog = null;
        }
        if (mScreenRecorder != null) {
            mScreenRecorder.stop();
            mScreenRecorder = null;
        }
        if (mCountTimeSubscription != null && !mCountTimeSubscription.isUnsubscribed()) {
            mCountTimeSubscription.unsubscribe();
        }
        if (mRecordLayout != null) {
            mRecordLayout.setOnOperationListener(null);
            mRecordLayout = null;
        }
    }

    private void releasePreviewDialog() {
        if (mPreviewDialog != null) {
            if (mPreviewDialog.isShowing()) {
                mPreviewDialog.dismiss();
            }
            mPreviewDialog.release();
            mPreviewDialog = null;
        }
    }

    private boolean isRecording() {
        return mScreenRecorder != null && mScreenRecorder.isRecording();
    }

    private boolean hasRecordedLongEnough() {
        return mMinRecordSec > 0 && mCurrentRecordedSec >= mMinRecordSec;
    }

    private void stopRecorderAndLayout() {
        if (mScreenRecorder != null) {
            mScreenRecorder.stop();
            mScreenRecorder = null;
        }
        if (mCountTimeSubscription != null && !mCountTimeSubscription.isUnsubscribed()) {
            mCountTimeSubscription.unsubscribe();
        }
        if (mRecordLayout != null) {
            mRecordLayout.notifyRecordStopped();
            mRecordLayout.reset();
        }
    }

    private void deleteFiles() {
        FileUtils.deleteFile(mOriginOutputFile);
        FileUtils.deleteFile(mEditedOutputFile);
        FileUtils.deleteFile(mCoverImageFile);
    }

    private void handleOriginVideoFile(Context context) {
        mCurrentRecordedSec = 0;

//        mEditedOutputFile = new File(getDir(), mOriginOutputFileName + "_edit.mp4");
//        if (!mEditedOutputFile.exists()) {
//            try {
//                mEditedOutputFile.createNewFile();
//            } catch (IOException e) {
//                e.printStackTrace();
//                return;
//            }
//        }

        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        mProgressDialog = new ProgressDialog(context);
        mProgressDialog.setMessage("正在生成视频");
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setIndeterminate(false);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setMax(100);
        mProgressDialog.show();

        mEditedVideoConfig = new LiveLongVideoConfig();
        mEditedVideoConfig.width = mOriginVideoConfig.width;
        mEditedVideoConfig.height = mOriginVideoConfig.height;
        mEditedVideoConfig.videoPath = mOriginOutputFile.getPath();
//        mEditedVideoConfig.videoName = mOriginOutputFile.getName();

        // 裁剪>加水印>生成封面
//            mRecordLayout.postDelayed(new Runnable() {
//                @Override
//                public void run() {
//        cropVideo(mOriginOutputFile);
//        addWaterMask(mOriginOutputFile);
        createCover(mOriginOutputFile);
//        dismissProgress(null);
//        showPreviewDialog(getActivity(), mOriginOutputFile);
//                }
//            }, 300L);
    }

    private void cropVideo(File videoFile) {
        /*Context context = getActivity().getApplicationContext();

        int scaledStatusBarH = (int) Math.ceil(DensityUtils.getStatusBarHeight(context) * mScaleFactor);
        int scaledFooterH = (int) Math.ceil(mRecordLayout.getRecordBarHeight() * mScaleFactor);
        int outputWidth = mEditedVideoConfig.width;
        final int outputHeight = mEditedVideoConfig.height - scaledStatusBarH - scaledFooterH;

        CropParam cropParam = new CropParam.Builder()
                .inputPath(videoFile.getPath())
                .outputPath(mEditedOutputFile.getPath())
                .outputWidth(outputWidth)
                .outputHeight(outputHeight)
                .resolution(RESOLUTION)
                .cropRect(new Rect(0, scaledStatusBarH,
                        outputWidth, scaledStatusBarH + outputHeight))
                .startTime(0)
                .endTime(mCurrentRecordedSec * 1000)
                .scaleMode(VideoParam.SCALE)
                .frameRate(mOriginVideoConfig.framerate)
                .bitRate(mOriginVideoConfig.bitrate)
                .gop(VideoParam.DEFAULT_GOP)
                .quality(VideoParam.HD)
                .build();
        final ICropper cropper = new AliyunCropper(context.getApplicationContext());
        cropper.setCropParam(cropParam);
        cropper.setCropCallback(new ICropper.CropCallback() {
            @Override
            public void onProgress(int percent) {
                updateProgress("正在裁剪视频", percent);
            }

            @Override
            public void onComplete(long duration) {
                cropper.onDestroy();

                mEditedVideoConfig.height = outputHeight;
                mEditedVideoConfig.videoPath = mEditedOutputFile.getPath();
                mEditedVideoConfig.videoName = mEditedOutputFile.getName();
//                addWaterMask(mEditedOutputFile);
                createCover(mEditedOutputFile);
            }

            @Override
            public void onCancel() {
                dismissProgress("cropVideo cancel");
            }

            @Override
            public void onError(int errorCode) {
                ToastUtils.toast(getActivity(), "cropVideo error " + errorCode);
                createCover(mOriginOutputFile);
            }
        });
        cropper.startCrop();*/
    }

    private void addWaterMask(File videoFile) {
        /*final File waterMarkFile = createWaterMarkFile();
        if (waterMarkFile == null || !waterMarkFile.exists()) {
            createCover(mEditedOutputFile);
            return;
        }

        final Bitmap waterMark = BitmapFactory.decodeFile(waterMarkFile.getPath());
        final File output = mEditedOutputFile != null && mEditedOutputFile.exists() ? mEditedOutputFile :
                mOriginOutputFile;

        Context context = getActivity();
        final IEditor editor = new AliyunEditor(context);
        editor.setVideoPath(videoFile.getPath());
        SurfaceView surfaceView = new SurfaceView(context);
//        SurfaceView surfaceView = mRecordLayout.getSV();
//        surfaceView.setLayoutParams(new ViewGroup.LayoutParams(
//                editor.getVideoWidth(), editor.getVideoHeight()));
        editor.setDisplayView(surfaceView);
        editor.setPlayCallback(new IEditor.PlayCallback() {
            @Override
            public void onPrepared() {
            }

            @Override
            public void onStarted() {
                Log.e("###", "onStart");
            }

            @Override
            public void onSeekDone() {

            }

            @Override
            public void onComplete() {
                Log.e("###", "onComplete");
            }

            @Override
            public void onError(int errorCode) {
                Log.e("###", "errorCode "+errorCode);
            }
        });
        editor.onResume();

        mRecordLayout.postDelayed(new Runnable() {
            @Override
            public void run() {

                editor.startPlay();

                float videoWidth = editor.getVideoWidth();
                float videoHeight = editor.getVideoHeight();
                float bitmapWidth = waterMark.getWidth();
                float bitmapHeight = waterMark.getHeight();
                editor.setWaterMark(waterMarkFile.getPath(),
                        bitmapWidth / videoWidth, bitmapHeight / videoHeight,
                        0, 0);
                editor.startCompose(output.getPath(), new IEditor.ComposeCallback() {
                    @Override
                    public void onStart() {
                        updateProgress("正在添加水印", 0);
                    }

                    @Override
                    public void onProgress(int percent) {
                        updateProgress("正在添加水印", percent);
                    }

                    @Override
                    public void onComplete() {
                        createCover(mEditedOutputFile);
                    }

                    @Override
                    public void onCancel() {
                        dismissProgress("addWaterMask cancel");
                    }

                    @Override
                    public void onError(int errorCode) {
                        dismissProgress("addWaterMask error " + errorCode);
                    }
                });
            }
        }, 3000);*/
    }

    private File createWaterMarkFile() {
        /*File f = new File(FilePathUtils.getPhotoDir(), "live_water_mark.png");
        if (!f.exists()) {
            Bitmap waterMark = BitmapFactory.decodeResource(getActivity().getResources(),
                R.drawable.live_video_record_screen_water_mark);
            BitmapUtils.saveBitmap(f, waterMark, Bitmap.CompressFormat.PNG, 100);
        }*/
//        return f;
        return null;
    }

    private void createCover(final File videoFile) {
        Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                Bitmap thumbnail = ThumbnailUtils.createVideoThumbnail(videoFile.getPath(),
                        MediaStore.Images.Thumbnails.MINI_KIND);
                mCoverImageFile = new File(FilePathUtils.getPhotoDir(),
                        mOriginOutputFileName + ".jpg");
                if (!mCoverImageFile.exists()) {
                    try {
                        mCoverImageFile.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                }
                BitmapUtils.saveBitmap(mCoverImageFile, thumbnail);
                subscriber.onNext(mCoverImageFile.getPath());
            }
        })
                .subscribeOn(Schedulers.io())
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        updateProgress("正在生成封面", 0);
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        dismissProgress("错误" + e.getMessage());
                    }

                    @Override
                    public void onNext(String path) {
                        mEditedVideoConfig.coverPath = path;
                        updateProgress("正在生成封面", 100);

                        dismissProgress(null);
                        showPreviewDialog(getActivity(), videoFile);
                    }
                });

/*
        updateProgress("正在生成封面", 0);
        Bitmap thumbnail = ThumbnailUtils.createVideoThumbnail(videoFile.getPath(),
                MediaStore.Images.Thumbnails.MICRO_KIND);

        updateProgress("正在生成封面", 50);

        mCoverImageFile = new File(FilePathUtils.getPhotoDir(), mOriginOutputFileName + ".jpg");
        if (!mCoverImageFile.exists()) {
            try {
                mCoverImageFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        mEditedVideoConfig.coverPath = mCoverImageFile.getPath();
        BitmapUtils.saveBitmap(mCoverImageFile, thumbnail);

        updateProgress("正在生成封面", 100);
        dismissProgress(null);

        showPreviewDialog(getActivity(), mEditedOutputFile);
        */
    }

    private void showPreviewDialog(Context context, File videoFile) {
        if (videoFile != null && videoFile.exists()) {
            releasePreviewDialog();
            mPreviewDialog = new RecordScreenPreviewDialog(context);
            mPreviewDialog.setOnCancelListenerExt(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    if (mRecordLayout != null && mRecordLayout.getVisibility() == View.VISIBLE) {
                        mRecordLayout.hide();
                    }
                    mPreviewDialog = null;
                }
            });
            mPreviewDialog.setVideoConfig(mEditedVideoConfig);
            mPreviewDialog.show();
            mPreviewDialog.afterShow();

        }
    }

    private Activity getActivity() {
        return (Activity) mRecordLayout.getContext();
//        return ActivityManager.getInstance().getTopBaseActivity();
    }

    private File generateOutputFile(Context context) {
        File dir = getDir();
        if (!dir.exists()) {
            dir.mkdir();
        }
        mOriginOutputFileName = String.valueOf(System.currentTimeMillis());
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

    private void updateProgress(String msg, int progress) {
//        if (mProgressDialog == null || !mProgressDialog.isShowing()) {
//            return;
//        }
//        mProgressDialog.setProgress(progress);
//        mProgressDialog.setMessage(msg);
    }

    private void dismissProgress(String toast) {
        if (mProgressDialog != null || mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
//        if (!TextUtils.isEmpty(toast)) {
//            ToastUtils.toast(getActivity(), toast);
//        }
    }
}

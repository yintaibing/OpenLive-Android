package com.zhenai.android.utils.record_screen.biz;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.ytb.commonbackground.CommonBackground;
import com.ytb.commonbackground.CommonBackgroundFactory;

import java.io.File;

import io.agora.openlive.R;
import io.agora.openlive.ui.PublishActivity;
import io.agora.util.BitmapUtils;
import io.agora.util.DensityUtils;

public class RecordScreenPreviewDialog extends Dialog implements View.OnClickListener {
    private ViewGroup mContentView;
    private Activity mActivity;

    private TextureView mTextureView;
    private ImageView mIvCover;
    private View mViewMask;
    private Surface mSurface;
    private MediaPlayer mMediaPlayer;
    private Bitmap mDefaultCover;
    private Bitmap mVideoScreenshot;

    private LiveLongVideoConfig mConfig;
    private File mVideoFile;
    private int mCurrentPosition;

    private DialogInterface.OnCancelListener mOnCancelListener;

    public RecordScreenPreviewDialog(Context context) {
        super(context, R.style.FullscreenTheme);

        if (context instanceof Activity) {
            mActivity = (Activity) context;
        }

        mContentView = (ViewGroup) LayoutInflater.from(context).inflate(getLayoutId(), null);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        setContentView(mContentView, lp);

        init();
    }

    protected int getLayoutId() {
        return R.layout.dialog_live_video_record_screen_preview;
    }

    public <T extends View> T find(int id) {
        return (T)findViewById(id);
    }

    protected void init() {
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        mTextureView = find(R.id.video_view);
        mIvCover = find(R.id.iv_video_cover);
        ImageView ivClose = find(R.id.iv_close);
        ivClose.setOnClickListener(this);
        find(R.id.tv_save_to_local).setOnClickListener(this);
        find(R.id.tv_release_moments).setOnClickListener(this);

        mTextureView.clearFocus();
        mTextureView.setFocusable(false);
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                                  int width, int height) {
                mSurface = new Surface(surfaceTexture);
                mMediaPlayer = createMediaPlayer();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });

        Context context = getContext();
        int dp8 = DensityUtils.dp2px(context, 8f);
        int dp4 = (int) (dp8 * 0.5f);
        ivClose.setPadding(dp4, dp4, dp4, dp4);
        CommonBackgroundFactory.createStateless()
                .shape(CommonBackground.SHAPE_ROUND_RECT)
                .radius(0, dp8, 0, dp8)
                .colorFill(Color.WHITE)
                .showOn(ivClose);
        int gradientStart = Color.parseColor("#4D000000");
        int gradientEnd = Color.parseColor("#00000000");
        CommonBackgroundFactory.createStateless()
                .fillMode(CommonBackground.FILL_MODE_LINEAR_GRADIENT)
                .linearGradient(gradientStart, gradientEnd, CommonBackground.GRADIENT_ORIENTATION_TB)
                .showOn(find(R.id.shadow_view_top));
        CommonBackgroundFactory.createStateless()
                .fillMode(CommonBackground.FILL_MODE_LINEAR_GRADIENT)
                .linearGradient(gradientStart, gradientEnd, CommonBackground.GRADIENT_ORIENTATION_BT)
                .showOn(find(R.id.shadow_view_bottom));

        // VideoView.setZOrderOnTop(true)会使得VideoView遮盖关闭按钮；
        // VideoView.setZOrderOnTop(false)则会使VideoView被Dialog的变暗背景遮盖，所以得设置attrs.dimAmount = 0。
        Window window = getWindow();
        if (window != null) {
//            window.setWindowAnimations(R.style.RecordScreenPreviewDialog);

            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0;
            window.setAttributes(attrs);
        }
    }

    @Override
    public void onClick(View v) {
        Context context = mActivity;
        switch (v.getId()) {
            case R.id.iv_close:
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder
                        .setMessage("确认放弃发布？")
                        .setPositiveButton("再看看", null)
                        .setNegativeButton("残忍放弃", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (mVideoFile != null) {
                                    mVideoFile.delete();
                                }
                                RecordScreenPreviewDialog.this.dismiss();
                                if (mOnCancelListener != null) {
                                    mOnCancelListener.onCancel(dialog);
                                    mOnCancelListener = null;
                                }
                                RecordScreenPreviewDialog.this.release();
                            }
                        })
                        .create()
                        .show();
                break;

            case R.id.tv_save_to_local:
                if (mVideoFile != null) {
                    Uri uri = Uri.fromFile(mVideoFile);
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
                    context.sendBroadcast(intent);
                    mVideoFile = null;// 点close时不再删除
                }
                Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show();
                break;

            case R.id.tv_release_moments:
                Intent intent = new Intent(context, PublishActivity.class);
                context.startActivity(intent);
                break;
            default:
        }
    }

    public void setVideoConfig(LiveLongVideoConfig config) {
        mConfig = config;

        float aspect = (float) config.width / (float) config.height;
        int videoViewWidth = mTextureView.getMeasuredWidth();
        if (videoViewWidth <= 0) {
            // parentWidth - marginLeft - marginRight
            videoViewWidth = DensityUtils.dp2px(getContext(), 286 - 8 * 2);
        }
        int videoViewHeight = (int) (videoViewWidth / aspect);

        resetHeight(mTextureView, videoViewHeight);
        resetHeight(mIvCover, videoViewHeight);
        resetHeight(mViewMask, videoViewHeight);

        if (!TextUtils.isEmpty(config.coverPath)) {
            mDefaultCover = BitmapFactory.decodeFile(config.coverPath);
            mIvCover.setImageBitmap(mDefaultCover);
        }
        if (!TextUtils.isEmpty(config.videoPath)) {
            mVideoFile = new File(config.videoPath);
        }
        mMediaPlayer = createMediaPlayer();
    }

    public void afterShow() {
        Window window = getWindow();
        if (window != null) {
            // 让activity以后onResume时，本dialog出现不再展示动画
            window.setWindowAnimations(0);
        }
    }

    public void onPause() {
        if (mMediaPlayer != null) {
            mCurrentPosition = mMediaPlayer.getCurrentPosition();
            try {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.pause();
                }

                mVideoScreenshot = mTextureView.getBitmap();
                if (mVideoScreenshot != null && !mVideoScreenshot.isRecycled()) {
                    mIvCover.setImageBitmap(mVideoScreenshot);
                }
                mIvCover.setVisibility(View.VISIBLE);

                mMediaPlayer.reset();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void onResume() {
        if (mMediaPlayer != null) {
            try {
                if (!mMediaPlayer.isPlaying()) {
                    mMediaPlayer.start();
                }
                mMediaPlayer.setDataSource(mConfig.videoPath);
                mMediaPlayer.prepareAsync();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setOnCancelListenerExt(OnCancelListener onCancelListener) {
        this.mOnCancelListener = onCancelListener;
    }

    private void resetHeight(View view, int newHeight) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.height = newHeight;
        view.setLayoutParams(lp);
    }

    private MediaPlayer createMediaPlayer() {
        if (mVideoFile != null && mVideoFile.exists() && mSurface != null) {
            MediaPlayer mediaPlayer = MediaPlayer.create(mActivity, Uri.fromFile(mVideoFile));
            if (mediaPlayer != null) {
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        setLoopingAndDisableVolume(mp);
                        mp.start();
                        if (mCurrentPosition > 0) {
                            mMediaPlayer.seekTo(mCurrentPosition);
                            mCurrentPosition = 0;
                        }
                    }
                });
                mediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                    @Override
                    public boolean onInfo(MediaPlayer mp, int what, int extra) {
                        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                            mIvCover.setVisibility(View.GONE);
                            return true;
                        }
                        return false;
                    }
                });
                mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                    @Override
                    public void onSeekComplete(MediaPlayer mp) {
                        setLoopingAndDisableVolume(mp);
                        mp.start();
                    }
                });
                mediaPlayer.setSurface(mSurface);
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                return mediaPlayer;
            }
        }
        return null;
    }

    private void setLoopingAndDisableVolume(MediaPlayer mp) {
        mp.setVolume(0f, 0f);
        mp.setLooping(true);
    }

    public void release() {
        if (mMediaPlayer != null) {
            try {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                }
                mMediaPlayer.reset();
                mMediaPlayer.release();
                mMediaPlayer = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
            mMediaPlayer = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        mTextureView = null;
        mContentView.removeAllViews();
        BitmapUtils.recycle(mDefaultCover);
        BitmapUtils.recycle(mVideoScreenshot);
        mVideoScreenshot = null;
        mOnCancelListener = null;
    }
}

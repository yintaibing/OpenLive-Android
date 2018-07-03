package com.zhenai.android.utils.record_screen.biz;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class RecordScreenLayout extends LinearLayout implements View.OnClickListener {
    private Button mBtnStart;
    private Button mBtnCancel;
    private TextView mTextView;

    private boolean mRecorderStarted;

    public RecordScreenLayout(@NonNull Context context) {
        super(context);
    }

    public RecordScreenLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public RecordScreenLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mBtnStart = (Button) getChildAt(0);
        mBtnCancel = (Button) getChildAt(1);
        mTextView = (TextView) getChildAt(2);

        mBtnStart.setOnClickListener(this);
        mBtnCancel.setOnClickListener(this);
    }

    public void setProgress(int sec, int sec2) {

    }

    public void notifyRecordStarted() {
        mRecorderStarted = true;
        mBtnStart.setText("recording");
    }

    public void notifyRecordStopped() {
        mRecorderStarted = false;
        mBtnStart.setText("start");
    }

    public void hide() {
        reset();
    }

    public void reset() {
        mRecorderStarted = false;
        mBtnStart.setText("start");
        mTextView.setText("00:00");
    }

    @Override
    public void onClick(View v) {
        Activity activity = (Activity) getContext();
        if (v == mBtnStart) {
            if (mRecorderStarted) {
                mOnOperationListener.onClickFinish(activity);
            } else {
                mOnOperationListener.onClickStart(activity);
            }
        } else if (v == mBtnCancel) {
            mOnOperationListener.onClickCancel(activity);
        }
    }

    private OnOperationListener mOnOperationListener;

    public void setOnOperationListener(OnOperationListener l) {
        mOnOperationListener = l;
    }

    public interface OnOperationListener {
        void onClickStart(Activity activity);

        void onClickFinish(Activity activity);

        void onClickCancel(Activity activity);
    }
}

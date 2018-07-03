package com.zhenai.android.utils.record_screen;

import android.os.SystemClock;

public class PtsCounter {
    private long mStartTimeNano;

    public boolean isFirstFrame() {
        return mStartTimeNano == 0L;
    }

    public void onFirstFrame() {
        mStartTimeNano = SystemClock.elapsedRealtimeNanos();
    }

    public long newPts() {
        return (SystemClock.elapsedRealtimeNanos() - mStartTimeNano) / 1000;
    }
}

package com.zhenai.android.utils.record_screen;

public class PtsCounter {
    PtsCounter(long startNano) {
        mStartNano = startNano;
    }

    private volatile long mStartNano;
    private volatile long mFirstNano;
    private volatile long mPrevNano;

    long newPts() {
        long nowNano = System.nanoTime();
        long result;

        if (mFirstNano == 0L) {
            mFirstNano = nowNano;
            result = 0L;
            return mPrevNano = result;
        } else {
            result = nowNano - mFirstNano;
        }

        result /= 1000L;

        if (result < mPrevNano) {
            result = (mPrevNano - result) + result;
        }
        if (mPrevNano > 0L && result == mPrevNano) {
            result += 100L;
        }

        return mPrevNano = result;
    }
}

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

        if (mStartNano == 0L) {
            result = nowNano;
        } else {
            if (mFirstNano == 0L) {
                mFirstNano = nowNano;
            }
            long elapsedNano = nowNano - mFirstNano;
            result = mStartNano + elapsedNano;
        }

        result /= 1000L;

        if (result < mPrevNano) {
            result = (mPrevNano - result) + result;
        }
        if (result == mPrevNano) {
            result += 100L;
        }

        return mPrevNano = result;
    }
}

package com.zhenai.android.utils.record_screen;

public class PresentationTimeCounter {
    PresentationTimeCounter(long baseElapsedMs) {
        mBaseElapsedMs = baseElapsedMs;
    }

    private long mBaseElapsedMs;// （其他流）已经逝去的时长
    private volatile long mFirstNano;// 此流第一帧的时间戳
    private volatile long mPrevPts;// 此流上一帧的时间戳

    public synchronized long newPresentationTimeUs() {
        long nowNano = System.nanoTime();
        long result;

        if (mFirstNano == 0L) {
            mFirstNano = nowNano;
            result = 0L;
        } else {
            result = (nowNano - mFirstNano) / 1000L;
        }
        result += mBaseElapsedMs;

        if (result > 0L && result <= mPrevPts) {
            result = mPrevPts + 1L;
        }
        return mPrevPts = result;
    }

//    public long getPrevPresentationTimeUs() {
//        return mPrevPts;
//    }
}

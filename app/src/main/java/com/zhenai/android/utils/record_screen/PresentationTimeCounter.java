package com.zhenai.android.utils.record_screen;

public class PresentationTimeCounter {
    PresentationTimeCounter(long baseElapsedMs) {
        mBaseElapsedMs = baseElapsedMs;
    }

    private long mBaseElapsedMs;// （其他流）已经逝去的时长
    private volatile long mFirstNano;// 此流第一帧的时间戳
    private volatile long mPrevPts;// 此流上一帧的时间戳

    private volatile long mPrevVideoPts, mPrevAudioPts;

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
            result = mPrevPts + 100L;
        }
        return mPrevPts = result;
    }

    public synchronized long newVideoPts() {
        if (mPrevVideoPts == 0L) {
            mPrevVideoPts = 66666;
            return 0L;
        }

        long r;
        if (mPrevVideoPts < mPrevAudioPts) {
            if (mPrevAudioPts - mPrevVideoPts < 66666) {
                r = mPrevVideoPts + 66666;
            } else {
                r = mPrevAudioPts;
            }
        } else {
            r = mPrevVideoPts + 66666;
        }
        return mPrevVideoPts = r;
    }

    public synchronized long newAudioPts() {
        if (mPrevAudioPts == 0L) {
            mPrevAudioPts = 24000;
            return 0L;
        }
        return mPrevAudioPts += 24000;
    }
}

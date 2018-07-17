package com.zhenai.android.utils.record_screen;

public class PresentationTimeCounter {
    private final int mVideoFrameDuration;
    private final int mAudioSampleRate;

    private volatile long mFirstFrameNano = -1L;

    private volatile long mPrevVideoPts = -1L, mPrevAudioPts = -1L;

    PresentationTimeCounter(int videoFrameRate, int audioSampleRate) {
        mVideoFrameDuration = 1000_000 / videoFrameRate;
        mAudioSampleRate = audioSampleRate;
    }

    public synchronized long newVideoPts() {
        checkFirstFrame();

        if (mPrevVideoPts < 0L) {
            return mPrevVideoPts = 0L;
        }
        mPrevVideoPts = checkCurrentPts(mPrevVideoPts + mVideoFrameDuration,
                mPrevVideoPts, mVideoFrameDuration);
        return mPrevVideoPts;
    }

    public synchronized long newVideoPts2() {
        checkFirstFrame();

        if (mPrevVideoPts < 0L) {
            // first frame
            return mPrevVideoPts = 0L;
        }

        long newPts;
        if (mPrevVideoPts < mPrevAudioPts) {
            if (mPrevAudioPts - mPrevVideoPts < mVideoFrameDuration) {
                newPts = mPrevVideoPts + mVideoFrameDuration;
            } else {
                newPts = mPrevAudioPts;
            }
        } else {
            newPts = mPrevVideoPts + mVideoFrameDuration;
        }
        return mPrevVideoPts = newPts;
    }

    public synchronized long newAudioPts(int frameSize) {
        checkFirstFrame();

        if (mPrevAudioPts < 0L) {
            return mPrevAudioPts = 0L;
        }
        long frameDuration = frameSize * 1000 / mAudioSampleRate;
        mPrevAudioPts = checkCurrentPts(mPrevAudioPts + frameDuration,
                mPrevAudioPts, frameDuration);
        return mPrevAudioPts;
    }

    public synchronized long newAudioPts2(int frameSize) {
        checkFirstFrame();

        if (mPrevAudioPts < 0L) {
            // first frame
            return mPrevAudioPts = 0L;
        }

        long frameDuration = frameSize * 1000 / mAudioSampleRate;
        long newPts;
        if (mPrevAudioPts < mPrevVideoPts) {
            if (mPrevVideoPts - mPrevAudioPts < frameDuration) {
                newPts = mPrevAudioPts + frameDuration;
            } else {
                newPts = mPrevVideoPts;
            }
        } else {
            newPts = mPrevAudioPts + frameDuration;
        }
        return mPrevAudioPts = newPts;
    }

    private void checkFirstFrame() {
        if (mFirstFrameNano < 0L) {
            mFirstFrameNano = System.nanoTime();
        }
    }

    private long checkCurrentPts(long pts, long prevPts, long frameDuration) {
        long nowNano = System.nanoTime();
        long currentPts = (nowNano - mFirstFrameNano) / 1000L;

        if (pts < currentPts) {
            // 落后
            if (currentPts - pts >= frameDuration) {
                // 落后过大，跳至当前
                return currentPts;
            }
        } else if (pts > currentPts) {
            // 超前
            if (pts - currentPts > frameDuration) {
                return Math.min(pts, currentPts + frameDuration);
            }
        }
        return pts;
    }

    public long getPrevVideoPts() {
        return mPrevVideoPts;
    }

    public long getPrevAudioPts() {
        return mPrevAudioPts;
    }

    public void setPrevVideoPts(long mPrevVideoPts) {
        this.mPrevVideoPts = mPrevVideoPts;
    }

    public void setPrevAudioPts(long mPrevAudioPts) {
        this.mPrevAudioPts = mPrevAudioPts;
    }
}

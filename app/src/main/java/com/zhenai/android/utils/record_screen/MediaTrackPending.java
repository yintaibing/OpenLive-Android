package com.zhenai.android.utils.record_screen;

import android.media.MediaCodec;
import android.util.Log;

import java.util.LinkedList;

public class MediaTrackPending {
    public MediaCodec mMediaCodec;
    public int mTrack;
    public LinkedList<Integer> mPendingBufferIndices = new LinkedList<>();
    public LinkedList<MediaCodec.BufferInfo> mPendingBufferInfos = new LinkedList<>();

    public void addPending(int outputIndex, MediaCodec.BufferInfo bufferInfo) {
        mPendingBufferIndices.add(outputIndex);
        mPendingBufferInfos.add(bufferInfo);
    }

    public void drain(MediaMuxerWrapper muxer) {
        Log.e("MuxerWrapper", "track " + mTrack + " start drain 1");
        if (mMediaCodec != null) {
            int outputIndex;
            MediaCodec.BufferInfo bufferInfo;
            Log.e("MuxerWrapper", "track " + mTrack + " start drain 2");
            while ((bufferInfo = mPendingBufferInfos.poll()) != null) {
                outputIndex = mPendingBufferIndices.poll();
                muxer.writeSampleDataJust(mTrack, mMediaCodec.getOutputBuffer(outputIndex), bufferInfo);
                mMediaCodec.releaseOutputBuffer(outputIndex, false);
                Log.e("MuxerWrapper", "track " + mTrack + "drain outputIndex="+outputIndex);
            }
        }
    }
}

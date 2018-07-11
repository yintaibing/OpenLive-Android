package com.zhenai.android.utils.record_screen;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.os.Build;
import android.util.Log;

import java.util.LinkedList;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MediaTrackPending {
    MediaStreamProvider mProvider;
    String mProviderName;
    int mTrack;
    LinkedList<Integer> mPendingBufferIndices = new LinkedList<>();
    LinkedList<MediaCodec.BufferInfo> mPendingBufferInfos = new LinkedList<>();

    public MediaTrackPending(MediaStreamProvider provider) {
        mProvider = provider;
        mProviderName = provider.getClass().getSimpleName();
    }

    public void addPending(int outputIndex, MediaCodec.BufferInfo bufferInfo) {
        mPendingBufferIndices.add(outputIndex);
        mPendingBufferInfos.add(bufferInfo);
        Log.e("MediaTrackPending", mProviderName + " addPending outputIndex=" + outputIndex);
    }

    public void setTrack(int track) {
        mTrack = track;
    }

    public void clear() {
        mProvider = null;
        mProviderName = null;
        mPendingBufferInfos.clear();
        mPendingBufferInfos = null;
        mPendingBufferIndices.clear();
        mPendingBufferIndices = null;
    }
}

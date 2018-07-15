package com.zhenai.android.utils.record_screen;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.os.Build;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.LinkedList;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MediaTrackPending {
    private int mTrack;
    private LinkedList<ByteBuffer> mPendingBuffers;
    private LinkedList<MediaCodec.BufferInfo> mPendingBufferInfos;
    private long mTotalSize;
    private long mTotalCapacity;

    public MediaTrackPending(int track) {
        setTrack(track);
        mPendingBuffers = new LinkedList<>();
        mPendingBufferInfos = new LinkedList<>();
    }

    public void addPending(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        mPendingBuffers.add(byteBuffer);
        mPendingBufferInfos.add(bufferInfo);
        mTotalCapacity += byteBuffer.capacity();
        mTotalSize += bufferInfo.size;
    }

    public void printTotalCapacityAndSize() {
        Log.e("MediaTrackPending", "track=" + mTrack +
                " num=" + mPendingBuffers.size() +
                " totalCapacity=" + mTotalCapacity +
                " totalSize=" + mTotalSize);
    }

    public void setTrack(int track) {
        mTrack = track;
    }

    public int getTrack() {
        return mTrack;
    }

    public LinkedList<ByteBuffer> getPendingBuffers() {
        return mPendingBuffers;
    }

    public LinkedList<MediaCodec.BufferInfo> getPendingBufferInfos() {
        return mPendingBufferInfos;
    }

    public void clear() {
        mPendingBufferInfos.clear();
        mPendingBufferInfos = null;
        mPendingBuffers.clear();
        mPendingBuffers = null;
    }
}

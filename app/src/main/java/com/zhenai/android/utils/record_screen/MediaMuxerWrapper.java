package com.zhenai.android.utils.record_screen;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.util.SparseArray;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaMuxerWrapper {
    private static final String TAG = "MediaMuxerWrapper";

    private MediaMuxer mMuxer;
    private final int mRequiredTrackCount;
    private volatile int mAddedTrackCount;
    private volatile boolean mMuxerStarted;
//    private volatile long mStartWriteTime = 0L;
    private PtsCounter ptsCounter;

    private SparseArray<MediaTrackPending> mPendings;

    public MediaMuxerWrapper(File outputFile, int requiredTrackCount) throws IOException {
        mMuxer = new MediaMuxer(outputFile.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        mRequiredTrackCount = requiredTrackCount;
        mPendings = new SparseArray<>(2);
        ptsCounter = new PtsCounter();
    }

    public synchronized int addTrack(MediaStreamProvider provider, MediaFormat mediaFormat) {
        int track = mMuxer.addTrack(mediaFormat);
        if (track >= 0) {
            mAddedTrackCount++;

            MediaTrackPending pending = mPendings.get(provider.hashCode());
            if (pending != null) {
                pending.mTrack = track;
                pending.mMediaCodec = provider.getMediaCodec();
            }
        }

        Log.e(TAG, "added track " + track +" format="+mediaFormat.toString());

        if (mAddedTrackCount >= mRequiredTrackCount) {
            mMuxer.start();
            mMuxerStarted = true;
            Log.e(TAG, "started muxer");

            int pendingSize = mPendings.size();
            for (int i = 0; i < pendingSize; i++) {
                int key = mPendings.keyAt(i);
                MediaTrackPending pending = mPendings.get(key);
                pending.drain(this);
            }
        }

        return track;
    }

    public void writeSampleData(MediaStreamProvider provider, int outputIndex, MediaCodec.BufferInfo bufferInfo) {
        if (ptsCounter.isFirstFrame()) {
            ptsCounter.onFirstFrame();
            bufferInfo.presentationTimeUs = 0L;
        } else {
            bufferInfo.presentationTimeUs = ptsCounter.newPts();
        }

        int track = provider.getMuxerTrackIndex();
        if (track >= 0 && mMuxerStarted) {
            MediaCodec codec = provider.getMediaCodec();
            if (codec != null) {
                writeSampleDataJust(track, codec.getOutputBuffer(outputIndex), bufferInfo);
                codec.releaseOutputBuffer(outputIndex, false);
            }
        } else if (mAddedTrackCount > 0) {
            int key = provider.hashCode();
            MediaTrackPending pending = mPendings.get(key);
            if (pending == null) {
                pending = new MediaTrackPending();
                pending.mTrack = provider. getMuxerTrackIndex();
                pending.mMediaCodec = provider.getMediaCodec();
                mPendings.put(key, pending);
            }
            pending.addPending(outputIndex, bufferInfo);
            Log.e("MuxerWrapper", provider.getClass().getSimpleName() + " addPending outputIndex="+outputIndex);
        }
    }

    public void writeSampleDataJust(int track, ByteBuffer byteBuffer,
                                    MediaCodec.BufferInfo bufferInfo) {
        if (track >= 0 && byteBuffer != null) {
            byteBuffer.position(bufferInfo.offset);
            byteBuffer.limit(bufferInfo.offset + bufferInfo.size);
            mMuxer.writeSampleData(track, byteBuffer, bufferInfo);
        }
    }

    public synchronized void stop() {
        if (mMuxerStarted) {
            mMuxerStarted = false;
            if (mMuxer != null) {
                try {
                    mMuxer.stop();
                    mMuxer.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            mMuxer = null;
        }
        mAddedTrackCount = 0;
    }
}

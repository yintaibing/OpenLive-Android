package com.zhenai.android.utils.record_screen;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;
import android.util.SparseArray;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MediaMuxerWrapper {
    private static final String TAG = MediaMuxerWrapper.class.getSimpleName();

    private MediaMuxer mMuxer;
    private final int mRequiredTrackCount;
    private volatile int mAddedTrackCount;
    private volatile boolean mMuxerStarted;
    private PtsCounter ptsCounter;

    private SparseArray<MediaTrackPending> mPendings = new SparseArray<>(2);

    public MediaMuxerWrapper(File outputFile, int requiredTrackCount) throws IOException {
        mMuxer = new MediaMuxer(outputFile.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        mRequiredTrackCount = requiredTrackCount;
        ptsCounter = new PtsCounter(0);
    }

    public synchronized int addTrack(MediaStreamProvider provider, MediaFormat mediaFormat) {
        int track = mMuxer.addTrack(mediaFormat);
        if (track >= 0) {
            mAddedTrackCount++;

            MediaTrackPending pending = getTrackPending(provider);
            if (pending != null) {
                pending.setTrack(track);
            }
        }

        Log.e(TAG, "added track " + track +" format="+mediaFormat.toString());

        if (mAddedTrackCount >= mRequiredTrackCount) {
            mMuxer.start();
            mMuxerStarted = true;
            Log.e(TAG, "started muxer");

            flushCache();
        }

        return track;
    }

    public void writeSampleData(MediaStreamProvider provider,
                                int outputIndex,
                                MediaCodec.BufferInfo bufferInfo) {
        if (mMuxerStarted) {
            // 所有流都ready了，写文件
            MediaCodec codec = provider.getMediaCodec();
            if (codec != null) {
                int track = provider.getMuxerTrackIndex();
                ByteBuffer byteBuffer = codec.getOutputBuffer(outputIndex);
                if (track >= 0 && byteBuffer != null) {
                    byteBuffer.position(bufferInfo.offset)
                            .limit(bufferInfo.offset + bufferInfo.size);
                    bufferInfo.presentationTimeUs=ptsCounter.newPts();
                    mMuxer.writeSampleData(track, byteBuffer, bufferInfo);
                }
                codec.releaseOutputBuffer(outputIndex, false);
            }
        } else if (mAddedTrackCount > 0) {
            // 有流还未ready，先缓存
            cache(provider, outputIndex, bufferInfo);
        }
    }

    private void cache(MediaStreamProvider provider,
                       int outputIndex,
                       MediaCodec.BufferInfo bufferInfo) {
        MediaTrackPending pending = getTrackPending(provider);
        if (pending == null) {
            pending = new MediaTrackPending(provider);
            mPendings.put(provider.hashCode(), pending);
        }
        pending.addPending(outputIndex, bufferInfo);
    }

    private void flushCache() {
        MediaTrackPending pending;
        LinkedList<Integer> indices;
        LinkedList<MediaCodec.BufferInfo> infos;
        MediaCodec.BufferInfo bufferInfo;

        int size = mPendings.size();
        for (int i = 0; i < size; i++) {
            pending = mPendings.get(mPendings.keyAt(i));
            if (pending != null) {
                indices = pending.mPendingBufferIndices;
                infos = pending.mPendingBufferInfos;

                while ((bufferInfo = infos.poll()) != null) {
                    int outputIndex = indices.poll();
                    writeSampleData(pending.mProvider, outputIndex, bufferInfo);
                }

                pending.clear();
            }
        }
        mPendings.clear();
    }

    private MediaTrackPending getTrackPending(MediaStreamProvider provider) {
        return mPendings.get(provider.hashCode());
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
                mPendings.clear();
            }
            mMuxer = null;
        }
        mAddedTrackCount = 0;
    }
}

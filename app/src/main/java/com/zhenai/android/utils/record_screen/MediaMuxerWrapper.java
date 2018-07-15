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
    private int mVideoTrack = -1, mAudioTrack = -1;
    private long mPrevVideoPts, mPrevAudioPts;
    private int mRequiredTrackCount;
    private volatile int mAddedTrackCount;
    private volatile boolean mMuxerStarted;

    private PresentationTimeCounter mCommonPtsCounter;
    private PresentationTimeCounter mVideoPtsCounter;
    private PresentationTimeCounter mAudioPtsCounter;

    private SparseArray<MediaTrackPending> mPendings = new SparseArray<>(2);

    public MediaMuxerWrapper(File outputFile, boolean hasVideo, boolean hasAudio) throws IOException {
        mMuxer = new MediaMuxer(outputFile.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        if (hasVideo) {
            mRequiredTrackCount++;
        }
        if (hasAudio) {
            mRequiredTrackCount++;
        }
    }

    public int addTrack(MediaFormat codecOutputFormat, boolean isVideo) {
        synchronized (this) {
            int track = mMuxer.addTrack(codecOutputFormat);
            if (track >= 0) {
                mAddedTrackCount++;

                if (isVideo) {
                    mVideoTrack = track;
                } else {
                    mAudioTrack = track;
                }

                MediaTrackPending pending = getPendingByTrack(track);
                if (pending != null) {
                    pending.setTrack(track);
                }
            }

            Log.e(TAG, "added track " + track + " format=" + codecOutputFormat.toString());

            if (mAddedTrackCount >= mRequiredTrackCount) {
                mMuxer.start();
                mMuxerStarted = true;
                Log.e(TAG, "started muxer");

                if (mStateCallback != null) {
                    mStateCallback.onStart();
                }

                flushCache();
            }

            return track;
        }
    }

    public void writeSampleData(int track,
                                ByteBuffer byteBuffer,
                                MediaCodec.BufferInfo bufferInfo,
                                boolean isVideo,
                                boolean resetPresentationTime) {
        if (mMuxerStarted) {
            // 所有流都ready了，直接写文件
            if (resetPresentationTime) {
                // 有些流需要重置presentationTimeUs（如声网的音频流，有时到得比较晚）
                bufferInfo.presentationTimeUs = newPresentationTime(isVideo);
            }

            if (isVideo) {
                mPrevVideoPts = Math.max(mPrevVideoPts, bufferInfo.presentationTimeUs);
            } else {
                mPrevAudioPts = Math.max(mPrevAudioPts, bufferInfo.presentationTimeUs);
            }

            doWriteSampleData(track, byteBuffer, bufferInfo);
        } else if (mAddedTrackCount > 0) {
            // 有流还未ready，copy一份数据缓存起来，返回true让codec释放buffer继续运行
            ByteBuffer tempByteBuffer = ByteBuffer.allocate(byteBuffer.capacity());
            tempByteBuffer.put(byteBuffer);

            MediaCodec.BufferInfo tempBufferInfo = new MediaCodec.BufferInfo();
            tempBufferInfo.set(bufferInfo.offset, bufferInfo.size,
                    bufferInfo.presentationTimeUs, bufferInfo.flags);
            if (resetPresentationTime) {
                tempBufferInfo.presentationTimeUs = newPresentationTime(isVideo);
            }

            if (isVideo) {
                mPrevVideoPts = Math.max(mPrevVideoPts, tempBufferInfo.presentationTimeUs);
            } else {
                mPrevAudioPts = Math.max(mPrevAudioPts, tempBufferInfo.presentationTimeUs);
            }

            cache(track, tempByteBuffer, tempBufferInfo);
        }
    }

    public void writeEndOfStream() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(0);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        bufferInfo.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;

        if (mVideoTrack >= 0) {
            bufferInfo.presentationTimeUs = mPrevVideoPts + 100L;
            doWriteSampleData(mVideoTrack, byteBuffer, bufferInfo);
        }
        if (mAudioTrack >= 0) {
            bufferInfo.presentationTimeUs =mPrevAudioPts + 100L;
            doWriteSampleData(mAudioTrack, byteBuffer, bufferInfo);
        }
    }

    private void doWriteSampleData(int track,
                                   ByteBuffer byteBuffer,
                                   MediaCodec.BufferInfo bufferInfo) {
        synchronized (this) {
            if (track >= 0 && byteBuffer != null) {
                Log.e(TAG, "write track=" + track +
                        " pts=" + bufferInfo.presentationTimeUs +
                        " flags=" + bufferInfo.flags);
                byteBuffer.position(bufferInfo.offset)
                        .limit(bufferInfo.size + bufferInfo.offset);
                mMuxer.writeSampleData(track, byteBuffer, bufferInfo);
            }
        }
    }

    private void cache(int track,
                       ByteBuffer byteBuffer,
                       MediaCodec.BufferInfo bufferInfo) {
        MediaTrackPending pending = getPendingByTrack(track);
        if (pending == null) {
            pending = new MediaTrackPending(track);
            mPendings.put(track, pending);
        }
        pending.addPending(byteBuffer, bufferInfo);
        Log.e(TAG, "cache track="+track);
    }

    private void flushCache() {
        MediaTrackPending pending;
        int track;
        LinkedList<ByteBuffer> buffers;
        LinkedList<MediaCodec.BufferInfo> infos;
        ByteBuffer byteBuffer;
        MediaCodec.BufferInfo bufferInfo;

        int size = mPendings.size();
        for (int i = 0; i < size; i++) {
            pending = mPendings.get(mPendings.keyAt(i));
            if (pending != null) {
                pending.printTotalCapacityAndSize();

                track = pending.getTrack();
                buffers = pending.getPendingBuffers();
                infos = pending.getPendingBufferInfos();

                while ((byteBuffer = buffers.poll()) != null) {
                    bufferInfo = infos.poll();
                    Log.e(TAG, "flushCache track=" + track);
                    doWriteSampleData(track, byteBuffer, bufferInfo);
                }

                pending.clear();
            }
        }
        mPendings.clear();
    }

    private MediaTrackPending getPendingByTrack(int track) {
        return mPendings.get(track);
    }

    private long newPresentationTime(boolean isVideo) {
//        if (mCommonPtsCounter == null) {
//            mCommonPtsCounter = new PresentationTimeCounter(0);
//        }
//        return mCommonPtsCounter.newPresentationTimeUs();

        if (isVideo) {
            if (mVideoPtsCounter == null) {
                mVideoPtsCounter = new PresentationTimeCounter(0);
            }
            return mVideoPtsCounter.newPresentationTimeUs();
        } else {
            if (mAudioPtsCounter == null) {
                mAudioPtsCounter = new PresentationTimeCounter(0);
            }
            return mAudioPtsCounter.newPresentationTimeUs();
        }
    }

    public void stop() {
        synchronized (this) {
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

            if (mStateCallback != null) {
                mStateCallback.onStop();
                mStateCallback = null;
            }
        }
    }

    private StateCallback mStateCallback;

    public void setStateCallback(StateCallback mStateCallback) {
        this.mStateCallback = mStateCallback;
    }

    public interface StateCallback {
        void onStart();
        void onStop();
    }
}

package com.zhenai.android.utils.record_screen.gl;

import android.opengl.GLES20;

import java.nio.FloatBuffer;

public abstract class BaseTextureRender {
    protected static final int SIZE_OF_FLOAT = 4;

    protected FloatBuffer mRectBuffer;
    protected FloatBuffer mRectTexBuffer;
    protected float[] mMVPMatrix;
    protected float[] mSTMatrix;

    protected int mProgram;
    protected int mTextureID = -1;
    protected int muMVPMatrixHandle;
    protected int muSTMatrixHandle;
    protected int maPositionHandle;
    protected int maTextureHandle;

    protected int mOriginWidth;
    protected int mOriginHeight;

    public BaseTextureRender(int originWidth, int originHeight) {
        mOriginWidth = originWidth;
        mOriginHeight = originHeight;
    }

    public int getTextureID() {
        return mTextureID;
    }

    public abstract void drawFrame();

    public void destroy() {
        if (mProgram > 0) {
            GLES20.glDeleteProgram(mProgram);
            mProgram = 0;
        }
        if (mTextureID > 0) {
            GLES20.glDeleteTextures(1, new int[]{mTextureID}, 0);
            mTextureID = 0;
        }
    }
}

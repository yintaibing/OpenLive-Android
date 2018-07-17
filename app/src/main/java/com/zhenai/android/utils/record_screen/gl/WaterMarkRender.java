package com.zhenai.android.utils.record_screen.gl;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import com.zhenai.android.utils.record_screen.GlUtil;

/**
 * Created by guoheng on 2016/8/31.
 */
public class WaterMarkRender extends BaseTextureRender {
    private final int VERTEX_NUM = 4;
    private final int GL_TEXTURE_TARGET = GLES20.GL_TEXTURE_2D;

    private final float FULL_RECTANGLE_COORDS[] = {
            -1.0f, -1.0f,//1.0f,   // 0 bottom left
            1.0f, -1.0f,//1.0f,   // 1 bottom right
            -1.0f,  1.0f,//1.0f,   // 2 top left
            1.0f,  1.0f,//1.0f   // 3 top right
    };

    private final float FULL_RECTANGLE_TEX_COORDS[] = {
            0.0f, 1.0f, //1f,1.0f,    // 0 bottom left
            1.0f, 1.0f,//1f,1.0f,     // 1 bottom right
            0.0f, 0.0f, //1f,1.0f,    // 2 top left
            1.0f, 0.0f ,//1f,1.0f     // 3 top right
    };

    private final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
//                    "uniform mat4 uSTMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * aPosition;\n" +
                    "    vTextureCoord = aTextureCoord.xy;\n" +
                    "}\n";

    private final String FRAGMENT_SHADER =
//            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +      // highp here doesn't seem to matter
                    "varying vec2 vTextureCoord;\n" +
                    "uniform sampler2D sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord);" +
                    "}\n";

    private Bitmap mBitmap;
    private int mWaterMarkX, mWaterMarkY;

    public WaterMarkRender(int originWidth, int originHeight, Bitmap bitmap,
                           int waterMarkX, int waterMarkY) {
        super(originWidth, originHeight);
        mBitmap = bitmap;
        mWaterMarkX = waterMarkX;
        mWaterMarkY = waterMarkY;

        mRectBuffer = GlUtil.createFloatBuffer(FULL_RECTANGLE_COORDS);
        mRectTexBuffer = GlUtil.createFloatBuffer(FULL_RECTANGLE_TEX_COORDS);

        mMVPMatrix = new float[16];
        Matrix.setIdentityM(mMVPMatrix, 0);
        mSTMatrix = new float[16];
        Matrix.setIdentityM(mSTMatrix, 0);

        mProgram = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (mProgram == 0) {
            throw new RuntimeException("failed creating program");
        }

        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
//        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");

        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        mTextureID = tex[0];
        GLES20.glBindTexture(GL_TEXTURE_TARGET, tex[0]);
        GLES20.glTexParameteri(GL_TEXTURE_TARGET,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_TARGET,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_TARGET,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GL_TEXTURE_TARGET,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mBitmap, 0);
        GLES20.glBindTexture(GL_TEXTURE_TARGET, 0);
    }

    /**
     * Draws the external texture in SurfaceTexture onto the current EGL surface.
     */
    @Override
    public void drawFrame() {
        GLES20.glUseProgram(mProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_TARGET, mTextureID);

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionHandle);

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionHandle, 2,
                GLES20.GL_FLOAT, false, 2 * SIZE_OF_FLOAT,
                mRectBuffer);

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureHandle);

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureHandle, 2,
                GLES20.GL_FLOAT, false, 2 * SIZE_OF_FLOAT,
                mRectTexBuffer);

        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
//        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);

        // set viewport
        GLES20.glViewport(mWaterMarkX, mWaterMarkY, mBitmap.getWidth(), mBitmap.getHeight());
        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        // resume viewport
        GLES20.glViewport(0, 0, mOriginWidth, mOriginHeight);

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionHandle);
        GLES20.glDisableVertexAttribArray(maTextureHandle);
        GLES20.glBindTexture(GL_TEXTURE_TARGET, 0);
        GLES20.glUseProgram(0);
    }
}

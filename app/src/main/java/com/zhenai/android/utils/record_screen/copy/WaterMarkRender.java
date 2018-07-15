package com.zhenai.android.utils.record_screen.copy;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import java.nio.FloatBuffer;

import io.agora.openlive.AGApplication;
import io.agora.openlive.R;

/**
 * Created by guoheng on 2016/8/31.
 */
public  class WaterMarkRender {
    private static final int FLOAT_SIZE_BYTES = 4;
    private static final String TAG = "STextureRendering";


    private static final int GL_TEXTURE_TARGET = GLES20.GL_TEXTURE_2D;


    private static final float FULL_RECTANGLE_COORDS[] = {
            -1.0f, -1.0f,//1.0f,   // 0 bottom left
            1.0f, -1.0f,//1.0f,   // 1 bottom right
            -1.0f,  1.0f,//1.0f,   // 2 top left
            1.0f,  1.0f,//1.0f   // 3 top right
    };

    private static final float FULL_RECTANGLE_TEX_COORDS[] = {
            0.0f, 1.0f, //1f,1.0f,    // 0 bottom left
            1.0f, 1.0f,//1f,1.0f,     // 1 bottom right
            0.0f, 0.0f, //1f,1.0f,    // 2 top left
            1.0f, 0.0f ,//1f,1.0f     // 3 top right
    };

    private static final FloatBuffer FULL_RECTANGLE_BUF =
            GlUtil.createFloatBuffer(FULL_RECTANGLE_COORDS);
    private static final FloatBuffer FULL_RECTANGLE_TEX_BUF =
            GlUtil.createFloatBuffer(FULL_RECTANGLE_TEX_COORDS);


    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
//                    "uniform mat4 uSTMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * aPosition;\n" +
                    "    vTextureCoord = aTextureCoord.xy;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER =
//            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +      // highp here doesn't seem to matter
                    "varying vec2 vTextureCoord;\n" +
                    "uniform sampler2D sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord);" +
                    "}\n";




    private float[] mMVPMatrix = new float[16];
    private float[] mSTMatrix = new float[16];

    private int mProgram;
    private int mTextureID = -12345;
    private int muMVPMatrixHandle;
    private int muSTMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;
    private int mWidth;
    private int mHeight;
    private Bitmap mBitmap;

    public WaterMarkRender(int mwidth, int mHeight) {
        this();
        this.mWidth = mwidth;
        this.mHeight = mHeight;
    }

    public WaterMarkRender() {
        Matrix.setIdentityM(mSTMatrix, 0);
    }

    public int getTextureId() {
        return mTextureID;
    }



    /**
     * Initializes GL state.  Call this after the EGL surface has been created and made current.
     */
    public void surfaceCreated() {
        mProgram = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (mProgram == 0) {
            throw new RuntimeException("failed creating program");
        }


        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
//        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");


        mTextureID = initTex();

//        GLES20.glTexParameterf(GL_TEXTURE_TARGET, GLES20.GL_TEXTURE_MIN_FILTER,
//                GLES20.GL_NEAREST);
//        GLES20.glTexParameterf(GL_TEXTURE_TARGET, GLES20.GL_TEXTURE_MAG_FILTER,
//                GLES20.GL_LINEAR);
//        GLES20.glTexParameteri(GL_TEXTURE_TARGET, GLES20.GL_TEXTURE_WRAP_S,
//                GLES20.GL_CLAMP_TO_EDGE);
//        GLES20.glTexParameteri(GL_TEXTURE_TARGET, GLES20.GL_TEXTURE_WRAP_T,
//                GLES20.GL_CLAMP_TO_EDGE);
    }
    /**
     * create external texture
     *
     * @return texture ID
     */
    public int initTex() {
        mBitmap = BitmapFactory.decodeResource(AGApplication.instance.getResources(),
                R.drawable.live_video_record_screen_water_mark);

        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_TARGET,tex[0]);
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

        return tex[0];
    }


    /**
     * Draws the external texture in SurfaceTexture onto the current EGL surface.
     */
    public void drawFrame() {
        GLES20.glUseProgram(mProgram);


        GLES20.glBindTexture(GL_TEXTURE_TARGET, mTextureID);

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionHandle);

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionHandle, 2,
                GLES20.GL_FLOAT, false, 2*FLOAT_SIZE_BYTES, FULL_RECTANGLE_BUF);

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureHandle);

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureHandle, 2,
                GLES20.GL_FLOAT, false, 2*FLOAT_SIZE_BYTES, FULL_RECTANGLE_TEX_BUF);

        Matrix.setIdentityM(mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
//        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);


        GLES20.glViewport(0, 300, mBitmap.getWidth(), mBitmap.getHeight());
        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionHandle);
        GLES20.glDisableVertexAttribArray(maTextureHandle);
        GLES20.glBindTexture(GL_TEXTURE_TARGET, 0);
        GLES20.glUseProgram(0);

    }


}

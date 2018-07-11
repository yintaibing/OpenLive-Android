package com.zhenai.android.utils.record_screen;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Surface;

import java.nio.Buffer;
import java.nio.FloatBuffer;

public class ScreenStreamCropper extends HandlerThread {
    public static final int MSG_START = 1,
                            MSG_RENDER = 2,
                            MSG_STOP = 3;

    private static final int EGL_RECORDABLE_ANDROID = 0x3142;

    public Handler mHandler;

    // gl
    private EGLDisplay eglDisplay;
    private EGLConfig eglConfig;
    private EGLContext eglContext;
    private EGLSurface eglSurface;
    private int mTextureID;
    private int mFrameBuffer;

    // config
    public int x, y, w, h;
    public Surface mOutputSurface;
    public boolean mReadBufferSet;
    public byte[] mReadBuffer;


    public ScreenStreamCropper() {
        super("ScreenStreamCropper");
    }

    public void initHandler() {
        mHandler = new Handler(getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_START:
                        createGL();
                        break;

//                    case MSG_RENDER:
//                        render();
//                        break;

                    case MSG_STOP:
                        destroyGL();
                        break;
                }
            }
        };
    }

    private void sendMsg(int what) {
        mHandler.obtainMessage(what).sendToTarget();
    }

    public void signalPrepare() {
        sendMsg(MSG_START);
    }

    public void signalStop() {
        sendMsg(MSG_STOP);
    }

    public void signalRender(Buffer readBuffer, long timestamp) {
        if (!mReadBufferSet) {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            mTextureID = textures[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, readBuffer);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);


            int[] frameBuffers = new int[1];
            GLES20.glGenFramebuffers(1, frameBuffers, 0);
            mFrameBuffer = frameBuffers[0];


            mReadBufferSet = true;
        }
        render(timestamp);
    }

    /**
     * 创建OpenGL环境
     */
    private void createGL() {
        // 获取显示设备(默认的显示设备)
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        // 初始化
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw new RuntimeException("EGL error " + EGL14.eglGetError());
        }
        // 获取FrameBuffer格式和能力
        int[] configAttribs = {
                EGL14.EGL_BUFFER_SIZE, 32,

                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_RED_SIZE, 8,

                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID,
                1,
                EGL14.EGL_NONE
        };
        int[] numConfigs = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, configs.length, numConfigs, 0)) {
            throw new RuntimeException("EGL error " + EGL14.eglGetError());
        }
        eglConfig = configs[0];
        // 创建OpenGL上下文(可以先不设置EGLSurface，但EGLContext必须创建，
        // 因为后面调用GLES方法基本都要依赖于EGLContext)
        int[] contextAttribs = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException("EGL error " + EGL14.eglGetError());
        }

        int[] attrs_list = new int[] {
                EGL14.EGL_NONE
        };
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, mOutputSurface, attrs_list, 0);

        // 设置默认的上下文环境和输出缓冲区(小米4上如果不设置有效的eglSurface后面创建着色器会失败，可以先创建一个默认的eglSurface)
        //EGL14.eglMakeCurrent(eglDisplay, surface.eglSurface, surface.eglSurface, eglContext);
//        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, eglContext);
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);


        mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D);

        maPositionLoc = GLES20.glGetAttribLocation(mProgramHandle, "aPosition");
        GlUtil.checkLocation(maPositionLoc, "aPosition");
        maTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord");
        GlUtil.checkLocation(maTextureCoordLoc, "aTextureCoord");
        muMVPMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uMVPMatrix");
        GlUtil.checkLocation(muMVPMatrixLoc, "uMVPMatrix");
        muTexMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexMatrix");
        GlUtil.checkLocation(muTexMatrixLoc, "uTexMatrix");

        mVertexArray = GlUtil.createFloatBuffer(FULL_RECTANGLE_COORDS);
        mTexCoordArray = GlUtil.createFloatBuffer(FULL_RECTANGLE_TEX_COORDS);

        Matrix.setIdentityM(mMvpMatrix, 0);
//        Matrix.setIdentityM(mTexMatrix, 0);
    }

    private int mProgramHandle;
    private int muMVPMatrixLoc;
    private int muTexMatrixLoc;
    private int maPositionLoc;
    private int maTextureCoordLoc;
    private FloatBuffer mTexCoordArray;
    private FloatBuffer mVertexArray;

    private float[] mMvpMatrix = new float[16];
    private float[] mTexMatrix = new float[]{
            1f, 0f, 0f, 0f,
            0f, -1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f
    };

    /**
     * 销毁OpenGL环境
     */
    private void destroyGL() {
        EGL14.eglDestroyContext(eglDisplay, eglContext);
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglDisplay = EGL14.EGL_NO_DISPLAY;
    }

    /**
     * 渲染到各个eglSurface
     */
    private void render(long timestamp) {
//        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);
//        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
//                GLES20.GL_COLOR_ATTACHMENT0,
//                GLES20.GL_TEXTURE_2D, mTextureID, 0);
//        renderInternal();
//        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        renderInternal();

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, timestamp);
        EGL14.eglSwapBuffers(eglDisplay, eglSurface);
    }

    private void renderInternal() {
        GLES20.glUseProgram(mProgramHandle);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID);

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");
//
//        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, mTexMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc,
                2,//Drawable2d.COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                8,//Drawable2d.VERTEXTURE_STRIDE,
                mVertexArray);
        GlUtil.checkGlError("glVertexAttribPointer");

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc,
                2,
                GLES20.GL_FLOAT,
                false,
                8,//Drawable2d.TEXTURE_COORD_STRIDE,
                mTexCoordArray);
        GlUtil.checkGlError("glVertexAttribPointer");

        GLES20.glViewport(x, y, w, h);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 4);
//        GLES20.glClearColor(50f, 100f, 150f, 1f);
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glDisableVertexAttribArray(maPositionLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glUseProgram(0);
    }

    private static final float FULL_RECTANGLE_COORDS[] = {
            -1.0f, -1.0f,   // 0 bottom left
            1.0f, -1.0f,   // 1 bottom right
            -1.0f, 1.0f,   // 2 top left
            1.0f, 1.0f,   // 3 top right
    };
    private static final float FULL_RECTANGLE_TEX_COORDS[] = {
            0.0f, 0.0f,     // 0 bottom left
            1.0f, 0.0f,     // 1 bottom right
            0.0f, 1.0f,     // 2 top left
            1.0f, 1.0f      // 3 top right
//            0f, 1f,
//            1f, 1f,
//            0f, 0f,
//            1f, 0f,
    };

    // Simple vertex shader, used for all programs.
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uTexMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * aPosition;\n" +
                    "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
                    "}\n";

    // Simple fragment shader for use with "normal" 2D textures.
    private static final String FRAGMENT_SHADER_2D =
            "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform sampler2D sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = vec4(texture2D(sTexture, vTextureCoord).rgb, 1.0);\n" +
                    "}\n";
}

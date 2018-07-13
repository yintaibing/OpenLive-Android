package com.zhenai.android.utils.record_screen;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.view.Surface;

import java.nio.FloatBuffer;

public class ScreenStreamCropper {
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private static final int GL_TEXTURE_TARGET = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
//    private static final int GL_TEXTURE_TARGET = GLES20.GL_TEXTURE_2D;

    public Handler mHandler;

    // gl
    private EGLDisplay eglDisplay;

    private EGLContext eglPbufferContext;
    private EGLSurface eglPbufferSurface;

    private EGLContext eglWindowContext;
    private EGLSurface eglWindowSurface;
    private int mTextureID;

    // config
    public int x, y, w, h;
    public Surface mOutputSurface;

    public ScreenStreamCropper() {

    }

    public void createTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        mTextureID = textures[0];
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_TARGET, mTextureID);
        GLES20.glTexParameteri(GL_TEXTURE_TARGET, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_TARGET, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_TARGET, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_TARGET, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
//        GLES20.glTexImage2D(GL_TEXTURE_TARGET, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA,
//                GLES20.GL_UNSIGNED_BYTE, null);
//        GLES20.glBindTexture(GL_TEXTURE_TARGET, 0);
    }

    public void setmOutputSurface(Surface mOutputSurface) {
        this.mOutputSurface = mOutputSurface;
    }

    public int getmTextureID() {
        return mTextureID;
    }

    /**
     * 创建OpenGL环境
     */
    public void createGL() {
        // 获取显示设备(默认的显示设备)
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        // 初始化
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw new RuntimeException("EGL error " + EGL14.eglGetError());
        }

        // pbuffer surface start
        createPBufferSurface();
        GlUtil.checkGlError("createPBufferSurface");
        // pbuffer surface end


        // window surface start
        createWindowSurface();
        GlUtil.checkGlError("createWindowSurface");
        // window surface end

        makeCurrent(false);

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

        // create texture
        createTexture();

        Matrix.setIdentityM(mMvpMatrix, 0);
//        Matrix.translateM(mMvpMatrix, 0, 0.5f, 0f, 0f);
        Matrix.setIdentityM(mTexMatrix, 0);
    }

    private void createPBufferSurface() {
        int[] pbufferConfigAttribs = {
                EGL14.EGL_BUFFER_SIZE, 32,

                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_RED_SIZE, 8,

                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,

                EGL14.EGL_NONE
        };
        int[] numConfigs = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        if (!EGL14.eglChooseConfig(eglDisplay, pbufferConfigAttribs, 0, configs, 0, configs.length, numConfigs, 0)) {
            throw new RuntimeException("EGL error " + EGL14.eglGetError());
        }
        EGLConfig eglConfig = configs[0];
        int[] contextAttribs = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        eglPbufferContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
        if (eglPbufferContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException("EGL error " + EGL14.eglGetError());
        }
        int[] pbufferSurfaceAttribs = new int[] {
                EGL14.EGL_WIDTH, w,
                EGL14.EGL_HEIGHT, h,

                EGL14.EGL_NONE
        };
        eglPbufferSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferSurfaceAttribs, 0);
    }

    private void createWindowSurface() {
        int[] windowConfigAttribs = {
                EGL14.EGL_BUFFER_SIZE, 32,

                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_RED_SIZE, 8,

                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
//                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE, 0,

                EGL14.EGL_NONE
        };
        int[] numConfigs = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        if (!EGL14.eglChooseConfig(eglDisplay, windowConfigAttribs, 0, configs, 0, configs.length, numConfigs, 0)) {
            throw new RuntimeException("EGL error " + EGL14.eglGetError());
        }
        EGLConfig eglConfig = configs[0];
        int[] contextAttribs = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        eglWindowContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
        if (eglWindowContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException("EGL error " + EGL14.eglGetError());
        }
        int[] windowSurfaceAttribs = new int[] {
                EGL14.EGL_NONE
        };
        eglWindowSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, mOutputSurface, windowSurfaceAttribs, 0);
    }

    public void makeCurrent(boolean window) {
        if (window) {
            EGL14.eglMakeCurrent(eglDisplay, eglWindowSurface, eglWindowSurface, eglWindowContext);
        } else {
            EGL14.eglMakeCurrent(eglDisplay, eglPbufferSurface, eglPbufferSurface, eglPbufferContext);
        }
    }

    private int mProgramHandle;
    private int muMVPMatrixLoc;
    private int muTexMatrixLoc;
    private int maPositionLoc;
    private int maTextureCoordLoc;
    private FloatBuffer mTexCoordArray;
    private FloatBuffer mVertexArray;

    private float[] mMvpMatrix = new float[16];
    private float[] mTexMatrix = new float[]
            {
            1f, 0f, 0f, 0f,
            0f, -1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f
    };

    /**
     * 销毁OpenGL环境
     */
    public void destroyGL() {
        EGL14.eglDestroyContext(eglDisplay, eglPbufferContext);
        eglPbufferContext = EGL14.EGL_NO_CONTEXT;
        EGL14.eglDestroyContext(eglDisplay, eglWindowContext);
        eglWindowContext = EGL14.EGL_NO_CONTEXT;
        eglDisplay = EGL14.EGL_NO_DISPLAY;
    }

    /**
     * 渲染到各个eglSurface
     */
    public void render(long timestamp) {
//        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);
//        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
//                GLES20.GL_COLOR_ATTACHMENT0,
//                GL_TEXTURE_TARGET, mTextureID, 0);
//        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        GLES20.glUseProgram(mProgramHandle);
        GlUtil.checkGlError("glUseProgram");

//        GLES20.glBindTexture(GL_TEXTURE_TARGET, mTextureID);

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc,
                3,//Drawable2d.COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                3*4,//Drawable2d.VERTEXTURE_STRIDE,
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
                4*4,//Drawable2d.TEXTURE_COORD_STRIDE,
                mTexCoordArray);
        GlUtil.checkGlError("glVertexAttribPointer");

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");
//
//        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, mTexMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

//        GLES20.glViewport(x, -100, w, h);
//        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
//        GLES20.glClearColor(50f, 100f, 150f, 1f);
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

//        GLES20.glDisableVertexAttribArray(maPositionLoc);
//        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
//        GLES20.glBindTexture(GL_TEXTURE_TARGET, 0);
//        GLES20.glUseProgram(0);

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglWindowSurface, timestamp);
        EGL14.eglSwapBuffers(eglDisplay, eglWindowSurface);
    }


//    private static final float FULL_RECTANGLE_COORDS[] = {
//            -1.0f, 1.0f, 0,
//            -1.0f, -1.0f, 0,
//            1.0f, 1.0f, 0,
//
//            -1.0f, -1.0f, 0,
//            1.0f, -1.0f, 0,
//            1.0f, 1.0f, 0
//    };
//    private static final float FULL_RECTANGLE_TEX_COORDS[] = {
//            0f, 1f,
//            0f, 0f,
//            1f, 1f,
//            0f, 0f,
//            1f, 0f,
//            1f, 1f
//    };

    private static final float FULL_RECTANGLE_COORDS[] = {
            -1.0f, -1.0f,1.0f,   // 0 bottom left
            1.0f, -1.0f,1.0f,   // 1 bottom right
            -1.0f,  1.0f,1.0f,   // 2 top left
            1.0f,  1.0f,1.0f   // 3 top right
    };

    private static final float FULL_RECTANGLE_TEX_COORDS[] = {
            0.0f, 1.0f, 1f, 1.0f,    // 0 bottom left
            1.0f, 1.0f, 1f, 1.0f,     // 1 bottom right
            0.0f, 0.0f, 1f, 1.0f,    // 2 top left
            1.0f, 0.0f, 1f, 1.0f     // 3 top right
    };

    // Simple vertex shader, used for all programs.
//    private static final String VERTEX_SHADER =
//            "uniform mat4 uMVPMatrix;\n"
//                    + "uniform mat4 uTexMatrix;\n"
//                    + "attribute highp vec3 aPosition;\n"
//                    + "attribute highp vec2 aTextureCoord;\n"
//                    + "varying highp vec2 vTextureCoord;\n"
//                    + "\n"
//                    + "void main() {\n"
//                    + "	gl_Position = uMVPMatrix * vec4(aPosition,1);\n"
//                    + "	vTextureCoord = (uTexMatrix * vec4(aTextureCoord,1,1)).xy;\n"
//                    + "}\n";
//
//    // Simple fragment shader for use with "normal" 2D textures.
//    private static final String FRAGMENT_SHADER_2D =
//            "precision mediump float;\n"
//                    + "uniform sampler2D sTexture;\n"
//                    + "varying highp vec2 vTextureCoord;\n"
//                    + "void main() {\n"
//                    + "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
//                    + "}";


    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uTexMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec4 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * aPosition;\n" +
                    "    vTextureCoord = uTexMatrix * aTextureCoord;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER_2D =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +      // highp here doesn't seem to matter
                    "varying vec4 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord.xy/vTextureCoord.z);" +
                    "}\n";
}

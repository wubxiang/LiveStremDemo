package com.xqhy.livestremdemo.screen.share;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.xqhy.livestremdemo.screen.GLThreadContext;
import com.xqhy.livestremdemo.screen.IExternalVideoInput;

public class ShowScreenShareInput implements IExternalVideoInput, TextureView.SurfaceTextureListener {
    private static final String TAG = ShowScreenShareInput.class.getSimpleName();
    private static final String VIRTUAL_DISPLAY_NAME = "screen-share-display";

    private Context mContext;
    private int mScreenWidth;
    private int mScreenHeight;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mScreenDpi;
    private int mFrameInterval;
    private Intent mIntent;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private volatile boolean mStopped;


    // The life cycle of SurfaceTexture only affects the
    // rendering of the local preview
    private volatile SurfaceTexture mLocalSurfaceTexture;
    private EGLSurface mPreviewSurface = EGL14.EGL_NO_SURFACE;

    public ShowScreenShareInput(Context context, int width, int height, int dpi, int framerate, Intent data) {
        mContext = context;
        mScreenWidth = width;
        mScreenHeight = height;

        mSurfaceWidth = width;
        mSurfaceHeight = height;
        mScreenDpi = dpi;
        mFrameInterval = 1000 / framerate;
        mIntent = data;
    }

    // 在外部视频输入线程的初始化过程中，通过 MediaProjection 创建 VirtualDisplay，并把 VirtualDisplay 的内容渲染到 SurfaceView
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onVideoInitialized(Surface target) {
        MediaProjectionManager pm = (MediaProjectionManager)
                mContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mMediaProjection = pm.getMediaProjection(Activity.RESULT_OK, mIntent);

        if (mMediaProjection == null) {
            Log.e(TAG, "media projection start failed");
            return;
        }

        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME, mScreenWidth, mScreenHeight, mScreenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, target,
                null, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onVideoStopped(GLThreadContext context) {
        mStopped = true;

        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }

        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }

        if (mPreviewSurface != EGL14.EGL_NO_SURFACE) {
            context.eglCore.makeNothingCurrent();
            context.eglCore.releaseSurface(mPreviewSurface);
            mPreviewSurface = EGL14.EGL_NO_SURFACE;
        }
    }

    @Override
    public boolean isRunning() {
        return !mStopped;
    }

    @Override
    public void onFrameAvailable(GLThreadContext context, int textureId, float[] transform) {
        // Screen sharing do not process or show local preview
        // 屏幕共享本地预览

        // This method is called in an OpenGLES thread. Usually this
        // method will give the frame information to draw local preview
        if (mLocalSurfaceTexture == null || mStopped) {
            if (mPreviewSurface != EGL14.EGL_NO_SURFACE) {
                context.eglCore.makeNothingCurrent();
                context.eglCore.releaseSurface(mPreviewSurface);
                mPreviewSurface = EGL14.EGL_NO_SURFACE;
            }
            return;
        }

        if (mPreviewSurface == EGL14.EGL_NO_SURFACE) {
            try {
                mPreviewSurface = context.eglCore.createWindowSurface(mLocalSurfaceTexture);
            } catch (Exception e) {
                return;
            }
        }

        if (!context.eglCore.isCurrent(mPreviewSurface)) {
            context.eglCore.makeCurrent(mPreviewSurface);
            setViewPort(mScreenWidth, mScreenHeight, mSurfaceWidth, mSurfaceHeight);
        }

        context.program.drawFrame(textureId, transform);
        context.eglCore.swapBuffers(mPreviewSurface);
    }

    private void setViewPort(int videoW, int videoH, int surfaceW, int surfaceH) {
        float videoRatio = videoW / (float) videoH;
        float surfaceRatio = surfaceW / (float) surfaceH;
        if (videoRatio == surfaceRatio) {
            GLES20.glViewport(0, 0, videoW, videoH);
            return;
        }

        int startX;
        int startY;
        int viewPortW;
        int viewPortH;
        if (videoRatio > surfaceRatio) {
            // the video is wider than the surface
            viewPortW = surfaceW;
            viewPortH = (int) (surfaceW / videoRatio);
            startX = 0;
            startY = (surfaceH - viewPortH) / 2;
        } else {
            // surface is wider than the video
            viewPortH = surfaceH;
            viewPortW = (int) (viewPortH * videoRatio);
            startX = (surfaceW - viewPortW) / 2;
            startY = 0;
        }

        GLES20.glViewport(startX, startY, viewPortW, viewPortH);
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public Size onGetFrameSize() {
        return new Size(mScreenWidth, mScreenHeight);
    }

    @Override
    public int timeToWait() {
        return mFrameInterval;
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        // add TextureView时触发
        mLocalSurfaceTexture = surface;
        mSurfaceWidth = width;
        mSurfaceHeight = height;
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        mLocalSurfaceTexture = surface;
        mSurfaceWidth = width;
        mSurfaceHeight = height;
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        mLocalSurfaceTexture = null;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

    }
}

package com.xqhy.livestremdemo.screen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ScreenShareInput implements IExternalVideoInput {
    private static final String TAG = ScreenShareInput.class.getSimpleName();
    private static final String VIRTUAL_DISPLAY_NAME = "screen-share-display";

    private Context mContext;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mScreenDpi;
    private int mFrameInterval;
    private Intent mIntent;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private volatile boolean mStopped;

    public ScreenShareInput(Context context, int width, int height, int dpi, int framerate, Intent data) {
        mContext = context;
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
                VIRTUAL_DISPLAY_NAME, mSurfaceWidth, mSurfaceHeight, mScreenDpi,
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
    }

    @Override
    public boolean isRunning() {
        return !mStopped;
    }

    @Override
    public void onFrameAvailable(GLThreadContext context, int textureId, float[] transform) {
        // Screen sharing do not process or show local preview
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public Size onGetFrameSize() {
        return new Size(mSurfaceWidth, mSurfaceHeight);
    }

    @Override
    public int timeToWait() {
        return mFrameInterval;
    }
}

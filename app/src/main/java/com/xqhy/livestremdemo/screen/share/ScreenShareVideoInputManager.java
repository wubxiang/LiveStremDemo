package com.xqhy.livestremdemo.screen.share;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import com.xqhy.livestremdemo.screen.EglCore;
import com.xqhy.livestremdemo.screen.GLThreadContext;
import com.xqhy.livestremdemo.screen.GlUtil;
import com.xqhy.livestremdemo.screen.IExternalVideoInput;
import com.xqhy.livestremdemo.screen.ProgramTextureOES;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.gl.TextureTransformer;
import io.agora.rtc.mediaio.IVideoFrameConsumer;
import io.agora.rtc.mediaio.IVideoSource;
import io.agora.rtc.mediaio.MediaIO;
import static io.agora.rtc.mediaio.MediaIO.BufferType.TEXTURE;
import static io.agora.rtc.mediaio.MediaIO.PixelFormat.TEXTURE_OES;

/**
 * {@link IVideoSource}
 * The IVideoSource interface defines a set of protocols to implement the custom video source and
 * pass it to the underlying media engine to replace the default video source.
 * By default, when enabling real-time communications, the Agora SDK enables the default video input
 * device (built-in camera) to start video streaming. The IVideoSource interface defines a set of
 * protocols to create customized video source objects and pass them to the media engine to replace
 * the default camera source so that you can take ownership of the video source and manipulate it.
 * Once you implement this interface, the Agora Media Engine automatically releases its ownership of
 * the current video input device and pass it on to you, so that you can use the same video input
 * device to capture the video stream.
 */
public class ScreenShareVideoInputManager implements IVideoSource {
    private static final String TAG = ScreenShareVideoInputManager.class.getSimpleName();

    public static final String FLAG_SCREEN_WIDTH = "screen-width";
    public static final String FLAG_SCREEN_HEIGHT = "screen-height";
    public static final String FLAG_SCREEN_DPI = "screen-dpi";
    public static final String FLAG_FRAME_RATE = "screen-frame-rate";



    private ExternalVideoInputThread mThread;
    private volatile IExternalVideoInput mCurVideoInput;
    private volatile IExternalVideoInput mNewVideoInput;

    // RTC video interface to send video
    private volatile IVideoFrameConsumer mConsumer;

    private Context context;
    private TextureTransformer textureTransformer;
    private static final int MAX_TEXTURE_COPY = 1;

    private RtcEngine mRtcEngine;


    public ScreenShareVideoInputManager(Context context, RtcEngine engine) {
        this.context = context;

        mRtcEngine = engine;
    }

    void start() {
        if(mThread == null) {
            mThread = new ExternalVideoInputThread();
        }
        if(!mThread.isAlive()){
            mThread.start();
        }
    }

    // 根据 intent 获取录屏视频的参数
    boolean setExternalVideoInput(IExternalVideoInput source) {
        // Do not reset current input if the target type is
        // the same as the current which is still running.
        if (mCurVideoInput != null && mCurVideoInput.isRunning()) {
            return false;
        }

        setExternalVideoInput2(source);
        return true;
    }

    // 执行该方法后就可以看到屏幕内容了
    private void setExternalVideoInput2(IExternalVideoInput source) {
        if (mThread != null && mThread.isAlive()) {
            mThread.pauseThread();
        }
        mNewVideoInput = source;
    }

    void stop() {
        mThread.setThreadStopped();
    }

    /**
     * This callback initializes the video source. You can enable the camera or initialize the video
     * source and then pass one of the following return values to inform the media engine whether
     * the video source is ready.
     *
     * @param consumer The IVideoFrameConsumer object which the media engine passes back. You need
     *                 to reserve this object and pass the video frame to the media engine through
     *                 this object once the video source is initialized. See the following contents
     *                 for the definition of IVideoFrameConsumer.
     * @return true: The external video source is initialized.
     * false: The external video source is not ready or fails to initialize, the media engine stops
     * and reports the error.
     * PS:
     * When initializing the video source, you need to specify a buffer type in the getBufferType
     * method and pass the video source in the specified type to the media engine.
     */
    @Override
    public boolean onInitialize(IVideoFrameConsumer consumer) {
        mConsumer = consumer;
        return true;
    }

    /**
     * The SDK triggers this callback when the underlying media engine is ready to start video streaming.
     * You should start the video source to capture the video frame. Once the frame is ready, use
     * IVideoFrameConsumer to consume the video frame.
     *
     * @return true: The external video source is enabled and the SDK calls IVideoFrameConsumer to receive
     * video frames.
     * false: The external video source is not ready or fails to enable, the media engine stops and
     * reports the error.
     */
    @Override
    public boolean onStart() {
        return true;
    }

    /**
     * The SDK triggers this callback when the media engine stops streaming. You should then stop
     * capturing and consuming the video frame. After calling this method, the video frames are
     * discarded by the media engine.
     */
    @Override
    public void onStop() {

    }

    /**
     * The SDK triggers this callback when IVideoFrameConsumer is released by the media engine. You
     * can now release the video source as well as IVideoFrameConsumer.
     */
    @Override
    public void onDispose() {
        Log.e(TAG, "SwitchExternalVideo-onDispose");
        mConsumer = null;
    }

    @Override
    public int getBufferType() {
        return TEXTURE.intValue();
    }

    @Override
    public int getCaptureType() {
        return MediaIO.CaptureType.SCREEN.intValue();
    }

    @Override
    public int getContentHint() {
        return MediaIO.ContentHint.NONE.intValue();
    }

    private class ExternalVideoInputThread extends Thread {
        private final String TAG = ExternalVideoInputThread.class.getSimpleName();
        private final int DEFAULT_WAIT_TIME = 1;

        private EglCore mEglCore;
        private EGLSurface mEglSurface;
        private int mTextureId;
        private SurfaceTexture mSurfaceTexture;
        private Surface mSurface;
        private float[] mTransform = new float[16];
        private GLThreadContext mThreadContext;
        int mVideoWidth;
        int mVideoHeight;
        private volatile boolean mStopped;
        private volatile boolean mPaused;

        // 在加入频道前，设置自定义视频源
        // 对自定义视频输入线程进行设置
        // 示例代码使用了 grafika 开源项目中的类。grafika 开源项目对 Android 的图形架构进行了封装。参考：https://source.android.com/devices/graphics/architecture
        // EglCore 类，GlUtil 类，EGLContext 类， ProgramTextureOES 类的具体实现参考：https://github.com/google/grafika
        // GLThreadContext 类包含 EglCore 类， EGLContext 类， ProgramTextureOES 类
        private void prepare() {
            // 通过 EglCore 类创建 OpenGL ES 环境
            mEglCore = new EglCore();
            mEglSurface = mEglCore.createOffscreenSurface(1, 1);
            mEglCore.makeCurrent(mEglSurface);
            // 通过 GlUtil 类创建 EGL texture 对象
            mTextureId = GlUtil.createTextureObject(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            // 通过 EGL texture 对象创建 SurfaceTexture 对象
            mSurfaceTexture = new SurfaceTexture(mTextureId);
            // 通过 SurfaceTexture 对象创建 Surface 对象
            mSurface = new Surface(mSurfaceTexture);
            // 将 EGLCore 对象，EGL context 对象，和 ProgramTextureOES 对象传给 GLThreadContext 对象的成员
            mThreadContext = new GLThreadContext();
            mThreadContext.eglCore = mEglCore;
            mThreadContext.context = mEglCore.getEGLContext();
            mThreadContext.program = new ProgramTextureOES();
            textureTransformer = new TextureTransformer(MAX_TEXTURE_COPY);

            /**Customizes the video source.
             * Call this method to add an external video source to the SDK.*/
            // 设置自定义视频源
            mRtcEngine.setVideoSource(ScreenShareVideoInputManager.this);
        }

        private void release() {
            if (mRtcEngine == null) {
                return;
            }
            /**release external video source*/
            textureTransformer.release();
            mRtcEngine.setVideoSource(null);
            mSurface.release();
            mEglCore.makeNothingCurrent();
            mEglCore.releaseSurface(mEglSurface);
            mSurfaceTexture.release();
            GlUtil.deleteTextureObject(mTextureId);
            mTextureId = 0;
            mEglCore.release();
        }

        //将 SurfaceView 作为自定义视频源。本地用户加入频道后，自采集模块通过 ExternalVideoInputThread 线程中的
        // consumeTextureFrame 消费视频帧，并将视频帧发送到 SDK
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void run() {
            prepare();

            while (!mStopped) {
                if (mCurVideoInput != mNewVideoInput) {
                    Log.i(TAG, "New video input selected");
                    // Current video input is running, but we now
                    // introducing a new video type.
                    // The new video input type may be null, referring
                    // that we are not using any video.
                    if (mCurVideoInput != null) {
                        mCurVideoInput.onVideoStopped(mThreadContext);
                        Log.i(TAG, "recycle stopped input");
                    }

                    mCurVideoInput = mNewVideoInput;
                    if (mCurVideoInput != null) {
                        mCurVideoInput.onVideoInitialized(mSurface);
                        Log.i(TAG, "initialize new input");
                    }

                    if (mCurVideoInput == null) {
                        continue;
                    }

                    Size size = mCurVideoInput.onGetFrameSize();
                    mVideoWidth = size.getWidth();
                    mVideoHeight = size.getHeight();
                    mSurfaceTexture.setDefaultBufferSize(mVideoWidth, mVideoHeight);

                    if (mPaused) {
                        // If current thread is in pause state, it must be paused
                        // because of switching external video sources.
                        mPaused = false;
                    }
                } else if (mCurVideoInput != null && !mCurVideoInput.isRunning()) {
                    // Current video source has been stopped by other
                    // mechanisms (video playing has completed, etc).
                    // A callback method is invoked to do some collect
                    // or release work.
                    // Note that we also set the new video source null,
                    // meaning at meantime, we are not introducing new
                    // video types.
                    Log.i(TAG, "current video input is not running");
                    mCurVideoInput.onVideoStopped(mThreadContext);
                    mCurVideoInput = null;
                    mNewVideoInput = null;
                }

                if (mPaused || mCurVideoInput == null) {
                    waitForTime(DEFAULT_WAIT_TIME);
                    continue;
                }

                // 调用 updateTexImage() 将数据更新到 OpenGL ES 纹理对象
                // 调用getTransformMatrix() 转换纹理坐标
                try {
                    mSurfaceTexture.updateTexImage();
                    mSurfaceTexture.getTransformMatrix(mTransform);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // 通过 onFrameAvailable 回调获取采集的视频帧信息。此处的 onFrameAvailable 为 Android 原生
                // 方法在 ScreenShareInput 类中的重写，可以获取 Texture ID，transform 信息。
                // 屏幕共享无需在本地渲染视频，因为无需本地预览
                if (mCurVideoInput != null) {
                    mCurVideoInput.onFrameAvailable(mThreadContext, mTextureId, mTransform);
                }

                mEglCore.makeCurrent(mEglSurface);
                GLES20.glViewport(0, 0, mVideoWidth, mVideoHeight);

                if (mConsumer != null) {
                    Log.e(TAG, "publish stream with ->width:" + mVideoWidth + ",height:" + mVideoHeight);
                    /**Receives the video frame in texture,and push it out
                     * @param textureId ID of the texture
                     * @param format Pixel format of the video frame
                     * @param width Width of the video frame
                     * @param height Height of the video frame
                     * @param rotation Clockwise rotating angle (0, 90, 180, and 270 degrees) of the video frame
                     * @param timestamp Timestamp of the video frame. For each video frame, you need to set a timestamp
                     * @param matrix Matrix of the texture. The float value is between 0 and 1, such as 0.1, 0.2, and so on*/
                    textureTransformer.copy(mTextureId, TEXTURE_OES.intValue(), mVideoWidth, mVideoHeight);
                    mConsumer.consumeTextureFrame(mTextureId,
                            TEXTURE_OES.intValue(),
                            mVideoWidth, mVideoHeight, 0,
                            System.currentTimeMillis(), mTransform);
                }

                // The pace at which the output Surface is sampled
                // for video frames is controlled by the waiting
                // time returned from the external video source.
                waitForNextFrame();
            }

            if (mCurVideoInput != null) {
                // The manager will cause the current
                // video source to be stopped.
                mCurVideoInput.onVideoStopped(mThreadContext);
            }
            release();
        }

        void pauseThread() {
            mPaused = true;
        }

        void setThreadStopped() {
            mStopped = true;
        }

        private void waitForNextFrame() {
            int wait = mCurVideoInput != null
                    ? mCurVideoInput.timeToWait()
                    : DEFAULT_WAIT_TIME;
            waitForTime(wait);
        }

        private void waitForTime(int time) {
            try {
                Thread.sleep(time);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
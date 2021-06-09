package com.xqhy.livestremdemo.screen.share

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import com.xqhy.livestremdemo.LiveConstants
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import io.agora.rtc.models.ChannelMediaOptions
import io.agora.rtc.video.VideoEncoderConfiguration

class ScreenShareService : Service() {
    private lateinit var mSourceManager: ScreenShareVideoInputManager
    private var mService: ScreenShareBinder? = null

    private lateinit var mRtcEngine: RtcEngine

    private var mCallback: Callback? = null

    private var mVideoInput: ShowScreenShareInput?=null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "ScreenShare"

        // 是否正在屏幕分享
        var IS_SHARING = false

        private const val DEFAULT_SCREEN_WIDTH = 640
        private const val DEFAULT_SCREEN_HEIGHT = 480
        private const val DEFAULT_SCREEN_DPI = 3
        private const val DEFAULT_FRAME_RATE = 15
    }

    inner class ScreenShareBinder : Binder() {
        // 开始直播
        fun startLive(token: String, channelName: String) {
            startLiveShare(token, channelName)
        }

        fun setCallback(callback: Callback) {
            mCallback = callback
        }

        // 离开直播间
        fun leaveLiveRoom(){
            mCallback = null
        }

        fun setExternalVideoInput(intent: Intent): Boolean {
            val metrics = DisplayMetrics()
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(
                metrics
            )

            mVideoInput = ShowScreenShareInput(
                this@ScreenShareService,
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.density.toInt(),
                DEFAULT_FRAME_RATE,
                intent
            )

            return mSourceManager.setExternalVideoInput(mVideoInput)
        }

        fun stopLive() {
            stopSelf()
            stopLiveShare()
        }

        //
        fun addTextViewListener(view: TextureView) {
            // add TextureView时触发
            view.surfaceTextureListener = mVideoInput
        }
    }

    interface Callback {
        fun onJoinChannelSuccess()
        fun stopLive()
    }

    private val mHandler by lazy { Handler(mainLooper) }

    override fun onCreate() {
        super.onCreate()

        mService = ScreenShareBinder()
    }

    override fun onBind(intent: Intent): IBinder? {
        return mService
    }

    override fun onDestroy() {
        if(IS_SHARING) {
            stopLiveShare()
        }
        super.onDestroy()
    }

    private fun startLiveShare(token: String, channelName: String){
        IS_SHARING = true

        initializeEngine(token, channelName)

        mSourceManager = ScreenShareVideoInputManager(this@ScreenShareService, mRtcEngine)

        startSourceManager()
        startForeground()
    }

    private fun stopLiveShare(){
        IS_SHARING = false

        stopSourceManager()
        stopForeground(true)

        leaveChannel()
        RtcEngine.destroy()

        mCallback?.stopLive()
    }

    // 在调用其他 Agora API 前，需要创建并初始化 RtcEngine 对象
    // 初始化 RtcEngine 对象。
    // 根据场景需要，在初始化时注册想要监听的回调事件，如本地用户加入频道，及远端主播加入频道等。注意不要在这些回调中进行 UI 操作
    private fun initializeEngine(token: String, channelName: String) {
        try {
            mRtcEngine = RtcEngine.create(baseContext, LiveConstants.APPID, mRtcEventHandler)
        } catch (e: Exception) {
            Log.e("TAG", Log.getStackTraceString(e))
            throw RuntimeException(
                """
                NEED TO check rtc sdk init fatal error
                ${Log.getStackTraceString(e)}
                """.trimIndent()
            )
        }

        // 设置频道场景为直播
        // 一个 RtcEngine 只能使用一种频道场景。如果想切换为其他模式，需要先调用 destroy 方法释放当前的 RtcEngine 实例，
        // 然后使用 create 方法创建一个新实例，再调用 setChannelProfile 设置新的频道场景
        mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)

        // 设置角色(主播)
        mRtcEngine.setClientRole(LiveConstants.BROADCASTER)

        // 启用视频模块。
        mRtcEngine.enableVideo()

        /**Set up to play remote sound with receiver */
        mRtcEngine!!.setDefaultAudioRoutetoSpeakerphone(false)
        mRtcEngine!!.setEnableSpeakerphone(false)

        setVideoConfiguration()

        joinChannel(token, channelName)
    }

    // 监听的回调事件，如本地用户加入频道，及远端主播加入频道等
    // 注意不要在这些回调中进行 UI 操作
    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        // 注册 onJoinChannelSuccess 回调。
        // 本地用户成功加入频道时，会触发该回调。
        // 不要在这些回调中进行 UI 操作
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            // 这是子线程，需要切换到主线程
            mHandler.post {
                Toast.makeText(this@ScreenShareService, "加入频道成功", Toast.LENGTH_SHORT).show()
                Log.i("agora", "Join channel success, uid: " + uid)

                mCallback?.onJoinChannelSuccess()
            }
        }

        // 注册 onUserJoined 回调。
        // 远端主播成功加入频道时，会触发该回调。
        // 可以在该回调中调用 setupRemoteVideo 方法设置远端视图。
        override fun onUserJoined(uid: Int, elapsed: Int) {
            // 这是子线程，需要切换到主线程
            mHandler.post {

            }
        }

        // 注册 onUserOffline 回调。
        // 远端主播离开频道或掉线时，会触发该回调。
        override fun onUserOffline(uid: Int, reason: Int) {
            // 这是子线程，需要切换到主线程
            mHandler.post {

            }
        }
    }

    private fun joinChannel(token: String, channelName: String) {
        // 使用 Token 加入频道。
        /** Allows a user to join a channel.
         * if you do not specify the uid, we will generate the uid for you */
        val option = ChannelMediaOptions()
        option.autoSubscribeAudio = true
        option.autoSubscribeVideo = true
        mRtcEngine.joinChannel(token, channelName, "Extra Optional Data", 0, option)
    }

    private fun leaveChannel() {
        // 离开当前频道。
        mRtcEngine.leaveChannel()
    }

    private fun setVideoConfiguration() {
        val metrics = DisplayMetrics()
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(
            metrics
        )

        val mode = VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
        /**Setup video stream encoding configs */
        mRtcEngine!!.setVideoEncoderConfiguration(
            VideoEncoderConfiguration(
                VideoEncoderConfiguration.VideoDimensions(
                    metrics.widthPixels,
                    metrics.heightPixels
                ),
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE, mode
            )
        )
    }


    private fun startForeground() {
        createNotificationChannel()
        val notificationIntent = Intent(
            applicationContext,
            applicationContext.javaClass
        )
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 0
        )
        val builder = Notification.Builder(this)
            .setContentTitle(CHANNEL_ID)
            .setContentIntent(pendingIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            builder.setChannelId(CHANNEL_ID)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, builder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, builder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_ID, importance
            )
            channel.description = CHANNEL_ID
            val notificationManager = getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startSourceManager() {
        mSourceManager!!.start()
    }

    private fun stopSourceManager() {
        if (mSourceManager != null) {
            mSourceManager!!.stop()
        }
    }
}
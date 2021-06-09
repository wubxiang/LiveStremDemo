package com.xqhy.livestremdemo

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.DisplayMetrics
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
import com.xqhy.livestremdemo.databinding.ActivityLiveRoomBinding
import com.xqhy.livestremdemo.screen.ExternalVideoInputManager
import com.xqhy.livestremdemo.screen.ExternalVideoInputService
import com.xqhy.livestremdemo.screen.ScreenConstants.ENGINE
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import io.agora.rtc.models.ChannelMediaOptions
import io.agora.rtc.video.VideoCanvas
import io.agora.rtc.video.VideoEncoderConfiguration
import io.agora.rtc.video.VideoEncoderConfiguration.ORIENTATION_MODE
import io.agora.rtc.video.VideoEncoderConfiguration.VideoDimensions


/**
 * Author: wbx
 * Date: 2021/6/4
 * Description:
 * 屏幕共享，不支持本地预览
 */

class ScreenLiveActivity : BaseActivity() {
    private val mBinding: ActivityLiveRoomBinding by lazy {
        ActivityLiveRoomBinding.inflate(
            layoutInflater
        )
    }

    private val PROJECTION_REQ_CODE = 1 shl 2
    private val DEFAULT_SHARE_FRAME_RATE = 15

    private lateinit var mRtcEngine: RtcEngine
    private var mRole = LiveConstants.AUDIENCE
    private var mChannelName = ""

    private var mService: ExternalVideoInputService.ExternalVideoBinder? = null
    private var mServiceConnection: VideoInputServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(mBinding.root)
        setClickListener()

        val params = intent.extras
        params?.apply {
            mRole = getInt(LiveConstants.ROLE)
            mChannelName = getString(LiveConstants.CHANNEL_NAME).toString()
        }

        initializeEngine()
        setChannelProfile()
        setClientRole()
        // 启用视频模块。
        mRtcEngine.enableVideo()

        /**Set up to play remote sound with receiver */
        ENGINE!!.setDefaultAudioRoutetoSpeakerphone(false)
        ENGINE!!.setEnableSpeakerphone(false)

//        if(mRole == LiveConstants.BROADCASTER) {
//            setupLocalVideo()
//        }

        joinChannel()
    }

    private fun setClickListener() {
        mBinding.ivSwitchCamera.setOnClickListener {
            mRtcEngine.switchCamera()
        }

        mBinding.ivExit.setOnClickListener {
            finish()
        }

        mBinding.liveBtnBeautification.setOnClickListener {
            val intent = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()
            startActivityForResult(intent, PROJECTION_REQ_CODE)
        }
    }

    // 在调用其他 Agora API 前，需要创建并初始化 RtcEngine 对象
    // 初始化 RtcEngine 对象。
    // 根据场景需要，在初始化时注册想要监听的回调事件，如本地用户加入频道，及远端主播加入频道等。注意不要在这些回调中进行 UI 操作
    private fun initializeEngine() {
        try {
            mRtcEngine = RtcEngine.create(baseContext, LiveConstants.APPID, mRtcEventHandler)
            ENGINE = mRtcEngine
        } catch (e: Exception) {
            Log.e("TAG", Log.getStackTraceString(e))
            throw RuntimeException(
                """
                NEED TO check rtc sdk init fatal error
                ${Log.getStackTraceString(e)}
                """.trimIndent()
            )
        }
    }

    // 监听的回调事件，如本地用户加入频道，及远端主播加入频道等
    // 注意不要在这些回调中进行 UI 操作
    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        // 注册 onJoinChannelSuccess 回调。
        // 本地用户成功加入频道时，会触发该回调。
        // 不要在这些回调中进行 UI 操作
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            // 这是子线程，需要切换到主线程
            runOnUiThread {
                Toast.makeText(this@ScreenLiveActivity, "加入频道成功", Toast.LENGTH_SHORT).show()
                Log.i("agora", "Join channel success, uid: " + uid)

                bindVideoService()
            }
        }

        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            super.onRemoteVideoStateChanged(uid, state, reason, elapsed)
            Toast.makeText(this@ScreenLiveActivity, "onRemoteVideoStateChanged", Toast.LENGTH_SHORT).show()

            if (state == Constants.REMOTE_VIDEO_STATE_STARTING) {
                /**Check if the context is correct */
                /**Check if the context is correct */
                runOnUiThread {
                    val surfaceView = RtcEngine.CreateRendererView(this@ScreenLiveActivity)
//                    surfaceView.setZOrderMediaOverlay(true)
                    if (mBinding.flVideoLayout.childCount > 0) {
                        mBinding.flVideoLayout.removeAllViews()
                    }
                    mBinding.flVideoLayout.addView(surfaceView)
                    /**Setup remote video to render */
                    ENGINE!!.setupRemoteVideo(
                        VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid)
                    )
                }
            }
        }

        // 注册 onUserJoined 回调。
        // 远端主播成功加入频道时，会触发该回调。
        // 可以在该回调中调用 setupRemoteVideo 方法设置远端视图。
        override fun onUserJoined(uid: Int, elapsed: Int) {
            // 这是子线程，需要切换到主线程
            runOnUiThread {
                Toast.makeText(this@ScreenLiveActivity, "远端主播成功加入频道", Toast.LENGTH_SHORT).show()
                Log.i("agora", "Remote user joined, uid: " + uid)
                if (mRole == LiveConstants.AUDIENCE) {
                    setupRemoteVideo(uid)
                }
            }
        }

        // 注册 onUserOffline 回调。
        // 远端主播离开频道或掉线时，会触发该回调。
        override fun onUserOffline(uid: Int, reason: Int) {
            // 这是子线程，需要切换到主线程
            runOnUiThread {
                Toast.makeText(this@ScreenLiveActivity, "远端主播离开频道或掉线", Toast.LENGTH_SHORT).show()
                Log.i("agora", "User offline, uid: " + uid)
                onRemoteUserLeft()
            }
        }
    }

    // 设置频道场景为直播
    // 一个 RtcEngine 只能使用一种频道场景。如果想切换为其他模式，需要先调用 destroy 方法释放当前的 RtcEngine 实例，
    // 然后使用 create 方法创建一个新实例，再调用 setChannelProfile 设置新的频道场景
    private fun setChannelProfile() {
        mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
    }

    // 设置角色
    private fun setClientRole() {
        mRtcEngine.setClientRole(mRole)
    }

    // 设置本地视图
    // 成功初始化 RtcEngine 对象后，需要在加入频道前设置本地视图，以便主播在直播中看到本地图像
    private fun setupLocalVideo() {
        // 创建 SurfaceView 对象。
        mBinding.flVideoLayout.removeAllViews()
        val mLocalView: SurfaceView = RtcEngine.CreateRendererView(baseContext)
//        mLocalView.setZOrderMediaOverlay(true)
        mBinding.flVideoLayout.addView(mLocalView)
        // 设置本地视图。
        val localVideoCanvas = VideoCanvas(mLocalView, VideoCanvas.RENDER_MODE_HIDDEN, 0)
        mRtcEngine.setupLocalVideo(localVideoCanvas)
    }

    private fun joinChannel() {
        // 使用 Token 加入频道。
        /** Allows a user to join a channel.
         * if you do not specify the uid, we will generate the uid for you */
        val option = ChannelMediaOptions()
        option.autoSubscribeAudio = true
        option.autoSubscribeVideo = true
        mRtcEngine.joinChannel(LiveConstants.TOKEN, mChannelName, "Extra Optional Data", 0, option)
    }

    // 如果是观众，设置主播视频
    private fun setupRemoteVideo(uid: Int) {
        // 创建 SurfaceView 对象。
        mBinding.flVideoLayout.removeAllViews()
        val mLocalView: SurfaceView = RtcEngine.CreateRendererView(baseContext)
        mBinding.flVideoLayout.addView(mLocalView)
        // 设置本地视图。
        val localVideoCanvas = VideoCanvas(mLocalView, VideoCanvas.RENDER_MODE_HIDDEN, uid)
        mRtcEngine.setupRemoteVideo(localVideoCanvas)
    }

    private fun onRemoteUserLeft() {
        mBinding.flVideoLayout.removeAllViews()
    }

    override fun onDestroy() {
        unbindVideoService()
        leaveChannel()
        RtcEngine.destroy()
        ENGINE = null

        super.onDestroy()
    }

    private fun leaveChannel() {
        // 离开当前频道。
        mRtcEngine.leaveChannel()
    }

    private fun bindVideoService() {
        val intent = Intent()
        intent.setClass(this, ExternalVideoInputService::class.java)
        mServiceConnection = VideoInputServiceConnection()
        bindService(intent, mServiceConnection!!, BIND_AUTO_CREATE)
    }

    private fun unbindVideoService() {
        if (mServiceConnection != null) {
            unbindService(mServiceConnection!!)
            mServiceConnection = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROJECTION_REQ_CODE && resultCode == RESULT_OK) {
            try {
                val metrics = DisplayMetrics()
                windowManager.getDefaultDisplay().getMetrics(metrics)
                data!!.putExtra(ExternalVideoInputManager.FLAG_SCREEN_WIDTH, metrics.widthPixels)
                data!!.putExtra(ExternalVideoInputManager.FLAG_SCREEN_HEIGHT, metrics.heightPixels)
                data!!.putExtra(ExternalVideoInputManager.FLAG_SCREEN_DPI, metrics.density.toInt())
                data.putExtra(ExternalVideoInputManager.FLAG_FRAME_RATE, DEFAULT_SHARE_FRAME_RATE)
                setVideoConfig(
                    ExternalVideoInputManager.TYPE_SCREEN_SHARE,
                    metrics.widthPixels,
                    metrics.heightPixels
                )
                mService!!.setExternalVideoInput(ExternalVideoInputManager.TYPE_SCREEN_SHARE, data)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }
    }

    private fun setVideoConfig(sourceType: Int, width: Int, height: Int) {
        val mode: ORIENTATION_MODE = when (sourceType) {
            ExternalVideoInputManager.TYPE_LOCAL_VIDEO, ExternalVideoInputManager.TYPE_SCREEN_SHARE -> ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
            else -> ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
        }
        /**Setup video stream encoding configs */
        ENGINE!!.setVideoEncoderConfiguration(
            VideoEncoderConfiguration(
                VideoDimensions(width, height),
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE, mode
            )
        )
    }


    // 通过 MediaProjection 创建 intent 并将 intent 传递给 startActivityForResult()，进行屏幕图像采集
    private inner class VideoInputServiceConnection : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            mService = iBinder as ExternalVideoInputService.ExternalVideoBinder
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mService = null
        }
    }
}
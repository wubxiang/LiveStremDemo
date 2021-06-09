package com.xqhy.livestremdemo

import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import com.xqhy.livestremdemo.databinding.ActivityLiveRoomBinding
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import io.agora.rtc.video.VideoCanvas


/**
 * Author: wbx
 * Date: 2021/6/4
 * Description:
 * 视频直播
 */

class LiveActivity:BaseActivity() {
    private val mBinding:ActivityLiveRoomBinding by lazy { ActivityLiveRoomBinding.inflate(
        layoutInflater
    ) }

    private lateinit var mRtcEngine: RtcEngine
    private var mRole = LiveConstants.AUDIENCE
    private var mChannelName = ""
    private var mLocalVideoView: SurfaceView?=null

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
        if(mRole == LiveConstants.BROADCASTER) {
            setupLocalVideo()
            mBinding.ivSwitchVideo.isActivated = true
        }else{
            mBinding.ivSwitchCamera.visibility = View.GONE
            mBinding.ivSwitchVideo.visibility = View.GONE
        }
        mBinding.ivSwitchAudio.isActivated = true

        joinChannel()
    }

    private fun setClickListener() {
        mBinding.ivExit.setOnClickListener {
            finish()
        }

        mBinding.ivSwitchCamera.setOnClickListener {
            mRtcEngine.switchCamera()
        }

        mBinding.ivSwitchVideo.setOnClickListener {
            if(it.isActivated){
                mRtcEngine.muteLocalVideoStream(true)
                it.isActivated = false
                mBinding.flVideoLayout.removeAllViews()
            }else{
                mRtcEngine.muteLocalVideoStream(false)
                mBinding.flVideoLayout.addView(mLocalVideoView)
                it.isActivated = true
            }
        }

        mBinding.ivSwitchAudio.setOnClickListener {
            if(mRole == LiveConstants.BROADCASTER) {
                if (it.isActivated) {
                    mRtcEngine.muteLocalAudioStream(true)
                    it.isActivated = false
                } else {
                    mRtcEngine.muteLocalAudioStream(false)
                    it.isActivated = true
                }
            }else{
                if (it.isActivated) {
                    mRtcEngine.muteAllRemoteAudioStreams(true)
                    it.isActivated = false
                } else {
                    mRtcEngine.muteAllRemoteAudioStreams(false)
                    it.isActivated = true
                }
            }
        }
    }

    // 在调用其他 Agora API 前，需要创建并初始化 RtcEngine 对象
    // 初始化 RtcEngine 对象。
    // 根据场景需要，在初始化时注册想要监听的回调事件，如本地用户加入频道，及远端主播加入频道等。注意不要在这些回调中进行 UI 操作
    private fun initializeEngine() {
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
    }

    // 监听的回调事件，如本地用户加入频道，及远端主播加入频道等
    // 注意不要在这些回调中进行 UI 操作
    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        // 注册 onJoinChannelSuccess 回调。
        // 本地用户成功加入频道时，会触发该回调。
        // 不要在这些回调中进行 UI 操作
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread {
                Toast.makeText(this@LiveActivity, "加入频道成功",Toast.LENGTH_SHORT).show()
                Log.i("agora", "Join channel success, uid: " + uid)
            }
        }

        // 注册 onUserJoined 回调。
        // 远端主播成功加入频道时，会触发该回调。
        // 可以在该回调中调用 setupRemoteVideo 方法设置远端视图。
        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                Toast.makeText(this@LiveActivity, "远端主播成功加入频道",Toast.LENGTH_SHORT).show()
                Log.i("agora", "Remote user joined, uid: " + uid)
                if(mRole == LiveConstants.AUDIENCE) {
                    setupRemoteVideo(uid)
                }
            }
        }

        // 注册 onUserOffline 回调。
        // 远端主播离开频道或掉线时，会触发该回调。
        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                Toast.makeText(this@LiveActivity, "远端主播离开频道或掉线",Toast.LENGTH_SHORT).show()
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
        mLocalVideoView= RtcEngine.CreateRendererView(baseContext)
//        mLocalView.setZOrderMediaOverlay(true)
        mBinding.flVideoLayout.addView(mLocalVideoView)
        // 设置本地视图。
        val localVideoCanvas = VideoCanvas(mLocalVideoView, VideoCanvas.RENDER_MODE_HIDDEN, 0)
        mRtcEngine.setupLocalVideo(localVideoCanvas)
    }

    private fun joinChannel() {
        // 使用 Token 加入频道。
        mRtcEngine.joinChannel(LiveConstants.TOKEN, mChannelName, "Extra Optional Data", 0)
    }

    // 如果是观众，设置主播视频
    private fun setupRemoteVideo(uid:Int){
        // 创建 SurfaceView 对象。
        mBinding.flVideoLayout.removeAllViews()
        val mLocalView: SurfaceView = RtcEngine.CreateRendererView(baseContext)
        mBinding.flVideoLayout.addView(mLocalView)
        // 设置本地视图。
        val localVideoCanvas = VideoCanvas(mLocalView, VideoCanvas.RENDER_MODE_HIDDEN, uid)
        mRtcEngine.setupRemoteVideo(localVideoCanvas)
    }

    private fun onRemoteUserLeft(){
        mBinding.flVideoLayout.removeAllViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        leaveChannel()
        RtcEngine.destroy()
    }

    private fun leaveChannel() {
        // 离开当前频道。
        mRtcEngine.leaveChannel()
    }
}
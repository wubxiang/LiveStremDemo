package com.xqhy.livestremdemo.screen.share

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.view.TextureView
import com.xqhy.livestremdemo.BaseActivity
import com.xqhy.livestremdemo.LiveConstants
import com.xqhy.livestremdemo.databinding.ActivityScreenLiveRoomBinding


/**
 * Author: wbx
 * Date: 2021/6/4
 * Description:
 * 屏幕共享。支持本地预览
 */

class ShowScreenLiveActivity : BaseActivity() {
    private val mBinding: ActivityScreenLiveRoomBinding by lazy {
        ActivityScreenLiveRoomBinding.inflate(
            layoutInflater
        )
    }

    private val PROJECTION_REQ_CODE = 1 shl 2
    private val DEFAULT_SHARE_FRAME_RATE = 15

    private var mRole = LiveConstants.AUDIENCE
    private var mChannelName = ""

    private var mService: ScreenShareService.ScreenShareBinder? = null
    private var mServiceConnection: VideoInputServiceConnection? = null

    private lateinit var mTextureView: TextureView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(mBinding.root)
        initView()
        setClickListener()

        val params = intent.extras
        params?.apply {
            mRole = getInt(LiveConstants.ROLE)
            mChannelName = getString(LiveConstants.CHANNEL_NAME).toString()
        }

        // Create render view by RtcEngine
        /**Instantiate the view ready to display the local preview screen */
    }

    private fun initView() {
        mTextureView = TextureView(this)

        if (ScreenShareService.IS_SHARING) {
            mBinding.tvScreenShare.text = "停止共享"
            bindVideoService()
        }
    }

    private fun setClickListener() {
        mBinding.ivExit.setOnClickListener {
            finish()
        }

        mBinding.tvScreenShare.setOnClickListener {
            if (ScreenShareService.IS_SHARING) {
                mService?.stopLive()
            } else {
                bindVideoService()
            }
        }
    }

    override fun onDestroy() {
        mService?.leaveLiveRoom()
        unbindVideoService()
        super.onDestroy()
    }

    private fun bindVideoService() {
        val intent = Intent()
        intent.setClass(this, ScreenShareService::class.java)
        startService(intent)

        mServiceConnection = VideoInputServiceConnection()
        bindService(intent, mServiceConnection!!, BIND_AUTO_CREATE)
    }

    private fun unbindVideoService() {
        if (mServiceConnection != null) {
            unbindService(mServiceConnection!!)
            mServiceConnection = null
        }
    }

    private inner class VideoInputServiceConnection : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            mService = iBinder as ScreenShareService.ScreenShareBinder

            mService?.apply {
                if (!ScreenShareService.IS_SHARING) {
                    // 通过 MediaProjection 创建 intent 并将 intent 传递给 startActivityForResult()，进行屏幕图像采集
                    val intent =
                        (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()
                    startActivityForResult(intent, PROJECTION_REQ_CODE)
                } else {
                    mService!!.addTextViewListener(mTextureView)
                    mBinding.flVideoLayout.removeAllViews()
                    mBinding.flVideoLayout.addView(mTextureView)
                }
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mService = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROJECTION_REQ_CODE && resultCode == RESULT_OK) {
            data?.let {
                mService?.startLive(LiveConstants.TOKEN, mChannelName)

                mService?.setCallback(object : ScreenShareService.Callback {
                    override fun onJoinChannelSuccess() {
                        // 加入频道成功
                        mService?.setExternalVideoInput(data!!)
                        mService?.addTextViewListener(mTextureView)

                        // Add to the local container
                        mBinding.flVideoLayout.removeAllViews()
                        mBinding.flVideoLayout.addView(mTextureView)

                        mBinding.tvScreenShare.text = "停止共享"
                    }

                    override fun stopLive() {
                        mBinding.flVideoLayout.removeAllViews()
                        mBinding.tvScreenShare.text = "屏幕共享"
                    }
                })
            }
        }
    }
}
package com.xqhy.livestremdemo

import android.Manifest
import android.content.Intent
import android.os.Bundle
import com.xqhy.livestremdemo.databinding.ActivityMainBinding
import com.xqhy.livestremdemo.permission.PermissionSettingUtil
import com.xqhy.livestremdemo.permission.PermissionUtil


class MainActivity : BaseActivity() {
    val mBinding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val PERMISSION_REQ_CODE = 22

    // App 运行时确认麦克风和摄像头设备的使用权限。
    private val PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mBinding.root)

        mBinding.startBroadcastButton.setOnClickListener {
            // 获取权限后，初始化 RtcEngine，并加入频道。
            var permissions = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
            if (PermissionUtil.checkPermisson(this, *PERMISSIONS)) {
                gotoRoleActivity(mBinding.etChannel.text.toString())
            } else {
                PermissionUtil.requestPermisson(this, object : PermissionUtil.Callback {
                    override fun onGranted() {
                        gotoRoleActivity(mBinding.etChannel.text.toString())
                    }

                    override fun onDenied() {
                        showSettingDialog()
                    }

                    override fun onNeverAsk() {
                        showSettingDialog()
                    }
                }, *permissions)
            }
        }
    }

    private fun showSettingDialog() {
        val dialog = BaseDialog(this)
        dialog.setRemindContent(resources.getString(R.string.permission_hint, "相机、麦克风"))
        dialog.setConfirmBtnText(resources.getString(R.string.goto_settting))
        dialog.setOnClickListener(object : BaseDialog.OnClickListener {
            override fun clickConfirm() {
                PermissionSettingUtil.gotoPermissionSettingActivity(this@MainActivity)
            }

            override fun clickCancel() {

            }
        })
        dialog.show()
    }

    private fun gotoRoleActivity(channelName:String) {
        val intent =Intent(this, RoleActivity::class.java)
        intent.putExtra(LiveConstants.CHANNEL_NAME, channelName)
        startActivity(intent)
    }
}
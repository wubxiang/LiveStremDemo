package com.xqhy.livestremdemo

import android.content.Intent
import android.os.Bundle
import com.xqhy.livestremdemo.databinding.ActivityChooseRoleBinding
import com.xqhy.livestremdemo.screen.share.ShowScreenLiveActivity

/**
 * Author: wbx
 * Date: 2021/6/4
 * Description:
 */

class RoleActivity :BaseActivity() {
    private val mBinding:ActivityChooseRoleBinding by lazy { ActivityChooseRoleBinding.inflate(
        layoutInflater
    ) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(mBinding.root)

        mBinding.broadcasterLayout.setOnClickListener {
            gotoLiveActivity(LiveConstants.BROADCASTER)
        }

        mBinding.screenBroadcasterLayout.setOnClickListener {
            gotoScreenLiveActivity(LiveConstants.BROADCASTER)
        }

        mBinding.audienceLayout.setOnClickListener {
            gotoLiveActivity(LiveConstants.AUDIENCE)
        }
    }

    private fun gotoLiveActivity(role: Int) {
        val intent = Intent(intent)
        intent.putExtra(LiveConstants.ROLE,  role)
        intent.setClass(this, LiveActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun gotoScreenLiveActivity(role: Int) {
        val intent = Intent(intent)
        intent.putExtra(LiveConstants.ROLE,  role)
        intent.setClass(this, ShowScreenLiveActivity::class.java)
        startActivity(intent)
        finish()
    }
}
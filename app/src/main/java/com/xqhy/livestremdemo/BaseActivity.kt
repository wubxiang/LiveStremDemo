package com.xqhy.livestremdemo

import androidx.appcompat.app.AppCompatActivity
import com.xqhy.livestremdemo.permission.PermissionUtil

/**
 * Author: wbx
 * Date: 2021/6/4
 * Description:
 */

open class BaseActivity:AppCompatActivity() {
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        PermissionUtil.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }
}
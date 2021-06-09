package com.xqhy.livestremdemo.permission;

import java.util.List;

/**
 * Author: wbx
 * Date: 2020/7/9
 * Description: 权限请求
 */

public class PermissionRequest {

    private final int requestCode;
    private final List<String> permissionsList;
    private final PermissionUtil.Callback callback;

    public PermissionRequest(int requestCode, List<String> permissionsList, PermissionUtil.Callback callback) {
        this.requestCode = requestCode;
        this.permissionsList = permissionsList;
        this.callback = callback;
    }

    public int getRequestCode() {
        return requestCode;
    }

    public PermissionUtil.Callback getCallback() {
        return callback;
    }

    public List<String> getPermissionsList() {
        return permissionsList;
    }
}

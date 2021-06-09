package com.xqhy.livestremdemo.permission;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: wbx
 * Date: 2020/7/9
 * Description: 权限申请工具
 */

public class PermissionUtil {
    private static final List<PermissionRequest> mPermissionRequestList = new ArrayList<>();

    public static boolean checkPermisson(Context context, String... permissions) {
        if (context == null || permissions == null) {
            return false;
        }
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    public static void requestPermisson(Activity activity, Callback callbak, String... permissions) {
        List<String> list = checkDenyPermissionsList(activity, permissions);
        if (list != null && list.size() > 0) {
            PermissionRequest permissionRequest = new PermissionRequest(mPermissionRequestList.size(), list, callbak);
            mPermissionRequestList.add(permissionRequest);
            ActivityCompat.requestPermissions(activity, list.toArray(new String[list.size()]), permissionRequest.getRequestCode());
        } else {
            if(callbak != null) {
                callbak.onGranted();
            }
        }
    }

    private static List<String> checkDenyPermissionsList(Context context, String... permissions) {
        List<String> list = new ArrayList<>();
        for (String permisson : permissions) {
            //在6.0以上和以下都可以判断
            if (ContextCompat.checkSelfPermission(context, permisson) != PackageManager.PERMISSION_GRANTED) {
                list.add(permisson);
            }
        }
        return list;
    }

    /**
     * 请求权限回调
     */
    public static void onRequestPermissionsResult(Activity activity, int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode < mPermissionRequestList.size()) {
            PermissionRequest permissionRequest = mPermissionRequestList.get(requestCode);
            boolean flag = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    flag = false;
                }
            }

            if (flag) {
                if(permissionRequest.getCallback()!=null) {
                    permissionRequest.getCallback().onGranted();
                }
            } else {
                //当所有权限全选不再提醒时才为true
                boolean isAllNotRemind = true;
                for (String permission : permissions) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                        isAllNotRemind = false;
                        break;
                    }
                }
                if (isAllNotRemind) {
                    if(permissionRequest.getCallback()!=null) {
                        permissionRequest.getCallback().onNeverAsk();
                    }
                } else {
                    if(permissionRequest.getCallback()!=null) {
                        permissionRequest.getCallback().onDenied();
                    }
                }

            }

            mPermissionRequestList.remove(requestCode);
        }
    }

    /**
     * 权限回调接口
     */
    public interface Callback {
        /**
         * 权限允许
         */
        void onGranted();

        /**
         * 权限拒绝
         */
        void onDenied();

        /**
         * 用户以前拒绝过权限，且勾选了不再询问，或是手机系统本身禁止了该权限
         */
        void onNeverAsk();
    }
}

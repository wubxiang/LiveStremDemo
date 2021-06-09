package com.xqhy.livestremdemo.permission;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Author: wbx
 * Date: 2021/4/20
 * Description:跳转权限设置界面
 */

public class PermissionSettingUtil {
    private static final String TAG = "MiuiPermissionUtils";

    public static final String SYS_EMUI = "sys_emui";
    public static final String SYS_MIUI = "sys_miui";
    public static final String SYS_FLYME = "sys_flyme";

    private static final String KEY_MIUI_VERSION_CODE = "ro.miui.ui.version.code";
    private static final String KEY_MIUI_VERSION_NAME = "ro.miui.ui.version.name";
    private static final String KEY_MIUI_INTERNAL_STORAGE = "ro.miui.internal.storage";
    private static final String KEY_EMUI_API_LEVEL = "ro.build.hw_emui_api_level";
    private static final String KEY_EMUI_VERSION = "ro.build.version.emui";
    private static final String KEY_EMUI_CONFIG_HW_SYS_VERSION = "ro.confg.hw_systemversion";
    private static final String ROM_MIUI_V5 = "V5";
    private static final String ROM_MIUI_PROPERTY = "ro.miui.ui.version.name";
    private static final String MIUI_SETTING_PACKAGE_NAME = "com.miui.securitycenter";
    private static final String MIUI_SETTING_CLASS_NAME = "com.miui.permcenter.permissions.AppPermissionsEditorActivity";
    private static final String MIUI_SETTING_V5_CLASS_NAME = "com.miui.securitycenter.permission.AppPermissionsEditor";

    private static final String RO_BUILD_DISPLAY_ID = "ro.build.display.id";

    /**
     * 跳转权限设置界面
     */
    public static void gotoPermissionSettingActivity(final Activity activity) {
        if (null == activity) {
            return;
        }

        if (activity.isFinishing()) {
            return;
        }

        String packageName;
        try {
            packageName = activity.getPackageName();
        } catch (Exception e) {
            return;
        }

        if (TextUtils.isEmpty(packageName)) {
            return;
        }

        String sysInfo = getDeviceSystem();

        if (SYS_EMUI.equals(sysInfo)) {
            // 华为(EMUI)
            gotoEmuiPermissionSettingActivity(activity, packageName);
        } else if (SYS_MIUI.equals(sysInfo)) {
            //Xiaomi(MIUI)
            gotoMiuiPermissionSettingActivity(activity, packageName);
        } else if (SYS_FLYME.equals(sysInfo)) {
            //MEIZU(Flyme)
            gotoMeizuPermissionActivity(activity, packageName);
        } else {
            gotoAppDetailSettingActivity(activity, packageName);
        }
    }

    /**
     * 获取手机系统信息（适配了华为、小米、魅族机型）
     *
     * @return
     */
    private static String getDeviceSystem() {
        if (isMIUI()) {
            return SYS_MIUI; //小米
        }

        String SYS = "";

        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream(new File(Environment.getRootDirectory(), "build.prop")));

            if (null != prop) {
                if (prop.getProperty(KEY_MIUI_VERSION_CODE, null) != null
                        || prop.getProperty(KEY_MIUI_VERSION_NAME, null) != null
                        || prop.getProperty(KEY_MIUI_INTERNAL_STORAGE, null) != null) {
                    SYS = SYS_MIUI;//小米
                } else if (prop.getProperty(KEY_EMUI_API_LEVEL, null) != null
                        || prop.getProperty(KEY_EMUI_VERSION, null) != null
                        || prop.getProperty(KEY_EMUI_CONFIG_HW_SYS_VERSION, null) != null) {
                    SYS = SYS_EMUI;//华为
                } else if (isFlyme()) {
                    SYS = SYS_FLYME;//魅族
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return SYS;
        }

        return SYS;
    }

    /**
     * 检查手机是否是miui
     *
     * @return
     */
    private static boolean isMIUI() {
        boolean isMiui = false;

        String deviceInfo = Build.MANUFACTURER;
        if (!TextUtils.isEmpty(deviceInfo)) {
            if ("Xiaomi".equals(deviceInfo)) {
                isMiui = true;
                return isMiui;
            }
        }

        return isMiui;
    }

    /**
     * 检查手机是否是魅族
     *
     * @return
     */
    private static boolean isFlyme() {
        try {
            // Invoke Build.hasSmartBar()
            final Method method = Build.class.getMethod("hasSmartBar");
            if (null != method) {
                return true;
            }
        } catch (Exception e) {
            Log.i(TAG, "获取hasSmartBar失败" + e.getMessage());
        }

        String meizuFlag = getMeizuFlymeOSFlag();
        if (!TextUtils.isEmpty(meizuFlag) && meizuFlag.toLowerCase(Locale.ENGLISH).contains("flyme")) {
            return true;
        }

        return false;
    }

    private static String getMeizuFlymeOSFlag() {
        return getSystemProperty(RO_BUILD_DISPLAY_ID, "");
    }

    private static String getSystemProperty(String key, String defaultValue) {
        try {
            Class<?> clz = Class.forName("android.os.SystemProperties");
            Method get = clz.getMethod("get", String.class, String.class);
            return (String) get.invoke(clz, key, defaultValue);
        } catch (Exception e) {
        }
        return defaultValue;
    }

    /**
     * 通过系统属性返回手机系统版本（经测试小米手机V5版本权限设置界面跳转是有区别的）
     *
     * @return：V5、V6等
     */
    private static String getProperty(final String propName) {
        String property = "null";
        if (!isMIUI() || TextUtils.isEmpty(propName)) {
            return property;
        }

        try {
            Class<?> spClazz = Class.forName("android.os.SystemProperties");
            Method method = spClazz.getDeclaredMethod("get", String.class, String.class);
            property = (String) method.invoke(spClazz, propName, null);
        } catch (Exception e) {
        }
        return property;
    }

    private static void gotoEmuiPermissionSettingActivity(Context context, String pkgName) {
        Intent intent = new Intent(pkgName);
        ComponentName comp = new ComponentName("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.MainActivity");
        intent.setComponent(comp);
        if (hasActivity(context, intent)) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else {
            gotoAppDetailSettingActivity(context, pkgName);
        }
    }

    /**
     * 打开小米手机权限设置界面
     */
    private static void gotoMiuiPermissionSettingActivity(Context context, String pkgName) {
        Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
        if (ROM_MIUI_V5.equals(getProperty(ROM_MIUI_PROPERTY))) {
            PackageInfo pInfo;
            try {
                pInfo = context.getPackageManager().getPackageInfo(pkgName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                gotoAppDetailSettingActivity(context, pkgName);
                return;
            }

            if (null != pInfo) {
                intent.putExtra("extra_package_uid", pInfo.applicationInfo.uid);
            }
            intent.setClassName(MIUI_SETTING_PACKAGE_NAME, MIUI_SETTING_V5_CLASS_NAME);
        } else {
            intent.setClassName(MIUI_SETTING_PACKAGE_NAME, MIUI_SETTING_CLASS_NAME);
            intent.putExtra("extra_pkgname", pkgName);
        }

        if (hasActivity(context, intent)) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else {
            gotoAppDetailSettingActivity(context, pkgName);
        }
    }

    /**
     * 打开魅族手机权限设置界面
     */
    private static void gotoMeizuPermissionActivity(Context context, String pkgName) {
        Intent intent = new Intent("com.meizu.safe.security.SHOW_APPSEC");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra("packageName", pkgName);
        if (hasActivity(context, intent)) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else {
            gotoAppDetailSettingActivity(context, pkgName);
        }
    }

    /**
     * 跳转应用详情设置界面
     */
    private static void gotoAppDetailSettingActivity(Context context, String pkgName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", pkgName, null);
        intent.setData(uri);

        if (hasActivity(context, intent)) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else {
            Log.i(TAG, "gotoAppDetailSettingActivity intent is not available!");
        }
    }

    /**
     * 检查是否有这个activity
     *
     * @param context
     * @param intent
     * @return
     */
    private static boolean hasActivity(Context context, Intent intent) {
        if (null == context || null == intent) {
            return false;
        }

        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(
                intent, PackageManager.MATCH_DEFAULT_ONLY);
        return (list != null && list.size() > 0);
    }
}

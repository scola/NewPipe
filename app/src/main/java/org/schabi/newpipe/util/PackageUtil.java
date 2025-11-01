package org.schabi.newpipe.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;


public final class PackageUtil {
    private static final String TAG = "PackageUtil";
    private static final long TIME_VERIFY = 24 * 60 * 60 * 1000;

    private PackageUtil() {
    }

    public static boolean isWithinVerifyTime(final Context context) {
        try {
            long firstInstallTime = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .firstInstallTime;

            long currentTime = System.currentTimeMillis();

            // 判断是否在安装时间
            boolean isWithinVerifyTime = (currentTime - firstInstallTime) <= TIME_VERIFY;

            if (isWithinVerifyTime) {
                Log.d(TAG, "安装时间不到24小时");
            } else {
                Log.e(TAG, "安装时间超过24小时");
            }
            return isWithinVerifyTime;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }


}

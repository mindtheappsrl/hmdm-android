/*
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hmdm.launcher.pro;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.util.Log;
import android.view.View;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.util.LegacyUtils;

import java.util.Calendar;

/**
 * These functions are available in Pro-version only
 * In a free version, the class contains stubs
 */
public class ProUtils {

    public static boolean isPro() {
        return false;
    }

    public static boolean kioskModeRequired(Context context) {
        ServerConfig config = SettingsHelper.getInstance(context).getConfig();
        return config != null && config.isKioskMode();
    }

    public static void initCrashlytics(Context context) {
        // Stub
    }

    public static void sendExceptionToCrashlytics(Throwable e) {
        // Stub
    }

    // Start the service checking if the foreground app is allowed to the user (by usage statistics)
    public static boolean checkAccessibilityService(Context context) {
        // Stub
        return true;
    }

    // Pro-version
    public static boolean checkUsageStatistics(Context context) {
        // Stub
        return true;
    }

    // Add a transparent view on top of the status bar which prevents user interaction with the status bar
    public static View preventStatusBarExpansion(Activity activity) {
        // Stub
        return null;
    }

    // Add a transparent view on top of a swipeable area at the right (opens app list on Samsung tablets)
    public static View preventApplicationsList(Activity activity) {
        // Stub
        return null;
    }

    public static View createKioskUnlockButton(Activity activity) {
        // Stub
        return null;
    }

    public static boolean isKioskAppInstalled(Context context) {
        ServerConfig config = SettingsHelper.getInstance(context).getConfig();
        return config != null && config.getMainApp() != null &&
                context.getPackageManager().getLaunchIntentForPackage(config.getMainApp()) != null;
    }

    public static boolean isKioskModeRunning(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return activityManager != null &&
                activityManager.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
    }

    public static Intent getKioskAppIntent(String kioskApp, Activity activity) {
        if (kioskApp == null || kioskApp.trim().isEmpty()) {
            return null;
        }
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(kioskApp);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        return intent;
    }

    // Start COSU kiosk mode
    public static boolean startCosuKioskMode(String kioskApp, Activity activity, boolean enableSettings) {
        Intent kioskIntent = getKioskAppIntent(kioskApp, activity);
        if (kioskIntent == null) {
            Log.e(Const.LOG_TAG, "Kiosk application is missing or has no launchable activity: " + kioskApp);
            return false;
        }

        DevicePolicyManager dpm =
                (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isDeviceOwnerApp(activity.getPackageName())) {
            Log.e(Const.LOG_TAG, "Kiosk mode requires Headwind MDM to be Device Owner");
            return false;
        }

        try {
            updateKioskAllowedApps(kioskApp, activity, enableSettings);
            updateKioskOptions(activity);
            if (!isKioskModeRunning(activity)) {
                activity.startLockTask();
            }
            activity.startActivity(kioskIntent);
            Log.i(Const.LOG_TAG, "Custom kiosk mode started for " + kioskApp);
            return true;
        } catch (Exception e) {
            Log.e(Const.LOG_TAG, "Unable to start custom kiosk mode", e);
            return false;
        }
    }

    // Set/update kiosk mode options (lock tack features)
    public static void updateKioskOptions(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        DevicePolicyManager dpm =
                (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isDeviceOwnerApp(activity.getPackageName())) {
            return;
        }

        ServerConfig config = SettingsHelper.getInstance(activity).getConfig();
        int features = DevicePolicyManager.LOCK_TASK_FEATURE_NONE;
        if (config != null) {
            if (Boolean.TRUE.equals(config.getKioskHome())) {
                features |= DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
            }
            if (Boolean.TRUE.equals(config.getKioskRecents())) {
                features |= DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW;
            }
            if (Boolean.TRUE.equals(config.getKioskNotifications())) {
                features |= DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS;
            }
            if (Boolean.TRUE.equals(config.getKioskSystemInfo())) {
                features |= DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO;
            }
            if (Boolean.TRUE.equals(config.getKioskKeyguard())) {
                features |= DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD;
            }
        }
        dpm.setLockTaskFeatures(LegacyUtils.getAdminComponentName(activity), features);
    }

    // Update app list in the kiosk mode
    public static void updateKioskAllowedApps(String kioskApp, Activity activity, boolean enableSettings) {
        DevicePolicyManager dpm =
                (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isDeviceOwnerApp(activity.getPackageName())) {
            return;
        }
        String[] packages = enableSettings ?
                new String[]{activity.getPackageName(), kioskApp, "com.android.settings"} :
                new String[]{activity.getPackageName(), kioskApp};
        dpm.setLockTaskPackages(LegacyUtils.getAdminComponentName(activity), packages);
    }

    public static void unlockKiosk(Activity activity) {
        try {
            if (isKioskModeRunning(activity)) {
                activity.stopLockTask();
            }
            DevicePolicyManager dpm =
                    (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(activity.getPackageName())) {
                dpm.setLockTaskPackages(LegacyUtils.getAdminComponentName(activity), new String[0]);
            }
            Log.i(Const.LOG_TAG, "Custom kiosk mode stopped");
        } catch (Exception e) {
            Log.e(Const.LOG_TAG, "Unable to stop custom kiosk mode", e);
        }
    }

    public static void processConfig(Context context, ServerConfig config) {
        if (config == null || !config.isKioskMode() || config.getMainApp() == null) {
            return;
        }
        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
            ComponentName admin = LegacyUtils.getAdminComponentName(context);
            dpm.setLockTaskPackages(admin,
                    new String[]{context.getPackageName(), config.getMainApp()});
        }
    }

    public static void processLocation(Context context, Location location, String provider) {
        // Stub    
    }

    public static String getAppName(Context context) {
        return context.getString(R.string.app_name);
    }

    public static String getCopyright(Context context) {
        return "(c) " + Calendar.getInstance().get(Calendar.YEAR) + " " + context.getString(R.string.vendor);
    }
}

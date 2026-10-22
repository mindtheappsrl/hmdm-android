package com.hmdm.launcher.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;

import com.hmdm.WifiResult;
import com.hmdm.launcher.BuildConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Saves a Wi-Fi network as Device Owner on request of the kiosk app (IMdmApi.setWifi).
 * SSID and password must never be logged.
 */
@SuppressWarnings("deprecation")
public class WifiSetter {
    private static final long TIMEOUT_MS = 60000;
    private static final String PREFS = "wifi_setter";
    private static final String KEY_LAST = "last";

    /** The caller must be one of the allowed packages, signed with one of the allowed certificates. */
    public static boolean isCallerAllowed(Context context, int uid) {
        String[] packages = context.getPackageManager().getPackagesForUid(uid);
        if (packages == null) {
            return false;
        }
        for (String pkg : packages) {
            if (csvContains(BuildConfig.WIFI_ALLOWED_PACKAGES, pkg) && hasAllowedCert(context, pkg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAllowedCert(Context context, String pkg) {
        // ponytail: no certificate configured = package name only; set WIFI_ALLOWED_CERTS to also check the signature
        if (BuildConfig.WIFI_ALLOWED_CERTS.trim().isEmpty()) {
            return true;
        }
        try {
            PackageManager pm = context.getPackageManager();
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                signatures = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                        .signingInfo.getApkContentsSigners();
            } else {
                PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            for (Signature s : signatures) {
                if (csvContains(BuildConfig.WIFI_ALLOWED_CERTS, sha256Hex(s.toByteArray()))) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Unknown package: not allowed
        }
        return false;
    }

    static boolean csvContains(String csv, String value) {
        for (String item : csv.split(",")) {
            if (!item.trim().isEmpty() && item.trim().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    static String sha256Hex(byte[] data) {
        try {
            StringBuilder sb = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // Blocking (up to TIMEOUT_MS). One request at a time.
    public static synchronized int setWifi(Context context, String ssid, String password, int security, boolean hidden) {
        if (ssid == null || ssid.isEmpty() || security < WifiResult.SECURITY_OPEN || security > WifiResult.SECURITY_WPA3
                || (security != WifiResult.SECURITY_OPEN && (password == null || password.isEmpty()))) {
            return WifiResult.ERROR;
        }
        if (!Utils.isDeviceOwner(context)) {
            return WifiResult.NOT_ALLOWED;
        }
        if (security != WifiResult.SECURITY_OPEN && security != WifiResult.SECURITY_WEP && password.length() < 8) {
            return WifiResult.WRONG_PASSWORD;
        }

        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (!wm.isWifiEnabled()) {
            wm.setWifiEnabled(true);
        }

        String quoted = "\"" + ssid + "\"";
        // Passwords of saved networks cannot be read back: remember a hash of the last request instead
        String fingerprint = sha256Hex((ssid + "\n" + password + "\n" + security + "\n" + hidden).getBytes(StandardCharsets.UTF_8));
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        WifiInfo current = wm.getConnectionInfo();
        if (fingerprint.equals(prefs.getString(KEY_LAST, null)) && current != null
                && quoted.equals(current.getSSID()) && isValidated(cm)) {
            return WifiResult.OK;
        }

        int previousId = current != null ? current.getNetworkId() : -1;
        int existingId = -1;
        List<WifiConfiguration> saved = wm.getConfiguredNetworks();
        if (saved != null) {
            for (WifiConfiguration c : saved) {
                if (quoted.equals(c.SSID)) {
                    existingId = c.networkId;
                }
            }
        }

        WifiConfiguration config = buildConfig(quoted, password, security, hidden);
        int id;
        if (existingId >= 0) {
            config.networkId = existingId;
            id = wm.updateNetwork(config);
        } else {
            id = wm.addNetwork(config);
        }
        if (id < 0) {
            return WifiResult.ERROR;
        }

        final AtomicBoolean authError = new AtomicBoolean();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1) == WifiManager.ERROR_AUTHENTICATING) {
                    authError.set(true);
                }
            }
        };
        context.registerReceiver(receiver, new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
        int result;
        try {
            wm.disconnect();
            wm.enableNetwork(id, true);
            wm.reconnect();
            wm.startScan();
            result = waitForConnection(wm, cm, id, authError);
            if (result == WifiResult.NETWORK_NOT_FOUND && isVisible(wm, ssid)) {
                result = WifiResult.ERROR;
            }
        } finally {
            context.unregisterReceiver(receiver);
        }

        if (result == WifiResult.OK) {
            prefs.edit().putString(KEY_LAST, fingerprint).apply();
        } else {
            // Back to the previous network.
            // ponytail: if the new SSID is the one already saved, its old password is overwritten and cannot be restored.
            if (existingId < 0) {
                wm.removeNetwork(id);
            }
            if (previousId >= 0 && previousId != id) {
                wm.enableNetwork(previousId, true);
                wm.reconnect();
            }
        }
        return result;
    }

    // Removes every saved network and restarts the Wi-Fi so that the tablet does not reconnect. Does not block.
    public static synchronized int forgetWifi(Context context) {
        if (!Utils.isDeviceOwner(context)) {
            return WifiResult.NOT_ALLOWED;
        }
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wm.disconnect();
        List<WifiConfiguration> saved = wm.getConfiguredNetworks();
        if (saved != null) {
            for (WifiConfiguration c : saved) {
                wm.removeNetwork(c.networkId);
            }
        }
        // ponytail: networks saved by other apps cannot be removed (removeNetwork returns false), they stay saved
        wm.setWifiEnabled(false);
        wm.setWifiEnabled(true);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LAST).apply();
        return WifiResult.OK;
    }

    private static int waitForConnection(WifiManager wm, ConnectivityManager cm, int id, AtomicBoolean authError) {
        boolean connected = false;
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (authError.get()) {
                return WifiResult.WRONG_PASSWORD;
            }
            WifiInfo info = wm.getConnectionInfo();
            connected = info != null && info.getNetworkId() == id && info.getSupplicantState() == SupplicantState.COMPLETED;
            if (connected && isValidated(cm)) {
                return WifiResult.OK;
            }
            SystemClock.sleep(1000);
        }
        return connected ? WifiResult.NO_INTERNET : WifiResult.NETWORK_NOT_FOUND;
    }

    private static boolean isValidated(ConnectivityManager cm) {
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private static boolean isVisible(WifiManager wm, String ssid) {
        List<ScanResult> results = wm.getScanResults();
        if (results != null) {
            for (ScanResult r : results) {
                if (ssid.equals(r.SSID)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static WifiConfiguration buildConfig(String quoted, String password, int security, boolean hidden) {
        WifiConfiguration c = new WifiConfiguration();
        c.SSID = quoted;
        c.hiddenSSID = hidden;
        String quotedPassword = "\"" + password + "\"";
        switch (security) {
            case WifiResult.SECURITY_WEP:
                c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                c.wepKeys[0] = quotedPassword;
                c.wepTxKeyIndex = 0;
                break;
            case WifiResult.SECURITY_WPA3:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    c.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
                } else {
                    c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SAE);
                }
                c.preSharedKey = quotedPassword;
                break;
            case WifiResult.SECURITY_WPA:
                c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                c.preSharedKey = quotedPassword;
                break;
            default:
                c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }
        return c;
    }
}

package com.hmdm;

/**
 * Outcome of IMdmApi.setWifi() and security types accepted by it.
 * Duplicated in the launcher (app/src/main/java/com/hmdm/WifiResult.java), like IMdmApi.aidl: keep identical.
 */
public class WifiResult {
    public static final int OK = 0;
    public static final int WRONG_PASSWORD = 1;
    public static final int NETWORK_NOT_FOUND = 2;
    public static final int NO_INTERNET = 3;
    public static final int NOT_ALLOWED = 4;
    public static final int ERROR = 5;

    public static final int SECURITY_OPEN = 0;
    public static final int SECURITY_WPA = 1;   // WPA / WPA2 personal
    public static final int SECURITY_WEP = 2;
    public static final int SECURITY_WPA3 = 3; // WPA3 personal (SAE)
}

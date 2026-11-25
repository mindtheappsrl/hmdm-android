# Headwind MDM: free and open-source MDM launcher

A Powerful Open Source Platform to Manage your Enterprise Android Devices

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.hmdm.launcher/)

## Starting work

Open the project directory in Android Studio (use default settings).

## Debugging on the device

1. Connect the device by USB
2. Click "Run 'App'" icon in Android Studio
3. After successful run, add device owner rights to the app (optional).

    Run in the console
   
    `adb shell`

    Run the command in the adb console
   
    `dpm set-device-owner com.hmdm.launcher/.AdminReceiver`

## Timecloud: device location always on (AB#5480)

On the Timecloud kiosks Android needs the system location to be on to scan Wi-Fi networks. When the launcher is the
Device Owner, `Utils.keepLocationEnabled()` turns the location on (`DevicePolicyManager.setLocationEnabled` on API 30+,
`LOCATION_MODE` before) and, from API 28, adds `DISALLOW_CONFIG_LOCATION` so the user cannot turn it off from Settings.
It runs at every launcher start (`Initializer.init`) and at every configuration update
(`Initializer.applyEarlyNonInteractivePolicies`), so already enrolled kiosks get it after the launcher update (v6.45).
It is always on: there is no configuration entry and the server "GPS" setting is ignored.

To check on a device: `adb shell settings get secure location_mode` must not be `0`.

## Building the APK

The release APK is signed with the Timecloud totem key (the same as the Kiosk app), kept outside the repository:

- keystore: `Private/AndroidKeys/timecloud-totem.jks`
- alias and passwords: `Private/AndroidKeys/props/timecloudtotemkey.properties` (`keyAlias`, `keyPassword`, `storePassword`)

Both paths are relative to the folder that contains this repository (for example `C:\PROGETTI\Private\AndroidKeys\...` if the
repository is in `C:\PROGETTI`). If one of the two files is missing, or the properties file lacks one of the three entries, the
release build stops and says which one: it never falls back to the debug key.

```
gradlew :app:assembleOpensourceRelease
```

The signed APK is in `app/build/outputs/apk/opensource/release/`. Check it with `apksigner verify --print-certs`: the SHA-256 must be
the one in `WIFI_ALLOWED_CERTS` (`app/build.gradle`), the Kiosk app must be signed with the same key, otherwise `setWifi()` refuses it.
The launcher is the Device Owner of the totems: it only updates over an app signed with the same key.

## Building the library

1. Select the 'lib' item in the project tree
2. Select Build - Make Module 'lib'
3. Find the library in the 'lib/build/outputs/aar' directory

## Building the project in the command line

1. Install the Gradle plugin v5.1.1 (Linux only)
2. Install Android Studio or download the standalone Android SDK
3. Create the file local.properties and store the SDK location in this file:

sdk.dir=/path/to/sdk

4. Run the command

gradlew build

5. Find the resulting APK in the app/build/outputs/apk/release/ directory.


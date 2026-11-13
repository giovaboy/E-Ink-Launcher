# InkPalm Tools

A small system app for the Moaan **inkPalm 5** e-reader (also sold as *my mini read* EPD105,
Android 8.1), companion to [E-Ink Launcher](../README.md). The inkPalm 5 firmware is signed with
the public AOSP test keys: an APK signed with the AOSP *platform* test key and declaring
`android:sharedUserId="android.uid.system"` runs there as the system user. This app uses that
to do what the firmware doesn't let normal apps, or you, do:

- **Keep apps running**: the firmware force-stops a non-system app 30 seconds after it leaves the
  screen. This turns that off for the apps you choose (E-Ink Launcher with its FTP server, your
  keyboard...). It stays set after a restart.
- **Disable apps** you don't use, vendor apps included (a suggested list for the inkPalm 5), so
  they never start; re-enable them any time.
- **Wi-Fi on in standby**: the firmware turns Wi-Fi off when the reader sleeps and back on at
  wake-up, so it has to reconnect every time. This leaves it on (it costs some battery).
- **Moaan settings**: an icon in the app list that opens the firmware's own settings menu (App
  freeze, DPI per app, logo key, refresh mode, sleep time...), which otherwise only the stock
  launcher can open.
- **Save diagnostics** to `Download/`: RAM, disabled apps, the background-kill setting of every
  app, memory of every process, `dumpsys meminfo` and the system log.
- **Reboot into recovery**.

It installs like any APK, from a file manager. On a firmware that is not signed with the test
keys Android refuses to install it, so it cannot do anything there. It is developed and tested
only on the inkPalm 5 with firmware V11 (`MAS_EPD105_L8AM105_V11_240307`).

## Is my firmware signed with the test keys?

The build fingerprint ends in `test-keys`, and the signing certificate of the `android` package
has SHA-256 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`. E-Ink
Launcher's device report shows both: in its settings, tap (i) and long press the version line.

## How it works

The Moaan firmware's own code (`services.jar`, `PackageManagerService` and
`ActivityManagerService`) was read to find these settings:

- **Background kill.** `computeOomAdjLocked` queues a non-system app that leaves the screen when
  the app's *freeze* setting is 1, and force-stops it 30 seconds later (`BEGIN_MOGU_KILL_APP` in
  the log). The setting is saved per package in `package-restrictions.xml` and changed with the
  vendor method `IPackageManager.setApplicationFreezeSetting`, which skips its permission check
  for the system user. The firmware's *App freeze* list changes the same setting.
- **Wi-Fi in standby.** `PowerManagerService` turns Wi-Fi off on sleep when the
  `Settings.Global` key `com.moan.closewifi` is 1, or unset. Normal apps can't write it; the
  system user can.
- **Moaan settings.** The menu is `com.moan.launcher/.settings.MySettingsActivity`, which is not
  exported; the system user may start it anyway.

The vendor methods are called by reflection, so on other firmwares the app reports that they
are missing instead of failing.

## Disabling apps

Disabled apps stay installed: they just don't start and don't appear in launchers. It is the
safer, reversible version of `pm uninstall --user 0`. The suggested list is what an inkPalm 5
owner removed on the same firmware build
([jacob.earth](https://jacob.earth/post/2025/inkpalm-5-mini-ereader-setup/)): readers,
keyboard, browser and store for the Chinese market, and factory test tools. Apps the system
or this app need can't be disabled: the stock launcher (it holds the Moaan settings and the
forgotten-password screens), the keyboard in use and the home app are among them.

Android 8.1 still starts *persistent* system apps at boot even when they are disabled. Among
the suggested apps, the telephony service (`com.android.phone`) and Allwinner's *Auto run*
(`com.softwinner.awmanager`) keep running after a restart. Disabling them is harmless but frees
no memory.

## adb over Wi-Fi: not possible

Even as the system user, SELinux refuses `service.adb.tcp.port` and `persist.adb.tcp.port`
(only adb's shell and root may set them), so adb over Wi-Fi needs root.

## Build

JDK 17 and the Android SDK (platform 34). From this folder:

```sh
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk, platform-signed
./gradlew testDebugUnitTest   # Robolectric tests
```

Both builds are signed with the platform test key in [`keys/`](keys/README.md).

## Warning

A system app can change anything on the device. This one only does what is listed above, and
everything it changes can be undone from the app itself, but use it at your own risk.

## License

[Apache License 2.0](LICENSE). The keys in `keys/` come from AOSP (Apache License 2.0).

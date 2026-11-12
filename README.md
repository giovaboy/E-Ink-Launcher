# InkPalm Tools

A small system app for e-readers whose firmware is signed with the public AOSP test keys, such
as the Moaan **inkPalm 5** (EPD105, Android 8.1) and its rebrands. On those devices the
firmware hides developer options, but an APK signed with the AOSP *platform* test key and
declaring `android:sharedUserId="android.uid.system"` runs as the system user. This app uses
that to:

- **disable apps** you don't use, vendor apps included (a suggested list for the inkPalm 5), so
  they never run and leave their RAM free; re-enable them any time;
- **save diagnostics** to `Download/`: RAM, memory of every process, `dumpsys meminfo` and the
  system log;
- **reboot into recovery**.

It installs like any APK, from a file manager. On a firmware that is not signed with the test
keys Android refuses to install it, so it cannot do anything there.

## Is my firmware signed with the test keys?

The build fingerprint ends in `test-keys` (Settings > About, or the crash/report screens of
[E-Ink Launcher](https://github.com/giovaboy/E-Ink-Launcher)), and the signing certificate of
the `android` package has SHA-256
`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`. E-Ink Launcher's device
report (long press on the version line in Help & About) shows both.

## Disabling apps

Disabled apps stay installed: they just never start and don't appear in launchers. It is the
safer, reversible version of `pm uninstall --user 0`. The suggested list is what an inkPalm 5
owner removed on the same firmware build
([jacob.earth](https://jacob.earth/post/2025/inkpalm-5-mini-ereader-setup/)): Chinese
readers and keyboard, vendor browser and store, factory test tools, and the telephony service
(the device has no modem). Apps the system or this app need, the keyboard in use and the home
app can't be disabled.

## adb over Wi-Fi: not possible

Even as the system user, SELinux refuses `service.adb.tcp.port` and `persist.adb.tcp.port`
(only adb's shell and root may set them), so adb over Wi-Fi needs root.

## Build

JDK 17 and the Android SDK (platform 34):

```sh
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk, platform-signed
./gradlew testDebugUnitTest   # Robolectric tests
```

## Warning

A system app can change anything on the device. This one only does what is listed above, but
use it at your own risk.

## License

[Apache License 2.0](LICENSE). The keys in `keys/` come from AOSP (Apache License 2.0).

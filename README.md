# InkPalm Tools

A small system app for e-readers whose firmware is signed with the public AOSP test keys, such
as the Moaan **inkPalm 5** (EPD105, Android 8.1) and its rebrands. On those devices the
firmware hides developer options, but an APK signed with the AOSP *platform* test key and
declaring `android:sharedUserId="android.uid.system"` runs as the system user. This app uses
that to:

- turn **adb over Wi-Fi** on and off (no USB cable needed), and show whether adb is listening;
- **reboot into recovery**.

It installs like any APK, from a file manager. On a firmware that is not signed with the test
keys Android refuses to install it, so it cannot do anything there.

## Is my firmware signed with the test keys?

The build fingerprint ends in `test-keys` (Settings > About, or the crash/report screens of
[E-Ink Launcher](https://github.com/giovaboy/E-Ink-Launcher)), and the signing certificate of
the `android` package has SHA-256
`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`. E-Ink Launcher's device
report (long press on the version line in Help & About) shows both.

## adb over Wi-Fi

Turning it on sets `service.adb.tcp.port` and `persist.adb.tcp.port` to 5555 and restarts adb
(the same way the USB debugging switch does). Then, from a computer on the same network:

```sh
adb connect <reader-ip>:5555
```

and allow the computer on the reader. Anyone on your network can *try* to connect, but only
computers you allowed get in. Turn it off when you don't need it.

## Build

JDK 17 and the Android SDK (platform 34):

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk, platform-signed
```

## Warning

A system app can change anything on the device. This one only does what is listed above, but
use it at your own risk.

## License

[Apache License 2.0](LICENSE). The keys in `keys/` come from AOSP (Apache License 2.0).

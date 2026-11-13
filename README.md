# E-Ink Launcher

A home screen for the **Moaan inkPalm 5** e-reader (also sold as *my mini read* EPD105), drawn
in pure black and white for e-ink: no gray backdrops, no animations, big touch targets, and as
little RAM as possible while you read.

> **Made for one device.** E-Ink Launcher is developed and tested only on the inkPalm 5 with
> the Moaan firmware V11 (`MAS_EPD105_L8AM105_V11_240307`, Android 8.1, 720×1280). Its app id,
> its front light and contrast panels and its workarounds target that firmware. It installs on
> other Android devices (4.2 and later) and the plain home screen may well work there, but
> nothing else is tested, and the device-specific parts won't appear or won't work.

<p>
  <img src="doc/home.png" width="240" alt="Home screen with the reading card, the app grid, the quick bar and the bottom bar">
  <img src="doc/settings.png" width="240" alt="Settings shown as a centered card">
  <img src="doc/front-light.png" width="240" alt="Front light panel with brightness and warmth">
</p>

## Features

- **Continue reading with KOReader**: a card above the apps shows the book last opened in
  [KOReader](https://koreader.rocks/) (cover, title, author and progress) and reopens it with
  one tap. It reads KOReader's own files; nothing to configure. Hidden when KOReader isn't
  installed.
- **App grid** with adjustable columns, rows and text size, custom sort orders and priorities,
  hidden apps, custom icons and Android shortcuts.
- **Quick bar** with Wi-Fi, the FTP server, front light, contrast, refresh and screen lock on one
  centered row. Each item can be hidden; the icons are evenly spaced and stay put when a label
  changes (the Wi-Fi network name, for example). Two icon styles: framed or line.

  <img src="doc/quick-bar.png" width="480" alt="Quick bar icons in the framed style (top) and the line style with Wi-Fi and FTP off (bottom)">

- **Bottom bar** with page, date and time, battery (icon, ring or text) and settings. If you turn
  on the system status bar, which already shows time and battery, the bottom bar goes away and
  settings move to the end of the quick bar.
- **Built-in FTP server** to copy books and APKs from a computer over Wi-Fi: switch it on from
  the quick bar, which then shows the address to connect to (long press to see it again).
- **Low memory use**: only the icons on screen are loaded, scaled to the cell, and everything is
  released while another app (your reader) is in front. The (i) button in the settings shows how
  much the launcher uses, in the foreground and in the background.
- **Languages**: English, Italian, Chinese, Polish and Korean, selectable in the launcher itself.

### inkPalm 5 specifics

On the inkPalm 5 the launcher also replaces the firmware's own panels, which are drawn
off-center:

- a **front light** panel (brightness and warmth, 0–24 each) and a **contrast** panel (6 levels),
  set the same way the firmware's SystemUI does;
- one-tap lock, refresh and Wi-Fi toggles that work around the firmware's quirks.

The firmware force-stops an app 30 seconds after it leaves the screen unless the app is
*unfrozen* in its *App freeze* list. Unfreeze E-Ink Launcher there if you want its FTP server to
keep running while you read. The list is in the settings of the firmware's stock launcher, which
other launchers can't open: do it before choosing E-Ink Launcher as Home, or switch back to the
stock launcher for a moment. [InkPalm Tools](#inkpalm-tools-optional) does the same from any
launcher.

The application id is `com.moan.moanwm` because the firmware only lets whitelisted packages use
some of its features (e.g. *App info*).

## Install

Download the APK from the [releases](../../releases), copy it to the device (for example with
the launcher's FTP server or over USB) and open it with a file manager. Press Home and choose
*E-Ink Launcher*, then *Always*.

On the inkPalm 5 developer options and adb are disabled by the firmware, so there is no other
way to install it.

## InkPalm Tools (optional)

The [releases](../../releases) also include **InkPalm Tools**, a small companion app for the
same device. It does what the firmware doesn't let normal apps, or you, do:

- **Keep apps running**: turns off the firmware's 30-second background kill for the apps you
  choose (E-Ink Launcher with its FTP server, your keyboard...). It stays set after a restart.
- **Disable apps**: turns off preinstalled apps you don't use (app stores and readers for the
  Chinese market, the browser, phone services on a device without a modem...) to free memory.
  It shows a suggested list first. Disabled apps stay installed and can be turned back on;
  apps the device needs can't be disabled.
- **Wi-Fi on in standby**: keeps Wi-Fi connected while the reader sleeps, so it doesn't have to
  reconnect at every wake-up. The firmware turns it off by default; keeping it on costs some
  battery.
- **Moaan settings**: an icon in the app list that opens the firmware's own settings menu (App
  freeze, DPI per app, logo key, refresh mode, sleep time...), which otherwise only the stock
  launcher can open.
- **Save diagnostics**: memory, running processes and the system log, written to `Download/`.
  Useful when reporting a problem.
- **Reboot into recovery**.

**Why it only works on the inkPalm 5.** It is signed with the public Android (AOSP) *platform
test key* and asks to run as the system user. The inkPalm 5 firmware is signed with that same
public test key, so Android accepts it as a system app. On a device with a properly signed
firmware, the installation just fails.

**Use it with care.** As a system app it can change what a normal app can't. Disable only apps
you know; everything it changes can be undone from the app itself. Uninstall it like any other
app.

## Build

Requirements: JDK 17 and the Android SDK (platform 34).

```sh
./gradlew assembleHomeDebug        # debug build, id com.moan.moanwm.dev: installs next to a release
./gradlew assembleHomeRelease      # release build, minified with R8
./gradlew testHomeDebugUnitTest    # Robolectric tests of the launcher lifecycle (API 27)
./gradlew testHomeDebugUnitTest -Pscreenshots   # also renders screens to app/build/screenshots/
```

Release builds are signed when a `keystore.properties` file exists at the project root
(it is gitignored):

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Each release build keeps its R8 mapping in `app/release/mappings/`, to decode crash reports
shown on the device.

The quick bar icons are vector drawables generated from one set of symbols, so every icon has
the same stroke: after changing a symbol in `tools/GenQuickIcons.java`, run
`java tools/GenQuickIcons.java app/src/main/res/drawable`.

## History and credits

This is a fork of a fork:

- [Modificator](https://github.com/Modificator/E-Ink-Launcher) wrote the original E-Ink Launcher;
- [shunf4](https://github.com/shunf4/E-Ink-Launcher) added sorting, priorities and shortcuts;
- [qwerty12](https://github.com/qwerty12/E-Ink-Launcher) (Faheem Pervez) added the inkPalm
  support;
- this fork modernizes the build and adds the KOReader card, the quick bar, the new settings,
  the front light and contrast panels and the memory work.

Translations: Maciej Haudek (Polish), Limerainne (Korean).

## License

[Apache License 2.0](LICENSE)

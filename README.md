# Auto Scroll — native Kotlin Android app

A standalone Android implementation of the [`first_version`](https://github.com/adampirog/auto-scroll/tree/first_version) Auto Scroll app. No Python, Flet, Flutter, Dart, or runtime third-party libraries.

## Features

- Compact draggable overlay with Play/Pause and Expand/Collapse controls
- Expanded overlay with Close, direction (Up/Down), and speed (0.5×–5.0×)
- Continuous `AccessibilityService.dispatchGesture()` scrolling using the original swipe geometry and timing
- Android Accessibility overlay (`TYPE_ACCESSIBILITY_OVERLAY`): no “display over other apps” permission
- Pause on screen-off, and stop the loop when the overlay closes
- Restore overlay visibility, scroll direction, and speed after Accessibility service reconnection (scrolling resumes **paused**)
- Native settings screen with Accessibility status, overlay switch, direction buttons, and speed slider
- Main launcher Activity excluded from Recents to avoid the swipe-to-clean action previously observed on Xiaomi
- Accessibility service isolated into its own `:accessibility` process; no foreground service or keep-alive worker

## Requirements

- Ubuntu/Linux terminal (or equivalent)
- JDK 17 or newer (JDK 21 works)
- Android SDK Platform 36 + build tools, and an `ANDROID_HOME` or `ANDROID_SDK_ROOT` pointing to your SDK installation
- `curl`, `unzip`, and `sha256sum` for the included Gradle launcher
- Internet for initial Gradle and Android plugin download

The included `./gradlew` is a small Linux bootstrap launcher for **Gradle 9.3.1**, not the official Gradle Wrapper JAR. It downloads and checksum-verifies the official distribution on first use, then invokes Gradle. To switch to the official Gradle Wrapper, run `gradle wrapper --gradle-version 9.3.1` from a machine with Gradle installed, and commit the generated wrapper files.

## Build / install

From the project root:

```bash
chmod +x gradlew
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Install a debug APK on a device with USB debugging:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Important:** A debug APK is signed with a different key and cannot upgrade the existing signed Flet app. Do not uninstall your existing application just to install a debug build if you want to preserve its permissions and data.

### Signed release APK (preserves installed app)

The project uses the same Android application ID and Accessibility service class as the existing app. Its `versionCode` is `2`. To install it as an upgrade, you must sign using **the same certificate as your installed APK**.

The release configuration defaults to:

- Keystore: `$HOME/.local/share/android-signing/personal-apps.p12`
- Keystore type: `PKCS12`
- Key alias: `personal-apps`

Set the passwords in your shell (these variable names can be adjusted in `app/build.gradle.kts`):

```bash
export ANDROID_KEYSTORE_PASSWORD='your-store-password'
export ANDROID_KEY_PASSWORD='your-key-password'
# Optional:
# export AUTO_SCROLL_KEYSTORE='/absolute/path/to/personal-apps.p12'
# export ANDROID_KEY_ALIAS='personal-apps'

./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

If the signing variables or keystore are absent, Gradle may produce an **unsigned** release artifact instead. Verify the signed APK before installing:

```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

If the previously installed APK has a larger `versionCode` than `2`, increase `versionCode` in `app/build.gradle.kts` before building. If its signing certificate differs, the in-place installation will be rejected.

## First run

1. Open Auto Scroll from its launcher icon.
2. Tap **Settings** in the Accessibility card, enable Auto Scroll, and return to the app.
3. Use the floating-controls switch to show the overlay.
4. Use Play/Pause to start or stop scrolling; Expand changes direction and speed.

**Navigation:** The launcher Activity does not appear in Recents, by design. Press Back to close the settings screen. This does not disable the Accessibility Service. The overlay's X hides the overlay and stops gestures, but keeps the Accessibility permission granted.

## Compatibility with the old application

Preserved from `first_version`:

- Android package/application ID: `com.adampirog.autoscroll`
- Accessibility component: `com.adampirog.auto_scroll_android.AutoScrollAccessibilityService`
- Dedicated process suffix: `:accessibility`
- SharedPreferences name: `auto_scroll`
- Keys: `overlay_visible`, `direction`, `speed`
- Original app icon and color palette

Android may require you to disable/re-enable the Accessibility Service once when switching implementations. Do **not** uninstall the old app if you want to retain its app data; sign the release with the old certificate and upgrade it in place.

## Project layout

```text
app/src/main/java/com/adampirog/autoscroll/
    MainActivity.kt             Native settings UI
    ServiceClient.kt            One-shot interprocess commands
app/src/main/java/com/adampirog/auto_scroll_android/
    AutoScrollAccessibilityService.kt  Gesture engine, state, persistence
    AutoScrollOverlay.kt               Native floating controls
app/src/main/AndroidManifest.xml
app/src/main/res/xml/accessibilityservice.xml
```

## Limitations

Xiaomi/HyperOS can force-stop all processes in an app package during cleanup. Hiding the launcher Activity from Recents avoids the specific `SwipeUpClean` gesture observed in the earlier debugging, but cannot guarantee immunity to all manufacturer-triggered process kills. No privileged permissions or undocumented self-restart tricks are used.

This archive contains **source code, not a compiled APK**. Compilation and testing on the physical device were not possible in the environment used to prepare this migration; the Gradle build therefore remains to be verified on your Ubuntu Android SDK installation.

# Auto Scroll

A lightweight Android app for automatic, hands-free scrolling in other applications.

Auto Scroll uses Android's Accessibility Service to simulate scrolling gestures. It is written entirely in Kotlin, runs locally, and requires no account or internet connection.

## Features

- Draggable floating controls with Play/Pause and Expand/Collapse
- Adjustable scrolling speed (1×–25×) and direction (Up/Down)
- Persistent settings and automatic restoration of the floating controls
- Automatic scrolling pause when the screen turns off
- No access to the contents of other applications

## Setup

Requires JDK 17+, Android SDK (API 36), and Android platform/build tools.

```bash
git clone -b first_version https://github.com/adampirog/auto-scroll.git
cd auto-scroll
```

Enable **Auto Scroll** in Android's Accessibility settings, then use the application to show the floating controls.

## Testing

Run the unit tests:

```bash
./gradlew testDebugUnitTest
```

## Build for Android

Build a debug APK:

```bash
./gradlew assembleDebug
```

For a signed release build, configure the signing password:

```bash
read -rsp "Keystore password: " ANDROID_KEYSTORE_PASSWORD
echo
export ANDROID_KEYSTORE_PASSWORD

./gradlew assembleRelease
```

Release signing uses `~/.local/share/android-signing/personal-apps.p12` with the `personal-apps` alias by default. Set `AUTO_SCROLL_KEYSTORE` to use another keystore, or `ANDROID_KEY_PASSWORD` if the key password differs.

Generated APKs are placed under:

```text
app/build/outputs/apk/
```

Install the signed release APK:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

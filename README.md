# Secure Chat Android Client

Android client for an end-to-end encrypted chat backend with:

- secure conversations and Signal-based bootstrap
- background push notifications via FCM
- branded theming and customizable color palettes
- message reactions, pinning, saved messages and attachments
- optional Google Authenticator TOTP 2FA
- release-ready Android build pipeline

## Stack

- Kotlin
- Jetpack Compose
- Hilt
- Retrofit + OkHttp
- DataStore
- Room
- Firebase Cloud Messaging

## Project Structure

- `app/` Android application module
- `app/src/main/java/.../ui/` Compose screens and view models
- `app/src/main/java/.../data/` repositories, DTOs, API clients and local sources
- `app/src/main/java/.../push/` push registration, notification manager and actions
- `app/src/main/java/.../app/` DI, app runtime and startup wiring

## Requirements

- Android Studio recent stable
- JDK 17
- Android SDK 35
- Running backend API compatible with the bundled client contracts

## Local Run

Debug build uses this base URL by default:

```text
https://170.168.10.207/api/v1/
```

Use `SECURE_CHAT_DEBUG_API_BASE_URL` to point to your own HTTPS backend.

### Build debug

```bash
./gradlew assembleDebug
```

### Install debug on device/emulator

```bash
./gradlew installDebug
```

### Run unit tests

```bash
./gradlew testDebugUnitTest
```

## Release Build

Release build requires an explicit production API URL:

```bash
SECURE_CHAT_RELEASE_API_BASE_URL=https://your-domain.example/api/v1/ ./gradlew assembleRelease
```

Optional flags:

```bash
SECURE_CHAT_DEBUG_HTTP_LOGGING=true
SECURE_CHAT_RELEASE_HTTP_LOGGING=false
SECURE_CHAT_DEBUG_SIGNAL_PROTOCOL=false
SECURE_CHAT_RELEASE_SIGNAL_PROTOCOL=false
SECURE_CHAT_SHOW_DEBUG_AUTH_INFO=true
```

## Install APK

After a successful release build, the APK is generated in:

```text
app/build/outputs/apk/release/
```

You can install it with ADB:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## FCM Setup

Real push delivery requires your own Firebase project. This repository intentionally does not commit a real `app/google-services.json`.

### What to do

1. Create or open your Firebase project.
2. Add an Android app with package name:

```text
com.example.securechatapp
```

3. Download `google-services.json`.
4. Place it at:

```text
app/google-services.json
```

5. Rebuild the app.

### Included in repo

- `app/google-services.json.example` with the expected shape
- `.gitignore` rule for the real `app/google-services.json`

Without a real Firebase config, the app still builds, but production FCM delivery on real devices will not work.

## Google 2FA Flow

The app supports optional Google Authenticator style TOTP 2FA.

### Enable

1. Open `Settings`.
2. Tap `Enable Google 2FA`.
3. Copy the generated secret or provisioning URI.
4. Add it to Google Authenticator.
5. Enter the current 6-digit code back in the app.
6. If the code matches, 2FA is enabled.

### Login with 2FA

If TOTP 2FA is enabled on the account:

1. Enter nickname and password on the login screen.
2. The client asks for the current Google Authenticator code.
3. Enter the code and continue login.

Google 2FA is optional and disabled by default.

## Notifications

The client supports:

- new message notifications
- quick reply from the notification tray
- mark-as-read action
- tap-to-open conversation routing
- app update notifications

Push notifications are enabled by default and can be disabled in `Settings`.

## Backend Compatibility

Client is aligned with backend features including:

- saved messages
- pinned messages
- richer message metadata
- user safety checks
- app release notifications
- Google TOTP 2FA

## Notes For GitHub

- do not commit a real `google-services.json`
- keep production API URL outside the repo
- prefer using release env vars in CI/CD
- if push fails locally, configure GitHub credentials first

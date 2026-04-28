# AppBlocker

`AppBlocker` is a Kotlin/Jetpack Compose Android app for self-control or supervised-device scenarios. It lets a user:

- add buckets
- add apps and website hostnames inside each bucket
- define recurring blocking windows with date ranges inside each bucket
- extend existing schedules without allowing reductions

## What this build does

- Blocks selected apps when a blocking window is active by using an `AccessibilityService`.
- Blocks selected websites in supported browsers by reading the visible address bar through the same accessibility service.
- Redirects blocked website hits to `https://github.com` instead of force-closing the browser.
- Silences WhatsApp / WhatsApp Business notifications during active WhatsApp app-block windows when Notification Access is enabled.
- Shows a weekly screen-time tracker when Usage Access is enabled.
- Stores rules locally with Room.
- Treats targets as append-only.
- Treats schedules as append-only or expandable-only.

## Important platform boundary

This project does **not** try to hide itself or bypass Android's security model. The supported path for "not removable from the phone UI" is to provision the app as an official **device owner** on a supervised device. In that model:

- the app is installed from a laptop with `adb`
- the app is assigned as device owner with `adb`
- uninstall requires a laptop script that first removes device-owner admin, then uninstalls

This only works on a device that is freshly reset or otherwise meets Android's device-owner provisioning requirements.

## Important setup steps

0. In the same PowerShell session, set a release token used by uninstall script + app build:
   - `$env:APPBLOCKER_RELEASE_TOKEN = "AxrJEL8KwLu18w6QWtxHFjzCMKfu1NzsTkw1Vu2lBKI"`
   - `$env:ORG_GRADLE_PROJECT_appblockerLaptopReleaseToken = $env:APPBLOCKER_RELEASE_TOKEN`
1. Build and install:
   - `scripts/build-debug.ps1`
   - `scripts/install.ps1`
2. Open AppBlocker and enable:
   - Accessibility service for AppBlocker
   - Battery mode as **Unrestricted**
   - Notification access for AppBlocker (needed for WhatsApp notification silencing)
   - Usage access for AppBlocker (needed for weekly screen-time totals)
3. Create at least one bucket.
4. Inside that bucket, add:
   - one or more apps and/or domains
   - at least one time window that is active now (or during your test time)

If a domain is not blocked, the most common causes are:

- accessibility is off
- battery optimization is still enabled
- the domain is not in the same active bucket/time window
- browser URL extraction did not expose the host in that specific browser build

If WhatsApp notifications are not silenced during block windows, the most common causes are:

- notification access is off
- WhatsApp is not added as a blocked app in an active bucket/time window

If weekly screen time is empty, the most common causes are:

- usage access is off
- there has not been enough recorded device usage yet during the current week

## Block device settings

- There is no manual settings-guard toggle.
- When both Accessibility and AppBlocker device admin are enabled, AppBlocker automatically blocks only settings pages that expose **uninstall** or **device-admin deactivation** for AppBlocker itself.
- Other settings pages remain allowed.
- In device-owner mode, the app also applies uninstall-blocking and clears broad app-control/config restrictions to avoid over-blocking normal settings usage.

Note: without device-owner mode, Android still allows users to change some security/power settings.

## Supported browsers for website blocking

The first implementation checks the address bar in these browsers:

- Chrome
- Brave
- Firefox
- Microsoft Edge
- Samsung Internet

Website blocking is best-effort because Android does not expose a universal browser URL API to third-party apps.

## Project layout

- `app/` Android application
- `scripts/build-debug.ps1` build helper
- `scripts/install.ps1` install helper
- `scripts/provision-device-owner.ps1` device-owner helper
- `scripts/uninstall.ps1` admin removal + uninstall helper (supports optional `-KeepBuckets`)

## Build notes

The project is configured for:

- JDK 17
- Android Gradle Plugin 8.4.2
- Gradle 8.6
- Android SDK Platform 34 / Build Tools 34.0.0

If you see this warning during build:

- `Warning: SDK processing. This version only understands SDK XML versions up to 3 but an SDK XML file of version 4 was encountered.`

it usually means Android Studio and the Android command-line SDK tools are from different release times. The APK can still build, but updating the command-line tools is recommended.

## Debugging steps

0. One-time machine check:
   - `java -version` should show JDK 17
   - if missing, install JDK 17 and set `JAVA_HOME` to that JDK path
1. Build debug APK:
   - `scripts/build-debug.ps1`
   - this uses Gradle `--no-daemon` to avoid false daemon-dispatch failures after a successful build
2. Install on connected phone:
   - `scripts/install.ps1`
3. Keep AppBlocker process logs open while testing:
   - `adb logcat | findstr /i "com.indrajeet.appblocker AndroidRuntime FATAL EXCEPTION"`
4. Capture full logs to a file when needed:
   - `adb logcat -d > logcat.txt`
5. If website blocking is inconsistent, re-check:
   - Accessibility service is ON for AppBlocker
   - Battery mode for AppBlocker is set to Unrestricted
   - Domain is in an active bucket/time window
   - Browser is in supported list and URL is visible in address bar

You can also debug from Android Studio:

- Open the project and run `app` on device/emulator.
- Use **Logcat** and filter by package `com.indrajeet.appblocker`.
- For Compose crashes, look for `java.lang.IllegalArgumentException` / `FATAL EXCEPTION` entries in Logcat first.

## Device-owner flow

1. In PowerShell, set matching token values:
   - `$env:APPBLOCKER_RELEASE_TOKEN = "AxrJEL8KwLu18w6QWtxHFjzCMKfu1NzsTkw1Vu2lBKI"`
   - `$env:ORG_GRADLE_PROJECT_appblockerLaptopReleaseToken = $env:APPBLOCKER_RELEASE_TOKEN`
2. Build the debug APK with `scripts/build-debug.ps1`.
3. Factory reset the Android device if needed so it can accept device-owner provisioning.
4. Make sure there is only one user profile on the device.
5. Remove any existing work profile / profile owner (for example Intune Company Portal profile owner) before provisioning.
6. Enable developer options and USB debugging on the phone.
7. Run `scripts/provision-device-owner.ps1`.
8. Enable the accessibility service from Android settings.

`set-device-owner` will fail if there are multiple users/profiles or another owner already present.

To remove the app later, run one of these in a shell where `APPBLOCKER_RELEASE_TOKEN` matches the value used at build time:

- Default (recommended): remove app **and** all blocked buckets/config:
  - `scripts/uninstall.ps1`
- Optional: uninstall app but keep blocked buckets/config on device data partition:
  - `scripts/uninstall.ps1 -KeepBuckets`
  - This mode uses Android package-manager uninstall with `-k` and may be user-0 scoped on modern Android builds.

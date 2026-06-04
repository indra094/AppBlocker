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
- Best-effort watches WhatsApp / WhatsApp Business activity and allows calls only during a configurable Pacific Time window (default `8:00 AM` to `6:30 AM` the next day). Outside that window, it ends foreground calls and can surface an ongoing background call from the WhatsApp notification so the accessibility service can press the in-call end button without bouncing other apps to Home.
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
   - $env:APPBLOCKER_RELEASE_TOKEN = "AxrJEL8KwLu18w6QWtxHFjzCMKfu1NzsTkw1Vu2lBKI"
   - $env:ORG_GRADLE_PROJECT_appblockerLaptopReleaseToken = $env:APPBLOCKER_RELEASE_TOKEN
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

If WhatsApp calls are not ended outside the configured Pacific Time window, the most common causes are:

- accessibility is off
- notification access is off, so an ongoing background call could not be surfaced when the blocked period started
- the in-call WhatsApp UI did not expose an accessible end-call control on that device/build
- the call was not in the foreground when the check ran

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
- `ios/` SwiftUI iPhone application scaffold using Apple's Screen Time APIs
- `scripts/build-debug.ps1` build helper
- `scripts/delete-buckets.ps1` delete selected buckets from the laptop
- `scripts/ios-build-debug.ps1` iOS build helper for macOS + Xcode
- `scripts/ios-delete-buckets.ps1` delete selected iOS buckets from an exported/shared rule file
- `scripts/ios-install.ps1` iOS install helper for Simulator or a connected device on macOS
- `scripts/ios-reset-bucket-schedules.ps1` clear selected iOS bucket windows from an exported/shared rule file
- `scripts/ios-uninstall.ps1` iOS uninstall helper for Simulator or a connected device on macOS
- `scripts/install.ps1` install helper
- `scripts/provision-device-owner.ps1` device-owner helper
- `scripts/reset-bucket-schedules.ps1` remove time windows from selected buckets on the laptop
- `scripts/uninstall.ps1` admin removal + uninstall helper (supports optional `-KeepBuckets`)

For iPhone install steps, see `ios/README.md`.
Note: building/installing the iOS app requires macOS/Xcode (Windows-only machines need a Mac/hosted Mac/CI for that step).

## iPhone maintenance parity

The iPhone build now supports the same bucket-maintenance outcomes as the Android build:

- delete buckets
- clear a bucket's schedules without deleting its targets
- export/import rule files for laptop-side edits

On iPhone, these actions are available in two supported ways:

- directly in the app from the bucket overflow menu and the top-right maintenance menu
- from laptop scripts by exporting `AppBlocker-buckets.json`, running an iOS maintenance script against that file, then importing it back into the app

Apple still does not allow third-party apps to recreate Android-only capabilities such as Accessibility-driven browser URL inspection, device-owner provisioning, or app-controlled uninstall hardening. The iOS implementation uses the closest supported equivalents: Screen Time shields, Files-based rule transfer, and Apple's own supervision / Screen Time controls.

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

To delete only certain buckets from the laptop without uninstalling:

- By bucket id:
  - `scripts/delete-buckets.ps1 -BucketId 3,5`
- By exact bucket name:
  - `scripts/delete-buckets.ps1 -BucketName Social,Games`

To keep a bucket but reset its timings from the laptop:

- By bucket id:
  - `scripts/reset-bucket-schedules.ps1 -BucketId 3,5`
- By exact bucket name:
  - `scripts/reset-bucket-schedules.ps1 -BucketName Social,Games`

Notes:

- This uses the same `APPBLOCKER_RELEASE_TOKEN` as uninstall.
- Deleting a bucket also deletes its apps/domains and time windows through Room foreign-key cascade.
- Bucket-name deletion is exact-match and deletes all buckets with that exact name.
- Timing reset deletes all schedules in the matched bucket(s) but keeps the bucket plus its apps/domains.

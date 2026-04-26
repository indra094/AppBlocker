# AppBlocker

`AppBlocker` is a Kotlin/Jetpack Compose Android app for self-control or supervised-device scenarios. It lets a user:

- add buckets
- add apps and website hostnames inside each bucket
- define recurring blocking windows with date ranges inside each bucket
- extend existing schedules without allowing reductions

## What this build does

- Blocks selected apps when a blocking window is active by using an `AccessibilityService`.
- Blocks selected websites in supported browsers by reading the visible address bar through the same accessibility service.
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

1. Build and install:
   - `scripts/build-debug.ps1`
   - `scripts/install.ps1`
2. Open AppBlocker and enable:
   - Accessibility service for AppBlocker
   - Battery mode as **Unrestricted**
3. Create at least one bucket.
4. Inside that bucket, add:
   - one or more apps and/or domains
   - at least one time window that is active now (or during your test time)

If a domain is not blocked, the most common causes are:

- accessibility is off
- battery optimization is still enabled
- the domain is not in the same active bucket/time window
- browser URL extraction did not expose the host in that specific browser build

## Block device settings

- The app has a **Device settings guard** toggle.
- When enabled, the accessibility service blocks opening system Settings.
- In device-owner mode, the app also applies `DISALLOW_APPS_CONTROL` and `no_config_settings` as best-effort policy hardening.

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
- `scripts/uninstall.ps1` admin removal + uninstall helper

## Build notes

The project is configured for:

- JDK 17
- Android Gradle Plugin 8.4.2
- Gradle 8.6
- Android SDK Platform 34 / Build Tools 34.0.0

## Device-owner flow

1. Build the debug APK with `scripts/build-debug.ps1`.
2. Factory reset the Android device if needed so it can accept device-owner provisioning.
3. Make sure there is only one user profile on the device.
4. Remove any existing work profile / profile owner (for example Intune Company Portal profile owner) before provisioning.
5. Enable developer options and USB debugging on the phone.
6. Run `scripts/provision-device-owner.ps1`.
7. Enable the accessibility service from Android settings.

`set-device-owner` will fail if there are multiple users/profiles or another owner already present.

To remove the app later, run `scripts/uninstall.ps1` from the laptop.

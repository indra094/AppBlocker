# AppBlocker for iPhone

This folder contains a native SwiftUI iPhone version of AppBlocker.

The iOS version targets **iOS 16.0 and newer** with `TARGETED_DEVICE_FAMILY = 1`, which makes it an iPhone app and keeps iPhone 8 in range while excluding older iPhones that cannot install iOS 16.

## Platform boundary

iOS does not expose Android-style Accessibility Service, Device Owner, or package-name blocking APIs to App Store apps. This implementation uses Apple's supported Screen Time frameworks instead:

- `FamilyControls` for user-approved target selection.
- `ManagedSettings` for shielding selected apps, app categories, and web domains.
- `DeviceActivity` for schedule callbacks that re-apply shields when windows start or end.
- An App Group for sharing bucket rules with the Device Activity monitor extension.

You need Apple's Family Controls entitlement enabled for the app and extensions in your Apple Developer account.

## Files

- `project.yml` is the XcodeGen project definition.
- `AppBlocker/` contains the SwiftUI app.
- `Shared/` contains the bucket, schedule, persistence, and shielding logic shared with the extension.
- `Extensions/DeviceActivityMonitor/` applies shields as scheduled windows start and end.
- `Extensions/ShieldConfiguration/` customizes the system shield screen.

## Build on a Mac

If you are using Windows: you can edit the iOS code on Windows, but the actual iPhone install step requires a Mac (or a hosted Mac CI) because Apple signing and device deployment use Xcode on macOS.

1. Install Xcode and XcodeGen:
   - `brew install xcodegen`
2. Edit `project.yml` and replace:
   - `DEVELOPMENT_TEAM`
   - `PRODUCT_BUNDLE_IDENTIFIER` values if needed
3. Edit all entitlement files if your App Group is different:
   - `group.com.indrajeet.appblocker`
4. Generate and open the project:
   - `cd ios`
   - `xcodegen generate`
   - `open AppBlocker.xcodeproj`
5. In Xcode, confirm capabilities:
   - Family Controls: `AppBlocker`, `AppBlockerDeviceActivityMonitor`, `AppBlockerShieldConfiguration`
   - App Groups: `AppBlocker`, `AppBlockerDeviceActivityMonitor` (shared rules storage)
6. Run on a physical iPhone. Apple's Screen Time APIs do not fully work in the simulator.

## Windows workflow (how to install if you only have Windows)

You cannot install this iOS app onto a real iPhone from Windows alone because Apple requires Xcode on macOS to build and sign iOS apps.

You have three practical options:

- Use any Mac (your own, a friend's, or a spare Mac mini) for the build/install step.
- Use a hosted Mac service (for example MacStadium) to remote into macOS, then run Xcode there.
- Use macOS CI (GitHub Actions macOS runner) to build/sign and distribute via TestFlight (more setup, best for multiple devices).

### Install to your own iPhone (remote Mac + Xcode Run)

1. Develop on Windows as usual (this repo is fine on Windows).
2. Push the repo to a Git remote that your Mac can pull from (GitHub, private repo, etc.).
3. On the Mac:
   - `cd ios`
   - `brew install xcodegen`
   - `xcodegen generate`
   - open `AppBlocker.xcodeproj` in Xcode
4. In Xcode, for **all three** targets:
   - `AppBlocker`
   - `AppBlockerDeviceActivityMonitor`
   - `AppBlockerShieldConfiguration`
   Set `Signing & Capabilities`:
   - select your Team
   - ensure bundle identifiers are unique and under your Team
   - ensure Family Controls entitlement is enabled for your Team/account
   - ensure App Groups includes `group.com.indrajeet.appblocker` (or your custom group) on `AppBlocker` + `AppBlockerDeviceActivityMonitor`
5. Connect the iPhone to the Mac (USB), unlock it, and press Trust if prompted.
6. On the iPhone (iOS 16+), enable Developer Mode if prompted.
7. Select the iPhone as the run destination in Xcode and click Run.
8. On the iPhone, open AppBlocker and approve the Screen Time authorization prompt.

### Install to multiple iPhones (Mac build + TestFlight)

1. On the Mac, build and archive in Xcode: Product -> Archive.
2. Upload the archive to App Store Connect from Organizer.
3. In App Store Connect -> TestFlight:
   - wait for processing
   - add internal/external testers
4. On each iPhone, install TestFlight and accept the AppBlocker invite.

Reminder: without macOS/Xcode somewhere in the loop, you cannot install this iOS version onto a real iPhone.

## Install on iPhone

Device requirements:

- iPhone 8 or newer
- iOS 16.0 or newer

### Option A (recommended): install directly from Xcode

This is the simplest path for installing on your own iPhone.

1. Connect the iPhone to your Mac (USB), unlock it, and press "Trust" if prompted.
2. In Xcode, open `ios/AppBlocker.xcodeproj` and select the `AppBlocker` scheme.
3. In `Signing & Capabilities`:
   - `AppBlocker`
   - `AppBlockerDeviceActivityMonitor`
   - `AppBlockerShieldConfiguration`
   Ensure Family Controls is enabled for all three targets.
4. Ensure App Groups is enabled for:
   - `AppBlocker`
   - `AppBlockerDeviceActivityMonitor`
   Ensure:
   - a valid Team is selected
   - the bundle identifiers are unique and under your Team
   - App Groups includes `group.com.indrajeet.appblocker` (or your custom group)
5. Choose your iPhone as the run destination and click Run.
6. On the iPhone, open AppBlocker and approve the Screen Time authorization prompt.

### Option B: install via TestFlight (for distributing to other iPhones)

This is the cleanest way to install on multiple devices you manage.

1. In Xcode: Product -> Archive.
2. In Organizer, distribute/upload the archive to App Store Connect.
3. In App Store Connect, create a TestFlight build and add testers.
4. On the iPhone, install the TestFlight app and accept the AppBlocker invite.

Notes:

- You generally need a paid Apple Developer Program account for TestFlight distribution and required entitlements.
- Apple's Screen Time APIs are designed to run on real devices; expect limited behavior on simulators.

## Troubleshooting

- Build fails with entitlement/capability errors: ensure Family Controls entitlement is enabled for your Team, and the same App Group is present on `AppBlocker` + `AppBlockerDeviceActivityMonitor`.
- Rules do not apply at runtime: confirm the iPhone granted Screen Time authorization inside the app (Home screen status should be Approved).
- Shields never appear: add a bucket, select at least one app/category/website token, then add a window that is active right now.

## Maintenance workflows

The iPhone app now includes the same bucket-maintenance outcomes as the Android build:

- delete a bucket
- clear all windows from a bucket while keeping its selected targets
- move rule files between the phone and a laptop

### In the app

1. On the Home screen, use the top-right `...` menu to export or import `AppBlocker-buckets.json`.
2. Use each bucket's `...` menu to reset its windows or delete the entire bucket.

### From laptop scripts

1. Export `AppBlocker-buckets.json` from the iPhone app.
2. Run one of these scripts against the exported file:
   - `scripts/ios-delete-buckets.ps1 -FilePath /path/to/AppBlocker-buckets.json -BucketName Social`
   - `scripts/ios-reset-bucket-schedules.ps1 -FilePath /path/to/AppBlocker-buckets.json -BucketName Social`
3. Import the updated JSON file back into the iPhone app.

For Simulator development on a Mac, you can also point those scripts at the shared App Group rule file instead of an exported copy.

## iOS build / install scripts

These helpers are for macOS with Xcode + PowerShell 7:

- `scripts/ios-build-debug.ps1` generates the Xcode project with XcodeGen and builds a Debug `.app`.
- `scripts/ios-install.ps1` installs the latest build to the booted Simulator by default, or to a connected device with `-DeviceId`.
- `scripts/ios-uninstall.ps1` removes the app from the booted Simulator by default, or from a connected device with `-DeviceId`.

Examples:

- `pwsh ./scripts/ios-build-debug.ps1`
- `pwsh ./scripts/ios-install.ps1 -SimulatorName "iPhone 16"`
- `pwsh ./scripts/ios-install.ps1 -DeviceId <your-device-udid> -AllowProvisioningUpdates`
- `pwsh ./scripts/ios-uninstall.ps1`

## Current behavior

- Create buckets.
- Delete buckets from the app or from exported rule files.
- Reset bucket windows from the app or from exported rule files.
- Export/import rule files through the app maintenance menu.
- Select apps, categories, and websites through Apple's private Screen Time picker.
- Add recurring blocking windows.
- Extend existing windows without weakening them.
- Apply active shields immediately and from Device Activity callbacks.

## Notes

- iOS intentionally hides selected app names from third-party apps, so the UI displays counts instead of package names.
- Website blocking uses Screen Time web-domain tokens chosen in the Apple picker, not arbitrary typed hostnames.
- App removal protection on iOS is normally handled through Screen Time or MDM/supervision, not by third-party app code.

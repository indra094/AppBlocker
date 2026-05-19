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
5. In Xcode, confirm capabilities for the app and extensions:
   - Family Controls
   - App Groups
6. Run on a physical iPhone. Apple's Screen Time APIs do not fully work in the simulator.

## Install on iPhone

Device requirements:

- iPhone 8 or newer
- iOS 16.0 or newer

### Option A (recommended): install directly from Xcode

This is the simplest path for installing on your own iPhone.

1. Connect the iPhone to your Mac (USB), unlock it, and press "Trust" if prompted.
2. In Xcode, open `ios/AppBlocker.xcodeproj` and select the `AppBlocker` scheme.
3. In `Signing & Capabilities` for all three targets:
   - `AppBlocker`
   - `AppBlockerDeviceActivityMonitor`
   - `AppBlockerShieldConfiguration`
   Ensure:
   - a valid Team is selected
   - the bundle identifiers are unique and under your Team
   - Family Controls entitlement is present
   - App Groups includes `group.com.indrajeet.appblocker` (or your custom group)
4. Choose your iPhone as the run destination and click Run.
5. On the iPhone, open AppBlocker and approve the Screen Time authorization prompt.

### Option B: install via TestFlight (for distributing to other iPhones)

This is the cleanest way to install on multiple devices you manage.

1. In Xcode: Product -> Archive.
2. In Organizer, distribute/upload the archive to App Store Connect.
3. In App Store Connect, create a TestFlight build and add testers.
4. On the iPhone, install the TestFlight app and accept the AppBlocker invite.

Notes:

- You generally need a paid Apple Developer Program account for TestFlight distribution and required entitlements.
- Apple’s Screen Time APIs are designed to run on real devices; expect limited behavior on simulators.

## Troubleshooting

- Build fails with entitlement/capability errors: ensure Family Controls entitlement is enabled for your Team, and the same App Group is present on the app + extension targets.
- Rules do not apply at runtime: confirm the iPhone granted Screen Time authorization inside the app (Home screen status should be Approved).
- Shields never appear: add a bucket, select at least one app/category/website token, then add a window that is active right now.

## Current behavior

- Create buckets.
- Select apps, categories, and websites through Apple's private Screen Time picker.
- Add recurring blocking windows.
- Extend existing windows without weakening them.
- Apply active shields immediately and from Device Activity callbacks.

## Notes

- iOS intentionally hides selected app names from third-party apps, so the UI displays counts instead of package names.
- Website blocking uses Screen Time web-domain tokens chosen in the Apple picker, not arbitrary typed hostnames.
- App removal protection on iOS is normally handled through Screen Time or MDM/supervision, not by third-party app code.

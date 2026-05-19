import DeviceActivity
import Foundation

final class AppBlockerDeviceActivityMonitor: DeviceActivityMonitor {
    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        ShieldApplicator.applyActiveShields()
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        ShieldApplicator.applyActiveShields()
    }
}

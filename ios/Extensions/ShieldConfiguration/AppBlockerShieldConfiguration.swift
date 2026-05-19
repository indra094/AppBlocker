import ManagedSettings
import ManagedSettingsUI
import UIKit

final class AppBlockerShieldConfiguration: ShieldConfigurationDataSource {
    override func configuration(shielding application: Application) -> ShieldConfiguration {
        configuration()
    }

    override func configuration(
        shielding application: Application,
        in category: ActivityCategory
    ) -> ShieldConfiguration {
        configuration()
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        configuration()
    }

    private func configuration() -> ShieldConfiguration {
        ShieldConfiguration(
            backgroundBlurStyle: .systemMaterial,
            backgroundColor: UIColor.systemBackground,
            icon: UIImage(systemName: "lock.shield"),
            title: ShieldConfiguration.Label(
                text: "Blocked by AppBlocker",
                color: UIColor.label
            ),
            subtitle: ShieldConfiguration.Label(
                text: "This app or website is inside an active blocking window.",
                color: UIColor.secondaryLabel
            ),
            primaryButtonLabel: ShieldConfiguration.Label(
                text: "OK",
                color: UIColor.white
            ),
            primaryButtonBackgroundColor: UIColor.systemBlue
        )
    }
}

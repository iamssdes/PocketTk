// PocketTk iOS runtime, part 3: the app entry point. pyScript() is generated from your Python file.
import UIKit

@UIApplicationMain
final class PyAppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let w = UIWindow(frame: UIScreen.main.bounds)
        let vc = UIViewController()
        vc.view.backgroundColor = .systemBackground
        w.rootViewController = vc
        w.makeKeyAndVisible()
        window = w
        PyApp.shared.rootVC = vc
        pyScript()
        PyApp.shared.root?.markDirty()
        PyApp.shared.flush()
        return true
    }
}

"""PocketTk --ipa (macOS only for now): Python -> Swift -> .ipa, and an iOS Simulator run.

Needs Xcode (the full app, for the iOS SDK) on a Mac. Experimental: written without access to a Mac.
"""
import os, sys, re, shutil, subprocess, plistlib, zipfile, platform, json

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "ipa"))
import pyswift                                  # noqa: E402

MIN_IOS = "15.0"


def next_version(rec, bump):
    vc, A, B, C, P = 1, 1, 0, 0, 0
    if rec:
        vc = int(rec.get("vc") or 1) + 1
        A, B, C = [int(x) for x in (rec.get("ver") or "1.0.0").split(".")[:3]]
        P = int(rec.get("patch") or 0)
        if bump == "large": A, B, C, P = A + 1, 0, 0, 0
        elif bump == "medium": B, C, P = B + 1, 0, 0
        elif bump == "small": C, P = C + 1, 0
        else: P += 1
    return vc, A, B, C, P


def need_mac(tk):
    if platform.system() != "Darwin":
        tk.die("--ipa only works on a Mac for now (it needs Apple's compiler and the iOS SDK, which only run on macOS)")


def xcrun(tk, *args, check=True):
    return tk.run(["xcrun"] + list(args), check=check)


def sdk_path(tk, sdk):
    if not shutil.which("xcrun"):
        tk.die("xcrun not found: install Xcode from the App Store, open it once, then: sudo xcode-select -s /Applications/Xcode.app")
    p = xcrun(tk, "--sdk", sdk, "--show-sdk-path", check=False)
    path = p.stdout.strip().splitlines()[-1] if p.stdout.strip() else ""
    if p.returncode != 0 or not os.path.isdir(path):
        tk.die("the %s SDK was not found. The iOS SDK comes with the full Xcode app (not only the Command Line Tools)." % sdk)
    return path


def translate(tk, src, bd):
    text = open(src, encoding="utf-8").read()
    try:
        swift = pyswift.translate(text, os.path.basename(src))
    except pyswift.GenError as e:
        print("\n".join(e.errors), file=sys.stderr)
        tk.die("this script uses something --ipa cannot translate yet (see above)")
    os.makedirs(bd, exist_ok=True)
    out = os.path.join(bd, "Script.swift")
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(swift)
    return out


def compile_swift(tk, script_swift, app_dir, exe, simulator):
    sdk_name = "iphonesimulator" if simulator else "iphoneos"
    sdk = sdk_path(tk, sdk_name)
    if simulator:
        arch = "arm64" if platform.machine() in ("arm64", "aarch64") else "x86_64"
        target = "%s-apple-ios%s-simulator" % (arch, MIN_IOS)
    else:
        target = "arm64-apple-ios%s" % MIN_IOS
    rt = [os.path.join(HERE, "ipa", "runtime", f) for f in ("PyCore.swift", "PyTk.swift", "AppEntry.swift")]
    cmd = ["xcrun", "--sdk", sdk_name, "swiftc", "-swift-version", "5", "-parse-as-library", "-O",
           "-sdk", sdk, "-target", target, "-module-name", "PocketApp", "-o", os.path.join(app_dir, exe)] + rt + [script_swift]
    tk.say("compiling Swift (%s)" % target)
    p = tk.run(cmd, check=False)
    if p.returncode != 0:
        sys.stdout.write(p.stdout)
        tk.die("swiftc failed. If the errors are in PyCore.swift / PyTk.swift, this is a bug in PocketTk's iOS runtime: please report it with the output above.")


def write_plist(app_dir, exe, bundle_id, name, build, short):
    info = {
        "CFBundleDevelopmentRegion": "en",
        "CFBundleExecutable": exe,
        "CFBundleIdentifier": bundle_id,
        "CFBundleInfoDictionaryVersion": "6.0",
        "CFBundleName": name,
        "CFBundleDisplayName": name,
        "CFBundlePackageType": "APPL",
        "CFBundleShortVersionString": short,
        "CFBundleVersion": str(build),
        "LSRequiresIPhoneOS": True,
        "MinimumOSVersion": MIN_IOS,
        "UIDeviceFamily": [1, 2],
        "UILaunchScreen": {},
        "UIRequiredDeviceCapabilities": ["arm64"],
        "UISupportedInterfaceOrientations": ["UIInterfaceOrientationPortrait", "UIInterfaceOrientationLandscapeLeft",
                                             "UIInterfaceOrientationLandscapeRight"],
        "UISupportedInterfaceOrientations~ipad": ["UIInterfaceOrientationPortrait", "UIInterfaceOrientationPortraitUpsideDown",
                                                  "UIInterfaceOrientationLandscapeLeft", "UIInterfaceOrientationLandscapeRight"],
    }
    with open(os.path.join(app_dir, "Info.plist"), "wb") as f:
        plistlib.dump(info, f)


def profile_entitlements(tk, profile):
    p = tk.run(["security", "cms", "-D", "-i", profile], check=False)
    if p.returncode != 0:
        tk.die("could not read the provisioning profile %s" % profile)
    d = plistlib.loads(p.stdout.encode("utf-8"))
    return d.get("Entitlements", {})


def sign(tk, app_dir, identity, profile, bd):
    shutil.copyfile(profile, os.path.join(app_dir, "embedded.mobileprovision"))
    ent = profile_entitlements(tk, profile)
    ent_path = os.path.join(bd, "entitlements.plist")
    with open(ent_path, "wb") as f:
        plistlib.dump(ent, f)
    tk.say("signing with %s" % identity)
    tk.run(["codesign", "-f", "-s", identity, "--entitlements", ent_path, "--timestamp=none", app_dir])


def make_ipa(app_dir, ipa_path):
    if os.path.exists(ipa_path):
        os.remove(ipa_path)
    top = os.path.basename(app_dir)
    with zipfile.ZipFile(ipa_path, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(app_dir):
            for fn in sorted(files):
                full = os.path.join(root, fn)
                arc = os.path.join("Payload", top, os.path.relpath(full, app_dir))
                zi = zipfile.ZipInfo(arc.replace(os.sep, "/"))
                zi.compress_type = zipfile.ZIP_DEFLATED
                mode = 0o755 if os.access(full, os.X_OK) else 0o644
                zi.external_attr = (0o100000 | mode) << 16
                with open(full, "rb") as f:
                    z.writestr(zi, f.read())


def bundle_id_for(pkg, profile_ent):
    """The bundle id: ours, unless the profile is for one exact id."""
    appid = profile_ent.get("application-identifier", "") if profile_ent else ""
    if appid and "." in appid:
        rest = appid.split(".", 1)[1]
        if rest != "*" and not rest.endswith(".*"):
            return rest
    return pkg


def build_ipa(tk, src, name=None, pkg=None, out=None, ident=None, bump="patch", sign_id=None, profile=None,
              simulator=False):
    need_mac(tk)
    if not os.path.isfile(src):
        tk.die("no such file: %s" % src)
    if bool(sign_id) != bool(profile) and not simulator:
        tk.die("signing needs both: --sign \"Apple Development: Your Name (TEAMID)\" and --profile file.mobileprovision")
    base = os.path.splitext(os.path.basename(src))[0]
    sn = tk.safe_name(base) or "app"
    if sn[0].isdigit():
        sn = "a" + sn
    name = name or base
    rec = tk.read_id(ident) if ident else None
    pkg = pkg or (rec["pkg"] if rec else tk.PKG_PREFIX + (ident or sn))
    if rec and (not name or name == base):
        name = rec.get("name") or name
    vc, A, B, C, P = next_version(rec, bump)
    short = "%d.%d.%d" % (A, B, C)

    bd = os.path.join(tk.WORK, "ipa", sn)
    shutil.rmtree(bd, ignore_errors=True)
    os.makedirs(bd)
    tk.say("translating %s to Swift" % src)
    script_swift = translate(tk, src, bd)

    exe = "PocketApp"
    app_dir = os.path.join(bd, "%s.app" % safe_app_name(name))
    os.makedirs(app_dir)
    compile_swift(tk, script_swift, app_dir, exe, simulator)

    ent = profile_entitlements(tk, profile) if (profile and not simulator) else {}
    bid = bundle_id_for(pkg, ent)
    write_plist(app_dir, exe, bid, name, vc, short)

    if simulator:
        tk.run(["codesign", "-f", "-s", "-", app_dir], check=False)
        result = app_dir
    else:
        signed = bool(sign_id and profile)
        if signed:
            sign(tk, app_dir, sign_id, profile, bd)
        od = tk.out_dir()
        os.makedirs(od, exist_ok=True)
        result = out or os.path.join(od, sn + ".ipa")
        make_ipa(app_dir, result)
        tk.say("built: %s%s" % (result, "" if signed else "  (UNSIGNED)"))
        if not signed:
            tk.say("an unsigned .ipa will not install by itself: sign it with --sign and --profile, or with a tool like Sideloadly or AltStore")

    if ident:
        tk.write_id(ident, {"pkg": pkg, "vc": vc, "name": name, "src": os.path.abspath(src),
                            "icon": (rec or {}).get("icon", ""), "ver": short, "patch": P, "apple": tk.VERSION.replace("PocketTk v", "")})
        tk.say("app id '%s' -> %s (iOS version %s%s, build %d)" % (ident, bid, short, " Patch %d" % P if P else "", vc))
    return result, bid


def safe_app_name(name):
    s = re.sub(r"[^A-Za-z0-9_-]", "", name.replace(" ", "")) or "App"
    return s


def install_on_device(tk, ipa):
    if shutil.which("ideviceinstaller"):
        tk.say("installing with ideviceinstaller")
        p = tk.run(["ideviceinstaller", "-i", ipa], check=False)
        sys.stdout.write(p.stdout)
        if p.returncode != 0:
            tk.die("ideviceinstaller failed (is the iPhone plugged in, unlocked and trusted?)")
        return
    tk.say("to put it on an iPhone: brew install ideviceinstaller (then run this again), or drag %s onto the device in" % ipa)
    tk.say("Finder / Apple Configurator, or use: xcrun devicectl device install app --device <id> <the .app>")


def run_in_simulator(tk, app_dir, bundle_id):
    tk.say("starting the iOS Simulator")
    p = xcrun(tk, "simctl", "list", "devices", "booted", check=False)
    m = re.search(r"\(([0-9A-F-]{36})\) \(Booted\)", p.stdout)
    if m:
        udid = m.group(1)
    else:
        p = xcrun(tk, "simctl", "list", "devices", "available")
        phones = re.findall(r"(iPhone[^()]*)\(([0-9A-F-]{36})\)", p.stdout)
        if not phones:
            tk.die("no iPhone simulator found: open Xcode > Settings > Platforms and install an iOS simulator runtime")
        udid = phones[-1][1]
        xcrun(tk, "simctl", "boot", udid, check=False)
        subprocess.run(["open", "-a", "Simulator"], check=False)
    xcrun(tk, "simctl", "bootstatus", udid, "-b", check=False)
    xcrun(tk, "simctl", "install", udid, app_dir)
    p = xcrun(tk, "simctl", "launch", udid, bundle_id, check=False)
    sys.stdout.write(p.stdout)
    tk.say("running in the iOS Simulator (this is a real iOS build running on your Mac)")

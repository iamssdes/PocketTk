// PocketTk iOS runtime, part 2: tkinter on UIKit (Tk, Frame, Label, Button, Entry, Checkbutton, variables, messagebox).
import UIKit

func pyShowAlert(_ title: String, _ msg: String) {
    DispatchQueue.main.async {
        guard let vc = PyApp.shared.rootVC else { return }
        let top = vc.presentedViewController ?? vc
        let al = UIAlertController(title: title, message: msg, preferredStyle: .alert)
        al.addAction(UIAlertAction(title: "OK", style: .default, handler: nil))
        top.present(al, animated: true, completion: nil)
    }
}

final class PyApp {
    static let shared = PyApp()
    var rootVC: UIViewController?
    var root: Container?
    var dirty: [Container] = []
    var scheduled = false
    var cancelled = Set<Int>()
    var nextAfter = 1

    func markDirty(_ c: Container) {
        if !dirty.contains(where: { $0 === c }) { dirty.append(c) }
        if !scheduled {
            scheduled = true
            DispatchQueue.main.async { self.flush() }
        }
    }
    func flush() {
        scheduled = false
        let list = dirty
        dirty = []
        for c in list { c.rebuild() }
    }
}

// ---------------------------------------------------------------- colors, fonts, options
func pyColor(_ v: PV?) -> UIColor? {
    guard let v = v, case .str(let s) = v else { return nil }
    if s.hasPrefix("#") {
        var hex = String(s.dropFirst())
        if hex.count == 3 { hex = hex.map { String([$0, $0]) }.joined() }
        if let n = UInt32(hex, radix: 16), hex.count == 6 {
            return UIColor(red: CGFloat((n >> 16) & 255) / 255, green: CGFloat((n >> 8) & 255) / 255, blue: CGFloat(n & 255) / 255, alpha: 1)
        }
        return nil
    }
    switch s.lowercased() {
    case "white": return .white
    case "black": return .black
    case "red": return .systemRed
    case "green": return .systemGreen
    case "blue": return .systemBlue
    case "yellow": return .systemYellow
    case "orange": return .systemOrange
    case "purple": return .systemPurple
    case "pink": return .systemPink
    case "gray", "grey": return .systemGray
    case "lightgray", "lightgrey": return .lightGray
    case "darkgray", "darkgrey": return .darkGray
    case "cyan": return .cyan
    case "magenta": return .magenta
    case "brown": return .brown
    case "gold": return UIColor(red: 1, green: 0.84, blue: 0, alpha: 1)
    case "navy": return UIColor(red: 0, green: 0, blue: 0.5, alpha: 1)
    case "teal": return UIColor(red: 0, green: 0.5, blue: 0.5, alpha: 1)
    case "lightblue": return UIColor(red: 0.68, green: 0.85, blue: 0.9, alpha: 1)
    case "lightgreen": return UIColor(red: 0.56, green: 0.93, blue: 0.56, alpha: 1)
    case "systembuttonface": return .systemGray5
    default: return nil
    }
}

func pyFont(_ v: PV?) -> UIFont? {
    guard let v = v else { return nil }
    var size: CGFloat = 14
    var bold = false
    var italic = false
    switch v {
    case .tuple(let t):
        if t.count > 1, let n = t[1].num { size = CGFloat(abs(n)) }
        if t.count > 2, case .str(let st) = t[2] { bold = st.contains("bold"); italic = st.contains("italic") }
    case .list(let l):
        if l.a.count > 1, let n = l.a[1].num { size = CGFloat(abs(n)) }
        if l.a.count > 2, case .str(let st) = l.a[2] { bold = st.contains("bold"); italic = st.contains("italic") }
    default: return nil
    }
    var f = UIFont.systemFont(ofSize: size, weight: bold ? .bold : .regular)
    if italic, let d = f.fontDescriptor.withSymbolicTraits([.traitItalic]) { f = UIFont(descriptor: d, size: size) }
    return f
}

func pyPad(_ v: PV?) -> CGFloat {
    guard let v = v else { return 0 }
    if let n = v.num { return CGFloat(n) }
    if case .tuple(let t) = v, let n = t.first?.num { return CGFloat(n) }
    return 0
}

func pyOptStr(_ v: PV?) -> String? {
    guard let v = v else { return nil }
    if case .null = v { return nil }
    return pyStr(v)
}

// ---------------------------------------------------------------- variables
final class PVar: PyObj {
    var value: PV
    let kind: String
    var observers: [() -> Void] = []
    init(_ kind: String, _ initial: PV?) {
        self.kind = kind
        switch kind {
        case "int": value = PV.int(0)
        case "double": value = PV.double(0)
        case "bool": value = PV.bool(false)
        default: value = PV.str("")
        }
        super.init()
        if let i = initial { value = coerce(i) }
    }
    override var typeName: String { return "Variable" }
    func coerce(_ v: PV) -> PV {
        switch kind {
        case "int": return pyToInt(v)
        case "double": return pyToFloat(v)
        case "bool": return PV.bool(v.truthy)
        default: return PV.str(pyStr(v))
        }
    }
    func assign(_ v: PV) {
        value = coerce(v)
        for o in observers { o() }
    }
    override func method(_ n: String, _ a: [PV], _ k: [String: PV]) -> PV {
        switch n {
        case "get": return value
        case "set": assign(a.first ?? PV.null); return PV.null
        case "trace_add", "trace", "trace_variable": return PV.null
        default: return pyFail("AttributeError", "Variable has no method '" + n + "'")
        }
    }
}

func makeVarCtor(_ kind: String) -> PV {
    return PV.fn(PFn { a, k in
        let initial: PV? = k["value"] ?? a.first
        return PV.obj(PVar(kind, initial))
    })
}
let b_StringVar = makeVarCtor("str")
let b_IntVar = makeVarCtor("int")
let b_DoubleVar = makeVarCtor("double")
let b_BooleanVar = makeVarCtor("bool")

func varOf(_ v: PV?) -> PVar? {
    if let v = v, case .obj(let o) = v { return o as? PVar }
    return nil
}

// ---------------------------------------------------------------- widgets
class Widget: PyObj {
    weak var parent: Widget?
    var children: [Widget] = []
    var view = UIView()
    var opts: [String: PV] = [:]
    var packInfo: [String: PV]? = nil
    var gridInfo: [String: PV]? = nil
    var destroyed = false

    init(_ parent: Widget?, _ o: [String: PV]) {
        super.init()
        self.parent = parent
        parent?.children.append(self)
        opts = o
    }
    override var typeName: String { return "Widget" }

    // the container that lays this widget out
    var container: Container? { return parent as? Container }

    func refresh() {
        if let c = pyColor(opts["bg"] ?? opts["background"]) { view.backgroundColor = c }
    }
    func configure(_ k: [String: PV]) {
        for (n, v) in k { opts[n] = v }
        refresh()
        container?.markDirty()
    }

    override func attr(_ n: String) -> PV {
        return PV.fn(PFn { a, k in self.method(n, a, k) })
    }

    override func method(_ n: String, _ a: [PV], _ k: [String: PV]) -> PV {
        switch n {
        case "pack", "pack_configure", "place", "place_configure":
            packInfo = k; gridInfo = nil
            container?.markDirty()
            return PV.null
        case "grid", "grid_configure":
            gridInfo = k; packInfo = nil
            container?.markDirty()
            return PV.null
        case "pack_forget", "grid_forget", "place_forget":
            packInfo = nil; gridInfo = nil
            container?.markDirty()
            return PV.null
        case "config", "configure":
            configure(k)
            return PV.null
        case "cget":
            if case .str(let key) = (a.first ?? PV.null) { return opts[key] ?? PV.null }
            return PV.null
        case "destroy":
            destroyed = true
            packInfo = nil; gridInfo = nil
            view.removeFromSuperview()
            container?.markDirty()
            return PV.null
        case "bind", "focus", "focus_set", "update", "update_idletasks", "lift", "lower", "grid_columnconfigure",
             "grid_rowconfigure", "columnconfigure", "rowconfigure", "pack_propagate", "grid_propagate",
             "option_add", "bind_all", "unbind":
            return PV.null
        case "winfo_width": return PV.int(Int(view.bounds.width))
        case "winfo_height": return PV.int(Int(view.bounds.height))
        case "winfo_screenwidth": return PV.int(Int(UIScreen.main.bounds.width))
        case "winfo_screenheight": return PV.int(Int(UIScreen.main.bounds.height))
        default:
            return pyFail("AttributeError", "'" + typeName + "' has no method '" + n + "'")
        }
    }
}

class Container: Widget {
    let inner = UIStackView()

    override init(_ parent: Widget?, _ o: [String: PV]) {
        super.init(parent, o)
        inner.axis = .vertical
        inner.spacing = 4
        inner.alignment = .fill
    }
    override var typeName: String { return "Frame" }

    func markDirty() { PyApp.shared.markDirty(self) }

    func rebuild() {
        for v in inner.arrangedSubviews { inner.removeArrangedSubview(v); v.removeFromSuperview() }
        let kids = children.filter { !$0.destroyed && ($0.packInfo != nil || $0.gridInfo != nil) }
        if kids.contains(where: { $0.gridInfo != nil }) { buildGrid(kids) } else { buildPack(kids) }
        for k in children { if let c = k as? Container { c.rebuild() } }
    }

    func wrap(_ w: Widget, _ info: [String: PV], fillX: Bool) -> UIView {
        let host = UIView()
        let v = w.view
        v.translatesAutoresizingMaskIntoConstraints = false
        host.addSubview(v)
        let px = pyPad(info["padx"])
        let py = pyPad(info["pady"])
        NSLayoutConstraint.activate([
            v.topAnchor.constraint(equalTo: host.topAnchor, constant: py),
            v.bottomAnchor.constraint(equalTo: host.bottomAnchor, constant: -py),
        ])
        if fillX {
            NSLayoutConstraint.activate([
                v.leadingAnchor.constraint(equalTo: host.leadingAnchor, constant: px),
                v.trailingAnchor.constraint(equalTo: host.trailingAnchor, constant: -px),
            ])
        } else {
            NSLayoutConstraint.activate([
                v.centerXAnchor.constraint(equalTo: host.centerXAnchor),
                v.leadingAnchor.constraint(greaterThanOrEqualTo: host.leadingAnchor, constant: px),
                v.trailingAnchor.constraint(lessThanOrEqualTo: host.trailingAnchor, constant: -px),
            ])
        }
        return host
    }

    func buildPack(_ kids: [Widget]) {
        var row: [Widget] = []
        func flush() {
            if row.isEmpty { return }
            let h = UIStackView()
            h.axis = .horizontal
            h.spacing = 4
            h.alignment = .center
            let lefts = row.filter { pyOptStr($0.packInfo?["side"]) != "right" }
            let rights = row.filter { pyOptStr($0.packInfo?["side"]) == "right" }
            for w in lefts { h.addArrangedSubview(wrap(w, w.packInfo ?? [:], fillX: true)) }
            if !rights.isEmpty {
                let sp = UIView()
                sp.setContentHuggingPriority(UILayoutPriority(1), for: .horizontal)
                h.addArrangedSubview(sp)
                for w in rights.reversed() { h.addArrangedSubview(wrap(w, w.packInfo ?? [:], fillX: true)) }
            }
            inner.addArrangedSubview(h)
            row = []
        }
        for w in kids {
            let info = w.packInfo ?? [:]
            let side = pyOptStr(info["side"]) ?? "top"
            if side == "left" || side == "right" { row.append(w); continue }
            flush()
            let fill = pyOptStr(info["fill"]) ?? ""
            inner.addArrangedSubview(wrap(w, info, fillX: fill == "x" || fill == "both"))
        }
        flush()
    }

    func buildGrid(_ kids: [Widget]) {
        var rows: [Int: [(Int, Widget)]] = [:]
        for w in kids {
            let info = w.gridInfo ?? [:]
            let r = (info["row"] ?? PV.null).intLike ?? 0
            let c = (info["column"] ?? PV.null).intLike ?? 0
            rows[r, default: []].append((c, w))
        }
        let counts = Set(rows.values.map { $0.count })
        for r in rows.keys.sorted() {
            let h = UIStackView()
            h.axis = .horizontal
            h.spacing = 4
            h.alignment = .center
            h.distribution = counts.count == 1 ? .fillEqually : .fill
            for (_, w) in (rows[r] ?? []).sorted(by: { $0.0 < $1.0 }) {
                let sticky = pyOptStr(w.gridInfo?["sticky"]) ?? ""
                h.addArrangedSubview(wrap(w, w.gridInfo ?? [:], fillX: sticky.contains("e") || sticky.contains("w") || counts.count == 1))
            }
            inner.addArrangedSubview(h)
        }
    }
}

final class FrameW: Container {
    override init(_ parent: Widget?, _ o: [String: PV]) {
        super.init(parent, o)
        let host = UIView()
        inner.translatesAutoresizingMaskIntoConstraints = false
        host.addSubview(inner)
        NSLayoutConstraint.activate([
            inner.topAnchor.constraint(equalTo: host.topAnchor),
            inner.bottomAnchor.constraint(equalTo: host.bottomAnchor),
            inner.leadingAnchor.constraint(equalTo: host.leadingAnchor),
            inner.trailingAnchor.constraint(equalTo: host.trailingAnchor),
        ])
        view = host
        refresh()
    }
}

final class TkRoot: Container {
    let scroll = UIScrollView()
    override init(_ parent: Widget?, _ o: [String: PV]) {
        super.init(nil, o)
        let app = PyApp.shared
        app.root = self
        view = scroll
        guard let vc = app.rootVC else { return }
        scroll.translatesAutoresizingMaskIntoConstraints = false
        vc.view.addSubview(scroll)
        let safe = vc.view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: safe.topAnchor),
            scroll.bottomAnchor.constraint(equalTo: safe.bottomAnchor),
            scroll.leadingAnchor.constraint(equalTo: safe.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: safe.trailingAnchor),
        ])
        inner.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(inner)
        NSLayoutConstraint.activate([
            inner.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 12),
            inner.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -12),
            inner.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 12),
            inner.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -12),
            inner.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -24),
        ])
        vc.view.backgroundColor = .systemBackground
        refresh()
    }
    override var typeName: String { return "Tk" }
    override func refresh() {
        if let c = pyColor(opts["bg"] ?? opts["background"]) { PyApp.shared.rootVC?.view.backgroundColor = c }
    }
    override func method(_ n: String, _ a: [PV], _ k: [String: PV]) -> PV {
        switch n {
        case "title", "geometry", "resizable", "minsize", "maxsize", "attributes", "protocol", "iconbitmap",
             "mainloop", "quit", "destroy", "state", "overrideredirect", "wm_title", "iconphoto":
            return PV.null
        case "after":
            guard a.count >= 2, let ms = a[0].num else { return PV.null }
            let id = PyApp.shared.nextAfter
            PyApp.shared.nextAfter += 1
            let f = a[1]
            let rest = Array(a.dropFirst(2))
            DispatchQueue.main.asyncAfter(deadline: .now() + max(ms, 0) / 1000.0) {
                if PyApp.shared.cancelled.contains(id) { return }
                _ = pyCall(f, rest, [:])
            }
            return PV.int(id)
        case "after_cancel":
            if let id = (a.first ?? PV.null).intLike { PyApp.shared.cancelled.insert(id) }
            return PV.null
        default: return super.method(n, a, k)
        }
    }
}

final class LabelW: Widget {
    let label = UILabel()
    override init(_ parent: Widget?, _ o: [String: PV]) {
        super.init(parent, o)
        label.numberOfLines = 0
        view = label
        if let tv = varOf(o["textvariable"]) { tv.observers.append { [weak self] in self?.refresh() } }
        refresh()
    }
    override var typeName: String { return "Label" }
    override func refresh() {
        super.refresh()
        if let tv = varOf(opts["textvariable"]) { label.text = pyStr(tv.value) } else { label.text = pyOptStr(opts["text"]) ?? "" }
        if let f = pyFont(opts["font"]) { label.font = f }
        if let c = pyColor(opts["fg"] ?? opts["foreground"]) { label.textColor = c }
        switch pyOptStr(opts["justify"]) ?? "" {
        case "left": label.textAlignment = .left
        case "right": label.textAlignment = .right
        default: label.textAlignment = .center
        }
    }
}

final class ButtonW: Widget {
    let button = UIButton(type: .system)
    override init(_ parent: Widget?, _ o: [String: PV]) {
        super.init(parent, o)
        view = button
        button.addAction(UIAction { [weak self] _ in self?.fire() }, for: .touchUpInside)
        refresh()
    }
    override var typeName: String { return "Button" }
    func fire() {
        if let c = opts["command"], case .null = c { return }
        if let c = opts["command"] { _ = pyCall(c, [], [:]) }
    }
    override func refresh() {
        super.refresh()
        var cfg = UIButton.Configuration.filled()
        cfg.title = pyOptStr(opts["text"]) ?? ""
        if let c = pyColor(opts["bg"] ?? opts["background"]) { cfg.baseBackgroundColor = c }
        if let c = pyColor(opts["fg"] ?? opts["foreground"]) { cfg.baseForegroundColor = c }
        if let f = pyFont(opts["font"]) {
            cfg.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { (inc: AttributeContainer) -> AttributeContainer in
                var out = inc
                out.font = f
                return out
            }
        }
        button.configuration = cfg
        button.isEnabled = (pyOptStr(opts["state"]) ?? "normal") != "disabled"
        view.backgroundColor = nil
    }
}

final class EntryDelegate: NSObject, UITextFieldDelegate {
    weak var owner: EntryW?
    func textFieldShouldReturn(_ tf: UITextField) -> Bool {
        if let f = owner?.onReturn { _ = pyCall(f, [PV.null], [:]) }
        tf.resignFirstResponder()
        return true
    }
}

final class EntryW: Widget {
    let field = UITextField()
    let del = EntryDelegate()
    var onReturn: PV? = nil
    override init(_ parent: Widget?, _ o: [String: PV]) {
        super.init(parent, o)
        view = field
        field.borderStyle = .roundedRect
        field.autocorrectionType = .no
        field.autocapitalizationType = .none
        del.owner = self
        field.delegate = del
        if let tv = varOf(o["textvariable"]) {
            field.text = pyStr(tv.value)
            tv.observers.append { [weak self] in
                guard let s = self, let t = varOf(s.opts["textvariable"]) else { return }
                if s.field.text != pyStr(t.value) { s.field.text = pyStr(t.value) }
            }
        }
        field.addAction(UIAction { [weak self] _ in
            guard let s = self, let t = varOf(s.opts["textvariable"]) else { return }
            t.value = t.coerce(PV.str(s.field.text ?? ""))
            for ob in t.observers { ob() }
        }, for: .editingChanged)
        refresh()
    }
    override var typeName: String { return "Entry" }
    override func refresh() {
        field.isSecureTextEntry = (pyOptStr(opts["show"]) ?? "") != ""
        if let f = pyFont(opts["font"]) { field.font = f }
        if let c = pyColor(opts["fg"] ?? opts["foreground"]) { field.textColor = c }
        field.isEnabled = (pyOptStr(opts["state"]) ?? "normal") != "disabled"
    }
    func setText(_ s: String) {
        field.text = s
        if let t = varOf(opts["textvariable"]) {
            t.value = t.coerce(PV.str(s))
            for ob in t.observers { ob() }
        }
    }
    override func method(_ n: String, _ a: [PV], _ k: [String: PV]) -> PV {
        let cur = field.text ?? ""
        switch n {
        case "get": return PV.str(cur)
        case "insert":
            guard a.count >= 2 else { return PV.null }
            let s = pyStr(a[1])
            if let i = a[0].intLike, i < cur.count {
                var chars = Array(cur)
                chars.insert(contentsOf: Array(s), at: max(i, 0))
                setText(String(chars))
            } else { setText(cur + s) }
            return PV.null
        case "delete":
            var chars = Array(cur)
            let from = (a.first ?? PV.null).intLike ?? 0
            var to = chars.count
            if a.count > 1, let t = a[1].intLike { to = min(t, chars.count) }
            if from < to && from >= 0 { chars.removeSubrange(from..<to) }
            setText(String(chars))
            return PV.null
        case "focus", "focus_set": field.becomeFirstResponder(); return PV.null
        case "bind":
            if a.count >= 2, case .str(let ev) = a[0], ev.contains("Return") { onReturn = a[1] }
            return PV.null
        default: return super.method(n, a, k)
        }
    }
}

final class CheckW: Widget {
    let row = UIStackView()
    let toggle = UISwitch()
    let label = UILabel()
    override init(_ parent: Widget?, _ o: [String: PV]) {
        super.init(parent, o)
        row.axis = .horizontal
        row.spacing = 8
        row.alignment = .center
        label.numberOfLines = 0
        row.addArrangedSubview(toggle)
        row.addArrangedSubview(label)
        view = row
        if let v = varOf(o["variable"]) {
            toggle.isOn = v.value.truthy
            v.observers.append { [weak self] in
                guard let s = self, let t = varOf(s.opts["variable"]) else { return }
                s.toggle.isOn = t.value.truthy
            }
        }
        toggle.addAction(UIAction { [weak self] _ in
            guard let s = self else { return }
            if let t = varOf(s.opts["variable"]) { t.assign(PV.bool(s.toggle.isOn)) }
            if let c = s.opts["command"] { _ = pyCall(c, [], [:]) }
        }, for: .valueChanged)
        refresh()
    }
    override var typeName: String { return "Checkbutton" }
    override func refresh() {
        label.text = pyOptStr(opts["text"]) ?? ""
        if let f = pyFont(opts["font"]) { label.font = f }
        if let c = pyColor(opts["fg"] ?? opts["foreground"]) { label.textColor = c }
    }
    override func method(_ n: String, _ a: [PV], _ k: [String: PV]) -> PV {
        switch n {
        case "select": if let t = varOf(opts["variable"]) { t.assign(PV.bool(true)) }; return PV.null
        case "deselect": if let t = varOf(opts["variable"]) { t.assign(PV.bool(false)) }; return PV.null
        case "toggle": if let t = varOf(opts["variable"]) { t.assign(PV.bool(!t.value.truthy)) }; return PV.null
        default: return super.method(n, a, k)
        }
    }
}

// ---------------------------------------------------------------- constructors
func parentOf(_ a: [PV], _ k: [String: PV]) -> Widget? {
    if let f = a.first, case .obj(let o) = f, let w = o as? Widget { return w }
    if let m = k["master"], case .obj(let o) = m, let w = o as? Widget { return w }
    return PyApp.shared.root
}

func widgetCtor(_ make: @escaping (Widget?, [String: PV]) -> Widget) -> PV {
    return PV.fn(PFn { a, k in
        var o = k
        o["master"] = nil
        return PV.obj(make(parentOf(a, k), o))
    })
}

let b_Tk = PV.fn(PFn { a, k in PV.obj(TkRoot(nil, k)) })
let b_Frame = widgetCtor { FrameW($0, $1) }
let b_LabelFrame = widgetCtor { FrameW($0, $1) }
let b_Label = widgetCtor { LabelW($0, $1) }
let b_Button = widgetCtor { ButtonW($0, $1) }
let b_Entry = widgetCtor { EntryW($0, $1) }
let b_Checkbutton = widgetCtor { CheckW($0, $1) }

// constants (as strings, like tkinter)
let b_END = PV.str("end")
let b_LEFT = PV.str("left")
let b_RIGHT = PV.str("right")
let b_TOP = PV.str("top")
let b_BOTTOM = PV.str("bottom")
let b_X = PV.str("x")
let b_Y = PV.str("y")
let b_BOTH = PV.str("both")
let b_CENTER = PV.str("center")
let b_W = PV.str("w")
let b_E = PV.str("e")
let b_N = PV.str("n")
let b_S = PV.str("s")
let b_NSEW = PV.str("nsew")
let b_EW = PV.str("ew")
let b_NS = PV.str("ns")
let b_NORMAL = PV.str("normal")
let b_DISABLED = PV.str("disabled")
let b_WORD = PV.str("word")
let b_YES = PV.bool(true)
let b_NO = PV.bool(false)
let b_TRUE = PV.bool(true)
let b_FALSE = PV.bool(false)

// messagebox
func msgCtor() -> PV {
    return PV.fn(PFn { a, k in
        let title = a.count > 0 ? pyStr(a[0]) : ""
        let msg = a.count > 1 ? pyStr(a[1]) : ""
        pyShowAlert(title, msg)
        return PV.str("ok")
    })
}
let b_showinfo = msgCtor()
let b_showwarning = msgCtor()
let b_showerror = msgCtor()

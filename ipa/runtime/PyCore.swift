// PocketTk iOS runtime, part 1: Python values and operations.
// The generated Script.swift calls these functions. Everything is dynamically typed (PV) like Python.
import Foundation

final class PList { var a: [PV]; init(_ a: [PV]) { self.a = a } }

final class PFn {
    let f: ([PV], [String: PV]) -> PV
    init(_ f: @escaping ([PV], [String: PV]) -> PV) { self.f = f }
}

final class PDict {
    var order: [String] = []
    var keys: [String: PV] = [:]
    var vals: [String: PV] = [:]
    func get(_ k: PV) -> PV? { return vals[k.hk] }
    func set(_ k: PV, _ v: PV) {
        let h = k.hk
        if vals[h] == nil { order.append(h); keys[h] = k }
        vals[h] = v
    }
    func remove(_ k: PV) -> PV? {
        let h = k.hk
        guard let v = vals[h] else { return nil }
        vals[h] = nil; keys[h] = nil
        order = order.filter { $0 != h }
        return v
    }
    var keyList: [PV] { return order.map { keys[$0] ?? PV.null } }
    var valList: [PV] { return order.map { vals[$0] ?? PV.null } }
}

class PyObj {
    var typeName: String { return "object" }
    func attr(_ n: String) -> PV { return pyFail("AttributeError", "'" + typeName + "' object has no attribute '" + n + "'") }
    func setAttr(_ n: String, _ v: PV) { _ = pyFail("AttributeError", "cannot set attribute '" + n + "'") }
    func call(_ a: [PV], _ k: [String: PV]) -> PV { return pyFail("TypeError", "'" + typeName + "' object is not callable") }
    func method(_ n: String, _ a: [PV], _ k: [String: PV]) -> PV { return pyCall(attr(n), a, k) }
    func text() -> String { return "<" + typeName + " object>" }
}

enum PV {
    case null
    case bool(Bool)
    case int(Int)
    case double(Double)
    case str(String)
    case list(PList)
    case dict(PDict)
    case tuple([PV])
    case fn(PFn)
    case obj(PyObj)
}

// ---------------------------------------------------------------- errors
var pyFailed = false
@discardableResult
func pyFail(_ kind: String, _ msg: String) -> PV {
    NSLog("PocketTk: %@: %@", kind, msg)
    if !pyFailed {
        pyFailed = true
        pyShowAlert(kind, msg)
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { pyFailed = false }
    }
    return PV.null
}

// ---------------------------------------------------------------- PV helpers
extension PV {
    var truthy: Bool {
        switch self {
        case .null: return false
        case .bool(let b): return b
        case .int(let i): return i != 0
        case .double(let d): return d != 0
        case .str(let s): return !s.isEmpty
        case .list(let l): return !l.a.isEmpty
        case .dict(let d): return !d.order.isEmpty
        case .tuple(let t): return !t.isEmpty
        case .fn: return true
        case .obj: return true
        }
    }
    var intLike: Int? {
        switch self {
        case .int(let i): return i
        case .bool(let b): return b ? 1 : 0
        default: return nil
        }
    }
    var num: Double? {
        switch self {
        case .int(let i): return Double(i)
        case .bool(let b): return b ? 1 : 0
        case .double(let d): return d
        default: return nil
        }
    }
    var hk: String {
        switch self {
        case .null: return "n"
        case .bool(let b): return b ? "i1" : "i0"
        case .int(let i): return "i" + String(i)
        case .double(let d):
            if d == d.rounded() && abs(d) < 1e15 { return "i" + String(Int(d)) }
            return "d" + String(d)
        case .str(let s): return "s" + s
        case .tuple(let t): return "t(" + t.map { $0.hk }.joined(separator: ",") + ")"
        case .list(let l): return "l" + String(UInt(bitPattern: ObjectIdentifier(l).hashValue))
        case .dict(let d): return "m" + String(UInt(bitPattern: ObjectIdentifier(d).hashValue))
        case .fn(let f): return "f" + String(UInt(bitPattern: ObjectIdentifier(f).hashValue))
        case .obj(let o): return "o" + String(UInt(bitPattern: ObjectIdentifier(o).hashValue))
        }
    }
}

func pyTruthy(_ v: PV) -> Bool { return v.truthy }

func pyFloatStr(_ d: Double) -> String {
    if d.isNaN { return "nan" }
    if d.isInfinite { return d < 0 ? "-inf" : "inf" }
    if d == d.rounded() && abs(d) < 1e16 { return String(Int(d)) + ".0" }
    return String(d)
}

func pyReprStr(_ s: String) -> String {
    var q = "'"
    if s.contains("'") && !s.contains("\"") { q = "\"" }
    var out = q
    for ch in s {
        switch ch {
        case "\n": out += "\\n"
        case "\t": out += "\\t"
        case "\r": out += "\\r"
        case "\\": out += "\\\\"
        default:
            if String(ch) == q { out += "\\" + q } else { out.append(ch) }
        }
    }
    return out + q
}

func pyRepr(_ v: PV) -> String {
    switch v {
    case .str(let s): return pyReprStr(s)
    case .list(let l): return "[" + l.a.map { pyRepr($0) }.joined(separator: ", ") + "]"
    case .tuple(let t):
        if t.count == 1 { return "(" + pyRepr(t[0]) + ",)" }
        return "(" + t.map { pyRepr($0) }.joined(separator: ", ") + ")"
    case .dict(let d):
        var parts: [String] = []
        for h in d.order { parts.append(pyRepr(d.keys[h] ?? PV.null) + ": " + pyRepr(d.vals[h] ?? PV.null)) }
        return "{" + parts.joined(separator: ", ") + "}"
    default: return pyStr(v)
    }
}

func pyStr(_ v: PV) -> String {
    switch v {
    case .null: return "None"
    case .bool(let b): return b ? "True" : "False"
    case .int(let i): return String(i)
    case .double(let d): return pyFloatStr(d)
    case .str(let s): return s
    case .list, .dict, .tuple: return pyRepr(v)
    case .fn: return "<function>"
    case .obj(let o): return o.text()
    }
}

func pyTypeName(_ v: PV) -> String {
    switch v {
    case .null: return "NoneType"
    case .bool: return "bool"
    case .int: return "int"
    case .double: return "float"
    case .str: return "str"
    case .list: return "list"
    case .dict: return "dict"
    case .tuple: return "tuple"
    case .fn: return "function"
    case .obj(let o): return o.typeName
    }
}

// ---------------------------------------------------------------- calls and arguments
func pyCall(_ f: PV, _ a: [PV], _ k: [String: PV]) -> PV {
    switch f {
    case .fn(let fn): return fn.f(a, k)
    case .obj(let o): return o.call(a, k)
    default: return pyFail("TypeError", "'" + pyTypeName(f) + "' object is not callable")
    }
}

func pyArg(_ a: [PV], _ k: [String: PV], _ i: Int, _ n: String, _ d: (() -> PV)?) -> PV {
    if i < a.count { return a[i] }
    if let v = k[n] { return v }
    if let d = d { return d() }
    return pyFail("TypeError", "missing argument '" + n + "'")
}

func pyRest(_ a: [PV], _ from: Int) -> PV {
    if a.count <= from { return PV.tuple([]) }
    return PV.tuple(Array(a[from...]))
}

func pyKwRest(_ k: [String: PV], _ named: [String]) -> PV {
    let d = PDict()
    for (key, v) in k where !named.contains(key) { d.set(PV.str(key), v) }
    return PV.dict(d)
}

func pyMakeDict(_ kv: [PV]) -> PV {
    let d = PDict()
    var i = 0
    while i + 1 < kv.count { d.set(kv[i], kv[i + 1]); i += 2 }
    return PV.dict(d)
}

func pyAnd(_ a: PV, _ b: @autoclosure () -> PV) -> PV { return a.truthy ? b() : a }
func pyOr(_ a: PV, _ b: @autoclosure () -> PV) -> PV { return a.truthy ? a : b() }

// ---------------------------------------------------------------- arithmetic
func pyNeg(_ a: PV) -> PV {
    if let i = a.intLike { return PV.int(-i) }
    if let d = a.num { return PV.double(-d) }
    return pyFail("TypeError", "bad operand type for unary -")
}

func pyAdd(_ a: PV, _ b: PV) -> PV {
    switch (a, b) {
    case (.str(let x), .str(let y)): return PV.str(x + y)
    case (.list(let x), .list(let y)): return PV.list(PList(x.a + y.a))
    case (.tuple(let x), .tuple(let y)): return PV.tuple(x + y)
    default:
        if let x = a.intLike, let y = b.intLike { return PV.int(x &+ y) }
        if let x = a.num, let y = b.num { return PV.double(x + y) }
        return pyFail("TypeError", "unsupported operand types for +: '" + pyTypeName(a) + "' and '" + pyTypeName(b) + "'")
    }
}

func pySub(_ a: PV, _ b: PV) -> PV {
    if let x = a.intLike, let y = b.intLike { return PV.int(x &- y) }
    if let x = a.num, let y = b.num { return PV.double(x - y) }
    return pyFail("TypeError", "unsupported operand types for -: '" + pyTypeName(a) + "' and '" + pyTypeName(b) + "'")
}

func pyRepeat(_ s: PV, _ n: Int) -> PV {
    let c = max(n, 0)
    switch s {
    case .str(let x): return PV.str(String(repeating: x, count: c))
    case .list(let l):
        var r: [PV] = []
        for _ in 0..<c { r += l.a }
        return PV.list(PList(r))
    case .tuple(let t):
        var r: [PV] = []
        for _ in 0..<c { r += t }
        return PV.tuple(r)
    default: return PV.null
    }
}

func pyMul(_ a: PV, _ b: PV) -> PV {
    if let x = a.intLike, let y = b.intLike { return PV.int(x &* y) }
    if let x = a.num, let y = b.num { return PV.double(x * y) }
    if let n = b.intLike { return pyRepeat(a, n) }
    if let n = a.intLike { return pyRepeat(b, n) }
    return pyFail("TypeError", "unsupported operand types for *: '" + pyTypeName(a) + "' and '" + pyTypeName(b) + "'")
}

func pyDiv(_ a: PV, _ b: PV) -> PV {
    guard let x = a.num, let y = b.num else { return pyFail("TypeError", "unsupported operand types for /") }
    if y == 0 { return pyFail("ZeroDivisionError", "division by zero") }
    return PV.double(x / y)
}

func pyFloorDiv(_ a: PV, _ b: PV) -> PV {
    if let x = a.intLike, let y = b.intLike {
        if y == 0 { return pyFail("ZeroDivisionError", "integer division or modulo by zero") }
        var q = x / y
        if (x % y != 0) && ((x < 0) != (y < 0)) { q -= 1 }
        return PV.int(q)
    }
    guard let x = a.num, let y = b.num else { return pyFail("TypeError", "unsupported operand types for //") }
    if y == 0 { return pyFail("ZeroDivisionError", "float floor division by zero") }
    return PV.double((x / y).rounded(.down))
}

func pyMod(_ a: PV, _ b: PV) -> PV {
    if case .str(let f) = a { return PV.str(pyPercentFormat(f, b)) }
    if let x = a.intLike, let y = b.intLike {
        if y == 0 { return pyFail("ZeroDivisionError", "integer division or modulo by zero") }
        let r = x % y
        if r != 0 && ((r < 0) != (y < 0)) { return PV.int(r + y) }
        return PV.int(r)
    }
    guard let x = a.num, let y = b.num else { return pyFail("TypeError", "unsupported operand types for %") }
    if y == 0 { return pyFail("ZeroDivisionError", "float modulo") }
    return PV.double(x - (x / y).rounded(.down) * y)
}

func pyPow(_ a: PV, _ b: PV) -> PV {
    if let x = a.intLike, let y = b.intLike, y >= 0 {
        var r = 1
        var i = 0
        while i < y { r = r &* x; i += 1 }
        return PV.int(r)
    }
    guard let x = a.num, let y = b.num else { return pyFail("TypeError", "unsupported operand types for **") }
    return PV.double(pow(x, y))
}

// ---------------------------------------------------------------- comparison
func pyEq(_ a: PV, _ b: PV) -> Bool {
    switch (a, b) {
    case (.null, .null): return true
    case (.str(let x), .str(let y)): return x == y
    case (.list(let x), .list(let y)):
        if x.a.count != y.a.count { return false }
        for i in 0..<x.a.count { if !pyEq(x.a[i], y.a[i]) { return false } }
        return true
    case (.tuple(let x), .tuple(let y)):
        if x.count != y.count { return false }
        for i in 0..<x.count { if !pyEq(x[i], y[i]) { return false } }
        return true
    case (.dict(let x), .dict(let y)):
        if x.order.count != y.order.count { return false }
        for h in x.order {
            guard let ya = y.vals[h], let xa = x.vals[h] else { return false }
            if !pyEq(xa, ya) { return false }
        }
        return true
    case (.obj(let x), .obj(let y)): return x === y
    case (.fn(let x), .fn(let y)): return x === y
    default:
        if let x = a.num, let y = b.num { return x == y }
        return false
    }
}

// -1, 0, 1, or nil when the two cannot be ordered
func pyCmp(_ a: PV, _ b: PV) -> Int? {
    if let x = a.intLike, let y = b.intLike { return x < y ? -1 : (x > y ? 1 : 0) }
    if let x = a.num, let y = b.num { return x < y ? -1 : (x > y ? 1 : 0) }
    switch (a, b) {
    case (.str(let x), .str(let y)): return x < y ? -1 : (x > y ? 1 : 0)
    case (.list(let x), .list(let y)): return pyCmpSeq(x.a, y.a)
    case (.tuple(let x), .tuple(let y)): return pyCmpSeq(x, y)
    default: return nil
    }
}

func pyCmpSeq(_ x: [PV], _ y: [PV]) -> Int? {
    var i = 0
    while i < x.count && i < y.count {
        guard let c = pyCmp(x[i], y[i]) else { return nil }
        if c != 0 { return c }
        i += 1
    }
    return x.count < y.count ? -1 : (x.count > y.count ? 1 : 0)
}

func pyOrder(_ a: PV, _ b: PV, _ op: String) -> Bool {
    guard let c = pyCmp(a, b) else {
        pyFail("TypeError", "'" + op + "' not supported between '" + pyTypeName(a) + "' and '" + pyTypeName(b) + "'")
        return false
    }
    switch op {
    case "<": return c < 0
    case "<=": return c <= 0
    case ">": return c > 0
    default: return c >= 0
    }
}
func pyLt(_ a: PV, _ b: PV) -> Bool { return pyOrder(a, b, "<") }
func pyLe(_ a: PV, _ b: PV) -> Bool { return pyOrder(a, b, "<=") }
func pyGt(_ a: PV, _ b: PV) -> Bool { return pyOrder(a, b, ">") }
func pyGe(_ a: PV, _ b: PV) -> Bool { return pyOrder(a, b, ">=") }

func pyIs(_ a: PV, _ b: PV) -> Bool {
    switch (a, b) {
    case (.null, .null): return true
    case (.bool(let x), .bool(let y)): return x == y
    case (.obj(let x), .obj(let y)): return x === y
    case (.list(let x), .list(let y)): return x === y
    case (.dict(let x), .dict(let y)): return x === y
    case (.fn(let x), .fn(let y)): return x === y
    default: return false
    }
}

func pyContains(_ c: PV, _ item: PV) -> Bool {
    switch c {
    case .str(let s):
        if case .str(let x) = item { return x.isEmpty || s.contains(x) }
        pyFail("TypeError", "'in <string>' requires string as left operand")
        return false
    case .dict(let d): return d.get(item) != nil
    case .list(let l): return l.a.contains(where: { pyEq($0, item) })
    case .tuple(let t): return t.contains(where: { pyEq($0, item) })
    default:
        pyFail("TypeError", "argument of type '" + pyTypeName(c) + "' is not iterable")
        return false
    }
}

// ---------------------------------------------------------------- sequences
func pyIter(_ c: PV) -> [PV] {
    switch c {
    case .list(let l): return l.a
    case .tuple(let t): return t
    case .str(let s): return s.map { PV.str(String($0)) }
    case .dict(let d): return d.keyList
    default:
        pyFail("TypeError", "'" + pyTypeName(c) + "' object is not iterable")
        return []
    }
}

func pyItem(_ c: PV, _ i: Int) -> PV {
    let a = pyIter(c)
    if i < a.count { return a[i] }
    return pyFail("ValueError", "not enough values to unpack")
}

func pyLen(_ c: PV) -> Int {
    switch c {
    case .str(let s): return s.count
    case .list(let l): return l.a.count
    case .tuple(let t): return t.count
    case .dict(let d): return d.order.count
    default:
        pyFail("TypeError", "object of type '" + pyTypeName(c) + "' has no len()")
        return 0
    }
}

func pyGetItem(_ c: PV, _ i: PV) -> PV {
    switch c {
    case .dict(let d):
        if let v = d.get(i) { return v }
        return pyFail("KeyError", pyRepr(i))
    case .list(let l): return pyIndexSeq(l.a, i)
    case .tuple(let t): return pyIndexSeq(t, i)
    case .str(let s):
        let chars = s.map { PV.str(String($0)) }
        return pyIndexSeq(chars, i)
    case .obj(let o): return o.method("__getitem__", [i], [:])
    default: return pyFail("TypeError", "'" + pyTypeName(c) + "' object is not subscriptable")
    }
}

func pyIndexSeq(_ a: [PV], _ i: PV) -> PV {
    guard var n = i.intLike else { return pyFail("TypeError", "indices must be integers") }
    if n < 0 { n += a.count }
    if n < 0 || n >= a.count { return pyFail("IndexError", "index out of range") }
    return a[n]
}

func pySetItem(_ c: PV, _ i: PV, _ v: PV) {
    switch c {
    case .dict(let d): d.set(i, v)
    case .list(let l):
        guard var n = i.intLike else { pyFail("TypeError", "list indices must be integers"); return }
        if n < 0 { n += l.a.count }
        if n < 0 || n >= l.a.count { pyFail("IndexError", "list assignment index out of range"); return }
        l.a[n] = v
    default: pyFail("TypeError", "'" + pyTypeName(c) + "' object does not support item assignment")
    }
}

func pyDelItem(_ c: PV, _ i: PV) {
    switch c {
    case .dict(let d): if d.remove(i) == nil { pyFail("KeyError", pyRepr(i)) }
    case .list(let l):
        guard var n = i.intLike else { return }
        if n < 0 { n += l.a.count }
        if n >= 0 && n < l.a.count { l.a.remove(at: n) }
    default: pyFail("TypeError", "cannot delete item")
    }
}

func pySliceIdx(_ n: Int, _ lo: PV, _ hi: PV, _ step: PV) -> [Int] {
    let st = step.intLike ?? 1
    if st == 0 { pyFail("ValueError", "slice step cannot be zero"); return [] }
    var start: Int
    var stop: Int
    if st > 0 {
        start = lo.intLike ?? 0
        stop = hi.intLike ?? n
        if start < 0 { start = max(start + n, 0) }
        if stop < 0 { stop = max(stop + n, 0) }
        start = min(start, n); stop = min(stop, n)
        var r: [Int] = []
        var i = start
        while i < stop { r.append(i); i += st }
        return r
    }
    start = lo.intLike ?? (n - 1)
    stop = hi.intLike ?? -n - 1
    if start < 0 { start += n }
    if stop < 0 && hi.intLike != nil { stop += n }
    if hi.intLike == nil { stop = -1 }
    start = min(start, n - 1)
    var r: [Int] = []
    var i = start
    while i > stop && i >= 0 { r.append(i); i += st }
    return r
}

func pySlice(_ c: PV, _ lo: PV, _ hi: PV, _ step: PV) -> PV {
    switch c {
    case .list(let l):
        return PV.list(PList(pySliceIdx(l.a.count, lo, hi, step).map { l.a[$0] }))
    case .tuple(let t):
        return PV.tuple(pySliceIdx(t.count, lo, hi, step).map { t[$0] })
    case .str(let s):
        let chars = Array(s)
        return PV.str(String(pySliceIdx(chars.count, lo, hi, step).map { chars[$0] }))
    default: return pyFail("TypeError", "'" + pyTypeName(c) + "' object is not sliceable")
    }
}

// ---------------------------------------------------------------- formatting
func pyFmt(_ v: PV, _ spec: String) -> String {
    if spec.isEmpty { return pyStr(v) }
    var chars = Array(spec)
    var fill: Character = " "
    var align: Character? = nil
    if chars.count >= 2 && "<>^=".contains(chars[1]) {
        fill = chars[0]; align = chars[1]; chars.removeFirst(2)
    } else if let f = chars.first, "<>^=".contains(f) {
        align = f; chars.removeFirst()
    }
    var sign = ""
    if let f = chars.first, f == "+" || f == "-" || f == " " { sign = String(f); chars.removeFirst() }
    var comma = false
    var zero = false
    if let f = chars.first, f == "0" { zero = true; chars.removeFirst() }
    var widthStr = ""
    while let f = chars.first, f.isNumber { widthStr.append(f); chars.removeFirst() }
    if let f = chars.first, f == "," { comma = true; chars.removeFirst() }
    var prec: Int? = nil
    if let f = chars.first, f == "." {
        chars.removeFirst()
        var p = ""
        while let g = chars.first, g.isNumber { p.append(g); chars.removeFirst() }
        prec = Int(p)
    }
    let type = chars.first.map { String($0) } ?? ""
    var body: String
    switch type {
    case "f", "F":
        let d = v.num ?? 0
        body = String(format: "%." + String(prec ?? 6) + "f", d)
    case "e", "E":
        body = String(format: "%." + String(prec ?? 6) + type, v.num ?? 0)
    case "d":
        body = String(v.intLike ?? Int(v.num ?? 0))
    case "x": body = String(v.intLike ?? 0, radix: 16)
    case "X": body = String(v.intLike ?? 0, radix: 16).uppercased()
    case "b": body = String(v.intLike ?? 0, radix: 2)
    case "%":
        body = String(format: "%." + String(prec ?? 6) + "f", (v.num ?? 0) * 100) + "%"
    default:
        if case .str(let s) = v {
            body = s
            if let p = prec { body = String(s.prefix(p)) }
        } else if case .double(let d) = v, let p = prec {
            body = String(format: "%." + String(p) + "g", d)
        } else {
            body = pyStr(v)
        }
    }
    if comma {
        var intPart = body
        var rest = ""
        if let dot = body.firstIndex(of: ".") { intPart = String(body[body.startIndex..<dot]); rest = String(body[dot...]) }
        var neg = false
        if intPart.hasPrefix("-") { neg = true; intPart.removeFirst() }
        var out = ""
        var n = 0
        for ch in intPart.reversed() {
            if n > 0 && n % 3 == 0 { out.append(",") }
            out.append(ch); n += 1
        }
        body = (neg ? "-" : "") + String(out.reversed()) + rest
    }
    if sign == "+" && !body.hasPrefix("-") && v.num != nil { body = "+" + body }
    if sign == " " && !body.hasPrefix("-") && v.num != nil { body = " " + body }
    let width = Int(widthStr) ?? 0
    if body.count < width {
        let pad = width - body.count
        var al = align
        if al == nil {
            if zero { al = "="; fill = "0" } else { al = (v.num != nil && type != "s") ? ">" : "<" }
        }
        if al == "=" && (body.hasPrefix("-") || body.hasPrefix("+")) {
            let s = String(body.removeFirst())
            body = s + String(repeating: fill, count: pad) + body
        } else if al == "<" {
            body = body + String(repeating: fill, count: pad)
        } else if al == "^" {
            let l = pad / 2
            body = String(repeating: fill, count: l) + body + String(repeating: fill, count: pad - l)
        } else {
            body = String(repeating: fill, count: pad) + body
        }
    }
    return body
}

func pyPercentFormat(_ f: String, _ arg: PV) -> String {
    var args: [PV]
    if case .tuple(let t) = arg { args = t } else { args = [arg] }
    var out = ""
    var idx = 0
    let chars = Array(f)
    var i = 0
    while i < chars.count {
        let c = chars[i]
        if c != "%" { out.append(c); i += 1; continue }
        i += 1
        if i >= chars.count { break }
        if chars[i] == "%" { out.append("%"); i += 1; continue }
        var flags = ""
        while i < chars.count, "-+ 0#".contains(chars[i]) { flags.append(chars[i]); i += 1 }
        var width = ""
        while i < chars.count, chars[i].isNumber { width.append(chars[i]); i += 1 }
        var prec = ""
        if i < chars.count, chars[i] == "." {
            i += 1
            prec = "."
            while i < chars.count, chars[i].isNumber { prec.append(chars[i]); i += 1 }
        }
        if i >= chars.count { break }
        let conv = chars[i]; i += 1
        let v = idx < args.count ? args[idx] : PV.null
        idx += 1
        var spec = ""
        if flags.contains("-") { spec += "<" } else if !width.isEmpty { spec += ">" }
        if flags.contains("0") && !flags.contains("-") { spec = "0" }
        spec += width + (prec == "." ? ".0" : prec)
        switch conv {
        case "d", "i": spec += "d"; out += pyFmt(PV.int(v.intLike ?? Int(v.num ?? 0)), spec)
        case "f": spec += "f"; out += pyFmt(v, spec)
        case "s": out += pyFmt(PV.str(pyStr(v)), spec)
        case "r": out += pyFmt(PV.str(pyRepr(v)), spec)
        case "x", "X": spec += String(conv); out += pyFmt(v, spec)
        default: out += pyStr(v)
        }
    }
    return out
}

func pyFormatMethod(_ f: String, _ a: [PV], _ k: [String: PV]) -> String {
    var out = ""
    let chars = Array(f)
    var i = 0
    var auto = 0
    while i < chars.count {
        let c = chars[i]
        if c == "{" {
            if i + 1 < chars.count && chars[i + 1] == "{" { out.append("{"); i += 2; continue }
            var j = i + 1
            var field = ""
            while j < chars.count && chars[j] != "}" { field.append(chars[j]); j += 1 }
            i = j + 1
            var spec = ""
            var conv = ""
            if let colon = field.firstIndex(of: ":") {
                spec = String(field[field.index(after: colon)...])
                field = String(field[field.startIndex..<colon])
            }
            if let bang = field.firstIndex(of: "!") {
                conv = String(field[field.index(after: bang)...])
                field = String(field[field.startIndex..<bang])
            }
            var v: PV = PV.null
            if field.isEmpty { if auto < a.count { v = a[auto] }; auto += 1 }
            else if let n = Int(field) { if n < a.count { v = a[n] } }
            else { v = k[field] ?? PV.null }
            if conv == "r" { v = PV.str(pyRepr(v)) }
            out += pyFmt(v, spec)
        } else if c == "}" {
            out.append("}")
            i += (i + 1 < chars.count && chars[i + 1] == "}") ? 2 : 1
        } else {
            out.append(c); i += 1
        }
    }
    return out
}

// ---------------------------------------------------------------- methods on built-in types
func pyMethod(_ o: PV, _ name: String, _ a: [PV], _ k: [String: PV]) -> PV {
    switch o {
    case .str(let s): return pyStrMethod(s, name, a, k)
    case .list(let l): return pyListMethod(l, name, a, k)
    case .dict(let d): return pyDictMethod(d, name, a, k)
    case .obj(let p): return p.method(name, a, k)
    case .tuple(let t):
        if name == "count" { return PV.int(t.filter { pyEq($0, a.first ?? PV.null) }.count) }
        if name == "index" {
            for (i, x) in t.enumerated() where pyEq(x, a.first ?? PV.null) { return PV.int(i) }
            return pyFail("ValueError", "tuple.index(x): x not in tuple")
        }
        return pyFail("AttributeError", "'tuple' object has no attribute '" + name + "'")
    default: return pyFail("AttributeError", "'" + pyTypeName(o) + "' object has no attribute '" + name + "'")
    }
}

func pyAttr(_ o: PV, _ name: String) -> PV {
    if case .obj(let p) = o { return p.attr(name) }
    return PV.fn(PFn { a, k in pyMethod(o, name, a, k) })
}

func pySetAttr(_ o: PV, _ name: String, _ v: PV) {
    if case .obj(let p) = o { p.setAttr(name, v); return }
    pyFail("AttributeError", "cannot set attribute '" + name + "' on " + pyTypeName(o))
}

func pyStrMethod(_ s: String, _ n: String, _ a: [PV], _ k: [String: PV]) -> PV {
    let first: PV = a.first ?? PV.null
    switch n {
    case "upper": return PV.str(s.uppercased())
    case "lower": return PV.str(s.lowercased())
    case "title": return PV.str(s.capitalized)
    case "capitalize": return PV.str(s.prefix(1).uppercased() + s.dropFirst().lowercased())
    case "strip", "lstrip", "rstrip":
        var chars = Array(s)
        var set = CharacterSet.whitespacesAndNewlines
        if case .str(let x) = first { set = CharacterSet(charactersIn: x) }
        func isStrip(_ c: Character) -> Bool { return String(c).unicodeScalars.allSatisfy { set.contains($0) } }
        if n != "rstrip" { while let c = chars.first, isStrip(c) { chars.removeFirst() } }
        if n != "lstrip" { while let c = chars.last, isStrip(c) { chars.removeLast() } }
        return PV.str(String(chars))
    case "split":
        var parts: [String]
        if case .str(let sep) = first, !sep.isEmpty {
            parts = s.components(separatedBy: sep)
        } else {
            parts = s.split(whereSeparator: { $0 == " " || $0 == "\n" || $0 == "\t" || $0 == "\r" }).map { String($0) }
        }
        if a.count > 1, let mx = a[1].intLike, mx >= 0, parts.count > mx + 1, case .str(let sep) = first {
            let head = Array(parts[0..<mx])
            let tail = parts[mx...].joined(separator: sep)
            parts = head + [tail]
        }
        return PV.list(PList(parts.map { PV.str($0) }))
    case "splitlines": return PV.list(PList(s.components(separatedBy: "\n").map { PV.str($0) }))
    case "join": return PV.str(pyIter(first).map { pyStr($0) }.joined(separator: s))
    case "replace":
        if a.count >= 2, case .str(let x) = a[0], case .str(let y) = a[1] { return PV.str(s.replacingOccurrences(of: x, with: y)) }
        return PV.str(s)
    case "startswith": if case .str(let x) = first { return PV.bool(s.hasPrefix(x)) }; return PV.bool(false)
    case "endswith": if case .str(let x) = first { return PV.bool(s.hasSuffix(x)) }; return PV.bool(false)
    case "find", "index":
        if case .str(let x) = first, let r = s.range(of: x) { return PV.int(s.distance(from: s.startIndex, to: r.lowerBound)) }
        if n == "index" { return pyFail("ValueError", "substring not found") }
        return PV.int(-1)
    case "count":
        if case .str(let x) = first, !x.isEmpty { return PV.int(s.components(separatedBy: x).count - 1) }
        return PV.int(0)
    case "format": return PV.str(pyFormatMethod(s, a, k))
    case "isdigit": return PV.bool(!s.isEmpty && s.allSatisfy { $0.isNumber })
    case "isalpha": return PV.bool(!s.isEmpty && s.allSatisfy { $0.isLetter })
    case "isalnum": return PV.bool(!s.isEmpty && s.allSatisfy { $0.isLetter || $0.isNumber })
    case "isspace": return PV.bool(!s.isEmpty && s.allSatisfy { $0.isWhitespace })
    case "isupper": return PV.bool(s == s.uppercased() && s != s.lowercased())
    case "islower": return PV.bool(s == s.lowercased() && s != s.uppercased())
    case "zfill":
        let w = first.intLike ?? 0
        if s.count >= w { return PV.str(s) }
        return PV.str(String(repeating: "0", count: w - s.count) + s)
    case "ljust", "rjust", "center":
        let w = first.intLike ?? 0
        var fillc = " "
        if a.count > 1, case .str(let f) = a[1] { fillc = f }
        if s.count >= w { return PV.str(s) }
        let pad = w - s.count
        if n == "ljust" { return PV.str(s + String(repeating: fillc, count: pad)) }
        if n == "rjust" { return PV.str(String(repeating: fillc, count: pad) + s) }
        let l = pad / 2
        return PV.str(String(repeating: fillc, count: l) + s + String(repeating: fillc, count: pad - l))
    default: return pyFail("AttributeError", "'str' object has no attribute '" + n + "'")
    }
}

func pySortValues(_ a: [PV], _ key: PV?, _ reverse: Bool) -> [PV] {
    var pairs: [(PV, PV)] = a.map { (x: PV) -> (PV, PV) in
        if let kf = key, case .null = kf { return (x, x) }
        if let kf = key { return (pyCall(kf, [x], [:]), x) }
        return (x, x)
    }
    pairs.sort { (p, q) in
        let c = pyCmp(p.0, q.0) ?? 0
        return reverse ? c > 0 : c < 0
    }
    return pairs.map { $0.1 }
}

func pyListMethod(_ l: PList, _ n: String, _ a: [PV], _ k: [String: PV]) -> PV {
    let first: PV = a.first ?? PV.null
    switch n {
    case "append": l.a.append(first); return PV.null
    case "extend": l.a += pyIter(first); return PV.null
    case "insert":
        if a.count >= 2, var i = a[0].intLike {
            if i < 0 { i = max(i + l.a.count, 0) }
            l.a.insert(a[1], at: min(i, l.a.count))
        }
        return PV.null
    case "pop":
        if l.a.isEmpty { return pyFail("IndexError", "pop from empty list") }
        var i = l.a.count - 1
        if let n0 = first.intLike { i = n0 < 0 ? n0 + l.a.count : n0 }
        if i < 0 || i >= l.a.count { return pyFail("IndexError", "pop index out of range") }
        return l.a.remove(at: i)
    case "remove":
        for (i, x) in l.a.enumerated() where pyEq(x, first) { l.a.remove(at: i); return PV.null }
        return pyFail("ValueError", "list.remove(x): x not in list")
    case "index":
        for (i, x) in l.a.enumerated() where pyEq(x, first) { return PV.int(i) }
        return pyFail("ValueError", "value is not in list")
    case "count": return PV.int(l.a.filter { pyEq($0, first) }.count)
    case "sort":
        l.a = pySortValues(l.a, k["key"], (k["reverse"] ?? PV.null).truthy)
        return PV.null
    case "reverse": l.a.reverse(); return PV.null
    case "clear": l.a.removeAll(); return PV.null
    case "copy": return PV.list(PList(l.a))
    default: return pyFail("AttributeError", "'list' object has no attribute '" + n + "'")
    }
}

func pyDictMethod(_ d: PDict, _ n: String, _ a: [PV], _ k: [String: PV]) -> PV {
    let first: PV = a.first ?? PV.null
    switch n {
    case "get": return d.get(first) ?? (a.count > 1 ? a[1] : PV.null)
    case "keys": return PV.list(PList(d.keyList))
    case "values": return PV.list(PList(d.valList))
    case "items":
        var r: [PV] = []
        for h in d.order { r.append(PV.tuple([d.keys[h] ?? PV.null, d.vals[h] ?? PV.null])) }
        return PV.list(PList(r))
    case "pop":
        if let v = d.remove(first) { return v }
        if a.count > 1 { return a[1] }
        return pyFail("KeyError", pyRepr(first))
    case "setdefault":
        if let v = d.get(first) { return v }
        let dv = a.count > 1 ? a[1] : PV.null
        d.set(first, dv)
        return dv
    case "update":
        if case .dict(let o) = first { for h in o.order { d.set(o.keys[h] ?? PV.null, o.vals[h] ?? PV.null) } }
        for (key, v) in k { d.set(PV.str(key), v) }
        return PV.null
    case "clear": d.order = []; d.keys = [:]; d.vals = [:]; return PV.null
    case "copy":
        let c = PDict()
        for h in d.order { c.set(d.keys[h] ?? PV.null, d.vals[h] ?? PV.null) }
        return PV.dict(c)
    default: return pyFail("AttributeError", "'dict' object has no attribute '" + n + "'")
    }
}

// ---------------------------------------------------------------- classes
final class PClass: PyObj {
    let name: String
    let base: PClass?
    var methods: [String: PFn]
    var attrs: [String: PV]
    init(_ name: String, _ base: PV, _ methods: [String: PFn], _ attrs: [String: PV]) {
        self.name = name
        if case .obj(let o) = base, let c = o as? PClass { self.base = c } else { self.base = nil }
        self.methods = methods
        self.attrs = attrs
    }
    override var typeName: String { return "type" }
    func findMethod(_ n: String) -> PFn? {
        if let m = methods[n] { return m }
        return base?.findMethod(n)
    }
    func findAttr(_ n: String) -> PV? {
        if let v = attrs[n] { return v }
        return base?.findAttr(n)
    }
    override func attr(_ n: String) -> PV {
        if let v = findAttr(n) { return v }
        if let m = findMethod(n) { return PV.fn(m) }
        return pyFail("AttributeError", "type object '" + name + "' has no attribute '" + n + "'")
    }
    override func call(_ a: [PV], _ k: [String: PV]) -> PV {
        let inst = PInst(self)
        let me = PV.obj(inst)
        if let ini = findMethod("__init__") { _ = ini.f([me] + a, k) }
        return me
    }
    override func text() -> String { return "<class '" + name + "'>" }
}

final class PInst: PyObj {
    let cls: PClass
    var attrs: [String: PV] = [:]
    init(_ c: PClass) { cls = c }
    override var typeName: String { return cls.name }
    override func attr(_ n: String) -> PV {
        if let v = attrs[n] { return v }
        if let m = cls.findMethod(n) {
            let me = PV.obj(self)
            return PV.fn(PFn { a, k in m.f([me] + a, k) })
        }
        if let v = cls.findAttr(n) { return v }
        return pyFail("AttributeError", "'" + cls.name + "' object has no attribute '" + n + "'")
    }
    override func setAttr(_ n: String, _ v: PV) { attrs[n] = v }
    override func text() -> String {
        if let m = cls.findMethod("__str__") { return pyStr(m.f([PV.obj(self)], [:])) }
        if let m = cls.findMethod("__repr__") { return pyStr(m.f([PV.obj(self)], [:])) }
        return "<" + cls.name + " object>"
    }
}

func pySuper(_ cls: PV, _ me: PV, _ name: String, _ a: [PV], _ k: [String: PV]) -> PV {
    if case .obj(let o) = cls, let c = o as? PClass, let b = c.base, let m = b.findMethod(name) {
        return m.f([me] + a, k)
    }
    if name == "__init__" { return PV.null }
    return pyFail("AttributeError", "super has no attribute '" + name + "'")
}

// ---------------------------------------------------------------- built-in functions
func pyToInt(_ v: PV) -> PV {
    switch v {
    case .int: return v
    case .bool(let b): return PV.int(b ? 1 : 0)
    case .double(let d):
        if d.isNaN || d.isInfinite { return pyFail("ValueError", "cannot convert float to integer") }
        return PV.int(Int(d))
    case .str(let s):
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        if let i = Int(t) { return PV.int(i) }
        return pyFail("ValueError", "invalid literal for int() with base 10: " + pyReprStr(s))
    default: return pyFail("TypeError", "int() argument must be a string or a number")
    }
}

func pyToFloat(_ v: PV) -> PV {
    switch v {
    case .double: return v
    case .int(let i): return PV.double(Double(i))
    case .bool(let b): return PV.double(b ? 1 : 0)
    case .str(let s):
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        if let d = Double(t) { return PV.double(d) }
        return pyFail("ValueError", "could not convert string to float: " + pyReprStr(s))
    default: return pyFail("TypeError", "float() argument must be a string or a number")
    }
}

func pyRound(_ x: PV, _ nd: PV) -> PV {
    guard let d = x.num else { return pyFail("TypeError", "type doesn't define __round__") }
    if let n = nd.intLike {
        let m = pow(10.0, Double(n))
        let r = (d * m).rounded(.toNearestOrEven) / m
        if case .int = x { return x }
        return PV.double(r)
    }
    return PV.int(Int(d.rounded(.toNearestOrEven)))
}

let b_print = PV.fn(PFn { a, k in
    var sep = " "
    var end = "\n"
    if let s = k["sep"], case .str(let x) = s { sep = x }
    if let e = k["end"], case .str(let x) = e { end = x }
    let line = a.map { pyStr($0) }.joined(separator: sep)
    FileHandle.standardOutput.write((line + end).data(using: .utf8) ?? Data())
    return PV.null
})
let b_len = PV.fn(PFn { a, k in PV.int(pyLen(a.first ?? PV.null)) })
let b_str = PV.fn(PFn { a, k in PV.str(a.isEmpty ? "" : pyStr(a[0])) })
let b_repr = PV.fn(PFn { a, k in PV.str(pyRepr(a.first ?? PV.null)) })
let b_int = PV.fn(PFn { a, k in a.isEmpty ? PV.int(0) : pyToInt(a[0]) })
let b_float = PV.fn(PFn { a, k in a.isEmpty ? PV.double(0) : pyToFloat(a[0]) })
let b_bool = PV.fn(PFn { a, k in PV.bool((a.first ?? PV.null).truthy) })
let b_abs = PV.fn(PFn { a, k in
    let v = a.first ?? PV.null
    if let i = v.intLike { return PV.int(abs(i)) }
    if let d = v.num { return PV.double(abs(d)) }
    return pyFail("TypeError", "bad operand type for abs()")
})
let b_range = PV.fn(PFn { a, k in
    var lo = 0, hi = 0, st = 1
    if a.count == 1 { hi = a[0].intLike ?? 0 }
    else if a.count >= 2 { lo = a[0].intLike ?? 0; hi = a[1].intLike ?? 0; if a.count > 2 { st = a[2].intLike ?? 1 } }
    if st == 0 { return pyFail("ValueError", "range() arg 3 must not be zero") }
    var r: [PV] = []
    var i = lo
    while (st > 0 && i < hi) || (st < 0 && i > hi) { r.append(PV.int(i)); i += st }
    return PV.list(PList(r))
})
func pyMinMax(_ a: [PV], _ k: [String: PV], _ wantMax: Bool) -> PV {
    var items = a
    if a.count == 1 { items = pyIter(a[0]) }
    if items.isEmpty { return pyFail("ValueError", "min()/max() arg is an empty sequence") }
    var best = items[0]
    let key = k["key"]
    func kv(_ v: PV) -> PV { if let kf = key { return pyCall(kf, [v], [:]) }; return v }
    for x in items.dropFirst() {
        guard let c = pyCmp(kv(x), kv(best)) else { return pyFail("TypeError", "cannot compare values") }
        if (wantMax && c > 0) || (!wantMax && c < 0) { best = x }
    }
    return best
}
let b_min = PV.fn(PFn { a, k in pyMinMax(a, k, false) })
let b_max = PV.fn(PFn { a, k in pyMinMax(a, k, true) })
let b_sum = PV.fn(PFn { a, k in
    var total: PV = a.count > 1 ? a[1] : PV.int(0)
    for x in pyIter(a.first ?? PV.null) { total = pyAdd(total, x) }
    return total
})
let b_sorted = PV.fn(PFn { a, k in
    PV.list(PList(pySortValues(pyIter(a.first ?? PV.null), k["key"], (k["reverse"] ?? PV.null).truthy)))
})
let b_reversed = PV.fn(PFn { a, k in PV.list(PList(pyIter(a.first ?? PV.null).reversed())) })
let b_enumerate = PV.fn(PFn { a, k in
    var start = 0
    if a.count > 1 { start = a[1].intLike ?? 0 }
    var r: [PV] = []
    for (i, x) in pyIter(a.first ?? PV.null).enumerated() { r.append(PV.tuple([PV.int(i + start), x])) }
    return PV.list(PList(r))
})
let b_zip = PV.fn(PFn { a, k in
    let seqs = a.map { pyIter($0) }
    let n = seqs.map { $0.count }.min() ?? 0
    var r: [PV] = []
    for i in 0..<n { r.append(PV.tuple(seqs.map { $0[i] })) }
    return PV.list(PList(r))
})
let b_list = PV.fn(PFn { a, k in PV.list(PList(a.isEmpty ? [] : pyIter(a[0]))) })
let b_tuple = PV.fn(PFn { a, k in PV.tuple(a.isEmpty ? [] : pyIter(a[0])) })
let b_dict = PV.fn(PFn { a, k in
    let d = PDict()
    if let f = a.first {
        if case .dict(let o) = f { for h in o.order { d.set(o.keys[h] ?? PV.null, o.vals[h] ?? PV.null) } }
        else { for p in pyIter(f) { let kv = pyIter(p); if kv.count == 2 { d.set(kv[0], kv[1]) } } }
    }
    for (key, v) in k { d.set(PV.str(key), v) }
    return PV.dict(d)
})
let b_round = PV.fn(PFn { a, k in pyRound(a.first ?? PV.null, a.count > 1 ? a[1] : PV.null) })
let b_chr = PV.fn(PFn { a, k in
    if let i = (a.first ?? PV.null).intLike, let u = Unicode.Scalar(i) { return PV.str(String(Character(u))) }
    return pyFail("ValueError", "chr() arg not in range")
})
let b_ord = PV.fn(PFn { a, k in
    if case .str(let s) = (a.first ?? PV.null), let u = s.unicodeScalars.first { return PV.int(Int(u.value)) }
    return pyFail("TypeError", "ord() expected a character")
})
let b_any = PV.fn(PFn { a, k in PV.bool(pyIter(a.first ?? PV.null).contains(where: { $0.truthy })) })
let b_all = PV.fn(PFn { a, k in PV.bool(pyIter(a.first ?? PV.null).allSatisfy({ $0.truthy })) })

// math and random
let b_math_pi = PV.double(Double.pi)
let b_math_e = PV.double(M_E)
func pyMath1(_ f: @escaping (Double) -> Double) -> PV {
    return PV.fn(PFn { a, k in PV.double(f((a.first ?? PV.null).num ?? 0)) })
}
let b_math_sqrt = PV.fn(PFn { a, k in
    let x = (a.first ?? PV.null).num ?? 0
    if x < 0 { return pyFail("ValueError", "math domain error") }
    return PV.double(x.squareRoot())
})
let b_math_sin = pyMath1(sin)
let b_math_cos = pyMath1(cos)
let b_math_tan = pyMath1(tan)
let b_math_fabs = pyMath1({ abs($0) })
let b_math_log = PV.fn(PFn { a, k in
    let x = (a.first ?? PV.null).num ?? 0
    if x <= 0 { return pyFail("ValueError", "math domain error") }
    if a.count > 1, let b = a[1].num { return PV.double(log(x) / log(b)) }
    return PV.double(log(x))
})
let b_math_floor = PV.fn(PFn { a, k in PV.int(Int(((a.first ?? PV.null).num ?? 0).rounded(.down))) })
let b_math_ceil = PV.fn(PFn { a, k in PV.int(Int(((a.first ?? PV.null).num ?? 0).rounded(.up))) })
let b_math_pow = PV.fn(PFn { a, k in PV.double(pow((a.first ?? PV.null).num ?? 0, (a.count > 1 ? a[1] : PV.null).num ?? 0)) })
let b_random_random = PV.fn(PFn { a, k in PV.double(Double.random(in: 0..<1)) })
let b_random_randint = PV.fn(PFn { a, k in
    let lo = (a.first ?? PV.null).intLike ?? 0
    let hi = (a.count > 1 ? a[1] : PV.null).intLike ?? lo
    if hi < lo { return pyFail("ValueError", "empty range for randint()") }
    return PV.int(Int.random(in: lo...hi))
})
let b_random_choice = PV.fn(PFn { a, k in
    let items = pyIter(a.first ?? PV.null)
    if items.isEmpty { return pyFail("IndexError", "Cannot choose from an empty sequence") }
    return items[Int.random(in: 0..<items.count)]
})
let b_time_time = PV.fn(PFn { a, k in PV.double(Date().timeIntervalSince1970) })

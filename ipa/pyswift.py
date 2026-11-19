"""PocketTk --ipa: translate a Python/tkinter script into Swift (Script.swift) for the iOS runtime in ipa/runtime.

Supported: functions (defaults, *args, **kwargs, nested, lambda), classes (methods, attributes, single inheritance
between your own classes, super()), if/while/for/break/continue, comprehensions, f-strings, str/list/dict methods,
math/random, and tkinter: Tk, Frame, Label, Button, Entry, Checkbutton, StringVar/IntVar/DoubleVar/BooleanVar,
pack/grid, after, messagebox.showinfo/showwarning/showerror.
Anything else stops with a message that has the Python line number.
"""
import ast

TK_NAMES = {
    "Tk", "Frame", "LabelFrame", "Label", "Button", "Entry", "Checkbutton",
    "StringVar", "IntVar", "DoubleVar", "BooleanVar",
    "END", "LEFT", "RIGHT", "TOP", "BOTTOM", "X", "Y", "BOTH", "CENTER", "W", "E", "N", "S", "NSEW", "EW", "NS",
    "NORMAL", "DISABLED", "WORD", "YES", "NO", "TRUE", "FALSE",
}
MSGBOX = {"showinfo", "showwarning", "showerror"}
MATH = {"pi", "e", "sqrt", "sin", "cos", "tan", "fabs", "log", "floor", "ceil", "pow"}
RANDOM = {"random", "randint", "choice"}
TIME = {"time"}
BUILTINS = {"print", "len", "str", "repr", "int", "float", "bool", "abs", "range", "min", "max", "sum", "sorted",
            "reversed", "enumerate", "zip", "list", "tuple", "dict", "round", "chr", "ord", "any", "all"}
MODULES = {"tkinter": "tk", "tkinter.ttk": "tk", "tkinter.messagebox": "msgbox", "ttk": "tk", "messagebox": "msgbox",
           "math": "math", "random": "random", "time": "time"}


class GenError(Exception):
    def __init__(self, errors):
        Exception.__init__(self, "\n".join(errors))
        self.errors = errors


def sw_str(s):
    out = ['"']
    for ch in s:
        o = ord(ch)
        if ch == "\\":
            out.append("\\\\")
        elif ch == '"':
            out.append('\\"')
        elif ch == "\n":
            out.append("\\n")
        elif ch == "\r":
            out.append("\\r")
        elif ch == "\t":
            out.append("\\t")
        elif o < 32 or o == 127:
            out.append("\\u{%x}" % o)
        else:
            out.append(ch)
    out.append('"')
    return "".join(out)


def ident(name):
    return "v_" + name


class Scope:
    def __init__(self, kind, locals_=None, globals_=None):
        self.kind = kind              # "module" | "function"
        self.locals = locals_ or set()
        self.globals = globals_ or set()


def assigned_names(body):
    """Names a function body binds (not looking inside nested function, class or lambda bodies)."""
    names, glob = set(), set()

    def target(t):
        if isinstance(t, ast.Name):
            names.add(t.id)
        elif isinstance(t, (ast.Tuple, ast.List)):
            for e in t.elts:
                target(e)

    def walk(n):
        if isinstance(n, (ast.FunctionDef, ast.ClassDef)):
            names.add(n.name)
            return
        if isinstance(n, ast.Lambda):
            return
        if isinstance(n, (ast.ListComp, ast.DictComp, ast.SetComp, ast.GeneratorExp)):
            return
        if isinstance(n, ast.Global):
            glob.update(n.names)
        elif isinstance(n, ast.Assign):
            for t in n.targets:
                target(t)
        elif isinstance(n, (ast.AugAssign, ast.AnnAssign)):
            target(n.target)
        elif isinstance(n, ast.For):
            target(n.target)
        for c in ast.iter_child_nodes(n):
            walk(c)

    for st in body:
        walk(st)
    return names - glob, glob


class Gen:
    def __init__(self, src, filename="app.py"):
        self.file = filename
        self.errors = []
        self.tmp = 0
        try:
            self.tree = ast.parse(src, filename)
        except SyntaxError as e:
            raise GenError(["%s:%s: syntax error: %s" % (filename, e.lineno, e.msg)])
        self.mod_alias = {}           # local name -> module kind (tk, msgbox, math, random, time)
        self.name_alias = {}          # local name -> swift ident (from tkinter import Label as L)
        self.star_tk = False
        self.module_names = set()
        self.scopes = []
        self.cls_stack = []           # (swift class ident, self param name)

    # ------------------------------------------------------------ helpers
    def err(self, node, msg):
        self.errors.append("%s:%s: %s" % (self.file, getattr(node, "lineno", 0), msg))

    def new(self, p="_t"):
        self.tmp += 1
        return "%s%d" % (p, self.tmp)

    def resolve(self, node, name):
        if name == "__name__" and name not in self.module_names:
            return 'PV.str("__main__")'
        for sc in reversed(self.scopes):
            if sc.kind == "function" and name in sc.locals:
                return ident(name)
            if sc.kind == "function" and name in sc.globals:
                break
        if name in self.module_names:
            return ident(name)
        if name in self.name_alias:
            return self.name_alias[name]
        if name in self.mod_alias:
            self.err(node, "module '%s' can only be used as %s.name" % (name, name))
            return "PV.null"
        if self.star_tk and name in TK_NAMES:
            return "b_" + name
        if name in BUILTINS:
            return "b_" + name
        self.err(node, "unknown name '%s' (not defined, and not a supported built-in)" % name)
        return "PV.null"

    def mod_attr(self, node, kind, attr):
        sets = {"tk": TK_NAMES, "msgbox": MSGBOX, "math": MATH, "random": RANDOM, "time": TIME}
        if attr not in sets[kind]:
            self.err(node, "%s.%s is not supported on iOS yet" % (kind if kind not in ("tk",) else "tkinter", attr))
            return "PV.null"
        if kind in ("tk", "msgbox"):
            return "b_" + attr
        return "b_%s_%s" % (kind, attr)

    def module_of(self, node):
        if isinstance(node, ast.Name) and node.id in self.mod_alias and node.id not in self.module_names:
            return self.mod_alias[node.id]
        if isinstance(node, ast.Attribute) and isinstance(node.value, ast.Name):
            full = node.value.id + "." + node.attr
            if node.value.id in self.mod_alias and full in ("tkinter.messagebox", "tkinter.ttk"):
                return MODULES[full]
        return None

    # ------------------------------------------------------------ expressions
    def args_of(self, call):
        pos = []
        for a in call.args:
            if isinstance(a, ast.Starred):
                self.err(a, "*args in a call is not supported yet")
                continue
            pos.append(self.expr(a))
        kws = []
        for k in call.keywords:
            if k.arg is None:
                self.err(call, "**kwargs in a call is not supported yet")
                continue
            kws.append("%s: %s" % (sw_str(k.arg), self.expr(k.value)))
        return "[" + ", ".join(pos) + "]", ("[" + ", ".join(kws) + "]" if kws else "[:]")

    def expr(self, n):
        m = getattr(self, "e_" + type(n).__name__, None)
        if m is None:
            self.err(n, "%s is not supported yet" % type(n).__name__)
            return "PV.null"
        return m(n)

    def e_Constant(self, n):
        v = n.value
        if v is None:
            return "PV.null"
        if isinstance(v, bool):
            return "PV.bool(%s)" % ("true" if v else "false")
        if isinstance(v, int):
            if abs(v) >= 2 ** 63:
                self.err(n, "integer too large for iOS (64-bit only)")
                return "PV.int(0)"
            return "PV.int(%d)" % v
        if isinstance(v, float):
            r = repr(v)
            if r in ("inf", "-inf", "nan"):
                return {"inf": "PV.double(Double.infinity)", "-inf": "PV.double(-Double.infinity)", "nan": "PV.double(Double.nan)"}[r]
            return "PV.double(%s)" % r
        if isinstance(v, str):
            return "PV.str(%s)" % sw_str(v)
        self.err(n, "this kind of constant is not supported")
        return "PV.null"

    def e_Name(self, n):
        return self.resolve(n, n.id)

    def e_BinOp(self, n):
        ops = {ast.Add: "pyAdd", ast.Sub: "pySub", ast.Mult: "pyMul", ast.Div: "pyDiv", ast.FloorDiv: "pyFloorDiv",
               ast.Mod: "pyMod", ast.Pow: "pyPow"}
        f = ops.get(type(n.op))
        if not f:
            self.err(n, "operator %s is not supported yet" % type(n.op).__name__)
            return "PV.null"
        return "%s(%s, %s)" % (f, self.expr(n.left), self.expr(n.right))

    def e_UnaryOp(self, n):
        if isinstance(n.op, ast.USub):
            return "pyNeg(%s)" % self.expr(n.operand)
        if isinstance(n.op, ast.UAdd):
            return self.expr(n.operand)
        if isinstance(n.op, ast.Not):
            return "PV.bool(!pyTruthy(%s))" % self.expr(n.operand)
        self.err(n, "operator %s is not supported yet" % type(n.op).__name__)
        return "PV.null"

    def e_BoolOp(self, n):
        f = "pyAnd" if isinstance(n.op, ast.And) else "pyOr"
        out = self.expr(n.values[-1])
        for v in reversed(n.values[:-1]):
            out = "%s(%s, %s)" % (f, self.expr(v), out)
        return out

    def cmp1(self, op, a, b, node):
        if isinstance(op, ast.Eq): return "pyEq(%s, %s)" % (a, b)
        if isinstance(op, ast.NotEq): return "!pyEq(%s, %s)" % (a, b)
        if isinstance(op, ast.Lt): return "pyLt(%s, %s)" % (a, b)
        if isinstance(op, ast.LtE): return "pyLe(%s, %s)" % (a, b)
        if isinstance(op, ast.Gt): return "pyGt(%s, %s)" % (a, b)
        if isinstance(op, ast.GtE): return "pyGe(%s, %s)" % (a, b)
        if isinstance(op, ast.Is): return "pyIs(%s, %s)" % (a, b)
        if isinstance(op, ast.IsNot): return "!pyIs(%s, %s)" % (a, b)
        if isinstance(op, ast.In): return "pyContains(%s, %s)" % (b, a)
        if isinstance(op, ast.NotIn): return "!pyContains(%s, %s)" % (b, a)
        self.err(node, "comparison not supported")
        return "false"

    def e_Compare(self, n):
        parts = []
        left = self.expr(n.left)
        for op, c in zip(n.ops, n.comparators):
            right = self.expr(c)
            parts.append("(" + self.cmp1(op, left, right, n) + ")")
            left = right
        return "PV.bool(%s)" % " && ".join(parts)

    def e_IfExp(self, n):
        return "(pyTruthy(%s) ? %s : %s)" % (self.expr(n.test), self.expr(n.body), self.expr(n.orelse))

    def e_Call(self, n):
        f = n.func
        # super().method(...)
        if isinstance(f, ast.Attribute) and isinstance(f.value, ast.Call) and isinstance(f.value.func, ast.Name) \
                and f.value.func.id == "super":
            if not self.cls_stack:
                self.err(n, "super() outside a class")
                return "PV.null"
            cls, me = self.cls_stack[-1]
            a, k = self.args_of(n)
            return "pySuper(%s, %s, %s, %s, %s)" % (cls, ident(me), sw_str(f.attr), a, k)
        kind = self.module_of(f.value) if isinstance(f, ast.Attribute) else None
        if isinstance(f, ast.Attribute) and kind:
            fn = self.mod_attr(n, kind, f.attr)
            a, k = self.args_of(n)
            return "pyCall(%s, %s, %s)" % (fn, a, k)
        a, k = self.args_of(n)
        if isinstance(f, ast.Attribute):
            return "pyMethod(%s, %s, %s, %s)" % (self.expr(f.value), sw_str(f.attr), a, k)
        return "pyCall(%s, %s, %s)" % (self.expr(f), a, k)

    def e_Attribute(self, n):
        kind = self.module_of(n.value)
        if kind:
            return self.mod_attr(n, kind, n.attr)
        return "pyAttr(%s, %s)" % (self.expr(n.value), sw_str(n.attr))

    def e_Subscript(self, n):
        sl = n.slice
        if hasattr(ast, "Index") and isinstance(sl, getattr(ast, "Index")):
            sl = sl.value
        if isinstance(sl, ast.Slice):
            g = lambda x: self.expr(x) if x is not None else "PV.null"
            return "pySlice(%s, %s, %s, %s)" % (self.expr(n.value), g(sl.lower), g(sl.upper), g(sl.step))
        return "pyGetItem(%s, %s)" % (self.expr(n.value), self.expr(sl))

    def e_List(self, n):
        return "PV.list(PList([%s]))" % ", ".join(self.expr(e) for e in n.elts)

    def e_Tuple(self, n):
        return "PV.tuple([%s])" % ", ".join(self.expr(e) for e in n.elts)

    def e_Dict(self, n):
        items = []
        for k, v in zip(n.keys, n.values):
            if k is None:
                self.err(n, "{**x} is not supported yet")
                continue
            items += [self.expr(k), self.expr(v)]
        return "pyMakeDict([%s])" % ", ".join(items)

    def e_JoinedStr(self, n):
        parts = []
        for v in n.values:
            if isinstance(v, ast.Constant):
                parts.append(sw_str(str(v.value)))
            elif isinstance(v, ast.FormattedValue):
                spec = ""
                if v.format_spec is not None:
                    if all(isinstance(x, ast.Constant) for x in v.format_spec.values):
                        spec = "".join(str(x.value) for x in v.format_spec.values)
                    else:
                        self.err(n, "nested format specs in f-strings are not supported yet")
                val = self.expr(v.value)
                if v.conversion == 114:      # !r
                    val = "PV.str(pyRepr(%s))" % val
                parts.append("pyFmt(%s, %s)" % (val, sw_str(spec)))
            else:
                self.err(n, "unsupported f-string part")
        return "PV.str(%s)" % (" + ".join(parts) if parts else '""')

    def target_decl_and_assign(self, t, src, lines, node):
        if isinstance(t, ast.Name):
            lines.append("%s = %s" % (self.resolve(t, t.id), src))
        elif isinstance(t, (ast.Tuple, ast.List)):
            tmp = self.new()
            lines.append("let %s = %s" % (tmp, src))
            for i, e in enumerate(t.elts):
                if isinstance(e, ast.Starred):
                    self.err(node, "starred assignment is not supported yet")
                    continue
                self.target_decl_and_assign(e, "pyItem(%s, %d)" % (tmp, i), lines, node)
        elif isinstance(t, ast.Subscript):
            sl = t.slice.value if hasattr(ast, "Index") and isinstance(t.slice, getattr(ast, "Index")) else t.slice
            if isinstance(sl, ast.Slice):
                self.err(node, "slice assignment is not supported yet")
            else:
                lines.append("pySetItem(%s, %s, %s)" % (self.expr(t.value), self.expr(sl), src))
        elif isinstance(t, ast.Attribute):
            lines.append("pySetAttr(%s, %s, %s)" % (self.expr(t.value), sw_str(t.attr), src))
        else:
            self.err(node, "unsupported assignment target")

    def comp(self, n, kind):
        r = self.new("_r")
        gens = n.generators
        names = set()

        def tnames(t):
            if isinstance(t, ast.Name):
                names.add(t.id)
            elif isinstance(t, (ast.Tuple, ast.List)):
                for e in t.elts:
                    tnames(e)
        for g in gens:
            if g.is_async:
                self.err(n, "async comprehensions are not supported")
            tnames(g.target)
        sc = Scope("function", set(names), set())
        self.scopes.append(sc)
        decl = "".join("var %s: PV = PV.null; " % ident(x) for x in sorted(names))
        inner = []
        if kind == "dict":
            kk, vv = self.expr(n.key), self.expr(n.value)
            body = "%s.set(%s, %s)" % (r, kk, vv)
        else:
            body = "%s.append(%s)" % (r, self.expr(n.elt))
        # build nested loops from the inside out
        code = body
        for g in reversed(gens):
            lines = []
            it = self.new("_i")
            self.target_decl_and_assign(g.target, it, lines, n)
            conds = "".join("if !pyTruthy(%s) { continue }; " % self.expr(c) for c in g.ifs)
            code = "for %s in pyIter(%s) { %s; %s%s }" % (it, self.expr(g.iter), "; ".join(lines), conds, code)
        self.scopes.pop()
        if kind == "dict":
            return "({ () -> PV in let %s = PDict(); %s%s; return PV.dict(%s) })()" % (r, decl, code, r)
        return "({ () -> PV in var %s: [PV] = []; %s%s; return PV.list(PList(%s)) })()" % (r, decl, code, r)

    def e_ListComp(self, n):
        return self.comp(n, "list")

    def e_GeneratorExp(self, n):
        return self.comp(n, "list")

    def e_DictComp(self, n):
        return self.comp(n, "dict")

    def e_Lambda(self, n):
        return self.function_value(n, n.args, [ast.Return(value=n.body, lineno=n.lineno)], "lambda")

    # ------------------------------------------------------------ functions
    def function_value(self, node, args, body, name):
        return "PV.fn(" + self.function_core(node, args, body, name) + ")"

    def function_core(self, node, args, body, name):
        if args.kwonlyargs or args.posonlyargs:
            self.err(node, "keyword-only and positional-only parameters are not supported yet")
        params = [a.arg for a in args.args]
        defaults = [None] * (len(params) - len(args.defaults)) + list(args.defaults)
        loc, glob = assigned_names(body)
        loc -= set(params)
        if args.vararg:
            loc.discard(args.vararg.arg)
        if args.kwarg:
            loc.discard(args.kwarg.arg)
        allnames = set(params) | loc
        if args.vararg: allnames.add(args.vararg.arg)
        if args.kwarg: allnames.add(args.kwarg.arg)
        outer = set()
        for sc in self.scopes:
            if sc.kind == "function":
                outer |= sc.locals
        sc = Scope("function", allnames, glob)       # enclosing functions' names stay visible (closure capture)
        self.scopes.append(sc)
        out = ["PFn({ (a: [PV], k: [String: PV]) -> PV in"]
        for i, p in enumerate(params):
            d = "nil" if defaults[i] is None else "{ %s }" % self.expr(defaults[i])
            out.append("var %s: PV = pyArg(a, k, %d, %s, %s)" % (ident(p), i, sw_str(p), d))
        if args.vararg:
            out.append("var %s: PV = pyRest(a, %d)" % (ident(args.vararg.arg), len(params)))
        if args.kwarg:
            out.append("var %s: PV = pyKwRest(k, [%s])" % (ident(args.kwarg.arg), ", ".join(sw_str(p) for p in params)))
        for v in sorted(loc):
            out.append("var %s: PV = PV.null" % ident(v))
        out += self.stmts(body)
        out.append("return PV.null")
        out.append("})")
        self.scopes.pop()
        return "\n".join(out)

    # ------------------------------------------------------------ statements
    def stmts(self, body):
        lines = []
        for s in body:
            m = getattr(self, "s_" + type(s).__name__, None)
            if m is None:
                self.err(s, "'%s' statements are not supported yet" % type(s).__name__.lower())
                continue
            r = m(s)
            if r:
                lines += r
        return lines

    def s_Pass(self, n):
        return []

    def s_Global(self, n):
        return []

    def s_Nonlocal(self, n):
        self.err(n, "nonlocal is not supported yet")

    def s_Import(self, n):
        for a in n.names:
            if a.name not in MODULES and a.name.split(".")[0] not in MODULES:
                self.err(n, "module '%s' is not available on iOS yet (supported: tkinter, math, random, time)" % a.name)
        return []

    def s_ImportFrom(self, n):
        return []

    def s_Expr(self, n):
        if isinstance(n.value, ast.Constant) and isinstance(n.value.value, str):
            return []
        return ["_ = %s" % self.expr(n.value)]

    def s_Assign(self, n):
        lines = []
        src = self.expr(n.value)
        if len(n.targets) > 1:
            tmp = self.new()
            lines.append("let %s = %s" % (tmp, src))
            src = tmp
        for t in n.targets:
            self.target_decl_and_assign(t, src, lines, n)
        return lines

    def s_AnnAssign(self, n):
        if n.value is None:
            return []
        lines = []
        self.target_decl_and_assign(n.target, self.expr(n.value), lines, n)
        return lines

    def s_AugAssign(self, n):
        ops = {ast.Add: "pyAdd", ast.Sub: "pySub", ast.Mult: "pyMul", ast.Div: "pyDiv", ast.FloorDiv: "pyFloorDiv",
               ast.Mod: "pyMod", ast.Pow: "pyPow"}
        f = ops.get(type(n.op))
        if not f:
            self.err(n, "operator not supported yet")
            return []
        lines = []
        t = n.target
        if isinstance(t, ast.Name):
            cur = self.expr(ast.Name(id=t.id, ctx=ast.Load(), lineno=n.lineno))
        elif isinstance(t, ast.Subscript):
            cur = self.expr(ast.Subscript(value=t.value, slice=t.slice, ctx=ast.Load(), lineno=n.lineno))
        elif isinstance(t, ast.Attribute):
            cur = self.expr(ast.Attribute(value=t.value, attr=t.attr, ctx=ast.Load(), lineno=n.lineno))
        else:
            self.err(n, "unsupported assignment target")
            return []
        self.target_decl_and_assign(t, "%s(%s, %s)" % (f, cur, self.expr(n.value)), lines, n)
        return lines

    def s_Delete(self, n):
        lines = []
        for t in n.targets:
            if isinstance(t, ast.Subscript):
                sl = t.slice.value if hasattr(ast, "Index") and isinstance(t.slice, getattr(ast, "Index")) else t.slice
                lines.append("pyDelItem(%s, %s)" % (self.expr(t.value), self.expr(sl)))
            else:
                self.err(n, "del is only supported for items (del x[i])")
        return lines

    def s_If(self, n):
        out = ["if pyTruthy(%s) {" % self.expr(n.test)] + self.stmts(n.body)
        orelse = n.orelse
        while len(orelse) == 1 and isinstance(orelse[0], ast.If):
            n2 = orelse[0]
            out += ["} else if pyTruthy(%s) {" % self.expr(n2.test)] + self.stmts(n2.body)
            orelse = n2.orelse
        if orelse:
            out += ["} else {"] + self.stmts(orelse)
        out.append("}")
        return out

    def s_While(self, n):
        if n.orelse:
            self.err(n, "while/else is not supported yet")
        return ["while pyTruthy(%s) {" % self.expr(n.test)] + self.stmts(n.body) + ["}"]

    def s_For(self, n):
        if n.orelse:
            self.err(n, "for/else is not supported yet")
        it = self.new("_i")
        lines = []
        self.target_decl_and_assign(n.target, it, lines, n)
        return ["for %s in pyIter(%s) {" % (it, self.expr(n.iter))] + lines + self.stmts(n.body) + ["}"]

    def s_Break(self, n):
        return ["break"]

    def s_Continue(self, n):
        return ["continue"]

    def s_Return(self, n):
        if not any(sc.kind == "function" for sc in self.scopes):
            self.err(n, "return outside a function")
            return []
        return ["return %s" % (self.expr(n.value) if n.value is not None else "PV.null")]

    def s_FunctionDef(self, n):
        if n.decorator_list:
            self.err(n, "decorators are not supported yet")
        return ["%s = %s" % (ident(n.name), self.function_value(n, n.args, n.body, n.name))]

    def s_ClassDef(self, n):
        if len(self.scopes) > 1 or self.scopes[-1].kind != "module":
            self.err(n, "classes must be defined at the top level of the file")
            return []
        if n.keywords or n.decorator_list:
            self.err(n, "class decorators and keywords are not supported yet")
        base = "PV.null"
        if len(n.bases) > 1:
            self.err(n, "multiple inheritance is not supported yet")
        elif n.bases:
            b = n.bases[0]
            if isinstance(b, ast.Name) and b.id == "object":
                pass
            elif isinstance(b, ast.Name) and b.id in self.module_names:
                base = ident(b.id)
            else:
                self.err(n, "a class can only extend 'object' or another class in this file (not a tkinter class)")
        cname = ident(n.name)
        methods, attrs = [], []
        for st in n.body:
            if isinstance(st, ast.FunctionDef):
                if st.decorator_list:
                    self.err(st, "decorators (staticmethod, property, ...) are not supported yet")
                me = st.args.args[0].arg if st.args.args else "self"
                self.cls_stack.append((cname, me))
                fv = self.function_core(st, st.args, st.body, st.name)
                self.cls_stack.pop()
                methods.append("%s: %s" % (sw_str(st.name), fv))
            elif isinstance(st, ast.Assign) and all(isinstance(t, ast.Name) for t in st.targets):
                for t in st.targets:
                    attrs.append("%s: %s" % (sw_str(t.id), self.expr(st.value)))
            elif isinstance(st, ast.Pass) or (isinstance(st, ast.Expr) and isinstance(st.value, ast.Constant)):
                pass
            else:
                self.err(st, "only methods and simple attributes are supported inside a class")
        m = "[" + ", ".join(methods) + "]" if methods else "[:]"
        a = "[" + ", ".join(attrs) + "]" if attrs else "[:]"
        return ["%s = PV.obj(PClass(%s, %s, %s, %s))" % (cname, sw_str(n.name), base, m, a)]

    def s_Try(self, n):
        self.err(n, "try/except is not supported yet")

    def s_Raise(self, n):
        self.err(n, "raise is not supported yet")

    def s_With(self, n):
        self.err(n, "with is not supported yet")

    def s_Assert(self, n):
        self.err(n, "assert is not supported yet")

    # ------------------------------------------------------------ module
    def prepass(self):
        names = set()
        loc, _ = assigned_names(self.tree.body)
        names |= loc
        for st in self.tree.body:
            if isinstance(st, ast.Import):
                for a in st.names:
                    top = a.name.split(".")[0]
                    if a.name in MODULES:
                        self.mod_alias[a.asname or top] = MODULES[a.name]
                    elif top in MODULES:
                        self.mod_alias[a.asname or top] = MODULES[top]
                        if not a.asname and a.name == "tkinter.messagebox":
                            self.mod_alias[top] = "tk"
            elif isinstance(st, ast.ImportFrom):
                mod = st.module or ""
                if mod not in MODULES:
                    self.err(st, "module '%s' is not available on iOS yet" % mod)
                    continue
                kind = MODULES[mod]
                for a in st.names:
                    if a.name == "*":
                        if kind == "tk":
                            self.star_tk = True
                        else:
                            self.err(st, "'from %s import *' is not supported" % mod)
                    elif mod == "tkinter" and a.name in ("messagebox", "ttk"):
                        self.mod_alias[a.asname or a.name] = MODULES[a.name]
                    else:
                        self.name_alias[a.asname or a.name] = self.mod_attr(st, kind, a.name)
        for node in ast.walk(self.tree):          # a name only ever set through `global x` inside a function
            if isinstance(node, ast.Global):
                names.update(node.names)
        self.module_names = names

    def generate(self):
        self.prepass()
        self.scopes = [Scope("module")]
        body = self.stmts(self.tree.body)
        if self.errors:
            raise GenError(self.errors)
        out = ["// Generated by PocketTk from %s - do not edit." % self.file, "import Foundation", ""]
        for n in sorted(self.module_names):
            out.append("var %s: PV = PV.null" % ident(n))
        out += ["", "func pyScript() {"] + body + ["}", ""]
        return "\n".join(out)


def translate(src, filename="app.py"):
    return Gen(src, filename).generate()


if __name__ == "__main__":
    import sys
    p = sys.argv[1]
    try:
        print(translate(open(p, encoding="utf-8").read(), p))
    except GenError as e:
        print(e, file=sys.stderr)
        sys.exit(1)

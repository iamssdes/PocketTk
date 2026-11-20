package apple.rt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * ApkApple runtime extras: argument binding, json, files, os/sys/re/datetime/threading,
 * collections, events, tracebacks and #use: datasaving. Pure Java (no Android classes).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class Ext {
    private Ext() {}

    // ------------------------------------------------------------------ config

    public static File baseDir = new File(".");
    public static String scriptName = "script.py";
    public static int[] lineMap = new int[0];
    public static String[] source = new String[0];
    public interface Reporter { void report(Throwable t); }
    public static Reporter reporter;
    public interface CloseHook { void closed(String path); }
    public static CloseHook closeHook;

    public static void setLineMap(String csv) {
        String[] p = csv.split(",");
        lineMap = new int[p.length];
        for (int i = 0; i < p.length; i++) lineMap[i] = p[i].isEmpty() ? 0 : Integer.parseInt(p[i]);
    }

    // ------------------------------------------------------------- arguments

    static final class Star { final Object v; Star(Object v) { this.v = v; } }
    static final class DStar { final Object v; DStar(Object v) { this.v = v; } }
    public static Object star(Object v) { return new Star(v); }
    public static Object dstar(Object v) { return new DStar(v); }

    /** Expands *x and **d markers into a flat argument array (Kw objects for keywords). */
    public static Object[] args(Object... parts) {
        List<Object> r = new ArrayList<Object>();
        for (Object p : parts) {
            if (p instanceof Star) { for (Object x : Py.iter(((Star) p).v)) r.add(x); }
            else if (p instanceof DStar) {
                Object d = ((DStar) p).v;
                if (!(d instanceof Map)) throw new Py.PyError("TypeError", "argument after ** must be a mapping, not " + Py.tn(d));
                for (Map.Entry e : ((Map<Object, Object>) d).entrySet()) r.add(Py.kw(Py.str(e.getKey()), e.getValue()));
            } else r.add(p);
        }
        return r.toArray();
    }

    /**
     * Bind call arguments to a function signature. names = positional params then keyword-only
     * params; defs has one entry per name (Py.MISSING = required). Result: values for names,
     * then the *args tuple (if va), then the **kwargs dict (if kwa).
     */
    public static Object[] bind(Object[] a, String[] names, int nPos, Object[] defs, boolean va, boolean kwa, String fn) {
        int n = names.length;
        Object[] out = new Object[n + (va ? 1 : 0) + (kwa ? 1 : 0)];
        boolean[] set = new boolean[n];
        List<Object> extra = new ArrayList<Object>();
        Map<Object, Object> kws = new LinkedHashMap<Object, Object>();
        int pos = 0;
        for (Object x : a) {
            if (x instanceof Py.Kw) {
                Py.Kw k = (Py.Kw) x;
                int idx = -1;
                for (int i = 0; i < n; i++) if (names[i].equals(k.k)) { idx = i; break; }
                if (idx >= 0) {
                    if (set[idx]) throw new Py.PyError("TypeError", fn + "() got multiple values for argument '" + k.k + "'");
                    out[idx] = k.v; set[idx] = true;
                } else if (kwa) kws.put(k.k, k.v);
                else throw new Py.PyError("TypeError", fn + "() got an unexpected keyword argument '" + k.k + "'");
            } else {
                if (pos < nPos) {
                    if (x == Py.MISSING) { pos++; continue; }
                    out[pos] = x; set[pos] = true; pos++;
                } else if (va) { extra.add(x); pos++; }
                else throw new Py.PyError("TypeError", fn + "() takes " + nPos + " positional argument" + (nPos == 1 ? "" : "s") + " but more were given");
            }
        }
        for (int i = 0; i < n; i++) {
            if (set[i]) continue;
            Object d = defs != null && i < defs.length ? defs[i] : Py.MISSING;
            if (d == Py.MISSING) throw new Py.PyError("TypeError", fn + "() missing required argument: '" + names[i] + "'");
            out[i] = d;
        }
        int o = n;
        if (va) out[o++] = new Py.Tuple(extra);
        if (kwa) out[o] = kws;
        return out;
    }

    /** Call a known user function with *args/**kwargs, turning keywords into positional slots. */
    public static Object callNamed(Fn f, String[] params, Object[] a) {
        List<Object> pos = new ArrayList<Object>();
        List<Object> rest = new ArrayList<Object>();
        Object[] slots = new Object[params.length];
        Arrays.fill(slots, Py.MISSING);
        int np = 0;
        for (Object x : a) {
            if (x instanceof Py.Kw) {
                Py.Kw k = (Py.Kw) x;
                int idx = Arrays.asList(params).indexOf(k.k);
                if (idx < 0) throw new Py.PyError("TypeError", "got an unexpected keyword argument '" + k.k + "'");
                slots[idx] = k.v;
            } else if (np < params.length) slots[np++] = x;
            else rest.add(x);
        }
        int last = params.length;
        while (last > 0 && slots[last - 1] == Py.MISSING) last--;
        for (int i = 0; i < last; i++) pos.add(slots[i]);
        pos.addAll(rest);
        return f.call(pos.toArray());
    }

    /** Normalizes a call that carries keywords for a plain (non-bind) function: keywords become positional slots. */
    public static Object[] normKw(Object[] a, String[] params) {
        List<Object> pos = new ArrayList<Object>();
        List<Object> rest = new ArrayList<Object>();
        Object[] slots = new Object[params.length];
        Arrays.fill(slots, Py.MISSING);
        int np = 0;
        for (Object x : a) {
            if (x instanceof Py.Kw) {
                Py.Kw k = (Py.Kw) x;
                int idx = Arrays.asList(params).indexOf(k.k);
                if (idx < 0) throw new Py.PyError("TypeError", "got an unexpected keyword argument '" + k.k + "'");
                if (slots[idx] != Py.MISSING) throw new Py.PyError("TypeError", "got multiple values for argument '" + k.k + "'");
                slots[idx] = k.v;
            } else if (np < params.length) slots[np++] = x;
            else rest.add(x);
        }
        int last = params.length;
        while (last > 0 && slots[last - 1] == Py.MISSING) last--;
        for (int i = 0; i < last; i++) pos.add(slots[i]);
        pos.addAll(rest);
        return pos.toArray();
    }

    public static Object mkDict(Object... kv) {
        Map<Object, Object> m = new LinkedHashMap<Object, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i] instanceof DStar) {
                Object d = ((DStar) kv[i]).v;
                if (!(d instanceof Map)) throw new Py.PyError("TypeError", "'" + Py.tn(d) + "' object is not a mapping");
                m.putAll((Map) d);
            } else m.put(Py.key(kv[i]), kv[i + 1]);
        }
        return m;
    }

    public static Object dedupe(Object[] xs) {
        List<Object> r = new ArrayList<Object>();
        for (Object x : xs) { boolean dup = false; for (Object y : r) if (Py.eq(x, y)) dup = true; if (!dup) r.add(x); }
        return r;
    }

    public static void delIdx(Object o, Object i) {
        if (o instanceof Oop.Inst) { Oop.delIdx((Oop.Inst) o, i); return; }
        if (o instanceof Map) {
            Object k = Py.key(i);
            if (!((Map) o).containsKey(k)) throw new Py.PyError("KeyError", Py.repr(i));
            ((Map) o).remove(k);
            return;
        }
        if (o instanceof List) {
            List l = (List) o;
            if (i instanceof Py.Sl) {
                Object keep = Py.idx(o, i);
                List drop = new ArrayList((List) keep);
                for (Object x : drop) { for (int k = 0; k < l.size(); k++) if (l.get(k) == x) { l.remove(k); break; } }
                return;
            }
            int k = Py.norm(Py.toLong(i), l.size());
            if (k < 0 || k >= l.size()) throw new Py.PyError("IndexError", "list assignment index out of range");
            l.remove(k);
            return;
        }
        throw new Py.PyError("TypeError", "'" + Py.tn(o) + "' object doesn't support item deletion");
    }

    // ------------------------------------------------------------ type checks

    static boolean isinst(Object x, Object t) {
        if (t instanceof Oop.Cls) return Oop.isinst(x, t);
        if (x instanceof Oop.Inst && t instanceof String && Oop.isinst(x, t)) return true;
        if (t instanceof List) { for (Object o : (List) t) if (isinst(x, o)) return true; return false; }
        String n = Py.str(t);
        switch (n) {
            case "int": return x instanceof Long || x instanceof Boolean;
            case "float": return x instanceof Double;
            case "bool": return x instanceof Boolean;
            case "str": return x instanceof String;
            case "tuple": return x instanceof Py.Tuple;
            case "list": return x instanceof List && !(x instanceof Py.Tuple);
            case "dict": return x instanceof Map;
            case "set": return x instanceof Set;
            case "NoneType": return x == null;
            case "bytes": return x instanceof Oop.Bytes && !((Oop.Bytes) x).mutable;
            case "bytearray": return x instanceof Oop.Bytes && ((Oop.Bytes) x).mutable;
            case "generator": return x instanceof Oop.Generator;
            case "function": case "callable": return x instanceof Fn;
            case "object": return true;
            case "complex": return false;
            default:
                if (x instanceof Py.PyError) return ((Py.PyError) x).matches(n);
                return x == Py.APP || (x != null && x.getClass().getSimpleName().equals(n));
        }
    }

    public static boolean typeIs(Object x, String n) {
        switch (n) {
            case "int": return x instanceof Long;
            case "list": return x instanceof List && !(x instanceof Py.Tuple);
            default: return isinst(x, n);
        }
    }

    public static final class Type implements Py.Obj {
        final String name;
        Type(String name) { this.name = name; }
        public Object callm(String n, Object[] a) {
            if (n.equals("@__name__")) return name;
            throw new Py.PyError("AttributeError", "type object has no attribute '" + n + "'");
        }
        public String toString() { return "<class '" + name + "'>"; }
    }

    static String typeName(Object o) { return Py.tn(o); }

    public static Object attr(Object o, String name) {
        if (o == Py.APP) o = Py.root;
        if (o instanceof Py.PyError) {
            if (name.equals("args")) return Py.tupleOf(((Py.PyError) o).getMessage());
            if (name.equals("msg") || name.equals("strerror")) return ((Py.PyError) o).getMessage();
        }
        if (!(o instanceof Oop.Inst) && !(o instanceof Oop.Cls)) {
            java.util.Map<String, Object> ex = Oop.extrasOf(o, false);
            if (ex != null && ex.containsKey(name)) return ex.get(name);
        }
        if (o instanceof Py.Obj) return ((Py.Obj) o).callm("@" + name, new Object[0]);
        if (o instanceof Double) {
            if (name.equals("real")) return o;
            if (name.equals("imag")) return 0.0;
        }
        if (o instanceof Long && name.equals("real")) return o;
        throw new Py.PyError("AttributeError", "'" + Py.tn(o) + "' object has no attribute '" + name + "'");
    }

    // ---------------------------------------------------------------- builtins

    public static Object bi(String name, Object[] a) {
        switch (name) {
            case "isinstance": return Boolean.valueOf(isinst(Py.arg(a, 0), Py.arg(a, 1)));
            case "type": { Object x = Py.arg(a, 0); return x instanceof Oop.Inst ? ((Oop.Inst) x).cls : new Type(Py.tn(x)); }
            case "bytes": return Oop.bytesCtor(false, a);
            case "bytearray": return Oop.bytesCtor(true, a);
            case "setattr": Oop.setattr(Py.arg(a, 0), Py.str(Py.arg(a, 1)), Py.arg(a, 2)); return null;
            case "delattr": Oop.delattr(Py.arg(a, 0), Py.str(Py.arg(a, 1))); return null;
            case "vars": { Object x = Py.arg(a, 0); return attr(x, "__dict__"); }
            case "issubclass": { Object c = Py.arg(a, 0), t = Py.arg(a, 1); return Boolean.valueOf(c instanceof Oop.Cls && (t instanceof Oop.Cls ? ((Oop.Cls) c).subclassOf((Oop.Cls) t) : Oop.isinst(new Oop.Inst((Oop.Cls) c), t))); }
            case "callable": { Object x = Py.arg(a, 0); return Boolean.valueOf(x instanceof Fn || (x instanceof Oop.Inst && ((Oop.Inst) x).dun("__call__") != null)); }
            case "open": return open(a);
            case "exit": case "quit": throw new Py.PyError("SystemExit", a.length > 0 ? Py.str(a[0]) : "");
            case "frozenset": return Py.bi("set", a);
            case "hasattr": {
                try { attr(a[0], Py.str(a[1])); return Boolean.TRUE; } catch (RuntimeException e) { return Boolean.FALSE; }
            }
            case "getattr": {
                try { return attr(a[0], Py.str(a[1])); } catch (RuntimeException e) { if (a.length > 2) return a[2]; throw e; }
            }
            case "iter": { Object x = Py.arg(a, 0); if (x instanceof java.util.Iterator) return x; if (x instanceof Oop.Inst && ((Oop.Inst) x).dun("__next__") != null) return x; return new ArrayList<Object>(Py.toList(x)); }
            case "next": {
                Object l = Py.arg(a, 0);
                if (l instanceof Oop.Generator || l instanceof Oop.Inst || l instanceof Oop.It) {
                    try { return l instanceof Oop.Generator ? ((Oop.Generator) l).nextValue() : Py.m(l, "__next__"); }
                    catch (Py.PyError e) { if (a.length > 1 && e.matches("StopIteration")) return a[1]; throw e; }
                }
                if (l instanceof List && !((List) l).isEmpty()) return ((List) l).remove(0);
                if (a.length > 1) return a[1];
                throw new Py.PyError("StopIteration", "");
            }
            case "id": return (long) System.identityHashCode(Py.arg(a, 0));
            case "hash": { Object x = Py.arg(a, 0); return x instanceof Oop.Inst || x instanceof Oop.Bytes ? (long) x.hashCode() : (long) String.valueOf(x).hashCode(); }
            case "complex": throw new Py.PyError("TypeError", "complex numbers are not supported");
            case "isqrt": return (long) Math.floor(Math.sqrt(Py.dbl(Py.arg(a, 0))));
            default: throw new Py.PyError("NameError", "name '" + name + "' is not defined");
        }
    }

    // -------------------------------------------------------------------- files

    static File resolve(String path) {
        File f = new File(path);
        return f.isAbsolute() ? f : new File(baseDir, path);
    }

    static Py.PyError osErr(IOException e, String path) {
        if (e instanceof java.io.FileNotFoundException) {
            File f = resolve(path);
            if (f.isDirectory()) return new Py.PyError("IsADirectoryError", "[Errno 21] Is a directory: " + Py.repr(path));
            if (f.exists() || (f.getParentFile() != null && f.getParentFile().exists())) return new Py.PyError("PermissionError", "[Errno 13] Permission denied: " + Py.repr(path));
            return new Py.PyError("FileNotFoundError", "[Errno 2] No such file or directory: " + Py.repr(path));
        }
        return new Py.PyError("OSError", String.valueOf(e.getMessage()));
    }

    static String readAll(File f, String path) { return readAll(f, path, false); }

    static String readAll(File f, String path, boolean bin) {
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), bin ? "ISO-8859-1" : "UTF-8");
        } catch (IOException e) {
            throw osErr(e, path);
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) { /* ignore */ }
        }
    }

    public static Object open(Object[] a) {
        String path = Py.str(Py.arg(a, 0));
        if (path.isEmpty()) throw new Py.PyError("FileNotFoundError", "[Errno 2] No such file or directory: ''");
        String mode = Py.str(Py.kwv(a, "mode", Py.arg(a, 1, "r")));
        return new PyFile(path, mode);
    }

    public static final class PyFile implements Py.Obj {
        final String path;
        final String mode;
        final boolean writing;
        String data = "";
        int pos = 0;
        StringBuilder out = new StringBuilder();
        boolean closed;
        boolean fresh = true;
        final boolean bin;

        PyFile(String path, String mode) {
            this.path = path;
            this.mode = mode;
            this.bin = mode.contains("b");
            File f = resolve(path);
            char m = mode.isEmpty() ? 'r' : mode.charAt(0);
            writing = m == 'w' || m == 'a' || m == 'x' || mode.contains("+");
            if (m == 'r') {
                if (!f.exists() && !mode.contains("+")) throw new Py.PyError("FileNotFoundError", "[Errno 2] No such file or directory: " + Py.repr(path));
                if (f.isDirectory()) throw new Py.PyError("IsADirectoryError", "[Errno 21] Is a directory: " + Py.repr(path));
                if (f.exists()) data = readAll(f, path, bin);
                if (mode.contains("+")) { out.append(data); }
            } else if (m == 'a') {
                if (f.exists()) data = readAll(f, path, bin);
            } else if (m == 'x' && f.exists()) {
                throw new Py.PyError("FileExistsError", "[Errno 17] File exists: " + Py.repr(path));
            }
            if (writing) {
                if (m == 'w' || m == 'x') flushTo(false);   // create/truncate now, like Python
                else if (m == 'a') out.append(data);
            }
        }

        void flushTo(boolean append) {
            File f = resolve(path);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            OutputStream os = null;
            try {
                os = new FileOutputStream(f, append);
                os.write(out.toString().getBytes(bin ? "ISO-8859-1" : "UTF-8"));
            } catch (IOException e) {
                throw osErr(e, path);
            } finally {
                if (os != null) try { os.close(); } catch (IOException ignored) { /* ignore */ }
            }
        }

        void check() { if (closed) throw new Py.PyError("ValueError", "I/O operation on closed file."); }

        List<Object> lines() {
            check();
            List<Object> r = new ArrayList<Object>();
            while (pos < data.length()) r.add(bin ? Oop.bytesLit(readline()) : readline());
            return r;
        }

        String readline() {
            if (pos >= data.length()) return "";
            int i = data.indexOf('\n', pos);
            int end = i < 0 ? data.length() : i + 1;
            String s = data.substring(pos, end);
            pos = end;
            return s;
        }

        void close() {
            if (closed) return;
            closed = true;
            if (writing) {
                flushTo(false);
                if (closeHook != null) closeHook.closed(resolve(path).getPath());
            }
        }

        public Object callm(String name, Object[] a) {
            switch (name) {
                case "read": {
                    check();
                    String s;
                    if (a.length > 0 && a[0] != null && Py.toLong(a[0]) >= 0) {
                        int e = (int) Math.min(data.length(), pos + Py.toLong(a[0]));
                        s = data.substring(pos, e); pos = e;
                    } else { s = data.substring(Math.min(pos, data.length())); pos = data.length(); }
                    return bin ? Oop.bytesLit(s) : s;
                }
                case "readline": check(); return bin ? Oop.bytesLit(readline()) : readline();
                case "readlines": return lines();
                case "write": {
                    check();
                    if (!writing) throw new Py.PyError("OSError", "not writable");
                    Object w0 = Py.arg(a, 0);
                    if (bin != (w0 instanceof Oop.Bytes)) throw new Py.PyError("TypeError", bin ? "a bytes-like object is required, not '" + Py.tn(w0) + "'" : "write() argument must be str, not " + Py.tn(w0));
                    String s = bin ? new String(((Oop.Bytes) w0).b, java.nio.charset.Charset.forName("ISO-8859-1")) : Py.str(w0);
                    out.append(s);
                    return (long) s.length();
                }
                case "writelines": for (Object x : Py.iter(Py.arg(a, 0))) callm("write", new Object[]{x}); return null;
                case "flush": if (writing && !closed) flushTo(false); return null;
                case "close": close(); return null;
                case "seek": pos = (int) Math.max(0, Math.min(data.length(), Py.toLong(Py.arg(a, 0)))); return (long) pos;
                case "tell": return (long) pos;
                case "readable": return Boolean.valueOf(!writing || mode.contains("+"));
                case "writable": return Boolean.valueOf(writing);
                case "@name": return path;
                case "@mode": return mode;
                case "@closed": return Boolean.valueOf(closed);
                case "__enter__": return this;
                case "__exit__": close(); return null;
                default: throw new Py.PyError("AttributeError", "'file' object has no attribute '" + name + "'");
            }
        }

        @Override public String toString() { return "<_io.TextIOWrapper name=" + Py.repr(path) + " mode=" + Py.repr(mode) + ">"; }
    }

    public static Object enter(Object o) {
        if (o instanceof Py.Obj && !(o instanceof Py.Var) && !(o == Py.root)) {
            try { Object r = ((Py.Obj) o).callm("__enter__", new Object[0]); return r == null ? o : r; }
            catch (Py.PyError e) { if (e.type.equals("AttributeError")) return o; throw e; }
        }
        return o;
    }

    public static void exit(Object o) {
        if (o instanceof Py.Obj && !(o instanceof Py.Var) && !(o == Py.root)) {
            try { ((Py.Obj) o).callm("__exit__", o instanceof Oop.Inst ? new Object[] { null, null, null } : new Object[0]); }
            catch (Py.PyError e) { if (!e.type.equals("AttributeError")) throw e; }
        }
    }

    // -------------------------------------------------------------------- json

    static final class JsonEnc {
        final String indent;
        final boolean sort, ascii;
        final String itemSep, keySep;
        final Object dflt;
        JsonEnc(Object[] a) {
            Object ind = Py.kwv(a, "indent", null);
            if (ind == null) indent = null;
            else if (ind instanceof String) indent = (String) ind;
            else { StringBuilder sb = new StringBuilder(); for (long i = 0; i < Py.toLong(ind); i++) sb.append(' '); indent = sb.toString(); }
            sort = Py.truth(Py.kwv(a, "sort_keys", Boolean.FALSE));
            ascii = Py.truth(Py.kwv(a, "ensure_ascii", Boolean.TRUE));
            Object sep = Py.kwv(a, "separators", null);
            if (sep instanceof List && ((List) sep).size() == 2) { itemSep = Py.str(((List) sep).get(0)); keySep = Py.str(((List) sep).get(1)); }
            else { itemSep = indent == null ? ", " : ","; keySep = ": "; }
            dflt = Py.kwv(a, "default", null);
        }
    }

    static void jstr(StringBuilder sb, String s, boolean ascii) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20 || (ascii && c > 0x7e)) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    static void enc(StringBuilder sb, Object o, JsonEnc e, int depth) {
        if (o instanceof Boolean) { sb.append(((Boolean) o) ? "true" : "false"); return; }
        o = Py.norm(o);
        if (o == null) { sb.append("null"); return; }
        if (o instanceof Long) { sb.append(o); return; }
        if (o instanceof Double) {
            double d = (Double) o;
            sb.append(Double.isNaN(d) ? "NaN" : Double.isInfinite(d) ? (d > 0 ? "Infinity" : "-Infinity") : Py.fmtDouble(d));
            return;
        }
        if (o instanceof String) { jstr(sb, (String) o, e.ascii); return; }
        String nl = e.indent == null ? "" : "\n" + rep(e.indent, depth + 1);
        String nlEnd = e.indent == null ? "" : "\n" + rep(e.indent, depth);
        if (o instanceof List) {
            List l = (List) o;
            if (l.isEmpty()) { sb.append("[]"); return; }
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(e.itemSep);
                sb.append(nl);
                enc(sb, l.get(i), e, depth + 1);
            }
            sb.append(nlEnd).append(']');
            return;
        }
        if (o instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) o;
            if (m.isEmpty()) { sb.append("{}"); return; }
            List<Object> keys = new ArrayList<Object>(m.keySet());
            if (e.sort) {
                List<String> ks = new ArrayList<String>();
                for (Object k : keys) ks.add(keyStr(k));
                Collections.sort(ks);
                keys = new ArrayList<Object>(ks);
                Map<Object, Object> m2 = new LinkedHashMap<Object, Object>();
                for (Object k : ks) for (Map.Entry<Object, Object> en : m.entrySet()) if (keyStr(en.getKey()).equals(k)) m2.put(k, en.getValue());
                m = m2;
            }
            sb.append('{');
            boolean first = true;
            for (Object k : keys) {
                if (!first) sb.append(e.itemSep);
                first = false;
                sb.append(nl);
                jstr(sb, keyStr(k), e.ascii);
                sb.append(e.keySep);
                enc(sb, m.get(k), e, depth + 1);
            }
            sb.append(nlEnd).append('}');
            return;
        }
        if (e.dflt != null) { enc(sb, Py.call(e.dflt, o), e, depth); return; }
        throw new Py.PyError("TypeError", "Object of type " + Py.tn(o) + " is not JSON serializable");
    }

    static String keyStr(Object k) {
        if (k instanceof Boolean) return ((Boolean) k) ? "true" : "false";
        k = Py.norm(k);
        if (k instanceof String) return (String) k;
        if (k == null) return "null";
        if (k instanceof Long || k instanceof Double) return Py.str(k);
        throw new Py.PyError("TypeError", "keys must be str, int, float, bool or None, not " + Py.tn(k));
    }

    static String rep(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    static String dumps(Object o, Object[] a) {
        StringBuilder sb = new StringBuilder();
        enc(sb, o, new JsonEnc(a), 0);
        return sb.toString();
    }

    static final class JsonParser {
        final String s;
        int i = 0;
        JsonParser(String s) { this.s = s; }

        Py.PyError fail(String msg, int at) {
            int line = 1, col = 1;
            for (int k = 0; k < at && k < s.length(); k++) { if (s.charAt(k) == '\n') { line++; col = 1; } else col++; }
            return new Py.PyError("JSONDecodeError", msg + ": line " + line + " column " + col + " (char " + at + ")");
        }

        void ws() { while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++; }

        Object parseTop() {
            ws();
            Object v = value();
            ws();
            if (i < s.length()) throw fail("Extra data", i);
            return v;
        }

        Object value() {
            if (i >= s.length()) throw fail("Expecting value", i);
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            if (s.startsWith("NaN", i)) { i += 3; return Double.NaN; }
            if (s.startsWith("Infinity", i)) { i += 8; return Double.POSITIVE_INFINITY; }
            if (s.startsWith("-Infinity", i)) { i += 9; return Double.NEGATIVE_INFINITY; }
            int st = i;
            if (c == '-') i++;
            int ds = i;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            if (i == ds) { i = st; throw fail("Expecting value", st); }
            boolean isF = false;
            if (i < s.length() && s.charAt(i) == '.' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1))) {
                isF = true; i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                int save = i;
                i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                int es = i;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                if (i == es) i = save; else isF = true;
            }
            String num = s.substring(st, i);
            if (isF) return Double.parseDouble(num);
            try { return Long.parseLong(num); } catch (NumberFormatException e) { return Double.parseDouble(num); }
        }

        String string() {
            int start = i;
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw fail("Unterminated string starting at", start);
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw fail("Unterminated string starting at", start);
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case '/': sb.append('/'); break;
                        case '\\': sb.append('\\'); break;
                        case '"': sb.append('"'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw fail("Invalid \\uXXXX escape", i - 1);
                            try { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); }
                            catch (NumberFormatException ex) { throw fail("Invalid \\uXXXX escape", i - 1); }
                            i += 4;
                            break;
                        default: throw fail("Invalid \\escape", i - 2);
                    }
                } else if (c < 0x20) throw fail("Invalid control character at", i - 1);
                else sb.append(c);
            }
        }

        Object array() {
            i++;
            List<Object> r = new ArrayList<Object>();
            ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return r; }
            while (true) {
                ws();
                r.add(value());
                ws();
                if (i >= s.length()) throw fail("Expecting ',' delimiter", i);
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; return r; }
                throw fail("Expecting ',' delimiter", i);
            }
        }

        Object object() {
            i++;
            Map<Object, Object> r = new LinkedHashMap<Object, Object>();
            ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return r; }
            while (true) {
                ws();
                if (i >= s.length() || s.charAt(i) != '"') throw fail("Expecting property name enclosed in double quotes", i);
                String k = string();
                ws();
                if (i >= s.length() || s.charAt(i) != ':') throw fail("Expecting ':' delimiter", i);
                i++;
                ws();
                r.put(k, value());
                ws();
                if (i >= s.length()) throw fail("Expecting ',' delimiter", i);
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; return r; }
                throw fail("Expecting ',' delimiter", i);
            }
        }
    }

    public static Object loads(String s) { return new JsonParser(s).parseTop(); }

    // ------------------------------------------------------------------ modules

    static String[] fnArgs(Object[] a) { return null; }

    public static Object modc(String module, String name) {
        { Object lr = Lib.modc(module, name); if (lr != Lib.NO) return lr; }
        switch (module) {
            case "sys":
                switch (name) {
                    case "argv": return Py.listOf(scriptName);
                    case "platform": return "linux";
                    case "version": return "3.11 (PocketTk)";
                    case "maxsize": return Long.MAX_VALUE;
                    case "stdout": case "stderr": return new StdOut(name.equals("stderr"));
                    case "path": return Py.listOf();
                }
                break;
            case "os":
                switch (name) {
                    case "sep": return "/";
                    case "linesep": return "\n";
                    case "name": return "posix";
                    case "curdir": return ".";
                    case "environ": return new LinkedHashMap<Object, Object>();
                    case "path.sep": return "/";
                }
                break;
            case "string":
                switch (name) {
                    case "ascii_lowercase": return "abcdefghijklmnopqrstuvwxyz";
                    case "ascii_uppercase": return "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
                    case "ascii_letters": return "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
                    case "digits": return "0123456789";
                    case "hexdigits": return "0123456789abcdefABCDEF";
                    case "punctuation": return "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
                    case "whitespace": return " \t\n\r\u000b\u000c";
                }
                break;
            case "re":
                switch (name) {
                    case "I": case "IGNORECASE": return 2L;
                    case "M": case "MULTILINE": return 8L;
                    case "S": case "DOTALL": return 16L;
                    case "X": case "VERBOSE": return 64L;
                }
                break;
            case "json":
                if (name.equals("JSONDecodeError")) return "JSONDecodeError";
                break;
        }
        throw new Py.PyError("AttributeError", "module '" + module + "' has no attribute '" + name + "'");
    }

    static final class StdOut implements Py.Obj {
        final boolean err;
        StdOut(boolean err) { this.err = err; }
        public Object callm(String n, Object[] a) {
            if (n.equals("write")) {
                String text = Py.str(Py.arg(a, 0));
                (err ? System.err : System.out).print(text);
                if (Py.sink != null) Py.sink.out(text, err);
                return null;
            }
            if (n.equals("flush")) return null;
            throw new Py.PyError("AttributeError", "stdout has no attribute '" + n + "'");
        }
    }

    public static Object mod(String module, String fn, Object[] a) {
        switch (module) {
            case "json": {
                switch (fn) {
                    case "dumps": return dumps(Py.arg(a, 0), a);
                    case "loads": {
                        Object s = Py.arg(a, 0);
                        if (!(s instanceof String)) throw new Py.PyError("TypeError", "the JSON object must be str, not " + Py.tn(s));
                        return loads((String) s);
                    }
                    case "dump": {
                        Object fp = Py.arg(a, 1);
                        Py.m(fp, "write", dumps(Py.arg(a, 0), a));
                        return null;
                    }
                    case "load": return loads(Py.str(Py.m(Py.arg(a, 0), "read")));
                }
                break;
            }
            case "sys":
                if (fn.equals("exit")) throw new Py.PyError("SystemExit", a.length > 0 && a[0] != null ? Py.str(a[0]) : "");
                break;
            case "os": return osMod(fn, a);
            case "termux": return Termux.call(fn, a);
            case "re": return reMod(fn, a);
            case "time": {
                switch (fn) {
                    case "strftime": return strftime(Py.str(Py.arg(a, 0)), a.length > 1 && a[1] instanceof DateTime ? ((DateTime) a[1]).cal() : Calendar.getInstance());
                    case "localtime": case "gmtime": return new DateTime(a.length > 0 && a[0] != null ? (long) (Py.dbl(a[0]) * 1000) : System.currentTimeMillis(), true);
                    case "ctime": return strftime("%a %b %d %H:%M:%S %Y", Calendar.getInstance());
                    case "process_time": return System.nanoTime() / 1e9;
                    case "time_ns": return System.currentTimeMillis() * 1000000L;
                }
                break;
            }
            case "datetime": return DateTime.mod(fn, a);
            case "threading": {
                switch (fn) {
                    case "Thread": return new PyThread(a);
                    case "Lock": case "RLock": return new Lock();
                    case "Event": return new Flag();
                    case "Timer": {
                        Object[] b = new Object[]{Py.kw("target", a[1]), Py.kw("delay", a[0])};
                        return new PyThread(b);
                    }
                    case "current_thread": return new PyThread(new Object[0]);
                    case "active_count": return 1L;
                }
                break;
            }
            case "collections": {
                switch (fn) {
                    case "defaultdict": {
                        DDict d = new DDict(a.length > 0 ? a[0] : null);
                        if (a.length > 1 && a[1] instanceof Map) d.putAll((Map) a[1]);
                        return d;
                    }
                    case "Counter": {
                        Counter c = new Counter();
                        if (a.length > 0 && a[0] instanceof Map) c.putAll((Map) a[0]);
                        else if (a.length > 0) for (Object x : Py.iter(a[0])) { Object k = Py.key(x); Object v = c.get(k); c.put(k, v == null ? 1L : (Long) v + 1); }
                        return c;
                    }
                    case "deque": {
                        Deque d = new Deque();
                        if (a.length > 0 && !(a[0] instanceof Py.Kw)) for (Object x : Py.iter(a[0])) d.add(x);
                        return d;
                    }
                    case "OrderedDict": { Map m = new LinkedHashMap(); if (a.length > 0 && a[0] instanceof Map) m.putAll((Map) a[0]); return m; }
                }
                break;
            }
            case "string": break;
            case "math": {
                switch (fn) {
                    case "isclose": {
                        double x = Py.dbl(a[0]), y = Py.dbl(a[1]);
                        double rt = Py.kwv(a, "rel_tol", null) != null ? Py.dbl(Py.kwv(a, "rel_tol", 1e-9)) : 1e-9;
                        double at = Py.kwv(a, "abs_tol", null) != null ? Py.dbl(Py.kwv(a, "abs_tol", 0.0)) : 0.0;
                        return Boolean.valueOf(Math.abs(x - y) <= Math.max(rt * Math.max(Math.abs(x), Math.abs(y)), at));
                    }
                    case "fsum": { double s = 0; for (Object x : Py.iter(a[0])) s += Py.dbl(x); return s; }
                    case "cosh": return Math.cosh(Py.dbl(a[0]));
                    case "sinh": return Math.sinh(Py.dbl(a[0]));
                    case "tanh": return Math.tanh(Py.dbl(a[0]));
                    case "isfinite": return Boolean.valueOf(!Double.isNaN(Py.dbl(a[0])) && !Double.isInfinite(Py.dbl(a[0])));
                    case "copysign": return Math.copySign(Py.dbl(a[0]), Py.dbl(a[1]));
                    case "comb": { long n = Py.toLong(a[0]), k = Py.toLong(a[1]); long r = 1; for (long i = 1; i <= k; i++) r = r * (n - k + i) / i; return r; }
                    case "perm": { long n = Py.toLong(a[0]), k = a.length > 1 ? Py.toLong(a[1]) : n; long r = 1; for (long i = 0; i < k; i++) r *= (n - i); return r; }
                    case "lcm": { long p = Math.abs(Py.toLong(a[0])), q = Math.abs(Py.toLong(a[1])); long g = p, h = q; while (h != 0) { long t = g % h; g = h; h = t; } return g == 0 ? 0L : p / g * q; }
                    case "prod": { Object r = 1L; for (Object x : Py.iter(a[0])) r = Py.mul(r, x); return r; }
                }
                break;
            }
            case "random": {
                switch (fn) {
                    case "choices": {
                        List<Object> l = Py.toList(a[0]);
                        long k = Py.toLong(Py.kwv(a, "k", 1L));
                        List<Object> r = new ArrayList<Object>();
                        for (long i = 0; i < k; i++) r.add(Py.mod("random", "choice", l));
                        return r;
                    }
                    case "gauss": case "normalvariate": {
                        double z = new java.util.Random().nextGaussian();
                        return Py.dbl(a[0]) + z * Py.dbl(a[1]);
                    }
                }
                break;
            }
        }
        throw new Py.PyError("AttributeError", "module '" + module + "' has no attribute '" + fn + "'");
    }

    // ----------------------------------------------------------------------- os

    static Object osMod(String fn, Object[] a) {
        switch (fn) {
            case "getcwd": return baseDir.getAbsolutePath();
            case "listdir": {
                File d = resolve(a.length > 0 ? Py.str(a[0]) : ".");
                String[] names = d.list();
                if (names == null) throw new Py.PyError("FileNotFoundError", "[Errno 2] No such file or directory: " + Py.repr(d.getPath()));
                Arrays.sort(names);
                return new ArrayList<Object>(Arrays.asList((Object[]) names));
            }
            case "makedirs": case "mkdir": {
                File d = resolve(Py.str(Py.arg(a, 0)));
                boolean ok = Py.truth(Py.kwv(a, "exist_ok", Boolean.FALSE));
                if (d.exists()) { if (ok && d.isDirectory()) return null; throw new Py.PyError("FileExistsError", "[Errno 17] File exists: " + Py.repr(Py.str(a[0]))); }
                if (!(fn.equals("makedirs") ? d.mkdirs() : d.mkdir())) throw new Py.PyError("OSError", "cannot create directory " + Py.repr(Py.str(a[0])));
                return null;
            }
            case "remove": case "unlink": case "rmdir": {
                File f = resolve(Py.str(Py.arg(a, 0)));
                if (!f.exists()) throw new Py.PyError("FileNotFoundError", "[Errno 2] No such file or directory: " + Py.repr(Py.str(a[0])));
                if (!f.delete()) throw new Py.PyError("OSError", "cannot remove " + Py.repr(Py.str(a[0])));
                return null;
            }
            case "rename": case "replace": {
                File f = resolve(Py.str(Py.arg(a, 0))), g = resolve(Py.str(Py.arg(a, 1)));
                if (!f.exists()) throw new Py.PyError("FileNotFoundError", "[Errno 2] No such file or directory: " + Py.repr(Py.str(a[0])));
                if (g.exists() && fn.equals("replace")) g.delete();
                if (!f.renameTo(g)) throw new Py.PyError("OSError", "cannot rename " + Py.repr(Py.str(a[0])));
                return null;
            }
            case "getenv": return a.length > 1 ? a[1] : null;
            case "getpid": return (long) android_pid();
            case "system": throw new Py.PyError("OSError", "os.system is not available in PocketTk apps");
            case "path.exists": return Boolean.valueOf(resolve(Py.str(Py.arg(a, 0))).exists());
            case "path.isfile": return Boolean.valueOf(resolve(Py.str(Py.arg(a, 0))).isFile());
            case "path.isdir": return Boolean.valueOf(resolve(Py.str(Py.arg(a, 0))).isDirectory());
            case "path.getsize": {
                File f = resolve(Py.str(Py.arg(a, 0)));
                if (!f.exists()) throw new Py.PyError("FileNotFoundError", "[Errno 2] No such file or directory: " + Py.repr(Py.str(a[0])));
                return f.length();
            }
            case "path.abspath": return resolve(Py.str(Py.arg(a, 0))).getAbsolutePath();
            case "path.expanduser": return Py.str(Py.arg(a, 0)).replaceFirst("^~", baseDir.getAbsolutePath());
            case "path.join": {
                StringBuilder sb = new StringBuilder();
                for (Object x : a) {
                    String p = Py.str(x);
                    if (p.startsWith("/")) sb.setLength(0);
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '/') sb.append('/');
                    sb.append(p);
                }
                return sb.toString();
            }
            case "path.basename": { String p = Py.str(Py.arg(a, 0)); return p.substring(p.lastIndexOf('/') + 1); }
            case "path.dirname": { String p = Py.str(Py.arg(a, 0)); int i = p.lastIndexOf('/'); return i < 0 ? "" : (i == 0 ? "/" : p.substring(0, i)); }
            case "path.split": {
                String p = Py.str(Py.arg(a, 0)); int i = p.lastIndexOf('/');
                return Py.tupleOf(i < 0 ? "" : (i == 0 ? "/" : p.substring(0, i)), p.substring(i + 1));
            }
            case "path.splitext": {
                String p = Py.str(Py.arg(a, 0));
                int slash = p.lastIndexOf('/'), dot = p.lastIndexOf('.');
                if (dot <= slash + 1) return Py.tupleOf(p, "");
                return Py.tupleOf(p.substring(0, dot), p.substring(dot));
            }
            case "path.normpath": return Py.str(Py.arg(a, 0));
        }
        throw new Py.PyError("AttributeError", "module 'os' has no attribute '" + fn + "'");
    }

    static int android_pid() { return 1; }

    // ----------------------------------------------------------------------- re

    static Pattern compileRe(String pat, long flags) {
        int f = 0;
        if ((flags & 2) != 0) f |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        if ((flags & 8) != 0) f |= Pattern.MULTILINE;
        if ((flags & 16) != 0) f |= Pattern.DOTALL;
        if ((flags & 64) != 0) f |= Pattern.COMMENTS;
        String j = pat.replace("(?P<", "(?<").replaceAll("\\(\\?P=(\\w+)\\)", "\\\\k<$1>");
        try { return Pattern.compile(j, f); }
        catch (PatternSyntaxException e) { throw new Py.PyError("error", "bad regular expression: " + e.getDescription()); }
    }

    static Object reMod(String fn, Object[] a) {
        if (fn.equals("compile")) return new RePattern(Py.str(Py.arg(a, 0)), Py.toLong(Py.arg(a, 1, 0L)));
        if (fn.equals("escape")) return Py.str(Py.arg(a, 0)).replaceAll("([^A-Za-z0-9_])", "\\\\$1");
        long flags = Py.toLong(Py.kwv(a, "flags", 0L));
        String pat = Py.str(Py.arg(a, 0));
        Object[] rest = new Object[Math.max(0, a.length - 1)];
        System.arraycopy(a, 1, rest, 0, rest.length);
        if (fn.equals("sub") || fn.equals("split") || fn.equals("subn")) {
            // flags may be positional (5th for sub, 3rd for split)
            int fi = fn.equals("split") ? 3 : 4;
            if (a.length > fi && !(a[fi] instanceof Py.Kw)) flags = Py.toLong(a[fi]);
        } else if (a.length > 2 && !(a[2] instanceof Py.Kw)) flags = Py.toLong(a[2]);
        return new RePattern(pat, flags).callm(fn, rest);
    }

    public static final class RePattern implements Py.Obj {
        final String src;
        final Pattern p;
        RePattern(String src, long flags) { this.src = src; this.p = compileRe(src, flags); }

        public Object callm(String name, Object[] a) {
            String s = a.length > 0 && a[0] instanceof String ? (String) a[0] : null;
            switch (name) {
                case "match": { Matcher m = p.matcher(str(a)); return m.lookingAt() ? new ReMatch(m, str(a)) : null; }
                case "fullmatch": { Matcher m = p.matcher(str(a)); return m.matches() ? new ReMatch(m, str(a)) : null; }
                case "search": { Matcher m = p.matcher(str(a)); return m.find() ? new ReMatch(m, str(a)) : null; }
                case "findall": {
                    Matcher m = p.matcher(str(a));
                    List<Object> r = new ArrayList<Object>();
                    while (m.find()) {
                        if (m.groupCount() == 0) r.add(m.group());
                        else if (m.groupCount() == 1) r.add(nz(m.group(1)));
                        else { List<Object> g = new ArrayList<Object>(); for (int i = 1; i <= m.groupCount(); i++) g.add(nz(m.group(i))); r.add(new Py.Tuple(g)); }
                    }
                    return r;
                }
                case "finditer": {
                    Matcher m = p.matcher(str(a));
                    List<Object> r = new ArrayList<Object>();
                    while (m.find()) r.add(new ReMatch(m, str(a)));
                    return r;
                }
                case "sub": case "subn": {
                    Object repl = Py.arg(a, 0);
                    String text = Py.str(Py.arg(a, 1));
                    long count = Py.toLong(Py.kwv(a, "count", Py.arg(a, 2, 0L)));
                    Matcher m = p.matcher(text);
                    StringBuilder sb = new StringBuilder();
                    int last = 0; long n = 0;
                    while ((count == 0 || n < count) && m.find()) {
                        sb.append(text, last, m.start());
                        if (repl instanceof Fn) sb.append(Py.str(((Fn) repl).call(new ReMatch(m, text))));
                        else sb.append(expand(Py.str(repl), m));
                        last = m.end(); n++;
                        if (m.end() == m.start()) { if (m.end() < text.length()) sb.append(text.charAt(m.end())); last = m.end() + 1; if (last > text.length()) break; m.region(last, text.length()); }
                    }
                    if (last < text.length()) sb.append(text, last, text.length());
                    return name.equals("sub") ? (Object) sb.toString() : Py.tupleOf(sb.toString(), n);
                }
                case "split": {
                    String text = Py.str(Py.arg(a, 0));
                    long max = Py.toLong(Py.kwv(a, "maxsplit", Py.arg(a, 1, 0L)));
                    Matcher m = p.matcher(text);
                    List<Object> r = new ArrayList<Object>();
                    int last = 0; long n = 0;
                    while ((max == 0 || n < max) && m.find()) {
                        if (m.end() == 0 && m.start() == 0) continue;
                        r.add(text.substring(last, m.start()));
                        for (int i = 1; i <= m.groupCount(); i++) r.add(nz(m.group(i)));
                        last = m.end(); n++;
                    }
                    r.add(text.substring(last));
                    return r;
                }
                case "@pattern": return src;
                default: throw new Py.PyError("AttributeError", "re.Pattern has no attribute '" + name + "'");
            }
        }

        static String str(Object[] a) { return Py.str(Py.arg(a, 0)); }
        static Object nz(String s) { return s; }

        static String expand(String t, Matcher m) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c == '\\' && i + 1 < t.length()) {
                    char d = t.charAt(i + 1);
                    if (Character.isDigit(d)) { int g = d - '0'; if (g <= m.groupCount() && m.group(g) != null) sb.append(m.group(g)); i++; }
                    else if (d == 'g' && i + 2 < t.length() && t.charAt(i + 2) == '<') {
                        int e = t.indexOf('>', i);
                        String nm = t.substring(i + 3, e);
                        String v = Character.isDigit(nm.charAt(0)) ? m.group(Integer.parseInt(nm)) : m.group(nm);
                        if (v != null) sb.append(v);
                        i = e;
                    }
                    else if (d == 'n') { sb.append('\n'); i++; }
                    else if (d == 't') { sb.append('\t'); i++; }
                    else if (d == '\\') { sb.append('\\'); i++; }
                    else sb.append(c);
                } else sb.append(c);
            }
            return sb.toString();
        }
    }

    public static final class ReMatch implements Py.Obj {
        final String[] groups;
        final int[] starts, ends;
        final Matcher m;
        final String text;
        ReMatch(Matcher m, String text) {
            this.text = text;
            int n = m.groupCount() + 1;
            groups = new String[n]; starts = new int[n]; ends = new int[n];
            for (int i = 0; i < n; i++) { groups[i] = m.group(i); starts[i] = m.start(i); ends[i] = m.end(i); }
            this.m = null;
            names = namesOf(m.pattern().pattern());
        }
        final Map<String, Integer> names;

        static Map<String, Integer> namesOf(String pat) {
            Map<String, Integer> r = new LinkedHashMap<String, Integer>();
            Matcher nm = Pattern.compile("\\(\\?<([A-Za-z]\\w*)>").matcher(pat);
            // group numbers: count capturing "(" not followed by "?" (plus named ones) before each name
            while (nm.find()) {
                int idx = 0;
                for (int i = 0; i < nm.start() + 1; i++) {
                    if (pat.charAt(i) == '\\') { i++; continue; }
                    if (pat.charAt(i) == '(' && (i + 1 >= pat.length() || pat.charAt(i + 1) != '?' || pat.startsWith("(?<", i) && pat.charAt(i + 3) != '=' && pat.charAt(i + 3) != '!')) idx++;
                }
                r.put(nm.group(1), idx);
            }
            return r;
        }

        int gi(Object g) {
            if (g instanceof String) {
                Integer i = names.get(g);
                if (i == null) throw new Py.PyError("IndexError", "no such group");
                return i;
            }
            int i = (int) Py.toLong(g);
            if (i < 0 || i >= groups.length) throw new Py.PyError("IndexError", "no such group");
            return i;
        }

        public Object callm(String name, Object[] a) {
            switch (name) {
                case "group": {
                    if (a.length == 0) return groups[0];
                    if (a.length == 1) return groups[gi(a[0])];
                    List<Object> r = new ArrayList<Object>();
                    for (Object g : a) r.add(groups[gi(g)]);
                    return new Py.Tuple(r);
                }
                case "groups": {
                    List<Object> r = new ArrayList<Object>();
                    for (int i = 1; i < groups.length; i++) r.add(groups[i]);
                    return new Py.Tuple(r);
                }
                case "groupdict": {
                    Map<Object, Object> r = new LinkedHashMap<Object, Object>();
                    for (Map.Entry<String, Integer> e : names.entrySet()) r.put(e.getKey(), groups[e.getValue()]);
                    return r;
                }
                case "start": return (long) starts[a.length > 0 ? gi(a[0]) : 0];
                case "end": return (long) ends[a.length > 0 ? gi(a[0]) : 0];
                case "span": { int i = a.length > 0 ? gi(a[0]) : 0; return Py.tupleOf((long) starts[i], (long) ends[i]); }
                case "@string": return text;
                case "__getitem__": return groups[gi(a[0])];
                default: throw new Py.PyError("AttributeError", "re.Match has no attribute '" + name + "'");
            }
        }

        @Override public String toString() { return "<re.Match object; span=(" + starts[0] + ", " + ends[0] + "), match=" + Py.repr(groups[0]) + ">"; }
    }

    // ------------------------------------------------------------- date & time

    static String strftime(String f, Calendar c) {
        String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        String[] mons = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < f.length(); i++) {
            char ch = f.charAt(i);
            if (ch != '%' || i + 1 >= f.length()) { sb.append(ch); continue; }
            char d = f.charAt(++i);
            boolean noPad = false;
            if (d == '-' && i + 1 < f.length()) { noPad = true; d = f.charAt(++i); }
            int hour12 = c.get(Calendar.HOUR) == 0 ? 12 : c.get(Calendar.HOUR);
            switch (d) {
                case 'Y': sb.append(c.get(Calendar.YEAR)); break;
                case 'y': sb.append(p2(c.get(Calendar.YEAR) % 100, noPad)); break;
                case 'm': sb.append(p2(c.get(Calendar.MONTH) + 1, noPad)); break;
                case 'd': sb.append(p2(c.get(Calendar.DAY_OF_MONTH), noPad)); break;
                case 'H': sb.append(p2(c.get(Calendar.HOUR_OF_DAY), noPad)); break;
                case 'I': sb.append(p2(hour12, noPad)); break;
                case 'M': sb.append(p2(c.get(Calendar.MINUTE), noPad)); break;
                case 'S': sb.append(p2(c.get(Calendar.SECOND), noPad)); break;
                case 'f': sb.append(String.format("%06d", c.get(Calendar.MILLISECOND) * 1000)); break;
                case 'p': sb.append(c.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM"); break;
                case 'A': sb.append(days[c.get(Calendar.DAY_OF_WEEK) - 1]); break;
                case 'a': sb.append(days[c.get(Calendar.DAY_OF_WEEK) - 1].substring(0, 3)); break;
                case 'B': sb.append(mons[c.get(Calendar.MONTH)]); break;
                case 'b': sb.append(mons[c.get(Calendar.MONTH)].substring(0, 3)); break;
                case 'j': sb.append(String.format("%03d", c.get(Calendar.DAY_OF_YEAR))); break;
                case 'w': sb.append(c.get(Calendar.DAY_OF_WEEK) - 1); break;
                case 'Z': sb.append(c.getTimeZone().getID()); break;
                case 'z': { int off = c.getTimeZone().getOffset(c.getTimeInMillis()) / 60000; sb.append(off < 0 ? '-' : '+').append(String.format("%02d%02d", Math.abs(off) / 60, Math.abs(off) % 60)); break; }
                case 'x': sb.append(strftime("%m/%d/%y", c)); break;
                case 'X': sb.append(strftime("%H:%M:%S", c)); break;
                case 'c': sb.append(strftime("%a %b %d %H:%M:%S %Y", c)); break;
                case 'D': sb.append(strftime("%m/%d/%y", c)); break;
                case 'F': sb.append(strftime("%Y-%m-%d", c)); break;
                case 'T': sb.append(strftime("%H:%M:%S", c)); break;
                case '%': sb.append('%'); break;
                default: sb.append('%').append(d);
            }
        }
        return sb.toString();
    }

    static String p2(int v, boolean noPad) { return noPad || v >= 10 ? String.valueOf(v) : "0" + v; }

    public static final class DateTime implements Py.Obj {
        final long ms;
        final boolean time;
        DateTime(long ms, boolean time) { this.ms = ms; this.time = time; }
        Calendar cal() { Calendar c = Calendar.getInstance(); c.setTimeInMillis(ms); return c; }

        static Object mod(String fn, Object[] a) {
            switch (fn) {
                case "datetime.now": case "datetime.today": case "datetime.utcnow": case "now": return new DateTime(System.currentTimeMillis(), true);
                case "date.today": return new DateTime(System.currentTimeMillis(), false);
                case "datetime": case "datetime.datetime": case "date": case "date.date": {
                    Calendar c = Calendar.getInstance();
                    c.clear();
                    c.set((int) Py.toLong(Py.arg(a, 0)), (int) Py.toLong(Py.arg(a, 1)) - 1, (int) Py.toLong(Py.arg(a, 2)),
                            (int) Py.toLong(Py.arg(a, 3, 0L)), (int) Py.toLong(Py.arg(a, 4, 0L)), (int) Py.toLong(Py.arg(a, 5, 0L)));
                    return new DateTime(c.getTimeInMillis(), fn.startsWith("datetime"));
                }
                case "datetime.fromtimestamp": return new DateTime((long) (Py.dbl(Py.arg(a, 0)) * 1000), true);
            }
            throw new Py.PyError("AttributeError", "module 'datetime' has no attribute '" + fn + "'");
        }

        public Object callm(String name, Object[] a) {
            Calendar c = cal();
            switch (name) {
                case "strftime": return strftime(Py.str(Py.arg(a, 0)), c);
                case "isoformat": return strftime(time ? "%Y-%m-%dT%H:%M:%S" : "%Y-%m-%d", c);
                case "timestamp": return ms / 1000.0;
                case "weekday": return (long) ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7);
                case "date": return new DateTime(ms, false);
                case "@year": return (long) c.get(Calendar.YEAR);
                case "@month": return (long) (c.get(Calendar.MONTH) + 1);
                case "@day": return (long) c.get(Calendar.DAY_OF_MONTH);
                case "@hour": return (long) c.get(Calendar.HOUR_OF_DAY);
                case "@minute": return (long) c.get(Calendar.MINUTE);
                case "@second": return (long) c.get(Calendar.SECOND);
                case "@microsecond": return (long) c.get(Calendar.MILLISECOND) * 1000L;
                case "@tm_year": return (long) c.get(Calendar.YEAR);
                case "@tm_mon": return (long) (c.get(Calendar.MONTH) + 1);
                case "@tm_mday": return (long) c.get(Calendar.DAY_OF_MONTH);
                case "@tm_hour": return (long) c.get(Calendar.HOUR_OF_DAY);
                case "@tm_min": return (long) c.get(Calendar.MINUTE);
                case "@tm_sec": return (long) c.get(Calendar.SECOND);
                default: throw new Py.PyError("AttributeError", "datetime has no attribute '" + name + "'");
            }
        }

        @Override public String toString() { return strftime(time ? "%Y-%m-%d %H:%M:%S" : "%Y-%m-%d", cal()); }
    }

    // --------------------------------------------------------------- threading

    public static final class PyThread implements Py.Obj {
        Object target;
        Object[] targs = new Object[0];
        Thread t;
        double delay;
        PyThread(Object[] a) {
            target = Py.kwv(a, "target", null);
            Object ar = Py.kwv(a, "args", null);
            if (ar instanceof List) targs = ((List) ar).toArray();
            delay = Py.dbl(Py.kwv(a, "delay", 0.0));
        }
        public Object callm(String name, Object[] a) {
            switch (name) {
                case "start": {
                    if (t != null) throw new Py.PyError("RuntimeError", "threads can only be started once");
                    t = new Thread(null, new Runnable() {
                        public void run() {
                            try {
                                if (delay > 0) Thread.sleep((long) (delay * 1000));
                                if (target != null) Py.call(target, targs);
                            } catch (InterruptedException e) { /* stopped */ }
                            catch (Throwable e) { if (reporter != null) reporter.report(e); }
                        }
                    }, "apkapple-thread", 8L << 20);
                    t.setDaemon(true);
                    t.start();
                    return null;
                }
                case "join": {
                    if (t != null) try { if (a.length > 0 && a[0] != null) t.join((long) (Py.dbl(a[0]) * 1000)); else t.join(); } catch (InterruptedException e) { /* ignore */ }
                    return null;
                }
                case "is_alive": return Boolean.valueOf(t != null && t.isAlive());
                case "setDaemon": return null;
                case "cancel": if (t != null) t.interrupt(); return null;
                case "@name": return "Thread-1";
                case "@daemon": return Boolean.TRUE;
                default: throw new Py.PyError("AttributeError", "Thread has no attribute '" + name + "'");
            }
        }
    }

    public static final class Lock implements Py.Obj {
        final java.util.concurrent.locks.ReentrantLock l = new java.util.concurrent.locks.ReentrantLock();
        public Object callm(String name, Object[] a) {
            switch (name) {
                case "acquire": case "__enter__": l.lock(); return Boolean.TRUE;
                case "release": case "__exit__": if (l.isHeldByCurrentThread()) l.unlock(); return null;
                case "locked": return Boolean.valueOf(l.isLocked());
                default: throw new Py.PyError("AttributeError", "Lock has no attribute '" + name + "'");
            }
        }
    }

    public static final class Flag implements Py.Obj {
        volatile boolean set;
        public Object callm(String name, Object[] a) {
            switch (name) {
                case "set": set = true; return null;
                case "clear": set = false; return null;
                case "is_set": return Boolean.valueOf(set);
                case "wait": {
                    long end = a.length > 0 && a[0] != null ? System.currentTimeMillis() + (long) (Py.dbl(a[0]) * 1000) : Long.MAX_VALUE;
                    while (!set && System.currentTimeMillis() < end) try { Thread.sleep(10); } catch (InterruptedException e) { break; }
                    return Boolean.valueOf(set);
                }
                default: throw new Py.PyError("AttributeError", "Event has no attribute '" + name + "'");
            }
        }
    }

    // -------------------------------------------------------------- collections

    public static final class DDict extends LinkedHashMap<Object, Object> {
        final Object factory;
        DDict(Object factory) { this.factory = factory; }
        Object make() { return factory == null ? null : Py.call(factory); }
    }

    public static final class Counter extends LinkedHashMap<Object, Object> {}

    public static final class Deque extends ArrayList<Object> {}

    static Object mapExtra(Map<Object, Object> m, String name, Object[] a) {
        switch (name) {
            case "most_common": {
                List<Object> items = new ArrayList<Object>();
                for (Map.Entry<Object, Object> e : m.entrySet()) items.add(Py.tupleOf(e.getKey(), e.getValue()));
                Collections.sort(items, new java.util.Comparator<Object>() {
                    public int compare(Object x, Object y) { return -Py.cmp(((List) x).get(1), ((List) y).get(1)); }
                });
                if (a.length > 0 && a[0] != null) { int n = (int) Py.toLong(a[0]); if (n < items.size()) items = new ArrayList<Object>(items.subList(0, n)); }
                return items;
            }
            case "elements": {
                List<Object> r = new ArrayList<Object>();
                for (Map.Entry<Object, Object> e : m.entrySet()) for (long i = 0; i < Py.toLong(e.getValue()); i++) r.add(e.getKey());
                return r;
            }
            case "total": { long s = 0; for (Object v : m.values()) s += Py.toLong(v); return s; }
            case "popitem": {
                if (m.isEmpty()) throw new Py.PyError("KeyError", "popitem(): dictionary is empty");
                Object k = null;
                for (Object x : m.keySet()) k = x;
                return Py.tupleOf(k, m.remove(k));
            }
            case "fromkeys": { Map<Object, Object> r = new LinkedHashMap<Object, Object>(); for (Object k : Py.iter(Py.arg(a, 0))) r.put(Py.key(k), Py.arg(a, 1, null)); return r; }
            default: throw new Py.PyError("AttributeError", "'dict' object has no attribute '" + name + "'");
        }
    }

    static Object listExtra(List<Object> l, String name, Object[] a) {
        switch (name) {
            case "appendleft": l.add(0, Py.arg(a, 0)); return null;
            case "popleft": if (l.isEmpty()) throw new Py.PyError("IndexError", "pop from an empty deque"); return l.remove(0);
            case "extendleft": for (Object x : Py.iter(Py.arg(a, 0))) l.add(0, x); return null;
            case "rotate": {
                if (l.isEmpty()) return null;
                Collections.rotate(l, (int) Py.toLong(Py.arg(a, 0, 1L)));
                return null;
            }
            default: throw new Py.PyError("AttributeError", "'list' object has no attribute '" + name + "'");
        }
    }

    // ------------------------------------------------------------------- Events

    /** tkinter event object: event.x, event.y, event.widget, event.char, event.keysym ... */
    public static final class Event implements Py.Obj {
        final Map<String, Object> f = new LinkedHashMap<String, Object>();
        public Event(Object widget, Object... kv) {
            f.put("widget", widget);
            for (int i = 0; i + 1 < kv.length; i += 2) f.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        public Object callm(String name, Object[] a) {
            if (name.startsWith("@")) {
                String k = name.substring(1);
                if (f.containsKey(k)) return f.get(k);
                if (k.equals("x") || k.equals("y") || k.equals("x_root") || k.equals("y_root") || k.equals("delta")) return 0L;
                if (k.equals("char") || k.equals("keysym")) return "";
                return null;
            }
            throw new Py.PyError("AttributeError", "event has no method '" + name + "'");
        }
        @Override public String toString() { return "<Event " + f.keySet() + ">"; }
    }

    // ----------------------------------------------------------------- tracebacks

    /** Python-style traceback for an error raised by the user's program. */
    public static String traceback(Throwable t) {
        Py.PyError pe = Py.wrap(t);
        StringBuilder sb = new StringBuilder("Traceback (most recent call last):\n");
        StackTraceElement[] st = t.getStackTrace();
        int lastLine = -1;
        int frames = 0;
        for (int i = st.length - 1; i >= 0; i--) {
            StackTraceElement e = st[i];
            if (!e.getClassName().startsWith("apple.gen.Script")) continue;
            int jl = e.getLineNumber();
            int pl = jl > 0 && jl < lineMap.length ? lineMap[jl] : 0;
            if (pl <= 0 || pl == lastLine) continue;
            lastLine = pl;
            frames++;
            sb.append("  File \"").append(scriptName).append("\", line ").append(pl).append('\n');
            if (pl - 1 < source.length) {
                String src = source[pl - 1].trim();
                if (!src.isEmpty()) sb.append("    ").append(src).append('\n');
            }
        }
        if (frames == 0) sb.append("  (error inside the PocketTk runtime)\n");
        sb.append(pe.toString());
        if (!(t instanceof Py.PyError)) sb.append("\n[java] ").append(t);
        return sb.toString();
    }

    // ---------------------------------------------------------------- datasaving

    static Map<String, Object> saved;
    static final Set<String> restored = new LinkedHashSet<String>();
    static Fn snapshot;
    static final Map<String, Object> initial = new LinkedHashMap<String, Object>();   // first assigned values, for datareset
    static boolean saving;
    static File stateFile;
    // Where the dpooa windows were ("$win:NAME" -> {x, y, w, h, min}, sizes in dp); saved next to the variables.
    static final Map<String, Object> winState = new LinkedHashMap<String, Object>();

    /** What was remembered about the dpooa window NAME (a Map with x, y, w, h, min), or null. */
    public static synchronized Object windowState(String name) {
        if (!saving) return null;
        String k = "$win:" + name;
        if (winState.containsKey(k)) return winState.get(k);
        return saved == null ? null : saved.get(k);
    }

    public static synchronized void putWindowState(String name, Object st) {
        if (!saving || st == null) return;
        winState.put("$win:" + name, st);
    }

    public static void enableDataSaving(File dir, Fn snap) {
        saving = true;
        snapshot = snap;
        stateFile = new File(dir, "apkapple-state.json");
        saved = new LinkedHashMap<String, Object>();
        if (stateFile.exists()) {
            try {
                Object o = loads(readAll(stateFile, stateFile.getPath()));
                if (o instanceof Map) for (Map.Entry e : ((Map<Object, Object>) o).entrySet()) saved.put(String.valueOf(e.getKey()), e.getValue());
            } catch (RuntimeException e) { saved.clear(); }
        }
    }

    static boolean isData(Object v) {
        if (v == null || v instanceof Boolean || v instanceof Long || v instanceof Double || v instanceof String) return true;
        if (v instanceof Py.Var) return true;
        if (v instanceof Py.Tuple || v instanceof List || v instanceof Set) {
            for (Object x : (java.util.Collection) v) if (!isData(x)) return false;
            return true;
        }
        if (v instanceof Map) {
            for (Object x : ((Map) v).values()) if (!isData(x)) return false;
            for (Object x : ((Map) v).keySet()) if (!isData(x)) return false;
            return true;
        }
        return false;
    }

    static Object encode(Object v) {
        if (v instanceof Py.Var) {
            Map<Object, Object> m = new LinkedHashMap<Object, Object>();
            m.put("$var", ((Py.Var) v).kind);
            m.put("v", encode(((Py.Var) v).val));
            return m;
        }
        if (v instanceof Py.Tuple) return wrap("$t", v);
        if (v instanceof Set) return wrap("$s", v);
        if (v instanceof List) { List<Object> r = new ArrayList<Object>(); for (Object x : (List) v) r.add(encode(x)); return r; }
        if (v instanceof Map) {
            List<Object> pairs = new ArrayList<Object>();
            for (Map.Entry e : ((Map<Object, Object>) v).entrySet()) pairs.add(Py.listOf(encode(e.getKey()), encode(e.getValue())));
            Map<Object, Object> m = new LinkedHashMap<Object, Object>();
            m.put("$d", pairs);
            return m;
        }
        if (v instanceof Double) {
            double d = (Double) v;
            if (Double.isNaN(d) || Double.isInfinite(d)) { Map<Object, Object> m = new LinkedHashMap<Object, Object>(); m.put("$f", Py.fmtDouble(d)); return m; }
        }
        return v;
    }

    static Object wrap(String tag, Object coll) {
        List<Object> r = new ArrayList<Object>();
        for (Object x : (java.util.Collection) coll) r.add(encode(x));
        Map<Object, Object> m = new LinkedHashMap<Object, Object>();
        m.put(tag, r);
        return m;
    }

    static Object decode(Object v) {
        if (v instanceof List) { List<Object> r = new ArrayList<Object>(); for (Object x : (List) v) r.add(decode(x)); return r; }
        if (v instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) v;
            if (m.containsKey("$t")) { List<Object> r = new ArrayList<Object>(); for (Object x : (List) m.get("$t")) r.add(decode(x)); return new Py.Tuple(r); }
            if (m.containsKey("$s")) { Set<Object> r = new LinkedHashSet<Object>(); for (Object x : (List) m.get("$s")) r.add(Py.key(decode(x))); return r; }
            if (m.containsKey("$f")) return Py.toFloat(m.get("$f"));
            if (m.containsKey("$d")) {
                Map<Object, Object> r = new LinkedHashMap<Object, Object>();
                for (Object p : (List) m.get("$d")) r.put(Py.key(decode(((List) p).get(0))), decode(((List) p).get(1)));
                return r;
            }
            if (m.containsKey("$var")) return v;
        }
        return v;
    }

    /** Called at every assignment of a module-level variable when #use: datasaving is on. */
    public static Object restore(String name, Object value) {
        if (!saving || saved == null) return value;
        if (!restored.add(name)) return value;
        if (isData(value)) initial.put(name, encode(value));   // pristine copy (encode copies containers)
        if (!saved.containsKey(name) || !isData(value)) return value;
        Object s = saved.get(name);
        try {
            if (value instanceof Py.Var) {
                if (s instanceof Map && ((Map) s).containsKey("$var")) {
                    Object inner = decode(((Map) s).get("v"));
                    ((Py.Var) value).set(inner);
                    return value;
                }
                return value;
            }
            if (s instanceof Map && ((Map) s).containsKey("$var")) return value;
            return decode(s);
        } catch (RuntimeException e) {
            return value;
        }
    }

    /** `#datasaving: datareset`: forgets the saved data and puts module-level variables back to their first assigned values. */
    public static synchronized void resetData(Fn assignAll) {
        if (!saving) return;
        if (saved != null) saved.clear();
        winState.clear();                      // window positions are saved data too
        if (stateFile != null) stateFile.delete();
        try { if (assignAll != null) assignAll.call(); } catch (RuntimeException ignored) { /* keep what was reset */ }
        saveState();
    }

    /** The value `name` had when the program first assigned it (a fresh copy), or `current` when unknown. */
    public static Object initialValue(String name, Object current) {
        if (!initial.containsKey(name)) return current;
        Object enc = initial.get(name);
        try {
            boolean wasVar = enc instanceof Map && ((Map) enc).containsKey("$var");
            if (current instanceof Py.Var) {
                if (wasVar) ((Py.Var) current).set(decode(((Map) enc).get("v")));
                return current;
            }
            return wasVar ? current : decode(enc);
        } catch (RuntimeException e) {
            return current;
        }
    }

    public static synchronized void saveState() {
        if (!saving || snapshot == null || stateFile == null) return;
        try {
            Object snap = snapshot.call();
            Map<Object, Object> out = new LinkedHashMap<Object, Object>();
            if (snap instanceof Map) {
                for (Map.Entry e : ((Map<Object, Object>) snap).entrySet()) {
                    if (!restored.contains(String.valueOf(e.getKey())) && saved != null && saved.containsKey(e.getKey())) {
                        out.put(e.getKey(), saved.get(e.getKey()));   // not reached yet: keep the old value
                        continue;
                    }
                    if (isData(e.getValue())) out.put(e.getKey(), encode(e.getValue()));
                }
            }
            if (saved != null) {                   // windows that were not opened this time keep their remembered place
                for (Map.Entry e : saved.entrySet()) if (String.valueOf(e.getKey()).startsWith("$win:")) out.put(e.getKey(), e.getValue());
            }
            out.putAll(winState);
            File tmp = new File(stateFile.getPath() + ".tmp");
            OutputStream os = new FileOutputStream(tmp);
            try { os.write(dumps(out, new Object[0]).getBytes("UTF-8")); } finally { os.close(); }
            if (!tmp.renameTo(stateFile)) { stateFile.delete(); tmp.renameTo(stateFile); }
        } catch (Throwable ignored) { /* best effort */ }
    }
}

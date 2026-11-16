package apple.rt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;

/** Runtime for user classes, bytes, generators and lazy iterators. */
public final class Oop {
    private Oop() {}

    public static final Object NOPE = new Object();

    // ------------------------------------------------------------ attribute extras (func.x = 1, widget.keep = img)

    static final Map<Object, Map<String, Object>> extras = Collections.synchronizedMap(new WeakHashMap<Object, Map<String, Object>>());

    static Map<String, Object> extrasOf(Object o, boolean create) {
        if (o == null || o instanceof String || o instanceof Number || o instanceof Boolean) return null;
        Map<String, Object> m = extras.get(o);
        if (m == null && create) { m = new LinkedHashMap<String, Object>(); extras.put(o, m); }
        return m;
    }

    // ------------------------------------------------------------ class machinery

    static final class StaticM { final Object fn; StaticM(Object f) { fn = f; } }
    static final class ClassM { final Object fn; ClassM(Object f) { fn = f; } }
    static final class Prop { Object get, set; Prop(Object g, Object s) { get = g; set = s; } }
    public static final class Field {
        final Object dflt, factory;
        Field(Object d, Object f) { dflt = d; factory = f; }
    }

    public static final class Bound implements Fn {
        final Object fn, self;
        Bound(Object fn, Object self) { this.fn = fn; this.self = self; }
        public Object call(Object... a) {
            Object[] b = new Object[a.length + 1];
            b[0] = self;
            System.arraycopy(a, 0, b, 1, a.length);
            return Py.call(fn, b);
        }
        @Override public String toString() { return "<bound method>"; }
    }

    static Object[] prepend(Object x, Object[] a) {
        Object[] b = new Object[a.length + 1];
        b[0] = x;
        System.arraycopy(a, 0, b, 1, a.length);
        return b;
    }

    public static final class Cls implements Fn, Py.Obj {
        public final String name;
        final Cls[] bases;
        final LinkedHashMap<String, Object> d = new LinkedHashMap<String, Object>();
        String tk;                 // tkinter widget kind this class derives from
        boolean dataclass, order, frozen, isEnum;
        String[] fields;
        Object[] defaults;
        List<Cls> mro;
        List<Object> members = new ArrayList<Object>();

        Cls(String name, Object[] bs) {
            this.name = name;
            List<Cls> cl = new ArrayList<Cls>();
            for (Object b : bs) {
                if (b instanceof Cls) {
                    Cls c = (Cls) b;
                    cl.add(c);
                    if (c.tk != null && tk == null) tk = c.tk;
                    if (c.isEnum) isEnum = true;
                } else if (b instanceof String) {
                    String s = (String) b;
                    if (s.startsWith("tk:")) tk = s.substring(3);
                    else if (s.startsWith("enum:")) isEnum = true;
                }
            }
            bases = cl.toArray(new Cls[0]);
        }

        List<Cls> mro() {
            if (mro != null) return mro;
            List<List<Cls>> seqs = new ArrayList<List<Cls>>();
            for (Cls b : bases) seqs.add(new ArrayList<Cls>(b.mro()));
            seqs.add(new ArrayList<Cls>(Arrays.asList(bases)));
            List<Cls> out = new ArrayList<Cls>();
            out.add(this);
            while (true) {
                seqs.removeIf(new java.util.function.Predicate<List<Cls>>() { public boolean test(List<Cls> l) { return l.isEmpty(); } });
                if (seqs.isEmpty()) break;
                Cls pick = null;
                for (List<Cls> s : seqs) {
                    Cls cand = s.get(0);
                    boolean bad = false;
                    for (List<Cls> t : seqs) if (t.indexOf(cand) > 0) { bad = true; break; }
                    if (!bad) { pick = cand; break; }
                }
                if (pick == null) throw new Py.PyError("TypeError", "Cannot create a consistent method resolution order (MRO) for bases");
                out.add(pick);
                for (List<Cls> s : seqs) if (!s.isEmpty() && s.get(0) == pick) s.remove(0);
            }
            mro = out;
            return out;
        }

        Object lookup(String n) {
            for (Cls c : mro()) {
                Object v = c.d.get(n);
                if (v != null || c.d.containsKey(n)) return v == null ? NONE : v;
            }
            return NOPE;
        }

        public void def(String n, Object fn, int kind) {
            switch (kind) {
                case 1: d.put(n, new StaticM(fn)); break;
                case 2: d.put(n, new ClassM(fn)); break;
                case 3: d.put(n, new Prop(fn, null)); break;
                case 4: {
                    Object o = d.get(n);
                    if (o instanceof Prop) ((Prop) o).set = fn; else d.put(n, new Prop(null, fn));
                    break;
                }
                default: d.put(n, fn);
            }
        }

        public void set(String n, Object v) { d.put(n, v); }

        public Object call(Object... a) {
            if (isEnum) return enumLookup(a);
            Inst o = new Inst(this);
            Object init = lookup("__init__");
            if (init != NOPE && init != NONE) Py.call(init, prepend(o, a));
            else if (dataclass) dataInit(o, a);
            else if (tk != null) tkInit(o, tk, a);
            else if (a.length > 0) throw new Py.PyError("TypeError", name + "() takes no arguments");
            return o;
        }

        Object enumLookup(Object[] a) {
            Object v = Py.arg(a, 0);
            for (Object m : members) if (Py.eq(((Inst) m).d.get("_value_"), v)) return m;
            throw new Py.PyError("ValueError", Py.repr(v) + " is not a valid " + name);
        }

        public Object callm(String n, Object[] a) {
            if (n.startsWith("@")) return attr(n.substring(1));
            Object f = lookup(n);
            if (f == NOPE) throw new Py.PyError("AttributeError", "type object '" + name + "' has no attribute '" + n + "'");
            if (f instanceof StaticM) return Py.call(((StaticM) f).fn, a);
            if (f instanceof ClassM) return Py.call(((ClassM) f).fn, prepend(this, a));
            return Py.call(f, a);
        }

        Object attr(String n) {
            if (n.equals("__name__") || n.equals("__qualname__")) return name;
            if (n.equals("__mro__")) return new Py.Tuple(new ArrayList<Object>(mro()));
            if (n.equals("__bases__")) return new Py.Tuple(new ArrayList<Object>(Arrays.asList(bases)));
            if (n.equals("__dict__")) return new LinkedHashMap<Object, Object>(d);
            if (n.equals("__doc__")) return null;
            if (isEnum && n.equals("__members__")) {
                Map<Object, Object> m = new LinkedHashMap<Object, Object>();
                for (Object x : members) m.put(((Inst) x).d.get("name"), x);
                return m;
            }
            Object f = lookup(n);
            if (f == NOPE) {
                Map<String, Object> ex = extrasOf(this, false);
                if (ex != null && ex.containsKey(n)) return ex.get(n);
                throw new Py.PyError("AttributeError", "type object '" + name + "' has no attribute '" + n + "'");
            }
            if (f == NONE) return null;
            if (f instanceof StaticM) return ((StaticM) f).fn;
            if (f instanceof ClassM) return new Bound(((ClassM) f).fn, this);
            if (f instanceof Prop) return f;
            return f;
        }

        boolean subclassOf(Cls o) { return mro().contains(o); }

        @Override public String toString() { return "<class '__main__." + name + "'>"; }
    }

    static final Object NONE = new Object();   // a class attribute explicitly set to None

    public static final class Inst implements Py.Obj {
        public final Cls cls;
        public final LinkedHashMap<String, Object> d = new LinkedHashMap<String, Object>();
        public Object base;        // underlying tkinter widget when the class derives from one

        public Inst(Cls c) { cls = c; }

        public Object dun(String n) {
            Object f = cls.lookup(n);
            return (f == NOPE || f == NONE) ? null : f;
        }

        Object invoke(Object f, Object[] a) {
            if (f instanceof StaticM) return Py.call(((StaticM) f).fn, a);
            if (f instanceof ClassM) return Py.call(((ClassM) f).fn, prepend(cls, a));
            if (f instanceof Prop) return Py.call(getProp((Prop) f), a);
            if (f instanceof Fn && !(f instanceof Cls) && !(f instanceof Bound) && isMethodFn(f)) return ((Fn) f).call(prepend(this, a));
            return Py.call(f, a);
        }

        Object getProp(Prop p) {
            if (p.get == null) throw new Py.PyError("AttributeError", "unreadable attribute");
            return Py.call(p.get, this);
        }

        public Object callm(String n, Object[] a) {
            if (n.startsWith("@")) return get(n.substring(1));
            Object v = d.get(n);
            if (v != null) return Py.call(v, a);
            Object f = cls.lookup(n);
            if (f != NOPE && f != NONE) return invoke(f, a);
            if (base != null) return Py.m(base, n, a);
            Object ga = dun("__getattr__");
            if (ga != null) return Py.call(Py.call(ga, this, n), a);
            throw new Py.PyError("AttributeError", "'" + cls.name + "' object has no attribute '" + n + "'");
        }

        Object get(String n) {
            Object v = d.get(n);
            if (v != null || d.containsKey(n)) return v;
            Object f = cls.lookup(n);
            if (f != NOPE) {
                if (f == NONE) return null;
                if (f instanceof Prop) return getProp((Prop) f);
                if (f instanceof StaticM) return ((StaticM) f).fn;
                if (f instanceof ClassM) return new Bound(((ClassM) f).fn, cls);
                if (f instanceof Fn && !(f instanceof Cls) && !(f instanceof Bound) && isMethodFn(f)) return new Bound(f, this);
                return f;
            }
            if (n.equals("__class__")) return cls;
            if (n.equals("__dict__")) return new LinkedHashMap<Object, Object>(d);
            if (base != null) {
                try { return Ext.attr(base, n); } catch (Py.PyError e) { if (!e.type.equals("AttributeError")) throw e; }
            }
            Object ga = dun("__getattr__");
            if (ga != null) return Py.call(ga, this, n);
            throw new Py.PyError("AttributeError", "'" + cls.name + "' object has no attribute '" + n + "'");
        }

        String str() {
            Object f = dun("__str__");
            if (f != null) return Py.str(Py.call(f, this));
            if (cls.isEnum && d.containsKey("name") && dun("__repr__") == null) return cls.name + "." + d.get("name");
            return repr();
        }

        String repr() {
            Object f = dun("__repr__");
            if (f != null) return Py.str(Py.call(f, this));
            if (cls.isEnum && d.containsKey("name")) return "<" + cls.name + "." + d.get("name") + ": " + Py.repr(d.get("_value_")) + ">";
            if (cls.dataclass) {
                StringBuilder sb = new StringBuilder(cls.name).append('(');
                for (int i = 0; i < cls.fields.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(cls.fields[i]).append('=').append(Py.repr(d.get(cls.fields[i])));
                }
                return sb.append(')').toString();
            }
            return "<__main__." + cls.name + " object at 0x" + Integer.toHexString(System.identityHashCode(this)) + ">";
        }

        @Override public String toString() { return repr(); }

        @Override public boolean equals(Object o) {
            if (o == this) return true;
            Object f = dun("__eq__");
            if (f != null) {
                Object r = Py.call(f, this, o);
                return r != Py.NOT_IMPLEMENTED && Py.truth(r);
            }
            if (cls.dataclass && o instanceof Inst && ((Inst) o).cls == cls) {
                for (String n : cls.fields) if (!Py.eq(d.get(n), ((Inst) o).d.get(n))) return false;
                return true;
            }
            return false;
        }

        @Override public int hashCode() {
            Object f = dun("__hash__");
            if (f != null) return (int) Py.toLong(Py.call(f, this));
            if (cls.dataclass && (cls.frozen || dun("__eq__") != null)) {
                int h = 17;
                for (String n : cls.fields) h = h * 31 + String.valueOf(d.get(n)).hashCode();
                return h;
            }
            return System.identityHashCode(this);
        }
    }

    // methods compiled by the transpiler are plain Fn objects; builtins/bound things are not methods
    static boolean isMethodFn(Object f) {
        return !(f instanceof Cls) && !(f instanceof Bound) && !(f instanceof BuiltinFn);
    }

    /** Marker for runtime-provided callables stored as class attributes (not turned into bound methods). */
    public abstract static class BuiltinFn implements Fn {}

    /** Remembers a function's Python name so f.__name__ works. */
    public static <T> T fname(T fn, String name) {
        Map<String, Object> m = extrasOf(fn, true);
        if (m != null) { m.put("__name__", name); m.put("__qualname__", name); }
        return fn;
    }

    public static Cls cls(String name, Object... bases) { return new Cls(name, bases); }

    public static Object tkMarker(String kind) { return "tk:" + kind; }

    static void tkInit(Inst o, String kind, Object[] a) {
        Object[] args = a;
        if (kind.equals("Tk")) o.base = Ui.tk();
        else if (kind.equals("thread")) {
            List<Object> l = new ArrayList<Object>(Arrays.asList(args));
            boolean hasTarget = false;
            for (Object x : l) if (x instanceof Py.Kw && ((Py.Kw) x).k.equals("target")) hasTarget = true;
            Object run = o.cls.lookup("run");
            if (!hasTarget && run != NOPE && run != NONE) l.add(Py.kw("target", new Bound(run, o)));
            o.base = Py.mod("threading", "Thread", l.toArray());
        }
        else o.base = Ui.wk(kind, args);
    }

    /** Explicit Base.__init__(self, ...) for a tkinter / native base class. */
    public static Object baseInit(Object self, String kind, Object... a) {
        if (self instanceof Inst && ((Inst) self).base == null) tkInit((Inst) self, kind, a);
        return null;
    }

    public static Object unwrap(Object o) {
        while (o instanceof Inst && ((Inst) o).base != null) o = ((Inst) o).base;
        return o;
    }

    // ------------------------------------------------------------ super()

    public static Object sup(Object self, Cls cur, String name, Object... a) {
        Cls start = self instanceof Cls ? (Cls) self : ((Inst) self).cls;
        List<Cls> mro = start.mro();
        int i = mro.indexOf(cur);
        for (int k = i + 1; k < mro.size(); k++) {
            Object f = mro.get(k).d.get(name);
            if (f != null) {
                if (f instanceof StaticM) return Py.call(((StaticM) f).fn, a);
                if (f instanceof ClassM) return Py.call(((ClassM) f).fn, prepend(start, a));
                if (f instanceof Prop) return Py.call(((Prop) f).get, self);
                return Py.call(f, prepend(self, a));
            }
        }
        // reached object / a tkinter base
        if (self instanceof Inst) {
            Inst o = (Inst) self;
            if (name.equals("__init__")) {
                if (o.cls.tk != null && o.base == null) tkInit(o, o.cls.tk, a);
                else if (o.cls.dataclass) dataInit(o, a);
                return null;
            }
            if (name.equals("__setattr__")) { o.d.put(Py.str(a[0]), a[1]); return null; }
            if (name.equals("__getattr__") || name.equals("__getattribute__")) return o.get(Py.str(a[0]));
            if (name.equals("__str__") || name.equals("__repr__")) return o.cls.dataclass || o.cls.isEnum ? o.repr() : "<__main__." + o.cls.name + " object>";
            if (name.equals("__eq__")) return o == a[0];
            if (name.equals("__hash__")) return (long) System.identityHashCode(o);
            if (name.equals("__post_init__") || name.equals("__del__")) return null;
            if (o.base != null) return Py.m(o.base, name, a);
        }
        if (name.equals("__init_subclass__")) return null;
        throw new Py.PyError("AttributeError", "'super' object has no attribute '" + name + "'");
    }

    // ------------------------------------------------------------ attribute set

    public static void setattr(Object o, String n, Object v) {
        if (o instanceof Inst) {
            Inst x = (Inst) o;
            Object sa = x.dun("__setattr__");
            if (sa != null) { Py.call(sa, x, n, v); return; }
            Object f = x.cls.lookup(n);
            if (f instanceof Prop) {
                Prop p = (Prop) f;
                if (p.set == null) throw new Py.PyError("AttributeError", "can't set attribute '" + n + "'");
                Py.call(p.set, x, v);
                return;
            }
            if (x.cls.frozen && x.cls.dataclass) throw new Py.PyError("FrozenInstanceError", "cannot assign to field '" + n + "'");
            x.d.put(n, v);
            return;
        }
        if (o instanceof Cls) { ((Cls) o).d.put(n, v == null ? NONE : v); return; }
        Map<String, Object> m = extrasOf(o, true);
        if (m == null) throw new Py.PyError("AttributeError", "'" + Py.tn(o) + "' object has no attribute '" + n + "'");
        m.put(n, v);
    }

    public static void delattr(Object o, String n) {
        if (o instanceof Inst) { ((Inst) o).d.remove(n); return; }
        if (o instanceof Cls) { ((Cls) o).d.remove(n); return; }
        Map<String, Object> m = extrasOf(o, false);
        if (m != null) m.remove(n);
    }

    // ------------------------------------------------------------ dunder dispatch used by Py

    static Object dunOf(Object o, String n) { return o instanceof Inst ? ((Inst) o).dun(n) : null; }

    private static final String[][] BIN = {
        {"add", "__add__", "__radd__", "+"}, {"sub", "__sub__", "__rsub__", "-"}, {"mul", "__mul__", "__rmul__", "*"},
        {"div", "__truediv__", "__rtruediv__", "/"}, {"floordiv", "__floordiv__", "__rfloordiv__", "//"},
        {"mod", "__mod__", "__rmod__", "%"}, {"pow", "__pow__", "__rpow__", "**"},
        {"&", "__and__", "__rand__", "&"}, {"|", "__or__", "__ror__", "|"}, {"^", "__xor__", "__rxor__", "^"},
        {"<<", "__lshift__", "__rlshift__", "<<"}, {">>", "__rshift__", "__rrshift__", ">>"},
    };

    public static Object bin(String op, Object a, Object b) {
        for (String[] r : BIN) {
            if (!r[0].equals(op)) continue;
            Object f = dunOf(a, r[1]);
            if (f != null) {
                Object res = Py.call(f, a, b);
                if (res != Py.NOT_IMPLEMENTED) return res;
            }
            Object g = dunOf(b, r[2]);
            if (g != null) {
                Object res = Py.call(g, b, a);
                if (res != Py.NOT_IMPLEMENTED) return res;
            }
            throw new Py.PyError("TypeError", "unsupported operand type(s) for " + r[3] + ": '" + Py.tn(a) + "' and '" + Py.tn(b) + "'");
        }
        throw new Py.PyError("TypeError", "unsupported operand");
    }

    public static Object neg(Inst a) {
        Object f = a.dun("__neg__");
        if (f == null) throw new Py.PyError("TypeError", "bad operand type for unary -: '" + a.cls.name + "'");
        return Py.call(f, a);
    }

    public static boolean truth(Inst a) {
        Object f = a.dun("__bool__");
        if (f != null) return Py.truth(Py.call(f, a));
        f = a.dun("__len__");
        if (f != null) return Py.toLong(Py.call(f, a)) != 0;
        return true;
    }

    public static int len(Inst a) {
        Object f = a.dun("__len__");
        if (f == null) throw new Py.PyError("TypeError", "object of type '" + a.cls.name + "' has no len()");
        return (int) Py.toLong(Py.call(f, a));
    }

    public static int cmp(Object a, Object b) {
        // a < b ?
        if (lt(a, b)) return -1;
        if (lt(b, a)) return 1;
        return 0;
    }

    static boolean lt(Object a, Object b) {
        Object f = dunOf(a, "__lt__");
        if (f != null) { Object r = Py.call(f, a, b); if (r != Py.NOT_IMPLEMENTED) return Py.truth(r); }
        Object g = dunOf(b, "__gt__");
        if (g != null) { Object r = Py.call(g, b, a); if (r != Py.NOT_IMPLEMENTED) return Py.truth(r); }
        if (a instanceof Inst && ((Inst) a).cls.dataclass && ((Inst) a).cls.order && b instanceof Inst && ((Inst) b).cls == ((Inst) a).cls) {
            Inst x = (Inst) a, y = (Inst) b;
            for (String n : x.cls.fields) {
                int c = Py.cmp(x.d.get(n), y.d.get(n));
                if (c != 0) return c < 0;
            }
            return false;
        }
        Object le = dunOf(a, "__le__");
        if (le != null) { Object r = Py.call(le, a, b); if (r != Py.NOT_IMPLEMENTED) return Py.truth(r) && !Py.eq(a, b); }
        Object ge = dunOf(b, "__ge__");
        if (ge != null) { Object r = Py.call(ge, b, a); if (r != Py.NOT_IMPLEMENTED) return Py.truth(r) && !Py.eq(a, b); }
        throw new Py.PyError("TypeError", "'<' not supported between instances of '" + Py.tn(a) + "' and '" + Py.tn(b) + "'");
    }

    public static boolean contains(Inst c, Object item) {
        Object f = c.dun("__contains__");
        if (f != null) return Py.truth(Py.call(f, c, item));
        for (Object o : Py.iter(c)) if (Py.eq(o, item)) return true;
        return false;
    }

    public static Object idx(Inst o, Object i) {
        Object f = o.dun("__getitem__");
        if (f != null) return Py.call(f, o, i);
        if (o.base != null) return Py.idx(o.base, i);
        throw new Py.PyError("TypeError", "'" + o.cls.name + "' object is not subscriptable");
    }

    public static void setIdx(Inst o, Object i, Object v) {
        Object f = o.dun("__setitem__");
        if (f != null) { Py.call(f, o, i, v); return; }
        if (o.base != null) { Py.setIdx(o.base, i, v); return; }
        throw new Py.PyError("TypeError", "'" + o.cls.name + "' object does not support item assignment");
    }

    public static Object clsIdx(Cls c, Object i) {
        if (c.isEnum) for (Object m : c.members) if (Py.eq(((Inst) m).d.get("name"), i)) return m;
        if (c.isEnum) throw new Py.PyError("KeyError", Py.repr(i));
        throw new Py.PyError("TypeError", "type '" + c.name + "' is not subscriptable");
    }

    public static void delIdx(Inst o, Object i) {
        Object f = o.dun("__delitem__");
        if (f == null) throw new Py.PyError("TypeError", "'" + o.cls.name + "' object doesn't support item deletion");
        Py.call(f, o, i);
    }

    public static Object callInst(Inst o, Object[] a) {
        Object f = o.dun("__call__");
        if (f == null) throw new Py.PyError("TypeError", "'" + o.cls.name + "' object is not callable");
        return Py.call(f, prepend(o, a));
    }

    public static Iterable<Object> iter(Object o) {
        if (o instanceof Cls) {
            Cls c = (Cls) o;
            if (c.isEnum) return new ArrayList<Object>(c.members);
            throw new Py.PyError("TypeError", "'type' object is not iterable");
        }
        Inst x = (Inst) o;
        Object f = x.dun("__iter__");
        if (f != null) {
            Object it = Py.call(f, x);
            if (it instanceof Inst) return new InstIter((Inst) it);
            return Py.iter(it);
        }
        if (x.dun("__next__") != null) return new InstIter(x);
        if (x.dun("__getitem__") != null) {
            final Inst xi = x;
            return new Iterable<Object>() {
                public Iterator<Object> iterator() {
                    return new Iterator<Object>() {
                        long i = 0; Object nxt; boolean ready, fin;
                        void fetch() {
                            if (ready || fin) return;
                            try { nxt = Py.call(xi.dun("__getitem__"), xi, i++); ready = true; }
                            catch (Py.PyError e) { if (e.matches("IndexError", "StopIteration")) fin = true; else throw e; }
                        }
                        public boolean hasNext() { fetch(); return ready; }
                        public Object next() { fetch(); if (!ready) throw new java.util.NoSuchElementException(); ready = false; return nxt; }
                        public void remove() { throw new UnsupportedOperationException(); }
                    };
                }
            };
        }
        if (x.base != null) return Py.iter(x.base);
        throw new Py.PyError("TypeError", "'" + x.cls.name + "' object is not iterable");
    }

    static final class InstIter implements Iterable<Object>, Iterator<Object> {
        final Inst it; Object nxt; boolean ready, fin;
        InstIter(Inst it) { this.it = it; }
        public Iterator<Object> iterator() { return this; }
        void fetch() {
            if (ready || fin) return;
            try { nxt = Py.call(it.dun("__next__"), it); ready = true; }
            catch (Py.PyError e) { if (e.matches("StopIteration")) fin = true; else throw e; }
        }
        public boolean hasNext() { fetch(); return ready; }
        public Object next() { fetch(); if (!ready) throw new java.util.NoSuchElementException(); ready = false; return nxt; }
        public void remove() { throw new UnsupportedOperationException(); }
    }

    // ------------------------------------------------------------ isinstance / type

    public static boolean isinst(Object x, Object t) {
        if (t instanceof Cls) {
            Cls c = (Cls) t;
            if (x instanceof Inst) return ((Inst) x).cls.subclassOf(c);
            return false;
        }
        if (t instanceof String && x instanceof Inst) {
            String n = (String) t;
            if (n.equals("object")) return true;
            for (Cls c : ((Inst) x).cls.mro()) if (c.name.equals(n)) return true;
            return false;
        }
        return false;
    }

    // ------------------------------------------------------------ dataclass / enum

    public static Object dataclass(Object c, String[] fields, Object[] defaults, boolean order, boolean frozen) {
        Cls cl = (Cls) c;
        cl.dataclass = true;
        cl.order = order;
        cl.frozen = frozen;
        // inherit fields of dataclass bases
        List<String> fs = new ArrayList<String>();
        List<Object> ds = new ArrayList<Object>();
        for (Cls b : cl.bases) if (b.dataclass) for (int i = 0; i < b.fields.length; i++) { fs.add(b.fields[i]); ds.add(b.defaults[i]); }
        for (int i = 0; i < fields.length; i++) {
            int at = fs.indexOf(fields[i]);
            if (at >= 0) ds.set(at, defaults[i]); else { fs.add(fields[i]); ds.add(defaults[i]); }
        }
        cl.fields = fs.toArray(new String[0]);
        cl.defaults = ds.toArray();
        return cl;
    }

    static void dataInit(Inst o, Object[] a) {
        Cls c = o.cls;
        Object[] pos = a;
        Map<String, Object> kws = new LinkedHashMap<String, Object>();
        int np = 0;
        for (Object x : a) { if (x instanceof Py.Kw) kws.put(((Py.Kw) x).k, ((Py.Kw) x).v); else np++; }
        if (np > c.fields.length) throw new Py.PyError("TypeError", c.name + "() takes " + (c.fields.length + 1) + " positional arguments but " + (np + 1) + " were given");
        int pi = 0;
        for (int i = 0; i < c.fields.length; i++) {
            String f = c.fields[i];
            Object v;
            if (pi < np) { while (pos[pi] instanceof Py.Kw) pi++; v = pos[pi++]; }
            else if (kws.containsKey(f)) v = kws.get(f);
            else {
                Object dv = c.defaults[i];
                if (dv == Py.MISSING) throw new Py.PyError("TypeError", c.name + "() missing 1 required positional argument: '" + f + "'");
                if (dv instanceof Field) {
                    Field fd = (Field) dv;
                    if (fd.factory != null) v = Py.call(fd.factory);
                    else if (fd.dflt == Py.MISSING) throw new Py.PyError("TypeError", c.name + "() missing 1 required positional argument: '" + f + "'");
                    else v = fd.dflt;
                } else v = dv;
            }
            o.d.put(f, v);
        }
        Object post = o.dun("__post_init__");
        if (post != null) Py.call(post, o);
    }

    public static Object field(Object... a) {
        Object dflt = Py.MISSING, factory = null;
        for (Object x : a) if (x instanceof Py.Kw) {
            Py.Kw k = (Py.Kw) x;
            if (k.k.equals("default")) dflt = k.v;
            else if (k.k.equals("default_factory")) factory = k.v;
        }
        return new Field(dflt, factory);
    }

    public static Object finishEnum(Object c) {
        Cls cl = (Cls) c;
        long auto = 0;
        List<String> names = new ArrayList<String>();
        for (Map.Entry<String, Object> e : new ArrayList<Map.Entry<String, Object>>(cl.d.entrySet())) {
            String k = e.getKey();
            Object v = e.getValue();
            if (k.startsWith("_") || v instanceof Fn || v instanceof StaticM || v instanceof ClassM || v instanceof Prop) continue;
            names.add(k);
        }
        for (String k : names) {
            Object v = cl.d.get(k);
            if (v == AUTO) v = ++auto;
            else if (v instanceof Long) auto = (Long) v;
            Inst m = new Inst(cl);
            m.d.put("name", k);
            m.d.put("_value_", v);
            m.d.put("value", v);
            cl.d.put(k, m);
            cl.members.add(m);
        }
        return cl;
    }

    public static final Object AUTO = new BuiltinFn() { public Object call(Object... a) { return AUTO; } };

    // ------------------------------------------------------------ generators

    static final ThreadLocal<Generator> CUR = new ThreadLocal<Generator>();

    static final class GenExit extends RuntimeException {
        GenExit() { super(null, null, false, false); }
    }

    public static final class Generator implements Iterable<Object>, Iterator<Object>, Py.Obj {
        final Fn body;
        Thread th;
        final Semaphore toGen = new Semaphore(0), toMain = new Semaphore(0);
        boolean started, done, closing, peeked, hasNext;
        Object value, sent, peekVal;
        Throwable err;

        Generator(Fn body) { this.body = body; }

        Object resume(Object send) {
            if (done) throw new Py.PyError("StopIteration", "");
            sent = send;
            if (!started) {
                started = true;
                th = new Thread(null, new Runnable() {
                    public void run() {
                        CUR.set(Generator.this);
                        try { body.call(); }
                        catch (GenExit ignored) { /* closed */ }
                        catch (Throwable t) { err = t; }
                        finally { done = true; toMain.release(); }
                    }
                }, "apkapple-gen", 16L * 1024 * 1024);
                th.setDaemon(true);
                th.start();
            } else {
                toGen.release();
            }
            toMain.acquireUninterruptibly();
            if (err != null) {
                Throwable t = err;
                err = null;
                if (t instanceof RuntimeException) throw (RuntimeException) t;
                if (t instanceof Error) throw (Error) t;
                throw new RuntimeException(t);
            }
            if (done) throw new Py.PyError("StopIteration", "");
            return value;
        }

        Object yieldValue(Object v) {
            value = v;
            toMain.release();
            toGen.acquireUninterruptibly();
            if (closing) throw new GenExit();
            return sent;
        }

        public Iterator<Object> iterator() { return this; }

        public boolean hasNext() {
            if (peeked) return hasNext;
            try { peekVal = resume(null); hasNext = true; }
            catch (Py.PyError e) { if (e.matches("StopIteration")) hasNext = false; else throw e; }
            peeked = true;
            return hasNext;
        }

        public Object next() {
            if (!hasNext()) throw new java.util.NoSuchElementException();
            peeked = false;
            return peekVal;
        }

        public void remove() { throw new UnsupportedOperationException(); }

        /** next(gen) */
        public Object nextValue() {
            if (peeked) {
                peeked = false;
                if (!hasNext) throw new Py.PyError("StopIteration", "");
                return peekVal;
            }
            return resume(null);
        }

        void close() {
            if (started && !done) {
                closing = true;
                toGen.release();
                toMain.acquireUninterruptibly();
            }
            done = true;
        }

        public Object callm(String n, Object[] a) {
            if (n.equals("__next__")) return nextValue();
            if (n.equals("send")) {
                Object v = a.length > 0 ? a[0] : null;
                if (!started) return resume(null);
                if (peeked) { peeked = false; }
                return resume(v);
            }
            if (n.equals("close")) { close(); return null; }
            if (n.equals("__iter__")) return this;
            if (n.startsWith("@")) {
                if (n.equals("@__iter__")) return this;
            }
            throw new Py.PyError("AttributeError", "'generator' object has no attribute '" + n + "'");
        }

        @Override public String toString() { return "<generator object>"; }
    }

    public static Object generator(Fn body) { return new Generator(body); }

    public static Object yld(Object v) {
        Generator g = CUR.get();
        if (g == null) throw new Py.PyError("RuntimeError", "yield outside a generator");
        return g.yieldValue(v);
    }

    public static Object yldFrom(Object src) {
        for (Object x : Py.iter(src)) yld(x);
        return null;
    }

    // ------------------------------------------------------------ lazy iterators (itertools)

    public abstract static class It implements Iterable<Object>, Iterator<Object>, Py.Obj {
        Object nxt;
        boolean ready, fin;
        /** Produce the next element or return NOPE when exhausted. */
        protected abstract Object produce();
        public Iterator<Object> iterator() { return this; }
        public boolean hasNext() {
            if (ready) return true;
            if (fin) return false;
            Object v = produce();
            if (v == NOPE) { fin = true; return false; }
            nxt = v;
            ready = true;
            return true;
        }
        public Object next() {
            if (!hasNext()) throw new java.util.NoSuchElementException();
            ready = false;
            return nxt;
        }
        public void remove() { throw new UnsupportedOperationException(); }
        public Object callm(String n, Object[] a) {
            if (n.equals("__next__")) {
                if (!hasNext()) throw new Py.PyError("StopIteration", "");
                return next();
            }
            if (n.equals("__iter__")) return this;
            throw new Py.PyError("AttributeError", "'iterator' object has no attribute '" + n + "'");
        }
        @Override public String toString() { return "<iterator>"; }
    }

    // ------------------------------------------------------------ bytes / bytearray

    public static final class Bytes implements Iterable<Object> {
        public byte[] b;
        public final boolean mutable;
        public Bytes(byte[] b, boolean mutable) { this.b = b; this.mutable = mutable; }
        public int size() { return b.length; }
        public Iterator<Object> iterator() {
            return new Iterator<Object>() {
                int i = 0;
                public boolean hasNext() { return i < b.length; }
                public Object next() { return (long) (b[i++] & 0xFF); }
                public void remove() { throw new UnsupportedOperationException(); }
            };
        }
        @Override public boolean equals(Object o) { return o instanceof Bytes && Arrays.equals(b, ((Bytes) o).b); }
        @Override public int hashCode() { return Arrays.hashCode(b); }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder(mutable ? "bytearray(b'" : "b'");
            for (byte x : b) {
                int c = x & 0xFF;
                if (c == '\\') sb.append("\\\\");
                else if (c == '\'') sb.append("\\'");
                else if (c == '\n') sb.append("\\n");
                else if (c == '\r') sb.append("\\r");
                else if (c == '\t') sb.append("\\t");
                else if (c >= 32 && c < 127) sb.append((char) c);
                else sb.append(String.format("\\x%02x", c));
            }
            return sb.append(mutable ? "')" : "'").toString();
        }
    }

    public static Bytes bytesOf(byte[] b) { return new Bytes(b, false); }

    public static Bytes bytesLit(String latin1) {
        byte[] b = new byte[latin1.length()];
        for (int i = 0; i < b.length; i++) b[i] = (byte) latin1.charAt(i);
        return new Bytes(b, false);
    }

    static byte[] utf8(String s) {
        try { return s.getBytes("UTF-8"); } catch (java.io.UnsupportedEncodingException e) { return s.getBytes(); }
    }

    static String decode(byte[] b, String enc) {
        enc = enc == null ? "utf-8" : enc.toLowerCase().replace("_", "-");
        try {
            if (enc.equals("utf-8") || enc.equals("utf8")) return new String(b, "UTF-8");
            if (enc.equals("ascii")) return new String(b, "US-ASCII");
            if (enc.equals("latin-1") || enc.equals("latin1") || enc.equals("iso-8859-1")) return new String(b, "ISO-8859-1");
            if (enc.equals("utf-16")) return new String(b, "UTF-16");
            return new String(b, enc);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new Py.PyError("LookupError", "unknown encoding: " + enc);
        }
    }

    public static byte[] encode(String s, String enc) {
        enc = enc == null ? "utf-8" : enc.toLowerCase().replace("_", "-");
        try {
            if (enc.equals("utf-8") || enc.equals("utf8")) return s.getBytes("UTF-8");
            if (enc.equals("ascii")) return s.getBytes("US-ASCII");
            if (enc.equals("latin-1") || enc.equals("latin1")) return s.getBytes("ISO-8859-1");
            return s.getBytes(enc);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new Py.PyError("LookupError", "unknown encoding: " + enc);
        }
    }

    public static Object bytesCtor(boolean mutable, Object[] a) {
        if (a.length == 0) return new Bytes(new byte[0], mutable);
        Object x = a[0];
        if (x instanceof String) return new Bytes(encode((String) x, a.length > 1 ? Py.str(a[1]) : "utf-8"), mutable);
        if (x instanceof Long) return new Bytes(new byte[(int) (long) (Long) x], mutable);
        if (x instanceof Bytes) return new Bytes(((Bytes) x).b.clone(), mutable);
        List<Object> l = Py.toList(x);
        byte[] b = new byte[l.size()];
        for (int i = 0; i < b.length; i++) {
            long v = Py.toLong(l.get(i));
            if (v < 0 || v > 255) throw new Py.PyError("ValueError", "bytes must be in range(0, 256)");
            b[i] = (byte) v;
        }
        return new Bytes(b, mutable);
    }

    public static Object bytesMethod(Bytes s, String n, Object[] a) {
        switch (n) {
            case "decode": return decode(s.b, a.length > 0 ? Py.str(a[0]) : "utf-8");
            case "hex": {
                StringBuilder sb = new StringBuilder();
                for (byte x : s.b) sb.append(String.format("%02x", x & 0xFF));
                return sb.toString();
            }
            case "append": {
                needMutable(s);
                s.b = Arrays.copyOf(s.b, s.b.length + 1);
                s.b[s.b.length - 1] = (byte) Py.toLong(a[0]);
                return null;
            }
            case "extend": {
                needMutable(s);
                for (Object o : Py.iter(a[0])) {
                    s.b = Arrays.copyOf(s.b, s.b.length + 1);
                    s.b[s.b.length - 1] = (byte) Py.toLong(o);
                }
                return null;
            }
            case "copy": return new Bytes(s.b.clone(), s.mutable);
            case "startswith": return startsWith(s.b, (Bytes) a[0], 0);
            case "endswith": return startsWith(s.b, (Bytes) a[0], s.b.length - ((Bytes) a[0]).b.length);
            case "find": return (long) indexOf(s.b, (Bytes) a[0]);
            case "join": {
                java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                boolean first = true;
                for (Object x : Py.iter(a[0])) {
                    if (!first) o.write(s.b, 0, s.b.length);
                    first = false;
                    byte[] bb = ((Bytes) x).b;
                    o.write(bb, 0, bb.length);
                }
                return new Bytes(o.toByteArray(), false);
            }
            case "upper": return new Bytes(new String(s.b, java.nio.charset.Charset.forName("ISO-8859-1")).toUpperCase().getBytes(java.nio.charset.Charset.forName("ISO-8859-1")), s.mutable);
            case "lower": return new Bytes(new String(s.b, java.nio.charset.Charset.forName("ISO-8859-1")).toLowerCase().getBytes(java.nio.charset.Charset.forName("ISO-8859-1")), s.mutable);
            case "strip": {
                int i = 0, j = s.b.length;
                while (i < j && (s.b[i] == ' ' || s.b[i] == '\n' || s.b[i] == '\r' || s.b[i] == '\t')) i++;
                while (j > i && (s.b[j - 1] == ' ' || s.b[j - 1] == '\n' || s.b[j - 1] == '\r' || s.b[j - 1] == '\t')) j--;
                return new Bytes(Arrays.copyOfRange(s.b, i, j), s.mutable);
            }
            case "split": {
                List<Object> out = new ArrayList<Object>();
                if (a.length == 0) {
                    String[] parts = new String(s.b, java.nio.charset.Charset.forName("ISO-8859-1")).trim().split("\\s+");
                    for (String p : parts) if (!p.isEmpty()) out.add(new Bytes(p.getBytes(java.nio.charset.Charset.forName("ISO-8859-1")), false));
                    return out;
                }
                byte[] sep = ((Bytes) a[0]).b;
                int from = 0;
                while (true) {
                    int at = indexOf(s.b, from, sep);
                    if (at < 0) { out.add(new Bytes(Arrays.copyOfRange(s.b, from, s.b.length), false)); break; }
                    out.add(new Bytes(Arrays.copyOfRange(s.b, from, at), false));
                    from = at + sep.length;
                }
                return out;
            }
            default:
                throw new Py.PyError("AttributeError", "'" + (s.mutable ? "bytearray" : "bytes") + "' object has no attribute '" + n + "'");
        }
    }

    static void needMutable(Bytes s) { if (!s.mutable) throw new Py.PyError("AttributeError", "'bytes' object has no attribute 'append'"); }
    static boolean startsWith(byte[] b, Bytes p, int at) {
        if (at < 0 || at + p.b.length > b.length) return false;
        for (int i = 0; i < p.b.length; i++) if (b[at + i] != p.b[i]) return false;
        return true;
    }
    static int indexOf(byte[] b, Bytes p) { return indexOf(b, 0, p.b); }
    static int indexOf(byte[] b, int from, byte[] p) {
        outer:
        for (int i = from; i + p.length <= b.length; i++) {
            for (int k = 0; k < p.length; k++) if (b[i + k] != p[k]) continue outer;
            return i;
        }
        return -1;
    }

    public static Object bytesIdx(Bytes s, Object i) {
        if (i instanceof Py.Sl) {
            List<Object> l = new ArrayList<Object>();
            for (byte x : s.b) l.add((long) (x & 0xFF));
            Object r = Py.idx(l, i);
            List<Object> rl = (List<Object>) r;
            byte[] out = new byte[rl.size()];
            for (int k = 0; k < out.length; k++) out[k] = (byte) Py.toLong(rl.get(k));
            return new Bytes(out, s.mutable);
        }
        int k = (int) Py.toLong(i);
        if (k < 0) k += s.b.length;
        if (k < 0 || k >= s.b.length) throw new Py.PyError("IndexError", "index out of range");
        return (long) (s.b[k] & 0xFF);
    }

    public static void bytesSetIdx(Bytes s, Object i, Object v) {
        needMutableItem(s);
        int k = (int) Py.toLong(i);
        if (k < 0) k += s.b.length;
        if (k < 0 || k >= s.b.length) throw new Py.PyError("IndexError", "index out of range");
        s.b[k] = (byte) Py.toLong(v);
    }

    static void needMutableItem(Bytes s) {
        if (!s.mutable) throw new Py.PyError("TypeError", "'bytes' object does not support item assignment");
    }

    public static Object bytesAdd(Bytes a, Bytes b) {
        byte[] r = Arrays.copyOf(a.b, a.b.length + b.b.length);
        System.arraycopy(b.b, 0, r, a.b.length, b.b.length);
        return new Bytes(r, a.mutable);
    }

    public static Object bytesMul(Bytes a, long n) {
        byte[] r = new byte[(int) Math.max(0, n) * a.b.length];
        for (int i = 0; i < n; i++) System.arraycopy(a.b, 0, r, i * a.b.length, a.b.length);
        return new Bytes(r, a.mutable);
    }
}

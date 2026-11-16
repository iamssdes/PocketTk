package apple.rt;

import java.security.MessageDigest;
import java.util.*;

/** Pure-Java ports of common stdlib modules: itertools, functools, copy, operator, statistics, hashlib, base64, uuid, csv. */
public final class Lib {
    private Lib() { }

    public static final Object NO = new Object();

    static Object arg(Object[] a, int i) { return Py.arg(a, i); }
    static long L(Object o) { return ((Number) Py.toInt(o)).longValue(); }
    static List<Object> pos(Object[] a) {
        List<Object> l = new ArrayList<Object>();
        for (Object x : a) if (!(x instanceof Py.Kw)) l.add(x);
        return l;
    }
    static Object kw(Object[] a, String k, Object d) { return Py.kwv(a, k, d); }
    static Py.Tuple tup(Object... xs) { return new Py.Tuple(new ArrayList<Object>(Arrays.asList(xs))); }
    static Py.Tuple tupL(List<Object> xs) { return new Py.Tuple(xs); }
    static Iterator<Object> it(Object o) { return Py.iter(o).iterator(); }
    static Fn fn(final java.util.function.Function<Object[], Object> f) {
        return new Fn() { public Object call(Object... a) { return f.apply(a); } };
    }

    public static Object modc(String module, String name) {
        switch (module) {
            case "math":
                break;
            case "csv":
                if (name.equals("QUOTE_ALL")) return 1L;
                if (name.equals("QUOTE_MINIMAL")) return 0L;
                if (name.equals("QUOTE_NONE")) return 3L;
                if (name.equals("QUOTE_NONNUMERIC")) return 2L;
                break;
            case "socket":
                return Net.modc(name);
            case "sqlite3":
                return Net.modc2(name);
        }
        return NO;
    }

    public static Object mod(String module, String fn, Object[] a) {
        switch (module) {
            case "itertools": return itertools(fn, a);
            case "functools": return functools(fn, a);
            case "copy": return copy(fn, a);
            case "operator": return operator(fn, a);
            case "statistics": return statistics(fn, a);
            case "hashlib": return hashlib(fn, a);
            case "base64": return base64(fn, a);
            case "uuid": return uuid(fn, a);
            case "csv": return csv(fn, a);
            case "socket": case "urllib": case "requests": case "sqlite3": return Net.mod(module, fn, a);
        }
        return NO;
    }

    // ------------------------------------------------------------ itertools

    static Object itertools(String fn, final Object[] a) {
        switch (fn) {
            case "count": {
                final Object[] cur = {a.length > 0 ? a[0] : (Object) 0L};
                final Object step = a.length > 1 ? a[1] : (Object) 1L;
                return new Oop.It() { protected Object produce() { Object r = cur[0]; cur[0] = Py.add(r, step); return r; } };
            }
            case "cycle": {
                final List<Object> saved = new ArrayList<Object>();
                final Iterator<Object> src = it(arg(a, 0));
                final int[] i = {0};
                final boolean[] done = {false};
                return new Oop.It() {
                    protected Object produce() {
                        if (!done[0]) {
                            if (src.hasNext()) { Object x = src.next(); saved.add(x); return x; }
                            done[0] = true;
                        }
                        if (saved.isEmpty()) return Oop.NOPE;
                        Object r = saved.get(i[0] % saved.size()); i[0]++; return r;
                    }
                };
            }
            case "repeat": {
                final Object v = arg(a, 0);
                final long n = a.length > 1 ? L(a[1]) : -1;
                final long[] c = {0};
                return new Oop.It() { protected Object produce() { if (n >= 0 && c[0] >= n) return Oop.NOPE; c[0]++; return v; } };
            }
            case "islice": {
                List<Object> p = pos(a);
                long start = 0, stop, step = 1;
                if (p.size() == 2) { stop = p.get(1) == null ? Long.MAX_VALUE : L(p.get(1)); }
                else {
                    start = p.get(1) == null ? 0 : L(p.get(1));
                    stop = p.get(2) == null ? Long.MAX_VALUE : L(p.get(2));
                    if (p.size() > 3 && p.get(3) != null) step = L(p.get(3));
                }
                final Iterator<Object> src = it(p.get(0));
                final long fStart = start, fStop = stop, fStep = step;
                final long[] idx = {0}, next = {fStart};
                return new Oop.It() {
                    protected Object produce() {
                        while (true) {
                            if (idx[0] >= fStop || !src.hasNext()) return Oop.NOPE;
                            Object x = src.next();
                            long i = idx[0]++;
                            if (i == next[0]) { next[0] += fStep; return x; }
                        }
                    }
                };
            }
            case "chain": {
                final List<Object> srcs = pos(a);
                return chainOf(srcs.iterator());
            }
            case "chain.from_iterable": return chainOf(it(arg(a, 0)));
            case "product": {
                List<Object> p = pos(a);
                long rep = L(kw(a, "repeat", 1L));
                List<List<Object>> pools = new ArrayList<List<Object>>();
                for (long r = 0; r < rep; r++) for (Object x : p) pools.add(Py.toList(x));
                List<Object> out = new ArrayList<Object>();
                prod(pools, 0, new Object[pools.size()], out);
                return listIt(out);
            }
            case "permutations": {
                List<Object> pool = Py.toList(arg(a, 0));
                int r = a.length > 1 && !(a[1] instanceof Py.Kw) && a[1] != null ? (int) L(a[1]) : pool.size();
                List<Object> out = new ArrayList<Object>();
                perm(pool, r, new ArrayList<Object>(), new boolean[pool.size()], out);
                return listIt(out);
            }
            case "combinations": case "combinations_with_replacement": {
                List<Object> pool = Py.toList(arg(a, 0));
                int r = (int) L(arg(a, 1));
                List<Object> out = new ArrayList<Object>();
                comb(pool, r, 0, new ArrayList<Object>(), out, fn.endsWith("replacement"));
                return listIt(out);
            }
            case "groupby": {
                final Object keyFn = a.length > 1 && !(a[1] instanceof Py.Kw) ? a[1] : kw(a, "key", null);
                final List<Object> items = Py.toList(arg(a, 0));
                final List<Object> out = new ArrayList<Object>();
                int i = 0;
                while (i < items.size()) {
                    Object k = keyFn == null ? items.get(i) : Py.call(keyFn, items.get(i));
                    List<Object> grp = new ArrayList<Object>();
                    while (i < items.size()) {
                        Object k2 = keyFn == null ? items.get(i) : Py.call(keyFn, items.get(i));
                        if (!Py.eq(k, k2)) break;
                        grp.add(items.get(i)); i++;
                    }
                    out.add(tup(k, listIt(grp)));
                }
                return listIt(out);
            }
            case "zip_longest": {
                final Object fill = kw(a, "fillvalue", null);
                final List<Iterator<Object>> its = new ArrayList<Iterator<Object>>();
                for (Object x : pos(a)) its.add(it(x));
                return new Oop.It() {
                    protected Object produce() {
                        List<Object> row = new ArrayList<Object>();
                        boolean any = false;
                        for (Iterator<Object> i : its) { if (i.hasNext()) { row.add(i.next()); any = true; } else row.add(fill); }
                        return any ? tupL(row) : Oop.NOPE;
                    }
                };
            }
            case "accumulate": {
                final Iterator<Object> src = it(arg(a, 0));
                final Object f = a.length > 1 && !(a[1] instanceof Py.Kw) ? a[1] : kw(a, "func", null);
                final Object init = kw(a, "initial", Oop.NOPE);
                final Object[] acc = {Oop.NOPE};
                final boolean[] first = {true};
                return new Oop.It() {
                    protected Object produce() {
                        if (first[0] && init != Oop.NOPE) { first[0] = false; acc[0] = init; return init; }
                        first[0] = false;
                        if (!src.hasNext()) return Oop.NOPE;
                        Object x = src.next();
                        acc[0] = acc[0] == Oop.NOPE ? x : (f == null ? Py.add(acc[0], x) : Py.call(f, acc[0], x));
                        return acc[0];
                    }
                };
            }
            case "takewhile": {
                final Object f = arg(a, 0); final Iterator<Object> src = it(arg(a, 1));
                return new Oop.It() { protected Object produce() {
                    if (!src.hasNext()) return Oop.NOPE; Object x = src.next();
                    return Py.truth(Py.call(f, x)) ? x : Oop.NOPE; } };
            }
            case "dropwhile": {
                final Object f = arg(a, 0); final Iterator<Object> src = it(arg(a, 1)); final boolean[] dropping = {true};
                return new Oop.It() { protected Object produce() {
                    while (src.hasNext()) { Object x = src.next(); if (dropping[0] && Py.truth(Py.call(f, x))) continue; dropping[0] = false; return x; }
                    return Oop.NOPE; } };
            }
            case "filterfalse": {
                final Object f = arg(a, 0); final Iterator<Object> src = it(arg(a, 1));
                return new Oop.It() { protected Object produce() {
                    while (src.hasNext()) { Object x = src.next(); if (!Py.truth(f == null ? x : Py.call(f, x))) return x; }
                    return Oop.NOPE; } };
            }
            case "starmap": {
                final Object f = arg(a, 0); final Iterator<Object> src = it(arg(a, 1));
                return new Oop.It() { protected Object produce() {
                    if (!src.hasNext()) return Oop.NOPE; return Py.call(f, Py.toList(src.next()).toArray()); } };
            }
            case "compress": {
                final Iterator<Object> d = it(arg(a, 0)), s = it(arg(a, 1));
                return new Oop.It() { protected Object produce() {
                    while (d.hasNext() && s.hasNext()) { Object x = d.next(); if (Py.truth(s.next())) return x; }
                    return Oop.NOPE; } };
            }
            case "pairwise": {
                final List<Object> l = Py.toList(arg(a, 0)); final int[] i = {0};
                return new Oop.It() { protected Object produce() {
                    if (i[0] + 1 >= l.size()) return Oop.NOPE; Object r = tup(l.get(i[0]), l.get(i[0] + 1)); i[0]++; return r; } };
            }
            case "tee": {
                List<Object> l = Py.toList(arg(a, 0));
                int n = a.length > 1 ? (int) L(a[1]) : 2;
                List<Object> out = new ArrayList<Object>();
                for (int i = 0; i < n; i++) out.add(listIt(new ArrayList<Object>(l)));
                return tupL(out);
            }
        }
        throw new Py.PyError("AttributeError", "module 'itertools' has no attribute '" + fn + "'");
    }

    static Oop.It listIt(final List<Object> l) {
        final int[] i = {0};
        return new Oop.It() { protected Object produce() { return i[0] < l.size() ? l.get(i[0]++) : Oop.NOPE; } };
    }

    static Oop.It chainOf(final Iterator<Object> srcs) {
        final Iterator<Object>[] cur = new Iterator[1];
        return new Oop.It() {
            protected Object produce() {
                while (true) {
                    if (cur[0] != null && cur[0].hasNext()) return cur[0].next();
                    if (!srcs.hasNext()) return Oop.NOPE;
                    cur[0] = it(srcs.next());
                }
            }
        };
    }

    static void prod(List<List<Object>> pools, int d, Object[] cur, List<Object> out) {
        if (d == pools.size()) { out.add(tup(cur.clone())); return; }
        for (Object x : pools.get(d)) { cur[d] = x; prod(pools, d + 1, cur, out); }
    }

    static void perm(List<Object> pool, int r, List<Object> cur, boolean[] used, List<Object> out) {
        if (cur.size() == r) { out.add(tupL(new ArrayList<Object>(cur))); return; }
        for (int i = 0; i < pool.size(); i++) {
            if (used[i]) continue;
            used[i] = true; cur.add(pool.get(i));
            perm(pool, r, cur, used, out);
            cur.remove(cur.size() - 1); used[i] = false;
        }
    }

    static void comb(List<Object> pool, int r, int start, List<Object> cur, List<Object> out, boolean rep) {
        if (cur.size() == r) { out.add(tupL(new ArrayList<Object>(cur))); return; }
        for (int i = start; i < pool.size(); i++) {
            cur.add(pool.get(i));
            comb(pool, r, rep ? i : i + 1, cur, out, rep);
            cur.remove(cur.size() - 1);
        }
    }

    // ------------------------------------------------------------ functools

    static Object functools(String fn, final Object[] a) {
        switch (fn) {
            case "reduce": {
                Iterator<Object> src = it(arg(a, 1));
                Object acc;
                if (a.length > 2) acc = a[2];
                else {
                    if (!src.hasNext()) throw new Py.PyError("TypeError", "reduce() of empty iterable with no initial value");
                    acc = src.next();
                }
                while (src.hasNext()) acc = Py.call(a[0], acc, src.next());
                return acc;
            }
            case "partial": {
                final Object f = arg(a, 0);
                final List<Object> held = new ArrayList<Object>(Arrays.asList(a).subList(1, a.length));
                return new Fn() {
                    public Object call(Object... b) {
                        List<Object> all = new ArrayList<Object>();
                        Map<String, Object> kws = new LinkedHashMap<String, Object>();
                        for (Object x : held) if (x instanceof Py.Kw) kws.put(((Py.Kw) x).k, ((Py.Kw) x).v); else all.add(x);
                        for (Object x : b) if (x instanceof Py.Kw) kws.put(((Py.Kw) x).k, ((Py.Kw) x).v); else all.add(x);
                        for (Map.Entry<String, Object> e : kws.entrySet()) all.add(Py.kw(e.getKey(), e.getValue()));
                        return Py.call(f, all.toArray());
                    }
                };
            }
            case "wraps": case "total_ordering": case "singledispatch":
                if (fn.equals("wraps")) return new Fn() { public Object call(Object... b) { return b[0]; } };
                return arg(a, 0);
            case "cache": return cached(arg(a, 0));
            case "lru_cache": {
                if (a.length > 0 && !(a[0] instanceof Py.Kw) && (a[0] instanceof Fn || a[0] instanceof Oop.Cls)) return cached(a[0]);
                return new Fn() { public Object call(Object... b) { return cached(b[0]); } };
            }
            case "cmp_to_key": {
                final Object f = arg(a, 0);
                return new Fn() { public Object call(Object... b) { return new CmpKey(f, b[0]); } };
            }
        }
        throw new Py.PyError("AttributeError", "module 'functools' has no attribute '" + fn + "'");
    }

    public static final class CmpKey implements Comparable<Object> {
        final Object f, v;
        CmpKey(Object f, Object v) { this.f = f; this.v = v; }
        public int compareTo(Object o) { return (int) L(Py.call(f, v, ((CmpKey) o).v)); }
    }

    static Object cached(final Object f) {
        final Map<Object, Object> memo = new HashMap<Object, Object>();
        return new Fn() {
            public Object call(Object... b) {
                List<Object> k = new ArrayList<Object>();
                for (Object x : b) k.add(x instanceof Py.Kw ? tup(((Py.Kw) x).k, Py.key(((Py.Kw) x).v)) : Py.key(x));
                if (memo.containsKey(k)) return memo.get(k);
                Object r = Py.call(f, b);
                memo.put(k, r);
                return r;
            }
        };
    }

    // ------------------------------------------------------------ copy

    static Object copy(String fn, Object[] a) {
        Object x = arg(a, 0);
        switch (fn) {
            case "copy": return dup(x, false);
            case "deepcopy": return dup(x, true);
        }
        throw new Py.PyError("AttributeError", "module 'copy' has no attribute '" + fn + "'");
    }

    @SuppressWarnings("unchecked")
    static Object dup(Object x, boolean deep) {
        if (x instanceof Py.Tuple) {
            if (!deep) return x;
            List<Object> l = new ArrayList<Object>();
            for (Object y : (List<Object>) x) l.add(dup(y, true));
            return new Py.Tuple(l);
        }
        if (x instanceof List) {
            List<Object> src = (List<Object>) x;
            List<Object> l = (x instanceof Ext.Deque) ? new Ext.Deque() : new ArrayList<Object>();
            for (Object y : src) l.add(deep ? dup(y, true) : y);
            return l;
        }
        if (x instanceof Map) {
            Map<Object, Object> m = (x instanceof Ext.Counter) ? new Ext.Counter() : new LinkedHashMap<Object, Object>();
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) x).entrySet()) m.put(e.getKey(), deep ? dup(e.getValue(), true) : e.getValue());
            return m;
        }
        if (x instanceof Set) {
            Set<Object> s = new LinkedHashSet<Object>();
            for (Object y : (Set<Object>) x) s.add(deep ? dup(y, true) : y);
            return s;
        }
        if (x instanceof Oop.Bytes) { Oop.Bytes b = (Oop.Bytes) x; return new Oop.Bytes(b.b.clone(), b.mutable); }
        if (x instanceof Oop.Inst) {
            Oop.Inst s = (Oop.Inst) x, n = new Oop.Inst(s.cls);
            n.base = s.base;
            for (Map.Entry<String, Object> e : s.d.entrySet()) n.d.put(e.getKey(), deep ? dup(e.getValue(), true) : e.getValue());
            return n;
        }
        return x;
    }

    // ------------------------------------------------------------ operator

    static Object operator(String fn, final Object[] a) {
        switch (fn) {
            case "itemgetter": {
                final List<Object> ks = pos(a);
                return new Fn() { public Object call(Object... b) {
                    if (ks.size() == 1) return Py.idx(b[0], ks.get(0));
                    List<Object> r = new ArrayList<Object>(); for (Object k : ks) r.add(Py.idx(b[0], k)); return tupL(r); } };
            }
            case "attrgetter": {
                final List<Object> ks = pos(a);
                return new Fn() { public Object call(Object... b) {
                    List<Object> r = new ArrayList<Object>();
                    for (Object k : ks) { Object v = b[0]; for (String part : Py.str(k).split("\\.")) v = Ext.attr(v, part); r.add(v); }
                    return ks.size() == 1 ? r.get(0) : tupL(r); } };
            }
            case "methodcaller": {
                final String n = Py.str(arg(a, 0)); final Object[] rest = Arrays.copyOfRange(a, 1, a.length);
                return new Fn() { public Object call(Object... b) { return Py.m(b[0], n, rest); } };
            }
            case "add": return Py.add(arg(a, 0), arg(a, 1));
            case "sub": return Py.sub(arg(a, 0), arg(a, 1));
            case "mul": return Py.mul(arg(a, 0), arg(a, 1));
            case "truediv": return Py.div(arg(a, 0), arg(a, 1));
            case "floordiv": return Py.floordiv(arg(a, 0), arg(a, 1));
            case "mod": return Py.mod(arg(a, 0), arg(a, 1));
            case "pow": return Py.pow(arg(a, 0), arg(a, 1));
            case "neg": return Py.neg(arg(a, 0));
            case "eq": return Py.eq(arg(a, 0), arg(a, 1));
            case "ne": return !Py.eq(arg(a, 0), arg(a, 1));
            case "lt": return Py.cmp(arg(a, 0), arg(a, 1)) < 0;
            case "le": return Py.cmp(arg(a, 0), arg(a, 1)) <= 0;
            case "gt": return Py.cmp(arg(a, 0), arg(a, 1)) > 0;
            case "ge": return Py.cmp(arg(a, 0), arg(a, 1)) >= 0;
            case "not_": return !Py.truth(arg(a, 0));
            case "truth": return Py.truth(arg(a, 0));
            case "getitem": return Py.idx(arg(a, 0), arg(a, 1));
            case "contains": return Py.contains(arg(a, 0), arg(a, 1));
        }
        throw new Py.PyError("AttributeError", "module 'operator' has no attribute '" + fn + "'");
    }

    // ------------------------------------------------------------ statistics

    static double[] nums(Object o) {
        List<Object> l = Py.toList(o);
        double[] d = new double[l.size()];
        for (int i = 0; i < d.length; i++) d[i] = Py.dbl(l.get(i));
        return d;
    }

    static Object statistics(String fn, Object[] a) {
        double[] d = nums(arg(a, 0));
        if (d.length == 0) throw new Py.PyError("StatisticsError", fn.equals("mode") ? "no mode for empty data" : "no data");
        double sum = 0;
        for (double x : d) sum += x;
        double mean = sum / d.length;
        switch (fn) {
            case "mean": case "fmean": return mean;
            case "median": case "median_low": case "median_high": {
                double[] s = d.clone(); Arrays.sort(s);
                int n = s.length;
                if (n % 2 == 1) return numOut(s[n / 2], arg(a, 0));
                if (fn.equals("median_low")) return numOut(s[n / 2 - 1], arg(a, 0));
                if (fn.equals("median_high")) return numOut(s[n / 2], arg(a, 0));
                return (s[n / 2 - 1] + s[n / 2]) / 2.0;
            }
            case "variance": case "stdev": case "pvariance": case "pstdev": {
                boolean pop = fn.startsWith("p");
                if (!pop && d.length < 2) throw new Py.PyError("StatisticsError", "variance requires at least two data points");
                double m = a.length > 1 && !(a[1] instanceof Py.Kw) ? Py.dbl(a[1]) : mean, ss = 0;
                for (double x : d) ss += (x - m) * (x - m);
                double v = ss / (pop ? d.length : d.length - 1);
                return fn.endsWith("dev") ? Math.sqrt(v) : v;
            }
            case "mode": {
                List<Object> l = Py.toList(arg(a, 0));
                Map<Object, Integer> c = new LinkedHashMap<Object, Integer>();
                Object best = null; int bc = 0;
                for (Object x : l) { Object k = Py.key(x); int n = c.containsKey(k) ? c.get(k) + 1 : 1; c.put(k, n); if (n > bc) { bc = n; best = x; } }
                return best;
            }
        }
        throw new Py.PyError("AttributeError", "module 'statistics' has no attribute '" + fn + "'");
    }

    static Object numOut(double v, Object src) {
        for (Object x : Py.toList(src)) if (x instanceof Double) return v;
        return (long) v;
    }

    // ------------------------------------------------------------ hashlib

    static byte[] bytesArg(Object o) {
        if (o instanceof Oop.Bytes) return ((Oop.Bytes) o).b;
        if (o instanceof String) return ((String) o).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        throw new Py.PyError("TypeError", "Strings must be encoded before hashing");
    }

    static Object hashlib(String fn, Object[] a) {
        String alg;
        switch (fn) {
            case "md5": alg = "MD5"; break;
            case "sha1": alg = "SHA-1"; break;
            case "sha224": alg = "SHA-224"; break;
            case "sha256": alg = "SHA-256"; break;
            case "sha384": alg = "SHA-384"; break;
            case "sha512": alg = "SHA-512"; break;
            case "new": alg = Py.str(arg(a, 0)).toUpperCase().replace("SHA", "SHA-").replace("SHA--", "SHA-"); a = Arrays.copyOfRange(a, 1, a.length); break;
            default: throw new Py.PyError("AttributeError", "module 'hashlib' has no attribute '" + fn + "'");
        }
        try {
            Hash h = new Hash(fn.equals("new") ? alg : alg, MessageDigest.getInstance(alg));
            if (a.length > 0 && !(a[0] instanceof Py.Kw)) h.md.update(bytesArg(a[0]));
            return h;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new Py.PyError("ValueError", "unsupported hash type " + fn);
        }
    }

    static final class Hash implements Py.Obj {
        final String name; final MessageDigest md;
        Hash(String n, MessageDigest m) { name = n; md = m; }
        static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x & 255)); return s.toString(); }
        public Object callm(String n, Object[] a) {
            switch (n) {
                case "update": md.update(bytesArg(arg(a, 0))); return null;
                case "hexdigest": try { return hex(((MessageDigest) md.clone()).digest()); } catch (CloneNotSupportedException e) { throw new RuntimeException(e); }
                case "digest": try { return new Oop.Bytes(((MessageDigest) md.clone()).digest(), false); } catch (CloneNotSupportedException e) { throw new RuntimeException(e); }
                case "copy": try { return new Hash(name, (MessageDigest) md.clone()); } catch (CloneNotSupportedException e) { throw new RuntimeException(e); }
                case "@name": return name.toLowerCase().replace("-", "");
                case "@digest_size": return (long) md.getDigestLength();
            }
            throw new Py.PyError("AttributeError", "'_hashlib.HASH' object has no attribute '" + n + "'");
        }
    }

    // ------------------------------------------------------------ base64 / uuid

    static Object base64(String fn, Object[] a) {
        byte[] in = fn.endsWith("decode") && arg(a, 0) instanceof String ? Py.str(arg(a, 0)).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1) : bytesArg(arg(a, 0));
        try {
            switch (fn) {
                case "b64encode": return new Oop.Bytes(Base64.getEncoder().encode(in), false);
                case "b64decode": return new Oop.Bytes(Base64.getMimeDecoder().decode(in), false);
                case "urlsafe_b64encode": return new Oop.Bytes(Base64.getUrlEncoder().encode(in), false);
                case "urlsafe_b64decode": return new Oop.Bytes(Base64.getUrlDecoder().decode(in), false);
                case "b16encode": return new Oop.Bytes(Hash.hex(in).toUpperCase().getBytes(), false);
                case "b16decode": {
                    String s = new String(in); byte[] o = new byte[s.length() / 2];
                    for (int i = 0; i < o.length; i++) o[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
                    return new Oop.Bytes(o, false);
                }
            }
        } catch (IllegalArgumentException e) {
            throw new Py.PyError("ValueError", "Invalid base64-encoded string");
        }
        throw new Py.PyError("AttributeError", "module 'base64' has no attribute '" + fn + "'");
    }

    static Object uuid(String fn, Object[] a) {
        if (fn.equals("uuid4") || fn.equals("uuid1")) return UUID.randomUUID().toString();
        throw new Py.PyError("AttributeError", "module 'uuid' has no attribute '" + fn + "'");
    }

    // ------------------------------------------------------------ csv

    static List<String> parseRow(String line, char delim, char quote) {
        List<String> row = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean inq = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inq) {
                if (c == quote) {
                    if (i + 1 < line.length() && line.charAt(i + 1) == quote) { cur.append(quote); i++; } else inq = false;
                } else cur.append(c);
            } else if (c == quote) inq = true;
            else if (c == delim) { row.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        row.add(cur.toString());
        return row;
    }

    static List<List<String>> readAll(Object src, char delim, char quote) {
        List<List<String>> rows = new ArrayList<List<String>>();
        StringBuilder pending = null;
        for (Object o : Py.iter(src)) {
            String line = Py.str(o);
            while (line.endsWith("\n") || line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            if (pending != null) { pending.append("\n").append(line); line = pending.toString(); }
            int q = 0;
            for (int i = 0; i < line.length(); i++) if (line.charAt(i) == quote) q++;
            if (q % 2 == 1) { pending = new StringBuilder(line); continue; }
            pending = null;
            if (line.isEmpty()) { rows.add(new ArrayList<String>()); continue; }
            rows.add(parseRow(line, delim, quote));
        }
        if (pending != null) rows.add(parseRow(pending.toString(), delim, quote));
        return rows;
    }

    static String cell(Object v, char delim, char quote, long quoting) {
        String s = v == null ? "" : Py.str(v);
        boolean need = quoting == 1 || (quoting == 2 && !(v instanceof Number)) || s.indexOf(delim) >= 0 || s.indexOf(quote) >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (quoting == 3) need = false;
        if (!need) return s;
        return quote + s.replace(String.valueOf(quote), "" + quote + quote) + quote;
    }

    static char ch(Object[] a, String k, char d) { Object v = kw(a, k, null); return v == null ? d : Py.str(v).charAt(0); }

    static Object csv(String fn, Object[] a) {
        final char delim = ch(a, "delimiter", ','), quote = ch(a, "quotechar", '"');
        final long quoting = L(kw(a, "quoting", 0L));
        switch (fn) {
            case "reader": {
                List<Object> out = new ArrayList<Object>();
                for (List<String> r : readAll(arg(a, 0), delim, quote)) out.add(new ArrayList<Object>(r));
                return listIt(out);
            }
            case "DictReader": {
                List<List<String>> rows = readAll(arg(a, 0), delim, quote);
                Object fnames = kw(a, "fieldnames", null);
                List<Object> names = new ArrayList<Object>();
                int start = 0;
                if (fnames != null) names = Py.toList(fnames);
                else { while (start < rows.size() && rows.get(start).isEmpty()) start++; if (start < rows.size()) names = new ArrayList<Object>(rows.get(start++)); }
                List<Object> out = new ArrayList<Object>();
                for (int i = start; i < rows.size(); i++) {
                    if (rows.get(i).isEmpty()) continue;
                    Map<Object, Object> m = new LinkedHashMap<Object, Object>();
                    for (int j = 0; j < names.size(); j++) m.put(names.get(j), j < rows.get(i).size() ? rows.get(i).get(j) : null);
                    out.add(m);
                }
                return listIt(out);
            }
            case "writer": return new CsvWriter(arg(a, 0), delim, quote, quoting, null, false);
            case "DictWriter": return new CsvWriter(arg(a, 0), delim, quote, quoting, Py.toList(kw(a, "fieldnames", a.length > 1 ? a[1] : null)), true);
        }
        throw new Py.PyError("AttributeError", "module 'csv' has no attribute '" + fn + "'");
    }

    static final class CsvWriter implements Py.Obj {
        final Object f; final char delim, quote; final long quoting; final List<Object> names; final boolean dict;
        CsvWriter(Object f, char d, char q, long qt, List<Object> names, boolean dict) { this.f = f; delim = d; quote = q; quoting = qt; this.names = names; this.dict = dict; }
        void row(List<Object> vals) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < vals.size(); i++) { if (i > 0) s.append(delim); s.append(cell(vals.get(i), delim, quote, quoting)); }
            s.append("\r\n");
            Py.m(f, "write", new Object[]{s.toString()});
        }
        List<Object> fromDict(Object o) {
            List<Object> r = new ArrayList<Object>();
            Map<Object, Object> m = (Map<Object, Object>) o;
            for (Object n : names) r.add(m.get(Py.key(n)));
            return r;
        }
        public Object callm(String n, Object[] a) {
            switch (n) {
                case "writeheader": row(names); return null;
                case "writerow": row(dict ? fromDict(arg(a, 0)) : Py.toList(arg(a, 0))); return null;
                case "writerows": for (Object o : Py.iter(arg(a, 0))) row(dict ? fromDict(o) : Py.toList(o)); return null;
            }
            throw new Py.PyError("AttributeError", "'_csv.writer' object has no attribute '" + n + "'");
        }
    }
}

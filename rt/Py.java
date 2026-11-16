package apple.rt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python semantics for transpiled programs. Pure Java (no Android classes) so it
 * can be unit-tested on a desktop JVM. Ints are Long, floats Double, str String,
 * list/tuple ArrayList, dict LinkedHashMap, set LinkedHashSet, None null.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class Py {
    private Py() {}

    /** Non-constant true: stops javac from flagging "unreachable code" after return/raise. */
    public static boolean T = true;
    public static boolean F = false;
    /** #use: fppe -- float results are computed as the decimals the programmer meant. */
    public static boolean FPPE = false;
    public static final Object NOT_IMPLEMENTED = new Object() { public String toString() { return "NotImplemented"; } };
    public static final Object MISSING = new Object() { public String toString() { return "<missing>"; } };
    /** Stand-in for an instance of a (flattened) user class. */
    public static final Object APP = new Object() { public String toString() { return "<app>"; } };
    /** The Tk root, set by Ui.init. APP method calls are forwarded here. */
    public static Obj root;

    public interface Obj { Object callm(String name, Object[] args); }

    /** Receives everything the program prints (set by the debug panel; null normally). */
    public interface Sink { void out(String s, boolean err); }
    public static Sink sink;

    /** Function with default-argument values captured at definition time. */
    public abstract static class DFn implements Fn {
        public final Object[] d;
        public DFn(Object[] d) { this.d = d; }
    }

    public static final class Kw {
        public final String k;
        public final Object v;
        Kw(String k, Object v) { this.k = k; this.v = v; }
    }
    public static Kw kw(String k, Object v) { return new Kw(k, v); }

    /** Immutable-by-convention sequence; only differs from list in how it prints. */
    public static final class Tuple extends ArrayList<Object> {
        public Tuple(java.util.Collection<?> c) { super(c); }
    }

    public static Object tupleOf(Object... xs) { return new Tuple(Arrays.asList(xs)); }

    public static final class Sl {
        public final Object lo, hi, st;
        Sl(Object lo, Object hi, Object st) { this.lo = lo; this.hi = hi; this.st = st; }
    }
    public static Object slice(Object lo, Object hi, Object st) { return new Sl(lo, hi, st); }

    // ------------------------------------------------------------------ errors

    public static final class PyError extends RuntimeException {
        public final String type;
        public PyError(String type, String msg) { super(msg == null ? "" : msg); this.type = type; }
        public boolean matches(String... types) {
            for (String t : types) {
                if (t.equals("BaseException")) return true;
                if (t.equals("Exception") && !type.equals("SystemExit") && !type.equals("KeyboardInterrupt")) return true;
                for (String c = type; c != null; c = PARENT.get(c)) if (c.equals(t)) return true;
            }
            return false;
        }
        @Override public String toString() {
            String m = getMessage();
            return (m == null || m.isEmpty()) ? type : type + ": " + m;
        }
    }

    static final Map<String, String> PARENT = new java.util.HashMap<String, String>();
    static {
        String[][] p = {{"KeyError", "LookupError"}, {"IndexError", "LookupError"}, {"ZeroDivisionError", "ArithmeticError"},
            {"OverflowError", "ArithmeticError"}, {"FileNotFoundError", "OSError"}, {"PermissionError", "OSError"},
            {"IsADirectoryError", "OSError"}, {"FileExistsError", "OSError"}, {"JSONDecodeError", "ValueError"},
            {"UnicodeError", "ValueError"}, {"NotImplementedError", "RuntimeError"}, {"RecursionError", "RuntimeError"},
            {"StopIteration", "Exception"}};
        for (String[] x : p) PARENT.put(x[0], x[1]);
    }
    public static void defExc(String name, String parent) { PARENT.put(name, parent); }

    public static PyError err(String type, String msg) { return new PyError(type, msg); }

    public static PyError wrap(Throwable t) {
        if (t instanceof PyError) return (PyError) t;
        if (t instanceof ArithmeticException) return new PyError("ZeroDivisionError", "division by zero");
        if (t instanceof NumberFormatException) return new PyError("ValueError", String.valueOf(t.getMessage()));
        if (t instanceof IndexOutOfBoundsException) return new PyError("IndexError", "index out of range");
        if (t instanceof ClassCastException) return new PyError("TypeError", "unsupported operand type");
        if (t instanceof NullPointerException) return new PyError("TypeError", "NoneType has no such attribute or value");
        return new PyError("RuntimeError", String.valueOf(t));
    }

    public static PyError exc(String type, Object... args) {
        return new PyError(type, args.length > 0 ? str(args[0]) : "");
    }

    public static RuntimeException raise(Object e) {
        if (e instanceof RuntimeException) return (RuntimeException) e;
        return new PyError("TypeError", "exceptions must derive from BaseException");
    }

    public static void assertTrue(boolean c, Object msg) {
        if (!c) throw new PyError("AssertionError", msg == null ? "" : str(msg));
    }

    // ----------------------------------------------------------------- numbers

    static boolean isNum(Object o) { return o instanceof Number || o instanceof Boolean; }

    static Object norm(Object o) {
        if (o instanceof Boolean) return ((Boolean) o) ? 1L : 0L;
        if (o instanceof Integer || o instanceof Short || o instanceof Byte) return ((Number) o).longValue();
        if (o instanceof Float) return ((Number) o).doubleValue();
        return o;
    }

    static double dbl(Object o) { return ((Number) norm(o)).doubleValue(); }

    static String tn(Object o) {
        if (o == null) return "NoneType";
        if (o instanceof Oop.Inst) return ((Oop.Inst) o).cls.name;
        if (o instanceof Oop.Cls) return "type";
        if (o instanceof Oop.Bytes) return ((Oop.Bytes) o).mutable ? "bytearray" : "bytes";
        if (o instanceof Oop.Generator) return "generator";
        if (o instanceof String) return "str";
        if (o instanceof Long) return "int";
        if (o instanceof Double) return "float";
        if (o instanceof Boolean) return "bool";
        if (o instanceof Tuple) return "tuple";
        if (o instanceof List) return "list";
        if (o instanceof Map) return "dict";
        if (o instanceof Set) return "set";
        if (o instanceof Fn) return "function";
        if (o instanceof PyError) return ((PyError) o).type;
        return o.getClass().getSimpleName();
    }

    static PyError opErr(String op, Object a, Object b) {
        return new PyError("TypeError", "unsupported operand type(s) for " + op + ": '" + tn(a) + "' and '" + tn(b) + "'");
    }

    static BigDecimal dec(double d) { return new BigDecimal(fmtDouble(d)); }

    /** Decimal arithmetic on the shortest representations of the operands (0.1 + 0.2 -> 0.3). */
    static double fp(char op, double x, double y) {
        if (Double.isNaN(x) || Double.isInfinite(x) || Double.isNaN(y) || Double.isInfinite(y)) {
            switch (op) {
                case '+': return x + y;
                case '-': return x - y;
                case '*': return x * y;
                case '/': return x / y;
                case 'f': return Math.floor(x / y);
                default: return x - y * Math.floor(x / y);
            }
        }
        BigDecimal bx = dec(x), by = dec(y);
        try {
            switch (op) {
                case '+': return bx.add(by).doubleValue();
                case '-': return bx.subtract(by).doubleValue();
                case '*': return bx.multiply(by).doubleValue();
                case '/': return bx.divide(by).doubleValue();       // exact only; else plain double below
                case 'f': return bx.divide(by, 0, RoundingMode.FLOOR).doubleValue();
                default: {
                    BigDecimal q = bx.divide(by, 0, RoundingMode.FLOOR);
                    return bx.subtract(by.multiply(q)).doubleValue();
                }
            }
        } catch (ArithmeticException e) {
            return x / y;
        }
    }

    public static Object add(Object a, Object b) {
        if (a instanceof Oop.Inst || b instanceof Oop.Inst) return Oop.bin("add", a, b);
        if (a instanceof Oop.Bytes && b instanceof Oop.Bytes) return Oop.bytesAdd((Oop.Bytes) a, (Oop.Bytes) b);
        if (a instanceof String && b instanceof String) return (String) a + (String) b;
        if (a instanceof List && b instanceof List) {
            List r = new ArrayList((List) a);
            r.addAll((List) b);
            return (a instanceof Tuple) ? new Tuple(r) : r;
        }
        if (isNum(a) && isNum(b)) {
            a = norm(a); b = norm(b);
            if (a instanceof Long && b instanceof Long) return (Long) a + (Long) b;
            return FPPE ? fp('+', dbl(a), dbl(b)) : dbl(a) + dbl(b);
        }
        throw opErr("+", a, b);
    }

    public static Object sub(Object a, Object b) {
        if (a instanceof Oop.Inst || b instanceof Oop.Inst) return Oop.bin("sub", a, b);
        if (isNum(a) && isNum(b)) {
            a = norm(a); b = norm(b);
            if (a instanceof Long && b instanceof Long) return (Long) a - (Long) b;
            return FPPE ? fp('-', dbl(a), dbl(b)) : dbl(a) - dbl(b);
        }
        if (a instanceof Set && b instanceof Set) {
            Set r = new LinkedHashSet((Set) a);
            r.removeAll((Set) b);
            return r;
        }
        throw opErr("-", a, b);
    }

    public static Object mul(Object a, Object b) {
        if (a instanceof Oop.Inst || b instanceof Oop.Inst) return Oop.bin("mul", a, b);
        if (a instanceof Oop.Bytes && b instanceof Long) return Oop.bytesMul((Oop.Bytes) a, (Long) b);
        if (isNum(a) && isNum(b)) {
            a = norm(a); b = norm(b);
            if (a instanceof Long && b instanceof Long) return (Long) a * (Long) b;
            return FPPE ? fp('*', dbl(a), dbl(b)) : dbl(a) * dbl(b);
        }
        if (isNum(b) && !isNum(a)) { Object t = a; a = b; b = t; }
        if (isNum(a) && norm(a) instanceof Long) {
            long n = Math.max(0, (Long) norm(a));
            if (b instanceof String) {
                StringBuilder sb = new StringBuilder();
                for (long i = 0; i < n; i++) sb.append((String) b);
                return sb.toString();
            }
            if (b instanceof List) {
                List r = new ArrayList();
                for (long i = 0; i < n; i++) r.addAll((List) b);
                return r;
            }
        }
        throw opErr("*", a, b);
    }

    public static Object div(Object a, Object b) {
        if (a instanceof Oop.Inst || b instanceof Oop.Inst) return Oop.bin("div", a, b);
        if (isNum(a) && isNum(b)) {
            double d = dbl(b);
            if (d == 0.0) throw new PyError("ZeroDivisionError", "division by zero");
            return FPPE ? fp('/', dbl(a), d) : dbl(a) / d;
        }
        throw opErr("/", a, b);
    }

    public static Object floordiv(Object a, Object b) {
        if (a instanceof Oop.Inst || b instanceof Oop.Inst) return Oop.bin("floordiv", a, b);
        if (isNum(a) && isNum(b)) {
            a = norm(a); b = norm(b);
            if (a instanceof Long && b instanceof Long) {
                if ((Long) b == 0L) throw new PyError("ZeroDivisionError", "integer division or modulo by zero");
                return Math.floorDiv((Long) a, (Long) b);
            }
            double d = dbl(b);
            if (d == 0.0) throw new PyError("ZeroDivisionError", "float floor division by zero");
            return FPPE ? fp('f', dbl(a), d) : Math.floor(dbl(a) / d);
        }
        throw opErr("//", a, b);
    }

    public static Object mod(Object a, Object b) {
        if (a instanceof Oop.Inst || b instanceof Oop.Inst) return Oop.bin("mod", a, b);
        if (a instanceof String) return pfmt((String) a, b);
        if (isNum(a) && isNum(b)) {
            a = norm(a); b = norm(b);
            if (a instanceof Long && b instanceof Long) {
                if ((Long) b == 0L) throw new PyError("ZeroDivisionError", "integer division or modulo by zero");
                return Math.floorMod((Long) a, (Long) b);
            }
            double x = dbl(a), y = dbl(b);
            if (y == 0.0) throw new PyError("ZeroDivisionError", "float modulo");
            return FPPE ? fp('%', x, y) : x - y * Math.floor(x / y);
        }
        throw opErr("%", a, b);
    }

    public static Object pow(Object a, Object b) {
        if (a instanceof Oop.Inst || b instanceof Oop.Inst) return Oop.bin("pow", a, b);
        if (isNum(a) && isNum(b)) {
            a = norm(a); b = norm(b);
            if (a instanceof Long && b instanceof Long && (Long) b >= 0) {
                long r = 1, base = (Long) a;
                for (long i = 0; i < (Long) b; i++) r *= base;
                return r;
            }
            return Math.pow(dbl(a), dbl(b));
        }
        throw opErr("** or pow()", a, b);
    }

    public static Object neg(Object a) {
        if (a instanceof Oop.Inst) return Oop.neg((Oop.Inst) a);
        if (isNum(a)) {
            a = norm(a);
            if (a instanceof Long) return -(Long) a;
            return -(Double) a;
        }
        throw new PyError("TypeError", "bad operand type for unary -: '" + tn(a) + "'");
    }

    public static Object bit(String op, Object a, Object b) {
        if (a instanceof Oop.Inst || b instanceof Oop.Inst) return Oop.bin(op, a, b);
        long x = toLong(a);
        switch (op) {
            case "~": return ~x;
            case "&": return x & toLong(b);
            case "|": return x | toLong(b);
            case "^": return x ^ toLong(b);
            case "<<": return x << toLong(b);
            case ">>": return x >> toLong(b);
            default: throw new PyError("TypeError", "bad bit operator " + op);
        }
    }

    static long toLong(Object o) {
        o = norm(o);
        if (o instanceof Long) return (Long) o;
        throw new PyError("TypeError", "an integer is required");
    }

    // ------------------------------------------------------------- comparisons

    public static boolean truth(Object o) {
        if (o == null) return false;
        if (o instanceof Oop.Inst) return Oop.truth((Oop.Inst) o);
        if (o instanceof Oop.Bytes) return ((Oop.Bytes) o).size() > 0;
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Long) return (Long) o != 0L;
        if (o instanceof Double) return (Double) o != 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue() != 0.0;
        if (o instanceof String) return !((String) o).isEmpty();
        if (o instanceof List) return !((List) o).isEmpty();
        if (o instanceof Map) return !((Map) o).isEmpty();
        if (o instanceof Set) return !((Set) o).isEmpty();
        return true;
    }

    public static boolean eq(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (isNum(a) && isNum(b)) {
            a = norm(a); b = norm(b);
            if (a instanceof Long && b instanceof Long) return ((Long) a).longValue() == ((Long) b).longValue();
            return dbl(a) == dbl(b);
        }
        if (a instanceof List && b instanceof List) {
            List x = (List) a, y = (List) b;
            if (x.size() != y.size()) return false;
            for (int i = 0; i < x.size(); i++) if (!eq(x.get(i), y.get(i))) return false;
            return true;
        }
        return a.equals(b);
    }

    public static boolean is(Object a, Object b) {
        if (a == b) return true;
        if (a instanceof Boolean && b instanceof Boolean) return a.equals(b);
        return false;
    }

    public static int cmp(Object a, Object b) {
        if (a instanceof Oop.Inst || b instanceof Oop.Inst) return Oop.cmp(a, b);
        if (isNum(a) && isNum(b)) {
            a = norm(a); b = norm(b);
            if (a instanceof Long && b instanceof Long) return Long.compare((Long) a, (Long) b);
            return Double.compare(dbl(a), dbl(b));
        }
        if (a instanceof String && b instanceof String) return Integer.signum(((String) a).compareTo((String) b));
        if (a instanceof List && b instanceof List) {
            List x = (List) a, y = (List) b;
            int n = Math.min(x.size(), y.size());
            for (int i = 0; i < n; i++) {
                int c = cmp(x.get(i), y.get(i));
                if (c != 0) return c;
            }
            return Integer.compare(x.size(), y.size());
        }
        throw new PyError("TypeError", "'<' not supported between instances of '" + tn(a) + "' and '" + tn(b) + "'");
    }

    public static boolean contains(Object container, Object item) {
        if (container instanceof Oop.Inst) return Oop.contains((Oop.Inst) container, item);
        if (container instanceof Oop.Cls) { for (Object m : iter(container)) if (m == item) return true; return false; }
        if (container instanceof Oop.Bytes) {
            Oop.Bytes bb = (Oop.Bytes) container;
            if (item instanceof Oop.Bytes) return Oop.indexOf(bb.b, (Oop.Bytes) item) >= 0;
            for (Object x : bb) if (eq(x, item)) return true;
            return false;
        }
        if (container instanceof String) {
            if (!(item instanceof String)) throw new PyError("TypeError", "'in <string>' requires string as left operand");
            return ((String) container).contains((String) item);
        }
        if (container instanceof List) {
            for (Object o : (List) container) if (eq(o, item)) return true;
            return false;
        }
        if (container instanceof Map) return ((Map) container).containsKey(key(item));
        if (container instanceof Set) return ((Set) container).contains(key(item));
        throw new PyError("TypeError", "argument of type '" + tn(container) + "' is not iterable");
    }

    public static Object key(Object k) {
        k = norm(k);
        if (k instanceof Double && (Double) k == Math.rint((Double) k) && Math.abs((Double) k) < 9e15) return (long) (double) (Double) k;
        return k;
    }

    // ----------------------------------------------------------------- strings

    public static String str(Object o) {
        if (o == null) return "None";
        if (o instanceof String) return (String) o;
        if (o instanceof PyError) return ((PyError) o).getMessage();
        if (o instanceof Oop.Inst) return ((Oop.Inst) o).str();
        return repr(o);
    }

    public static String repr(Object o) {
        if (o == null) return "None";
        if (o instanceof String) {
            String s = (String) o;
            char q = (s.indexOf('\'') >= 0 && s.indexOf('"') < 0) ? '"' : '\'';
            StringBuilder sb = new StringBuilder().append(q);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == q || c == '\\') sb.append('\\').append(c);
                else if (c == '\n') sb.append("\\n");
                else if (c == '\t') sb.append("\\t");
                else if (c == '\r') sb.append("\\r");
                else sb.append(c);
            }
            return sb.append(q).toString();
        }
        if (o instanceof Boolean) return ((Boolean) o) ? "True" : "False";
        if (o instanceof Double) return fmtDouble((Double) o);
        if (o instanceof Number) return String.valueOf(((Number) o).longValue());
        if (o instanceof Tuple) {
            Tuple t = (Tuple) o;
            StringBuilder sb = new StringBuilder("(");
            boolean first = true;
            for (Object x : t) { if (!first) sb.append(", "); first = false; sb.append(repr(x)); }
            if (t.size() == 1) sb.append(',');
            return sb.append(')').toString();
        }
        if (o instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object x : (List) o) { if (!first) sb.append(", "); first = false; sb.append(repr(x)); }
            return sb.append(']').toString();
        }
        if (o instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Object e : ((Map) o).entrySet()) {
                Map.Entry en = (Map.Entry) e;
                if (!first) sb.append(", "); first = false;
                sb.append(repr(en.getKey())).append(": ").append(repr(en.getValue()));
            }
            return sb.append('}').toString();
        }
        if (o instanceof Set) {
            if (((Set) o).isEmpty()) return "set()";
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Object x : (Set) o) { if (!first) sb.append(", "); first = false; sb.append(repr(x)); }
            return sb.append('}').toString();
        }
        if (o instanceof PyError) { String m = ((PyError) o).getMessage(); return ((PyError) o).type + "(" + (m.isEmpty() ? "" : repr(m)) + ")"; }
        if (o instanceof Fn) return "<function>";
        return String.valueOf(o);
    }

    public static String fmtDouble(double d) {
        if (Double.isNaN(d)) return "nan";
        if (Double.isInfinite(d)) return d > 0 ? "inf" : "-inf";
        if (d == Math.rint(d) && Math.abs(d) < 1e16) {
            if (d == 0.0 && 1.0 / d < 0) return "-0.0";
            return String.valueOf((long) d) + ".0";
        }
        String s = Double.toString(d);
        if (s.indexOf('E') < 0) return s;
        double ad = Math.abs(d);
        if (ad >= 1e-4 && ad < 1e16) {
            String p = new BigDecimal(s).toPlainString();
            if (p.indexOf('.') < 0) p += ".0";
            return p;
        }
        int ei = s.indexOf('E');
        String mant = s.substring(0, ei);
        int e = Integer.parseInt(s.substring(ei + 1));
        if (mant.endsWith(".0")) mant = mant.substring(0, mant.length() - 2);
        return mant + "e" + (e < 0 ? "-" : "+") + (Math.abs(e) < 10 ? "0" : "") + Math.abs(e);
    }

    public static String cat(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) sb.append(str(p));
        return sb.toString();
    }

    private static final Pattern FMT = Pattern.compile(
            "^(?:(.)?([<>^=]))?([+\\- ])?(#)?(0)?(\\d+)?([,_])?(?:\\.(\\d+))?([bcdeEfFgGnosxX%])?$");

    public static String fmt(Object v, Object specObj) {
        String spec = specObj == null ? "" : str(specObj);
        if (spec.isEmpty()) return str(v);
        Matcher m = FMT.matcher(spec);
        if (!m.matches()) throw new PyError("ValueError", "Invalid format specifier '" + spec + "'");
        String fill = m.group(1), align = m.group(2), sign = m.group(3), zero = m.group(5);
        String width = m.group(6), comma = m.group(7), prec = m.group(8), type = m.group(9);
        Object n = norm(v);
        String body;
        boolean numeric = n instanceof Long || n instanceof Double;
        if (!numeric || "s".equals(type)) {
            body = str(v);
            if (prec != null && body.length() > Integer.parseInt(prec)) body = body.substring(0, Integer.parseInt(prec));
            if (align == null) align = "<";
        } else {
            if (type == null) type = (n instanceof Long) ? "d" : (prec == null ? "r" : "g");
            if (type.equals("d") || type.equals("n")) {
                if (n instanceof Double) throw new PyError("ValueError", "Unknown format code 'd' for object of type 'float'");
                long x = (Long) n;
                body = (comma != null ? String.format("%,d", Math.abs(x)) : Long.toString(Math.abs(x)));
                if (x < 0) body = "-" + body; else if (sign != null && !sign.equals("-")) body = sign + body;
            } else if (type.equals("x") || type.equals("X") || type.equals("o") || type.equals("b") || type.equals("c")) {
                long x = toLong(n);
                String t = type.equals("b") ? Long.toBinaryString(Math.abs(x))
                        : type.equals("o") ? Long.toOctalString(Math.abs(x))
                        : type.equals("c") ? new String(Character.toChars((int) x))
                        : Long.toHexString(Math.abs(x));
                if (type.equals("X")) t = t.toUpperCase();
                body = (x < 0 ? "-" : (sign != null && !sign.equals("-") ? sign : "")) + (m.group(4) != null ? (type.equals("b") ? "0b" : type.equals("o") ? "0o" : "0x") : "") + t;
            } else if (type.equals("r")) {
                body = fmtDouble(dbl(n));
                if (sign != null && !sign.equals("-") && dbl(n) >= 0) body = sign + body;
            } else {
                double x = dbl(n);
                int p = prec == null ? 6 : Integer.parseInt(prec);
                String t;
                switch (type) {
                    case "f": case "F": t = String.format(java.util.Locale.ROOT, comma != null ? "%,." + p + "f" : "%." + p + "f", x); break;
                    case "e": case "E": t = String.format(java.util.Locale.ROOT, "%." + p + "e", x); t = t.replaceFirst("e([+-])(\\d)$", "e$10$2"); if (type.equals("E")) t = t.toUpperCase(); break;
                    case "%": t = String.format(java.util.Locale.ROOT, comma != null ? "%,." + p + "f" : "%." + p + "f", x * 100) + "%"; break;
                    default: // g
                        t = new BigDecimal(x).round(new java.math.MathContext(Math.max(p, 1))).stripTrailingZeros().toPlainString();
                        break;
                }
                body = t;
                if (x >= 0 && sign != null && !sign.equals("-")) body = sign + body;
            }
            if (align == null) align = (zero != null) ? "=" : ">";
            if (zero != null && fill == null) fill = "0";
        }
        if (fill == null) fill = " ";
        if (width != null) {
            int w = Integer.parseInt(width), len = body.length();
            if (len < w) {
                int pad = w - len;
                String f = fill;
                switch (align) {
                    case "<": body = body + rep(f, pad); break;
                    case "^": body = rep(f, pad / 2) + body + rep(f, pad - pad / 2); break;
                    case "=": {
                        int si = (body.startsWith("-") || body.startsWith("+") || body.startsWith(" ")) ? 1 : 0;
                        body = body.substring(0, si) + rep(f, pad) + body.substring(si);
                        break;
                    }
                    default: body = rep(f, pad) + body;
                }
            }
        }
        return body;
    }

    private static String rep(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    /** printf-style: "%d items, %.2f" % (3, 1.5) */
    static String pfmt(String f, Object arg) {
        List args = arg instanceof List ? (List) arg : Collections.singletonList(arg);
        StringBuilder out = new StringBuilder();
        int ai = 0;
        for (int i = 0; i < f.length(); i++) {
            char c = f.charAt(i);
            if (c != '%') { out.append(c); continue; }
            int j = i + 1;
            if (j < f.length() && f.charAt(j) == '%') { out.append('%'); i = j; continue; }
            StringBuilder flags = new StringBuilder();
            while (j < f.length() && "-+ 0#".indexOf(f.charAt(j)) >= 0) flags.append(f.charAt(j++));
            StringBuilder w = new StringBuilder();
            while (j < f.length() && Character.isDigit(f.charAt(j))) w.append(f.charAt(j++));
            String prec = null;
            if (j < f.length() && f.charAt(j) == '.') {
                j++;
                StringBuilder p = new StringBuilder();
                while (j < f.length() && Character.isDigit(f.charAt(j))) p.append(f.charAt(j++));
                prec = p.length() == 0 ? "0" : p.toString();
            }
            if (j >= f.length()) throw new PyError("ValueError", "incomplete format");
            char t = f.charAt(j);
            if (ai >= args.size()) throw new PyError("TypeError", "not enough arguments for format string");
            Object v = args.get(ai++);
            String s;
            switch (t) {
                case 'd': case 'i': case 'u': s = String.format(java.util.Locale.ROOT, "%" + flags + w + "d", (long) dbl(v)); break;
                case 'f': case 'F': s = String.format(java.util.Locale.ROOT, "%" + flags + w + "." + (prec == null ? "6" : prec) + "f", dbl(v)); break;
                case 'e': case 'E': case 'g': case 'G': s = String.format(java.util.Locale.ROOT, "%" + flags + w + (prec == null ? "" : "." + prec) + t, dbl(v)); break;
                case 'x': case 'X': case 'o': s = String.format(java.util.Locale.ROOT, "%" + flags + w + t, toLong(v)); break;
                case 'r': s = String.format("%" + flags + w + "s", repr(v)); break;
                case 'c': s = String.format("%" + flags + w + "s", v instanceof String ? v : new String(Character.toChars((int) toLong(v)))); break;
                default: {
                    String sv = str(v);
                    if (prec != null && sv.length() > Integer.parseInt(prec)) sv = sv.substring(0, Integer.parseInt(prec));
                    s = String.format("%" + flags.toString().replace("0", "") + w + "s", sv);
                }
            }
            out.append(s);
            i = j;
        }
        return out.toString();
    }

    /** "{} {name:>5}".format(...) */
    static String strFormat(String f, Object[] a) {
        List<Object> pos = new ArrayList<>();
        Map<String, Object> named = new LinkedHashMap<>();
        for (Object x : a) {
            if (x instanceof Kw) named.put(((Kw) x).k, ((Kw) x).v); else pos.add(x);
        }
        StringBuilder out = new StringBuilder();
        int auto = 0;
        for (int i = 0; i < f.length(); i++) {
            char c = f.charAt(i);
            if (c == '{') {
                if (i + 1 < f.length() && f.charAt(i + 1) == '{') { out.append('{'); i++; continue; }
                int close = f.indexOf('}', i);
                if (close < 0) throw new PyError("ValueError", "Single '{' encountered in format string");
                String field = f.substring(i + 1, close);
                String spec = "", conv = null;
                int ci = field.indexOf(':');
                if (ci >= 0) { spec = field.substring(ci + 1); field = field.substring(0, ci); }
                int bi = field.indexOf('!');
                if (bi >= 0) { conv = field.substring(bi + 1); field = field.substring(0, bi); }
                Object v;
                if (field.isEmpty()) {
                    if (auto >= pos.size()) throw new PyError("IndexError", "Replacement index " + auto + " out of range");
                    v = pos.get(auto++);
                } else if (Character.isDigit(field.charAt(0))) {
                    int idx = Integer.parseInt(field);
                    if (idx >= pos.size()) throw new PyError("IndexError", "Replacement index " + idx + " out of range");
                    v = pos.get(idx);
                } else {
                    if (!named.containsKey(field)) throw new PyError("KeyError", "'" + field + "'");
                    v = named.get(field);
                }
                if ("r".equals(conv)) v = repr(v);
                out.append(fmt(v, spec));
                i = close;
            } else if (c == '}') {
                if (i + 1 < f.length() && f.charAt(i + 1) == '}') i++;
                out.append('}');
            } else out.append(c);
        }
        return out.toString();
    }

    // ----------------------------------------------------------- conversions

    public static Object toInt(Object o) {
        o = norm(o);
        if (o instanceof Long) return o;
        if (o instanceof Double) {
            double d = (Double) o;
            if (Double.isNaN(d) || Double.isInfinite(d)) throw new PyError("ValueError", "cannot convert float " + fmtDouble(d) + " to integer");
            return (long) d;
        }
        if (o instanceof String) {
            String s = ((String) o).trim().replace("_", "");
            try { return Long.parseLong(s.startsWith("+") ? s.substring(1) : s); }
            catch (NumberFormatException e) { throw new PyError("ValueError", "invalid literal for int() with base 10: " + repr(o)); }
        }
        throw new PyError("TypeError", "int() argument must be a string or a number, not '" + tn(o) + "'");
    }

    public static Object toFloat(Object o) {
        o = norm(o);
        if (o instanceof Double) return o;
        if (o instanceof Long) return ((Long) o).doubleValue();
        if (o instanceof String) {
            String s = ((String) o).trim();
            String l = s.toLowerCase();
            if (l.equals("nan") || l.equals("inf") || l.equals("-inf") || l.equals("infinity") || l.equals("-infinity") || l.equals("+inf")) {
                return l.equals("nan") ? Double.NaN : (l.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
            }
            if (!s.matches("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")) throw new PyError("ValueError", "could not convert string to float: " + repr(o));
            return Double.parseDouble(s);
        }
        throw new PyError("TypeError", "float() argument must be a string or a number, not '" + tn(o) + "'");
    }

    // ------------------------------------------------------------ containers

    public static Object listOf(Object... xs) { return new ArrayList<Object>(Arrays.asList(xs)); }

    public static Object setOf(Object... xs) {
        Set<Object> s = new LinkedHashSet<>();
        for (Object x : xs) s.add(key(x));
        return s;
    }

    public static Object dict(Object... kv) {
        Map<Object, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(key(kv[i]), kv[i + 1]);
        return m;
    }

    public static Object[] unpack(Object seq, int n) {
        List<Object> l = new ArrayList<>();
        for (Object o : iter(seq)) l.add(o);
        if (l.size() < n) throw new PyError("ValueError", "not enough values to unpack (expected " + n + ", got " + l.size() + ")");
        if (l.size() > n) throw new PyError("ValueError", "too many values to unpack (expected " + n + ")");
        return l.toArray();
    }

    public static Iterable<Object> iter(Object o) {
        if (o instanceof List) return new ArrayList<Object>((List) o);
        if (o instanceof String) {
            List<Object> r = new ArrayList<>();
            String s = (String) o;
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i);
                r.add(new String(Character.toChars(cp)));
                i += Character.charCount(cp);
            }
            return r;
        }
        if (o instanceof Map) return new ArrayList<Object>(((Map) o).keySet());
        if (o instanceof Set) return new ArrayList<Object>((Set) o);
        if (o instanceof Ext.PyFile) return ((Ext.PyFile) o).lines();
        if (o instanceof Oop.Inst || o instanceof Oop.Cls) return Oop.iter(o);
        if (o instanceof Iterable) return (Iterable<Object>) o;
        throw new PyError("TypeError", "'" + tn(o) + "' object is not iterable");
    }

    static List<Object> toList(Object o) {
        List<Object> r = new ArrayList<>();
        for (Object x : iter(o)) r.add(x);
        return r;
    }

    public static Object comp(Object src, Fn map, Fn pred) {
        List<Object> r = new ArrayList<>();
        for (Object x : iter(src)) {
            if (pred != null && !truth(pred.call(x))) continue;
            r.add(map.call(x));
        }
        return r;
    }

    static int norm(long i, int n) {
        if (i < 0) i += n;
        return (int) i;
    }

    public static Object idx(Object o, Object i) {
        if (o instanceof Oop.Inst) return Oop.idx((Oop.Inst) o, i);
        if (o instanceof Oop.Bytes) return Oop.bytesIdx((Oop.Bytes) o, i);
        if (o instanceof Oop.Cls) return Oop.clsIdx((Oop.Cls) o, i);
        if (i instanceof Sl) return sliceOf(o, (Sl) i);
        if (o instanceof Map) {
            Map m = (Map) o;
            Object k = key(i);
            if (!m.containsKey(k)) {
                if (m instanceof Ext.DDict) { Object v = ((Ext.DDict) m).make(); m.put(k, v); return v; }
                if (m instanceof Ext.Counter) return 0L;
                throw new PyError("KeyError", repr(i));
            }
            return m.get(k);
        }
        if (o instanceof Obj && !(o instanceof Ext.Event)) return ((Obj) o).callm("__getitem__", new Object[]{i});
        long n = toLong(i);
        if (o instanceof List) {
            List l = (List) o;
            int k = norm(n, l.size());
            if (k < 0 || k >= l.size()) throw new PyError("IndexError", "list index out of range");
            return l.get(k);
        }
        if (o instanceof String) {
            String s = (String) o;
            int k = norm(n, s.length());
            if (k < 0 || k >= s.length()) throw new PyError("IndexError", "string index out of range");
            return String.valueOf(s.charAt(k));
        }
        throw new PyError("TypeError", "'" + tn(o) + "' object is not subscriptable");
    }

    private static Object sliceOf(Object o, Sl s) {
        int n = o instanceof String ? ((String) o).length() : (o instanceof List ? ((List) o).size() : -1);
        if (n < 0) throw new PyError("TypeError", "'" + tn(o) + "' object is not subscriptable");
        long step = s.st == null ? 1 : toLong(s.st);
        if (step == 0) throw new PyError("ValueError", "slice step cannot be zero");
        long lo, hi;
        if (step > 0) {
            lo = s.lo == null ? 0 : clamp(norm(toLong(s.lo), n), 0, n);
            hi = s.hi == null ? n : clamp(norm(toLong(s.hi), n), 0, n);
        } else {
            lo = s.lo == null ? n - 1 : clamp(norm(toLong(s.lo), n), -1, n - 1);
            hi = s.hi == null ? -1 : clamp(norm(toLong(s.hi), n), -1, n - 1);
        }
        List<Integer> ix = new ArrayList<>();
        if (step > 0) for (long k = lo; k < hi; k += step) ix.add((int) k);
        else for (long k = lo; k > hi; k += step) ix.add((int) k);
        if (o instanceof String) {
            StringBuilder sb = new StringBuilder();
            for (int k : ix) sb.append(((String) o).charAt(k));
            return sb.toString();
        }
        List r = new ArrayList();
        for (int k : ix) r.add(((List) o).get(k));
        return (o instanceof Tuple) ? new Tuple(r) : r;
    }

    private static long clamp(long v, long lo, long hi) { return Math.max(lo, Math.min(hi, v)); }

    public static void setIdx(Object o, Object i, Object v) {
        if (o instanceof Oop.Inst) { Oop.setIdx((Oop.Inst) o, i, v); return; }
        if (o instanceof Oop.Bytes) { Oop.bytesSetIdx((Oop.Bytes) o, i, v); return; }
        if (o instanceof Map) { ((Map) o).put(key(i), v); return; }
        if (o instanceof Obj) { ((Obj) o).callm("__setitem__", new Object[]{i, v}); return; }
        if (o instanceof List) {
            List l = (List) o;
            int k = norm(toLong(i), l.size());
            if (k < 0 || k >= l.size()) throw new PyError("IndexError", "list assignment index out of range");
            l.set(k, v);
            return;
        }
        throw new PyError("TypeError", "'" + tn(o) + "' object does not support item assignment");
    }

    public static Object ignore(Object o) { return o; }

    public static Object and(Object a, Fn b) { return truth(a) ? b.call() : a; }
    public static Object or(Object a, Fn b) { return truth(a) ? a : b.call(); }

    public static Object call(Object f, Object... args) {
        if (f instanceof Fn) return ((Fn) f).call(args);
        if (f instanceof Oop.Inst) return Oop.callInst((Oop.Inst) f, args);
        throw new PyError("TypeError", "'" + tn(f) + "' object is not callable");
    }

    public static Object callApp(Fn init, Object... args) {
        if (init != null) init.call(args);
        return APP;
    }

    // --------------------------------------------------------------- variables

    /** A widget's own listener on a Var (skipped when that widget is the one that changed it). */
    public abstract static class WL implements Fn {
        public final Object owner;
        public WL(Object owner) { this.owner = owner; }
    }

    /** tkinter StringVar / IntVar / DoubleVar / BooleanVar */
    public static final class Var implements Obj {
        public final String kind;
        public Object val;
        public final List<Fn> listeners = new ArrayList<>();

        public Var(String kind, Object init) {
            this.kind = kind;
            this.val = coerce(init == null ? defaultFor(kind) : init, true);
        }

        static Object defaultFor(String k) {
            switch (k) {
                case "IntVar": return 0L;
                case "DoubleVar": return 0.0;
                case "BooleanVar": return Boolean.FALSE;
                default: return "";
            }
        }

        Object coerce(Object v, boolean strict) {
            try {
                switch (kind) {
                    case "IntVar": return toInt(v instanceof Double ? (Object) (long) (double) (Double) v : v);
                    case "DoubleVar": return toFloat(v);
                    case "BooleanVar": return v instanceof String ? Boolean.valueOf(!((String) v).isEmpty() && !((String) v).equals("0") && !((String) v).equalsIgnoreCase("false")) : Boolean.valueOf(truth(v));
                    default: return v == null ? "" : str(v);
                }
            } catch (PyError e) {
                if (strict) return defaultFor(kind);
                throw e;
            }
        }

        public Object get() { return val; }

        public void set(Object v) {
            val = coerce(v, false);
            fire();
        }

        /** Called when a widget's text changed; ignores text that is not a valid value. */
        public void setFromText(Object s, Object source) {
            try { val = coerce(s, false); fire(source); } catch (PyError e) { /* keep previous value */ }
        }

        void fire() { fire(null); }

        void fire(Object source) {
            for (Fn f : new ArrayList<>(listeners)) {
                if (source != null && f instanceof WL && ((WL) f).owner == source) continue;
                f.call();
            }
        }

        @Override public Object callm(String name, Object[] a) {
            switch (name) {
                case "get": return get();
                case "set": set(a.length > 0 ? a[0] : null); return null;
                case "trace_add": case "trace": case "trace_variable":
                    if (a.length > 0 && a[a.length - 1] instanceof Fn) listeners.add((Fn) a[a.length - 1]);
                    return null;
                default: throw new PyError("AttributeError", kind + " has no attribute '" + name + "'");
            }
        }

        @Override public String toString() { return str(val); }
    }

    public static Object var(String kind, Object init) { return new Var(kind, init); }

    // ---------------------------------------------------------------- builtins

    static int npos(Object[] a) {
        int n = 0;
        for (Object x : a) if (!(x instanceof Kw)) n++;
        return n;
    }

    static Object kwv(Object[] a, String k, Object dflt) {
        for (Object x : a) if (x instanceof Kw && ((Kw) x).k.equals(k)) return ((Kw) x).v;
        return dflt;
    }

    static Object arg(Object[] a, int i) {
        if (i >= a.length || a[i] instanceof Kw) throw new PyError("TypeError", "missing required argument");
        return a[i];
    }

    static Object arg(Object[] a, int i, Object dflt) {
        return (i >= a.length || a[i] instanceof Kw) ? dflt : a[i];
    }

    static int len(Object o) {
        if (o instanceof Oop.Inst) return Oop.len((Oop.Inst) o);
        if (o instanceof Oop.Bytes) return ((Oop.Bytes) o).size();
        if (o instanceof Ext.PyFile) throw new PyError("TypeError", "object of type 'file' has no len()");
        if (o instanceof String) return ((String) o).length();
        if (o instanceof List) return ((List) o).size();
        if (o instanceof Map) return ((Map) o).size();
        if (o instanceof Set) return ((Set) o).size();
        throw new PyError("TypeError", "object of type '" + tn(o) + "' has no len()");
    }

    static Comparator<Object> keyed(final Object keyFn, final boolean reverse) {
        return new Comparator<Object>() {
            public int compare(Object x, Object y) {
                int c = keyFn == null ? cmp(x, y) : cmp(call(keyFn, x), call(keyFn, y));
                return reverse ? -c : c;
            }
        };
    }

    static List<Object> sortedList(Object src, Object keyFn, boolean reverse) {
        List<Object> l = toList(src);
        // stable sort; reverse via comparator keeps stability like Python
        Collections.sort(l, keyed(keyFn, reverse));
        return l;
    }

    public static Object bi(String name, Object... a) {
        switch (name) {
            case "len": return (long) len(arg(a, 0));
            case "str": return a.length == 0 ? "" : str(a[0]);
            case "repr": return repr(arg(a, 0));
            case "int": {
                if (a.length == 0) return 0L;
                if (npos(a) > 1 && a[0] instanceof String) {
                    try { return Long.parseLong(((String) a[0]).trim(), (int) toLong(a[1])); }
                    catch (NumberFormatException e) { throw new PyError("ValueError", "invalid literal for int() with base " + str(a[1]) + ": " + repr(a[0])); }
                }
                return toInt(a[0]);
            }
            case "float": return a.length == 0 ? (Object) 0.0 : toFloat(a[0]);
            case "bool": return a.length == 0 ? Boolean.FALSE : Boolean.valueOf(truth(a[0]));
            case "abs": {
                Object x = norm(arg(a, 0));
                if (x instanceof Long) return Math.abs((Long) x);
                if (x instanceof Double) return Math.abs((Double) x);
                throw new PyError("TypeError", "bad operand type for abs(): '" + tn(x) + "'");
            }
            case "min": case "max": {
                boolean isMax = name.equals("max");
                List<Object> items = npos(a) == 1 ? toList(a[0]) : new ArrayList<Object>(Arrays.asList(a).subList(0, npos(a)));
                if (items.isEmpty()) {
                    Object d = kwv(a, "default", MISSING);
                    if (d != MISSING) return d;
                    throw new PyError("ValueError", name + "() arg is an empty sequence");
                }
                Object kf = kwv(a, "key", null);
                Object best = items.get(0);
                for (int i = 1; i < items.size(); i++) {
                    Object x = items.get(i);
                    int c = kf == null ? cmp(x, best) : cmp(call(kf, x), call(kf, best));
                    if (isMax ? c > 0 : c < 0) best = x;
                }
                return best;
            }
            case "sum": {
                Object total = arg(a, 1, 0L);
                for (Object x : iter(arg(a, 0))) total = add(total, x);
                return total;
            }
            case "round": {
                Object x = norm(arg(a, 0));
                if (a.length < 2 || a[1] == null) {
                    if (x instanceof Long) return x;
                    return (long) Math.rint(dbl(x));
                }
                int nd = (int) toLong(a[1]);
                if (x instanceof Long && nd >= 0) return x;
                double d = dbl(x);
                if (Double.isNaN(d) || Double.isInfinite(d)) return d;
                BigDecimal r = FPPE ? dec(d).setScale(nd, RoundingMode.HALF_UP) : new BigDecimal(d).setScale(nd, RoundingMode.HALF_EVEN);
                return x instanceof Long ? (Object) r.longValue() : (Object) r.doubleValue();
            }
            case "print": {
                StringBuilder sb = new StringBuilder();
                String sep = str(kwv(a, "sep", " ")), end = str(kwv(a, "end", "\n"));
                boolean first = true;
                for (Object x : a) {
                    if (x instanceof Kw) continue;
                    if (!first) sb.append(sep);
                    first = false;
                    sb.append(str(x));
                }
                String text = sb.append(end).toString();
                System.out.print(text);
                if (sink != null) sink.out(text, false);
                return null;
            }
            case "input": return "";
            case "sorted": return sortedList(arg(a, 0), kwv(a, "key", null), truth(kwv(a, "reverse", Boolean.FALSE)));
            case "reversed": { List<Object> l = toList(arg(a, 0)); Collections.reverse(l); return l; }
            case "list": return a.length == 0 ? new ArrayList<Object>() : toList(a[0]);
            case "tuple": return a.length == 0 ? new Tuple(new ArrayList<Object>()) : new Tuple(toList(a[0]));
            case "set": {
                Set<Object> s = new LinkedHashSet<>();
                if (a.length > 0) for (Object x : iter(a[0])) s.add(key(x));
                return s;
            }
            case "dict": {
                Map<Object, Object> m = new LinkedHashMap<>();
                if (a.length > 0 && !(a[0] instanceof Kw)) {
                    if (a[0] instanceof Map) m.putAll((Map) a[0]);
                    else for (Object p : iter(a[0])) { Object[] kv = unpack(p, 2); m.put(key(kv[0]), kv[1]); }
                }
                for (Object x : a) if (x instanceof Kw) m.put(((Kw) x).k, ((Kw) x).v);
                return m;
            }
            case "range": {
                long lo = 0, hi, st = 1;
                if (npos(a) == 1) hi = toLong(a[0]);
                else { lo = toLong(a[0]); hi = toLong(a[1]); if (npos(a) > 2) st = toLong(a[2]); }
                if (st == 0) throw new PyError("ValueError", "range() arg 3 must not be zero");
                List<Object> r = new ArrayList<>();
                if (st > 0) for (long i = lo; i < hi; i += st) { r.add(i); if (r.size() > 5_000_000) throw new PyError("MemoryError", "range too large"); }
                else for (long i = lo; i > hi; i += st) { r.add(i); if (r.size() > 5_000_000) throw new PyError("MemoryError", "range too large"); }
                return r;
            }
            case "enumerate": {
                long i = a.length > 1 ? toLong(a[1]) : 0;
                List<Object> r = new ArrayList<>();
                for (Object x : iter(arg(a, 0))) r.add(tupleOf(i++, x));
                return r;
            }
            case "zip": {
                List<List<Object>> ls = new ArrayList<>();
                int n = Integer.MAX_VALUE;
                for (Object x : a) { List<Object> l = toList(x); ls.add(l); n = Math.min(n, l.size()); }
                List<Object> r = new ArrayList<>();
                if (ls.isEmpty()) return r;
                for (int i = 0; i < n; i++) {
                    List<Object> row = new ArrayList<>();
                    for (List<Object> l : ls) row.add(l.get(i));
                    r.add(new Tuple(row));
                }
                return r;
            }
            case "map": {
                List<Object> r = new ArrayList<>();
                for (Object x : iter(arg(a, 1))) r.add(call(a[0], x));
                return r;
            }
            case "filter": {
                List<Object> r = new ArrayList<>();
                for (Object x : iter(arg(a, 1))) if (a[0] == null ? truth(x) : truth(call(a[0], x))) r.add(x);
                return r;
            }
            case "any": { for (Object x : iter(arg(a, 0))) if (truth(x)) return Boolean.TRUE; return Boolean.FALSE; }
            case "all": { for (Object x : iter(arg(a, 0))) if (!truth(x)) return Boolean.FALSE; return Boolean.TRUE; }
            case "chr": return new String(Character.toChars((int) toLong(arg(a, 0))));
            case "ord": {
                Object s = arg(a, 0);
                if (!(s instanceof String) || ((String) s).isEmpty()) throw new PyError("TypeError", "ord() expected a character");
                return (long) ((String) s).codePointAt(0);
            }
            case "pow": return pow(arg(a, 0), arg(a, 1));
            case "divmod": return tupleOf(floordiv(arg(a, 0), arg(a, 1)), mod(arg(a, 0), arg(a, 1)));
            case "format": return fmt(arg(a, 0), arg(a, 1, ""));
            case "hex": return (toLong(arg(a, 0)) < 0 ? "-0x" : "0x") + Long.toHexString(Math.abs(toLong(a[0])));
            case "bin": return (toLong(arg(a, 0)) < 0 ? "-0b" : "0b") + Long.toBinaryString(Math.abs(toLong(a[0])));
            case "oct": return (toLong(arg(a, 0)) < 0 ? "-0o" : "0o") + Long.toOctalString(Math.abs(toLong(a[0])));
            default: return Ext.bi(name, a);
        }
    }

    public static Object attr(Object o, String name) { return Ext.attr(o, name); }

    // ------------------------------------------------------------------ modules

    private static final Random RNG = new Random();

    public static Object modc(String module, String name) {
        if (module.equals("math")) {
            switch (name) {
                case "pi": return Math.PI;
                case "e": return Math.E;
                case "tau": return 2 * Math.PI;
                case "inf": return Double.POSITIVE_INFINITY;
                case "nan": return Double.NaN;
            }
        }
        return Ext.modc(module, name);
    }

    public static Object mod(String module, String fn, Object... a) {
        { Object lr = Lib.mod(module, fn, a); if (lr != Lib.NO) return lr; }
        switch (module) {
            case "math": {
                double x = a.length > 0 && !(a[0] instanceof List) && !(a[0] instanceof String) ? dbl(a[0]) : 0;
                switch (fn) {
                    case "sqrt": if (x < 0) throw new PyError("ValueError", "math domain error"); return Math.sqrt(x);
                    case "sin": return Math.sin(x);
                    case "cos": return Math.cos(x);
                    case "tan": return Math.tan(x);
                    case "asin": return Math.asin(x);
                    case "acos": return Math.acos(x);
                    case "atan": return Math.atan(x);
                    case "atan2": return Math.atan2(x, dbl(a[1]));
                    case "exp": return Math.exp(x);
                    case "log":
                        if (x <= 0) throw new PyError("ValueError", "math domain error");
                        return a.length > 1 ? (Object) (Math.log(x) / Math.log(dbl(a[1]))) : (Object) Math.log(x);
                    case "log10": if (x <= 0) throw new PyError("ValueError", "math domain error"); return Math.log10(x);
                    case "log2": if (x <= 0) throw new PyError("ValueError", "math domain error"); return Math.log(x) / Math.log(2);
                    case "pow": return Math.pow(x, dbl(a[1]));
                    case "floor": return (long) Math.floor(x);
                    case "ceil": return (long) Math.ceil(x);
                    case "trunc": return (long) x;
                    case "fabs": return Math.abs(x);
                    case "hypot": return Math.hypot(x, dbl(a[1]));
                    case "radians": return Math.toRadians(x);
                    case "degrees": return Math.toDegrees(x);
                    case "isnan": return Boolean.valueOf(Double.isNaN(x));
                    case "isinf": return Boolean.valueOf(Double.isInfinite(x));
                    case "factorial": { long r = 1; for (long i = 2; i <= (long) x; i++) r *= i; return r; }
                    case "gcd": { long p = Math.abs(toLong(a[0])), q = Math.abs(toLong(a[1])); while (q != 0) { long t = p % q; p = q; q = t; } return p; }
                }
                break;
            }
            case "random": {
                switch (fn) {
                    case "random": return RNG.nextDouble();
                    case "randint": { long lo = toLong(a[0]), hi = toLong(a[1]); return lo + (long) (RNG.nextDouble() * (hi - lo + 1)); }
                    case "randrange": {
                        long lo = a.length > 1 ? toLong(a[0]) : 0, hi = a.length > 1 ? toLong(a[1]) : toLong(a[0]);
                        return lo + (long) (RNG.nextDouble() * (hi - lo));
                    }
                    case "uniform": return dbl(a[0]) + RNG.nextDouble() * (dbl(a[1]) - dbl(a[0]));
                    case "choice": { List<Object> l = toList(a[0]); if (l.isEmpty()) throw new PyError("IndexError", "Cannot choose from an empty sequence"); return l.get(RNG.nextInt(l.size())); }
                    case "shuffle": Collections.shuffle((List) a[0], RNG); return null;
                    case "sample": { List<Object> l = toList(a[0]); Collections.shuffle(l, RNG); return new ArrayList<Object>(l.subList(0, (int) toLong(a[1]))); }
                    case "seed": RNG.setSeed(a.length > 0 && a[0] != null ? (long) dbl(a[0]) : System.nanoTime()); return null;
                }
                break;
            }
            case "time": {
                switch (fn) {
                    case "time": return System.currentTimeMillis() / 1000.0;
                    case "perf_counter": case "monotonic": return System.nanoTime() / 1e9;
                    case "sleep":
                        try { Thread.sleep((long) (dbl(a[0]) * 1000)); } catch (InterruptedException e) { /* ignore */ }
                        return null;
                }
                break;
            }
        }
        return Ext.mod(module, fn, a);
    }

    // ------------------------------------------------------------ method calls

    public static Object m(Object o, String name, Object... a) {
        if (o == APP) o = root;
        if (o instanceof Obj) return ((Obj) o).callm(name, a);
        if (o instanceof Oop.Bytes) return Oop.bytesMethod((Oop.Bytes) o, name, a);
        if (o instanceof String) return strm((String) o, name, a);
        if (o instanceof List) return listm((List<Object>) o, name, a);
        if (o instanceof Map) return mapm((Map<Object, Object>) o, name, a);
        if (o instanceof Set) return setm((Set<Object>) o, name, a);
        throw new PyError("AttributeError", "'" + tn(o) + "' object has no attribute '" + name + "'");
    }

    private static String sarg(Object[] a, int i) {
        Object x = arg(a, i);
        if (!(x instanceof String)) throw new PyError("TypeError", "expected str, got " + tn(x));
        return (String) x;
    }

    private static Object strm(String s, String name, Object[] a) {
        switch (name) {
            case "encode": return Oop.bytesOf(Oop.encode(s, a.length > 0 ? Py.str(a[0]) : "utf-8"));
            case "upper": return s.toUpperCase();
            case "lower": return s.toLowerCase();
            case "title": {
                StringBuilder sb = new StringBuilder();
                boolean up = true;
                for (char c : s.toCharArray()) {
                    sb.append(up ? Character.toUpperCase(c) : Character.toLowerCase(c));
                    up = !Character.isLetter(c);
                }
                return sb.toString();
            }
            case "capitalize": return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
            case "strip": return a.length == 0 || a[0] == null ? s.trim() : stripChars(s, sarg(a, 0), true, true);
            case "lstrip": return a.length == 0 || a[0] == null ? stripChars(s, " \t\n\r\f\u000b", true, false) : stripChars(s, sarg(a, 0), true, false);
            case "rstrip": return a.length == 0 || a[0] == null ? stripChars(s, " \t\n\r\f\u000b", false, true) : stripChars(s, sarg(a, 0), false, true);
            case "split": {
                List<Object> r = new ArrayList<>();
                if (a.length == 0 || a[0] == null) {
                    for (String p : s.trim().split("\\s+")) if (!p.isEmpty()) r.add(p);
                    return r;
                }
                String sep = sarg(a, 0);
                if (sep.isEmpty()) throw new PyError("ValueError", "empty separator");
                long max = a.length > 1 ? toLong(a[1]) : -1;
                int from = 0;
                while (true) {
                    int i = s.indexOf(sep, from);
                    if (i < 0 || (max >= 0 && r.size() >= max)) { r.add(s.substring(from)); break; }
                    r.add(s.substring(from, i));
                    from = i + sep.length();
                }
                return r;
            }
            case "splitlines": {
                List<Object> r = new ArrayList<>();
                for (String p : s.split("\r?\n", -1)) r.add(p);
                if (!r.isEmpty() && ((String) r.get(r.size() - 1)).isEmpty()) r.remove(r.size() - 1);
                return r;
            }
            case "join": {
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (Object x : iter(arg(a, 0))) {
                    if (!(x instanceof String)) throw new PyError("TypeError", "sequence item: expected str instance, " + tn(x) + " found");
                    if (!first) sb.append(s);
                    first = false;
                    sb.append((String) x);
                }
                return sb.toString();
            }
            case "replace": return s.replace(sarg(a, 0), sarg(a, 1));
            case "startswith": return Boolean.valueOf(s.startsWith(sarg(a, 0)));
            case "endswith": return Boolean.valueOf(s.endsWith(sarg(a, 0)));
            case "find": return (long) s.indexOf(sarg(a, 0));
            case "rfind": return (long) s.lastIndexOf(sarg(a, 0));
            case "index": { int i = s.indexOf(sarg(a, 0)); if (i < 0) throw new PyError("ValueError", "substring not found"); return (long) i; }
            case "count": {
                String sub = sarg(a, 0);
                if (sub.isEmpty()) return (long) s.length() + 1;
                long n = 0;
                for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + sub.length())) n++;
                return n;
            }
            case "isdigit": case "isnumeric": case "isdecimal": return Boolean.valueOf(!s.isEmpty() && allChars(s, 0));
            case "isalpha": return Boolean.valueOf(!s.isEmpty() && allChars(s, 1));
            case "isalnum": return Boolean.valueOf(!s.isEmpty() && allChars(s, 2));
            case "isspace": return Boolean.valueOf(!s.isEmpty() && allChars(s, 3));
            case "isupper": return Boolean.valueOf(anyLetter(s) && s.equals(s.toUpperCase()));
            case "islower": return Boolean.valueOf(anyLetter(s) && s.equals(s.toLowerCase()));
            case "zfill": {
                int w = (int) toLong(arg(a, 0));
                boolean neg = s.startsWith("-") || s.startsWith("+");
                String digits = neg ? s.substring(1) : s;
                StringBuilder sb = new StringBuilder(digits);
                while (sb.length() + (neg ? 1 : 0) < w) sb.insert(0, '0');
                return (neg ? s.substring(0, 1) : "") + sb;
            }
            case "center": {
                int w = (int) toLong(arg(a, 0));
                String f = a.length > 1 ? sarg(a, 1) : " ";
                int pad = Math.max(0, w - s.length());
                return rep(f, pad / 2) + s + rep(f, pad - pad / 2);
            }
            case "ljust": { int w = (int) toLong(arg(a, 0)); return s + rep(a.length > 1 ? sarg(a, 1) : " ", Math.max(0, w - s.length())); }
            case "rjust": { int w = (int) toLong(arg(a, 0)); return rep(a.length > 1 ? sarg(a, 1) : " ", Math.max(0, w - s.length())) + s; }
            case "format": return strFormat(s, a);
            default: throw new PyError("AttributeError", "'str' object has no attribute '" + name + "'");
        }
    }

    private static boolean allChars(String s, int mode) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = mode == 0 ? Character.isDigit(c) : mode == 1 ? Character.isLetter(c)
                    : mode == 2 ? Character.isLetterOrDigit(c) : Character.isWhitespace(c);
            if (!ok) return false;
        }
        return true;
    }

    private static boolean anyLetter(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) return true;
        return false;
    }

    private static String stripChars(String s, String chars, boolean left, boolean right) {
        int i = 0, j = s.length();
        if (left) while (i < j && chars.indexOf(s.charAt(i)) >= 0) i++;
        if (right) while (j > i && chars.indexOf(s.charAt(j - 1)) >= 0) j--;
        return s.substring(i, j);
    }

    private static Object listm(List<Object> l, String name, Object[] a) {
        switch (name) {
            case "append": l.add(arg(a, 0, null)); return null;
            case "extend": l.addAll(toList(arg(a, 0))); return null;
            case "insert": { int i = norm(toLong(arg(a, 0)), l.size()); l.add(Math.max(0, Math.min(l.size(), i)), a[1]); return null; }
            case "pop": {
                if (l.isEmpty()) throw new PyError("IndexError", "pop from empty list");
                int i = a.length > 0 ? norm(toLong(a[0]), l.size()) : l.size() - 1;
                if (i < 0 || i >= l.size()) throw new PyError("IndexError", "pop index out of range");
                return l.remove(i);
            }
            case "remove": {
                for (int i = 0; i < l.size(); i++) if (eq(l.get(i), a[0])) { l.remove(i); return null; }
                throw new PyError("ValueError", "list.remove(x): x not in list");
            }
            case "index": {
                for (int i = 0; i < l.size(); i++) if (eq(l.get(i), a[0])) return (long) i;
                throw new PyError("ValueError", repr(a[0]) + " is not in list");
            }
            case "count": { long n = 0; for (Object x : l) if (eq(x, a[0])) n++; return n; }
            case "sort": {
                List<Object> r = sortedList(l, kwv(a, "key", null), truth(kwv(a, "reverse", Boolean.FALSE)));
                l.clear();
                l.addAll(r);
                return null;
            }
            case "reverse": Collections.reverse(l); return null;
            case "clear": l.clear(); return null;
            case "copy": return new ArrayList<Object>(l);
            default: return Ext.listExtra(l, name, a);
        }
    }

    private static Object mapm(Map<Object, Object> m, String name, Object[] a) {
        switch (name) {
            case "get": { Object k = key(arg(a, 0)); return m.containsKey(k) ? m.get(k) : arg(a, 1, null); }
            case "keys": return new ArrayList<Object>(m.keySet());
            case "values": return new ArrayList<Object>(m.values());
            case "items": {
                List<Object> r = new ArrayList<>();
                for (Map.Entry<Object, Object> e : m.entrySet()) r.add(tupleOf(e.getKey(), e.getValue()));
                return r;
            }
            case "pop": {
                Object k = key(arg(a, 0));
                if (m.containsKey(k)) return m.remove(k);
                if (a.length > 1) return a[1];
                throw new PyError("KeyError", repr(a[0]));
            }
            case "update": {
                if (a.length > 0 && a[0] instanceof Map) m.putAll((Map) a[0]);
                for (Object x : a) if (x instanceof Kw) m.put(((Kw) x).k, ((Kw) x).v);
                return null;
            }
            case "setdefault": { Object k = key(arg(a, 0)); if (!m.containsKey(k)) m.put(k, arg(a, 1, null)); return m.get(k); }
            case "clear": m.clear(); return null;
            case "copy": return new LinkedHashMap<Object, Object>(m);
            default: return Ext.mapExtra(m, name, a);
        }
    }

    private static Object setm(Set<Object> s, String name, Object[] a) {
        switch (name) {
            case "add": s.add(key(arg(a, 0))); return null;
            case "remove": if (!s.remove(key(arg(a, 0)))) throw new PyError("KeyError", repr(a[0])); return null;
            case "discard": s.remove(key(arg(a, 0))); return null;
            case "clear": s.clear(); return null;
            case "union": { Set<Object> r = new LinkedHashSet<>(s); for (Object x : iter(arg(a, 0))) r.add(key(x)); return r; }
            case "intersection": { Set<Object> r = new LinkedHashSet<>(s); r.retainAll(new LinkedHashSet<Object>(toList(arg(a, 0)))); return r; }
            case "copy": return new LinkedHashSet<Object>(s);
            default: throw new PyError("AttributeError", "'set' object has no attribute '" + name + "'");
        }
    }
}

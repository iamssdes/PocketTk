package apple.rt;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** socket, urllib.request/parse, a small `requests` shim and sqlite3. Network code runs on the Python thread. */
public final class Net {
    private Net() { }

    static Object arg(Object[] a, int i) { return Py.arg(a, i); }
    static long L(Object o) { return ((Number) Py.toInt(o)).longValue(); }
    static Py.Tuple tup(Object... xs) { return new Py.Tuple(new ArrayList<Object>(Arrays.asList(xs))); }
    static byte[] bytes(Object o) {
        if (o instanceof Oop.Bytes) return ((Oop.Bytes) o).b;
        if (o instanceof String) return ((String) o).getBytes(StandardCharsets.UTF_8);
        throw new Py.PyError("TypeError", "a bytes-like object is required, not '" + Py.tn(o) + "'");
    }
    static Py.PyError oserr(Throwable t) { return new Py.PyError("OSError", String.valueOf(t.getMessage() == null ? t : t.getMessage())); }

    // ------------------------------------------------------------ constants

    static Object modc(String name) {
        switch (name) {
            case "AF_INET": case "AF_INET6": return name.equals("AF_INET") ? 2L : 10L;
            case "SOCK_STREAM": return 1L;
            case "SOCK_DGRAM": return 2L;
            case "SOL_SOCKET": return 1L;
            case "SO_REUSEADDR": return 2L;
            case "SO_BROADCAST": return 6L;
            case "IPPROTO_TCP": return 6L;
            case "TCP_NODELAY": return 1L;
            case "SHUT_RD": return 0L;
            case "SHUT_WR": return 1L;
            case "SHUT_RDWR": return 2L;
            case "timeout": return "timeout";
            case "error": return "OSError";
        }
        throw new Py.PyError("AttributeError", "module 'socket' has no attribute '" + name + "'");
    }

    static Object modc2(String name) {
        switch (name) {
            case "Error": case "DatabaseError": case "OperationalError": case "IntegrityError": case "ProgrammingError": return name;
            case "version": return "3.40";
            case "PARSE_DECLTYPES": return 1L;
            case "Row": return "Row";
        }
        throw new Py.PyError("AttributeError", "module 'sqlite3' has no attribute '" + name + "'");
    }

    public static Object mod(String module, String fn, Object[] a) {
        switch (module) {
            case "socket": return socketMod(fn, a);
            case "urllib": return urllibMod(fn, a);
            case "requests": return requestsMod(fn, a);
            case "sqlite3": return sqliteMod(fn, a);
        }
        return Lib.NO;
    }

    // ------------------------------------------------------------ socket

    static Object socketMod(String fn, Object[] a) {
        switch (fn) {
            case "socket": return new Sock(a.length > 1 && !(a[1] instanceof Py.Kw) ? L(a[1]) : 1L);
            case "gethostname": return "localhost";
            case "gethostbyname": try { return InetAddress.getByName(Py.str(arg(a, 0))).getHostAddress(); } catch (IOException e) { throw oserr(e); }
            case "create_connection": {
                Sock s = new Sock(1L);
                s.callm("connect", new Object[]{arg(a, 0)});
                return s;
            }
            case "setdefaulttimeout": return null;
        }
        throw new Py.PyError("AttributeError", "module 'socket' has no attribute '" + fn + "'");
    }

    static final class Sock implements Py.Obj {
        final boolean udp;
        Socket s; ServerSocket ss; DatagramSocket ds;
        int timeout = 0;
        String bindHost; int bindPort = -1;
        boolean closed;
        Sock(long type) { udp = type == 2L; }
        Sock(Socket s) { udp = false; this.s = s; }

        void need() { if (closed) throw new Py.PyError("OSError", "[Errno 9] Bad file descriptor"); }

        public Object callm(String n, Object[] a) {
            try {
                switch (n) {
                    case "settimeout": timeout = a[0] == null ? 0 : (int) (Py.dbl(a[0]) * 1000); if (s != null) s.setSoTimeout(timeout); if (ss != null) ss.setSoTimeout(timeout); if (ds != null) ds.setSoTimeout(timeout); return null;
                    case "setblocking": return null;
                    case "setsockopt": case "shutdown": case "getsockopt": return null;
                    case "bind": {
                        List<Object> ad = Py.toList(arg(a, 0));
                        bindHost = Py.str(ad.get(0)); bindPort = (int) L(ad.get(1));
                        if (udp) { ds = new DatagramSocket(bindPort); if (timeout > 0) ds.setSoTimeout(timeout); }
                        return null;
                    }
                    case "listen": {
                        ss = new ServerSocket();
                        ss.setReuseAddress(true);
                        ss.bind(new InetSocketAddress(bindHost == null || bindHost.isEmpty() ? "0.0.0.0" : bindHost, bindPort < 0 ? 0 : bindPort));
                        if (timeout > 0) ss.setSoTimeout(timeout);
                        return null;
                    }
                    case "accept": {
                        need();
                        Socket c = ss.accept();
                        return tup(new Sock(c), tup(c.getInetAddress().getHostAddress(), (long) c.getPort()));
                    }
                    case "connect": case "connect_ex": {
                        List<Object> ad = Py.toList(arg(a, 0));
                        if (udp) { ds = new DatagramSocket(); ds.connect(InetAddress.getByName(Py.str(ad.get(0))), (int) L(ad.get(1))); return null; }
                        s = new Socket();
                        try { s.connect(new InetSocketAddress(Py.str(ad.get(0)), (int) L(ad.get(1))), timeout > 0 ? timeout : 15000); }
                        catch (IOException e) { if (n.equals("connect_ex")) return 111L; throw e; }
                        if (timeout > 0) s.setSoTimeout(timeout);
                        return n.equals("connect_ex") ? (Object) 0L : null;
                    }
                    case "send": case "sendall": {
                        need();
                        byte[] b = bytes(arg(a, 0));
                        if (udp && ds != null) { ds.send(new DatagramPacket(b, b.length)); return (long) b.length; }
                        s.getOutputStream().write(b); s.getOutputStream().flush();
                        return n.equals("send") ? (Object) (long) b.length : null;
                    }
                    case "sendto": {
                        byte[] b = bytes(arg(a, 0)); List<Object> ad = Py.toList(arg(a, 1));
                        if (ds == null) ds = new DatagramSocket();
                        ds.send(new DatagramPacket(b, b.length, InetAddress.getByName(Py.str(ad.get(0))), (int) L(ad.get(1))));
                        return (long) b.length;
                    }
                    case "recv": {
                        need();
                        int max = (int) L(arg(a, 0));
                        byte[] buf = new byte[max];
                        int k;
                        if (udp) { DatagramPacket p = new DatagramPacket(buf, max); ds.receive(p); k = p.getLength(); }
                        else k = s.getInputStream().read(buf);
                        return new Oop.Bytes(k <= 0 ? new byte[0] : Arrays.copyOf(buf, k), false);
                    }
                    case "recvfrom": {
                        int max = (int) L(arg(a, 0));
                        byte[] buf = new byte[max];
                        DatagramPacket p = new DatagramPacket(buf, max);
                        ds.receive(p);
                        return tup(new Oop.Bytes(Arrays.copyOf(buf, p.getLength()), false), tup(p.getAddress().getHostAddress(), (long) p.getPort()));
                    }
                    case "close": closed = true;
                        if (s != null) s.close(); if (ss != null) ss.close(); if (ds != null) ds.close();
                        return null;
                    case "getsockname": return tup(bindHost == null ? "0.0.0.0" : bindHost, (long) (ss != null ? ss.getLocalPort() : bindPort));
                    case "getpeername": return tup(s.getInetAddress().getHostAddress(), (long) s.getPort());
                    case "fileno": return 3L;
                    case "__enter__": return this;
                    case "__exit__": callm("close", new Object[0]); return null;
                }
            } catch (SocketTimeoutException e) {
                throw new Py.PyError("timeout", "timed out");
            } catch (IOException e) {
                throw oserr(e);
            }
            throw new Py.PyError("AttributeError", "'socket' object has no attribute '" + n + "'");
        }
    }

    // ------------------------------------------------------------ urllib / requests

    static String enc(String x) { try { return URLEncoder.encode(x, "UTF-8"); } catch (UnsupportedEncodingException e) { throw oserr(e); } }

    static Object urllibMod(String fn, Object[] a) {
        switch (fn) {
            case "request.urlopen": {
                Object u = arg(a, 0);
                Object data = Py.kwv(a, "data", a.length > 1 && !(a[1] instanceof Py.Kw) ? a[1] : null);
                Object tmo = Py.kwv(a, "timeout", null);
                Object hdr = null;
                String url;
                if (u instanceof Req) { url = ((Req) u).url; hdr = ((Req) u).headers; if (data == null) data = ((Req) u).data; }
                else url = Py.str(u);
                HttpResult hr = http(data == null ? "GET" : "POST", url, hdr, data == null ? null : bytes(data), tmo == null ? 20000 : (int) (Py.dbl(tmo) * 1000));
                if (hr.status >= 400) throw new Py.PyError("HTTPError", "HTTP Error " + hr.status);
                return new Resp(hr);
            }
            case "request.Request": return new Req(Py.str(arg(a, 0)), Py.kwv(a, "data", null), Py.kwv(a, "headers", null));
            case "parse.quote": return enc(Py.str(arg(a, 0))).replace("+", "%20").replace("%2F", "/");
            case "parse.quote_plus": return enc(Py.str(arg(a, 0)));
            case "parse.unquote": case "parse.unquote_plus": try { return URLDecoder.decode(Py.str(arg(a, 0)), "UTF-8"); } catch (UnsupportedEncodingException e) { throw oserr(e); }
            case "parse.urlencode": return urlencode(arg(a, 0));
        }
        throw new Py.PyError("AttributeError", "module 'urllib' has no attribute '" + fn + "'");
    }

    @SuppressWarnings("unchecked")
    static String urlencode(Object o) {
        StringBuilder s = new StringBuilder();
        try {
            if (o instanceof Map) {
                for (Map.Entry<Object, Object> e : ((Map<Object, Object>) o).entrySet()) {
                    if (s.length() > 0) s.append('&');
                    s.append(URLEncoder.encode(Py.str(e.getKey()), "UTF-8")).append('=').append(URLEncoder.encode(Py.str(e.getValue()), "UTF-8"));
                }
            } else for (Object p : Py.iter(o)) {
                List<Object> kv = Py.toList(p);
                if (s.length() > 0) s.append('&');
                s.append(URLEncoder.encode(Py.str(kv.get(0)), "UTF-8")).append('=').append(URLEncoder.encode(Py.str(kv.get(1)), "UTF-8"));
            }
        } catch (UnsupportedEncodingException e) { throw oserr(e); }
        return s.toString();
    }

    static final class Req implements Py.Obj {
        final String url; final Object data, headers;
        Req(String u, Object d, Object h) { url = u; data = d; headers = h; }
        public Object callm(String n, Object[] a) { throw new Py.PyError("AttributeError", "'Request' object has no attribute '" + n + "'"); }
    }

    static final class HttpResult {
        int status; byte[] body; Map<Object, Object> headers = new LinkedHashMap<Object, Object>(); String url;
    }

    @SuppressWarnings("unchecked")
    static HttpResult http(String method, String url, Object headers, byte[] body, int timeoutMs) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(timeoutMs); c.setReadTimeout(timeoutMs);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "TkDroid");
            if (headers instanceof Map) for (Map.Entry<Object, Object> e : ((Map<Object, Object>) headers).entrySet()) c.setRequestProperty(Py.str(e.getKey()), Py.str(e.getValue()));
            if (body != null) { c.setDoOutput(true); OutputStream o = c.getOutputStream(); o.write(body); o.close(); }
            HttpResult r = new HttpResult();
            r.status = c.getResponseCode();
            r.url = url;
            InputStream in = r.status >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            if (in != null) { byte[] buf = new byte[8192]; int k; while ((k = in.read(buf)) > 0) bo.write(buf, 0, k); in.close(); }
            r.body = bo.toByteArray();
            for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) if (e.getKey() != null) r.headers.put(e.getKey(), e.getValue().get(e.getValue().size() - 1));
            return r;
        } catch (SocketTimeoutException e) {
            throw new Py.PyError("Timeout", "timed out");
        } catch (IOException e) {
            throw new Py.PyError("URLError", "<urlopen error " + e.getMessage() + ">");
        }
    }

    /** urllib response and requests.Response share one class. */
    static final class Resp implements Py.Obj {
        final HttpResult r;
        final boolean requests;
        Resp(HttpResult r) { this(r, false); }
        Resp(HttpResult r, boolean requests) { this.r = r; this.requests = requests; }
        String text() { return new String(r.body, StandardCharsets.UTF_8); }
        public Object callm(String n, Object[] a) {
            switch (n) {
                case "read": return new Oop.Bytes(r.body, false);
                case "getcode": return (long) r.status;
                case "close": return null;
                case "__enter__": return this;
                case "__exit__": return null;
                case "json": return Py.mod("json", "loads", new Object[]{text()});
                case "raise_for_status":
                    if (r.status >= 400) throw new Py.PyError("HTTPError", r.status + " Error for url: " + r.url);
                    return null;
                case "@status_code": case "@status": return (long) r.status;
                case "@text": return text();
                case "@content": return new Oop.Bytes(r.body, false);
                case "@ok": return r.status < 400;
                case "@headers": return r.headers;
                case "@url": return r.url;
                case "@reason": return r.status == 200 ? "OK" : "";
                case "@encoding": return "utf-8";
            }
            throw new Py.PyError("AttributeError", "response object has no attribute '" + n + "'");
        }
    }

    @SuppressWarnings("unchecked")
    static Object requestsMod(String fn, Object[] a) {
        String method = fn.toUpperCase();
        if (fn.equals("request")) { method = Py.str(arg(a, 0)).toUpperCase(); a = Arrays.copyOfRange(a, 1, a.length); }
        else if (!Arrays.asList("get", "post", "put", "delete", "head", "patch").contains(fn))
            throw new Py.PyError("AttributeError", "module 'requests' has no attribute '" + fn + "'");
        String url = Py.str(arg(a, 0));
        Object params = Py.kwv(a, "params", null), data = Py.kwv(a, "data", null), json = Py.kwv(a, "json", null), hdr = Py.kwv(a, "headers", null), tmo = Py.kwv(a, "timeout", null);
        if (params != null) url += (url.contains("?") ? "&" : "?") + urlencode(params);
        byte[] body = null;
        Map<Object, Object> h = hdr instanceof Map ? new LinkedHashMap<Object, Object>((Map<Object, Object>) hdr) : new LinkedHashMap<Object, Object>();
        if (json != null) { body = Py.str(Py.mod("json", "dumps", new Object[]{json})).getBytes(StandardCharsets.UTF_8); h.put("Content-Type", "application/json"); }
        else if (data != null) {
            if (data instanceof Map) { body = urlencode(data).getBytes(StandardCharsets.UTF_8); h.put("Content-Type", "application/x-www-form-urlencoded"); }
            else body = bytes(data);
        }
        return new Resp(http(method, url, h, body, tmo == null ? 20000 : (int) (Py.dbl(tmo) * 1000)), true);
    }

    // ------------------------------------------------------------ sqlite3

    static Object sqliteMod(String fn, Object[] a) {
        if (fn.equals("connect")) return new Conn(Py.str(arg(a, 0)));
        throw new Py.PyError("AttributeError", "module 'sqlite3' has no attribute '" + fn + "'");
    }

    static final class Conn implements Py.Obj {
        final android.database.sqlite.SQLiteDatabase db;
        Conn(String path) {
            try {
                if (path.equals(":memory:")) db = android.database.sqlite.SQLiteDatabase.create(null);
                else {
                    File f = path.startsWith("/") ? new File(path) : new File(Ext.baseDir, path);
                    if (f.getParentFile() != null) f.getParentFile().mkdirs();
                    db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(f, null);
                }
            } catch (RuntimeException e) { throw new Py.PyError("OperationalError", String.valueOf(e.getMessage())); }
        }
        public Object callm(String n, Object[] a) {
            switch (n) {
                case "cursor": return new Cur(this);
                case "commit": case "rollback": return null;
                case "close": db.close(); return null;
                case "execute": case "executemany": case "executescript": {
                    Cur c = new Cur(this);
                    c.callm(n, a);
                    return c;
                }
                case "__enter__": return this;
                case "__exit__": return null;
            }
            throw new Py.PyError("AttributeError", "'sqlite3.Connection' object has no attribute '" + n + "'");
        }
    }

    static final class Cur extends Oop.It {
        final Conn conn;
        List<Object> rows = new ArrayList<Object>();
        int pos = 0;
        long lastrowid = 0, rowcount = -1;
        Object description;
        Cur(Conn c) { conn = c; }
        @Override protected Object produce() { return pos < rows.size() ? rows.get(pos++) : Oop.NOPE; }

        static boolean returnsRows(String sql) {
            String t = sql.trim().toLowerCase();
            return t.startsWith("select") || t.startsWith("pragma") || t.startsWith("with") || t.startsWith("explain");
        }

        void run(String sql, Object params) {
            Object[] ps = params == null ? new Object[0] : Py.toList(params).toArray();
            try {
                if (returnsRows(sql)) {
                    String[] sa = new String[ps.length];
                    for (int i = 0; i < ps.length; i++) sa[i] = ps[i] == null ? null : Py.str(ps[i]);
                    android.database.Cursor c = conn.db.rawQuery(sql, sa);
                    rows = new ArrayList<Object>(); pos = 0;
                    List<Object> names = new ArrayList<Object>();
                    for (int i = 0; i < c.getColumnCount(); i++) names.add(tup(c.getColumnName(i), null, null, null, null, null, null));
                    description = new Py.Tuple(names);
                    while (c.moveToNext()) {
                        List<Object> row = new ArrayList<Object>();
                        for (int i = 0; i < c.getColumnCount(); i++) {
                            switch (c.getType(i)) {
                                case 0: row.add(null); break;
                                case 1: row.add(c.getLong(i)); break;
                                case 2: row.add(c.getDouble(i)); break;
                                case 4: row.add(new Oop.Bytes(c.getBlob(i), false)); break;
                                default: row.add(c.getString(i));
                            }
                        }
                        rows.add(new Py.Tuple(row));
                    }
                    c.close();
                    rowcount = -1;
                } else {
                    Object[] bind = new Object[ps.length];
                    for (int i = 0; i < ps.length; i++) bind[i] = ps[i] instanceof Oop.Bytes ? ((Oop.Bytes) ps[i]).b : ps[i] instanceof Boolean ? (Object) ((Boolean) ps[i] ? 1L : 0L) : ps[i];
                    if (bind.length == 0) conn.db.execSQL(sql); else conn.db.execSQL(sql, bind);
                    rows = new ArrayList<Object>(); pos = 0;
                    android.database.Cursor c = conn.db.rawQuery("SELECT last_insert_rowid(), changes()", null);
                    if (c.moveToFirst()) { lastrowid = c.getLong(0); rowcount = c.getLong(1); }
                    c.close();
                }
                fin = false; ready = false;
            } catch (android.database.sqlite.SQLiteConstraintException e) {
                throw new Py.PyError("IntegrityError", String.valueOf(e.getMessage()));
            } catch (RuntimeException e) {
                throw new Py.PyError("OperationalError", String.valueOf(e.getMessage()));
            }
        }

        @Override public Object callm(String n, Object[] a) {
            switch (n) {
                case "execute": run(Py.str(arg(a, 0)), a.length > 1 ? a[1] : null); return this;
                case "executemany": {
                    long total = 0;
                    for (Object p : Py.iter(arg(a, 1))) { run(Py.str(arg(a, 0)), p); total += Math.max(0, rowcount); }
                    rowcount = total;
                    return this;
                }
                case "executescript": for (String st : Py.str(arg(a, 0)).split(";")) if (!st.trim().isEmpty()) run(st, null); return this;
                case "fetchone": return pos < rows.size() ? rows.get(pos++) : null;
                case "fetchall": { List<Object> r = new ArrayList<Object>(rows.subList(pos, rows.size())); pos = rows.size(); return r; }
                case "fetchmany": {
                    int k = a.length > 0 ? (int) L(a[0]) : 1;
                    int end = Math.min(rows.size(), pos + k);
                    List<Object> r = new ArrayList<Object>(rows.subList(pos, end)); pos = end; return r;
                }
                case "close": return null;
                case "@lastrowid": return lastrowid;
                case "@rowcount": return rowcount;
                case "@description": return description;
            }
            return super.callm(n, a);
        }
    }
}

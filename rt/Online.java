package apple.rt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;

/**
 * The little bit of networking the About page (demo download) and the Request page (sending a request) need.
 * Plain Java, no Android classes. Every failure ends up as a short sentence that can be shown to the person.
 */
final class Online {
    private Online() {}

    interface Progress { void update(long done, long total); }

    /** A problem whose message can be shown as it is. */
    static final class Failure extends IOException {
        Failure(String message) { super(message); }
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String UA = "Mozilla/5.0 (Linux; Android) PocketTk";
    private static final int MAX_REDIRECTS = 6;

    // ------------------------------------------------------------------------------------ download

    /**
     * GETs `url` into `out` (redirects are followed, but never from https down to http). With `requireZip` the
     * first bytes must be a zip header, so a web page (an error or "virus scan" page) is not saved as the file.
     * Returns the number of bytes written. `cancel` may be null.
     */
    static long download(String url, OutputStream out, boolean requireZip, Progress progress, AtomicBoolean cancel) throws IOException {
        HttpURLConnection c = open(url);
        try {
            long total = c.getContentLength();
            InputStream in = c.getInputStream();
            byte[] head = new byte[4];
            int h = 0;
            while (h < 4) {
                int r = in.read(head, h, 4 - h);
                if (r < 0) break;
                h += r;
            }
            if (h == 0) throw new Failure("The download was empty.");
            if (requireZip && !(h == 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4)) {
                throw new Failure("That link did not give a zip file. It may be a web page instead of the file itself.");
            }
            out.write(head, 0, h);
            long done = h;
            long last = 0;
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (cancel != null && cancel.get()) throw new Failure("Cancelled.");
                out.write(buf, 0, n);
                done += n;
                long now = System.currentTimeMillis();
                if (progress != null && now - last >= 100) {
                    last = now;
                    progress.update(done, total);
                }
            }
            if (total > 0 && done < total) throw new Failure("The download stopped early. Try again.");
            if (progress != null) progress.update(done, total);
            return done;
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        String cur = url;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            URL u = new URL(cur);
            String proto = u.getProtocol();
            if (!proto.equals("http") && !proto.equals("https")) throw new Failure("Only http and https links can be downloaded.");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", UA);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && code != 304) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null || loc.length() == 0) throw new Failure("The server redirected without saying where.");
                URL next = new URL(u, loc);
                if (proto.equals("https") && next.getProtocol().equals("http")) {
                    throw new Failure("The link redirects from https to http, which is not safe, so it was stopped.");
                }
                cur = next.toString();
                continue;
            }
            if (code < 200 || code >= 300) {
                c.disconnect();
                throw new Failure("The download failed: the server answered " + code + ".");
            }
            return c;
        }
        throw new Failure("The link redirects too many times.");
    }

    // ---------------------------------------------------------------------------------------- post

    /** POSTs JSON and returns the answer's body. https only (plain http is allowed just for this device itself). */
    static String postJson(String url, String json) throws IOException {
        URL u = new URL(url);
        boolean loopback = u.getHost().equals("localhost") || u.getHost().equals("127.0.0.1");
        if (!u.getProtocol().equals("https") && !(loopback && u.getProtocol().equals("http"))) {
            throw new Failure("Requests are only sent over https.");
        }
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        try {
            c.setRequestMethod("POST");
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", UA);
            byte[] data = json.getBytes(UTF8);
            c.setFixedLengthStreamingMode(data.length);
            OutputStream os = c.getOutputStream();
            os.write(data);
            os.close();
            int code = c.getResponseCode();
            String body = readText(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code < 200 || code >= 300) {
                String why = jsonMessage(body);
                throw new Failure("The server answered " + code + (why != null ? ": " + why : "."));
            }
            return body;
        } finally {
            c.disconnect();
        }
    }

    private static String readText(InputStream in) throws IOException {
        if (in == null) return "";
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1 && bo.size() < 65536) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), UTF8);
        } finally {
            in.close();
        }
    }

    // ------------------------------------------------------------------------------------- helpers

    /** A JSON object from name, value, name, value... (all strings). */
    static String json(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(esc(kv[i])).append("\":\"").append(esc(kv[i + 1])).append('"');
        }
        return sb.append('}').toString();
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static final Pattern SUCCESS = Pattern.compile("\"success\"\\s*:\\s*\"?(true|false)\"?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** True only when the answer says `"success": true` (or "true"); anything else counts as not accepted. */
    static boolean jsonSuccess(String body) {
        if (body == null) return false;
        Matcher m = SUCCESS.matcher(body);
        return m.find() && m.group(1).equalsIgnoreCase("true");
    }

    /** The "message" of a JSON answer, or null (also for web pages). */
    static String jsonMessage(String body) {
        if (body == null) return null;
        Matcher m = MESSAGE.matcher(body);
        if (!m.find()) return null;
        String s = m.group(1).replace("\\\"", "\"").replace("\\n", " ").replace("\\/", "/").replace("\\\\", "\\").trim();
        if (s.length() == 0) return null;
        return s.length() > 220 ? s.substring(0, 220) + "..." : s;
    }

    /** A safe file name for a download: the last part of the link if it is a .zip, else `fallback`. */
    static String fileName(String url, String fallback) {
        String p = url;
        int cut = p.indexOf('?');
        if (cut >= 0) p = p.substring(0, cut);
        cut = p.indexOf('#');
        if (cut >= 0) p = p.substring(0, cut);
        p = p.substring(p.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        return p.toLowerCase().endsWith(".zip") && p.length() > 4 ? p : fallback;
    }

    /** The host part of a link, for showing who gets the data. */
    static String host(String url) {
        try {
            String h = new URL(url).getHost();
            return h.length() > 0 ? h : url;
        } catch (IOException e) {
            return url;
        }
    }

    /** A sentence for any problem, in words a person can act on. */
    static String describe(Throwable t) {
        if (t instanceof Failure) return t.getMessage();
        if (t instanceof UnknownHostException || t instanceof ConnectException || t instanceof NoRouteToHostException) {
            return "Could not reach the server. Check your internet connection.";
        }
        if (t instanceof SocketTimeoutException) return "The connection timed out. Try again.";
        if (t instanceof SSLException) return "A secure connection could not be made.";
        if (t instanceof SecurityException) return "This app is not allowed to use the internet.";
        String m = t.getMessage();
        return "Something went wrong (" + t.getClass().getSimpleName() + (m != null && m.length() > 0 ? ": " + m : "") + ").";
    }
}

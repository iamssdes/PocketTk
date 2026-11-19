package pockettk.preview;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.PreviewEnv;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.TextView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * The PocketTk PC preview: a phone drawn on the desktop that runs the app's real Java code on the desktop JVM.
 * Exit codes tell the launcher what to do next: 75 = settings changed (start again), 76 = rebuild from the .py file
 * and start again, 77 = start again, anything else = done.
 */
public class PhoneHost {
    // ---- settings
    final Map<String, String> opt;
    int wDp, hDp, dpi, cpu, zoom;
    float density; int W, H, sbH, nbH;
    final File settingsFile;
    Activity activity; String appName;
    // ---- state
    boolean layoutDirty = true, homeScreen = false, closed = false, finished = false;
    View content;
    final List<Overlay> overlays = new ArrayList<>();
    String toast; long toastUntil;
    Dlg dialog;
    JFrame frame; Panel panel;

    static class Overlay { View v; WindowManager.LayoutParams p; }
    static class Dlg { String title, msg; String[] btns; Runnable[] acts; Rectangle[] hit; }

    public static void main(String[] args) throws Exception {
        Map<String, String> o = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) o.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        if (!GraphicsEnvironment.isHeadless() || o.containsKey("force")) {
            System.setProperty("sun.java2d.uiScale", "1");
        }
        SwingUtilities.invokeAndWait(() -> new PhoneHost(o).start());
    }

    PhoneHost(Map<String, String> o) {
        opt = o;
        settingsFile = o.containsKey("settings") ? new File(o.get("settings")) : null;
        Properties p = new Properties();
        if (settingsFile != null && settingsFile.isFile()) try (InputStream in = new FileInputStream(settingsFile)) { p.load(in); } catch (IOException ignored) { }
        wDp = geti(o, p, "width", 360); hDp = geti(o, p, "height", 800); dpi = geti(o, p, "dpi", 420); cpu = geti(o, p, "cpu", 100); zoom = geti(o, p, "zoom", 100);
        wDp = Math.max(200, Math.min(wDp, 2000)); hDp = Math.max(300, Math.min(hDp, 3000)); dpi = Math.max(100, Math.min(dpi, 700)); cpu = Math.max(5, Math.min(cpu, 100)); zoom = Math.max(25, Math.min(zoom, 300));
        applyEnv();
        appName = o.getOrDefault("title", "App");
    }
    static int geti(Map<String, String> o, Properties p, String k, int d) {
        try { String v = o.get(k); if (v == null) v = p.getProperty(k); return v == null ? d : Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return d; }
    }
    void applyEnv() {
        density = dpi / 160f;
        W = Math.round(wDp * density); H = Math.round(hDp * density); sbH = Math.round(24 * density); nbH = Math.round(24 * density);
        PreviewEnv.density = density; PreviewEnv.scaledDensity = density; PreviewEnv.widthPx = W; PreviewEnv.heightPx = H; PreviewEnv.cpu = cpu;
    }
    void save() {
        if (settingsFile == null) return;
        Properties p = new Properties(); p.setProperty("width", "" + wDp); p.setProperty("height", "" + hDp); p.setProperty("dpi", "" + dpi); p.setProperty("cpu", "" + cpu); p.setProperty("zoom", "" + zoom);
        try (OutputStream out = new FileOutputStream(settingsFile)) { p.store(out, "PocketTk preview"); } catch (IOException ignored) { }
    }
    void exit(int code) { save(); System.exit(code); }

    // ---- window
    void start() {
        PreviewEnv.sink = new Sink();
        PreviewEnv.windowManager = new Wm();
        View.Hook.onLayout = () -> { layoutDirty = true; repaint(); };
        View.Hook.onPaint = this::repaint;
        Activity.contentChanged = () -> { content = activity == null ? null : activity.content; layoutDirty = true; repaint(); };
        frame = new JFrame("PocketTk Preview - " + appName);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        panel = new Panel();
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        frame.add(controls(), BorderLayout.EAST);
        Rectangle scr = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(Math.min(scr.width, 900), Math.min(scr.height, 940));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        panel.requestFocusInWindow();
        new javax.swing.Timer(530, e -> { TextView.caretOn = !TextView.caretOn; if (View.focused instanceof EditText) repaint(); if (toast != null && System.currentTimeMillis() > toastUntil) { toast = null; repaint(); } }).start();
        launch();
        if (opt.containsKey("script")) runScript(opt.get("script"));
    }

    void launch() {
        try {
            String cn = opt.getOrDefault("class", "apple.gen.MainActivity");
            activity = (Activity) Class.forName(cn).getDeclaredConstructor().newInstance();
            Method(activity, "onCreate");
            content = activity.content;
            Method(activity, "onResume");
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            c.printStackTrace();
            PreviewEnv.sink.toast("The app failed to start: " + c);
        }
        layoutDirty = true; repaint();
    }
    static void Method(Activity a, String name) throws Exception {
        java.lang.reflect.Method m = Activity.class.getDeclaredMethod(name, name.equals("onCreate") ? new Class<?>[]{android.os.Bundle.class} : new Class<?>[0]);
        m.setAccessible(true);
        // call the app's own override (protected) through reflection on its class
        java.lang.reflect.Method mm = null;
        for (Class<?> k = a.getClass(); k != null && mm == null; k = k.getSuperclass()) { try { mm = k.getDeclaredMethod(name, name.equals("onCreate") ? new Class<?>[]{android.os.Bundle.class} : new Class<?>[0]); } catch (NoSuchMethodException e) { } }
        (mm == null ? m : mm).setAccessible(true);
        if (name.equals("onCreate")) (mm == null ? m : mm).invoke(a, new Object[]{null}); else (mm == null ? m : mm).invoke(a);
    }

    // ---- controls on the right
    JComponent controls() {
        JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); p.setPreferredSize(new Dimension(230, 100));
        JLabel t = new JLabel("Phone"); t.setFont(t.getFont().deriveFont(Font.BOLD, 15f)); p.add(t); p.add(Box.createVerticalStrut(8));
        String[] names = {"Small phone  360 x 640 @ 320", "Phone  360 x 800 @ 420", "Large phone  412 x 915 @ 420", "Small tablet  600 x 960 @ 240", "Tablet  800 x 1280 @ 240", "Custom"};
        int[][] pre = {{360, 640, 320}, {360, 800, 420}, {412, 915, 420}, {600, 960, 240}, {800, 1280, 240}};
        JComboBox<String> dev = new JComboBox<>(names); dev.setMaximumSize(new Dimension(300, 28)); p.add(dev);
        int sel = names.length - 1; for (int i = 0; i < pre.length; i++) if (pre[i][0] == wDp && pre[i][1] == hDp && pre[i][2] == dpi) sel = i; dev.setSelectedIndex(sel);
        JSpinner sw = spin(wDp, 200, 2000, 10), sh = spin(hDp, 300, 3000, 10), sd = spin(dpi, 100, 700, 10);
        p.add(Box.createVerticalStrut(8)); p.add(row("Width (dp)", sw)); p.add(row("Height (dp)", sh)); p.add(row("Density (dpi)", sd));
        dev.addActionListener(e -> { int i = dev.getSelectedIndex(); if (i < pre.length) { sw.setValue(pre[i][0]); sh.setValue(pre[i][1]); sd.setValue(pre[i][2]); } });
        JLabel cl = new JLabel("CPU strength: " + cpu + "%"); JSlider cs = new JSlider(5, 100, cpu);
        cs.addChangeListener(e -> cl.setText("CPU strength: " + cs.getValue() + "%"));
        p.add(Box.createVerticalStrut(10)); p.add(cl); p.add(cs);
        JLabel note = new JLabel("<html><small>Slows the app down on purpose to imitate a weaker phone. It is a simulation.</small></html>"); p.add(note);
        JLabel zl = new JLabel("Zoom: " + zoom + "%"); JSlider zs = new JSlider(25, 300, zoom);
        zs.addChangeListener(e -> { zl.setText("Zoom: " + zs.getValue() + "%"); zoom = zs.getValue(); repaint(); });
        p.add(Box.createVerticalStrut(10)); p.add(zl); p.add(zs);
        JButton apply = new JButton("Apply and restart app");
        apply.addActionListener(e -> { wDp = (Integer) sw.getValue(); hDp = (Integer) sh.getValue(); dpi = (Integer) sd.getValue(); cpu = cs.getValue(); zoom = zs.getValue(); exit(75); });
        JButton rotate = new JButton("Rotate"); rotate.addActionListener(e -> { int a = (Integer) sw.getValue(), b = (Integer) sh.getValue(); wDp = b; hDp = a; dpi = (Integer) sd.getValue(); cpu = cs.getValue(); exit(75); });
        JButton reload = new JButton("Reload code (rebuild)"); reload.addActionListener(e -> { wDp = (Integer) sw.getValue(); hDp = (Integer) sh.getValue(); dpi = (Integer) sd.getValue(); cpu = cs.getValue(); exit(76); });
        JButton back = new JButton("Back"), home = new JButton("Home");
        back.addActionListener(e -> goBack()); home.addActionListener(e -> goHome());
        p.add(Box.createVerticalStrut(14)); for (JButton b : new JButton[]{apply, rotate, reload}) { b.setAlignmentX(0f); b.setMaximumSize(new Dimension(300, 30)); p.add(b); p.add(Box.createVerticalStrut(4)); }
        JPanel nav = new JPanel(new GridLayout(1, 2, 4, 0)); nav.add(back); nav.add(home); nav.setMaximumSize(new Dimension(300, 30)); nav.setAlignmentX(0f); p.add(nav);
        p.add(Box.createVerticalStrut(10));
        JLabel tip = new JLabel("<html><small>Click and drag with the mouse; type on the keyboard; Esc = Back. Mouse wheel scrolls. Some things (sound, sqlite3, file pickers, notifications) do not work in the preview.</small></html>"); p.add(tip);
        return p;
    }
    static JSpinner spin(int v, int lo, int hi, int step) { JSpinner s = new JSpinner(new SpinnerNumberModel(v, lo, hi, step)); return s; }
    static JComponent row(String l, JComponent c) { JPanel r = new JPanel(new BorderLayout()); r.add(new JLabel(l), BorderLayout.WEST); r.add(c, BorderLayout.EAST); r.setMaximumSize(new Dimension(300, 28)); return r; }

    // ---- navigation
    void goBack() {
        if (closed) return;
        if (dialog != null) { dialog = null; repaint(); return; }
        try { KeyEvent_back(); } catch (Throwable t) { t.printStackTrace(); }
    }
    void KeyEvent_back() {
        if (activity != null && !homeScreen) { PreviewEnv.edt(() -> activity.onBackPressed()).run(); }
        repaint();
    }
    void goHome() {
        if (activity == null || closed) return;
        if (!homeScreen) { homeScreen = true; try { Method(activity, "onPause"); Method(activity, "onStop"); } catch (Throwable t) { t.printStackTrace(); } }
        repaint();
    }
    void reopen() {
        if (closed) { exit(77); return; }
        if (homeScreen) { homeScreen = false; try { Method(activity, "onResume"); } catch (Throwable t) { t.printStackTrace(); } }
        repaint();
    }

    // ---- hooks the Android classes call
    class Sink implements PreviewEnv.Sink {
        public void toast(String s) { SwingUtilities.invokeLater(() -> { toast = s; toastUntil = System.currentTimeMillis() + 2600; repaint(); }); }
        public void note(String s) { System.out.println("[preview] " + s); }
        public void finish() { SwingUtilities.invokeLater(() -> { finished = true; if (overlays.isEmpty()) closed = true; repaint(); }); }
        public void startActivity(Intent i) {
            if (i.data != null && i.action != null && i.action.endsWith("VIEW") && i.data.toString().startsWith("http")) {
                try { Desktop.getDesktop().browse(new java.net.URI(i.data.toString())); toast("Opening " + i.data); return; } catch (Throwable t) { }
            }
            toast("Not available in the PC preview: " + (i.action == null ? i.cls : i.action));
        }
        public void dialog(String t, String m, String[] b, Runnable[] a) { SwingUtilities.invokeLater(() -> { Dlg d = new Dlg(); d.title = t; d.msg = m; d.btns = b; d.acts = a; dialog = d; repaint(); }); }
    }
    class Wm implements WindowManager {
        public void addView(View v, ViewGroup.LayoutParams p) { Overlay o = new Overlay(); o.v = v; o.p = (LayoutParams) p; Runnable r = () -> { overlays.add(o); layoutDirty = true; repaint(); }; if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r); }
        public void removeView(View v) { Runnable r = () -> { overlays.removeIf(o -> o.v == v); if (finished && overlays.isEmpty()) closed = true; repaint(); }; if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r); }
        public void updateViewLayout(View v, ViewGroup.LayoutParams p) { for (Overlay o : overlays) if (o.v == v) o.p = (LayoutParams) p; layoutDirty = true; repaint(); }
    }

    // ---- painting
    final java.util.concurrent.atomic.AtomicBoolean pending = new java.util.concurrent.atomic.AtomicBoolean();
    void repaint() { if (panel != null && pending.compareAndSet(false, true)) SwingUtilities.invokeLater(() -> { pending.set(false); panel.repaint(); }); }

    void relayout() {
        int ch = H - sbH - nbH;
        if (content != null) { content.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ch, View.MeasureSpec.EXACTLY)); content.layout(0, 0, W, ch); }
        for (Overlay o : overlays) {
            int pw = o.p.width, ph = o.p.height;
            o.v.measure(View.MeasureSpec.makeMeasureSpec(pw >= 0 ? pw : W, pw >= 0 ? View.MeasureSpec.EXACTLY : (pw == -1 ? View.MeasureSpec.EXACTLY : View.MeasureSpec.AT_MOST)),
                        View.MeasureSpec.makeMeasureSpec(ph >= 0 ? ph : H, ph >= 0 ? View.MeasureSpec.EXACTLY : (ph == -1 ? View.MeasureSpec.EXACTLY : View.MeasureSpec.AT_MOST)));
            int w = o.v.getMeasuredWidth(), h = o.v.getMeasuredHeight(), g = o.p.gravity, x = o.p.x, y = o.p.y;
            int gh = Gravity.h(g), gv = Gravity.v(g);
            if (gh == 1) x += (W - w) / 2; else if (gh == 5) x = W - w - x;
            if (gv == Gravity.CENTER_VERTICAL) y += (H - h) / 2; else if (gv == Gravity.BOTTOM) y = H - h - y;
            o.v.layout(x, y, x + w, y + h);
        }
        layoutDirty = false;
        if (content instanceof ViewGroup) ((ViewGroup) content).fireGlobalLayout();
        for (Overlay o : overlays) if (o.v instanceof ViewGroup) ((ViewGroup) o.v).fireGlobalLayout();
    }

    class Panel extends JPanel {
        double scale; int ox, oy; int bez; Rectangle backHit, homeHit;
        Panel() {
            setBackground(new Color(0x2B2D31)); setFocusable(true); setFocusTraversalKeysEnabled(false);
            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { requestFocusInWindow(); touch(e, MotionEvent.ACTION_DOWN); }
                public void mouseDragged(MouseEvent e) { touch(e, MotionEvent.ACTION_MOVE); }
                public void mouseReleased(MouseEvent e) { touch(e, MotionEvent.ACTION_UP); }
                public void mouseWheelMoved(MouseWheelEvent e) { wheel(e); }
            };
            addMouseListener(ma); addMouseMotionListener(ma); addMouseWheelListener(ma);
            addKeyListener(new KeyAdapter() {
                public void keyTyped(KeyEvent e) { char c = e.getKeyChar(); if (e.isControlDown() || e.isAltDown() || c == KeyEvent.CHAR_UNDEFINED) return; if (c == '\b' || c == 127 || c == 27) return; if (c < 32 && c != '\n' && c != '\t') return; typed(c == '\r' ? '\n' : c); }
                public void keyPressed(KeyEvent e) { pressedKey(e); }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            long t0 = System.nanoTime();
            if (layoutDirty) relayout();
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            double bz = 16 * density / 2.0;                                           // bezel thickness in screen px
            double fit = Math.min((getWidth() - 24) / (W + 2 * bz), (getHeight() - 24) / (H + 2 * bz));
            scale = Math.max(0.05, fit * zoom / 100.0); bez = (int) Math.round(bz * scale);
            int pw = (int) Math.round((W + 2 * bz) * scale), ph = (int) Math.round((H + 2 * bz) * scale);
            int px = Math.max(8, (getWidth() - pw) / 2), py = Math.max(8, (getHeight() - ph) / 2);
            ox = px + bez; oy = py + bez;
            double rr = 34 * density * scale;
            g.setColor(new Color(0x0B0B0D)); g.fill(new RoundRectangle2D.Double(px, py, pw, ph, rr + bez, rr + bez));
            g.setColor(new Color(0x4A4C52)); g.setStroke(new BasicStroke(1.5f)); g.draw(new RoundRectangle2D.Double(px + 0.5, py + 0.5, pw - 1, ph - 1, rr + bez, rr + bez));
            RoundRectangle2D screen = new RoundRectangle2D.Double(ox, oy, W * scale, H * scale, rr, rr);
            Shape old = g.getClip(); g.clip(screen);
            g.translate(ox, oy); g.scale(scale, scale);
            paintScreen(g);
            g.dispose();
            PreviewEnv.throttle(System.nanoTime() - t0);
        }
    }

    void paintScreen(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0xE9EEF8), 0, H, new Color(0xCBD4E6))); g.fillRect(0, 0, W, H);          // wallpaper
        if (homeScreen || closed) paintHome(g);
        else if (content != null && !finished) { g.translate(0, sbH); g.setClip(0, 0, W, H - sbH - nbH); content.draw(new Canvas(g)); g.setClip(null); g.translate(0, -sbH); }
        if (!closed) for (Overlay o : new ArrayList<>(overlays)) { Graphics2D g2 = (Graphics2D) g.create(); g2.translate(o.v.getLeft(), o.v.getTop()); g2.clipRect(0, 0, o.v.getWidth(), o.v.getHeight()); o.v.draw(new Canvas(g2)); g2.dispose(); }
        paintBars(g);
        if (toast != null) paintToast(g);
        if (dialog != null) paintDialog(g);
    }
    void paintBars(Graphics2D g) {
        g.setColor(new Color(0xFF000000)); g.fillRect(0, 0, W, sbH);
        if (content == null || homeScreen || closed) { g.setColor(new Color(0x66000000)); g.fillRect(0, 0, W, sbH); }
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, Math.round(11 * density)));
        String time = new java.text.SimpleDateFormat("h:mm").format(new Date()); g.drawString(time, 16 * density, sbH * 0.72f);
        g.setColor(new Color(0x333338)); g.fill(new Ellipse2D.Float(W / 2f - 5 * density, sbH / 2f - 5 * density, 10 * density, 10 * density));
        g.setColor(Color.WHITE); float bx = W - 36 * density, by = sbH / 2f - 5 * density; g.draw(new RoundRectangle2D.Float(bx, by, 20 * density, 10 * density, 3 * density, 3 * density)); g.fill(new Rectangle2D.Float(bx + 2 * density, by + 2 * density, 14 * density, 6 * density));
        int[] bars = {3, 5, 7, 9}; for (int i = 0; i < 4; i++) g.fill(new Rectangle2D.Float(bx - (30 - i * 6) * density, sbH / 2f + 5 * density - bars[i] * density, 4 * density, bars[i] * density));
        g.setColor(new Color(0xFF000000)); g.fillRect(0, H - nbH, W, nbH);
        g.setColor(new Color(0xCCFFFFFF)); g.fill(new RoundRectangle2D.Float(W / 2f - 55 * density, H - nbH / 2f - 2 * density, 110 * density, 4 * density, 2 * density, 2 * density));
    }
    void paintHome(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.round(14 * density)));
        String msg = closed ? "The app closed." : "Home screen"; FontMetrics fm = g.getFontMetrics();
        int sz = Math.round(56 * density), x = (W - sz) / 2, y = H / 2 - sz; g.setColor(new Color(0xFFFFFF, false)); g.setColor(new Color(0x3C4A6B)); g.fill(new RoundRectangle2D.Float(x, y, sz, sz, sz * .28f, sz * .28f));
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, Math.round(22 * density))); String ini = appName.isEmpty() ? "?" : appName.substring(0, 1).toUpperCase(); g.drawString(ini, x + (sz - g.getFontMetrics().stringWidth(ini)) / 2, y + sz * 0.67f);
        g.setColor(new Color(0x202124)); g.setFont(new Font("SansSerif", Font.PLAIN, Math.round(13 * density))); g.drawString(appName, (W - g.getFontMetrics().stringWidth(appName)) / 2, y + sz + 18 * density);
        g.setColor(new Color(0x5F6368)); String hint = closed ? "Tap the icon to start it again" : "Tap the icon to open it"; g.drawString(hint, (W - g.getFontMetrics().stringWidth(hint)) / 2, y + sz + 40 * density);
        appIcon = new Rectangle(x, y, sz, sz);
    }
    Rectangle appIcon;
    void paintToast(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.round(13 * density))); FontMetrics fm = g.getFontMetrics();
        int maxW = W - Math.round(48 * density); List<String> lines = wrap(toast, fm, maxW - Math.round(32 * density));
        int lh = fm.getHeight(), w = 0; for (String l : lines) w = Math.max(w, fm.stringWidth(l)); w += Math.round(32 * density); int h = lines.size() * lh + Math.round(20 * density);
        int x = (W - w) / 2, y = H - nbH - h - Math.round(40 * density);
        g.setColor(new Color(0xE6303030, true)); g.fill(new RoundRectangle2D.Float(x, y, w, h, h / 2f > 30 * density ? 30 * density : h / 2f, h / 2f > 30 * density ? 30 * density : h / 2f));
        g.setColor(Color.WHITE); int ty = y + Math.round(10 * density) + fm.getAscent(); for (String l : lines) { g.drawString(l, x + (w - fm.stringWidth(l)) / 2f, ty); ty += lh; }
    }
    void paintDialog(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 140)); g.fillRect(0, sbH, W, H - sbH - nbH);
        Font tf = new Font("SansSerif", Font.BOLD, Math.round(17 * density)), mf = new Font("SansSerif", Font.PLAIN, Math.round(14 * density));
        int cw = W - Math.round(64 * density), pad = Math.round(20 * density);
        g.setFont(mf); FontMetrics fm = g.getFontMetrics(); List<String> ml = wrap(dialog.msg, fm, cw - 2 * pad);
        g.setFont(tf); FontMetrics tfm = g.getFontMetrics(); List<String> tl = wrap(dialog.title, tfm, cw - 2 * pad);
        int h = pad + (dialog.title.isEmpty() ? 0 : tl.size() * tfm.getHeight() + pad / 2) + ml.size() * fm.getHeight() + pad + Math.round(48 * density);
        int x = (W - cw) / 2, y = (H - h) / 2;
        g.setColor(new Color(0xFFFFFFFF)); g.fill(new RoundRectangle2D.Float(x, y, cw, h, 28 * density, 28 * density));
        int ty = y + pad; g.setColor(new Color(0xFF202124));
        if (!dialog.title.isEmpty()) { g.setFont(tf); for (String l : tl) { ty += tfm.getAscent(); g.drawString(l, x + pad, ty); ty += tfm.getDescent(); } ty += pad / 2; }
        g.setFont(mf); g.setColor(new Color(0xFF444746)); for (String l : ml) { ty += fm.getAscent(); g.drawString(l, x + pad, ty); ty += fm.getDescent(); }
        dialog.hit = new Rectangle[dialog.btns.length]; int bx = x + cw - pad; g.setFont(tf.deriveFont(Font.BOLD, 14f * density));
        for (int i = dialog.btns.length - 1; i >= 0; i--) {
            FontMetrics bf = g.getFontMetrics(); int bw = bf.stringWidth(dialog.btns[i]) + Math.round(24 * density), bh = Math.round(40 * density); bx -= bw;
            int by = y + h - pad / 2 - bh; dialog.hit[i] = new Rectangle(bx, by, bw, bh);
            g.setColor(new Color(0xFF1A73E8)); g.drawString(dialog.btns[i], bx + Math.round(12 * density), by + (bh + bf.getAscent() - bf.getDescent()) / 2); bx -= Math.round(4 * density);
        }
    }
    static List<String> wrap(String s, FontMetrics fm, int maxW) {
        List<String> out = new ArrayList<>();
        for (String para : s.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String w : para.split(" ")) { String t = line.length() == 0 ? w : line + " " + w; if (fm.stringWidth(t) > maxW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(w); } else { line = new StringBuilder(t); } }
            out.add(line.toString());
        }
        return out;
    }

    // ---- input
    int pressedButton = 0; boolean pressedOverlay;
    void touch(MouseEvent e, int action) {
        if (!SwingUtilities.isLeftMouseButton(e) && action == MotionEvent.ACTION_DOWN) return;
        float sx = (float) ((e.getX() - panel.ox) / panel.scale), sy = (float) ((e.getY() - panel.oy) / panel.scale);
        Runnable r = () -> {
            if (dialog != null) { if (action == MotionEvent.ACTION_UP && dialog.hit != null) for (int i = 0; i < dialog.hit.length; i++) if (dialog.hit[i] != null && dialog.hit[i].contains(sx, sy)) { Runnable a = dialog.acts[i]; dialog = null; repaint(); a.run(); return; } return; }
            if (sx < 0 || sy < 0 || sx > W || sy > H) return;
            if (action == MotionEvent.ACTION_DOWN && sy > H - nbH) { if (sx < W / 3f) goBack(); else goHome(); return; }
            MotionEvent me = MotionEvent.obtain(action, sx, sy, sx, sy + 0);
            if (action == MotionEvent.ACTION_DOWN) {
                target = null;
                for (int i = overlays.size() - 1; i >= 0; i--) {
                    Overlay o = overlays.get(i); if ((o.p.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) continue;
                    if (sx >= o.v.getLeft() && sx < o.v.getRight() && sy >= o.v.getTop() && sy < o.v.getBottom()) { target = o.v; targetX = o.v.getLeft(); targetY = o.v.getTop(); break; }
                    if ((o.p.flags & WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH) != 0) { MotionEvent oe = MotionEvent.obtain(MotionEvent.ACTION_OUTSIDE, sx, sy, sx, sy); o.v.dispatchTouchEvent(oe); }
                }
                if (target == null && !homeScreen && !closed && !finished && content != null) { target = content; targetX = 0; targetY = sbH; }
            }
            if (target == null && (homeScreen || closed) && action == MotionEvent.ACTION_UP && appIcon != null && appIcon.contains(sx, sy)) { reopen(); return; }
            if (target != null) {
                MotionEvent te = MotionEvent.obtain(action, sx - targetX, sy - targetY, sx, sy); View.RootInfo.originX = 0; View.RootInfo.originY = 0;
                target.dispatchTouchEvent(te);
            }
            if (action == MotionEvent.ACTION_UP) target = null;
            repaint();
        };
        PreviewEnv.edt(r).run();
    }
    View target; int targetX, targetY;
    void wheel(MouseWheelEvent e) {
        float sx = (float) ((e.getX() - panel.ox) / panel.scale), sy = (float) ((e.getY() - panel.oy) / panel.scale);
        PreviewEnv.edt(() -> {
            for (int i = overlays.size() - 1; i >= 0; i--) { Overlay o = overlays.get(i); if (o.v.onWheel(sx - o.v.getLeft(), sy - o.v.getTop(), (float) e.getPreciseWheelRotation())) { repaint(); return; } }
            if (content != null && !homeScreen && !closed) content.onWheel(sx, sy - sbH, (float) e.getPreciseWheelRotation());
            repaint();
        }).run();
    }
    void typed(char c) {
        PreviewEnv.edt(() -> { if (View.focused instanceof EditText) { ((EditText) View.focused).typeChar(c); TextView.caretOn = true; repaint(); } }).run();
    }
    void pressedKey(KeyEvent e) {
        int vk = e.getKeyCode();
        if (vk == KeyEvent.VK_ESCAPE) { goBack(); return; }
        if (e.isControlDown() && vk == KeyEvent.VK_V) {
            try { String s = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor); PreviewEnv.edt(() -> { if (View.focused instanceof EditText) ((EditText) View.focused).paste(s); repaint(); }).run(); } catch (Throwable t) { }
            return;
        }
        int code = vk == KeyEvent.VK_BACK_SPACE ? android.view.KeyEvent.KEYCODE_DEL : vk == KeyEvent.VK_DELETE ? android.view.KeyEvent.KEYCODE_FORWARD_DEL : vk == KeyEvent.VK_LEFT ? android.view.KeyEvent.KEYCODE_DPAD_LEFT
                : vk == KeyEvent.VK_RIGHT ? android.view.KeyEvent.KEYCODE_DPAD_RIGHT : vk == KeyEvent.VK_HOME ? android.view.KeyEvent.KEYCODE_MOVE_HOME : vk == KeyEvent.VK_END ? android.view.KeyEvent.KEYCODE_MOVE_END : 0;
        if (code != 0) PreviewEnv.edt(() -> { View f = View.focused; if (f != null) f.onKeyDown(code, new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code)); TextView.caretOn = true; repaint(); }).run();
    }

    // ---- scripted actions for automated checks: "click X Y;type TEXT;key back;wait MS;shot FILE.png;exit" (X Y in screen px)
    void runScript(String script) {
        final String[] steps = script.split(";"); final int[] i = {0};
        javax.swing.Timer t = new javax.swing.Timer(250, null);
        t.addActionListener(ev -> {
            if (i[0] >= steps.length) { t.stop(); return; }
            String st = steps[i[0]++].trim(); String[] w = st.split(" ", 3);
            try {
                switch (w[0]) {
                    case "click": click(Float.parseFloat(w[1]), Float.parseFloat(w[2])); break;
                    case "type": for (char c : st.substring(5).toCharArray()) typed(c); break;
                    case "key": if (w[1].equals("back")) goBack(); else if (w[1].equals("home")) goHome(); else if (w[1].equals("del")) panelKey(java.awt.event.KeyEvent.VK_BACK_SPACE); break;
                    case "wheel": wheelAt(Float.parseFloat(w[1]), Float.parseFloat(w[2].split(" ")[0]), Float.parseFloat(w[2].split(" ")[1])); break;
                    case "wait": t.setInitialDelay(Integer.parseInt(w[1])); t.restart(); break;
                    case "shot": shot(w[1]); break;
                    case "layout": relayout(); break;
                    case "apply": { String[] q = st.split(" "); wDp = Integer.parseInt(q[1]); hDp = Integer.parseInt(q[2]); dpi = Integer.parseInt(q[3]); cpu = Integer.parseInt(q[4]); exit(75); break; }
                    case "drag": { String[] q = st.split(" "); drag(Float.parseFloat(q[1]), Float.parseFloat(q[2]), Float.parseFloat(q[3]), Float.parseFloat(q[4])); break; }
                    case "dump": System.out.println("dirty=" + layoutDirty + " panel=" + panel.getWidth() + "x" + panel.getHeight() + " showing=" + panel.isShowing() + " content=" + content + " overlays=" + overlays.size() + " home=" + homeScreen + " closed=" + closed); dump(content, 0); for (Overlay o : overlays) { System.out.println("overlay x=" + o.p.x + " y=" + o.p.y + " flags=" + o.p.flags + " w=" + o.p.width + " h=" + o.p.height); dump(o.v, 1); } break;
                    case "exit": exit(0); break;
                }
            } catch (Throwable x) { x.printStackTrace(); }
        });
        t.setRepeats(true); t.start();
    }
    void dump(View v, int d) {
        if (v == null) return; StringBuilder b = new StringBuilder(); for (int i = 0; i < d; i++) b.append("  ");
        b.append(v.getClass().getSimpleName()).append(" [").append(v.getLeft()).append(',').append(v.getTop()).append(' ').append(v.getWidth()).append('x').append(v.getHeight()).append("] vis=").append(v.visibility);
        if (v instanceof TextView) b.append(" text=\"").append(((TextView) v).getText()).append('"');
        System.out.println(b); if (v instanceof ViewGroup) for (View k : ((ViewGroup) v).kids) dump(k, d + 1);
    }
    void panelKey(int vk) { pressedKey(new java.awt.event.KeyEvent(panel, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, vk, (char) 0)); }
    void click(float sx, float sy) {
        int px = (int) (panel.ox + sx * panel.scale), py = (int) (panel.oy + sy * panel.scale);
        touch(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK, px, py, 1, false, MouseEvent.BUTTON1), MotionEvent.ACTION_DOWN);
        touch(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, px, py, 1, false, MouseEvent.BUTTON1), MotionEvent.ACTION_UP);
    }
    void drag(float x1, float y1, float x2, float y2) {
        int ax = (int) (panel.ox + x1 * panel.scale), ay = (int) (panel.oy + y1 * panel.scale), bx = (int) (panel.ox + x2 * panel.scale), by = (int) (panel.oy + y2 * panel.scale);
        touch(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK, ax, ay, 1, false, MouseEvent.BUTTON1), MotionEvent.ACTION_DOWN);
        for (int i = 1; i <= 6; i++) touch(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, 0, MouseEvent.BUTTON1_DOWN_MASK, ax + (bx - ax) * i / 6, ay + (by - ay) * i / 6, 0, false, MouseEvent.NOBUTTON), MotionEvent.ACTION_MOVE);
        touch(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, bx, by, 1, false, MouseEvent.BUTTON1), MotionEvent.ACTION_UP);
    }
    void wheelAt(float sx, float sy, float amount) {
        int px = (int) (panel.ox + sx * panel.scale), py = (int) (panel.oy + sy * panel.scale);
        wheel(new MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL, 0, 0, px, py, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, (int) amount));
    }
    void shot(String file) throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(panel.getWidth(), panel.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics(); panel.paint(g); g.dispose(); javax.imageio.ImageIO.write(img, "png", new File(file));
        System.out.println("[shot] " + file + " " + img.getWidth() + "x" + img.getHeight());
    }
}

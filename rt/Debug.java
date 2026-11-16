package apple.rt;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Debug panel for apps built with `--debug` (apkapple build/run/update --debug). The app is shown on top and a
 * log panel below it with everything the program prints, plus errors and runtime warnings.
 * Does nothing unless {@link #on} is set (by the generated MainActivity).
 */
public final class Debug {
    private Debug() {}

    public static boolean on = false;

    static final int NORMAL = 0xFFE6EDF3, ERR = 0xFFFF6B6B, WARN = 0xFFFFC857, INFO = 0xFF8BE9FD;
    static final int MAX_LINES = 500;

    static final class Line {
        final String time, text; final int color;
        Line(String time, String text, int color) { this.time = time; this.text = text; this.color = color; }
    }

    static final List<Line> lines = new ArrayList<Line>();
    static final StringBuilder partial = new StringBuilder();
    static final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.US);
    static boolean refreshQueued;
    static TextView body, toggle;
    static ScrollView scroll;
    static Context ctx;
    static boolean collapsed;

    // ------------------------------------------------------------------ feeding the panel (any thread)

    public static void out(String s) { add(s, NORMAL); }
    public static void err(String s) { add(s, ERR); }
    public static void warn(String s) { add(s + "\n", WARN); }
    public static void info(String s) { add(s + "\n", INFO); }

    static void add(String s, int color) {
        if (!on || s == null || s.length() == 0) return;
        synchronized (lines) {
            partial.append(s);
            int nl;
            while ((nl = partial.indexOf("\n")) >= 0) {
                String one = partial.substring(0, nl);
                partial.delete(0, nl + 1);
                lines.add(new Line(clock.format(new Date()), one, color));
                if (lines.size() > MAX_LINES) lines.remove(0);
            }
            if (partial.length() > 0) lastPartialColor = color;
            if (refreshQueued) return;
            refreshQueued = true;
        }
        Ui.postUi(new Runnable() { public void run() { refresh(); } });
    }
    static int lastPartialColor = NORMAL;

    // ------------------------------------------------------------------ view

    /** Put the app view on top and the log panel below it. Returns the view to show as the window content. */
    static View wrap(Context c, View app) {
        ctx = c;
        LinearLayout stack = new LinearLayout(c);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.addView(app, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xF0101418);

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(8), Ui.dp(2), Ui.dp(4), Ui.dp(2));
        bar.setBackgroundColor(0xFF1E252C);
        TextView title = new TextView(c);
        title.setText("DEBUG LOG");
        title.setTextColor(INFO);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(button(c, "COPY", new Runnable() { public void run() { copy(); } }));
        bar.addView(button(c, "CLEAR", new Runnable() { public void run() { clear(); } }));
        toggle = button(c, "\u25BC", new Runnable() { public void run() { collapse(); } });
        bar.addView(toggle);
        panel.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll = new ScrollView(c);
        body = new TextView(c);
        body.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        body.setTextColor(NORMAL);
        body.setTextIsSelectable(true);
        body.setPadding(Ui.dp(8), Ui.dp(4), Ui.dp(8), Ui.dp(4));
        scroll.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(150)));

        stack.addView(panel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        info("debug build: prints, errors and warnings appear here");
        return stack;
    }

    static TextView button(Context c, String label, final Runnable r) {
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextColor(0xFFC9D1D9);
        t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        t.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(10), Ui.dp(6));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return t;
    }

    static void collapse() {
        collapsed = !collapsed;
        scroll.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        toggle.setText(collapsed ? "\u25B2" : "\u25BC");
    }

    static void clear() {
        synchronized (lines) { lines.clear(); partial.setLength(0); }
        refresh();
    }

    static void copy() {
        try {
            StringBuilder sb = new StringBuilder();
            synchronized (lines) {
                for (Line l : lines) sb.append(l.time).append(' ').append(l.text).append('\n');
                sb.append(partial);
            }
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("ApkApple log", sb.toString()));
            Ui.toast("Log copied");
        } catch (Throwable t) { Ui.toast("Could not copy the log"); }
    }

    static void refresh() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        synchronized (lines) {
            refreshQueued = false;
            if (body == null) return;
            for (Line l : lines) append(sb, l.time + " " + l.text + "\n", l.color);
            if (partial.length() > 0) append(sb, partial.toString(), lastPartialColor);
        }
        body.setText(sb);
        scroll.post(new Runnable() { public void run() { scroll.fullScroll(View.FOCUS_DOWN); } });
    }

    static void append(SpannableStringBuilder sb, String s, int color) {
        int from = sb.length();
        sb.append(s);
        sb.setSpan(new ForegroundColorSpan(color), from, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}

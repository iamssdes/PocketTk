package apple.rt;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The PocketTk Request page, opened from "Submit a request" on the About page.
 * Name (optional), email and request details (both required). "Send request" sends the request from inside the app:
 * a JSON post to Contact.FORM_ENDPOINT + Contact.REQUEST_EMAIL, a form-to-email service, so no server of our own is
 * needed. If that fails (offline, service refuses), the person's email app opens with the request filled in instead.
 */
public class Request extends Activity {
    static final int BG = About.BG, TEXT = About.TEXT, DIM = About.DIM, HEAD = About.HEAD, WARN = About.WARN;

    float density = 1f;
    EditText name, email, details;
    TextView status, sendBtn;
    boolean sending;

    int dp(float v) { return Math.round(v * density); }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        setTitle("PocketTk Request");

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(36), dp(20), dp(28));
        sv.addView(page, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        text(page, "Request the consumer version", 30, TEXT, true, 0);
        text(page, "Consumer is PocketTk without #use: builtin-editing. CONSUMER DOES NOT MEAN FULL.", 13, WARN, true, 8);
        text(page, "Tell me who you are and what you want to use PocketTk for. The request is sent to me by email.",
                15, DIM, false, 8);
        text(page, "It is sent over the internet through " + Online.host(Contact.FORM_ENDPOINT)
                + ". If that does not work, your email app opens with the request filled in.", 12, DIM, false, 6);

        label(page, "Name (optional)");
        name = field(page, "Your name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, 1);

        label(page, "Email *");
        email = field(page, "you@example.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 1);

        label(page, "Request details *");
        details = field(page, "What will you use PocketTk for?",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 5);

        text(page, "* required", 12, DIM, false, 8);

        TextView send = text(page, "Send request", 16, BG, true, 22);
        sendBtn = send;
        send.setGravity(Gravity.CENTER);
        send.setPadding(dp(12), dp(14), dp(12), dp(14));
        GradientDrawable btn = new GradientDrawable();
        btn.setColor(HEAD);
        btn.setCornerRadius(dp(10));
        send.setBackground(btn);
        send.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { send(); } });

        status = text(page, "", 14, WARN, false, 12);

        TextView back = text(page, "Back", 15, HEAD, true, 24);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(12), dp(14), dp(12), dp(14));
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });

        setContentView(sv);
    }

    void send() {
        if (sending) return;
        String n = name.getText().toString().trim();
        String e = email.getText().toString().trim();
        String d = details.getText().toString().trim();
        View bad = null;
        if (e.length() == 0) {
            email.setError("Email is required");
            bad = email;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
            email.setError("That does not look like an email address");
            bad = email;
        }
        if (d.length() == 0) {
            details.setError("Request details are required");
            if (bad == null) bad = details;
        }
        if (bad != null) {
            bad.requestFocus();
            status.setText("");
            return;
        }
        if (Contact.REQUEST_EMAIL.length() == 0) {
            status.setText("Requests are not set up in this build: it has no address to send them to.");
            return;
        }

        String built = Ui.builtWith != null && Ui.builtWith.length() > 0 ? " (app built with PocketTk " + Ui.builtWith + ")" : "";
        String body = (n.length() > 0 ? "Name: " + n + "\n" : "")
                + "Email: " + e + "\n\n"
                + "What I want to use PocketTk for:\n" + d + "\n\n"
                + "-- sent from the PocketTk Request page" + built + "\n";
        String subject = "PocketTk consumer version request" + (n.length() > 0 ? " - " + n : "");

        final String fSubject = subject, fBody = body;
        final String json = Online.json("name", n.length() > 0 ? n : "(not given)", "email", e, "message", body,
                "_subject", subject, "_captcha", "false");
        sending = true;
        sendBtn.setAlpha(0.5f);
        status.setTextColor(WARN);
        status.setText("Sending your request...");
        Thread th = new Thread(new Runnable() {
            public void run() {
                String problem = null;
                try {
                    String answer = Online.postJson(Contact.FORM_ENDPOINT + Contact.REQUEST_EMAIL, json);
                    if (!Online.jsonSuccess(answer)) {
                        String why = Online.jsonMessage(answer);
                        problem = "The request was not accepted" + (why != null ? ": " + why : ".");
                    }
                } catch (Throwable t) {
                    problem = Online.describe(t);
                }
                final String fProblem = problem;
                runOnUiThread(new Runnable() { public void run() { sent(fProblem, fSubject, fBody); } });
            }
        }, "pockettk-request");
        th.setDaemon(true);
        th.start();
    }

    /** Back on the UI thread: `problem` is null when the request was sent. */
    void sent(String problem, String subject, String body) {
        sending = false;
        if (isFinishing()) return;
        sendBtn.setAlpha(1f);
        if (problem == null) {
            details.setText("");
            status.setTextColor(HEAD);
            status.setText("Your request was sent. Thank you!");
            return;
        }
        status.setTextColor(WARN);
        status.setText(problem + " Trying your email app instead. " + openMailApp(subject, body));
    }

    /** The fallback: the email app with the request filled in (or the request copied). Returns what to tell the person. */
    String openMailApp(String subject, String body) {
        Intent i = new Intent(Intent.ACTION_SENDTO);
        i.setData(Uri.parse("mailto:"));                       // only email apps answer this
        i.putExtra(Intent.EXTRA_EMAIL, new String[] { Contact.REQUEST_EMAIL });
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, body);
        try {
            startActivity(i);
            return "Your email app opened with the request filled in. Press send there to finish.";
        } catch (Throwable t) {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("PocketTk request", "To: " + Contact.REQUEST_EMAIL + "\nSubject: " + subject + "\n\n" + body));
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) { /* nothing more to try */ }
            return "No email app was found. The request was copied: send it to " + Contact.REQUEST_EMAIL + " yourself.";
        }
    }

    TextView text(LinearLayout parent, String s, float sp, int color, boolean bold, int topDp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        parent.addView(t, lp);
        return t;
    }

    void label(LinearLayout parent, String s) { text(parent, s, 14, TEXT, true, 18); }

    EditText field(LinearLayout parent, String hint, int inputType, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF6B7782);
        e.setTextColor(TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setInputType(inputType);
        e.setMinLines(lines);
        e.setGravity(lines > 1 ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL | Gravity.START);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF1A2129);
        g.setCornerRadius(dp(8));
        g.setStroke(dp(1), 0xFF3A4650);
        e.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        parent.addView(e, lp);
        return e;
    }
}

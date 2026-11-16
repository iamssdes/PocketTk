package apple.rt;

/**
 * Where the "About TkDroid" page sends people. Set REQUEST_EMAIL, then build (flash) the module: every app
 * built afterwards uses it. (There is no demo link any more: the full TkDroid bundles the demo module inside every
 * app it builds, and "Download the demo" copies it out of the app.)
 */
final class Contact {
    private Contact() {}

    /** The email address that receives requests for the consumer version. The Request page sends them there from inside the app. */
    static final String REQUEST_EMAIL = "";

    /**
     * The form-to-email service the Request page posts to: the request goes to FORM_ENDPOINT + REQUEST_EMAIL.
     * The default is FormSubmit, which needs no account: it sends a one-time confirmation email to REQUEST_EMAIL
     * after the first request, and delivers requests once that is confirmed. If the post fails, the person's
     * email app opens with the request filled in instead.
     */
    static final String FORM_ENDPOINT = "https://formsubmit.co/ajax/";
}

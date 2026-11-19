package android.provider;
public class MediaStore { public static class MediaColumns { public static final String DISPLAY_NAME = "_display_name", MIME_TYPE = "mime_type", IS_PENDING = "is_pending"; }
    public static class Downloads extends MediaColumns { public static final android.net.Uri EXTERNAL_CONTENT_URI = android.net.Uri.parse("content://media/external/downloads"); } }

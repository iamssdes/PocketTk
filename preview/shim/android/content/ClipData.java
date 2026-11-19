package android.content;
public class ClipData { public String text; public java.util.List<android.net.Uri> uris = new java.util.ArrayList<>();
    public static ClipData newPlainText(CharSequence l, CharSequence t) { ClipData c = new ClipData(); c.text = t.toString(); return c; }
    public static class Item { android.net.Uri u; public android.net.Uri getUri() { return u; } public CharSequence getText() { return null; } }
    public int getItemCount() { return uris.size(); } public Item getItemAt(int i) { Item x = new Item(); x.u = uris.get(i); return x; } }

package android.content;
public class ClipboardManager { public void setPrimaryClip(ClipData c) { try { java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(c.text == null ? "" : c.text), null); } catch (Throwable t) { } } }

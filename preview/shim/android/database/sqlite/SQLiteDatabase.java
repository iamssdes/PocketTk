package android.database.sqlite;
public class SQLiteDatabase {
    public interface CursorFactory {}
    static RuntimeException no() { return new UnsupportedOperationException("sqlite3 is not available in the PC preview (it works in the real app)"); }
    public static SQLiteDatabase create(CursorFactory f) { throw no(); }
    public static SQLiteDatabase openOrCreateDatabase(java.io.File f, CursorFactory c) { throw no(); }
    public void execSQL(String s) { throw no(); } public void execSQL(String s, Object[] a) { throw no(); }
    public android.database.Cursor rawQuery(String s, String[] a) { throw no(); } public void close() {}
}

package android.database;
public interface Cursor { boolean moveToNext(); boolean moveToFirst(); int getColumnCount(); String getColumnName(int i); int getType(int i); long getLong(int i); double getDouble(int i); String getString(int i); byte[] getBlob(int i); int getColumnIndex(String n); int getColumnIndexOrThrow(String n); void close(); }

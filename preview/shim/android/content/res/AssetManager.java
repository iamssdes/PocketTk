package android.content.res;
public class AssetManager { public java.io.InputStream open(String n) throws java.io.IOException { java.io.File f = new java.io.File(System.getProperty("pockettk.assets", "."), n); if (f.isFile()) return new java.io.FileInputStream(f); throw new java.io.IOException("no asset " + n); } }

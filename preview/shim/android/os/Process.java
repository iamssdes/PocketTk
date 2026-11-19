package android.os;
public class Process { public static void killProcess(int p) { System.out.println("[process ended]"); Runtime.getRuntime().halt(0); } public static int myPid() { return 1; } }

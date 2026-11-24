package xfcestatus;

import android.app.Application;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;
import com.termux.mod.Mod;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Map;

/**
 * Keeps xfce-status (panel data collector for the xfce-wl session) alive.
 * Every 20 s: if something LISTENS on $PREFIX/tmp/wayland-1 and the process in $PREFIX/tmp/god-status/pid is not xfce-status, start it.
 * A socket file alone is not enough: wlcomp leaves it behind when it dies, and a stale one made this watcher respawn xfce-status forever
 * (two JVM launches via termux-api every 20 s -> periodic system_server stalls).
 * Runs in a daemon thread, every step is wrapped in try/catch: a failure here must never take Termux down.
 */
public class XfceStatus implements Mod {
    static final String TAG = "XfceStatus";

    public void onLoad(final Application app, File modDir, ClassLoader loader) {
        final File usr = new File(app.getFilesDir(), "usr");
        final File home = new File(app.getFilesDir(), "home");
        Thread t = new Thread(new Runnable() { public void run() { loop(usr, home); } }, "xfcestatus");
        t.setDaemon(true); t.start();
        Log.i(TAG, "watcher started");
    }

    static void loop(File usr, File home) {
        File sock = new File(usr, "tmp/wayland-1"), pidf = new File(usr, "tmp/god-status/pid"), log = new File(usr, "tmp/xfcestatus-mod.log");
        for (;;) {
            try {
                if (sock.exists() && !running(pidf) && alive(sock)) {
                    ProcessBuilder pb = new ProcessBuilder(new File(usr, "bin/bash").getPath(), new File(usr, "bin/xfce-status").getPath());
                    Map<String, String> e = pb.environment();
                    e.put("PREFIX", usr.getPath()); e.put("HOME", home.getPath()); e.put("TMPDIR", new File(usr, "tmp").getPath());
                    e.put("PATH", new File(usr, "bin").getPath() + ":/system/bin"); e.put("LANG", "en_US.UTF-8");
                    e.remove("LD_PRELOAD");
                    pb.directory(home).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log));
                    pb.start();
                    note(log, "started xfce-status");
                    Log.i(TAG, "started xfce-status");
                }
            } catch (Throwable t) { Log.e(TAG, "loop", t); try { note(log, "ERROR " + t); } catch (Throwable ignored) {} }
            try { Thread.sleep(20000); } catch (InterruptedException ie) { return; }
        }
    }

    /** True if a compositor accepts connections on the socket (a stale file gives ECONNREFUSED). */
    static boolean alive(File sock) {
        LocalSocket s = new LocalSocket();
        try { s.connect(new LocalSocketAddress(sock.getPath(), LocalSocketAddress.Namespace.FILESYSTEM)); return true; }
        catch (Throwable t) { return false; }
        finally { try { s.close(); } catch (Throwable ignored) {} }
    }

    static boolean running(File pidf) {
        try {
            String pid; try (FileInputStream in = new FileInputStream(pidf)) { pid = new String(readAll(in)).trim(); }
            if (pid.isEmpty()) return false;
            try (FileInputStream in = new FileInputStream(new File("/proc/" + pid + "/cmdline"))) { return new String(readAll(in)).contains("xfce-status"); }
        } catch (Throwable t) { return false; }
    }

    static byte[] readAll(FileInputStream in) throws Exception {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(); byte[] b = new byte[4096]; int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    static void note(File f, String s) throws Exception {
        try (FileWriter w = new FileWriter(f, true)) { w.write(new java.util.Date() + " " + s + "\n"); }
    }
}

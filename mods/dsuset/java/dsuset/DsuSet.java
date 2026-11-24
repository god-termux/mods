package dsuset;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Log;
import com.termux.mod.Mod;
import java.io.File;
import java.io.FileWriter;

/**
 * dsu experiment 4a: lets Termux switch Wireless debugging on/off without touching the Settings UI.
 * Broadcast: am broadcast -a dsu.ADB_WIFI --ei value 1   (0 = off, 1 = on, default 1; "get" via --es op get)
 * Only senders holding android.permission.WRITE_SECURE_SETTINGS are accepted (Termux itself; whoever has it can change
 * Settings.Global anyway). com.termux.permission.RUN_COMMAND is only declared by the app, not requested, so it can't be used.
 * Needs android.permission.WRITE_SECURE_SETTINGS (already granted to com.termux on this device).
 * Result is written to $PREFIX/tmp/dsu-adbwifi.result and returned as the broadcast result data.
 */
public class DsuSet implements Mod {
    static final String TAG = "DsuSet", ACTION = "dsu.ADB_WIFI", PERM = "android.permission.WRITE_SECURE_SETTINGS", KEY = "adb_wifi_enabled";

    public void onLoad(final Application app, File modDir, ClassLoader loader) {
        final File res = new File(new File(app.getFilesDir(), "usr/tmp"), "dsu-adbwifi.result");
        HandlerThread ht = new HandlerThread("dsuset"); ht.start();
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String out;
                try {
                    if ("get".equals(i.getStringExtra("op"))) {
                        out = "adb_wifi_enabled=" + Settings.Global.getInt(c.getContentResolver(), KEY, -1);
                    } else {
                        int v = i.getIntExtra("value", 1) != 0 ? 1 : 0;
                        boolean ok = Settings.Global.putInt(c.getContentResolver(), KEY, v);
                        out = "put " + KEY + "=" + v + " -> " + ok + " (now " + Settings.Global.getInt(c.getContentResolver(), KEY, -1) + ")";
                    }
                } catch (Throwable t) { out = "ERROR " + t; Log.e(TAG, "ADB_WIFI", t); }
                Log.i(TAG, out);
                try (FileWriter w = new FileWriter(res)) { w.write(out + "\n"); } catch (Throwable ignored) {}
                if (isOrderedBroadcast()) setResultData(out);
            }
        };
        app.registerReceiver(r, new IntentFilter(ACTION), PERM, new Handler(ht.getLooper()));
        Log.i(TAG, "receiver registered for " + ACTION);
    }
}

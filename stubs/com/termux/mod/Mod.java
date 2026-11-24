package com.termux.mod;
import android.app.Application;
import java.io.File;
/** Compile-time copy of the interface from the god-termux APK (not packaged into mods). */
public interface Mod { void onLoad(Application app, File modDir, ClassLoader loader); }

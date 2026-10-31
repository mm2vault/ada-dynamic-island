package com.ada.island;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/** Restarts the island after a reboot or an app update, if the user had it enabled. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Prefs prefs = new Prefs(context);
        if (!prefs.islandOn()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return;
        try {
            Intent i = new Intent(context, IslandService.class);
            i.setAction(IslandService.ACTION_SHOW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i);
            } else {
                context.startService(i);
            }
        } catch (Exception ignored) {
        }
    }
}

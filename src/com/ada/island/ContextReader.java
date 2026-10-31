package com.ada.island;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Process;

import java.util.Calendar;

/** Reads "what the user is doing right now" from UsageStats + battery. */
public class ContextReader {

    public static class Ctx {
        public String appLabel = "Ana ekran";
        public String pkg = "";
        public long minutes = 0;
        public int battery = -1;
        public boolean charging = false;
        public int hour = 12;

        public String block() {
            StringBuilder sb = new StringBuilder();
            sb.append("Uygulama: ").append(appLabel);
            if (minutes > 0) sb.append(" (").append(minutes).append(" dakikadır açık)");
            sb.append("\nSaat: ").append(hour).append(":00 civarı");
            if (battery >= 0) {
                sb.append("\nBatarya: %").append(battery).append(charging ? " (şarjda)" : " (şarjda değil)");
            }
            return sb.toString();
        }
    }

    public static boolean hasUsageAccess(Context c) {
        try {
            AppOpsManager appOps = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public static Ctx read(Context c) {
        Ctx out = new Ctx();
        Calendar cal = Calendar.getInstance();
        out.hour = cal.get(Calendar.HOUR_OF_DAY);

        Intent battery = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) out.battery = Math.round(level * 100f / scale);
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            out.charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }

        if (!hasUsageAccess(c)) return out;

        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 1000L * 60 * 90, now);
            if (events == null) return out;
            UsageEvents.Event e = new UsageEvents.Event();
            String lastPkg = null;
            long lastTs = 0;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                int type = e.getEventType();
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    String pk = e.getPackageName();
                    if (pk == null) continue;
                    if (pk.equals(c.getPackageName())) continue;
                    if (pk.equals("com.android.systemui")) continue;
                    lastPkg = pk;
                    lastTs = e.getTimeStamp();
                }
            }
            if (lastPkg != null && lastTs > 0) {
                out.pkg = lastPkg;
                out.appLabel = labelOf(c, lastPkg);
                out.minutes = Math.max(0, (now - lastTs) / 60000L);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String labelOf(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            if (label != null && label.length() > 0) return label.toString();
        } catch (Exception ignored) {
        }
        String[] parts = pkg.split("\\.");
        String last = parts[parts.length - 1];
        if (last.length() == 0) return pkg;
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }
}

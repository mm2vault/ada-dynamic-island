package com.ada.island;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Receives Android PackageInstaller results; user confirmation remains controlled by Android. */
public class UpdateInstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirmation);
                } catch (Exception e) {
                    Toast.makeText(context, "Android kurulum ekranı açılamadı. Güncellemeyi uygulamadan başlat.", Toast.LENGTH_LONG).show();
                }
            }
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(context, "Ada güncellendi.", Toast.LENGTH_LONG).show();
        } else {
            String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            String message = detail == null || detail.isEmpty() ? "Android güncellemeyi yükleyemedi." : "Android yüklemeyi reddetti: " + detail;
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }
}

package com.ada.island;

import android.service.notification.NotificationListenerService;

/**
 * The app never reads notifications. This listener only exists so Android grants us
 * MediaSessionManager.getActiveSessions(), which is how Ada sees and controls music.
 */
public class MusicNotificationListener extends NotificationListenerService {

    public static volatile MusicNotificationListener instance;

    @Override
    public void onListenerConnected() {
        instance = this;
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}

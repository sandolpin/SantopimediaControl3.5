package com.sandolpin.santopimedia35

import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * NotificationListenerService
 *
 * このサービスが「接続」されて初めて getActiveSessions() が動作する。
 * onListenerConnected() で MediaSessionRepository に通知し、
 * ViewModel 側でセッション取得を再試行させる。
 */
class MediaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("SantopiMedia", "MediaNotificationListener: onListenerConnected")
        MediaSessionRepository.onListenerConnected()

        // 接続直後にセッション一覧をリポジトリに渡す
        try {
            val sessionManager =
                getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, MediaNotificationListener::class.java)
            val controllers = sessionManager.getActiveSessions(componentName)
            Log.d("SantopiMedia", "onListenerConnected: found ${controllers.size} sessions")
            controllers.forEach { Log.d("SantopiMedia", "  session: ${it.packageName}") }
            MediaSessionRepository.onControllersUpdated(controllers)
        } catch (e: Exception) {
            Log.e("SantopiMedia", "onListenerConnected getActiveSessions failed", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("SantopiMedia", "MediaNotificationListener: onListenerDisconnected")
        MediaSessionRepository.onListenerDisconnected()
    }
}
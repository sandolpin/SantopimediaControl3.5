package com.sandolpin.santopimedia35

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.sandolpin.santopimedia35.database.SettingsKeys
import com.sandolpin.santopimedia35.database.dataStore
import com.sandolpin.santopimedia35.mainactivity.SantopimediaThemeWrapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var navigatedToPermissionSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 歌詞キャッシュDB初期化
        LyricsCache.init(this)
        setContent {
            SantopimediaThemeWrapper()
        }
    }

    override fun onResume() {
        super.onResume()
        if (navigatedToPermissionSettings) {
            navigatedToPermissionSettings = false
            return
        }
        if (!isNotificationListenerEnabled()) {
            navigatedToPermissionSettings = true
            android.widget.Toast.makeText(
                this,
                "「通知へのアクセス」でこのアプリをONにしてください",
                android.widget.Toast.LENGTH_LONG
            ).show()
            startActivity(
                android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            )
        }
        // バックグラウンド設定がONならService起動
        lifecycleScope.launch {
            val prefs = dataStore.data.first()
            val enabled = prefs[com.sandolpin.santopimedia35.database.SettingsKeys.BACKGROUND_HISTORY_ENABLED] ?: false
            if (enabled) HistoryService.start(this@MainActivity)
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.split(":").any { it.startsWith(packageName) }
    }
}
package com.sandolpin.santopimedia35

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sandolpin.santopimedia35.database.MediaState
import com.sandolpin.santopimedia35.remember.HistoryRepository
import com.sandolpin.santopimedia35.remember.HistoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * HistoryService
 *
 * ===== これが必要な理由 =====
 * 再生履歴の記録は今まで MainActivity（Compose画面）が生きている間だけ、
 * HistoryViewModel が MediaPlayerViewModel の状態変化を受け取ることで行われていた。
 * そのため、アプリを閉じたりOSにバックグラウンドでプロセスごと終了させられると、
 * 記録処理も一緒に止まってしまっていた。
 *
 * NotificationListenerService（MediaNotificationListener）自体はシステムからバインド
 * され続けるが、それだけではアプリの「プロセス」自体がOS標準のメモリ回収や、
 * 端末メーカー製のバッテリー最適化（Xiaomi/Samsung等でよくある独自のタスクキル）に
 * よって終了させられるのを防げない。
 *
 * このサービスは startForeground() で「常駐通知」を出すことで、
 * OSに対して「ユーザーが明示的に許可した継続動作である」ことを示し、
 * バックグラウンドでもプロセスが生かされやすくする。
 *
 * ===== 何をするか =====
 * MediaPlayerViewModel の startListening()/updateSessions() とほぼ同じ手順で、
 * このサービス自身が MediaSessionManager から MediaController 一覧を取得・監視し、
 * 再生状態が変化するたびに HistoryViewModel.onMediaStateChanged() を呼び出す。
 * 記録ロジック自体（曲変更判定・再生時間積算・DB保存）は HistoryViewModel に
 * 既にあるものをそのまま利用し、重複実装しない。
 *
 * ===== 使い方 =====
 * SettingScreen.kt の「バックグラウンドで履歴記録を許可」トグルから
 * HistoryService.start(context) / HistoryService.stop(context) で起動・停止する。
 * 停止は必ずこの設定トグル経由で行うこと（通知からの直接停止は用意していない。
 * 設定値と実際の起動状態がズレるのを防ぐため）。
 */
class HistoryService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var historyViewModel: HistoryViewModel

    // ===== セッション監視（MediaPlayerViewModelと同様の仕組みをサービス内に独立して持つ）=====
    private var sessionManager: MediaSessionManager? = null
    private var isSessionListenerRegistered = false
    private val activeControllers = mutableMapOf<String, MediaController>()
    // 現在記録対象として追跡しているコントローラー
    private var trackedController: MediaController? = null
    private var positionJob: Job? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refreshHistoryState()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshHistoryState()
    }

    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            controllers?.let { updateSessions(it) }
        }

    override fun onCreate() {
        super.onCreate()
        Log.d("SantopiMedia", "HistoryService: onCreate")

        val repo = HistoryRepository(applicationContext)
        // ViewModelProviderを介さず直接生成するが、viewModelScopeはViewModelインスタンス自身が
        // 内部で保持するCoroutineScopeのため、Activity/ViewModelStoreの有無に関わらず問題なく動作する。
        historyViewModel = HistoryViewModel(repo, applicationContext)

        createNotificationChannel()
        // startForegroundServiceで起動された場合、数秒以内にstartForeground()を呼ぶ必要がある
        // specialUseタイプを明示してフォアグラウンド化する。
        // ★ ServiceCompat.startForeground()はAndroidX Core 1.12以降でしか使えないため、
        //   依存バージョンを上げずに済むよう、標準のService.startForeground()を
        //   自前のAPIレベル分岐で呼ぶ。3引数版(id, notification, type)はAPI 29以降にしか
        //   存在せず、specialUseタイプ自体もAndroid 14(API 34)で新設されたものなので、
        //   34未満では従来通り2引数版で十分（古いOSはforegroundServiceTypeの強制チェック自体を
        //   行わないため実害はない）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(null),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(null))
        }

        // ①すでに通知アクセス権限があり、リスナーが接続済みならすぐ取得を試みる
        startListening()

        // ②MediaNotificationListenerの接続状態が変化した（後から権限が付与された等）ときに再試行
        MediaSessionRepository.isListenerConnected
            .onEach { connected -> if (connected) startListening() }
            .launchIn(serviceScope)

        // ===== 履歴リセットへの追従 =====
        // 「履歴をリセット」はDBの行を消すだけで、このサービスが内部で保持している
        // 追跡セッション（trackStartMs・accumulatedPlayMs等）には影響しない。
        // そのままだと、同じ曲がリセットをまたいで流れ続けた場合、リセット前から
        // 積み上がっていた時間が次の自動保存でそのまま復活してしまう。
        // リセット通知を受けたら、保存せずに追跡状態だけをリセットする。
        HistoryResetSignal.resetEvents
            .onEach {
                Log.d("SantopiMedia", "HistoryService: 履歴リセットを検知→追跡状態をリセット")
                historyViewModel.resetCurrentTrackingWithoutSaving()
            }
            .launchIn(serviceScope)

        // ===== 記録の役割分担について =====
        // このサービスは「バックグラウンドで履歴記録を許可」がONのときのみ起動される
        // （MainActivity.onResume() / SettingScreenのトグルを参照）。
        // ONのときはフォアグラウンド・バックグラウンドを問わず常にこのサービスだけが
        // 記録を担当する。MainActivity.kt側は同じ設定がONの間は自身の
        // HistoryViewModel.onMediaStateChanged()を一切呼ばないため、
        // 「フォアグラウンド中は一時停止する」といった調整はここでは不要になった
        // （以前はAppForegroundStateというフラグで両者を都度切り替えていたが、
        //   切り替えタイミングの同期が難しく二重記録の原因になっていたため撤廃した）。

        // 5秒ごとに再生位置を反映（HistoryViewModel.updatePosition）
        positionJob = serviceScope.launch {
            while (true) {
                delay(5_000)
                val ctrl = trackedController ?: continue
                if (ctrl.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    historyViewModel.updatePosition(ctrl.playbackState?.position ?: 0L)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ===== 通知スワイプ削除への対抗策 =====
        // Android 14以降、setOngoing(true)を使っていても、OSの仕様変更により
        // ユーザーが通知をスワイプで消せるようになってしまった（意図的な仕様変更）。
        // これを完全に禁止するAPIは無いため、「消された瞬間に自分で即座に再表示する」
        // という広く使われている回避策を使う。
        // setDeleteIntent()でこのIntent（ACTION_NOTIFICATION_DISMISSED）を通知に仕込んでおき、
        // ユーザーが通知をスワイプすると、このonStartCommandがこのactionで呼ばれる。
        if (intent?.action == ACTION_NOTIFICATION_DISMISSED) {
            Log.d("SantopiMedia", "HistoryService: 通知がスワイプで消された→即座に再表示")
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, buildNotification(trackedController?.let { buildMediaStateForHistory(it) }))
            return START_STICKY
        }
        // プロセスがOSに再起動された場合も自動的にサービスを再開させる
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ===== アプリがタスク一覧（最近使ったアプリ）から消された場合 =====
    // 標準的なAndroidの挙動では、フォアグラウンドサービスはタスクが消されても
    // 自動では止まらない（何もしなければ動作し続ける）。
    // ただし、これが効くのは「OSの正規の仕組みで動いている場合」のみ。
    // 端末メーカー独自のタスクキラーによって、この直後にプロセスごと強制終了
    // （force-stop相当）された場合は、Android自体が「強制終了されたアプリの
    // 自動復帰」を意図的に禁止しているため、ここで何を書いても復活できない。
    // その対策は端末側の「自動起動」「保護されたアプリ」等の設定をユーザーに
    // 許可してもらう以外に方法がない。
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("SantopiMedia", "HistoryService: onTaskRemoved（タスク一覧から削除された）")
        // 念のため自分自身の再起動を試みておく（プロセス自体は生きているが
        // Serviceだけ何らかの理由で終了しかけているケースへの保険）
        val restartIntent = Intent(applicationContext, HistoryService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restartIntent)
            } else {
                applicationContext.startService(restartIntent)
            }
        } catch (e: Exception) {
            Log.e("SantopiMedia", "HistoryService: onTaskRemoved再起動試行に失敗", e)
        }
    }

    override fun onDestroy() {
        Log.d("SantopiMedia", "HistoryService: onDestroy")
        positionJob?.cancel()
        activeControllers.values.forEach { it.unregisterCallback(controllerCallback) }
        activeControllers.clear()
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {
            // 権限なし等で例外になる場合がある
        }
        // 終了時点で追跡中の曲があれば保存してからViewModelを解放する
        historyViewModel.onCleared()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ===== セッション監視 =====
    private fun startListening() {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            sessionManager = manager
            val componentName = ComponentName(this, MediaNotificationListener::class.java)
            val controllers = manager.getActiveSessions(componentName)
            Log.d("SantopiMedia", "HistoryService: startListening found ${controllers.size} controllers")
            updateSessions(controllers)

            if (!isSessionListenerRegistered) {
                manager.addOnActiveSessionsChangedListener(sessionListener, componentName)
                isSessionListenerRegistered = true
            }
        } catch (e: SecurityException) {
            Log.e("SantopiMedia", "HistoryService: 通知アクセス権限なし", e)
        } catch (e: Exception) {
            Log.e("SantopiMedia", "HistoryService: startListening 例外", e)
        }
    }

    private fun updateSessions(controllers: List<MediaController>) {
        activeControllers.values.forEach { it.unregisterCallback(controllerCallback) }
        activeControllers.clear()
        for (controller in controllers) {
            activeControllers[controller.packageName] = controller
            controller.registerCallback(controllerCallback, mainHandler)
        }
        refreshHistoryState()
    }

    // 再生中のコントローラーを最優先で記録対象にする。
    // 無ければ直前まで追跡していたコントローラーを維持し、それも無くなっていれば先頭を使う。
    // （MediaPlayerViewModelのアプリ自動切り替えロジックと同じ考え方）
    private fun refreshHistoryState() {
        val playing = activeControllers.values.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        val target = playing
            ?: trackedController?.takeIf { activeControllers.containsValue(it) }
            ?: activeControllers.values.firstOrNull()

        trackedController = target
        val ctrl = target ?: return

        val state = buildMediaStateForHistory(ctrl)
        // このサービスが起動している間（＝設定ONの間）は常に記録の担当者である。
        // MainActivity.kt側は同じ設定がONの間は自身のonMediaStateChanged()を
        // 呼ばないため、ここで無条件に呼んでも二重記録にはならない。
        historyViewModel.onMediaStateChanged(state)
        updateNotificationContent(state)
    }

    // MediaController から HistoryViewModel の記録に必要な最小限の MediaState を組み立てる。
    // MediaPlayerViewModel.buildMediaState() と同じ考え方だが、履歴記録に不要な
    // UI専用フィールド（progress/volume/dominantColor等）は省いている。
    private fun buildMediaStateForHistory(ctrl: MediaController): MediaState {
        val meta = ctrl.metadata
        val pb = ctrl.playbackState

        val appLabel: String = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(ctrl.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            ctrl.packageName
        }

        val artUri = meta?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
        val artBitmap = if (artUri == null) {
            meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        } else null

        return MediaState(
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            albumArtUri = artUri,
            albumArtBitmap = artBitmap,
            isPlaying = pb?.state == PlaybackState.STATE_PLAYING,
            currentPositionMs = pb?.position ?: 0L,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            packageName = ctrl.packageName,
            appLabel = appLabel,
            mediaId = meta?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: ""
        )
    }

    // ===== 常駐通知 =====
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "再生履歴の記録",
                NotificationManager.IMPORTANCE_LOW // 音・バイブなしの控えめな通知
            ).apply {
                description = "バックグラウンドで再生履歴を記録していることを示す常駐通知です"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: MediaState?): Notification {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        val contentIntent = openIntent?.let {
            PendingIntent.getActivity(this, 0, it, pendingIntentFlags)
        }

        val contentText = if (state != null && state.title.isNotEmpty()) {
            "${state.title} / ${state.artist}"
        } else {
            "再生を待機中..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("再生履歴を記録中")
            .setContentText(contentText)
            .setSmallIcon(applicationInfo.icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply { if (contentIntent != null) setContentIntent(contentIntent) }
            // Android 14+ではsetOngoing(true)だけではスワイプ削除を防げなくなったため、
            // 削除された瞬間に自分自身へ通知を送り、即座に再表示させる（buildDeleteIntent参照）
            .setDeleteIntent(buildDeleteIntent())
            .build()
    }

    // 通知がスワイプ削除された際にonStartCommand(ACTION_NOTIFICATION_DISMISSED)を
    // 呼び出させるためのPendingIntent。対象はこのService自身。
    private fun buildDeleteIntent(): PendingIntent {
        val intent = Intent(this, HistoryService::class.java).apply {
            action = ACTION_NOTIFICATION_DISMISSED
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getService(this, 1, intent, flags)
    }

    private fun updateNotificationContent(state: MediaState) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    companion object {
        private const val CHANNEL_ID = "santopi_history_recording"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_NOTIFICATION_DISMISSED =
            "com.sandolpin.santopimedia35.action.HISTORY_NOTIFICATION_DISMISSED"

        fun start(context: android.content.Context) {
            val intent = Intent(context, HistoryService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, HistoryService::class.java))
        }
    }
}
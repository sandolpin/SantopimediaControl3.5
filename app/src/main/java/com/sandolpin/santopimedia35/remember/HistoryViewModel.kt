package com.sandolpin.santopimedia35.remember

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sandolpin.santopimedia35.HistoryResetSignal
import com.sandolpin.santopimedia35.database.MediaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class HistoryViewModel(
    private val repo: HistoryRepository,
    private val context: Context
) : ViewModel() {

    enum class Filter { TODAY, WEEK, ALL }

    private val _filter = MutableStateFlow(Filter.TODAY)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    private val _historyList = MutableStateFlow<List<PlayHistoryEntity>>(emptyList())
    val historyList: StateFlow<List<PlayHistoryEntity>> = _historyList.asStateFlow()

    private val _todayTotalMs = MutableStateFlow(0L)
    val todayTotalMs: StateFlow<Long> = _todayTotalMs.asStateFlow()

    private val _todayTrackCount = MutableStateFlow(0)
    val todayTrackCount: StateFlow<Int> = _todayTrackCount.asStateFlow()

    private val _excludedApps = MutableStateFlow<List<ExcludedApp>>(emptyList())
    val excludedApps: StateFlow<List<ExcludedApp>> = _excludedApps.asStateFlow()

    // ===== デバッグ用診断情報 =====
    data class RecordingStatus(
        val isTracking: Boolean,       // 曲を追跡中か
        val isPlaying: Boolean,        // 再生中か
        val currentTitle: String,      // 現在の曲名
        val currentApp: String,        // 現在のアプリ
        val accumulatedSec: Long,      // 現在の積算再生秒数
        val timerRunning: Boolean,     // タイマーが動いているか
        val lastUpdatedMs: Long,       // 最後に onMediaStateChanged が呼ばれた時刻
    )
    private val _recordingStatus = MutableStateFlow(
        RecordingStatus(false, false, "", "", 0L, false, 0L)
    )
    val recordingStatus: StateFlow<RecordingStatus> = _recordingStatus.asStateFlow()

    private fun updateRecordingStatus() {
        val currentPlayMs = if (wasPlaying && playStartTimeMs > 0L)
            accumulatedPlayMs + (System.currentTimeMillis() - playStartTimeMs)
        else accumulatedPlayMs
        _recordingStatus.value = RecordingStatus(
            isTracking    = currentTrackKey.isNotEmpty(),
            isPlaying     = wasPlaying,
            currentTitle  = currentState?.title ?: "",
            currentApp    = currentState?.appLabel ?: "",
            accumulatedSec = currentPlayMs / 1000L,
            timerRunning  = statsRefreshJob?.isActive == true,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    // ===== 再生時間カウント =====
    // 【計測方式】システム時刻ベース
    // 再生開始時刻を記録し、一時停止・曲変更時に (現在時刻 - 再生開始時刻) を加算する。
    private var currentState: MediaState? = null
    private var trackStartMs: Long = 0L
    private var playStartTimeMs: Long = 0L
    private var accumulatedPlayMs: Long = 0L
    private var wasPlaying: Boolean = false
    private var statsRefreshJob: Job? = null
    // 除外パッケージをメモリキャッシュ（DBアクセスをメインスレッドで行わないため）
    private val excludedPackageCache = mutableSetOf<String>()
    // 現在追跡中の曲キー（title|artist|packageName）。重複呼び出しを防ぐ
    private var currentTrackKey: String = ""
    // 再生中フラグの最後の確認値（isPlaying の重複通知を防ぐ）
    private var lastIsPlaying: Boolean = false

    // ===== アートワークURI解決 =====
    // albumArtUri が無く albumArtBitmap のみ提供するアプリ（Bitmapのみのアプリ）対応。
    // Bitmap をキャッシュファイルとして保存し、その file:// URI を履歴に保存する。
    // これにより HistoryScreen / HistoryLongTapDialog 等の URI 前提の表示でもアートが出る。
    private fun resolveAlbumArtUri(state: MediaState): String? {
        if (state.albumArtUri != null) return state.albumArtUri
        val bitmap = state.albumArtBitmap ?: return null
        val trackKey = "${state.title}|${state.artist}|${state.packageName}"
        return com.sandolpin.santopimedia35.AlbumArtCache.saveAndGetUri(context, bitmap, trackKey)
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val excluded = repo.getExcludedApps()
            _excludedApps.value = excluded
            // 除外リストをメモリキャッシュに読み込む
            excludedPackageCache.addAll(excluded.map { it.packageName })
            refreshStats()
            refreshList()
        }
        viewModelScope.launch {
            _filter.collect { refreshList() }
        }
    }

    fun setFilter(f: Filter) { _filter.value = f }

    // ===== MediaStateの変化を受け取る =====
    fun onMediaStateChanged(newState: MediaState) {
        // 除外アプリはメモリキャッシュで判定（DBアクセスなし）
        if (excludedPackageCache.contains(newState.packageName)) return

        // ★ タイトルが空の更新は「一時的に不完全なメタデータ」とみなして無視する。
        //   一部のアプリはシーク中・一時停止から再生への切り替え時などに、
        //   一瞬だけメタデータが空（タイトルなし）の状態を経由することがある。
        //   これをそのまま処理すると、
        //     ① 空タイトルへの変化を「曲が変わった」と誤検知 → 現在の曲を確定保存
        //     ② 直後に本来のタイトルへ戻る変化も「曲が変わった」と誤検知 → 新規追跡開始
        //   という2段階の誤検知が起き、同じ曲なのに履歴が2曲に分裂してしまう
        //   （実際に発生した不具合）。空タイトルの間は何もせず、直前の追跡状態を
        //   そのまま維持することでこれを防ぐ。
        if (newState.title.isEmpty()) return

        val newTrackKey = "${newState.title}|${newState.artist}|${newState.packageName}"

        // ===== 曲変更の判定 =====
        val prev = currentState
        val trackChanged = prev != null &&
                currentTrackKey != newTrackKey &&
                prev.title.isNotEmpty()

        if (trackChanged && prev != null) {
            if (wasPlaying && playStartTimeMs > 0L) {
                val elapsed = System.currentTimeMillis() - playStartTimeMs
                if (elapsed > 0L) accumulatedPlayMs += elapsed
            }
            val savedTrackStartMs = trackStartMs
            saveRecord(prev, accumulatedPlayMs, savedTrackStartMs)

            accumulatedPlayMs = 0L
            trackStartMs = System.currentTimeMillis()
            currentTrackKey = newTrackKey
            playStartTimeMs = if (newState.isPlaying) System.currentTimeMillis() else 0L
            wasPlaying = newState.isPlaying
            lastIsPlaying = newState.isPlaying
            currentState = newState
            startStatsRefreshTimer()
            updateRecordingStatus()
            return
        }

        if (prev == null || currentTrackKey.isEmpty()) {
            trackStartMs = System.currentTimeMillis()
            accumulatedPlayMs = 0L
            currentTrackKey = newTrackKey
            playStartTimeMs = if (newState.isPlaying) System.currentTimeMillis() else 0L
            wasPlaying = newState.isPlaying
            lastIsPlaying = newState.isPlaying
            currentState = newState
            startStatsRefreshTimer()
            updateRecordingStatus()
            return
        }

        val playingChanged = lastIsPlaying != newState.isPlaying
        if (playingChanged) {
            lastIsPlaying = newState.isPlaying
            if (newState.isPlaying) {
                playStartTimeMs = System.currentTimeMillis()
                wasPlaying = true
                startStatsRefreshTimer()
            } else {
                if (wasPlaying && playStartTimeMs > 0L) {
                    val elapsed = System.currentTimeMillis() - playStartTimeMs
                    if (elapsed > 0L) accumulatedPlayMs += elapsed
                    playStartTimeMs = 0L
                }
                wasPlaying = false
                stopStatsRefreshTimer()
            }
        } else if (newState.isPlaying && playStartTimeMs == 0L) {
            playStartTimeMs = System.currentTimeMillis()
            wasPlaying = true
            startStatsRefreshTimer()
        }

        currentState = newState
        updateRecordingStatus()
    }

    // 再生中に統計を定期更新するタイマー（UIに今日の累積時間を反映するため）
    // ・例外で止まっても自動再起動する
    // ・再生中の accumulatedPlayMs を定期的に仮保存（長時間同一曲再生でも記録が残る）
    private fun startStatsRefreshTimer() {
        statsRefreshJob?.cancel()
        statsRefreshJob = viewModelScope.launch {
            while (true) {
                try {
                    delay(5_000)

                    // 再生中なら現在までの elapsed を加算した仮の playTimeMs を計算
                    // （曲変更・停止がなくても定期的に DB に書き込む）
                    val state = currentState
                    if (state != null && wasPlaying && playStartTimeMs > 0L) {
                        val now = System.currentTimeMillis()
                        val elapsed = now - playStartTimeMs
                        if (elapsed > 0L) {
                            val currentPlayMs = accumulatedPlayMs + elapsed
                            val savedStart = trackStartMs
                            if (state.title.isNotEmpty() && currentPlayMs >= 10_000L) {
                                withContext(Dispatchers.IO) {
                                    val existing = repo.getPlayTimeMs(savedStart)
                                    if (existing < currentPlayMs) {
                                        repo.insert(
                                            PlayHistoryEntity(
                                                title       = state.title,
                                                artist      = state.artist,
                                                album       = state.album,
                                                albumArtUri = resolveAlbumArtUri(state),
                                                appLabel    = state.appLabel,
                                                packageName = state.packageName,
                                                playedAtMs  = savedStart,
                                                durationMs  = state.durationMs,
                                                playTimeMs  = currentPlayMs.coerceAtLeast(0L),
                                                listenedMs  = state.currentPositionMs,
                                                listenedAll = state.durationMs > 0 &&
                                                        state.currentPositionMs >= state.durationMs * 0.9,
                                                mediaId     = state.mediaId
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.IO) {
                        refreshStats()
                        refreshList()
                    }
                    updateRecordingStatus()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Job がキャンセルされたら正常終了
                    throw e
                } catch (e: Exception) {
                    // DB例外など予期しないエラー: ログだけ出してループ継続
                    android.util.Log.e("HistoryViewModel", "statsRefreshTimer error", e)
                    delay(5_000) // エラー時も少し待ってから再試行
                }
            }
        }
    }

    private fun stopStatsRefreshTimer() {
        statsRefreshJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            refreshStats()
            refreshList()
        }
    }

    private fun saveRecord(state: MediaState, playTimeMs: Long, savedTrackStartMs: Long = trackStartMs) {
        if (state.title.isEmpty()) return
        // 10秒未満の再生は履歴に残さない
        if (playTimeMs < 10_000L) return

        viewModelScope.launch(Dispatchers.IO) {
            // 既存レコード（同じ playedAtMs）の playTimeMs より小さければ上書きしない
            val existing = repo.getPlayTimeMs(savedTrackStartMs)
            if (existing >= playTimeMs) return@launch

            val entity = PlayHistoryEntity(
                title       = state.title,
                artist      = state.artist,
                album       = state.album,
                albumArtUri = resolveAlbumArtUri(state),
                appLabel    = state.appLabel,
                packageName = state.packageName,
                playedAtMs  = savedTrackStartMs,
                durationMs  = state.durationMs,
                playTimeMs  = playTimeMs.coerceAtLeast(0L),
                listenedMs  = state.currentPositionMs,
                listenedAll = state.durationMs > 0 &&
                        state.currentPositionMs >= state.durationMs * 0.9,
                mediaId     = state.mediaId
            )
            repo.insert(entity)
            refreshStats()
            refreshList()
        }
    }

    private suspend fun refreshStats() = withContext(Dispatchers.IO) {
        val from = todayStartMs()
        _todayTotalMs.value    = repo.getTotalPlayTimeMs(from)
        _todayTrackCount.value = repo.getTrackCount(from)
    }

    private suspend fun refreshList() = withContext(Dispatchers.IO) {
        val from = when (_filter.value) {
            Filter.TODAY -> todayStartMs()
            Filter.WEEK  -> weekStartMs()
            Filter.ALL   -> 0L
        }
        _historyList.value = repo.queryFrom(from)
    }

    // ===== 再生位置の定期更新（互換性のため残す。現在は listenedMs 更新のみ）=====
    fun updatePosition(positionMs: Long) {
        currentState = currentState?.copy(currentPositionMs = positionMs)
    }

    // ===== HistoryScreen表示時に現在再生中の曲を即時反映 =====
    // saveRecord は10秒以上再生済みの場合のみ実際にDBに書き込む
    // すでに保存済みのレコードを一時的に上書きしないよう、
    // 「現在の accumulatedPlayMs」を加味した仮のエントリとして refreshList する
    fun flushCurrentTrack() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = currentState ?: run {
                refreshStats()
                refreshList()
                return@launch
            }

            // 再生中なら現在までの経過時間を加算した仮の playTimeMs を計算
            val currentPlayMs = if (wasPlaying && playStartTimeMs > 0L) {
                val elapsed = System.currentTimeMillis() - playStartTimeMs
                accumulatedPlayMs + if (elapsed > 0L) elapsed else 0L
            } else {
                accumulatedPlayMs
            }

            // 10秒以上再生していれば仮レコードを保存（上書き可能にするため trackStartMs を一致させる）
            if (state.title.isNotEmpty() && currentPlayMs >= 10_000L) {
                val entity = com.sandolpin.santopimedia35.remember.PlayHistoryEntity(
                    title       = state.title,
                    artist      = state.artist,
                    album       = state.album,
                    albumArtUri = resolveAlbumArtUri(state),
                    appLabel    = state.appLabel,
                    packageName = state.packageName,
                    playedAtMs  = trackStartMs,
                    durationMs  = state.durationMs,
                    playTimeMs  = currentPlayMs.coerceAtLeast(0L),
                    listenedMs  = state.currentPositionMs,
                    listenedAll = state.durationMs > 0 &&
                            state.currentPositionMs >= state.durationMs * 0.9,
                    mediaId     = state.mediaId
                )
                repo.insert(entity)
            }

            refreshStats()
            refreshList()
        }
    }

    // ===== RankingScreen用: 全履歴取得 =====
    suspend fun getAllHistory(): List<PlayHistoryEntity> =
        withContext(Dispatchers.IO) { repo.queryAll() }

    // ===== 履歴リセット =====
    fun resetHistory(fromMs: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (fromMs == null) repo.deleteAll()
            else repo.deleteFrom(fromMs)
            refreshStats()
            refreshList()
        }
        // ★ このインスタンス自身が追跡中だった場合に備え、蓄積時間をリセットする
        //   （保存はしない。保存するとリセット直後に同じ時間が復活してしまうため）。
        resetCurrentTrackingWithoutSaving()
        // ★ 別インスタンスで動いているHistoryService側にもリセットを伝える。
        //   これが無いと、同じ曲がリセットをまたいで流れ続けていた場合に、
        //   HistoryService側で積み上がっていた時間がそのまま復活してしまう
        //   （実際に発生した不具合）。
        HistoryResetSignal.notifyReset()
    }

    // ===== 除外アプリ管理 =====
    fun addExcludedApp(packageName: String, appLabel: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.addExcludedApp(packageName, appLabel)
            val updated = repo.getExcludedApps()
            _excludedApps.value = updated
            excludedPackageCache.clear()
            excludedPackageCache.addAll(updated.map { it.packageName })
        }
    }

    fun removeExcludedApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.removeExcludedApp(packageName)
            val updated = repo.getExcludedApps()
            _excludedApps.value = updated
            excludedPackageCache.clear()
            excludedPackageCache.addAll(updated.map { it.packageName })
        }
    }

    // ===== 記録の一時停止（内部タイマーの完全停止＋状態リセット）=====
    // ★ これが無いと何が起きるか：
    //   statsRefreshJob（5秒おきに再生時間をDBへ書き込み続ける内部タイマー）は
    //   onMediaStateChanged()で曲の追跡を開始した時点で起動され、
    //   一度始まると「新しい曲に変わる」か「onCleared/pauseTrackingで明示的に止める」
    //   まで、このインスタンスが生きている限りバックグラウンドで動き続ける。
    //   flushCurrentTrack()はその場のチェックポイント保存をするだけで、
    //   このタイマー自体は止めない。そのため、HistoryService側のインスタンスが
    //   一度でも曲を追跡していると、フォアグラウンド復帰後もタイマーが動き続け、
    //   MainActivity側の記録と5秒おきに二重に書き込まれてしまう
    //   （実際に発生した不具合の直接の原因）。
    //
    //   pauseTracking()はタイマーを確実に止めた上で、現在の追跡分を保存し、
    //   曲の追跡状態(currentTrackKey等)も空にリセットする。
    //   こうしておくことで、次にonMediaStateChanged()が呼ばれたときに
    //   「新しい曲の追跡開始」として正しく認識され、タイマーも正常に再始動する。
    fun pauseTracking() {
        statsRefreshJob?.cancel()
        currentState?.let { state ->
            if (wasPlaying && playStartTimeMs > 0L) {
                val elapsed = System.currentTimeMillis() - playStartTimeMs
                if (elapsed > 0L) accumulatedPlayMs += elapsed
            }
            if (accumulatedPlayMs >= 10_000L) saveRecord(state, accumulatedPlayMs, trackStartMs)
        }
        resetTrackingFields()
    }

    /**
     * 現在の追跡を「保存せずに」破棄してリセットする。
     * 履歴の手動リセット（resetHistory）時に使う。
     * pauseTracking()と違い、リセット前に積み上がっていた再生時間は保存しない
     * （保存してしまうと、リセットした直後にまた同じ時間が復活してしまうため）。
     * 曲の再生自体は継続しているので、次にonMediaStateChangedが呼ばれたときに
     * 「新しい曲」として扱われ、0から正しく数え直される。
     */
    fun resetCurrentTrackingWithoutSaving() {
        statsRefreshJob?.cancel()
        resetTrackingFields()
    }

    // 追跡状態を完全リセット（再開時に「新しい曲」として正しく扱われるようにする）
    private fun resetTrackingFields() {
        currentState = null
        currentTrackKey = ""
        trackStartMs = 0L
        playStartTimeMs = 0L
        accumulatedPlayMs = 0L
        wasPlaying = false
        lastIsPlaying = false
        updateRecordingStatus()
    }

    // ===== アプリ終了時に現在の曲を保存 =====
    // ★ HistoryService（フォアグラウンドサービス）から明示的に呼び出せるよう、
    //   デフォルトのprotected（ViewModel.onCleared()由来）からpublicに広げている。
    //   中身はpauseTracking()と同じ処理のため、そちらに統一した。
    public override fun onCleared() {
        super.onCleared()
        pauseTracking()
    }

    // ===== ユーティリティ =====
    private fun todayStartMs() = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun weekStartMs() = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HistoryViewModel(
                HistoryRepository(context.applicationContext),
                context.applicationContext
            ) as T
    }
}

// ===== フォーマットユーティリティ =====
fun formatListenedTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60
    return when {
        h > 0  -> "${h}時間${m}分"
        m > 0  -> "${m}分"
        else   -> "${s}秒"
    }
}

/**
 * 設定(historyTimeDisplayMode)に応じて聴いた時間の表示形式を切り替える。
 * "auto"    : formatListenedTime と同じ（時間/分/秒を自動選択）
 * "seconds" : 常に秒で表示（例: "7384秒"）
 * "minutes" : 常に分で表示（端数切り捨て、例: "123分"）
 * "hours"   : 常に時間で表示（小数第1位まで、例: "2.1時間"）
 */
fun formatListenedTimeMode(ms: Long, mode: String): String {
    val totalSec = ms / 1000
    return when (mode) {
        "seconds" -> "${totalSec}秒"
        "minutes" -> "${totalSec / 60}分"
        "hours"   -> "%.1f時間".format(ms / 3_600_000.0)
        else      -> formatListenedTime(ms) // "auto"（未知の値もここにフォールバック）
    }
}

fun formatPlayedAt(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return "%d/%d %02d:%02d".format(
        cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
    )
}

/**
 * りれき画面の「きいた曲」カードに表示する日時文字列を、設定に応じて組み立てる。
 * @param playedAtMs 再生開始時刻
 * @param playTimeMs 実際に再生していた時間（終了時刻の算出に使用: 開始時刻 + playTimeMs）
 * @param showDate   日付部分(M/D)を表示するか
 * @param rangeMode  "start"=開始時間のみ / "end"=終了時間のみ / "both"=開始〜終了
 */
fun formatCardDateTime(
    playedAtMs: Long,
    playTimeMs: Long,
    showDate: Boolean,
    rangeMode: String
): String {
    val cal = Calendar.getInstance()
    fun timeOnly(ms: Long): String {
        cal.timeInMillis = ms
        return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
    fun dateOnly(ms: Long): String {
        cal.timeInMillis = ms
        return "%d/%d".format(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    val startMs = playedAtMs
    val endMs = playedAtMs + playTimeMs
    val timePart = when (rangeMode) {
        "end"  -> timeOnly(endMs)
        "both" -> "${timeOnly(startMs)}〜${timeOnly(endMs)}"
        else   -> timeOnly(startMs) // "start"（未知の値もここにフォールバック）
    }
    return if (showDate) "${dateOnly(startMs)} $timePart" else timePart
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
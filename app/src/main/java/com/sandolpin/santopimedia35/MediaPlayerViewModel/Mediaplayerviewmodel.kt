package com.sandolpin.santopimedia35

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.MediaState
import com.sandolpin.santopimedia35.database.QueueItem
import com.sandolpin.santopimedia35.database.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.filterNotNull

class MediaPlayerViewModel(
    private val context: Context,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    // ===== 設定 =====
    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    // ===== メディア状態 =====
    private val _mediaState = MutableStateFlow(MediaState())
    val mediaState: StateFlow<MediaState> = _mediaState.asStateFlow()

    // ===== 曲送りの方向（ホーム画面のテキストスライドアニメーション用）=====
    // 1 = 次へ(進む) / -1 = 前へ(戻る)。skipToNext/skipToPrevious を呼んだ直近の方向を
    // 保持するだけの単純な仕組みで、他アプリ側の操作やキュージャンプでは更新されない
    // （その場合は直近の方向がそのまま維持され、次にどちらかのボタンを押すまで変わらない）。
    private val _lastSkipDirection = MutableStateFlow(1)
    val lastSkipDirection: StateFlow<Int> = _lastSkipDirection.asStateFlow()

    // ===== アクティブセッション一覧（アプリ切り替え用）=====
    private val _activeSessions = MutableStateFlow<List<MediaState>>(emptyList())
    val activeSessions: StateFlow<List<MediaState>> = _activeSessions.asStateFlow()

    // ===== 現在のパッケージ名 =====
    private val _currentPackageName = MutableStateFlow("")
    val currentPackageName: StateFlow<String> = _currentPackageName.asStateFlow()

    // ===== キュー =====
    private val _queueItems = MutableStateFlow<List<QueueItem>>(emptyList())
    val queueItems: StateFlow<List<QueueItem>> = _queueItems.asStateFlow()

    // ===== シャッフル =====
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    // ===== リピート =====
    private val _repeatMode = MutableStateFlow(QueueRepeatMode.NONE)
    val repeatMode: StateFlow<QueueRepeatMode> = _repeatMode.asStateFlow()

    // ===== 歌詞スタッガー =====
    // LazyColumn外（ViewModel）でコルーチン管理することで確実に遅延を実現
    private val _lyricsActiveIndex = MutableStateFlow(-1)
    val lyricsActiveIndex: StateFlow<Int> = _lyricsActiveIndex.asStateFlow()

    private val _lyricsPastSet = MutableStateFlow(setOf<Int>())
    val lyricsPastSet: StateFlow<Set<Int>> = _lyricsPastSet.asStateFlow()

    // 未来行のハイライト（1秒後にアクティブ行より後ろの行をハイライト）
    private val _lyricsFutureHighlight = MutableStateFlow(false)
    val lyricsFutureHighlight: StateFlow<Boolean> = _lyricsFutureHighlight.asStateFlow()

    // スクロールトリガー: 1秒後にスクロール位置を通知
    private val _lyricsScrollIndex = MutableStateFlow(-1)
    val lyricsScrollIndex: StateFlow<Int> = _lyricsScrollIndex.asStateFlow()

    private var staggerJob: Job? = null
    private var lastLyricsIndex = -1

    /** currentIndexが変わったら呼ぶ。ViewModel側でスタッガー遅延を管理する */
    fun updateLyricsIndex(index: Int) {
        if (index == lastLyricsIndex) return
        lastLyricsIndex = index
        staggerJob?.cancel()
        staggerJob = viewModelScope.launch {
            // 1. 未来行ハイライトをリセット
            _lyricsFutureHighlight.value = false
            // 2. アクティブ行・過去行を即時反映 + 即時スクロール（アクティブ行を表示）
            _lyricsActiveIndex.value = index
            _lyricsPastSet.value = (0 until index).toSet()
            _lyricsScrollIndex.value = -(index + 1)  // 負値=即時スクロール用（index+1で0との衝突回避）
            // 3. 1秒後に未来行ハイライト + 未来行へのスクロール
            delay(1000)
            _lyricsFutureHighlight.value = true
            _lyricsScrollIndex.value = index  // 正値=遅延スクロール用
        }
    }

    // ===== 内部フィールド =====
    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val activeControllers = mutableMapOf<String, MediaController>()
    private var currentController: MediaController? = null
    private var progressJob: Job? = null
    private var isSessionListenerRegistered = false

    /**
     * MediaSession.QueueItem を表示用 QueueItem に変換する。
     * iconUri が無く iconBitmap のみ提供するアプリ対応:
     * Bitmap をキャッシュファイル化してURIとして扱う（AlbumArtCache）。
     * ファイルI/Oを伴うため呼び出し側で IO ディスパッチャ上から呼ぶこと。
     */
    private fun toDisplayQueueItem(item: MediaSession.QueueItem): QueueItem {
        val desc = item.description
        val uriStr = desc.iconUri?.toString()
        val resolvedUri = if (uriStr != null) {
            uriStr
        } else {
            desc.iconBitmap?.let { bmp ->
                val key = "${desc.title}|${desc.subtitle}|${item.queueId}"
                com.sandolpin.santopimedia35.AlbumArtCache.saveAndGetUri(context, bmp, key)
            }
        }
        return QueueItem(
            id = item.queueId,
            title = desc.title?.toString() ?: "",
            artist = desc.subtitle?.toString() ?: "",
            albumArtUri = resolvedUri
        )
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            refreshCurrentState()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // 非アクティブアプリが再生開始したら自動切り替え
            if (state?.state == PlaybackState.STATE_PLAYING) {
                val playingPkg = activeControllers.entries
                    .firstOrNull { (_, ctrl) ->
                        ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
                    }?.key
                if (playingPkg != null && playingPkg != _currentPackageName.value) {
                    Log.d("SantopiMedia", "Auto-switching to playing app: $playingPkg")
                    activeControllers[playingPkg]?.let { switchToController(it) }
                }
            }
            refreshCurrentState()
        }

        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
            Log.d("SantopiMedia", "onQueueChanged: ${queue?.size ?: 0} items")
            if (!queue.isNullOrEmpty()) {
                // iconBitmapのファイル化を伴うためIOディスパッチャで変換する
                viewModelScope.launch(Dispatchers.IO) {
                    val items = queue.map { item -> toDisplayQueueItem(item) }
                    _queueItems.value = items
                }
            } else {
                _queueItems.value = emptyList()
            }
        }
    }

    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            controllers?.let { updateSessions(it) }
        }

    // ===== システム音量の監視 =====
    // 従来はinit時に一度だけシステム音量を読み取るだけで、
    // ハードウェアの音量ボタンや他アプリからの音量変更には追従していなかった。
    // "android.media.VOLUME_CHANGED_ACTION" はSTREAM_MUSICの音量が変化した際に
    // システムから飛んでくるブロードキャスト（公式SDK定数ではないが、システム保護
    // ブロードキャストとして他アプリからは送信できず、多くのメディアアプリで
    // 実用上の標準として使われている）。これを購読して都度UIに反映する。
    private val volumeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                if (streamType == AudioManager.STREAM_MUSIC) {
                    refreshVolumeFromSystem()
                }
            }
        }
    }

    private fun refreshVolumeFromSystem() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol <= 0) return
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVolume = currentVol.toFloat() / maxVol.toFloat()
        if (_mediaState.value.volume != newVolume) {
            _mediaState.value = _mediaState.value.copy(volume = newVolume)
        }
    }

    init {
        // 起動時にシステム音量を初期値として取得
        refreshVolumeFromSystem()
        // 音量ボタン操作・他アプリからの音量変更にも追従できるよう監視を開始
        // （RECEIVER_NOT_EXPORTED: システム保護ブロードキャストのため他アプリに公開する必要はない）
        ContextCompat.registerReceiver(
            context,
            volumeChangeReceiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // ① Service 接続状態を監視 → 接続されたら startListening() で再取得
        MediaSessionRepository.isListenerConnected
            .onEach { connected ->
                Log.d("SantopiMedia", "isListenerConnected changed: $connected")
                if (connected) startListening()
            }
            .launchIn(viewModelScope)

        // ② Service が直接渡してきたコントローラー一覧を監視（最速ルート）
        MediaSessionRepository.controllersFromService
            .onEach { controllers ->
                Log.d("SantopiMedia", "controllersFromService: ${controllers.size} controllers")
                updateSessions(controllers)
            }
            .launchIn(viewModelScope)

        // ③ 初回：すでに権限がある場合はすぐ取得を試みる
        startListening()
        startProgressUpdater()
    }

    // ===== セッション監視 =====
    private fun startListening() {
        try {
            val componentName = ComponentName(context, MediaNotificationListener::class.java)
            Log.d("SantopiMedia", "startListening called")
            val controllers = sessionManager.getActiveSessions(componentName)
            Log.d("SantopiMedia", "startListening: found ${controllers.size} controllers")
            controllers.forEach { Log.d("SantopiMedia", "  -> ${it.packageName}") }
            updateSessions(controllers)

            // リスナーの重複登録を防ぐ
            if (!isSessionListenerRegistered) {
                sessionManager.addOnActiveSessionsChangedListener(sessionListener, componentName)
                isSessionListenerRegistered = true
                Log.d("SantopiMedia", "SessionListener registered")
            }
        } catch (e: SecurityException) {
            Log.e("SantopiMedia", "SecurityException: 通知アクセス権限なし", e)
        } catch (e: Exception) {
            Log.e("SantopiMedia", "startListening 例外", e)
        }
    }

    private fun updateSessions(controllers: List<MediaController>) {
        activeControllers.values.forEach { it.unregisterCallback(controllerCallback) }
        activeControllers.clear()

        for (controller in controllers) {
            activeControllers[controller.packageName] = controller
            controller.registerCallback(controllerCallback, Handler(Looper.getMainLooper()))
        }

        _activeSessions.value = controllers.map { buildMediaState(it) }

        // 再生中のアプリを優先してアクティブに設定
        val playingController = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }

        when {
            // 現在のコントローラーがなくなった → 再生中 or 先頭に切り替え
            !activeControllers.containsKey(_currentPackageName.value) -> {
                val target = playingController ?: controllers.firstOrNull()
                target?.let { switchToController(it) }
            }
            // 現在のコントローラーが停止中 & 別のアプリが再生中 → 自動切り替え
            playingController != null &&
                    playingController.packageName != _currentPackageName.value &&
                    currentController?.playbackState?.state != PlaybackState.STATE_PLAYING -> {
                switchToController(playingController)
            }
            else -> {
                currentController?.let { fetchQueueFromController(it) }
            }
        }
    }

    private fun refreshCurrentState() {
        val ctrl = currentController ?: run {
            Log.d("SantopiMedia", "refreshCurrentState: currentController is null")
            return
        }
        val state = buildMediaState(ctrl)
        Log.d("SantopiMedia", "refreshCurrentState: title=${state.title} isPlaying=${state.isPlaying}")
        // dominantColor・blendedArtColor・volume は外部から設定するため既存値を引き継ぐ
        _mediaState.value = state.copy(
            dominantColor = _mediaState.value.dominantColor,
            blendedArtColor = _mediaState.value.blendedArtColor,
            volume = _mediaState.value.volume
        )
        _activeSessions.value = _activeSessions.value.map {
            if (it.packageName == ctrl.packageName) _mediaState.value else it
        }
        extractDominantColor(ctrl)
    }

    private fun buildMediaState(ctrl: MediaController): MediaState {
        val meta = ctrl.metadata
        val pb = ctrl.playbackState

        val appLabel: String = try {
            context.packageManager
                .getApplicationLabel(
                    context.packageManager.getApplicationInfo(ctrl.packageName, 0)
                )
                .toString()
        } catch (e: Exception) {
            ctrl.packageName
        }

        val pos = pb?.position ?: 0L
        val dur = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 1L
        val progress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)

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
            progress = progress,
            currentPositionMs = pos,
            durationMs = if (dur > 0L) dur else 0L,
            packageName = ctrl.packageName,
            appLabel = appLabel,
            // ===== プロパティ用追加メタデータ =====
            displaySubtitle = meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
            mediaId = meta?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: "",
            displayDescription = meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION) ?: "",
            artUriType = when {
                meta?.getString(MediaMetadata.METADATA_KEY_ART_URI) != null -> "art"
                meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) != null -> "album_art"
                artBitmap != null -> "bitmap"
                else -> "none"
            },
            queueTitle = ctrl.queueTitle?.toString() ?: "",
            queueAvailable = !ctrl.queue.isNullOrEmpty(),
            activeQueueItemId = pb?.activeQueueItemId ?: -1L,
        )
    }

    // 前回処理したビットマップのハッシュ（同じ画像で再計算しないため）
    private var lastBitmapHash: Int = -1

    private fun extractDominantColor(ctrl: MediaController) {
        val bitmap = ctrl.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: ctrl.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: return

        val hash = bitmap.generationId
        if (hash == lastBitmapHash) return
        lastBitmapHash = hash

        Palette.from(bitmap).generate { palette ->
            val s = settings.value
            // パターンによって使うSwatchを変える
            val swatch = when (s.colorExtractPattern) {
                2 -> palette?.vibrantSwatch      // P2: Vibrant優先（彩度強め）
                    ?: palette?.dominantSwatch
                3 -> palette?.darkVibrantSwatch  // P3: DarkVibrant優先（落ち着いた色）
                    ?: palette?.vibrantSwatch
                    ?: palette?.dominantSwatch
                4 -> palette?.mutedSwatch        // P4: Muted優先（淡い色）
                    ?: palette?.lightMutedSwatch
                    ?: palette?.dominantSwatch
                5 -> palette?.dominantSwatch     // P5: Dominant優先（補正なし）
                else ->                          // P1: Vibrant優先（デフォルト・白黒補正あり）
                    palette?.vibrantSwatch
                        ?: palette?.darkVibrantSwatch
                        ?: palette?.mutedSwatch
                        ?: palette?.dominantSwatch
            } ?: return@generate

            val adjustedColor = adjustColor(
                swatch.rgb,
                pattern = s.colorExtractPattern,
                saturationMultiplier = s.colorSaturation,
                brightnessMultiplier = s.colorBrightness
            )
            if (_mediaState.value.dominantColor == adjustedColor) return@generate
            _mediaState.value = _mediaState.value.copy(dominantColor = adjustedColor)

            // ===== 動的アニメーション背景専用: 複数箇所から混色した色を別途保持 =====
            // プレーヤーのアクセントカラー(dominantColor)は従来通り単一Swatch方式のままにし、
            // 動的アニメーション背景で「album_art」を選んだ場合だけ、
            // 画像全体の色構成を反映したこちらの混色結果を使う。
            val blended = computeBlendedPaletteColor(palette)
            if (blended != null && _mediaState.value.blendedArtColor != blended) {
                _mediaState.value = _mediaState.value.copy(blendedArtColor = blended)
            }
        }
    }

    /**
     * Paletteが生成する主要Swatch（vibrant/darkVibrant/lightVibrant/muted/darkMuted/lightMuted/dominant）を
     * 全て集め、各Swatchの population（画像中でその色がどれだけ多く出現したか）を重みとして
     * RGB値を加重平均する。
     *
     * 【なぜこれが必要か】
     * 単一のSwatch（例: vibrantSwatch）だけを使うと、画像内で「鮮やかだが面積は小さい色」
     * （例: 一部だけの青い衣装）が選ばれやすく、実際に画像の大部分を占める色（例: 白背景）が
     * 無視されてしまう。populationで重み付けした加重平均を取ることで、
     * 実際に画像中に多く存在する色ほど強く結果に反映されるようにする。
     *
     * 【白系Swatchのボーナス加重について】
     * 単純なpopulation加重平均だけだと、鮮やかな色と白系の色を混ぜた結果は
     * 「くすんだ中間色」になりがちで、白っぽさが薄れてしまう。
     * 明度が高く彩度が低い(＝白に近い)Swatchについては、populationに加えて
     * 「白らしさ」に応じた追加ボーナスを重みに加算することで、
     * 画像に白背景が多いケースでは結果もちゃんと白寄りになるようにしている。
     */
    private fun computeBlendedPaletteColor(palette: Palette?): Color? {
        if (palette == null) return null
        val swatches = listOfNotNull(
            palette.vibrantSwatch,
            palette.darkVibrantSwatch,
            palette.lightVibrantSwatch,
            palette.mutedSwatch,
            palette.darkMutedSwatch,
            palette.lightMutedSwatch,
            palette.dominantSwatch
        )
        if (swatches.isEmpty()) return null

        var totalWeight = 0f
        var rSum = 0f; var gSum = 0f; var bSum = 0f

        for (swatch in swatches) {
            val hsv = FloatArray(3)
            AndroidColor.RGBToHSV(
                AndroidColor.red(swatch.rgb), AndroidColor.green(swatch.rgb), AndroidColor.blue(swatch.rgb), hsv
            )
            val saturation = hsv[1]
            val value = hsv[2]

            // 「白らしさ」= 明度が高く彩度が低いほど強い（0〜1）
            val whiteness = (value * (1f - saturation)).coerceIn(0f, 1f)
            // populationは実際の出現数(画像サイズやSwatch数に応じてスケールが変わる相対値)なので、
            // ボーナス係数は「populationに対する倍率」として与える方が画像サイズに依存しにくい。
            val whiteBonusMultiplier = 1f + whiteness * 2.5f

            val weight = swatch.population.toFloat().coerceAtLeast(1f) * whiteBonusMultiplier

            rSum += AndroidColor.red(swatch.rgb) * weight
            gSum += AndroidColor.green(swatch.rgb) * weight
            bSum += AndroidColor.blue(swatch.rgb) * weight
            totalWeight += weight
        }

        if (totalWeight <= 0f) return null

        val r = (rSum / totalWeight).toInt().coerceIn(0, 255)
        val g = (gSum / totalWeight).toInt().coerceIn(0, 255)
        val b = (bSum / totalWeight).toInt().coerceIn(0, 255)
        return Color(AndroidColor.rgb(r, g, b))
    }

    /**
     * パターン別の色補正
     * P1: 白黒補正あり（デフォルト）
     * P2: 彩度を強くする
     * P3: 明度を下げて落ち着かせる
     * P4: 淡くする（彩度低め・明度高め）
     * P5: 補正なし
     */
    private fun adjustColor(
        rgb: Int,
        pattern: Int = 1,
        saturationMultiplier: Float = 1.0f,
        brightnessMultiplier: Float = 1.0f
    ): Color {
        val r = AndroidColor.red(rgb) / 255f
        val g = AndroidColor.green(rgb) / 255f
        val b = AndroidColor.blue(rgb) / 255f
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b

        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(
            AndroidColor.red(rgb), AndroidColor.green(rgb), AndroidColor.blue(rgb), hsv
        )

        when (pattern) {
            1 -> {
                // P1: 白黒補正（元の実装）
                when {
                    luminance > 0.75f -> {
                        // 白に近い色: 彩度を上げて明度を下げる（上限を0.85に緩和）
                        hsv[1] = (hsv[1] + 0.4f).coerceIn(0.5f, 1.0f)
                        hsv[2] = (hsv[2] - 0.35f).coerceIn(0.35f, 0.85f)
                    }
                    luminance < 0.15f -> {
                        // 黒に近い色: 明度を上げる（上限を0.9に緩和）
                        hsv[2] = (hsv[2] + 0.35f).coerceIn(0.45f, 0.9f)
                    }
                }
            }
            2 -> {
                // P2: 彩度を強める・明度を高めに保つ
                hsv[1] = (hsv[1] + 0.3f).coerceIn(0.6f, 1.0f)
                hsv[2] = hsv[2].coerceIn(0.6f, 1.0f)
            }
            3 -> {
                // P3: 落ち着いた暗め
                hsv[1] = (hsv[1] + 0.1f).coerceIn(0.4f, 0.9f)
                hsv[2] = (hsv[2] - 0.15f).coerceIn(0.3f, 0.75f)
            }
            4 -> {
                // P4: 淡い・パステル
                hsv[1] = (hsv[1] - 0.2f).coerceIn(0.2f, 0.7f)
                hsv[2] = (hsv[2] + 0.2f).coerceIn(0.55f, 1.0f)
            }
            5 -> {
                // P5: 補正なし（Dominantそのまま）
            }
        }

        // 設定による彩度・明度の追加補正
        hsv[1] = (hsv[1] * saturationMultiplier).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * brightnessMultiplier).coerceIn(0f, 1f)

        return Color(AndroidColor.HSVToColor(hsv))
    }

    // ===== 進捗の定期更新 =====
    private fun startProgressUpdater() {
        progressJob = viewModelScope.launch {
            settings.collectLatest { s ->
                while (true) {
                    val intervalMs: Long = when (s.seekBarUpdateInterval) {
                        0.1f -> 100L
                        0.3f -> 300L
                        0.5f -> 500L
                        1.0f -> 1000L
                        else -> Long.MAX_VALUE // "なし"
                    }
                    delay(intervalMs)
                    refreshCurrentState()
                }
            }
        }
    }

    // ===== 再生コントロール =====
    fun togglePlayPause() {
        val ctrl = currentController ?: return
        if (ctrl.playbackState?.state == PlaybackState.STATE_PLAYING) {
            ctrl.transportControls.pause()
        } else {
            ctrl.transportControls.play()
        }
    }

    fun skipToPrevious() {
        _lastSkipDirection.value = -1
        currentController?.transportControls?.skipToPrevious()
    }

    fun skipToNext() {
        _lastSkipDirection.value = 1
        currentController?.transportControls?.skipToNext()
    }

    fun seekTo(progress: Float) {
        val duration = _mediaState.value.durationMs
        currentController?.transportControls?.seekTo((duration * progress).toLong())
    }

    fun seekTo(positionMs: Long) {
        currentController?.transportControls?.seekTo(positionMs)
    }

    fun seekRelative(offsetMs: Long) {
        val current = _mediaState.value.currentPositionMs
        currentController?.transportControls?.seekTo((current + offsetMs).coerceAtLeast(0L))
    }

    fun setVolume(volume: Float) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * volume).toInt(), 0)
        _mediaState.value = _mediaState.value.copy(volume = volume)
    }

    // ===== シャッフル / リピート =====
    fun toggleShuffle() {
        val enabled = !_isShuffleEnabled.value
        _isShuffleEnabled.value = enabled
        // framework の TransportControls に setShuffleMode は存在しないため
        // sendCustomAction で対応アプリに通知（非対応アプリは無視される）
        val extras = android.os.Bundle().apply { putBoolean("shuffle", enabled) }
        currentController?.transportControls?.sendCustomAction("SET_SHUFFLE", extras)
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            QueueRepeatMode.NONE -> QueueRepeatMode.ALL
            QueueRepeatMode.ALL  -> QueueRepeatMode.ONE
            QueueRepeatMode.ONE  -> QueueRepeatMode.NONE
        }
        _repeatMode.value = next
        val mode: Int = when (next) {
            QueueRepeatMode.NONE -> 0
            QueueRepeatMode.ALL  -> 2
            QueueRepeatMode.ONE  -> 1
        }
        // 同様に sendCustomAction で通知
        val extras = android.os.Bundle().apply { putInt("repeat_mode", mode) }
        currentController?.transportControls?.sendCustomAction("SET_REPEAT_MODE", extras)
    }

    fun skipToQueueItem(id: Long) {
        currentController?.transportControls?.skipToQueueItem(id)
    }

    // ===== アプリ切り替え =====
    fun switchToSession(packageName: String) {
        activeControllers[packageName]?.let { switchToController(it) }
    }

    private fun switchToController(ctrl: MediaController) {
        currentController = ctrl
        _currentPackageName.value = ctrl.packageName
        refreshCurrentState()
        // 接続時点のキューを能動的に取得（onQueueChangedは変化時しか呼ばれないため）
        fetchQueueFromController(ctrl)
    }

    private fun fetchQueueFromController(ctrl: MediaController) {
        val queue = ctrl.queue
        Log.d("SantopiMedia", "fetchQueue: ${queue?.size ?: 0} items from ${ctrl.packageName}")
        if (!queue.isNullOrEmpty()) {
            // iconBitmapのファイル化を伴うためIOディスパッチャで変換する
            viewModelScope.launch(Dispatchers.IO) {
                val items = queue.map { item -> toDisplayQueueItem(item) }
                _queueItems.value = items
            }
        } else {
            // キューが空 or null → 現在の曲だけフォールバック表示
            _queueItems.value = emptyList()
        }
    }

    fun togglePlayPauseForSession(packageName: String) {
        val ctrl = activeControllers[packageName] ?: return
        if (ctrl.playbackState?.state == PlaybackState.STATE_PLAYING) {
            ctrl.transportControls.pause()
        } else {
            ctrl.transportControls.play()
        }
    }

    fun skipToPreviousForSession(packageName: String) {
        activeControllers[packageName]?.transportControls?.skipToPrevious()
    }

    fun skipToNextForSession(packageName: String) {
        activeControllers[packageName]?.transportControls?.skipToNext()
    }

    /**
     * アプリ切り替え画面（AppSelectScreen）のシークバー用。
     * togglePlayPauseForSession等と同じく、現在アクティブなセッション(currentController)を
     * 経由せず、指定パッケージのMediaControllerへ直接シーク命令を送る
     * （このカードを選択・アクティブ化しなくても操作できるようにするため）。
     */
    fun seekToForSession(packageName: String, progress: Float, durationMs: Long) {
        val ctrl = activeControllers[packageName] ?: return
        if (durationMs <= 0L) return
        ctrl.transportControls.seekTo((durationMs * progress).toLong().coerceIn(0L, durationMs))
    }

    // ===== 履歴から曲の再生をリクエスト =====
    // 結果: SUCCESS=再生コマンド送信成功 / NO_SESSION=対象アプリのセッションが見つからない（アプリ未起動など）
    enum class PlayRequestResult { SUCCESS, NO_SESSION }

    /**
     * 指定アプリ(packageName)のMediaControllerに対して mediaId を指定して再生をリクエストする。
     * 履歴カードの「この曲を再生」から呼ばれる想定。
     * 対象アプリが現在アクティブなセッションを持っていない場合は NO_SESSION を返す
     * （呼び出し側でアプリ起動を促す等のフォールバックに使う）。
     */
    fun playFromMediaIdForSession(packageName: String, mediaId: String): PlayRequestResult {
        val ctrl = activeControllers[packageName] ?: return PlayRequestResult.NO_SESSION
        if (mediaId.isEmpty()) return PlayRequestResult.NO_SESSION
        ctrl.transportControls.playFromMediaId(mediaId, null)
        // 該当アプリにコントロールを切り替えてUIにも反映する
        switchToController(ctrl)
        return PlayRequestResult.SUCCESS
    }

    /**
     * キュー優先スマート再生 (Apple Music等のオフライン対策)
     *
     * 【問題】Apple MusicのmediaIdは10桁のカタログID。
     *   playFromMediaId() で渡すとダウンロード済み曲でもストリーミング扱いになる。
     *
     * 【解決策】
     *   Step1: 対象アプリのキューに title+artist が一致する曲があれば
     *          skipToQueueItem() で直接ジャンプ → Apple Musicがキャッシュを使って再生する
     *   Step2: キューに存在しない場合のみ playFromMediaId() にフォールバック
     *
     * @param packageName  再生対象アプリのパッケージ名
     * @param mediaId      記録済みのメディアID
     * @param title        曲タイトル（キュー照合用）
     * @param artist       アーティスト名（キュー照合用）
     * @return PlayRequestResult
     */
    fun playSmartForTrack(
        packageName: String,
        mediaId: String,
        title: String,
        artist: String
    ): PlayRequestResult {
        val ctrl = activeControllers[packageName] ?: return PlayRequestResult.NO_SESSION
        if (mediaId.isEmpty()) return PlayRequestResult.NO_SESSION

        // ===== Step 1: キュー内照合 =====
        // title + artist の正規化比較（前後スペース除去・小文字統一）
        val normalizedTitle  = title.trim().lowercase()
        val normalizedArtist = artist.trim().lowercase()

        val queue = ctrl.queue
        val queueMatch = queue?.firstOrNull { item ->
            val desc = item.description
            val qTitle  = (desc.title  ?: "").toString().trim().lowercase()
            val qArtist = (desc.subtitle ?: "").toString().trim().lowercase()
            qTitle == normalizedTitle && qArtist == normalizedArtist
        }

        return if (queueMatch != null) {
            // キューに見つかった → 対象アプリに切り替えてから skipToQueueItem
            // (skipToQueueItem は currentController を使うため先に switch が必要)
            switchToController(ctrl)
            ctrl.transportControls.skipToQueueItem(queueMatch.queueId)
            PlayRequestResult.SUCCESS
        } else {
            // キューにない → playFromMediaId にフォールバック
            ctrl.transportControls.playFromMediaId(mediaId, null)
            switchToController(ctrl)
            PlayRequestResult.SUCCESS
        }
    }

    // ===== 現在再生中アプリを起動 =====
    fun openCurrentApp() {
        val packageName = _currentPackageName.value.ifEmpty { return }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 指定パッケージのアプリを起動する（履歴の「この曲を再生」がNO_SESSIONだった場合のフォールバック用）*/
    fun openApp(packageName: String) {
        if (packageName.isEmpty()) return
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ===== 設定更新 =====
    fun updateSetting(key: Preferences.Key<String>, value: String) {
        viewModelScope.launch { settingsRepo.updateSetting { it[key] = value } }
    }

    fun updateSetting(key: Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch { settingsRepo.updateSetting { it[key] = value } }
    }

    fun updateSetting(key: Preferences.Key<Int>, value: Int) {
        viewModelScope.launch { settingsRepo.updateSetting { it[key] = value } }
    }

    fun updateSetting(key: Preferences.Key<Float>, value: Float) {
        viewModelScope.launch { settingsRepo.updateSetting { it[key] = value } }
    }

    // ===== 設定のエクスポート/インポート =====
    // Compose側（rememberLauncherForActivityResultのコールバック等、suspend関数を
    // 直接呼べない文脈）から扱いやすいよう、結果をコールバックで受け取る形にしている。
    // 実際のJSON変換・DataStoreへの反映は SettingsRepository / SettingsBackup
    // （Database.kt）に集約しており、ここは呼び出しをviewModelScopeに乗せるだけの薄い窓口。

    /** 現在の設定をJSON文字列として書き出す */
    fun exportSettingsJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = settingsRepo.exportSettingsJson()
            onResult(json)
        }
    }

    /**
     * JSON文字列から設定を読み込み、DataStoreへ反映する。
     * ファイル形式が不正な場合など、失敗時は onResult(false, エラーメッセージ) を返す
     * （壊れたファイルを読み込ませても例外でアプリが落ちないようにするため）。
     */
    fun importSettingsJson(json: String, onResult: (success: Boolean, error: String?) -> Unit) {
        viewModelScope.launch {
            try {
                settingsRepo.importSettingsJson(json)
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message ?: "不明なエラー")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        activeControllers.values.forEach { it.unregisterCallback(controllerCallback) }
        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {
            // 権限なし時に例外になる場合がある
        }
        try {
            context.unregisterReceiver(volumeChangeReceiver)
        } catch (e: Exception) {
            // 登録されていない場合に例外になることがある
        }
    }

    // ===== Factory =====
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = SettingsRepository(context.applicationContext)
            return MediaPlayerViewModel(context.applicationContext, repo) as T
        }
    }
}
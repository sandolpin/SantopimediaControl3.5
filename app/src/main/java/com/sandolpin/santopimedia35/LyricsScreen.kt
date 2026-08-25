package com.sandolpin.santopimedia35

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sandolpin.santopimedia35.database.MediaState
import com.sandolpin.santopimedia35.home.AnimatedMeshBackground
import com.sandolpin.santopimedia35.home.AlbumArtCard
import com.sandolpin.santopimedia35.home.NormalSeekBar
import com.sandolpin.santopimedia35.home.PlayerControlsA
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ===== LyricsScreen =====
@Composable
fun LyricsScreen(
    viewModel: MediaPlayerViewModel,
    onDismiss: () -> Unit,
    onOpenLyricsSettings: () -> Unit = {}
) {
    val mediaState by viewModel.mediaState.collectAsState()
    val settings   by viewModel.settings.collectAsState()
    var lyricsState by remember { mutableStateOf<LyricsState>(LyricsState.Idle) }
    // ☆=安定モード（offsetなし）、★=offsetアニメモード（デフォルトON）
    var useOffsetAnim by remember { mutableStateOf(true) }

    val accentColor  = mediaState.dominantColor
    // 動的アニメーション背景専用の色ソース（ホーム画面と同じ設定を共有）:
    // "accent"    = このアプリで言う「アクセントカラー」= アルバムアートから抽出した
    //               dominantColor（accentColorと同じ値）。
    //               ★以前は MaterialTheme.colorScheme.primary（テーマの固定色）を
    //               使っていたため、曲が変わっても常に同じ紫寄りの青になってしまっていた。
    //               HomeScreenA.kt の rawAccentColor と同じ考え方に揃える。
    // "album_art" = アルバムアートの複数箇所をpopulation比率で混色した色
    //               （単一Swatchだと面積の小さい鮮やかな色に偏るため）
    val animatedBgColor = when (settings.animatedColorSource) {
        "accent" -> accentColor
        else -> mediaState.blendedArtColor.copy(alpha = 1f)
    }
    // "blur" 直接指定、"animated"（動的アニメーション背景は常に暗め）、
    // または "player" でプレーヤー背景がぼかし/動的アニメーションの場合も白テキスト
    val isBlurBg     = settings.lyricsBg == "blur" ||
            settings.lyricsBg == "animated" ||
            (settings.lyricsBg == "player" &&
                    (settings.backgroundStyle == "blur" || settings.backgroundStyle == "animated"))
    val activeColor  = if (isBlurBg) Color.White else accentColor

    // ===== 横画面判定 =====
    // ★ 横画面のときは「左半分=プレーヤー／右半分=歌詞」の左右分割レイアウト
    //   (LyricsLandscapeContent)に切り替える。縦画面は従来通り
    //   ミニプレーヤー型ヘッダー(LyricsHeader)+下に歌詞、のまま変更しない。
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // past / future ともに alpha=0.5 均一
    val inactiveColor= if (isBlurBg) Color.White.copy(0.35f)
    else MaterialTheme.colorScheme.onSurface.copy(0.35f)
    val futureColor  = if (isBlurBg) Color.White.copy(0.35f)
    else MaterialTheme.colorScheme.onSurface.copy(0.35f)

    val context = androidx.compose.ui.platform.LocalContext.current

    // キャッシュDB初期化
    LaunchedEffect(Unit) { LyricsCache.init(context) }

    // DataStoreに古い値(15秒)が残っている場合は7秒に移行する
    LaunchedEffect(Unit) {
        if (settings.lyricsInterludeThreshold > 7) {
            viewModel.updateSetting(
                com.sandolpin.santopimedia35.database.SettingsKeys.LYRICS_INTERLUDE_THRESHOLD, 7
            )
        }
    }

    // ★ 手動操作（紐づけ解除など）で強制的に再取得させるためのトリガー。
    //   これをfetchKeyに含めておくことで、title/artistが変わっていなくても
    //   値をインクリメントするだけで下のLaunchedEffect(fetchKey)を再実行できる。
    var refreshTrigger by remember { mutableStateOf(0) }
    val fetchKey = "${mediaState.title}|${mediaState.artist}|$refreshTrigger"
    val coroutineScope = rememberCoroutineScope()
    var fetchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(fetchKey) {
        if (mediaState.title.isEmpty()) return@LaunchedEffect
        fetchJob?.cancel()
        lyricsState = LyricsState.Loading
        fetchJob = coroutineScope.launch {
            // ローカルファイル優先、なければLRCLIB API（useNetwork=falseならローカルのみ）
            val result = fetchLyricsWithLocal(
                context      = context,
                title        = mediaState.title,
                artist       = mediaState.artist,
                album        = mediaState.album,
                durationMs   = mediaState.durationMs,
                lyricsFolder = settings.lyricsFolder,
                autoRetry    = settings.lyricsAutoRetry,
                useNetwork   = settings.lyricsUseNetwork
            )
            lyricsState = result
        }
    }

    // ===== ヘッダー三点メニュー用 =====
    // 「ファイルを読み込む」: 共通のファイルピッカーを使い、選んだ瞬間に歌詞を反映
    val launchHeaderFilePicker = rememberLyricsFilePicker(
        mediaState = mediaState,
        onLyricsLoaded = { loaded -> lyricsState = loaded }
    )
    // 「歌詞をAPI検索する」: 現在の歌詞の見つかり方に関わらずいつでも呼べる検索ダイアログ
    var showManualSearchDialog by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ===== 背景 =====
            when (settings.lyricsBg) {
                "blur" -> {
                    if (mediaState.albumArtUri != null) {
                        AsyncImage(
                            model = mediaState.albumArtUri, contentDescription = null,
                            modifier = Modifier.fillMaxSize().blur(60.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)))
                }
                "gradient" -> {
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(accentColor.copy(0.55f),
                            MaterialTheme.colorScheme.background))
                    ))
                }
                "animated" -> {
                    // ホーム画面と同じ動的アニメーション背景（AGSL / API33+）。
                    // API33未満はホームと同じ方針でぼかしにフォールバックする。
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        AnimatedMeshBackground(
                            color1 = animatedBgColor,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (mediaState.albumArtUri != null) {
                        AsyncImage(
                            model = mediaState.albumArtUri, contentDescription = null,
                            modifier = Modifier.fillMaxSize().blur(60.dp),
                            contentScale = ContentScale.Crop
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)))
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    }
                }
                "player" -> {
                    if (settings.backgroundStyle == "blur" && mediaState.albumArtUri != null) {
                        AsyncImage(
                            model = mediaState.albumArtUri, contentDescription = null,
                            modifier = Modifier.fillMaxSize().blur(60.dp),
                            contentScale = ContentScale.Crop
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)))
                    } else if (settings.backgroundStyle == "gradient") {
                        Box(Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(accentColor.copy(0.5f),
                                MaterialTheme.colorScheme.background))
                        ))
                    } else if (settings.backgroundStyle == "animated") {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            AnimatedMeshBackground(
                                color1 = animatedBgColor,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (mediaState.albumArtUri != null) {
                            AsyncImage(
                                model = mediaState.albumArtUri, contentDescription = null,
                                modifier = Modifier.fillMaxSize().blur(60.dp),
                                contentScale = ContentScale.Crop
                            )
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)))
                        } else {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        }
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    }
                }
                else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }

            // ===== 歌詞コンテンツ本体（縦画面・横画面で共用するため関数値として抽出） =====
            val lyricsContent: @Composable () -> Unit = {
                when (val state = lyricsState) {
                    is LyricsState.Loading -> LoadingView(
                        onCancel = {
                            fetchJob?.cancel()
                            lyricsState = LyricsState.NotFound
                        },
                        onSkipApi = {
                            fetchJob?.cancel()
                            // この曲はAPI検索しないとしてキャッシュにスキップフラグを保存
                            LyricsCache.saveSkip(mediaState.title, mediaState.artist)
                            lyricsState = LyricsState.NotFound
                        }
                    )
                    is LyricsState.Synced -> SyncedLyricsView(
                        items = state.items,
                        currentPositionMs = mediaState.currentPositionMs,
                        accentColor = activeColor,
                        inactiveColor = inactiveColor,
                        futureColor = futureColor,
                        fontSize = settings.lyricsFontSize,
                        fontWeight = settings.lyricsFontWeight,
                        lineHeightMultiplier = settings.lyricsLineHeight,
                        lineSpacingDp = settings.lyricsLineSpacing,
                        inactiveLight = settings.lyricsInactiveLight,
                        interludeThresholdMs = settings.lyricsInterludeThreshold * 1000L,
                        multiLineThresholdMs = (settings.lyricsMultiLineThreshold * 1000f).toLong(),
                        scrollSpeedMs = settings.lyricsScrollSpeedMs,
                        scrollEasing = settings.lyricsScrollEasing,
                        offsetSync = settings.lyricsOffsetSync,
                        offsetDelayMs = settings.lyricsOffsetDelayMs,
                        offsetStaggerMs = settings.lyricsOffsetStaggerMs,
                        inactiveScale = settings.lyricsInactiveScale,
                        inactiveBlur = settings.lyricsInactiveBlur,
                        inactiveBlurRadius = settings.lyricsInactiveBlurRadius,
                        scrollAnimType = settings.lyricsScrollAnimType,
                        overshootDistance = settings.lyricsOvershootDistance,
                        fontPath = settings.lyricsFontPath,
                        forceInactiveOnEnd = settings.lyricsForceInactiveOnEnd,
                        disableMultiShow   = settings.sdlrcDisableMultiShow,
                        disableRight       = settings.sdlrcDisableRight,
                        onLineClick = { viewModel.seekTo(it) },
                        viewModel = viewModel,
                        useOffsetAnim = useOffsetAnim
                    )
                    is LyricsState.Plain -> PlainLyricsView(
                        lines = state.lines,
                        isBlurBg = isBlurBg
                    )
                    is LyricsState.NotFound -> NotFoundView(
                        isBlurBg = isBlurBg,
                        mediaState = mediaState,
                        onLyricsLoaded = { loaded -> lyricsState = loaded }
                    )
                    is LyricsState.SearchResults -> SearchResultsView(
                        results = state.results,
                        isBlurBg = isBlurBg,
                        onSelect = { id ->
                            kotlinx.coroutines.MainScope().launch {
                                lyricsState = LyricsState.Loading
                                lyricsState = fetchLyricsById(id, mediaState.title, mediaState.artist)
                            }
                        },
                        onDismiss = { lyricsState = LyricsState.NotFound }
                    )
                    is LyricsState.Error -> ErrorView(
                        message = state.message,
                        isBlurBg = isBlurBg,
                        mediaState = mediaState,
                        onLyricsLoaded = { loaded -> lyricsState = loaded }
                    )
                    else -> {}
                }
            }

            // ===== コンテンツ（横画面: 左=プレーヤー/右=歌詞、縦画面: 従来通り上下） =====
            if (isLandscape) {
                LyricsLandscapeContent(
                    mediaState = mediaState,
                    settings = settings,
                    viewModel = viewModel,
                    accentColor = if (isBlurBg) Color.White else accentColor,
                    isBlurBg = isBlurBg,
                    useOffsetAnim = useOffsetAnim,
                    onToggleOffsetAnim = { useOffsetAnim = !useOffsetAnim },
                    onDismiss = onDismiss,
                    onLoadFile = { launchHeaderFilePicker() },
                    onUnlinkFile = {
                        LyricsCache.delete(mediaState.title, mediaState.artist)
                        refreshTrigger++
                    },
                    onSearchApi = { showManualSearchDialog = true },
                    onOpenLyricsSettings = onOpenLyricsSettings,
                    lyricsContent = lyricsContent
                )
            } else {
                // ★ ミニプレーヤー本体を関数値として抽出し、上部/下部どちらの配置でも
                //   同じ内容を呼び出せるようにする。
                val miniPlayer: @Composable () -> Unit = {
                    LyricsHeader(
                        mediaState = mediaState,
                        accentColor = if (isBlurBg) Color.White else accentColor,
                        isBlurBg = isBlurBg,
                        viewModel = viewModel,
                        useOffsetAnim = useOffsetAnim,
                        onToggleOffsetAnim = { useOffsetAnim = !useOffsetAnim },
                        onLoadFile = { launchHeaderFilePicker() },
                        onUnlinkFile = {
                            // キャッシュ（ファイル紐づけ含む）を削除し、refreshTriggerで
                            // LaunchedEffect(fetchKey)を再実行 → フォルダ再走査/API検索からやり直す
                            LyricsCache.delete(mediaState.title, mediaState.artist)
                            refreshTrigger++
                        },
                        onSearchApi = { showManualSearchDialog = true },
                        onOpenLyricsSettings = onOpenLyricsSettings,
                        miniPlayerPosition = settings.lyricsMiniPlayerPosition,
                        onChangePosition = { pos ->
                            viewModel.updateSetting(
                                com.sandolpin.santopimedia35.database.SettingsKeys.LYRICS_MINI_PLAYER_POSITION,
                                pos
                            )
                        }
                    )
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    // ★ 戻るボタンはミニプレーヤーの位置(上/下)設定に関わらず、
                    //   常に画面の最上部に固定表示する（依頼により分離）。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(
                                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                                bottom = 4.dp
                            )
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.ArrowBack, "閉じる",
                                tint = if (isBlurBg) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    if (settings.lyricsMiniPlayerPosition != "bottom") {
                        miniPlayer()
                    }
                    // ★ weight(1f)を明示することで、ミニプレーヤーが上/下どちらの
                    //   宣言順であっても「ミニプレーヤーを除いた残りスペース」を
                    //   正しく歌詞エリアに割り当てられる（Columnの重み付け measurement は
                    //   宣言順に関わらず、重み無し要素を先に測ってから残りを重み要素に
                    //   配分するため）。
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        lyricsContent()
                    }
                    if (settings.lyricsMiniPlayerPosition == "bottom") {
                        miniPlayer()
                    }
                }
            }
        }
    }

    // ===== ヘッダー三点メニュー「歌詞をAPI検索する」用ダイアログ =====
    // 歌詞の表示状態（見つかっている/いない等）に関わらず、いつでも呼び出せる。
    if (showManualSearchDialog) {
        LyricsSearchDialog(
            mediaState = mediaState,
            isBlurBg = isBlurBg,
            context = context,
            onDismiss = { showManualSearchDialog = false },
            onLyricsLoaded = { result ->
                showManualSearchDialog = false
                lyricsState = result
            }
        )
    }
}

// =============================================================================
// 横画面用: 左半分=プレーヤー／右半分=歌詞
// -----------------------------------------------------------------------------
// 縦画面のLyricsHeader（折りたたみ式ミニプレーヤーカード）とは別に、横画面は
// 縦幅に余裕がないぶん横幅に余裕があるため、折りたたみ無しの「常時展開」プレーヤーを
// 左半分に固定表示する。アルバムアート・曲名・シークバー・再生コントロールに加え、
// 戻るボタン・☆/★（offsetアニメ切替）・三点メニュー（縦画面ヘッダーと同じ4項目）も
// ここに集約する。右半分は歌詞本体（lyricsContentラムダ、LyricsScreen側の
// when(lyricsState)ブロックをそのまま流用）をBoxで表示するだけ。
// =============================================================================
@Composable
private fun LyricsLandscapeContent(
    mediaState: MediaState,
    settings: com.sandolpin.santopimedia35.database.AppSettings,
    viewModel: MediaPlayerViewModel,
    accentColor: Color,
    isBlurBg: Boolean,
    useOffsetAnim: Boolean,
    onToggleOffsetAnim: () -> Unit,
    onDismiss: () -> Unit,
    onLoadFile: () -> Unit,
    onUnlinkFile: () -> Unit,
    onSearchApi: () -> Unit,
    onOpenLyricsSettings: () -> Unit,
    lyricsContent: @Composable () -> Unit
) {
    val textColor = if (isBlurBg) Color.White else MaterialTheme.colorScheme.onSurface
    val subColor = textColor.copy(alpha = 0.6f)
    var showMenu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    // ★ インカメラ（パンチホール）対策:
    //   横画面ではステータスバーの上端余白(statusBars)だけでは不十分で、
    //   端末の回転方向によってはインカメラの切り欠きが画面の左右どちらかの
    //   端に来る（縦画面では上端中央にあったものが横画面では横に移動するため）。
    //   WindowInsets.displayCutout の start/end/top を合わせて考慮することで、
    //   カメラがどちら側に来ても内容が被らないようにする。
    val cutoutPadding = WindowInsets.displayCutout.asPaddingValues()

    Row(modifier = Modifier.fillMaxSize()) {
        // ===== 左半分: プレーヤー =====
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(
                    start = 20.dp + cutoutPadding.calculateStartPadding(layoutDirection),
                    end = 20.dp
                )
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                            cutoutPadding.calculateTopPadding() + 8.dp,
                    bottom = 16.dp
                )
        ) {
            // ----- 戻るボタン + ☆/★ + 三点メニュー -----
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ArrowBack, "閉じる", tint = textColor, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleOffsetAnim, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (useOffsetAnim) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (useOffsetAnim) "安定モードに切り替え" else "アニメモードに切り替え",
                        tint = if (useOffsetAnim) accentColor else textColor.copy(0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.MoreVert, "その他の操作", tint = textColor.copy(0.8f), modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("ファイルを読み込む") },
                            leadingIcon = { Icon(Icons.Rounded.FileOpen, null, modifier = Modifier.size(20.dp)) },
                            onClick = { showMenu = false; onLoadFile() }
                        )
                        DropdownMenuItem(
                            text = { Text("歌詞ファイルの紐づけを解除") },
                            leadingIcon = { Icon(Icons.Rounded.LinkOff, null, modifier = Modifier.size(20.dp)) },
                            onClick = { showMenu = false; onUnlinkFile() }
                        )
                        DropdownMenuItem(
                            text = { Text("歌詞をAPI検索する") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
                            onClick = { showMenu = false; onSearchApi() }
                        )
                        DropdownMenuItem(
                            text = { Text("歌詞設定を開く") },
                            leadingIcon = { Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(20.dp)) },
                            onClick = { showMenu = false; onOpenLyricsSettings() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ----- アート/テキスト～シークバー～コントロールのグループ -----
            // ★ 以前は最後に Spacer(weight(1f)) でコントロールを画面下端まで
            //   押し下げていたため、横画面の低い高さでは「アート/テキストの直後に
            //   巨大な空白ができ、コントロールだけが下端に離れて見える」という
            //   不自然なレイアウトになっていた（実際に指摘された不具合）。
            //   トップバーの下の残りスペース全体をこのColumnに割り当て、
            //   その中で verticalArrangement=Center によりグループ全体を中央に
            //   まとめて配置する形に変更する（要素間の余白は固定値のまま）。
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center
            ) {
                // ----- アルバムアート（小さめ）+ 曲名・アーティスト・アルバム（横並び） -----
                Row(verticalAlignment = Alignment.Top) {
                    AlbumArtCard(
                        uri = mediaState.albumArtUri,
                        bitmap = mediaState.albumArtBitmap,
                        shadowColor = accentColor,
                        modifier = Modifier.size(110.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                        Text(
                            mediaState.title.ifEmpty { "タイトル不明" },
                            fontWeight = FontWeight.Bold, fontSize = 20.sp, color = textColor,
                            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            mediaState.artist.ifEmpty { "アーティスト不明" },
                            fontSize = 14.sp, color = subColor,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (mediaState.album.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                mediaState.album,
                                fontSize = 12.sp, color = textColor.copy(alpha = 0.45f),
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ----- シークバー -----
                NormalSeekBar(
                    progress = mediaState.progress,
                    onProgressChange = { viewModel.seekTo(it) },
                    thickness = 4.dp,
                    color = accentColor,
                    durationMs = mediaState.durationMs
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(mediaState.currentPositionStr, fontSize = 11.sp, color = subColor)
                    Text(mediaState.remainingTimeStr, fontSize = 11.sp, color = subColor)
                }

                Spacer(Modifier.height(10.dp))

                // ----- 再生コントロール -----
                // ★ isBlurBg=true のとき accentColor（=呼び出し元でColor.Whiteに上書き済み）が
                //   ボタン背景色として使われるため、アイコンをデフォルトの白のままにすると
                //   白背景に白アイコンで同化して見えなくなっていた（実際に発生した不具合）。
                //   HomeScreenA.kt の「animated+白固定ON時はplayIconColorを黒にする」のと
                //   同じ考え方で、ボタン背景が白になるケース(isBlurBg)だけアイコンを黒にする。
                PlayerControlsA(
                    isPlaying = mediaState.isPlaying,
                    onPrev = { viewModel.skipToPrevious() },
                    onNext = { viewModel.skipToNext() },
                    onPlayPause = { viewModel.togglePlayPause() },
                    onRewind = { viewModel.seekRelative(-10000L) },
                    onFastForward = { viewModel.seekRelative(10000L) },
                    onLongPressPlay = { /* 歌詞画面には長押しアクションメニューは無い */ },
                    buttonColor = accentColor,
                    iconColor = textColor,
                    playIconColor = if (isBlurBg) Color.Black else Color.Unspecified,
                    vibrationMs = if (settings.vibrationEnabled) settings.vibrationStrength else 0,
                    isBlurBg = isBlurBg,
                )
            }
        }

        // ===== 右半分: 歌詞 =====
        // ★ 回転方向によってはインカメラが右側に来ることもあるため、
        //   上端・右端にもcutoutPaddingを反映する。
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(
                    top = cutoutPadding.calculateTopPadding(),
                    end = cutoutPadding.calculateEndPadding(layoutDirection)
                )
        ) {
            lyricsContent()
        }
    }
}


@Composable
fun LyricsHeader(
    mediaState: MediaState,
    accentColor: Color,
    isBlurBg: Boolean = false,
    viewModel: MediaPlayerViewModel? = null,
    useOffsetAnim: Boolean = true,
    onToggleOffsetAnim: () -> Unit = {},
    onLoadFile: () -> Unit = {},
    onUnlinkFile: () -> Unit = {},
    onSearchApi: () -> Unit = {},
    onOpenLyricsSettings: () -> Unit = {},
    // ★ 縦画面のミニプレーヤーを画面の上部/下部どちらに表示するか。
    //   "top"=上部（デフォルト、従来通り） / "bottom"=下部
    miniPlayerPosition: String = "top",
    onChangePosition: (String) -> Unit = {}
) {
    val textColor    = if (isBlurBg) Color.White else MaterialTheme.colorScheme.onSurface
    val subColor     = textColor.copy(alpha = 0.6f)
    val surfaceColor = if (isBlurBg) Color.White.copy(0.12f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(0.85f)

    // 折りたたみ状態（初期は展開）
    var expanded by remember { mutableStateOf(true) }
    // 三点メニューの開閉状態
    var showMenu by remember { mutableStateOf(false) }

    // ★ 戻るボタンはこの関数の外（LyricsScreen側）で常に画面最上部に固定表示するため、
    //   ここでは持たない（以前はここにあったが、ミニプレーヤーの位置(上/下)設定に
    //   連動して戻るボタンごと下に移動してしまっていたのを解消するため分離した）。
    //   そのため、ここでのColumnの上端余白はステータスバー避けを目的とせず、
    //   単純な小さな余白のみにする（"top"配置時は戻るボタン行の直下、
    //   "bottom"配置時は歌詞エリアの直下に来るため、どちらもステータスバー分の
    //   余白は不要）。下端は"bottom"配置時のみナビゲーションバー分を確保する。
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .padding(
            top = 4.dp,
            bottom = if (miniPlayerPosition == "bottom")
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 4.dp
            else 4.dp
        )
    ) {        // ミニプレーヤーカード（タップで折りたたみ）
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = surfaceColor,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {

                // アルバムアート・タイトル・アーティスト＋☆ボタン（常時表示）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mediaState.albumArtUri != null) {
                        AsyncImage(
                            model = mediaState.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.MusicNote, null,
                                tint = subColor, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            mediaState.title.ifEmpty { "タイトル不明" },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, fontSize = 14.sp, color = textColor,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            mediaState.artist.ifEmpty { "アーティスト不明" },
                            fontSize = 11.sp, color = subColor, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    // ☆/★ ボタン: offsetアニメモード切り替え
                    // ★=offsetアニメON（デフォルト）、☆=安定モード（offsetなし）
                    IconButton(
                        onClick = { onToggleOffsetAnim() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (useOffsetAnim)
                                Icons.Rounded.Star
                            else
                                Icons.Rounded.StarBorder,
                            contentDescription = if (useOffsetAnim) "安定モードに切り替え" else "アニメモードに切り替え",
                            tint = if (useOffsetAnim) accentColor else textColor.copy(0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // ===== 三点メニュー =====
                    // ・ファイルを読み込む
                    // ・歌詞ファイルの紐づけを解除
                    // ・歌詞をAPI検索する
                    // ・歌詞設定を開く
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.MoreVert,
                                contentDescription = "その他の操作",
                                tint = textColor.copy(0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("ファイルを読み込む") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.FileOpen,
                                        null, modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onLoadFile()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("歌詞ファイルの紐づけを解除") },
                                leadingIcon = {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Rounded.LinkOff,
                                        null, modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onUnlinkFile()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("歌詞をAPI検索する") },
                                leadingIcon = {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Rounded.Search,
                                        null, modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onSearchApi()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("歌詞設定を開く") },
                                leadingIcon = {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Rounded.Settings,
                                        null, modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onOpenLyricsSettings()
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            // ★ ミニプレーヤーの位置切り替え（上部⇔下部）。
                            //   現在位置と逆側に切り替える単純なトグル項目。
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (miniPlayerPosition == "bottom")
                                            "ミニプレーヤーを上部に表示"
                                        else
                                            "ミニプレーヤーを下部に表示"
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (miniPlayerPosition == "bottom")
                                            androidx.compose.material.icons.Icons.Rounded.KeyboardArrowUp
                                        else
                                            androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown,
                                        null, modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onChangePosition(if (miniPlayerPosition == "bottom") "top" else "bottom")
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // シークバー（常時表示・操作可能）
                // ★ 以前はLinearProgressIndicator（表示専用・タップ操作不可）だったため、
                //   ミニプレーヤーから直接シークできなかった。HomeScreenA.kt・横画面版の
                //   歌詞画面(LyricsLandscapeContent)と同じNormalSeekBarに統一し、
                //   タップ・ドラッグでシークできるようにする。
                NormalSeekBar(
                    progress = mediaState.progress,
                    onProgressChange = { viewModel?.seekTo(it) },
                    thickness = 3.dp,
                    color = accentColor,
                    durationMs = mediaState.durationMs
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(mediaState.currentPositionStr, fontSize = 10.sp, color = subColor)
                    Text(mediaState.remainingTimeStr,   fontSize = 10.sp, color = subColor)
                }

                // 展開時のみ: コントロールボタン
                androidx.compose.animation.AnimatedVisibility(
                    visible = expanded,
                    enter = androidx.compose.animation.expandVertically() +
                            androidx.compose.animation.fadeIn(),
                    exit  = androidx.compose.animation.shrinkVertically() +
                            androidx.compose.animation.fadeOut()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel?.skipToPrevious() },
                            modifier = Modifier.size(36.dp)) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.SkipPrevious,
                                null, tint = textColor, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { viewModel?.seekRelative(-10000L) },
                            modifier = Modifier.size(36.dp)) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.Replay10,
                                null, tint = textColor, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { viewModel?.togglePlayPause() },
                            modifier = Modifier.size(44.dp)) {
                            Icon(
                                if (mediaState.isPlaying)
                                    androidx.compose.material.icons.Icons.Rounded.Pause
                                else
                                    androidx.compose.material.icons.Icons.Rounded.PlayArrow,
                                null, tint = textColor, modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(onClick = { viewModel?.seekRelative(10000L) },
                            modifier = Modifier.size(36.dp)) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.Forward10,
                                null, tint = textColor, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { viewModel?.skipToNext() },
                            modifier = Modifier.size(36.dp)) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.SkipNext,
                                null, tint = textColor, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ===== ローディング =====
@Composable
fun LoadingView(
    onCancel: (() -> Unit)? = null,
    onSkipApi: (() -> Unit)? = null
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("歌詞を取得中...", color = textColor.copy(0.6f))
            if (onCancel != null || onSkipApi != null) {
                Spacer(Modifier.height(20.dp))
                if (onCancel != null) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Close,
                            null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("検索を中止")
                    }
                }
                if (onSkipApi != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSkipApi,
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Block,
                            null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("この曲はAPI検索しない")
                    }
                }
            }
        }
    }
}

// ===== エラー =====
// NotFoundView と同様に「検索条件を変更」ボタンを表示する。
// エラー（通信エラー等）が起きても、そこで行き止まりにせず
// 手動で条件を変えて再検索できるようにするため。
@Composable
fun ErrorView(
    message: String,
    isBlurBg: Boolean = false,
    mediaState: MediaState? = null,
    onLyricsLoaded: ((LyricsState) -> Unit)? = null
) {
    val textColor = if (isBlurBg) Color.White else MaterialTheme.colorScheme.onSurface
    var showSearchDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "エラー: $message",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            // mediaState（現在再生中の曲情報）が無いと再検索できないため、ある場合のみ表示
            if (mediaState != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showSearchDialog = true },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Search, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("検索条件を変更")
                }
            }
        }
    }

    if (showSearchDialog && mediaState != null) {
        LyricsSearchDialog(
            mediaState = mediaState,
            isBlurBg = isBlurBg,
            context = context,
            onDismiss = { showSearchDialog = false },
            onLyricsLoaded = if (onLyricsLoaded != null) { result ->
                showSearchDialog = false
                onLyricsLoaded(result)
            } else null
        )
    }
}

// ===== スクロール量の事前計算 =====
// offsetアニメの初期値設定に使用（スクロール実行前に呼ぶ）
private fun calcScrollDiff(
    state: androidx.compose.foundation.lazy.LazyListState,
    index: Int,
    offsetPx: Int
): Float {
    if (state.layoutInfo.totalItemsCount == 0) return 0f
    val safeIndex = index.coerceIn(0, state.layoutInfo.totalItemsCount - 1)
    val info = state.layoutInfo
    val targetItem = info.visibleItemsInfo.firstOrNull { it.index == safeIndex }
    return if (targetItem != null) {
        (targetItem.offset - offsetPx).toFloat()
    } else {
        val avgH = if (info.visibleItemsInfo.isNotEmpty())
            info.visibleItemsInfo.sumOf { it.size } / info.visibleItemsInfo.size.toFloat()
        else 120f
        val firstIdx = state.firstVisibleItemIndex
        val firstOff = state.firstVisibleItemScrollOffset
        (safeIndex - firstIdx) * avgH - firstOff - offsetPx
    }
}

// ===== なめらかスクロールヘルパー =====
private suspend fun scrollWithAnim(
    state: androidx.compose.foundation.lazy.LazyListState,
    index: Int,
    offsetPx: Int,
    durationMs: Long = 500L,
    easing: String = "decelerate",
    overshootDistancePercent: Int = 10, // オーバーシュートの行き過ぎ量(%): 3/5/10/15
    onProgress: (Float) -> Unit = {}
): Float {
    if (state.layoutInfo.totalItemsCount == 0) return 0f
    val safeIndex = index.coerceIn(0, state.layoutInfo.totalItemsCount - 1)

    fun applyEasing(t: Float): Float = when (easing) {
        "overshoot" -> {
            // easeOutBack: 目標を一度行き過ぎてから戻ってくるバウンド感のある動き
            // c1=1.70158 のとき標準の「10%行き過ぎる」動きになるため、
            // 設定された行き過ぎ量(%)からその比率でc1を逆算する。
            val c1 = 1.70158f * (overshootDistancePercent / 10f)
            val c3 = c1 + 1f
            val u = t - 1f
            1f + c3 * u * u * u + c1 * u * u
        }
        "decelerate_circ" -> {
            // easeOutCirc: 円弧を描くような自然な減速カーブ
            val u = t - 1f
            kotlin.math.sqrt((1f - u * u).coerceAtLeast(0f))
        }
        "decelerate_quad" -> {
            // easeOutQuad: 最もマイルドな減速
            val u = 1f - t; 1f - u * u
        }
        "decelerate_quint" -> {
            // easeOutQuint: 強めの減速
            val u = 1f - t; 1f - u * u * u * u * u
        }
        "decelerate_expo" -> {
            // easeOutExpo: 序盤に急停止し、その後ゆっくり収束する独特の減速感
            if (t >= 1f) 1f else 1f - Math.pow(2.0, -10.0 * t).toFloat()
        }
        else -> {
            // デフォルト = decelerate_quint と同じ
            val u = 1f - t; 1f - u * u * u * u * u
        }
    }

    val totalDiff = calcScrollDiff(state, safeIndex, offsetPx)
    if (kotlin.math.abs(totalDiff) < 2f) return 0f

    state.scroll(scrollPriority = androidx.compose.foundation.MutatePriority.Default) {
        val startMs = System.currentTimeMillis()
        var prevScrolled = 0f
        while (true) {
            val elapsed = System.currentTimeMillis() - startMs
            val t = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            val progress = applyEasing(t)
            val scrolled = totalDiff * progress
            scrollBy(scrolled - prevScrolled)
            onProgress(progress)
            prevScrolled = scrolled
            if (t >= 1f) break
            kotlinx.coroutines.delay(16)
        }
    }
    return totalDiff
}

// ===== 同期歌詞ビュー =====
@Composable
fun SyncedLyricsView(
    items: List<LyricItem>,
    currentPositionMs: Long,
    accentColor: Color,
    inactiveColor: Color = Color.Unspecified,
    futureColor: Color = Color.Unspecified,
    fontSize: Int = 34,
    fontWeight: Int = 700,
    lineHeightMultiplier: Float = 1.4f,
    lineSpacingDp: Float = 8f,
    inactiveLight: Boolean = true,
    interludeThresholdMs: Long = 7000L,
    multiLineThresholdMs: Long = 2500L,
    scrollSpeedMs: Int = 500,           // スクロール速さ
    scrollEasing: String = "decelerate", // スクロールeasing: "decelerate"/"accelerate_decelerate"/"bounce"
    offsetSync: String = "together",    // "together"=全行同時 / "stagger"=行ごとに遅延
    offsetDelayMs: Int = 50,            // offsetアニメ開始遅延(ms)
    offsetStaggerMs: Int = 50,          // staggerモード時の行ごと遅延間隔(ms)
    inactiveScale: Float = 0.98f,       // 非アクティブ歌詞の大きさ(0.97～1.00)
    inactiveBlur: Boolean = false,      // 非アクティブ歌詞をぼかす
    inactiveBlurRadius: Float = 1.5f,   // ぼかし強度(dp): 0=行距離に応じて自動調整
    scrollAnimType: String = "decelerate_quint", // スクロールアニメ種類
    overshootDistance: Int = 10,        // オーバーシュートの行き過ぎ量(%): 3/5/10/15
    fontPath: String = "",              // カスタムフォントのURI文字列
    forceInactiveOnEnd: Boolean = true, // endMs到達で強制非アクティブ(sdlrc)
    disableMultiShow: Boolean = false,  // sdlrc複数同時表示を無効化
    disableRight: Boolean = false,      // sdlrc右寄せを無効化
    onLineClick: (Long) -> Unit,
    viewModel: MediaPlayerViewModel,
    useOffsetAnim: Boolean = true
) {
    // currentIndex: 現在再生位置に対応するアクティブ行のインデックス
    // 「timeMs <= currentPositionMs」を満たす行のうち最後のもの
    // (同一timeMsが複数ある場合は先頭インデックスを使う)
    // ★ 最後の行の推定終了時刻を過ぎたら -1 に戻して全行非アクティブにする
    val currentIndex = remember(currentPositionMs, items) {
        val lines = items.filterIsInstance<LyricItem.Line>()
        val firstLineTime = lines.firstOrNull()?.timeMs ?: 0L
        if (currentPositionMs < firstLineTime) return@remember -1

        var lastMatchTime = -1L
        var lastMatchIdx  = -1
        for (i in items.indices) {
            val item = items[i]
            if (item is LyricItem.Line && item.timeMs <= currentPositionMs) {
                if (item.timeMs > lastMatchTime) {
                    lastMatchTime = item.timeMs
                    lastMatchIdx  = i
                }
            }
        }
        if (lastMatchIdx < 0) return@remember -1

        // 最後の行かどうか判定
        // ★ 最後の行だけは forceInactiveOnEnd の設定に関わらず、
        //   endMs（無ければ推定終了時刻）を過ぎたら強制的に非アクティブにする。
        //   これが無いと最後の行が再生終了までずっとアクティブ表示のままになるため。
        val lastLine = lines.lastOrNull()
        val matchedLine = items[lastMatchIdx]
        if (matchedLine is LyricItem.Line && matchedLine == lastLine) {
            val estimatedEndMs = if (matchedLine.endMs != -1L) {
                matchedLine.endMs
            } else {
                matchedLine.timeMs + 5_000L
            }
            if (currentPositionMs >= estimatedEndMs) return@remember -1
        }

        lastMatchIdx
    }

    // sdlrc以外(lrc)ではmultiShow・間奏ドットを使わない
    // lrcはbuildLyricItemsでInterludeを生成しないため、
    // Interludeが存在するか endMs != -1L の行があればsdlrc形式と判定
    val isSdlrc = remember(items) {
        items.any { it is LyricItem.Line && it.endMs != -1L } ||
                items.any { it is LyricItem.Interlude }
    }

    // ===== sdlrc設定の適用 =====
    // disableMultiShow=true の場合 multiShowIndices を強制空にする（isSdlrcより優先）
    val effectiveDisableMultiShow = disableMultiShow

    // ===== isRight判定 =====
    // disableRight=true の場合は isRight を全て false 扱い
    // isRight行が1つでもあれば(かつdisableRightでなければ) 85%幅、なければ 95%幅
    val hasRightLines = remember(items, disableRight) {
        !disableRight && items.any { it is LyricItem.Line && it.isRight }
    }
    val lyricLineWidth = if (hasRightLines) 0.85f else 0.95f

    // ===== multiShow判定（sdlrcのみ有効）=====
    // ★ 判定方法を「currentPositionMsに対して毎回再計算」ではなく、
    //   「currentIndexが変わった瞬間のcurrentPositionMsだけを使って、
    //   その時点で重なっている行を確定し、それをcurrentIndexが変わるまで維持する」
    //   方式に変更する。
    //
    //   （変更前の問題点）
    //   remember のキーに currentPositionMs を含めて「currentLineの時間範囲(固定値)と
    //   他の行の時間範囲(固定値)が重なっているか」を判定していたため、
    //   currentPositionMs の値に関係なく、currentLineが同じ行である間はずっと
    //   同じ判定結果になっていた。これにより、たとえばcurrentLineが行1
    //   （00:55.97〜01:00.94）である間（＝00:55.97の時点から）、本来は
    //   01:00.27にならないと重ならないはずの行3・行4的な離れた行までもが、
    //   時間に関係なく最初からmultiShow扱いされてしまう不具合の原因になっていた。
    //
    //   （修正後）
    //   currentIndexが切り替わった瞬間の currentPositionMs だけを使って
    //   「今まさに重なっている行」を1回だけ確定し、以後はcurrentIndexが
    //   変わるまでそのSetをそのまま保持する。currentPositionMsの経過では
    //   再計算しないため、後から時間経過で新しい行が巻き込まれることもなく、
    //   かつ「先に終わった行がendMs到達で消えない」という目的も両立できる。
    var multiShowIndices by remember { mutableStateOf(setOf<Int>()) }
    LaunchedEffect(currentIndex) {
        if (!isSdlrc || effectiveDisableMultiShow ||
            currentIndex < 0 || currentIndex >= items.size) {
            multiShowIndices = emptySet()
            return@LaunchedEffect
        }
        val currentLine = items[currentIndex]
        if (currentLine !is LyricItem.Line || currentLine.endMs == -1L) {
            multiShowIndices = emptySet()
            return@LaunchedEffect
        }

        // currentIndexに切り替わった「今」の再生位置を使い、
        // その瞬間に currentLine の時間範囲内で実際に発声中の行だけを収集する。
        val nowMs = currentPositionMs
        val result = mutableSetOf<Int>()
        for (i in items.indices) {
            if (i == currentIndex) continue
            val item = items[i]
            if (item is LyricItem.Line &&
                item.endMs != -1L &&
                nowMs >= item.timeMs &&
                nowMs < item.endMs &&
                item.timeMs < currentLine.endMs) {
                result.add(i)
            }
        }
        multiShowIndices = result
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current

    var scrollDirection by remember { mutableStateOf(1) }
    var prevIndex by remember { mutableStateOf(0) }
    var prevMultiShowIndices by remember { mutableStateOf(setOf<Int>()) }

    // ===== offsetアニメ用 =====
    // scrollDiffPx: 今回のスクロールで実際に動く量(px)。
    //   1行スクロールなら小さく、2行以上なら大きくなる。
    //   isFuture行の translationY 初期値としてこの値を使うことで
    //   「スクロール量に比例した自然なスライドイン」になる。
    // scrollDiffForAnim: animateFloatAsState で滑らかに変化させるためのState。
    //   これを各行の LaunchedEffect のキーにすることで高さ変化もアニメーションする。
    var scrollDiffPx by remember { mutableStateOf(0f) }
    // 実スクロールの進捗。各行がこの量だけ一時的に相殺してから、
    // stagger 順に相殺を解除する。
    var scrollProgress by remember { mutableStateOf(0f) }

    // ===== 手動スクロール検知・ぼかし抑制 =====
    // isUserScrolling は「手を離した瞬間にfalse」になるため、
    // スクロール後すぐぼかしが戻ってしまう問題がある。
    // 代わりに isBlurSuppressed フラグを使い、以下のタイミングで制御する:
    //   ON : 手動スクロール開始時
    //   OFF: 歌詞行タップ時 / currentIndex が変化した時（次の歌詞に進んだ時）
    //        ただし間奏中は OFF にしない
    var isAutoScrolling by remember { mutableStateOf(false) }
    var isBlurSuppressed by remember { mutableStateOf(false) }

    // ===== 間奏がアクティブかどうか（isBlurSuppressed判定で使うため、この位置で先に計算しておく）=====
    val activeInterludeStartMs = remember(currentPositionMs, items, interludeThresholdMs) {
        items.filterIsInstance<LyricItem.Interlude>()
            .firstOrNull { item ->
                val gapMs = item.endMs - item.startMs
                gapMs >= interludeThresholdMs &&
                        currentPositionMs >= item.startMs &&
                        currentPositionMs < item.endMs
            }?.startMs ?: -1L
    }

    // 手動スクロール開始を検知してぼかし抑制をON
    // ★ 間奏中（activeInterludeStartMs >= 0L）は、間奏スクロールに伴う
    //   isScrollInProgressの一時的な変化を手動スクロールと誤認しないよう、
    //   ぼかし抑制自体をかけない保険を入れる。
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isAutoScrolling && activeInterludeStartMs < 0L) {
            isBlurSuppressed = true
        }
    }

    // ★ 自動スクロールが完了したタイミング（true→falseに変わった瞬間）でぼかし抑制をOFFに戻す。
    //   LaunchedEffect(isAutoScrolling) だと「初回コンポーズ時にfalseである」ことでも
    //   発火してしまい、手動スクロール中のisBlurSuppressed=trueまで巻き込んで
    //   即座にリセットしてしまう。そのため true→false の遷移だけを検知する。
    LaunchedEffect(Unit) {
        var wasAutoScrolling = isAutoScrolling
        snapshotFlow { isAutoScrolling }.collect { scrolling ->
            if (wasAutoScrolling && !scrolling) {
                isBlurSuppressed = false
            }
            wasAutoScrolling = scrolling
        }
    }

    // ===== 間奏スクロール =====
    LaunchedEffect(activeInterludeStartMs) {
        if (activeInterludeStartMs < 0L) return@LaunchedEffect
        val interludeIdx = items.indexOfFirst {
            it is LyricItem.Interlude && it.startMs == activeInterludeStartMs
        }
        if (interludeIdx < 0) return@LaunchedEffect
        val viewportH2 = listState.layoutInfo.viewportSize.height
        // ★ isAutoScrolling を立てずに scrollWithAnim を呼ぶと、
        //   内部の実スクロールが listState.isScrollInProgress を true にし、
        //   「手動スクロールが起きた」と誤判定されて isBlurSuppressed が true になり
        //   間奏中ずっとぼかしが解除されたままになっていた。currentIndex用のスクロールと
        //   同様に isAutoScrolling を正しく管理する。
        isAutoScrolling = true
        scrollWithAnim(listState, interludeIdx, (viewportH2 * 0.15f).toInt(),
            durationMs = scrollSpeedMs.toLong(), easing = scrollEasing)
        isAutoScrolling = false
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex < 0) return@LaunchedEffect

        val wasMulti = prevMultiShowIndices.isNotEmpty()
        val isMultiNow = multiShowIndices.isNotEmpty()
        prevMultiShowIndices = multiShowIndices

        scrollDirection = if (currentIndex >= prevIndex) 1 else -1
        prevIndex = currentIndex

        val viewportH = listState.layoutInfo.viewportSize.height

        // アクティブ行が画面上端から15%の位置に来るように設定
        // （1つ前の行がちらっと見えるくらいの余白）
        val topOffset = (viewportH * 0.15f).toInt()

        val allActiveIndices = (multiShowIndices + currentIndex)
            .filter { it >= 0 }.toSortedSet()
        val topScrollIndex = allActiveIndices.firstOrNull()?.coerceAtLeast(0) ?: currentIndex

        // スクロール先を決定:
        // multiShow時・通常時どちらも topScrollIndex（最上部アクティブ行）を使用
        val target = when {
            isMultiNow  -> topScrollIndex   // multiShow: 複数行の中で最上の行へ
            wasMulti    -> currentIndex     // multiShow終了直後: currentIndexへ
            else        -> currentIndex     // 通常: currentIndexへ
        }

        val diff = if (currentIndex > 0) calcScrollDiff(listState, target, topOffset) else 0f
        scrollDiffPx = diff
        scrollProgress = 0f

        val resolvedEasing = scrollAnimType

        // multiShow時も含めて常にスクロールを実行する
        when {
            currentIndex <= 0 -> { /* 先頭はスクロール不要 */ }
            else -> {
                isAutoScrolling = true
                try {
                    scrollWithAnim(listState, target, topOffset,
                        durationMs = scrollSpeedMs.toLong(), easing = resolvedEasing,
                        overshootDistancePercent = overshootDistance,
                        onProgress = { scrollProgress = it })
                } finally {
                    isAutoScrolling = false
                }
            }
        }
    }

    // カスタムフォントの読み込み（URIのストリームをキャッシュファイルにコピーしてTypeface生成）
    val context = androidx.compose.ui.platform.LocalContext.current
    val customFontFamily = remember(fontPath) {
        if (fontPath.isEmpty()) return@remember null
        try {
            val uri = android.net.Uri.parse(fontPath)
            // コンテンツURIは直接Typefaceに渡せないため、
            // キャッシュディレクトリに一時コピーしてからファイルパスで読み込む
            val cacheFile = java.io.File(context.cacheDir, "lyrics_custom_font.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            val typeface = android.graphics.Typeface.createFromFile(cacheFile)
            FontFamily(typeface)
        } catch (e: Exception) {
            android.util.Log.e("SantopiMedia", "フォント読み込み失敗", e)
            null
        }
    }

    // フォントサイズに応じた水平余白
    val hPadding = (32 - (fontSize - 20).coerceIn(0, 20)).coerceAtLeast(8).dp

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            userScrollEnabled = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = hPadding, end = hPadding,
                top = 16.dp, bottom = 600.dp
            ),
            verticalArrangement = Arrangement.spacedBy(lineSpacingDp.dp)
        ) {
            itemsIndexed(items) { index, item ->
                when (item) {
                    is LyricItem.Line -> {
                        // 間奏中かどうか: 次のアイテムがInterludeで現在位置がその範囲内
                        val nextItem = items.getOrNull(index + 1)
                        val isDuringInterlude = nextItem is LyricItem.Interlude &&
                                currentPositionMs >= nextItem.startMs &&
                                currentPositionMs < nextItem.endMs

                        // 間奏中は currentIndex 行も非アクティブ(isPast)扱いにする
                        // forceInactiveOnEnd: sdlrc行でendMs設定済みかつ現在位置がendMsを超えたら非アクティブ
                        // ★ カラオケ行(isKaraoke)はワイプ表現の性質上、endMsを過ぎたら
                        //   「歌い終わった」状態を見た目に反映する必要があるため、
                        //   forceInactiveOnEnd の設定に関わらず常にendMs判定を適用する。
                        // ★ ただしこの判定は「単独アクティブ行(isActive)」にのみ適用する。
                        //   multiShow中の行(isMulti)にも適用すると、2行同時表示中に
                        //   先に発声が終わった方の行だけが自分のendMs到達で強制非表示になり、
                        //   「2行あるのに1行しかワイプ表示されない」原因になっていたため。
                        //   multiShow行はcurrentIndex行の表示が終わるまで表示を維持する。
                        val pastEndMs = (forceInactiveOnEnd || item.isKaraoke) &&
                                item.endMs != -1L &&
                                currentPositionMs >= item.endMs
                        val isActive = currentIndex >= 0 && index == currentIndex && !isDuringInterlude && !pastEndMs
                        val isMulti  = currentIndex >= 0 && multiShowIndices.contains(index) && !isDuringInterlude
                        val isPast   = currentIndex >= 0 && (index < currentIndex || (index == currentIndex && isDuringInterlude)) && !isMulti

                        val isFuture = index > currentIndex && !isMulti
                        val isAnimTarget = isActive || isMulti || isPast || isFuture

                        // distFromActive: ぼかし強度の自動計算用（Composableスコープで算出）
                        val distFromActive = when {
                            index > currentIndex -> (index - currentIndex).coerceAtLeast(0)
                            else                 -> (currentIndex - index).coerceAtLeast(0)
                        }

                        // 1f = スクロール相殺なし、0f = 相殺を保持中。
                        val releaseAnim = remember { Animatable(1f) }

                        // 行高さの推定値（可視アイテムの平均、なければfontSize*lineHeight*densityで推定）
                        val estItemH = with(density) {
                            listState.layoutInfo.visibleItemsInfo
                                .map { it.size }.average()
                                .takeIf { !it.isNaN() && it > 0.0 }?.toFloat()
                                ?: (fontSize * lineHeightMultiplier * 1.5f).dp.toPx()
                        }
                        // 余白 = 行高さの45%
                        val maxOffsetPx = estItemH * 0.45f

                        // ===== currentIndex と scrollDirection を State として渡す =====
                        // LaunchedEffect は起動時の値をキャプチャするが、
                        // scrollDirection は var mutableStateOf のため再コンポーズ前の値を
                        // キャプチャしてしまう。
                        // snapshotFlow で State の最新値をコルーチン内で確実に読む。
                        LaunchedEffect(currentIndex) {
                            if (!useOffsetAnim || !isAnimTarget) {
                                releaseAnim.snapTo(1f)
                                return@LaunchedEffect
                            }

                            val rawOffset = kotlin.math.abs(scrollDiffPx).coerceIn(0f, maxOffsetPx)
                            if (rawOffset == 0f) {
                                releaseAnim.snapTo(1f)
                                return@LaunchedEffect
                            }

                            // LaunchedEffect 内で scrollDirection・行位置を再評価
                            val fwd = scrollDirection >= 0
                            val thisIsActive = index == currentIndex || isMulti
                            val thisIsFuture = index > currentIndex && !isMulti
                            val thisIsPast   = index < currentIndex && !isMulti

                            // 前進時の過去行は、画面外から遅れて compose されることがある。
                            // その時点で offset を始めると、アクティブ行の上で後追いの
                            // ガクつきが起きるため、過去行は今回の遅延対象にしない。
                            if (fwd && thisIsPast) {
                                releaseAnim.snapTo(1f)
                                return@LaunchedEffect
                            }

                            // ===== 遅延設計 =====
                            // 順スクロール（前進）:
                            //   past/active = 0ms（即時）
                            //   future      = 100ms + stagger
                            // 逆スクロール（後退）:
                            //   past        = 0ms（即時）
                            //   active      = 50ms
                            //   future      = 100ms + stagger
                            val thisDist = when {
                                thisIsFuture -> (index - currentIndex).coerceAtLeast(0)
                                thisIsPast   -> (currentIndex - index).coerceAtLeast(0)
                                else         -> 0
                            }
                            val baseDelayMs = offsetDelayMs.toLong()
                            val widenedStaggerMs = (offsetStaggerMs * 1.8f).toLong()
                            val thisDelayMs = when {
                                fwd && thisIsFuture ->
                                    if (offsetSync == "stagger")
                                        baseDelayMs + (thisDist - 1).coerceAtLeast(0) * widenedStaggerMs
                                    else baseDelayMs
                                !fwd && thisIsActive -> baseDelayMs
                                !fwd && thisIsFuture ->
                                    if (offsetSync == "stagger")
                                        baseDelayMs + (thisDist - 1).coerceAtLeast(0) * widenedStaggerMs
                                    else baseDelayMs
                                else -> 0L  // 順スクロールのpast/active、逆スクロールのpastは即時
                            }

                            // スクロール量を保持して表示位置を一時停止させ、
                            // 行ごとの遅延後に解除する。下方向への移動は発生しない。
                            releaseAnim.snapTo(0f)
                            kotlinx.coroutines.delay(thisDelayMs)
                            releaseAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = (scrollSpeedMs * 0.70f).toInt().coerceAtLeast(1),
                                    easing = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, 0.2f, 1f)
                                )
                            )
                        }
                        val scrolledPx = scrollDiffPx * scrollProgress
                        val cappedScrolledPx = when {
                            scrolledPx > 0f -> scrolledPx.coerceAtMost(maxOffsetPx)
                            else -> scrolledPx.coerceAtLeast(-maxOffsetPx)
                        }
                        val rowTranslationY = if (useOffsetAnim && isAnimTarget) {
                            cappedScrolledPx * (1f - releaseAnim.value)
                        } else 0f

                        if (item.isKaraoke) {
                            // ===== カラオケ行: ワイプアニメーション =====
                            KaraokeLineItem(
                                karaoke = item.karaoke,
                                currentPositionMs = currentPositionMs,
                                isActive = isActive || isMulti,
                                isPast = isPast,
                                accentColor = accentColor,
                                inactiveColor = if (inactiveColor == Color.Unspecified)
                                    MaterialTheme.colorScheme.onSurface.copy(0.35f) else inactiveColor,
                                fontSize = fontSize,
                                fontWeight = fontWeight,
                                lineHeightMultiplier = lineHeightMultiplier,
                                inactiveScale = inactiveScale,
                                fontFamily = customFontFamily,
                                translationY = rowTranslationY,
                                isRight = if (disableRight) false else item.isRight,
                                lineWidthFraction = lyricLineWidth,
                                onClick = {
                                    isBlurSuppressed = false
                                    coroutineScope.launch { onLineClick(item.timeMs) }
                                }
                            )
                        } else {
                            LyricLineItem(
                                text = item.text,
                                isActive = isActive || isMulti,
                                isPast = isPast,
                                accentColor = accentColor,
                                inactiveColor = if (inactiveColor == Color.Unspecified)
                                    MaterialTheme.colorScheme.onSurface.copy(0.35f) else inactiveColor,
                                futureColor = if (futureColor == Color.Unspecified)
                                    MaterialTheme.colorScheme.onSurface.copy(0.35f) else futureColor,
                                fontSize = fontSize,
                                fontWeight = fontWeight,
                                lineHeightMultiplier = lineHeightMultiplier,
                                inactiveLight = inactiveLight,
                                inactiveScale = inactiveScale,
                                // ★ 間奏中でもぼかしを解除しないよう activeInterludeStartMs による条件を撤去
                                inactiveBlur = inactiveBlur && !isBlurSuppressed,
                                inactiveBlurRadius = inactiveBlurRadius,
                                distFromActive = distFromActive,
                                fontFamily = customFontFamily,
                                translationY = rowTranslationY,
                                isRight = if (disableRight) false else item.isRight,
                                lineWidthFraction = lyricLineWidth,
                                onClick = {
                                    isBlurSuppressed = false
                                    coroutineScope.launch { onLineClick(item.timeMs) }
                                }
                            )
                        }
                    }
                    is LyricItem.Interlude -> {
                        val gapMs = item.endMs - item.startMs
                        // ★ sdlrc以外・閾値未満の間奏は「表示しない」のではなく
                        //   isActive を常にfalse扱いにすることで対応する。
                        //   if分岐でコンポーザブル自体の呼び出しをスキップすると、
                        //   間奏終了の瞬間に InterludeIndicator がコンポジションから
                        //   完全に消滅し、内部で用意している縮小・フェードアウト
                        //   アニメーションが実行される前に強制終了してしまう
                        //   （LazyColumn側のレイアウトも同時に変化してガクつきの原因になる）。
                        //   常にコンポーズし続け、表示すべきでない場合は isActive=false を
                        //   渡すことで、InterludeIndicator内部の「常時コンポーズ・高さ固定・
                        //   scaleYのみアニメーション」という設計を活かす。
                        val qualifiesAsInterlude = isSdlrc && gapMs >= interludeThresholdMs
                        val isActive = qualifiesAsInterlude &&
                                currentPositionMs >= item.startMs &&
                                currentPositionMs < item.endMs
                        val progress = if (isActive)
                            ((currentPositionMs - item.startMs).toFloat() /
                                    (item.endMs - item.startMs).toFloat()).coerceIn(0f, 1f)
                        else 0f
                        val remainingMs = if (isActive) item.endMs - currentPositionMs else 0L
                        // 次の行が右寄せかどうか取得
                        val nextIsRight = items.getOrNull(index + 1)
                            .let { it is LyricItem.Line && it.isRight }

                        InterludeIndicator(
                            accentColor = accentColor,
                            isActive = isActive,
                            progress = progress,
                            remainingMs = remainingMs,
                            isRight = nextIsRight,
                            onClick = { coroutineScope.launch { onLineClick(item.startMs) } }
                        )
                    }
                }
            }
        }
    } // Box
}

// ===== 歌詞1行アイテム =====
@Composable
fun LyricLineItem(
    text: String,
    isActive: Boolean,
    isPast: Boolean,
    accentColor: Color,
    inactiveColor: Color = Color.Unspecified,
    futureColor: Color = Color.Unspecified,
    fontSize: Int = 34,
    fontWeight: Int = 700,
    lineHeightMultiplier: Float = 1.4f,
    inactiveLight: Boolean = true,
    inactiveScale: Float = 0.98f,       // 非アクティブ歌詞の大きさ(0.97～1.00)
    inactiveBlur: Boolean = false,      // 非アクティブ歌詞をぼかす
    inactiveBlurRadius: Float = 1.5f,   // ぼかし強度(dp): 0=自動（行距離に応じて調整）
    distFromActive: Int = 0,            // アクティブ行からの距離（自動ぼかし用）
    fontFamily: FontFamily? = null,     // カスタムフォント（nullでシステムフォント）
    translationY: Float = 0f,           // graphicsLayer translationY (px)
    isRight: Boolean = false,
    lineWidthFraction: Float = 0.85f,   // テキスト幅の割合 (isRight行なし→0.95f)
    onClick: () -> Unit
) {
    val resolvedInactive = if (inactiveColor == Color.Unspecified)
        MaterialTheme.colorScheme.onSurface.copy(0.35f) else inactiveColor
    val resolvedFuture = if (futureColor == Color.Unspecified)
        MaterialTheme.colorScheme.onSurface.copy(0.35f) else futureColor

    val colorSpec: AnimationSpec<Color> =
        tween(durationMillis = 400, easing = FastOutSlowInEasing)

    val textColor by animateColorAsState(
        targetValue = when {
            isActive -> accentColor
            isPast   -> resolvedInactive
            else     -> resolvedFuture
        },
        animationSpec = colorSpec,
        label = "lyricColor"
    )

    val activeFontWeight = FontWeight(fontWeight)

    // ===== ガクつき防止 =====
    // fontWeight を Light/Normal/Bold と切り替えると文字幅が変わり折り返し位置が変化、
    // LazyColumn がアイテム高さを再計算して下の行を瞬時に押し下げる（ガクっ）。
    // → fontWeight は常に activeFontWeight 固定にしてレイアウト高さを変えない。
    // → 太さの視覚差は color の alpha 差（accentColor vs 薄いグレー）だけで表現する。

    // 非アクティブ時のscale（animateFloatAsStateで滑らかに変化）
    val targetScale by animateFloatAsState(
        targetValue = if (isActive) 1.0f else inactiveScale,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "lyricScale"
    )
    // 非アクティブblur量（animateFloatAsStateで滑らかに変化）
    // inactiveBlurRadius == 0f のとき自動モード: 行距離に応じて線形にぼかし強度を上げる
    //   距離0(active)=0dp, 距離1=0.5dp, 距離2=1.0dp, 距離3以上=1.5dp (上限)
    // inactiveBlurRadius > 0f のとき固定モード: 指定値を使用
    val resolvedBlurDp = when {
        isActive -> 0f
        !inactiveBlur -> 0f
        inactiveBlurRadius == 0f -> (distFromActive * 0.5f).coerceIn(0f, 1.5f)
        else -> inactiveBlurRadius
    }
    val targetBlurDp by animateFloatAsState(
        targetValue = resolvedBlurDp,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "lyricBlur"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.translationY = translationY
                // 非アクティブ時のスケール（左端/右端基準）
                scaleX = targetScale
                scaleY = targetScale
                transformOrigin = if (isRight)
                    androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                else
                    androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = if (isRight) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = textColor,
            // 常に activeFontWeight 固定 → 高さが変わらずガクつかない
            fontWeight = activeFontWeight,
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp,
            softWrap = true,
            textAlign = if (isRight) androidx.compose.ui.text.style.TextAlign.End
            else androidx.compose.ui.text.style.TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth(lineWidthFraction)
                .padding(vertical = 8.dp)
                .then(
                    if (targetBlurDp > 0f)
                        Modifier.blur(targetBlurDp.dp)
                    else
                        Modifier
                )
        )
    }
}


// ===== カラオケ行アイテム（ワイプアニメーション）=====
// 各文字に startMs/endMs が設定されており、現在位置に応じて
// 左→右へ accentColor でワイプ（塗りつぶし）する。
// Canvas で「ベーステキスト（薄色）」と「ワイプ済み（アクセント色）」を重ね描画。
@Composable
fun KaraokeLineItem(
    karaoke: List<KaraokeChar>,
    currentPositionMs: Long,
    isActive: Boolean,
    isPast: Boolean,
    accentColor: Color,
    inactiveColor: Color = Color.Unspecified,
    fontSize: Int = 34,
    fontWeight: Int = 700,
    lineHeightMultiplier: Float = 1.4f,
    inactiveScale: Float = 0.98f,
    fontFamily: FontFamily? = null,
    translationY: Float = 0f,
    isRight: Boolean = false,
    lineWidthFraction: Float = 0.85f,   // テキスト幅の割合 (isRight行なし→0.95f)
    onClick: () -> Unit
) {
    // ===== 滑らかなワイプのための時間補間 =====
    // currentPositionMs は再生位置のスナップショットであり、
    // 設定(seekBarUpdateInterval)によっては0.3〜1秒に1回など粗い間隔でしか更新されない。
    // そのままワイプ進捗の計算に使うと「パッ、パッ」とカクカク進んで見えてしまうため、
    // 「最後に受け取った値」と「それを受け取った実時刻」を記録し、
    // withFrameNanosで毎フレーム経過時間を足し合わせて滑らかな位置を推定する。
    // シーク等でcurrentPositionMsが大きくジャンプした場合は即座にスナップする。
    var lastKnownPositionMs by remember { mutableStateOf(currentPositionMs) }
    var lastKnownRealtimeMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var interpolatedPositionMs by remember { mutableStateOf(currentPositionMs) }

    LaunchedEffect(currentPositionMs) {
        // 前回の推定値との差が大きい（シーク・曲変更など）場合は即座にスナップ
        val jumpMs = kotlin.math.abs(currentPositionMs - interpolatedPositionMs)
        lastKnownPositionMs = currentPositionMs
        lastKnownRealtimeMs = System.currentTimeMillis()
        if (jumpMs > 400L) {
            interpolatedPositionMs = currentPositionMs
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                val elapsed = System.currentTimeMillis() - lastKnownRealtimeMs
                interpolatedPositionMs = lastKnownPositionMs + elapsed.coerceAtLeast(0L)
            }
        }
    }

    val resolvedInactive = if (inactiveColor == Color.Unspecified)
        MaterialTheme.colorScheme.onSurface.copy(0.35f) else inactiveColor
    val activeFontWeight = FontWeight(fontWeight)

    // スケールアニメーション（LyricLineItemと同じ）
    val targetScale by animateFloatAsState(
        targetValue = if (isActive) 1.0f else inactiveScale,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "karaokeScale"
    )

    // 行全体のカラーアニメーション（非アクティブ↔アクティブ）
    val baseColor by animateColorAsState(
        targetValue = when {
            isPast -> resolvedInactive
            isActive -> resolvedInactive  // ベースは薄色、ワイプでaccentを重ねる
            else -> resolvedInactive
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "karaokeBaseColor"
    )

    // 各文字のワイプ進捗（0.0=未到達、1.0=完全ワイプ済み）
    // interpolatedPositionMs（毎フレーム滑らかに進む推定位置）を使うことで
    // 文字送りのワイプが飛び飛びにならずなめらかに動く
    val wipeProgresses = karaoke.map { k ->
        when {
            interpolatedPositionMs < k.startMs -> 0f
            k.endMs > k.startMs && interpolatedPositionMs < k.endMs ->
                (interpolatedPositionMs - k.startMs).toFloat() / (k.endMs - k.startMs).toFloat()
            else -> if (isPast || interpolatedPositionMs >= k.endMs) 1f else 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.translationY = translationY
                scaleX = targetScale
                scaleY = targetScale
                transformOrigin = if (isRight)
                    androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                else
                    androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = if (isRight) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        // ===== ワイプ描画 =====
        // 1. ベーステキスト（薄色）を描画
        // 2. Canvas で clipRect + drawText によりワイプ済み部分をアクセントカラーで重ね描画
        //
        // ★ LyricLineItem と全く同じレイアウト構造（外側Boxはcontentalignmentのみ、
        //   内側要素にfillMaxWidth(lineWidthFraction)+padding(vertical=8.dp)を直接適用）
        //   に揃えることで、sdlrc通常行とカラオケ行の行間の差異を解消する。
        //   以前は内側にも独立したBox+contentAlignmentがあり、二重のアラインメント計算で
        //   高さの算出結果がわずかにズレていた。
        val textMeasurer = rememberTextMeasurer()
        val fullText = karaoke.joinToString("") { it.char }
        val textStyle = androidx.compose.ui.text.TextStyle(
            fontWeight = activeFontWeight,
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(lineWidthFraction)
                .padding(vertical = 8.dp)
        ) {
            // ベース（薄色）テキスト
            Text(
                text = fullText,
                style = textStyle,
                color = baseColor,
                softWrap = true,
                textAlign = if (isRight) androidx.compose.ui.text.style.TextAlign.End
                else androidx.compose.ui.text.style.TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            // ワイプ済み部分を Canvas で重ねる
            Canvas(modifier = Modifier.matchParentSize()) {
                // 右寄せの場合は全体幅から開始X座標を算出
                val totalLayout = textMeasurer.measure(
                    text = fullText,
                    style = textStyle,
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = size.width.toInt())
                )
                var charX = if (isRight) {
                    size.width - totalLayout.size.width.toFloat()
                } else 0f

                karaoke.forEachIndexed { i, k ->
                    val charLayout = textMeasurer.measure(
                        text = k.char,
                        style = textStyle
                    )
                    val charWidth = charLayout.size.width.toFloat()
                    val wipeWidth = charWidth * wipeProgresses[i]

                    if (wipeWidth > 0f) {
                        drawContext.canvas.save()
                        drawContext.canvas.clipRect(
                            androidx.compose.ui.geometry.Rect(
                                left   = charX,
                                top    = 0f,
                                right  = charX + wipeWidth,
                                bottom = size.height
                            )
                        )
                        // drawText(TextMeasurer, ...) 形式で描画
                        drawText(
                            textMeasurer = textMeasurer,
                            text         = k.char,
                            topLeft      = androidx.compose.ui.geometry.Offset(charX, 0f),
                            style        = textStyle.copy(color = accentColor)
                        )
                        drawContext.canvas.restore()
                    }
                    charX += charWidth
                }
            }
        }
    }
}

// ===== 間奏インジケーター =====
// アニメーションシーケンス:
// 【出現】枠を開く(height 0→54dp) → 各ドットが stagger で小さく→大きくポップイン →
//         パルスループ: 全体100%→115%→100% を繰り返す
// 【消滅】残り800ms以下: 全体を130%に拡大 → 300ms待機 → 0%に縮小（ここがしっかり見えるようにしてから）→ フェードアウト＋枠を閉じる(height→0dp)
// 【isActive=false】拡大→縮小消滅が未実行なら即フェードアウト
//
// ★ 実装メモ: 以前は「常に最大高さ(54dp)の枠を確保した上で中身をscaleYだけ変形する」
//   方式だった。scaleYはあくまで描画の見た目を縮小するだけでレイアウト上の占有スペースは
//   変わらないため、間奏が無いときも常に54dp分の余白がそのまま残ってしまう不具合があった。
//   今回は heightScale の値をそのまま実際の Modifier.height() に反映させ、
//   間奏が無いときは高さ0dp（余白なし）、出現時は0dp→54dpへなめらかにアニメーションしながら
//   実際にスペースが開くようにする。LazyColumn側の再計算は発生するが、
//   heightScaleの変化はtweenで滑らかに補間されるため、周囲の行も一緒になめらかに
//   詰まる/広がる動きになる。
@Composable
fun InterludeIndicator(
    accentColor: Color,
    isActive: Boolean,
    progress: Float,
    remainingMs: Long = Long.MAX_VALUE,
    isRight: Boolean = false,
    onClick: () -> Unit
) {
    val dotCount = 3
    val maxHeightDp = 54f

    val alpha        = remember { Animatable(0f) }
    val pulseScale    = remember { Animatable(1.0f) }
    val dotScales     = remember { List(dotCount) { Animatable(0f) } }
    // heightScale: 0f=高さ0dp（余白なし）、1f=高さmaxHeightDp（枠を全開にした状態）
    val heightScale   = remember { Animatable(0f) }
    // クリック領域を残さないための無効化フラグ（レイアウトからは外さない）
    var isCollapsed by remember { mutableStateOf(true) }

    // remainingMsをSnapshotStateとして持つ（snapshotFlow で監視するため）
    val remainingMsState = rememberUpdatedState(remainingMs)

    val scope = rememberCoroutineScope()

    LaunchedEffect(isActive) {
        if (isActive) {
            // ===== リセット =====
            isCollapsed = false
            alpha.snapTo(0f)
            pulseScale.snapTo(1.0f)
            dotScales.forEach { it.snapTo(0f) }
            heightScale.snapTo(0f)

            // ===== 1. 枠を開く（0dp→maxHeightDpへ実際にレイアウト高さをアニメーション）=====
            heightScale.animateTo(1f, tween(350, easing = FastOutSlowInEasing))

            // ===== 2. ドットを stagger でポップイン =====
            alpha.animateTo(1f, tween(200))
            dotScales.forEachIndexed { i, anim ->
                scope.launch {
                    delay(i * 80L)
                    anim.animateTo(1.3f, tween(220, easing = FastOutSlowInEasing))
                    anim.animateTo(1.0f, tween(160, easing = FastOutSlowInEasing))
                }
            }
            delay(dotCount * 80L + 380L)

            // ===== 3. パルスループ + 消滅監視 =====
            // snapshotFlow で remainingMs を監視しながらループ
            // 800ms 以下になったら消滅シーケンスへ
            val pulseJob = scope.launch {
                while (true) {
                    pulseScale.animateTo(1.15f, tween(1400, easing = FastOutSlowInEasing))
                    delay(600)
                    pulseScale.animateTo(1.0f, tween(1400, easing = FastOutSlowInEasing))
                    delay(400)
                }
            }

            // remainingMs が 800ms 以下になるまで待つ
            snapshotFlow { remainingMsState.value }
                .collect { ms ->
                    if (ms in 1L..800L) {
                        // 消滅シーケンス開始
                        pulseJob.cancel()
                        pulseScale.stop()
                        isCollapsed = true

                        // 130% に拡大(700ms) → 300ms待機 → 0% に縮小(300ms)
                        // ★ 縮小(pulseScale→0)がしっかり見えるよう、
                        //   枠を閉じるアニメ(heightScale)は縮小完了後に開始する（直列化）。
                        //   以前は縮小と同時に枠も閉じていたため、縮小の動きが
                        //   枠が消える勢いに埋もれてほぼ見えなかった。
                        pulseScale.animateTo(1.30f, tween(700, easing = FastOutSlowInEasing))
                        delay(300)
                        pulseScale.animateTo(0f, tween(300, easing = androidx.compose.animation.core.LinearEasing))

                        // 縮小が見えきってから、フェードアウト＋枠を閉じる
                        scope.launch { alpha.animateTo(0f, tween(300)) }
                        heightScale.animateTo(0f, tween(300, easing = FastOutSlowInEasing))

                        // collect を終了
                        throw kotlinx.coroutines.CancellationException("dismiss done")
                    }
                }
        } else {
            // isActive=false: 消滅アニメ未実行なら即フェードアウト
            isCollapsed = true
            if (alpha.value > 0f) {
                pulseScale.stop()
                scope.launch { alpha.animateTo(0f, tween(250)) }
                heightScale.animateTo(0f, tween(350))
            }
        }
    }

    // ★ isCollapsed による早期return は行わず、常にコンポーズし続ける。
    //   （if分岐でコンポーザブル自体をLazyColumnから抜き差しすると、間奏終了の瞬間に
    //   フェードアウト・縮小アニメーションが実行される前に強制終了してしまうため）
    //   ただし高さ自体は heightScale に応じて 0dp〜maxHeightDp まで実際にアニメーションさせる。
    //   → 間奏が無い間は高さ0dpとなり、余白は一切表示されない。
    //   → 間奏が始まると0dpからなめらかに広がり、終わるとなめらかに0dpへ戻る。
    val animatedHeightDp = (maxHeightDp * heightScale.value.coerceIn(0f, 1f)).dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(animatedHeightDp)
            .clickable(enabled = !isCollapsed) { onClick() },
        contentAlignment = if (isRight) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
                this.alpha = alpha.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(dotCount) { i ->
                val dotFraction = (progress * dotCount - i).coerceIn(0f, 1f)
                val dotAlpha    = 0.30f + dotFraction * 0.70f

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = dotScales[i].value
                            scaleY = dotScales[i].value
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                        }
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = dotAlpha))
                )
                if (i < dotCount - 1) Spacer(Modifier.width(6.dp))
            }
        }
    }
}

// ===== 非同期歌詞ビュー =====
@Composable
fun PlainLyricsView(lines: List<LyricLine>, isBlurBg: Boolean = false) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(lines) { _, line ->
            if (line.text.isBlank()) {
                Spacer(Modifier.height(12.dp))
            } else {
                Text(
                    text = line.text,
                    fontSize = 22.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isBlurBg) Color.White
                    else MaterialTheme.colorScheme.onSurface.copy(0.75f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                )
            }
        }
    }
}

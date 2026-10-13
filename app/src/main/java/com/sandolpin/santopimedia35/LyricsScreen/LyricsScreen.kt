package com.sandolpin.santopimedia35.lyricsscreen

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.database.MediaState
import com.sandolpin.santopimedia35.home.explicitMeshColorsOrNull
import com.sandolpin.santopimedia35.home.homescreena.AlbumArtCard
import com.sandolpin.santopimedia35.home.homescreena.AnimatedMeshBackground
import com.sandolpin.santopimedia35.home.homescreena.BackgroundDecorationLayer
import com.sandolpin.santopimedia35.home.homescreena.NormalSeekBar
import com.sandolpin.santopimedia35.home.homescreena.PlayerControlsA
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// 歌詞画面 本体（背景・縦横レイアウト・歌詞取得）

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

    // ★ 背景装飾（BackgroundDecorationLayer）を歌詞のスクロールに連動させるため、
    //   SyncedLyricsView内部で使っていたLazyListStateをここに引き上げて共有する。
    val lyricsListState = rememberLazyListState()

    // ★ 装飾のパララックス量は「歌詞のLazyColumnが実際に消費したスクロールpx」を
    //   NestedScrollConnectionでそのまま積算して求める。
    //   以前はfirstVisibleItemIndex×概算の行高さで近似していたが、sdlrcの行は
    //   改行・間奏・カラオケ行などで高さがバラバラなため、行が切り替わる瞬間に
    //   推定値と実際のスクロール量がズレて、装飾がガクッと飛んだり
    //   ほとんど動いていないように見えたりする不具合があった（実際に発生した不具合）。
    //   NestedScrollConnectionは子(LazyColumn)が実際に消費したpx量をそのまま
    //   教えてくれるため、行の高さに関わらず正確・滑らかに追従できる。
    var lyricsScrollAccumPx by remember { mutableStateOf(0f) }
    val lyricsScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                lyricsScrollAccumPx += consumed.y
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

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
    // ★ 以前はAnimatedMeshBackground呼び出し時にisDarkTheme/darkness/colorPatternを
    //   一切渡しておらず、常にデフォルト値（ライトモード相当の濃さ・normalパターン）
    //   で描画されていた。ダークモード専用スライダーを機能させるため、ここで
    //   HomeScreenA.kt等と同じ方法でダークモード判定を行う。
    val isDarkThemeForBg = when (settings.themeMode) {
        "dark"  -> true
        "light" -> false
        else    -> isSystemInDarkTheme()
    }
    // ★ 白文字固定の背景: ぼかし / 動的アニメーション / グラデーション / 色混ぜ
    //   （歌詞専用背景 or プレーヤー背景連動のどちらでも同じ）。
    //   以前は「動的アニメーション背景が明るいと濃い文字/アクセントカラー」に切り替えていたが、
    //   ホーム側の文字色が黒のとき歌詞がアクセントカラーになる不具合の原因だったため廃止し、
    //   常に白+ぼかし縁取りで読めるようにする。
    val usesAnimatedBg = settings.lyricsBg == "animated" ||
            (settings.lyricsBg == "player" && settings.backgroundStyle == "animated")
    val meshColors = if (usesAnimatedBg &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    ) explicitMeshColorsOrNull(settings, mediaState) else null
    val isBlurBg     = settings.lyricsBg == "blur" ||
            settings.lyricsBg == "animated" ||
            settings.lyricsBg == "gradient" ||
            (settings.lyricsBg == "player" &&
                    (settings.backgroundStyle == "blur" ||
                            settings.backgroundStyle == "animated" ||
                            settings.backgroundStyle == "gradient" ||
                            settings.backgroundStyle == "color_mix"))
    val activeColor  = if (isBlurBg) Color.White else accentColor

    // ===== 横画面判定 =====
    // ★ 横画面のときは「左半分=プレーヤー／右半分=歌詞」の左右分割レイアウト
    //   (LyricsLandscapeContent)に切り替える。縦画面は従来通り
    //   ミニプレーヤー型ヘッダー(LyricsHeader)+下に歌詞、のまま変更しない。
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // past / future ともに alpha=0.5 均一
    val inactiveColor= if (isBlurBg) Color.White.copy(0.5f)
    else MaterialTheme.colorScheme.onSurface.copy(0.35f)
    val futureColor  = if (isBlurBg) Color.White.copy(0.5f)
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

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize().nestedScroll(lyricsScrollConnection)) {
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
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(
                                        accentColor.copy(0.55f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                        )
                    }

                    "animated" -> {
                        // ホーム画面と同じ動的アニメーション背景（AGSL / API33+）。
                        // API33未満はホームと同じ方針でぼかしにフォールバックする。
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            AnimatedMeshBackground(
                                color1 = animatedBgColor,
                                darkness = settings.animatedBgDarkness,
                                darkModeDarkness = settings.animatedBgDarknessDark,
                                colorPattern = settings.animatedColorPattern,
                                isDarkTheme = isDarkThemeForBg,
                                explicitColors = meshColors,
                                modifier = Modifier.fillMaxSize()
                            )
                            // 歌詞画面では動的アニメーションを少し暗くして白文字を読みやすくする
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.22f)))
                        } else if (mediaState.albumArtUri != null) {
                            AsyncImage(
                                model = mediaState.albumArtUri, contentDescription = null,
                                modifier = Modifier.fillMaxSize().blur(60.dp),
                                contentScale = ContentScale.Crop
                            )
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)))
                        } else {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            )
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
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        listOf(
                                            accentColor.copy(0.5f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                            )
                        } else if (settings.backgroundStyle == "color_mix") {
                            // ホームの「色混ぜ」と同じ: アクセントカラー→アルバムアート混色の斜めグラデーション
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.linearGradient(
                                        listOf(accentColor, mediaState.blendedArtColor.copy(alpha = 1f))
                                    )
                                )
                            )
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.25f)))
                        } else if (settings.backgroundStyle == "animated") {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                AnimatedMeshBackground(
                                    color1 = animatedBgColor,
                                    darkness = settings.animatedBgDarkness,
                                    darkModeDarkness = settings.animatedBgDarknessDark,
                                    colorPattern = settings.animatedColorPattern,
                                    isDarkTheme = isDarkThemeForBg,
                                    explicitColors = meshColors,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // 歌詞画面では動的アニメーションを少し暗くして白文字を読みやすくする
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.22f)))
                            } else if (mediaState.albumArtUri != null) {
                                AsyncImage(
                                    model = mediaState.albumArtUri, contentDescription = null,
                                    modifier = Modifier.fillMaxSize().blur(60.dp),
                                    contentScale = ContentScale.Crop
                                )
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)))
                            } else {
                                Box(
                                    Modifier.fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                                )
                            }
                        } else {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            )
                        }
                    }

                    else -> Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    )
                }

                // ===== 背景装飾（ドット/水玉/ランダム図形。どの背景スタイルにも重ねられる独立レイヤー）=====
                // ★ lyricsScrollAccumPx（NestedScrollConnectionで実測した実際のスクロールpx）を
                //   そのまま使う。LazyColumnはスクロール量が増えるほど中身が画面上では
                //   上方向に流れる（次の行が下から上がってくる）が、装飾側の描画は逆に
                //   scrollOffsetPxが増えるほどY座標を増やす（下に動く）実装のため、
                //   符号を反転させて歌詞と同じ方向・その35%の量だけ装飾も動くようにする。
                val decorationScrollOffsetPx = lyricsScrollAccumPx * -0.35f

                if (settings.bgDecorationType != "none") {
                    BackgroundDecorationLayer(
                        type = settings.bgDecorationType,
                        color = if (settings.bgDecorationColor == "white") Color.White else accentColor,
                        opacity = settings.bgDecorationOpacity,
                        sizeScale = settings.bgDecorationSize,
                        scrollOffsetPx = decorationScrollOffsetPx,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // ===== 歌詞コンテンツ本体（縦画面・横画面で共用するため関数値として抽出） =====
                val lyricsContent: @Composable () -> Unit = {
                    // ★ 取得完了などで歌詞の状態が切り替わるたびに、テキストをフェードインさせる。
                    //   （Loading→歌詞表示、NotFound→歌詞表示など。前の表示はフェードアウトする）
                    Crossfade(
                        targetState = lyricsState,
                        animationSpec = tween(500),
                        label = "lyricsFade"
                    ) { currentState ->
                        when (val state = currentState) {
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
                                disableMultiShow = settings.sdlrcDisableMultiShow,
                                disableRight = settings.sdlrcDisableRight,
                                onLineClick = { viewModel.seekTo(it) },
                                viewModel = viewModel,
                                useOffsetAnim = useOffsetAnim,
                                externalListState = lyricsListState,
                                textOutline = isBlurBg
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
                                        lyricsState =
                                            fetchLyricsById(id, mediaState.title, mediaState.artist)
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
                    } // Crossfade
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
                                    top = WindowInsets.statusBars.asPaddingValues()
                                        .calculateTopPadding() + 8.dp,
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
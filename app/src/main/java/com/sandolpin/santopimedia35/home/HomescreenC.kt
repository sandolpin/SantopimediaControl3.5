package com.sandolpin.santopimedia35.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import coil.compose.AsyncImage
import com.sandolpin.santopimedia35.LyricItem
import com.sandolpin.santopimedia35.LyricsState
import com.sandolpin.santopimedia35.MediaPlayerViewModel
import com.sandolpin.santopimedia35.PlainLyricsView
import com.sandolpin.santopimedia35.QueueItemRow
import com.sandolpin.santopimedia35.SyncedLyricsView
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.MediaState
import com.sandolpin.santopimedia35.database.QueueItem
import com.sandolpin.santopimedia35.database.SettingsKeys
import com.sandolpin.santopimedia35.favorite.FavoriteViewModel
import com.sandolpin.santopimedia35.fetchLyricsWithLocal
import com.sandolpin.santopimedia35.remember.HistoryCard
import com.sandolpin.santopimedia35.remember.HistoryViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// =============================================================================
// UI-C: 上下分割プレーヤー画面
// -----------------------------------------------------------------------------
// ・上下どちらかに「プレーヤー」、もう片方に「歌詞 / キュー / りれき」（設定で選択）を表示する
// ・中央の区切り線を上下ドラッグすると 3:7 / 5:5 / 7:3 の3段階にスナップする
// ・区切り線をダブルタップすると上下の内容（プレーヤー⇔もう片方）を入れ替えられる
//
// ★ 設計メモ:
//   分割比率(splitRatio)・入れ替え状態(isSwapped)は rememberSaveable で保持しているため、
//   画面回転や一時的なタブ切り替えでは保持されるが、アプリのプロセス終了・再起動を
//   またいでは保持されない（＝設定として永続化するほどの項目ではない、一時的な
//   画面レイアウト状態と判断したため）。
//
//   「歌詞 / キュー / りれき」は、それぞれの専用フル画面（LyricsScreen / QueueScreen /
//   HistoryScreen）をそのまま小さいボックスに埋め込むことは構造上できない
//   （Scaffold・TopAppBar前提の作りのため）。そのため、各画面の中身で使われている
//   部品（SyncedLyricsView・QueueItemRow・HistoryCard等）を流用した、
//   この画面専用の簡易表示（Compact〜Pane）として組んでいる。
// =============================================================================

private val SPLIT_RATIOS = listOf(0.3f, 0.5f, 0.7f)

@Composable
fun HomeScreenC(
    viewModel: MediaPlayerViewModel,
    historyViewModel: HistoryViewModel,
    settings: AppSettings,
    // ★ ピル型ナビゲーションバーの実際の高さ。MainActivity.ktから渡される。
    //   背景(HomeScreenCBackground)は画面の本当の端まで伸ばしたままにし、
    //   上下のペイン(コントロール等)だけこの高さぶんの余白を追加で持たせる。
    bottomNavBarHeight: androidx.compose.ui.unit.Dp = 0.dp,
    onLongPressPlay: () -> Unit,
    // ★ 区切り線をドラッグしている間だけ true にする。
    //   MainActivity側の「画面全体を下スワイプするとミニプレーヤーが出る」ジェスチャーと
    //   このバーのドラッグ操作が競合してしまう不具合の対策として、ドラッグ中はこのコールバックで
    //   呼び出し元（PlayerScreenWithSwipe）に伝え、全体スワイプ判定を一時的に無効化してもらう。
    onDividerDragActiveChange: (Boolean) -> Unit = {},
    // ★ 区切り線の長押しメニュー「設定画面で細かい設定を行う」から呼ばれる。
    //   MainActivity.kt側でnavController.navigate("settings")に繋がっている。
    onOpenSettings: () -> Unit = {},
) {
    val mediaState by viewModel.mediaState.collectAsState()

    // ===== 区切り線 長押しクイックメニューの表示状態 =====
    var showQuickMenu by remember { mutableStateOf(false) }

    // ===== 分割比率・上下入れ替え状態 =====
    var splitRatio by rememberSaveable { mutableStateOf(0.5f) }
    var isSwapped by rememberSaveable { mutableStateOf(false) }
    // ドラッグ中だけ使う一時的な比率（null=ドラッグしていない）
    var dragRatio by remember { mutableStateOf<Float?>(null) }
    var containerHeightPx by remember { mutableStateOf(0f) }

    val targetRatio = dragRatio ?: splitRatio
    // ドラッグ中は追従（snap）、指を離した後は最寄りの3段階へなめらかに収まる（tween）
    val animatedRatio by animateFloatAsState(
        targetValue = targetRatio,
        animationSpec = if (dragRatio != null) snap() else tween(220, easing = FastOutSlowInEasing),
        label = "homeScreenCSplitRatio"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // ===== 背景（外観設定の背景スタイルに準拠。HomeScreenAと同じロジックを共有）=====
        // 画面全体（余白・区切り線のすき間も含む）の下地として敷く。
        HomeScreenCBackground(mediaState = mediaState, settings = settings)

        // ===== タップ漏れ防止用の透明ブロッカー =====
        // ★ このComposable自体はPlayerScreenWithSwipe（MainActivity.kt）側で
        //   QueueScreenBody（背後のキュー画面）と重なるように配置されている。
        //   HomeScreenC側の「余白」（各ペインカードの外側の隙間・区切り線バーの
        //   ハンドル以外の部分など）にはボタン等の受け皿となるpointerInputが
        //   何も無いため、タップがどの要素にも消費されず、そのままヒットテストの
        //   奥（背後のQueueScreenBody）まで通り抜けてしまっていた（実際に発生した不具合）。
        //   背景(HomeScreenCBackground)と本体(Column)の間にfillMaxSizeの
        //   タップ吸収レイヤーを挟むことで、Column側の各ボタン・区切り線・
        //   スクロール領域など「本来反応すべき要素」が処理しなかったタップだけを
        //   ここで拾って握りつぶし、それより後ろには一切伝播させないようにする。
        //   （Column側の要素は自分自身のpointerInputで先に消費するため、
        //   このブロッカーが誤ってボタン操作等を妨げることはない）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    // ★ MainActivity.kt の Scaffold で contentWindowInsets = WindowInsets(0) を
                    //   指定し、Scaffold自体によるステータスバー分の余白自動確保をやめたため、
                    //   この画面が自分でステータスバー分の高さを読んで余白を確保する。
                    //   （Scaffold側で確保させたままだと背景がステータスバーの裏まで届かず、
                    //   Scaffoldの地の色(白)がそのまま透けて見えてしまっていた）
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                    // ★ 以前はWindowInsets.navigationBars（システムのナビゲーションバー分）のみを
                    //   見ていたが、これだとMainActivity.kt側のピル型ナビゲーションバー自体の
                    //   高さは考慮されておらず、上下ペインの内容がピルに隠れる可能性があった。
                    //   bottomNavBarHeightはピル+システムナビゲーションバー分を含めて実測した
                    //   値のため、これを使うことでピルに隠れないようにする。
                    //   （背景はHomeScreenCBackgroundで既にBox全体をfillMaxSize()で
                    //   覆っているのでピルの裏まで届く）
                    bottom = bottomNavBarHeight + 10.dp
                )
                .padding(horizontal = 14.dp)
                .onGloballyPositioned { containerHeightPx = it.size.height.toFloat() }
        ) {
            // ===== 上側ボックス =====
            Box(modifier = Modifier.fillMaxWidth().weight(animatedRatio.coerceIn(0.05f, 0.95f))) {
                PaneCard(alpha = settings.homeScreenCCardAlpha) {
                    PaneContent(
                        isPlayer = !isSwapped,
                        secondaryContent = settings.homeScreenCSecondaryContent,
                        viewModel = viewModel,
                        historyViewModel = historyViewModel,
                        settings = settings,
                        mediaState = mediaState,
                        onLongPressPlay = onLongPressPlay,
                        // ★ このペインに割り当てられている実際の比率(splitRatio、ドラッグ中の
                        //   animatedRatioではなくスナップ後の確定値)から、狭い(3:7の"3"側)
                        //   コンパクトレイアウトにすべきかを判定する。
                        //   以前は BoxWithConstraints の実測高さ(240dp)を基準にしていたが、
                        //   画面の総高さが大きい端末では3:7の"3"側でも240dpを超えてしまい、
                        //   意図せず広いレイアウト(アート大きめ縦積み)が選ばれ、
                        //   かつそのレイアウトが実際の狭い高さに収まらずボタンが
                        //   押しつぶされて見える不具合の原因になっていた。
                        isCompactLayout = splitRatio <= 0.35f
                    )
                }
            }

            // ===== 区切り線 =====
            SplitDivider(
                onDrag = { deltaPx ->
                    if (containerHeightPx <= 0f) return@SplitDivider
                    val base = dragRatio ?: splitRatio
                    dragRatio = (base + deltaPx / containerHeightPx).coerceIn(0.2f, 0.8f)
                },
                onDragEnd = {
                    val current = dragRatio ?: splitRatio
                    splitRatio = SPLIT_RATIOS.minByOrNull { kotlin.math.abs(it - current) } ?: 0.5f
                    dragRatio = null
                },
                onDoubleTap = { isSwapped = !isSwapped },
                onLongPress = { showQuickMenu = true },
                onDragActiveChange = onDividerDragActiveChange
            )

            // ===== 下側ボックス =====
            Box(modifier = Modifier.fillMaxWidth().weight((1f - animatedRatio).coerceIn(0.05f, 0.95f))) {
                PaneCard(alpha = settings.homeScreenCCardAlpha) {
                    PaneContent(
                        isPlayer = isSwapped,
                        secondaryContent = settings.homeScreenCSecondaryContent,
                        viewModel = viewModel,
                        historyViewModel = historyViewModel,
                        settings = settings,
                        mediaState = mediaState,
                        onLongPressPlay = onLongPressPlay,
                        isCompactLayout = (1f - splitRatio) <= 0.35f
                    )
                }
            }
        }

        // ===== 区切り線 長押しクイックメニュー =====
        if (showQuickMenu) {
            HomeScreenCQuickMenu(
                viewModel = viewModel,
                settings = settings,
                splitRatio = splitRatio,
                onSelectRatio = { splitRatio = it },
                onSwap = { isSwapped = !isSwapped },
                onOpenSettings = onOpenSettings,
                onDismiss = { showQuickMenu = false }
            )
        }
    }
}

// ===== ペインの見た目: 設定した背景の上に白(透明度は設定で調整可能)の半透明カードを重ねる =====
@Composable
private fun PaneCard(alpha: Float = 0.35f, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = alpha.coerceIn(0.05f, 0.95f)))
    ) {
        content()
    }
}

// ===== HomeScreenAと同じ考え方の背景（外観設定の「背景スタイル」に準拠）=====
@Composable
private fun HomeScreenCBackground(mediaState: MediaState, settings: AppSettings) {
    val isBlur = settings.backgroundStyle == "blur"
    val isAnimated = settings.backgroundStyle == "animated"
    val useAgsl = isAnimated && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    val useAnimatedFallback = isAnimated && !useAgsl // API<33 → ぼかしにフォールバック

    val isDark = when (settings.themeMode) {
        "dark"  -> true
        "light" -> false
        else    -> isSystemInDarkTheme()
    }

    val rawAccentColor = when {
        settings.extractColorFromArt -> mediaState.dominantColor.copy(alpha = 1f)
        else -> MaterialTheme.colorScheme.primary
    }
    val animatedBgColor = when (settings.animatedColorSource) {
        "album_art" -> mediaState.blendedArtColor.copy(alpha = 1f)
        else -> rawAccentColor
    }

    if ((isBlur || useAnimatedFallback) && mediaState.albumArtUri != null) {
        AsyncImage(
            model = mediaState.albumArtUri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(60.dp),
            contentScale = ContentScale.Crop
        )
        when (settings.blurStyle) {
            "dark_mode" -> Box(
                modifier = Modifier.fillMaxSize().background(
                    if (isDark) Color.Black.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.60f)
                )
            )
            else -> { /* normal: オーバーレイなし */ }
        }
    }

    if (useAgsl) {
        AnimatedMeshBackground(
            color1 = animatedBgColor,
            darkness = settings.animatedBgDarkness,
            colorPattern = settings.animatedColorPattern,
            isDarkTheme = isDark,
            modifier = Modifier.fillMaxSize()
        )
    }

    when (settings.backgroundStyle) {
        "gradient" -> Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(rawAccentColor.copy(alpha = 0.5f), MaterialTheme.colorScheme.background)
                )
            )
        )
        "blur" -> { /* 上で描画済み */ }
        "animated" -> { /* 上で描画済み（AGSLまたはフォールバック） */ }
        "color_mix" -> {
            // HomeScreenA.ktと同じ考え方: dominantColorとblendedArtColorを
            // アニメーションなしの静的グラデーションで混ぜるだけ
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(rawAccentColor, mediaState.blendedArtColor.copy(alpha = 1f))
                    )
                )
            )
        }
        else -> Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
}

// ===== ボックス1つ分の中身を、プレーヤーかもう片方の内容かで出し分ける =====
@Composable
private fun PaneContent(
    isPlayer: Boolean,
    secondaryContent: String,
    viewModel: MediaPlayerViewModel,
    historyViewModel: HistoryViewModel,
    settings: AppSettings,
    mediaState: MediaState,
    onLongPressPlay: () -> Unit,
    isCompactLayout: Boolean = false,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isPlayer) {
            CompactPlayerPane(
                viewModel = viewModel,
                mediaState = mediaState,
                settings = settings,
                onLongPressPlay = onLongPressPlay,
                forceCompactLayout = isCompactLayout
            )
        } else {
            when (secondaryContent) {
                "queue"   -> CompactQueuePane(viewModel = viewModel, mediaState = mediaState)
                "history" -> CompactHistoryPane(historyViewModel = historyViewModel, settings = settings)
                else      -> CompactLyricsPane(viewModel = viewModel, mediaState = mediaState, settings = settings)
            }
        }
    }
}

// =============================================================================
// 区切り線: ドラッグで比率変更 + ダブルタップで入れ替え
// =============================================================================
@Composable
private fun SplitDivider(
    onDrag: (deltaPx: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit = {},
    onDragActiveChange: (Boolean) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            // ★ このバーに触れている間だけ true を通知する（他のジェスチャー検出とは独立した
            //   観測専用のpointerInputブロック。consumeは行わずタッチの有無だけを監視する）。
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onDragActiveChange(true)
                    waitForUpOrCancellation()
                    onDragActiveChange(false)
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    // ★ 長押しで区切り線のクイックメニュー（レイアウト切替等）を開く。
                    //   ドラッグ検出は別のpointerInputブロックで並行して監視しているが、
                    //   detectVerticalDragGestures側は「一定距離動いたら」ドラッグとして
                    //   consumeするため、指を動かさずに長押しした場合はここのonLongPressが
                    //   問題なく発火する。
                    onLongPress = { onLongPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // つまみ（ドラッグ・ダブルタップ可能であることを示す視覚的ハンドル）
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.9f))
        )
    }
}

// =============================================================================
// コンパクトプレーヤー（アート小・タイトル・シークバー・コントロール）
// =============================================================================
// ★ 分割比率（3:7 / 5:5 / 7:3）によってこのペインの実際の高さが変わるため、
//   2種類のレイアウトを出し分ける。
//   ・広い（5:5・7:3）→ アートを上部に大きく表示する縦積みレイアウト
//   ・狭い（3:7）    → アート+テキストを横並びにしつつ、アートを高さいっぱいまで
//                       大きくし、アートと再生バーの間の余白を詰めるレイアウト
//
//   ★ 以前は BoxWithConstraints で実測した高さ(240dp)だけを基準に判定していたが、
//     画面の総高さが大きい端末では「3:7の"3"側」でもこの高さを超えてしまうことがあり、
//     本来コンパクトレイアウトにすべき狭いペインで誤って広いレイアウトが選ばれ、
//     しかもそのレイアウトが実際には収まりきらずコントロールボタンが押しつぶされて
//     表示される不具合の原因になっていた（実際に発生した不具合）。
//     呼び出し元(HomeScreenC)が確定した分割比率(splitRatio)から「3:7の狭い側」
//     かどうかを直接教えてくれる forceCompactLayout を優先して使うことで、
//     画面サイズに関わらず常に意図通りのレイアウトになるようにする。
private val EXPANDED_LAYOUT_HEIGHT_THRESHOLD = 240.dp

@Composable
private fun CompactPlayerPane(
    viewModel: MediaPlayerViewModel,
    mediaState: MediaState,
    settings: AppSettings,
    onLongPressPlay: () -> Unit,
    forceCompactLayout: Boolean = false,
) {
    val accentColor = if (settings.extractColorFromArt) mediaState.dominantColor.copy(alpha = 1f)
    else MaterialTheme.colorScheme.primary

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // forceCompactLayout=true（3:7の狭い側）なら常にコンパクトレイアウト。
        // それ以外（5:5・7:3）は実測した高さで大きさを調整する。
        val useExpanded = !forceCompactLayout && maxHeight >= EXPANDED_LAYOUT_HEIGHT_THRESHOLD
        if (useExpanded) {
            ExpandedPlayerContent(
                viewModel = viewModel,
                mediaState = mediaState,
                accentColor = accentColor,
                onLongPressPlay = onLongPressPlay,
                boxMaxWidth = maxWidth,
                boxMaxHeight = maxHeight
            )
        } else {
            CompactPlayerContent(
                viewModel = viewModel,
                mediaState = mediaState,
                accentColor = accentColor,
                settings = settings,
                onLongPressPlay = onLongPressPlay,
                boxMaxHeight = maxHeight
            )
        }
    }
}

// ===== 広いとき（5:5・7:3）: アートを上部に大きく表示する縦積みレイアウト =====
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandedPlayerContent(
    viewModel: MediaPlayerViewModel,
    mediaState: MediaState,
    accentColor: Color,
    onLongPressPlay: () -> Unit,
    boxMaxWidth: androidx.compose.ui.unit.Dp,
    boxMaxHeight: androidx.compose.ui.unit.Dp,
) {
    val horizontalPadding = 18.dp
    val verticalPadding = 14.dp
    // タイトル/アーティスト/アルバム＋シークバー＋コントロール行でおよそ168dp使う想定。
    // 残りをアートの一辺に充てる（幅にも収まるようwidth側ともminを取る）
    val reservedForBelowArt = 168.dp
    val artSize = minOf(
        boxMaxWidth - horizontalPadding * 2,
        (boxMaxHeight - verticalPadding * 2 - reservedForBelowArt).coerceAtLeast(120.dp)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        // ===== アート（上部・大きく表示・中央寄せ）=====
        // artSizeは高さ側の制約でボックス幅より小さくなることがあるため、
        // fillMaxWidth()のBoxで包んでcontentAlignment=Centerにし、
        // Column既定のStart寄せで左に寄って見えるのを防ぐ。
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AlbumArtCard(
                uri = mediaState.albumArtUri,
                bitmap = mediaState.albumArtBitmap,
                shadowColor = accentColor,
                modifier = Modifier.size(artSize)
            )
        }

        Spacer(Modifier.height(12.dp))

        // ===== タイトル/アーティスト/アルバム =====
        Text(
            mediaState.title.ifEmpty { "タイトル不明" },
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            mediaState.artist.ifEmpty { "アーティスト不明" },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (mediaState.album.isNotEmpty()) {
            Text(
                mediaState.album,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(14.dp))

        // ===== シークバー =====
        NormalSeekBar(
            progress = mediaState.progress,
            onProgressChange = { viewModel.seekTo(it) },
            thickness = 4.dp,
            color = accentColor,
            durationMs = mediaState.durationMs
        )

        Spacer(Modifier.height(4.dp))

        // ===== 時間 + コントロール =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                mediaState.currentPositionStr, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.skipToPrevious() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "前の曲", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { viewModel.seekRelative(-10000L) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Replay10, "10秒戻し", modifier = Modifier.size(18.dp))
                }
                // 再生/一時停止（長押しでアクションメニュー）
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .combinedClickable(
                            onClick = { viewModel.togglePlayPause() },
                            onLongClick = onLongPressPlay
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (mediaState.isPlaying) "一時停止" else "再生",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = { viewModel.seekRelative(10000L) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Forward10, "10秒送り", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { viewModel.skipToNext() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.SkipNext, "次の曲", modifier = Modifier.size(20.dp))
                }
            }

            Text(
                "-${mediaState.remainingTimeStr}", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )
        }
    }
}

// ===== 狭いとき（3:7）: アート+テキストを横並びのまま、アートを目一杯大きく =====
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactPlayerContent(
    viewModel: MediaPlayerViewModel,
    mediaState: MediaState,
    accentColor: Color,
    settings: AppSettings,
    onLongPressPlay: () -> Unit,
    boxMaxHeight: androidx.compose.ui.unit.Dp,
) {
    // 高さに応じてアートを可変にする（以前は固定64dpで、ボックスが小さいほど
    // 余白ばかり目立って小さく見えていたため、利用可能な高さの割合で決める）
    val artSize = (boxMaxHeight * 0.55f).coerceIn(80.dp, 132.dp)
    val artSizeVal = artSize.value
    val titleFontSize = (18f + (artSizeVal - 80f) * 0.08f).coerceIn(18f, 23f).sp
    val albumFontSize = (13f + (artSizeVal - 80f) * 0.025f).coerceIn(13f, 15f).sp
    // ★ アーティスト名はアルバム名と同じ大きさに揃える（以前は別の計算式で
    //   アーティストの方が一回り大きく、サイズがバラついて見えていた）。
    val artistFontSize = albumFontSize

    // ★ カード背景(PaneCard)は白の半透明で、透明度(homeScreenCCardAlpha)が高い
    //   ほどカードは白でほぼ塗りつぶされ、低いほど下の背景(ぼかしアート等、
    //   暗い場合が多い)が透けて見える。カードがほぼ不透明(白寄り)なら黒文字、
    //   カードが透明寄り(背景が透ける)なら白文字にすることで、
    //   カードの透明度設定に応じて自動的に読める色になるようにする。
    val cardTextColor = if (settings.homeScreenCCardAlpha >= 0.5f) Color.Black else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 12.dp)
    ) {
        // ===== アート + タイトル/アーティスト/アルバム =====
        Row(verticalAlignment = Alignment.Top) {
            AlbumArtCard(
                uri = mediaState.albumArtUri,
                bitmap = mediaState.albumArtBitmap,
                shadowColor = accentColor,
                modifier = Modifier.size(artSize)
            )
            Spacer(Modifier.width(12.dp))
            // ★ テキスト行同士の間隔が詰まって見えていたため、
            //   spacedByで明示的に上下の余白を確保する。
            //   さらに上部に少し余白(8dp)を足し、アート上端よりテキストの
            //   開始位置を少し下にずらす（アートと横並びにしたときの視覚的な
            //   バランスを取るため）。
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    mediaState.title.ifEmpty { "タイトル不明" },
                    fontSize = titleFontSize, fontWeight = FontWeight.Bold,
                    color = cardTextColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    mediaState.artist.ifEmpty { "アーティスト不明" },
                    fontSize = artistFontSize,
                    color = cardTextColor.copy(alpha = 0.75f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (mediaState.album.isNotEmpty()) {
                    Text(
                        mediaState.album,
                        fontSize = albumFontSize,
                        color = cardTextColor.copy(alpha = 0.6f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ★ 以前はSpacer(Modifier.weight(1f))でシークバーを常に下端に押し下げていたため、
        //   ボックスが小さい3:7のときにアート/テキストと再生バーの間に大きな空白ができていた。
        //   固定の小さな余白に変え、内容をそのまま詰めて表示する。
        Spacer(Modifier.height(10.dp))

        // ===== シークバー =====
        NormalSeekBar(
            progress = mediaState.progress,
            onProgressChange = { viewModel.seekTo(it) },
            thickness = 4.dp,
            color = accentColor,
            durationMs = mediaState.durationMs
        )

        Spacer(Modifier.height(4.dp))

        // ===== 時間 + コントロール =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                mediaState.currentPositionStr, fontSize = 11.sp,
                color = cardTextColor.copy(alpha = 0.65f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.skipToPrevious() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "前の曲", tint = cardTextColor, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { viewModel.seekRelative(-10000L) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Replay10, "10秒戻し", tint = cardTextColor, modifier = Modifier.size(18.dp))
                }
                // 再生/一時停止（長押しでアクションメニュー）
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .combinedClickable(
                            onClick = { viewModel.togglePlayPause() },
                            onLongClick = onLongPressPlay
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (mediaState.isPlaying) "一時停止" else "再生",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = { viewModel.seekRelative(10000L) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Forward10, "10秒送り", tint = cardTextColor, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { viewModel.skipToNext() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.SkipNext, "次の曲", tint = cardTextColor, modifier = Modifier.size(20.dp))
                }
            }

            Text(
                "-${mediaState.remainingTimeStr}", fontSize = 11.sp,
                color = cardTextColor.copy(alpha = 0.65f)
            )
        }
    }
}

// =============================================================================
// コンパクト歌詞ペイン（LyricsScreenの取得ロジック・表示部品を流用した簡易版）
// =============================================================================
@Composable
private fun CompactLyricsPane(
    viewModel: MediaPlayerViewModel,
    mediaState: MediaState,
    settings: AppSettings,
) {
    var lyricsState by remember { mutableStateOf<LyricsState>(LyricsState.Idle) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var fetchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(mediaState.title, mediaState.artist) {
        if (mediaState.title.isEmpty()) {
            lyricsState = LyricsState.Idle
            return@LaunchedEffect
        }
        fetchJob?.cancel()
        lyricsState = LyricsState.Loading
        fetchJob = coroutineScope.launch {
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

    // ★ HomeScreenCの背景が「ぼかし」または「動的アニメーション」のときは、
    //   LyricsScreen.kt(通常の歌詞画面)と同じ理由で背景が暗くなるため、
    //   アクセントカラーのままだと視認性が落ちる。アクティブ行の色を白に固定する。
    val isDarkBg = settings.backgroundStyle == "blur" || settings.backgroundStyle == "animated"
    val accentColor = when {
        isDarkBg -> Color.White
        settings.extractColorFromArt -> mediaState.dominantColor.copy(alpha = 1f)
        else -> MaterialTheme.colorScheme.primary
    }
    val inactiveColor = if (isDarkBg) Color.White.copy(alpha = 0.35f)
    else MaterialTheme.colorScheme.onSurface.copy(0.35f)

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = lyricsState) {
            is LyricsState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                }
            }
            is LyricsState.Synced -> {
                // ★ フル画面版のフォント設定(20〜48sp)はこの小さなボックスには大きすぎるため、
                //   サイズ・行間・余白はここ専用のコンパクトな固定値を使う。
                //   色・スクロール挙動・アニメーション種別など「見た目の質感」に関わる設定は
                //   本体の設定をそのまま引き継ぐ。
                SyncedLyricsView(
                    items = state.items,
                    currentPositionMs = mediaState.currentPositionMs,
                    accentColor = accentColor,
                    inactiveColor = inactiveColor,
                    futureColor = inactiveColor,
                    fontSize = 24,
                    fontWeight = settings.lyricsFontWeight,
                    lineHeightMultiplier = 1.3f,
                    lineSpacingDp = 6f,
                    inactiveLight = settings.lyricsInactiveLight,
                    interludeThresholdMs = settings.lyricsInterludeThreshold * 1000L,
                    multiLineThresholdMs = (settings.lyricsMultiLineThreshold * 1000f).toLong(),
                    scrollSpeedMs = settings.lyricsScrollSpeedMs,
                    scrollEasing = settings.lyricsScrollEasing,
                    offsetSync = settings.lyricsOffsetSync,
                    offsetDelayMs = settings.lyricsOffsetDelayMs,
                    offsetStaggerMs = settings.lyricsOffsetStaggerMs,
                    inactiveScale = settings.lyricsInactiveScale,
                    inactiveBlur = false, // 小さい文字にぼかしは可読性を落とすため無効化
                    inactiveBlurRadius = settings.lyricsInactiveBlurRadius,
                    scrollAnimType = settings.lyricsScrollAnimType,
                    overshootDistance = settings.lyricsOvershootDistance,
                    fontPath = settings.lyricsFontPath,
                    forceInactiveOnEnd = settings.lyricsForceInactiveOnEnd,
                    disableMultiShow = settings.sdlrcDisableMultiShow,
                    disableRight = settings.sdlrcDisableRight,
                    onLineClick = { viewModel.seekTo(it) },
                    viewModel = viewModel,
                    useOffsetAnim = true
                )
            }
            is LyricsState.Plain -> PlainLyricsView(lines = state.lines, isBlurBg = false)
            is LyricsState.NotFound, is LyricsState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "歌詞が見つかりません",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                    )
                }
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "再生すると歌詞を表示します",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                    )
                }
            }
        }
    }
}

// =============================================================================
// コンパクトキューペイン（QueueScreenのQueueItemRowを流用）
// =============================================================================
@Composable
private fun CompactQueuePane(
    viewModel: MediaPlayerViewModel,
    mediaState: MediaState,
) {
    val queueItems by viewModel.queueItems.collectAsState()
    val context = LocalContext.current

    // albumArtUri が無く albumArtBitmap のみのアプリ対応（QueueScreenと同じ考え方）
    val fallbackArtUri = remember(mediaState.title, mediaState.artist, mediaState.packageName) {
        mediaState.albumArtUri ?: mediaState.albumArtBitmap?.let { bmp ->
            val key = "${mediaState.title}|${mediaState.artist}|${mediaState.packageName}"
            com.sandolpin.santopimedia35.AlbumArtCache.saveAndGetUri(context, bmp, key)
        }
    }
    val displayItems = if (queueItems.isNotEmpty()) {
        queueItems
    } else if (mediaState.title.isNotEmpty()) {
        listOf(QueueItem(id = 0L, title = mediaState.title, artist = mediaState.artist, albumArtUri = fallbackArtUri))
    } else {
        emptyList()
    }

    if (displayItems.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "キュー情報がありません",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(displayItems) { index, item ->
                QueueItemRow(
                    index = index + 1,
                    item = item,
                    isBlurBg = false,
                    onClick = { viewModel.skipToQueueItem(item.id) }
                )
            }
        }
    }
}

// =============================================================================
// コンパクトりれきペイン（HistoryCardを流用。フィルターなどは省略した簡易表示）
// =============================================================================
@Composable
private fun CompactHistoryPane(
    historyViewModel: HistoryViewModel,
    settings: AppSettings,
) {
    val context = LocalContext.current
    val favoriteViewModel: FavoriteViewModel = composeViewModel(
        factory = FavoriteViewModel.Factory(context)
    )
    val historyList by historyViewModel.historyList.collectAsState()

    if (historyList.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "まだ履歴がありません",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(historyList, key = { it.id }) { entity ->
                HistoryCard(
                    entity = entity,
                    favoriteViewModel = favoriteViewModel,
                    cardBackgroundStyle = settings.historyCardBackgroundStyle,
                    showDate = settings.historyShowDate,
                    timeRangeMode = settings.historyTimeRangeMode,
                    artBorderEnabled = settings.historyArtBorderEnabled,
                    showPlayTime = settings.historyShowPlayTime,
                    showProgressBar = settings.historyShowProgressBar,
                    artSize = settings.historyArtSize
                )
            }
        }
    }
}

// =============================================================================
// 区切り線 長押しクイックメニュー
// -----------------------------------------------------------------------------
// 区切り線を長押しすると開く設定パネル。ここで変更できる項目はすべて
// 既存の設定（splitRatio・isSwapped・各種AppSettings項目）にそのまま反映される
// ため、設定画面を開かなくてもレイアウトをその場で素早く調整できる。
// =============================================================================
@Composable
private fun HomeScreenCQuickMenu(
    viewModel: MediaPlayerViewModel,
    settings: AppSettings,
    splitRatio: Float,
    onSelectRatio: (Float) -> Unit,
    onSwap: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            // ★ ダークテーマ時にMaterialTheme.colorScheme.surfaceへ委ねると、
            //   端末や設定によって黒系の背景になってしまい、当初のデザイン
            //   （常に白いカード）と異なる見た目になっていた。
            //   このクイックメニューはテーマに関わらず常に白背景で固定する。
            //   合わせてcontentColorも明示的に黒系へ固定し、ダークテーマ時に
            //   文字色が白のまま残って白背景に埋もれる（見えなくなる）事故を防ぐ。
            color = Color.White,
            contentColor = Color(0xFF1A1A1A),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {

                Text(
                    "レイアウト",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                // ===== 分割比率の3択（画像のサムネイル相当） =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SPLIT_RATIOS.forEach { ratio ->
                        LayoutRatioOption(
                            ratio = ratio,
                            selected = kotlin.math.abs(splitRatio - ratio) < 0.01f,
                            onClick = { onSelectRatio(ratio) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ===== 項目をいれかえる（isSwapped トグル。区切り線ダブルタップと同じ動作） =====
                OutlinedButton(
                    onClick = onSwap,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.SwapVert, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("項目をいれかえる", fontSize = 14.sp)
                }

                Spacer(Modifier.height(20.dp))

                // ===== 表示項目（homeScreenCSecondaryContent） =====
                QuickMenuSectionLabel("表示項目")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("lyrics" to "歌詞", "history" to "りれき", "queue" to "キュー").forEach { (v, label) ->
                        val selected = settings.homeScreenCSecondaryContent == v
                        FilterChip(
                            selected = selected,
                            onClick = {
                                viewModel.updateSetting(SettingsKeys.HOME_SCREEN_C_SECONDARY_CONTENT, v)
                            },
                            label = { Text(label, fontSize = 13.sp) },
                            trailingIcon = if (selected) {
                                { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ===== カードの透明度（homeScreenCCardAlpha） =====
                QuickMenuSectionLabel("カードの透明度")
                Slider(
                    value = settings.homeScreenCCardAlpha,
                    onValueChange = {
                        viewModel.updateSetting(SettingsKeys.HOME_SCREEN_C_CARD_ALPHA, it)
                    },
                    valueRange = 0.05f..0.95f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // ===== ホームスタイルのきりかえ（playerUiStyle） =====
                // ★ ここでUI-A/Bに切り替えると、このHomeScreenC自体が表示されなくなる
                //   （MainActivity.kt側のwhen分岐でHomeScreenA/Bに切り替わる）ため、
                //   切り替えと同時にこのダイアログも閉じる。
                QuickMenuSectionLabel("ホームスタイルのきりかえ")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("A", "B", "C").forEach { style ->
                        val selected = settings.playerUiStyle == style
                        FilterChip(
                            selected = selected,
                            onClick = {
                                viewModel.updateSetting(SettingsKeys.PLAYER_UI_STYLE, style)
                                if (style != "C") onDismiss()
                            },
                            label = { Text("UI-$style", fontSize = 13.sp) },
                            trailingIcon = if (selected) {
                                { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ===== 設定画面で細かい設定を行う =====
                TextButton(
                    onClick = {
                        onOpenSettings()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("設定画面で細かい設定を行う", fontSize = 14.sp)
                }
            }
        }
    }
}

// ===== クイックメニュー内のセクション見出し =====
@Composable
private fun QuickMenuSectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        // ★ このダイアログは背景を常に白固定にしたため、テーマ依存のonSurfaceだと
        //   ダークテーマ時に薄い色（白寄り）になり白背景に埋もれてしまう。
        //   固定の黒系グレーにして常に読めるようにする。
        color = Color(0xFF1A1A1A).copy(alpha = 0.6f)
    )
}

// ===== 分割比率オプション（上下2つの箱のミニチュア + 選択チェック） =====
@Composable
private fun LayoutRatioOption(
    ratio: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(5.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(ratio)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f - ratio)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
package com.sandolpin.santopimedia35.home

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sandolpin.santopimedia35.MediaPlayerViewModel
import com.sandolpin.santopimedia35.QueueRepeatMode
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.MediaState
import com.sandolpin.santopimedia35.shareScreenshot

// =============================================================================
// UI-D: 「アルバムアートがグラデーションで背景に溶け込む」高級感のあるPlayerScreen
// -----------------------------------------------------------------------------
// ■ UI-Dの個性
//   ・アルバムアートは画面上端まで(ステータスバーの裏まで)全幅で表示し、
//     下に向かって「透明に」フェードアウトさせる（マスク処理）。
//     → 背後の背景(ぼかし等)にアートが自然に溶け込む
//   ・曲名は左寄せ、右側に丸いお気に入りボタンと「…」(アクションメニュー)ボタン
//   ・操作系は画面下に固定して配置（アートは背景の一部のように敷かれる）
//
// ■ HomeScreenAと共通の機能
//   背景5種(default/gradient/blur/color_mix/animated)・背景装飾・色判定・
//   ピル型ナビバー余白(縦・横)・横画面レイアウト・曲名スライド・波形・
//   波状/通常シークバー・空間オーディオ・ショートカット・音量・出力先・
//   お気に入り/シェア/履歴/歌詞・sharedElementTransition
//
// ■ 依存（同じパッケージ内の別ファイル）
//   HomescreenA.kt : TrackInfo / WaveSeekBar / WaveformAnimation / SpatialAudioBadge /
//                    BackgroundDecorationLayer / SharedLyricsKeys / sharedElementIfAvailable
//   HomescreenB.kt : HomeScreenBBackground / SeekBarB / VolumeBarB / HomeShortcutButtonB /
//                    PlayerControlsB   ※Bの該当関数を private → internal に変更済み
//   Audiooutputhelper.kt : AudioOutputIndicator
// =============================================================================

private data class TrackInfoSnapshotD(val title: String, val artist: String, val album: String)

private class DStyle(
    val accentColor: Color,
    val iconColor: Color,
    val textColor: Color,
    val textColorSub: Color,
    val isBlur: Boolean,
)

private class DActions(
    val onLongPressPlay: () -> Unit,
    val onShowLyrics: () -> Unit,
    val onShowHistory: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onShare: () -> Unit,
)

// アートのフェード（下方向 or 右方向に向かって透明になる）
// ★ 仕組み: 一度オフスクリーンに描いてから、グラデーションを BlendMode.DstIn で重ねると
//   「グラデーションの不透明度」ぶんだけ元の絵が残る＝絵そのものが透明に抜ける。
//   背景を黒や白で塗り潰す方式と違い、どんな背景スタイルの上でも自然に溶け込む。
private fun Modifier.artFadeMask(horizontal: Boolean): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val stops = arrayOf(
            0.00f to Color.Black,
            0.55f to Color.Black,
            0.70f to Color.Black.copy(alpha = 0.80f),
            0.83f to Color.Black.copy(alpha = 0.40f),
            0.93f to Color.Black.copy(alpha = 0.12f),
            1.00f to Color.Transparent
        )
        drawRect(
            brush = if (horizontal) Brush.horizontalGradient(colorStops = stops)
            else Brush.verticalGradient(colorStops = stops),
            blendMode = BlendMode.DstIn
        )
    }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreenD(
    viewModel: MediaPlayerViewModel,
    settings: AppSettings,
    bottomNavBarHeight: Dp = 0.dp,
    endNavBarWidth: Dp = 0.dp,
    onLongPressPlay: () -> Unit,
    onShowLyrics: () -> Unit = {},
    onShowHistory: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    homeAnimatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val mediaState by viewModel.mediaState.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val lastSkipDirection by viewModel.lastSkipDirection.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val context = LocalContext.current

    // ===== お気に入り（A/Bと同じFavoriteViewModelを共有）=====
    val favoriteViewModel: com.sandolpin.santopimedia35.favorite.FavoriteViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = com.sandolpin.santopimedia35.favorite.FavoriteViewModel.Factory(context)
        )
    val isFavorite = favoriteViewModel.isFavorite(
        mediaState.title, mediaState.artist, mediaState.packageName
    )
    val resolvedFavoriteArtUri = remember(mediaState.title, mediaState.artist, mediaState.packageName) {
        mediaState.albumArtUri ?: mediaState.albumArtBitmap?.let { bmp ->
            val key = "${mediaState.title}|${mediaState.artist}|${mediaState.packageName}"
            com.sandolpin.santopimedia35.AlbumArtCache.saveAndGetUri(context, bmp, key)
        }
    }

    // ===== 背景スタイル・色判定（HomeScreenAと同じ）=====
    val isBlur = settings.backgroundStyle == "blur"
    val isAnimated = settings.backgroundStyle == "animated"
    val isColorMix = settings.backgroundStyle == "color_mix"
    val isDark = when (settings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val rawAccentColor = when {
        settings.extractColorFromArt -> mediaState.dominantColor.copy(alpha = 1f)
        else -> MaterialTheme.colorScheme.primary
    }
    val accentColor = when {
        isAnimated && settings.animatedWhiteIcon -> Color.White
        else -> rawAccentColor
    }
    val animatedBgColor = when (settings.animatedColorSource) {
        "album_art", "blur_album_art" -> mediaState.blendedArtColor.copy(alpha = 1f)
        else -> rawAccentColor
    }
    val textColor = when {
        isAnimated -> Color.White
        isColorMix -> {
            val avg = (rawAccentColor.luminance() + mediaState.blendedArtColor.luminance()) / 2f
            if (avg > 0.5f) Color.Black else Color.White
        }
        isBlur && settings.blurStyle != "dark_mode" -> Color.White
        isBlur && settings.blurStyle == "dark_mode" -> if (isDark) Color.White else Color.Black
        else -> MaterialTheme.colorScheme.onBackground
    }
    val style = DStyle(
        accentColor = accentColor,
        iconColor = textColor.copy(alpha = settings.homeIconOpacity),
        textColor = textColor,
        textColorSub = textColor.copy(alpha = 0.75f),
        isBlur = isBlur
    )
    val actions = DActions(
        onLongPressPlay = onLongPressPlay,
        onShowLyrics = onShowLyrics,
        onShowHistory = onShowHistory,
        onToggleFavorite = {
            favoriteViewModel.toggleFavorite(
                title = mediaState.title,
                artist = mediaState.artist,
                album = mediaState.album,
                albumArtUri = resolvedFavoriteArtUri,
                appLabel = mediaState.appLabel,
                packageName = mediaState.packageName,
                mediaId = mediaState.mediaId,
                durationMs = mediaState.durationMs
            )
        },
        onShare = {
            shareScreenshot(context = context, title = mediaState.title, artist = mediaState.artist)
        }
    )

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        // 白フラッシュ対策の最終防衛ライン
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))

        // 背景（Bと共通。曲切り替え時にクロスフェード）
        HomeScreenBBackground(
            mediaState = mediaState,
            settings = settings,
            accentColor = accentColor,
            rawAccentColor = rawAccentColor,
            animatedBgColor = animatedBgColor,
            isDark = isDark,
            isBlur = isBlur,
            isAnimated = isAnimated
        )

        // 背景装飾
        if (settings.bgDecorationType != "none") {
            BackgroundDecorationLayer(
                type = settings.bgDecorationType,
                color = if (settings.bgDecorationColor == "white") Color.White else rawAccentColor,
                opacity = settings.bgDecorationOpacity,
                sizeScale = settings.bgDecorationSize,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isLandscape) {
            // ===== 横画面: 左=アート（右に向かって透明にフェード）／右=操作系 =====
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    FadingArtD(
                        mediaState = mediaState,
                        horizontalFade = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                HomeScreenDControls(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp, end = 20.dp + endNavBarWidth)
                        .padding(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                            bottom = bottomNavBarHeight + 8.dp
                        )
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    compact = true,
                    mediaState = mediaState,
                    settings = settings,
                    viewModel = viewModel,
                    style = style,
                    actions = actions,
                    repeatMode = repeatMode,
                    lastSkipDirection = lastSkipDirection,
                    isShuffleEnabled = isShuffleEnabled,
                    isFavorite = isFavorite,
                    sharedTransitionScope = null,
                    homeAnimatedVisibilityScope = null
                )
            }
        } else {
            // ===== 縦画面: アートは上端から大きく表示し、下に向かって透明にフェード =====
            // アートは「背景の一部」として敷き、操作系は画面下に固定する。
            // ★ アートの高さは画面高の約66%。フェードが始まる位置(高さの55%)が画面の約36%、
            //   完全に消える位置が約66%になるため、画面の半分あたりまでアートが見える。
            //   縦長の画面ではアートが画面幅より縦に長くなるので、正方形を保ったまま
            //   左右を画面外へはみ出させ(requiredWidth)、はみ出した分は画面端で切れる。
            //   画面が低くて操作系が高くなっても、アートの上に重なるだけでスクロールは不要。
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val artHeight = maxHeight * 0.52f
                val artWidth = if (artHeight > maxWidth) artHeight else maxWidth
                FadingArtD(
                    mediaState = mediaState,
                    horizontalFade = false,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .requiredWidth(artWidth)
                        .height(artHeight)
                        .sharedElementIfAvailable(
                            key = SharedLyricsKeys.ALBUM_ART,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = homeAnimatedVisibilityScope
                        )
                )
                HomeScreenDControls(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        // bottomNavBarHeight はピル+システムナビバー込みの実測値
                        .padding(bottom = bottomNavBarHeight + 12.dp),
                    compact = false,
                    mediaState = mediaState,
                    settings = settings,
                    viewModel = viewModel,
                    style = style,
                    actions = actions,
                    repeatMode = repeatMode,
                    lastSkipDirection = lastSkipDirection,
                    isShuffleEnabled = isShuffleEnabled,
                    isFavorite = isFavorite,
                    sharedTransitionScope = sharedTransitionScope,
                    homeAnimatedVisibilityScope = homeAnimatedVisibilityScope
                )
            } // BoxWithConstraints
        }
    }
}

// =============================================================================
// フェードするアルバムアート（曲切り替え時はクロスフェード）
// =============================================================================
@Composable
private fun FadingArtD(
    mediaState: MediaState,
    horizontalFade: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.artFadeMask(horizontalFade)) {
        Crossfade(
            targetState = mediaState.albumArtUri to mediaState.albumArtBitmap,
            animationSpec = tween(500),
            label = "artFadeD"
        ) { (uri, bitmap) ->
            when {
                uri != null -> AsyncImage(
                    model = uri,
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                else -> Box(
                    modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                }
            }
        }
    }
}

// =============================================================================
// 操作系: 曲情報行 → シークバー → 時間 → コントロール → 音量 → ショートカット/出力先
// =============================================================================
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeScreenDControls(
    modifier: Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    compact: Boolean,
    mediaState: MediaState,
    settings: AppSettings,
    viewModel: MediaPlayerViewModel,
    style: DStyle,
    actions: DActions,
    repeatMode: QueueRepeatMode,
    lastSkipDirection: Int,
    isShuffleEnabled: Boolean,
    isFavorite: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    homeAnimatedVisibilityScope: AnimatedVisibilityScope?,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = verticalArrangement
    ) {
        // ===== 波形 =====
        if (settings.showWaveform) {
            WaveformAnimation(
                isPlaying = mediaState.isPlaying,
                color = style.accentColor,
                modifier = Modifier.height(if (compact) 12.dp else 14.dp)
                    .fillMaxWidth(if (compact) 0.35f else 0.22f)
            )
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        }

        // ===== 曲情報（左寄せ）＋ 右側に丸ボタン（お気に入り・…）=====
        val trackInfoSnapshot = TrackInfoSnapshotD(mediaState.title, mediaState.artist, mediaState.album)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = trackInfoSnapshot,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val spec = tween<IntOffset>(durationMillis = 350)
                    if (lastSkipDirection >= 0) {
                        (slideInHorizontally(animationSpec = spec) { w -> w } + fadeIn(tween(350))) togetherWith
                                (slideOutHorizontally(animationSpec = spec) { w -> -w } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(animationSpec = spec) { w -> -w } + fadeIn(tween(350))) togetherWith
                                (slideOutHorizontally(animationSpec = spec) { w -> w } + fadeOut(tween(200)))
                    }.using(SizeTransform(clip = false))
                },
                label = "trackInfoSlideD"
            ) { snap ->
                TrackInfo(
                    title = snap.title,
                    artist = snap.artist,
                    album = snap.album,
                    titleFontSize = settings.titleFontSize.sp,
                    titleFontWeight = FontWeight(settings.titleFontWeight),
                    artistFontSize = settings.artistFontSize.sp,
                    artistFontWeight = FontWeight(settings.artistFontWeight),
                    albumFontSize = settings.albumFontSize.sp,
                    albumFontWeight = FontWeight(settings.albumFontWeight),
                    scrollTitle = settings.scrollLongTitle,
                    textColor = style.textColor,
                    // 文字の影は付けない（TrackInfoはisBlurBg=trueのときだけ影を付ける仕様のため、常にfalse）
                    isBlurBg = false,
                    titleModifier = Modifier.sharedElementIfAvailable(
                        key = SharedLyricsKeys.TITLE,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = homeAnimatedVisibilityScope
                    ),
                    artistModifier = Modifier.sharedElementIfAvailable(
                        key = SharedLyricsKeys.ARTIST,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = homeAnimatedVisibilityScope
                    ),
                )
            }
            Spacer(Modifier.width(12.dp))
            CircleActionButtonD(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                description = "お気に入り",
                tint = if (isFavorite) style.accentColor else style.iconColor,
                background = style.textColor.copy(alpha = 0.18f),
                onClick = actions.onToggleFavorite
            )
            Spacer(Modifier.width(8.dp))
            CircleActionButtonD(
                icon = Icons.Rounded.MoreHoriz,
                description = "メニュー",
                tint = style.iconColor,
                background = style.textColor.copy(alpha = 0.18f),
                onClick = actions.onLongPressPlay
            )
        }

        Spacer(Modifier.height(if (compact) 12.dp else 18.dp))

        // ===== シークバー（設定で波状/通常を切り替え）=====
        if (settings.waveSeekBar) {
            WaveSeekBar(
                progress = mediaState.progress,
                onProgressChange = { viewModel.seekTo(it) },
                thickness = settings.seekBarThickness.dp,
                color = style.accentColor,
                style = settings.waveSeekBarStyle,
                isPlaying = mediaState.isPlaying,
                durationMs = mediaState.durationMs
            )
        } else {
            SeekBarB(
                progress = mediaState.progress,
                onProgressChange = { viewModel.seekTo(it) },
                thickness = settings.seekBarThickness.dp,
                color = style.accentColor,
                durationMs = mediaState.durationMs
            )
        }

        Spacer(Modifier.height(6.dp))

        // ===== 時間表示 + 空間オーディオバッジ =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(mediaState.currentPositionStr, style = MaterialTheme.typography.labelSmall, color = style.textColorSub)
            if (mediaState.isSpatialAudio) SpatialAudioBadge(isBlurBg = style.isBlur)
            Text("-${mediaState.remainingTimeStr}", style = MaterialTheme.typography.labelSmall, color = style.textColorSub)
        }

        Spacer(Modifier.height(if (compact) 10.dp else 22.dp))

        // ===== コントロール（Bと共通のフラットなボタン）=====
        PlayerControlsB(
            isPlaying = mediaState.isPlaying,
            onPrev = { viewModel.skipToPrevious() },
            onNext = { viewModel.skipToNext() },
            onPlayPause = { viewModel.togglePlayPause() },
            onRewind = { viewModel.seekRelative(-10000L) },
            onFastForward = { viewModel.seekRelative(10000L) },
            onLongPressPlay = actions.onLongPressPlay,
            buttonColor = style.accentColor,
            iconColor = style.iconColor,
            vibrationMs = if (settings.vibrationEnabled) settings.vibrationStrength else 0
        )

        Spacer(Modifier.height(if (compact) 12.dp else 22.dp))

        // ===== 音量バー =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.VolumeDown, null, modifier = Modifier.size(16.dp), tint = style.iconColor.copy(alpha = 0.6f))
            Spacer(Modifier.width(6.dp))
            var isDragging by remember { mutableStateOf(false) }
            VolumeBarB(
                progress = mediaState.volume,
                isActive = isDragging,
                accentColor = style.accentColor,
                trackColor = style.accentColor.copy(alpha = 0.20f),
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, _ ->
                                change.consume()
                                viewModel.setVolume((change.position.x / size.width).coerceIn(0f, 1f))
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            viewModel.setVolume((offset.x / size.width).coerceIn(0f, 1f))
                        }
                    }
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.VolumeUp, null, modifier = Modifier.size(16.dp), tint = style.iconColor.copy(alpha = 0.6f))
        }

        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))

        // ===== 下段: 左ショートカット ／ 音の出力先（中央）／ 右ショートカット =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeShortcutButtonB(
                type = settings.homeShortcutLeft, repeatMode = repeatMode, isShuffleEnabled = isShuffleEnabled,
                isFavorite = isFavorite, iconTint = style.iconColor, activeColor = style.accentColor,
                onShowLyrics = actions.onShowLyrics, onRepeat = { viewModel.cycleRepeatMode() },
                onShuffle = { viewModel.toggleShuffle() }, onToggleFavorite = actions.onToggleFavorite,
                onShowHistory = actions.onShowHistory, onShare = actions.onShare
            )
            AudioOutputIndicator(
                tint = style.iconColor.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f)
            )
            HomeShortcutButtonB(
                type = settings.homeShortcutRight, repeatMode = repeatMode, isShuffleEnabled = isShuffleEnabled,
                isFavorite = isFavorite, iconTint = style.iconColor, activeColor = style.accentColor,
                onShowLyrics = actions.onShowLyrics, onRepeat = { viewModel.cycleRepeatMode() },
                onShuffle = { viewModel.toggleShuffle() }, onToggleFavorite = actions.onToggleFavorite,
                onShowHistory = actions.onShowHistory, onShare = actions.onShare
            )
        }
    }
}

// ===== 半透明の丸いアクションボタン =====
@Composable
private fun CircleActionButtonD(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
    }
}
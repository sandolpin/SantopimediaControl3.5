package com.sandolpin.santopimedia35.home

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sandolpin.santopimedia35.MediaPlayerViewModel
import com.sandolpin.santopimedia35.QueueRepeatMode
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.MediaState
import com.sandolpin.santopimedia35.shareScreenshot
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerInputChange

// =============================================================================
// UI-B: ミニマルなPlayerScreen（HomeScreenAと同等の機能に対応）
// -----------------------------------------------------------------------------
// ■ UI-Bの個性（Aとの違い）
//   ・アルバムアートは画面幅いっぱい・大きな角丸(28dp)・白縁取りも影もなし
//   ・コントロールはフラット（前後の曲・再生ボタンは背景なし）
//   ・波状シークバーは非対応（通常シークバーのみ）
//
// ■ HomeScreenAと共通の機能
//   ・背景: default / gradient / blur / color_mix / animated（AGSL・API33+）
//           動的アニメの色ルール（accent / album_art / blur_album_art）
//           曲切り替え時のクロスフェード・白フラッシュ対策
//   ・背景装飾（ドット / 水玉 / ランダム図形）
//   ・テキスト・アイコン色の自動判定、ホーム画面アイコン透明度
//   ・ステータスバー / ピル型ナビゲーションバー分の余白（縦画面・横画面）
//   ・縦画面: アート以外の高さを先に実測し、アートの大きさを逆算（スクロール無しで収まる）
//   ・横画面: 左=アート／右=操作系 の左右分割
//   ・曲名のスライドアニメーション、長いタイトルのスクロール
//   ・空間オーディオバッジ、左右ショートカット、音量バー、音声出力先表示
//   ・お気に入り・シェア・履歴・歌詞への導線
//   ・sharedElementTransition（アート・タイトル・アーティスト）
//
// ■ 設計メモ
//   HomeScreenA.ktの private な部品（背景スナップショット等）は、あえて共有化せず
//   B専用のprivate部品として持たせている（Aに一切手を入れずに済ませるため）。
//   クラス名の衝突を避けるため、名前の末尾に「B」を付けている。
// =============================================================================

// ===== 背景クロスフェード用スナップショット =====
private data class HomeScreenBBgSnapshot(
    val albumArtUri: String?,
    val blendedArtColor: Color,
    val accentColor: Color,
    val rawAccentColor: Color,
    val animatedBgColor: Color
)

// ===== テキストスライド用スナップショット =====
private data class TrackInfoSnapshotB(
    val title: String,
    val artist: String,
    val album: String
)

// ===== 色・スタイルまとめ =====
private class BStyle(
    val accentColor: Color,
    val iconColor: Color,
    val textColor: Color,
    val textColorSub: Color,
    val isBlur: Boolean,
    val isAnimated: Boolean,
)

// ===== 操作コールバックまとめ =====
private class BActions(
    val onLongPressPlay: () -> Unit,
    val onShowLyrics: () -> Unit,
    val onShowHistory: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onShare: () -> Unit,
)

private val ArtShapeB = RoundedCornerShape(28.dp)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreenB(
    viewModel: MediaPlayerViewModel,
    settings: AppSettings,
    // ピル型ナビゲーションバーの実測高さ（MainActivityから）。
    // 背景は画面の端まで伸ばしたまま、操作要素だけこの高さぶん上げる。
    bottomNavBarHeight: Dp = 0.dp,
    // 横画面時、画面右端の縦向きピルの実測幅。縦画面では0dp。
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

    // ===== お気に入り（HomeScreenAと同じFavoriteViewModelを共有）=====
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

    // ===== 背景スタイル判定（HomeScreenAと同じ）=====
    val isBlur = settings.backgroundStyle == "blur"
    val isAnimated = settings.backgroundStyle == "animated"
    val isColorMix = settings.backgroundStyle == "color_mix"

    val isDark = when (settings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    // ===== アクセントカラー =====
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

    // ===== テキスト・アイコン色 =====
    val textColor = when {
        isAnimated -> Color.White
        isColorMix -> {
            val avgLuminance = (rawAccentColor.luminance() + mediaState.blendedArtColor.luminance()) / 2f
            if (avgLuminance > 0.5f) Color.Black else Color.White
        }
        isBlur && settings.blurStyle != "dark_mode" -> Color.White
        isBlur && settings.blurStyle == "dark_mode" -> if (isDark) Color.White else Color.Black
        else -> MaterialTheme.colorScheme.onBackground
    }
    val iconColor = textColor.copy(alpha = settings.homeIconOpacity)
    val textColorSub = textColor.copy(alpha = 0.75f)

    val style = BStyle(
        accentColor = accentColor,
        iconColor = iconColor,
        textColor = textColor,
        textColorSub = textColorSub,
        isBlur = isBlur,
        isAnimated = isAnimated
    )
    val actions = BActions(
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
            shareScreenshot(
                context = context,
                title = mediaState.title,
                artist = mediaState.artist
            )
        }
    )

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        // ===== 常時下敷き（曲切り替え時の白フラッシュ対策の最終防衛ライン）=====
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))

        // ===== 背景（曲切り替え時にクロスフェード）=====
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

        // ===== 背景装飾（どの背景スタイルにも重ねられる独立レイヤー）=====
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
            HomeScreenBLandscapeContent(
                mediaState = mediaState,
                settings = settings,
                viewModel = viewModel,
                style = style,
                actions = actions,
                repeatMode = repeatMode,
                lastSkipDirection = lastSkipDirection,
                isShuffleEnabled = isShuffleEnabled,
                isFavorite = isFavorite,
                bottomNavBarHeight = bottomNavBarHeight,
                endNavBarWidth = endNavBarWidth
            )
        } else {
            HomeScreenBPortraitAdaptive(
                mediaState = mediaState,
                settings = settings,
                viewModel = viewModel,
                style = style,
                actions = actions,
                repeatMode = repeatMode,
                lastSkipDirection = lastSkipDirection,
                isShuffleEnabled = isShuffleEnabled,
                isFavorite = isFavorite,
                bottomNavBarHeight = bottomNavBarHeight,
                sharedTransitionScope = sharedTransitionScope,
                homeAnimatedVisibilityScope = homeAnimatedVisibilityScope
            )
        }
    }
}

// =============================================================================
// 背景（HomeScreenAと同じロジック。曲切り替え時にクロスフェード）
// =============================================================================
@Composable
internal fun HomeScreenBBackground(
    mediaState: MediaState,
    settings: AppSettings,
    accentColor: Color,
    rawAccentColor: Color,
    animatedBgColor: Color,
    isDark: Boolean,
    isBlur: Boolean,
    isAnimated: Boolean,
) {
    val context = LocalContext.current
    val useAgsl = isAnimated && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    val useAnimatedFallback = isAnimated && !useAgsl // API<33 → ぼかしにフォールバック
    val useBlurAlbumArtBase = isAnimated && settings.animatedColorSource == "blur_album_art"

    // Crossfadeのフェードアウト側は「切り替わる前の値」を保持し続ける必要があるため、
    // 背景描画に必要な値だけをスナップショットにまとめて渡す
    val bgSnapshot = HomeScreenBBgSnapshot(
        albumArtUri = mediaState.albumArtUri,
        blendedArtColor = mediaState.blendedArtColor,
        accentColor = accentColor,
        rawAccentColor = rawAccentColor,
        animatedBgColor = animatedBgColor
    )

    Crossfade(
        targetState = bgSnapshot,
        animationSpec = tween(durationMillis = 500),
        label = "playerBackgroundFadeB"
    ) { snap ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ===== ぼかし背景 =====
            val showBlurBase = isBlur || useAnimatedFallback || (useAgsl && useBlurAlbumArtBase)
            if (showBlurBase) {
                // 画像読み込み中は透明のままになり白が透けるため、アクセントカラーを下敷きにする
                Box(modifier = Modifier.fillMaxSize().background(snap.rawAccentColor))

                if (snap.albumArtUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(snap.albumArtUri)
                            .crossfade(300)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().blur(60.dp),
                        contentScale = ContentScale.Crop
                    )
                    // ★ オーバーレイは「ぼかし」スタイル本来の設定のため isBlur のときだけ適用
                    if (isBlur) {
                        when (settings.blurStyle) {
                            "dark_mode" -> Box(
                                modifier = Modifier.fillMaxSize().background(
                                    if (isDark) Color.Black.copy(alpha = 0.60f)
                                    else Color.White.copy(alpha = 0.60f)
                                )
                            )
                            else -> { /* normal: オーバーレイなし */ }
                        }
                    }
                }
            }

            // ===== 動的アニメーション背景（AGSL / API33+）=====
            if (useAgsl) {
                AnimatedMeshBackground(
                    color1 = snap.animatedBgColor,
                    darkness = settings.animatedBgDarkness,
                    darkModeDarkness = settings.animatedBgDarknessDark,
                    colorPattern = settings.animatedColorPattern,
                    isDarkTheme = isDark,
                    alpha = if (useBlurAlbumArtBase) 0.62f else 1f,
                    modifier = Modifier.fillMaxSize()
                )
            }

            when (settings.backgroundStyle) {
                "gradient" -> Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                snap.accentColor.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                )
                "blur" -> { /* 上で描画済み */ }
                "animated" -> { /* 上で描画済み（AGSLまたはフォールバック） */ }
                "color_mix" -> Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(snap.rawAccentColor, snap.blendedArtColor.copy(alpha = 1f))
                        )
                    )
                )
                else -> Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                )
            }
        }
    }
}

// =============================================================================
// 縦画面: SubcomposeLayoutで「アート以外の高さ」を先に実測し、
// 残りの高さと画面幅の小さい方をアートの一辺にする（スクロール無しで必ず収まる）
// =============================================================================
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeScreenBPortraitAdaptive(
    mediaState: MediaState,
    settings: AppSettings,
    viewModel: MediaPlayerViewModel,
    style: BStyle,
    actions: BActions,
    repeatMode: QueueRepeatMode,
    lastSkipDirection: Int,
    isShuffleEnabled: Boolean,
    isFavorite: Boolean,
    bottomNavBarHeight: Dp,
    sharedTransitionScope: SharedTransitionScope?,
    homeAnimatedVisibilityScope: AnimatedVisibilityScope?,
) {
    SubcomposeLayout(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                // ピル+システムナビゲーションバー分を含めて実測した値
                bottom = bottomNavBarHeight
            )
    ) { constraints ->
        val fullWidth = constraints.maxWidth
        val fullHeight = constraints.maxHeight
        val topSpacerPx = 4.dp.roundToPx()
        val artGapPx = 16.dp.roundToPx()
        val minArtPx = 100.dp.roundToPx()

        // ① アート以外のコンテンツを高さ無制限で先に測る
        val belowPlaceable = subcompose("below") {
            HomeScreenBControls(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
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
        }.first().measure(
            Constraints(
                minWidth = fullWidth, maxWidth = fullWidth,
                minHeight = 0, maxHeight = Constraints.Infinity
            )
        )

        // ② 残り高さと画面幅の小さい方をアートの一辺サイズにする
        val availableForArtPx = (fullHeight - belowPlaceable.height - topSpacerPx - artGapPx)
            .coerceAtLeast(minArtPx)
        val artSizePx = minOf(fullWidth, availableForArtPx)

        // ③ 決まったサイズでアートを測る
        val artPlaceable = subcompose("art") {
            AlbumArtCardB(
                uri = mediaState.albumArtUri,
                bitmap = mediaState.albumArtBitmap,
                modifier = Modifier
                    .size(artSizePx.toDp())
                    .sharedElementIfAvailable(
                        key = SharedLyricsKeys.ALBUM_ART,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = homeAnimatedVisibilityScope
                    )
            )
        }.first().measure(Constraints.fixed(artSizePx, artSizePx))

        val totalContentHeight = topSpacerPx + artPlaceable.height + artGapPx + belowPlaceable.height

        layout(fullWidth, maxOf(totalContentHeight, fullHeight)) {
            val artX = (fullWidth - artPlaceable.width) / 2
            var y = topSpacerPx
            artPlaceable.placeRelative(artX, y)
            y += artPlaceable.height + artGapPx
            belowPlaceable.placeRelative(0, y)
        }
    }
}

// =============================================================================
// 横画面: 左半分=アルバムアート／右半分=操作系
// =============================================================================
@Composable
private fun HomeScreenBLandscapeContent(
    mediaState: MediaState,
    settings: AppSettings,
    viewModel: MediaPlayerViewModel,
    style: BStyle,
    actions: BActions,
    repeatMode: QueueRepeatMode,
    lastSkipDirection: Int,
    isShuffleEnabled: Boolean,
    isFavorite: Boolean,
    bottomNavBarHeight: Dp,
    endNavBarWidth: Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            // 縦向きピルが右端に重なるぶん、右の余白を追加する
            .padding(start = 16.dp, end = 16.dp + endNavBarWidth)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                bottom = bottomNavBarHeight + 8.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ===== 左半分: アルバムアート（縦幅基準）=====
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtCardB(
                uri = mediaState.albumArtUri,
                bitmap = mediaState.albumArtBitmap,
                modifier = Modifier.fillMaxHeight(0.92f).aspectRatio(1f)
            )
        }

        // ===== 右半分: 操作系（収まらない場合の保険でスクロール可能）=====
        HomeScreenBControls(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
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
}

// =============================================================================
// アート以外のコンテンツ（波形 → シークバー → 時間 → 曲情報 → コントロール → サブコントロール）
// 縦画面・横画面で共用。compact=true のときは余白を詰める。
// =============================================================================
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeScreenBControls(
    modifier: Modifier,
    horizontalAlignment: Alignment.Horizontal,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    compact: Boolean,
    mediaState: MediaState,
    settings: AppSettings,
    viewModel: MediaPlayerViewModel,
    style: BStyle,
    actions: BActions,
    repeatMode: QueueRepeatMode,
    lastSkipDirection: Int,
    isShuffleEnabled: Boolean,
    isFavorite: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    homeAnimatedVisibilityScope: AnimatedVisibilityScope?,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
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
            Spacer(modifier = Modifier.height(if (compact) 10.dp else 14.dp))
        }

        // ===== シークバー（UI-Bは波状非対応 → 通常のみ）=====
        SeekBarB(
            progress = mediaState.progress,
            onProgressChange = { viewModel.seekTo(it) },
            thickness = settings.seekBarThickness.dp,
            color = style.accentColor,
            durationMs = mediaState.durationMs
        )

        Spacer(modifier = Modifier.height(6.dp))

        // ===== 時間表示 + 空間オーディオバッジ =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                mediaState.currentPositionStr,
                style = MaterialTheme.typography.labelSmall,
                color = style.textColorSub
            )
            if (mediaState.isSpatialAudio) {
                SpatialAudioBadge(isBlurBg = style.isBlur)
            }
            Text(
                "-${mediaState.remainingTimeStr}",
                style = MaterialTheme.typography.labelSmall,
                color = style.textColorSub
            )
        }

        Spacer(modifier = Modifier.height(if (compact) 14.dp else 16.dp))

        // ===== 曲名・アーティスト（曲切り替え時にスライドアニメーション）=====
        val trackInfoSnapshot = TrackInfoSnapshotB(
            title = mediaState.title,
            artist = mediaState.artist,
            album = mediaState.album
        )
        AnimatedContent(
            targetState = trackInfoSnapshot,
            transitionSpec = {
                val spec = tween<IntOffset>(durationMillis = 350)
                if (lastSkipDirection >= 0) {
                    (slideInHorizontally(animationSpec = spec) { width -> width } + fadeIn(tween(350))) togetherWith
                            (slideOutHorizontally(animationSpec = spec) { width -> -width } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(animationSpec = spec) { width -> -width } + fadeIn(tween(350))) togetherWith
                            (slideOutHorizontally(animationSpec = spec) { width -> width } + fadeOut(tween(200)))
                }.using(SizeTransform(clip = false))
            },
            label = "trackInfoSlideB"
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
                isBlurBg = style.isBlur,
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

        Spacer(modifier = Modifier.height(if (compact) 20.dp else 28.dp))

        // ===== コントロールボタン UI-B（フラット）=====
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

        Spacer(modifier = Modifier.height(if (compact) 16.dp else 24.dp))

        // ===== サブボタン（左右ショートカット・音量・音声出力先）=====
        SubControlsB(
            mediaState = mediaState,
            repeatMode = repeatMode,
            isShuffleEnabled = isShuffleEnabled,
            isFavorite = isFavorite,
            shortcutLeft = settings.homeShortcutLeft,
            shortcutRight = settings.homeShortcutRight,
            onShowLyrics = actions.onShowLyrics,
            onVolumeChange = { viewModel.setVolume(it) },
            onRepeat = { viewModel.cycleRepeatMode() },
            onShuffle = { viewModel.toggleShuffle() },
            onToggleFavorite = actions.onToggleFavorite,
            onShowHistory = actions.onShowHistory,
            onShare = actions.onShare,
            accentColor = style.accentColor,
            iconColor = style.iconColor,
            isBlurBg = style.isBlur,
            textColor = style.textColor,
        )

        Spacer(modifier = Modifier.height(if (compact) 8.dp else 16.dp))
    }
}

// =============================================================================
// アルバムアート（UI-B: 大きな角丸・縁取りなし・影なしのミニマル）
// URIが無くBitmapのみ提供するアプリにも対応
// =============================================================================
@Composable
private fun AlbumArtCardB(
    uri: String?,
    bitmap: android.graphics.Bitmap?,
    modifier: Modifier = Modifier
) {
    when {
        uri != null -> AsyncImage(
            model = uri,
            contentDescription = "Album Art",
            modifier = modifier.clip(ArtShapeB),
            contentScale = ContentScale.Crop
        )
        bitmap != null -> androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Album Art",
            modifier = modifier.clip(ArtShapeB),
            contentScale = ContentScale.Crop
        )
        else -> Box(
            modifier = modifier.background(Color.Gray.copy(alpha = 0.3f), ArtShapeB),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
        }
    }
}

// ===== 長押しスケールアニメーション付きボタン（B専用）=====
@Composable
private fun PressScaleButtonB(
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScaleB"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onTap?.invoke() },
                    onLongPress = {
                        isPressed = false
                        onLongPress?.invoke()
                    }
                )
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

// ===== コントロールボタン UI-B（フラット + Rounded）=====
// すべてのボタンが背景なしのフラットなデザイン。
@Composable
fun PlayerControlsB(
    isPlaying: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onLongPressPlay: () -> Unit,
    buttonColor: Color,
    vibrationMs: Int,
    iconColor: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    val iconTint = if (iconColor != Color.Unspecified) iconColor
    else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PressScaleButtonB(
            modifier = Modifier.size(48.dp),
            onTap = { vibrateIfEnabled(context, vibrationMs); onPrev() }
        ) {
            Icon(Icons.Rounded.SkipPrevious, "前の曲", modifier = Modifier.size(34.dp), tint = iconTint)
        }

        // 10秒戻し（背景なし）
        PressScaleButtonB(
            modifier = Modifier.size(52.dp),
            onTap = { vibrateIfEnabled(context, vibrationMs); onRewind() }
        ) {
            Icon(Icons.Rounded.Replay10, "10秒戻し", modifier = Modifier.size(28.dp), tint = iconTint)
        }

        // 再生・停止（大きめ・フラット）長押しでアクションメニュー
        PressScaleButtonB(
            modifier = Modifier.size(80.dp),
            onTap = { vibrateIfEnabled(context, vibrationMs); onPlayPause() },
            onLongPress = { vibrateIfEnabled(context, vibrationMs); onLongPressPlay() }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "一時停止" else "再生",
                tint = buttonColor,
                modifier = Modifier.size(72.dp)
            )
        }

        // 10秒送り（背景なし）
        PressScaleButtonB(
            modifier = Modifier.size(52.dp),
            onTap = { vibrateIfEnabled(context, vibrationMs); onFastForward() }
        ) {
            Icon(Icons.Rounded.Forward10, "10秒送り", modifier = Modifier.size(28.dp), tint = iconTint)
        }

        PressScaleButtonB(
            modifier = Modifier.size(48.dp),
            onTap = { vibrateIfEnabled(context, vibrationMs); onNext() }
        ) {
            Icon(Icons.Rounded.SkipNext, "次の曲", modifier = Modifier.size(34.dp), tint = iconTint)
        }
    }
}

// =============================================================================
// シークバー UI-B（直線トラック）
// -----------------------------------------------------------------------------
// HomeScreenAのNormalSeekBarは未再生部分が「破線」だが、Bでは
// 未再生部分も途切れのない直線（薄い色）で描く。操作（タップ/ドラッグ/時間吹き出し）は同じ。
// =============================================================================
@Composable
internal fun SeekBarB(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    thickness: Dp,
    color: Color,
    durationMs: Long = 0L
) {
    var dragProgress by remember { mutableStateOf(-1f) }
    var isDraggingSeek by remember { mutableStateOf(false) }
    var lastKnownDragProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(dragProgress) {
        if (dragProgress >= 0f) lastKnownDragProgress = dragProgress
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isDraggingSeek && dragProgress >= 0f) dragProgress else progress,
        animationSpec = if (isDraggingSeek) snap() else tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "seekProgressB"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // ドラッグ中の時間吹き出し（シークバーの上に表示）
        AnimatedVisibility(
            visible = isDraggingSeek && durationMs > 0,
            enter = fadeIn(tween(150)) + scaleIn(
                initialScale = 0.85f,
                animationSpec = tween(150),
                transformOrigin = TransformOrigin(0.5f, 1f)
            ),
            exit = fadeOut(tween(120)) + scaleOut(
                targetScale = 0.85f,
                animationSpec = tween(120),
                transformOrigin = TransformOrigin(0.5f, 1f)
            )
        ) {
            val posMs = (durationMs * lastKnownDragProgress).toLong()
            val posStr = "%d:%02d".format(posMs / 60000, (posMs % 60000) / 1000)
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = (lastKnownDragProgress * 300).dp.coerceIn(0.dp, 280.dp))
                ) {
                    Text(
                        posStr,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(thickness * 3)
                .pointerInput(Unit) {
                    detectTapGestures { offset -> onProgressChange((offset.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDraggingSeek = true },
                        onDragEnd = {
                            if (dragProgress >= 0f) onProgressChange(dragProgress)
                            isDraggingSeek = false; dragProgress = -1f
                        },
                        onDragCancel = { isDraggingSeek = false; dragProgress = -1f },
                        onDrag = { change: PointerInputChange, _: Offset ->
                            change.consume()
                            dragProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onProgressChange(dragProgress)
                        }
                    )
                }
        ) {
            val cy = size.height / 2f
            val r = thickness.toPx() / 2f
            val progressX = size.width * animatedProgress

            // 未再生：全幅の直線（薄い色）
            drawRoundRect(
                color = color.copy(alpha = 0.25f),
                topLeft = Offset(0f, cy - r),
                size = Size(size.width, r * 2),
                cornerRadius = CornerRadius(r)
            )
            // 再生済み：同じ太さの直線（濃い色）
            if (progressX > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, cy - r),
                    size = Size(progressX.coerceAtLeast(r * 2), r * 2),
                    cornerRadius = CornerRadius(r)
                )
            }
        }
    }
}

// =============================================================================
// 音量バー UI-B（ドットなしのシンプルなカプセル）
// =============================================================================
@Composable
internal fun VolumeBarB(
    progress: Float,
    isActive: Boolean,
    accentColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedHeight by animateDpAsState(
        targetValue = if (isActive) 28.dp else 18.dp,
        animationSpec = tween(200),
        label = "volHeightB"
    )
    Canvas(modifier = modifier.fillMaxWidth().height(animatedHeight)) {
        val p = 4.dp.toPx()
        val rad = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = trackColor, size = size, cornerRadius = rad)
        if (progress > 0f) drawRoundRect(
            color = accentColor,
            topLeft = Offset(p, p),
            size = Size((size.width - p * 2) * progress, size.height - p * 2),
            cornerRadius = CornerRadius((size.height - p * 2) / 2)
        )
        // ★ Aにあった未再生側の点線(ドット)は描かない
    }
}

// ===== ショートカットボタン（Aのprivate関数のB用コピー）=====
@Composable
internal fun HomeShortcutButtonB(
    type: String,
    repeatMode: QueueRepeatMode,
    isShuffleEnabled: Boolean,
    isFavorite: Boolean,
    iconTint: Color,
    activeColor: Color,
    onShowLyrics: () -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowHistory: () -> Unit,
    onShare: () -> Unit,
) {
    when (type) {
        "favorite" -> IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
            Icon(
                if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "お気に入り",
                tint = if (isFavorite) activeColor else iconTint, modifier = Modifier.size(24.dp)
            )
        }
        "history" -> IconButton(onClick = onShowHistory, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.History, "履歴", tint = iconTint, modifier = Modifier.size(24.dp))
        }
        "share" -> IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Share, "シェア", tint = iconTint, modifier = Modifier.size(24.dp))
        }
        "repeat" -> IconButton(onClick = onRepeat, modifier = Modifier.size(48.dp)) {
            Icon(
                if (repeatMode == QueueRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "リピート",
                tint = if (repeatMode != QueueRepeatMode.NONE) activeColor else iconTint.copy(0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
        "shuffle" -> IconButton(onClick = onShuffle, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Rounded.Shuffle, "シャッフル",
                tint = if (isShuffleEnabled) activeColor else iconTint.copy(0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
        else -> IconButton(onClick = onShowLyrics, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Lyrics, "歌詞", tint = iconTint, modifier = Modifier.size(24.dp))
        }
    }
}

// ===== サブコントロール UI-B（ショートカット・音量バー・出力先）=====
@Composable
private fun SubControlsB(
    mediaState: MediaState,
    repeatMode: QueueRepeatMode,
    isShuffleEnabled: Boolean,
    isFavorite: Boolean,
    shortcutLeft: String,
    shortcutRight: String,
    onShowLyrics: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowHistory: () -> Unit,
    onShare: () -> Unit,
    accentColor: Color,
    iconColor: Color,
    isBlurBg: Boolean,
    textColor: Color,
) {
    val iconTint = if (iconColor != Color.Unspecified) iconColor
    else MaterialTheme.colorScheme.onBackground

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeShortcutButtonB(
                type = shortcutLeft, repeatMode = repeatMode, isShuffleEnabled = isShuffleEnabled,
                isFavorite = isFavorite, iconTint = iconTint, activeColor = accentColor,
                onShowLyrics = onShowLyrics, onRepeat = onRepeat, onShuffle = onShuffle,
                onToggleFavorite = onToggleFavorite, onShowHistory = onShowHistory, onShare = onShare
            )

            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VolumeDown, null, modifier = Modifier.size(16.dp), tint = iconTint.copy(alpha = 0.6f))
                Spacer(Modifier.width(4.dp))
                var isDragging by remember { mutableStateOf(false) }
                VolumeBarB(
                    progress = mediaState.volume,
                    isActive = isDragging,
                    accentColor = accentColor,
                    trackColor = accentColor.copy(alpha = 0.20f),
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onDrag = { change, _ ->
                                    change.consume()
                                    onVolumeChange((change.position.x / size.width).coerceIn(0f, 1f))
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                onVolumeChange((offset.x / size.width).coerceIn(0f, 1f))
                            }
                        }
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.VolumeUp, null, modifier = Modifier.size(16.dp), tint = iconTint.copy(alpha = 0.6f))
            }

            HomeShortcutButtonB(
                type = shortcutRight, repeatMode = repeatMode, isShuffleEnabled = isShuffleEnabled,
                isFavorite = isFavorite, iconTint = iconTint, activeColor = accentColor,
                onShowLyrics = onShowLyrics, onRepeat = onRepeat, onShuffle = onShuffle,
                onToggleFavorite = onToggleFavorite, onShowHistory = onShowHistory, onShare = onShare
            )
        }

        Spacer(Modifier.height(6.dp))
        AudioOutputIndicator(tint = iconTint.copy(alpha = 0.55f))
    }
}
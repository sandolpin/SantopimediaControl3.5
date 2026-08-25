package com.sandolpin.santopimedia35.home

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sandolpin.santopimedia35.MediaPlayerViewModel
import com.sandolpin.santopimedia35.QueueRepeatMode
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.MediaState
import kotlin.math.sin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.sandolpin.santopimedia35.shareScreenshot

// ===== 背景クロスフェード用スナップショット =====
// Crossfade(targetState = ...)に渡す値。フェードアウト側のインスタンスには
// 「切り替わる前」の値が保持され続けるため、背景描画に必要な値だけをここに集約する。
private data class HomeScreenABgSnapshot(
    val albumArtUri: String?,
    val blendedArtColor: Color,
    val accentColor: Color,
    val rawAccentColor: Color,
    val animatedBgColor: Color
)

// ===== テキストスライド用スナップショット =====
// AnimatedContentも同様に、フェードアウト側は「切り替わる前」の値を保持し続ける必要が
// あるため、曲名・アーティスト・アルバムのテキストだけをここに集約して渡す。
private data class TrackInfoSnapshot(
    val title: String,
    val artist: String,
    val album: String
)

/**
 * UI-A PlayerScreen
 * レイアウト: アルバムアート(大) → 曲名 → シークバー → コントロール → 音量バー
 * コントロール・音量バーを下側に固定
 */
@Composable
fun HomeScreenA(
    viewModel: MediaPlayerViewModel,
    settings: AppSettings,
    // ★ ピル型ナビゲーションバーの実際の高さ。MainActivity.ktから渡される。
    //   背景(このComposable内でfillMaxSizeで描画している)は画面の本当の端まで
    //   伸ばしたままにし、コントロール等の操作要素だけこの高さぶんの余白を追加で持たせる。
    bottomNavBarHeight: androidx.compose.ui.unit.Dp = 0.dp,
    // ★ 横画面のとき、画面右端に表示される縦向きピルナビゲーションバーの実測幅。
    //   MainActivity.ktから渡される。横画面レイアウト(HomeScreenALandscapeContent)の
    //   右側の操作要素がこのピルに隠れないよう、右端の余白として使う。
    //   縦画面では常に0dp（縦向きピル自体が存在しないため）。
    endNavBarWidth: androidx.compose.ui.unit.Dp = 0.dp,
    onLongPressPlay: () -> Unit,
    onShowLyrics: () -> Unit = {},
    onShowHistory: () -> Unit = {}
) {
    val mediaState: MediaState by viewModel.mediaState.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    // ★ テキストのスライド方向判定用（次へ=1 / 前へ=-1）。
    //   skipToNext()/skipToPrevious()を呼んだ直近の方向がここに反映される。
    val lastSkipDirection by viewModel.lastSkipDirection.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()

    // ホーム画面ショートカット「favorite」用: 現在の曲がお気に入り済みかどうか
    // ActionMenuDialog等と同じFavoriteViewModelインスタンス（Activity単位で共有）を使うため、
    // ここで☆をトグルしても他画面の表示ともきちんと同期する。
    val favoriteContext = androidx.compose.ui.platform.LocalContext.current
    val favoriteViewModel: com.sandolpin.santopimedia35.favorite.FavoriteViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = com.sandolpin.santopimedia35.favorite.FavoriteViewModel.Factory(favoriteContext)
        )
    val isFavorite = favoriteViewModel.isFavorite(
        mediaState.title, mediaState.artist, mediaState.packageName
    )
    // albumArtUri が無くBitmapのみのアプリ対応: キャッシュ済みファイルURIを取得
    val resolvedFavoriteArtUri = remember(mediaState.title, mediaState.artist, mediaState.packageName) {
        mediaState.albumArtUri ?: mediaState.albumArtBitmap?.let { bmp ->
            val key = "${mediaState.title}|${mediaState.artist}|${mediaState.packageName}"
            com.sandolpin.santopimedia35.AlbumArtCache.saveAndGetUri(favoriteContext, bmp, key)
        }
    }

    val isBlur     = settings.backgroundStyle == "blur"
    val isAnimated = settings.backgroundStyle == "animated"
    // ★ 「色混ぜ」: AGSL(RuntimeShader)によるアニメーションは使わず、
    //   アルバムアートから抽出済みの2色（dominantColor・blendedArtColor）を
    //   静的なグラデーションで混ぜるだけのシンプルな背景スタイル。
    //   API制限が無く、動的アニメーションが使えない端末(API<33)でも常に使える。
    val isColorMix = settings.backgroundStyle == "color_mix"
    // animated かつ API < 33 の場合はぼかしにフォールバック
    val useAgsl    = isAnimated && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    val useAnimatedFallback = isAnimated && !useAgsl  // API<33 → ぼかし

    // ダークモード判定
    val isDark = when (settings.themeMode) {
        "dark"  -> true
        "light" -> false
        else    -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    // アクセントカラー決定
    // rawAccentColor: 白上書き前の元色。UI部品（ボタン等）の元色にはこちらを使う
    // accentColor:    UI部品用（シークバー・音量バー等）。白固定時は白に上書き
    val rawAccentColor = when {
        settings.extractColorFromArt -> mediaState.dominantColor.copy(alpha = 1f)
        else -> MaterialTheme.colorScheme.primary
    }
    val accentColor = when {
        isAnimated && settings.animatedWhiteIcon -> Color.White
        else -> rawAccentColor
    }

    // 動的アニメーション背景専用の色ソース:
    // "accent"    = rawAccentColor（extractColorFromArt設定に従う）をそのまま使う
    // "album_art" = extractColorFromArtの設定に関わらず、常にアルバムアートの色を強制使用
    //               単一のSwatchではなく、画像内の複数箇所をpopulation比率で混色した
    //               blendedArtColorを使うことで、実際の画像の色構成に近い結果にする
    //               （鮮やかだが面積の小さい色だけが選ばれてしまうのを防ぐ）
    val animatedBgColor = when (settings.animatedColorSource) {
        "album_art" -> mediaState.blendedArtColor.copy(alpha = 1f)
        else -> rawAccentColor
    }

    // テキスト色:
    // animated（白固定ON・OFF共通） → 白（背景が暗いため常に白）
    // blur・normal                  → 白
    // blur・dark_mode               → ダーク=白、ライト=黒
    // それ以外                      → テーマに従う
    val textColor = when {
        isAnimated -> Color.White   // animated は ON/OFF 問わずテキスト・アイコンは白
        isColorMix -> {
            // 混ぜる2色の平均明度で自動的に白/黒を選ぶ（HistoryCardの「複数色ミックス」と同じ考え方）
            val avgLuminance = (rawAccentColor.luminance() + mediaState.blendedArtColor.luminance()) / 2f
            if (avgLuminance > 0.5f) Color.Black else Color.White
        }
        isBlur && settings.blurStyle != "dark_mode" -> Color.White
        isBlur && settings.blurStyle == "dark_mode" -> if (isDark) Color.White else Color.Black
        else -> MaterialTheme.colorScheme.onBackground
    }

    // アイコン色: textColor に従う（animated は常に白）
    // ★ 設定(homeIconOpacity)で50〜100%の範囲でアイコン全体の不透明度を調整できる。
    //   textColorは基本的に不透明(alpha=1)のため、そのまま上書きしてよい。
    val iconColor = textColor.copy(alpha = settings.homeIconOpacity)
    val textColorSub = textColor.copy(alpha = 0.75f)

    // ===== 横画面判定 =====
    // ★ 以前は縦画面専用のColumn一本だけで組んでいたため、端末を横向きにすると
    //   「アルバムアート(fillMaxWidth+aspectRatio(1f))が画面幅いっぱいの正方形になろうとして
    //   画面の縦幅を大幅に超える」→「その下のコントロール類が画面外に押し出される/
    //   極端に圧縮される」という形でUIが崩壊していた（実際に発生した不具合）。
    //   横画面のときだけ「左半分:アルバムアート／右半分:テキスト・シークバー・
    //   コントロール・音量バー」の左右分割レイアウト(HomeScreenALandscapeContent)に
    //   切り替えることで対応する。
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        // ===== 常時下敷きレイヤー（白フラッシュ対策の最終防衛ライン） =====
        // ★ Crossfade内部で個別に下敷き色(rawAccentColor)を敷いても、
        //   Crossfade自体やAsyncImage(アルバムアート大表示側も含む)の読み込みタイミングに
        //   よっては、瞬間的にどのレイヤーも何も描画していない空白フレームが発生しうる。
        //   その場合に透けて見えるのがCompose/Activityのデフォルト背景（白）だったため、
        //   曲切り替え時の白フラッシュとして視認されていた。
        //   Crossfadeよりさらに下（常に存在し続ける、曲が変わっても再構築されない層）に
        //   黒背景を1枚敷いておくことで、以後どのレイヤーが読み込み中でも
        //   白ではなく黒が見える状態に統一する（メディアプレーヤーとして自然な見た目でもある）。
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))

        // ===== 背景（曲切り替え時にクロスフェード） =====
        // ★ Crossfadeのcontentラムダに渡ってくる値(snapshot)は、フェードアウトする側は
        //   「切り替わる前のスナップショット」、フェードインする側は「最新の値」が渡される。
        //   もしこの中で直接 mediaState を読んでしまうと、フェードアウト中の古い背景も
        //   一瞬で新しい状態に切り替わってしまいクロスフェードにならないため、
        //   背景描画に必要な値だけを HomeScreenABgSnapshot に一度まとめてから渡している。
        val bgSnapshot = HomeScreenABgSnapshot(
            albumArtUri = mediaState.albumArtUri,
            blendedArtColor = mediaState.blendedArtColor,
            accentColor = accentColor,
            rawAccentColor = rawAccentColor,
            animatedBgColor = animatedBgColor
        )
        Crossfade(
            targetState = bgSnapshot,
            animationSpec = tween(durationMillis = 500),
            label = "playerBackgroundFade"
        ) { snap ->
            Box(modifier = Modifier.fillMaxSize()) {
                // ===== ぼかし背景 =====
                if (isBlur || useAnimatedFallback) {
                    // ★ 白フラッシュ対策: AsyncImageは新しい画像の読み込みが終わるまで
                    //   何も描画しない（透明のまま）。Crossfadeでフェードイン中のこの層に
                    //   何も無いと、下に何も無ければテーマ背景（白）がそのまま透けて見え、
                    //   「曲切り替え時に一瞬白くなる」原因になっていた。
                    //   読み込み中もアクセントカラー（そのスナップショット時点で既に
                    //   同期的に取得できている値）を下敷きにしておくことで、白ではなく
                    //   曲の色味に近い色がすぐに見えるようにする。
                    Box(modifier = Modifier.fillMaxSize().background(snap.rawAccentColor))

                    if (snap.albumArtUri != null) {
                        AsyncImage(
                            // ★ Coil自体のcrossfadeも有効にし、画像読み込み完了の瞬間に
                            //   ポップインするのではなく、下敷き色から滑らかにフェードインさせる。
                            model = ImageRequest.Builder(favoriteContext)
                                .data(snap.albumArtUri)
                                .crossfade(300)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().blur(60.dp),
                            contentScale = ContentScale.Crop
                        )
                        // blurStyle: normal=オーバーレイなし、dark_mode=ダーク連動オーバーレイ
                        when (settings.blurStyle) {
                            "dark_mode" -> Box(
                                modifier = Modifier.fillMaxSize().background(
                                    if (isDark) Color.Black.copy(alpha = 0.60f)
                                    else        Color.White.copy(alpha = 0.60f)
                                )
                            )
                            else -> { /* normal: オーバーレイなし */ }
                        }
                    }
                }

                // ===== グラデーション・デフォルト背景（Boxに付けてシステムバー領域もカバー）=====
                // ===== 動的アニメーション背景（AGSL / API33+）=====
                if (useAgsl) {
                    AnimatedMeshBackground(
                        color1 = snap.animatedBgColor,  // 設定で選んだ色ソース（アクセント or アルバムアート）
                        darkness = settings.animatedBgDarkness,
                        colorPattern = settings.animatedColorPattern,
                        isDarkTheme = isDark,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                when (settings.backgroundStyle) {
                    "gradient" -> {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(
                                    snap.accentColor.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.background
                                ))
                            )
                        )
                    }
                    "blur" -> { /* ぼかし背景は上で処理済み */ }
                    "animated" -> { /* AGSLまたはフォールバックは上で処理済み */ }
                    "color_mix" -> {
                        // dominantColor（アクセント）とblendedArtColor（アート全体の混色）を
                        // 斜めグラデーションで静的に混ぜるだけ。アニメーションなし。
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.linearGradient(
                                    listOf(snap.rawAccentColor, snap.blendedArtColor.copy(alpha = 1f))
                                )
                            )
                        )
                    }
                    else -> Box(
                        modifier = Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
        }

        if (isLandscape) {
            // ===== 横画面レイアウト: 左半分=アルバムアート／右半分=テキスト・操作系 =====
            HomeScreenALandscapeContent(
                mediaState = mediaState,
                settings = settings,
                viewModel = viewModel,
                accentColor = accentColor,
                iconColor = iconColor,
                textColor = textColor,
                textColorSub = textColorSub,
                isBlur = isBlur,
                isAnimated = isAnimated,
                repeatMode = repeatMode,
                lastSkipDirection = lastSkipDirection,
                isShuffleEnabled = isShuffleEnabled,
                isFavorite = isFavorite,
                bottomNavBarHeight = bottomNavBarHeight,
                endNavBarWidth = endNavBarWidth,
                onLongPressPlay = onLongPressPlay,
                onShowLyrics = onShowLyrics,
                onShowHistory = onShowHistory,
                onToggleFavorite = {
                    favoriteViewModel.toggleFavorite(
                        title       = mediaState.title,
                        artist      = mediaState.artist,
                        album       = mediaState.album,
                        albumArtUri = resolvedFavoriteArtUri,
                        appLabel    = mediaState.appLabel,
                        packageName = mediaState.packageName,
                        mediaId     = mediaState.mediaId,
                        durationMs  = mediaState.durationMs
                    )
                },
                onShare = {
                    shareScreenshot(
                        context = favoriteContext,
                        title   = mediaState.title,
                        artist  = mediaState.artist
                    )
                }
            )
        } else {
            // ===== 縦画面レイアウト（従来通り）=====
            // ★ 画面の幅が大きい端末では、fillMaxWidth()+aspectRatio(1f)のアルバムアートの
            //   「高さ」も幅に比例して大きくなる。以前はスクロール無しの固定Columnだったため、
            //   幅が大きいほどアルバムアートが縦にも大きくなり、その分だけ下のコントロール類が
            //   画面下からはみ出して見切れてしまっていた（実際に発生した不具合）。
            //   横画面レイアウト(HomeScreenALandscapeContent)と同じ考え方で、
            //   verticalScrollを保険として付け、万一収まりきらない場合でも
            //   クリップされて操作不能になるのではなく、スクロールで最後まで
            //   到達できるようにする。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
                        // ★ 以前はWindowInsets.navigationBars（システムのナビゲーションバー分）のみを
                        //   見ていたが、これだとMainActivity.kt側のピル型ナビゲーションバー自体の
                        //   高さは考慮されておらず、コントロール等がピルに隠れる可能性があった。
                        //   bottomNavBarHeightはピル+システムナビゲーションバー分を含めて実測した
                        //   値のため、これを使うことでコントロールがピルに隠れないようにする。
                        //   （背景は上のBoxで既にfillMaxSize()のままなのでピルの裏まで届く）
                        bottom = bottomNavBarHeight
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // ===== アルバムアート（上部・大きく表示）=====
                AlbumArtCard(
                    uri = mediaState.albumArtUri,
                    bitmap = mediaState.albumArtBitmap,
                    shadowColor = accentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 波形 =====
                if (settings.showWaveform) {
                    WaveformAnimation(
                        isPlaying = mediaState.isPlaying,
                        color = accentColor,
                        modifier = Modifier.height(14.dp).fillMaxWidth(0.22f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ===== シークバー =====
                if (settings.waveSeekBar) {
                    WaveSeekBar(
                        progress = mediaState.progress,
                        onProgressChange = { viewModel.seekTo(it) },
                        thickness = settings.seekBarThickness.dp,
                        color = accentColor,
                        style = settings.waveSeekBarStyle,
                        isPlaying = mediaState.isPlaying,
                        durationMs = mediaState.durationMs
                    )
                } else {
                    NormalSeekBar(
                        progress = mediaState.progress,
                        onProgressChange = { viewModel.seekTo(it) },
                        thickness = settings.seekBarThickness.dp,
                        color = accentColor,
                        durationMs = mediaState.durationMs
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ===== 時間表示 + 空間オーディオバッジ（中央）=====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        mediaState.currentPositionStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColorSub
                    )
                    if (mediaState.isSpatialAudio) {
                        SpatialAudioBadge(isBlurBg = isBlur)
                    }
                    Text(
                        "-${mediaState.remainingTimeStr}",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColorSub
                    )
                }

                // ===== シークバーとタイトルの間の余白（追加: 10dp→16dp→26dp）=====
                Spacer(modifier = Modifier.height(26.dp))

                // ===== 曲名・アーティスト（曲切り替え時にスライドアニメーション）=====
                // 次へ(lastSkipDirection>=0): 新しいテキストは右から入り、古いテキストは左へ抜ける
                // 前へ(lastSkipDirection<0) : 新しいテキストは左から入り、古いテキストは右へ抜ける
                val trackInfoSnapshot = TrackInfoSnapshot(
                    title = mediaState.title,
                    artist = mediaState.artist,
                    album = mediaState.album
                )
                AnimatedContent(
                    targetState = trackInfoSnapshot,
                    transitionSpec = {
                        val spec = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 350)
                        if (lastSkipDirection >= 0) {
                            (slideInHorizontally(animationSpec = spec) { width -> width } + fadeIn(tween(350))) togetherWith
                                    (slideOutHorizontally(animationSpec = spec) { width -> -width } + fadeOut(tween(200)))
                        } else {
                            (slideInHorizontally(animationSpec = spec) { width -> -width } + fadeIn(tween(350))) togetherWith
                                    (slideOutHorizontally(animationSpec = spec) { width -> width } + fadeOut(tween(200)))
                        }.using(SizeTransform(clip = false))
                    },
                    label = "trackInfoSlide"
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
                        textColor = textColor,
                        isBlurBg = isBlur,
                    )
                }

                // ★ 以前はSpacer(weight(1f))で残りスペースを全て埋めてコントロールを
                //   画面下端まで押し下げていたため、テキストとコントロールの間に
                //   大きな空白ができていた。固定の余白に変更し、間隔を調整する。
                Spacer(modifier = Modifier.height(56.dp))

                // ===== コントロールボタン =====
                PlayerControlsA(
                    isPlaying = mediaState.isPlaying,
                    onPrev = { viewModel.skipToPrevious() },
                    onNext = { viewModel.skipToNext() },
                    onPlayPause = { viewModel.togglePlayPause() },
                    onRewind = { viewModel.seekRelative(-10000L) },
                    onFastForward = { viewModel.seekRelative(10000L) },
                    onLongPressPlay = onLongPressPlay,
                    buttonColor = accentColor,
                    iconColor = iconColor,
                    // animated + 白固定ON: ボタン背景が白になるので再生アイコンだけ黒にする
                    playIconColor = if (isAnimated && settings.animatedWhiteIcon) Color.Black
                    else Color.Unspecified,
                    vibrationMs = if (settings.vibrationEnabled) settings.vibrationStrength else 0,
                    isBlurBg = isBlur,
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ===== サブボタン（左右ショートカット・音量）=====
                SubControlsA(
                    mediaState       = mediaState,
                    repeatMode       = repeatMode,
                    isShuffleEnabled = isShuffleEnabled,
                    isFavorite       = isFavorite,
                    shortcutLeft     = settings.homeShortcutLeft,
                    shortcutRight    = settings.homeShortcutRight,
                    onShowLyrics     = onShowLyrics,
                    onVolumeChange   = { viewModel.setVolume(it) },
                    onRepeat         = { viewModel.cycleRepeatMode() },
                    onShuffle        = { viewModel.toggleShuffle() },
                    onToggleFavorite = {
                        favoriteViewModel.toggleFavorite(
                            title       = mediaState.title,
                            artist      = mediaState.artist,
                            album       = mediaState.album,
                            albumArtUri = resolvedFavoriteArtUri,
                            appLabel    = mediaState.appLabel,
                            packageName = mediaState.packageName,
                            mediaId     = mediaState.mediaId,
                            durationMs  = mediaState.durationMs
                        )
                    },
                    onShowHistory    = onShowHistory,
                    onShare          = {
                        shareScreenshot(
                            context = favoriteContext,
                            title   = mediaState.title,
                            artist  = mediaState.artist
                        )
                    },
                    accentColor      = accentColor,
                    iconColor        = iconColor,
                    isBlurBg         = isBlur,
                    textColor        = textColor,
                )

                Spacer(modifier = Modifier.height(28.dp))
            }
        } // else（縦画面）ここまで
    }
}

// ===== 横画面レイアウト: 左半分=アルバムアート／右半分=テキスト・操作系 =====
// ★ 縦画面のColumn内で使っていたコンポーザブル（TrackInfo・NormalSeekBar/WaveSeekBar・
//   PlayerControlsA・SubControlsA等）をそのまま流用し、配置だけをRow（左右分割）に
//   組み替えたもの。横画面は縦幅が狭くなりやすいため、
//   ・アルバムアートは fillMaxWidth+aspectRatio(1f) ではなく fillMaxHeight+aspectRatio(1f)
//     にして「高さ基準」でサイズを決める（幅基準のままだと縦画面と同じ理由で
//     画面からはみ出す）
//   ・右側の操作エリアは verticalScroll を付け、小さい端末やDPIの高い端末で
//     万一収まりきらない場合でもスクロールで対応できるようにする（クリップされて
//     ボタンが押せなくなる事故を防ぐ保険）
//   ・各Spacerの余白は縦画面よりも詰めて、縦幅に収まりやすくしている
@Composable
private fun HomeScreenALandscapeContent(
    mediaState: MediaState,
    settings: AppSettings,
    viewModel: MediaPlayerViewModel,
    accentColor: Color,
    iconColor: Color,
    textColor: Color,
    textColorSub: Color,
    isBlur: Boolean,
    isAnimated: Boolean,
    repeatMode: QueueRepeatMode,
    lastSkipDirection: Int,
    isShuffleEnabled: Boolean,
    isFavorite: Boolean,
    bottomNavBarHeight: androidx.compose.ui.unit.Dp,
    // ★ 横画面の縦向きピルナビゲーションバー分、右端に追加する余白。
    //   縦向きピルは画面右端に重ねて表示されるため、このRow自体の右padding
    //   （通常の20dpに加算）として反映し、右側の音量バー等が隠れないようにする。
    endNavBarWidth: androidx.compose.ui.unit.Dp = 0.dp,
    onLongPressPlay: () -> Unit,
    onShowLyrics: () -> Unit,
    onShowHistory: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp + endNavBarWidth)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                bottom = bottomNavBarHeight + 8.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ===== 左半分: アルバムアート =====
        // 横幅ではなく縦幅(fillMaxHeight)を基準にすることで、横画面の狭い縦幅に
        // 収まりつつできるだけ大きく表示する。weight(1f)で右側と半々の幅を確保しつつ、
        // Box+contentAlignment=Centerでその中央にアートを置く。
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtCard(
                uri = mediaState.albumArtUri,
                bitmap = mediaState.albumArtBitmap,
                shadowColor = accentColor,
                modifier = Modifier
                    .fillMaxHeight(0.92f)
                    .aspectRatio(1f)
            )
        }

        // ===== 右半分: テキスト・シークバー・コントロール・音量バー =====
        // 端末やフォントサイズ設定次第では縦幅に収まりきらない可能性があるため、
        // verticalScrollを付けて安全側に倒す（収まる場合は実質スクロールしない）。
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            // ===== 波形 =====
            if (settings.showWaveform) {
                WaveformAnimation(
                    isPlaying = mediaState.isPlaying,
                    color = accentColor,
                    modifier = Modifier.height(12.dp).fillMaxWidth(0.35f)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ===== シークバー =====
            if (settings.waveSeekBar) {
                WaveSeekBar(
                    progress = mediaState.progress,
                    onProgressChange = { viewModel.seekTo(it) },
                    thickness = settings.seekBarThickness.dp,
                    color = accentColor,
                    style = settings.waveSeekBarStyle,
                    isPlaying = mediaState.isPlaying,
                    durationMs = mediaState.durationMs
                )
            } else {
                NormalSeekBar(
                    progress = mediaState.progress,
                    onProgressChange = { viewModel.seekTo(it) },
                    thickness = settings.seekBarThickness.dp,
                    color = accentColor,
                    durationMs = mediaState.durationMs
                )
            }

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
                    color = textColorSub
                )
                if (mediaState.isSpatialAudio) {
                    SpatialAudioBadge(isBlurBg = isBlur)
                }
                Text(
                    "-${mediaState.remainingTimeStr}",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColorSub
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ===== 曲名・アーティスト（曲切り替え時にスライドアニメーション）=====
            // 縦画面と同じAnimatedContent+TrackInfoの組み合わせをそのまま使う。
            val trackInfoSnapshot = TrackInfoSnapshot(
                title = mediaState.title,
                artist = mediaState.artist,
                album = mediaState.album
            )
            AnimatedContent(
                targetState = trackInfoSnapshot,
                transitionSpec = {
                    val spec = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 350)
                    if (lastSkipDirection >= 0) {
                        (slideInHorizontally(animationSpec = spec) { width -> width } + fadeIn(tween(350))) togetherWith
                                (slideOutHorizontally(animationSpec = spec) { width -> -width } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(animationSpec = spec) { width -> -width } + fadeIn(tween(350))) togetherWith
                                (slideOutHorizontally(animationSpec = spec) { width -> width } + fadeOut(tween(200)))
                    }.using(SizeTransform(clip = false))
                },
                label = "trackInfoSlideLandscape"
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
                    textColor = textColor,
                    isBlurBg = isBlur,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ===== コントロールボタン =====
            PlayerControlsA(
                isPlaying = mediaState.isPlaying,
                onPrev = { viewModel.skipToPrevious() },
                onNext = { viewModel.skipToNext() },
                onPlayPause = { viewModel.togglePlayPause() },
                onRewind = { viewModel.seekRelative(-10000L) },
                onFastForward = { viewModel.seekRelative(10000L) },
                onLongPressPlay = onLongPressPlay,
                buttonColor = accentColor,
                iconColor = iconColor,
                playIconColor = if (isAnimated && settings.animatedWhiteIcon) Color.Black
                else Color.Unspecified,
                vibrationMs = if (settings.vibrationEnabled) settings.vibrationStrength else 0,
                isBlurBg = isBlur,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ===== サブボタン（左右ショートカット・音量）=====
            SubControlsA(
                mediaState       = mediaState,
                repeatMode       = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
                isFavorite       = isFavorite,
                shortcutLeft     = settings.homeShortcutLeft,
                shortcutRight    = settings.homeShortcutRight,
                onShowLyrics     = onShowLyrics,
                onVolumeChange   = { viewModel.setVolume(it) },
                onRepeat         = { viewModel.cycleRepeatMode() },
                onShuffle        = { viewModel.toggleShuffle() },
                onToggleFavorite = onToggleFavorite,
                onShowHistory    = onShowHistory,
                onShare          = onShare,
                accentColor      = accentColor,
                iconColor        = iconColor,
                isBlurBg         = isBlur,
                textColor        = textColor,
            )
        }
    }
}

// ===== アルバムアート =====
@Composable
fun AlbumArtCard(
    uri: String?,
    bitmap: android.graphics.Bitmap? = null,
    modifier: Modifier = Modifier,
    shadowColor: Color = Color.Black
) {
    val shape = RoundedCornerShape(20.dp)
    if (uri != null || bitmap != null) {
        // 外Box: shadowのみ — clipと分離することで影が切り取られない
        Box(
            modifier = modifier.shadow(
                elevation    = 28.dp,
                shape        = shape,
                ambientColor = shadowColor.copy(alpha = 0.45f),
                spotColor    = shadowColor.copy(alpha = 0.75f)
            )
        ) {
            // 内Box: clip + 白縁取り6dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .border(width = 6.dp, color = Color.White.copy(alpha = 0.85f), shape = shape)
            ) {
                if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier.background(Color.Gray.copy(alpha = 0.3f), shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
        }
    }
}

// ===== 波形アニメーション =====
@Composable
fun WaveformAnimation(isPlaying: Boolean, color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val barCount = 5
    val animatedValues: List<State<Float>> = List(barCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400 + index * 80, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "bar$index"
        )
    }
    Canvas(modifier = modifier) {
        val totalBarsWidth = barCount * (size.width / (barCount * 2.5f))
        val barWidth = size.width / (barCount * 2.5f) * 0.45f
        val gap = size.width / (barCount * 2.5f) * 1.55f
        val startX = (size.width - (barWidth * barCount + gap * (barCount - 1))) / 2f
        for (i in 0 until barCount) {
            val h = if (isPlaying) size.height * animatedValues[i].value else size.height * 0.25f
            val x = startX + i * (barWidth + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}

// ===== 波状シークバー =====
@Composable
fun WaveSeekBar(progress: Float, onProgressChange: (Float) -> Unit, thickness: Dp, color: Color, style: Int = 1, isPlaying: Boolean = true, durationMs: Long = 0L) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveSeek")
    // 波アニメーション（遅め: 2800ms）
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(2800, easing = LinearEasing)),
        label = "phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(3800, easing = LinearEasing)),
        label = "phase2"
    )
    val phase3 by infiniteTransition.animateFloat(
        initialValue = (Math.PI).toFloat(), targetValue = (3 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(3200, easing = LinearEasing)),
        label = "phase3"
    )

    // 停止時に波振幅を滑らかに0へ（1000msかけて）
    val amplitudeScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "ampScale"
    )

    // ドラッグ中の位置（-1=非ドラッグ）
    var dragProgress by remember { mutableStateOf(-1f) }
    var isDraggingSeek by remember { mutableStateOf(false) }
    // ★ 時間インジケーターの消滅アニメーション中もdragProgressが-1fにリセットされた後の
    //   一瞬、表示位置・時刻の計算に使う値を保持しておくための変数。
    //   dragProgressをそのまま使うと、フェードアウトの途中で表示中の吹き出しが
    //   0:00の位置にワープしてから消えるような不自然な動きになってしまう。
    var lastKnownDragProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(dragProgress) {
        if (dragProgress >= 0f) lastKnownDragProgress = dragProgress
    }

    // ドラッグ中は即時追従、それ以外（シーク・スキップ等）はなめらかに補間
    val animatedProgress by animateFloatAsState(
        targetValue = if (isDraggingSeek && dragProgress >= 0f) dragProgress else progress,
        animationSpec = if (isDraggingSeek) snap() else tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "waveSeekProgress"
    )

    // ★ 以前はこの領域全体がBox（子要素は重ねて描画される）だったため、
    //   ドラッグ中の時間インジケーターとシークバー本体(Canvas)が同じ位置に
    //   重なって表示されてしまっていた（Boxは指定が無い限り両方左上に配置される）。
    //   Columnに変更し、インジケーター→シークバーの順に縦積みすることで、
    //   インジケーターが必ずシークバーの「上」に表示され、重ならないようにする。
    Column(modifier = Modifier.fillMaxWidth()) {
        // TimeIndicator（ドラッグ中のみ表示）
        // ★ 以前は if(...) による単純な出し分けだったため、表示・非表示が
        //   アニメーション無しで瞬時に切り替わっていた（実際に指摘された不具合）。
        //   AnimatedVisibilityに変更し、フェード＋ポップイン/ポップアウトの
        //   アニメーションを付ける。位置・時刻の計算には lastKnownDragProgress を使い、
        //   消える瞬間にdragProgressが-1fへリセットされても表示内容が
        //   0:00にワープしないようにする（見た目は最後の位置のままフェードアウトする）。
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    // ★ 以前は color(=シークバーと同じアクセントカラー) を背景に使っていたため、
                    //   アクセントカラーが白に近い場合、白文字(Color.White)が背景に埋もれて
                    //   見えなくなっていた（実際に発生した不具合）。
                    //   テーマ・アクセントカラーに関わらず常に読めるよう、
                    //   背景は黒の半透明・文字は白で固定する（吹き出し風の見た目にする）。
                    color = Color.Black.copy(alpha = 0.78f),
                    // ★ 以前は fillMaxWidth(indicatorFraction) で吹き出しの「幅」自体を
                    //   ドラッグ位置の割合で制限していたため、シークバー左端付近
                    //   （dragProgressが0に近い、例: 0:01など）では幅がほぼ0まで潰れ、
                    //   中の文字が入りきらず2〜3行に折り返されてしまっていた
                    //   （実際に発生した不具合）。
                    //   NormalSeekBarと同じく、幅は制限せず絶対オフセット(padding start)で
                    //   位置だけをドラッグ位置に追従させる方式に統一する。
                    //   これにより吹き出しは常に文字が収まる自然な幅になり、折り返さない。
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
                .height(thickness * 3.5f)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val p = (offset.x / size.width).coerceIn(0f, 1f)
                        onProgressChange(p)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDraggingSeek = true },
                        onDragEnd = {
                            if (dragProgress >= 0f) onProgressChange(dragProgress)
                            isDraggingSeek = false
                            dragProgress = -1f
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


            if (style == 1) {
                // ===== Style 1: おだやかな波線 + 右端縦棒 + 未再生破線 =====

                // 再生済み: おだやかな波線
                val waveAmplitude = r * 0.9f * amplitudeScale  // おだやかに
                val waveFreq = 0.035f                           // 周波数を少し下げる
                val wavePath = androidx.compose.ui.graphics.Path()
                wavePath.moveTo(0f, cy)
                var xw = 0f
                while (xw <= progressX) {
                    val yw = cy + waveAmplitude * sin((xw * waveFreq + phase1).toDouble()).toFloat()
                    wavePath.lineTo(xw, yw)
                    xw += 2f                                    // 間隔を短く
                }
                drawPath(
                    path = wavePath,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = r * 2f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )

                // 右端縦棒（再生位置マーカー）大きく・太く
                val markerW = r * 1.4f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(progressX - markerW / 2f, cy - r * 3.5f),
                    size = Size(markerW, r * 7.0f),
                    cornerRadius = CornerRadius(markerW / 2f)
                )

                // 未再生: 破線（固定・整列）
                val dotW = r * 3.0f
                val dotH = r * 0.6f
                val dotGap = r * 2.5f
                val startOffset = progressX % (dotW + dotGap)
                var dx = progressX + (dotW + dotGap - startOffset)
                while (dx + dotW < size.width) {
                    drawRoundRect(
                        color = color.copy(alpha = 0.35f),
                        topLeft = Offset(dx, cy - dotH),
                        size = Size(dotW, dotH * 2),
                        cornerRadius = CornerRadius(dotH)
                    )
                    dx += dotW + dotGap
                }
            } else {
                // ===== Style 2: 塗りつぶし波形（音楽プレーヤー風）=====
                // 未再生トラック（薄い直線）
                drawRoundRect(
                    color = color.copy(alpha = 0.2f),
                    topLeft = Offset(progressX, cy - r * 0.5f),
                    size = Size((size.width - progressX).coerceAtLeast(0f), r),
                    cornerRadius = CornerRadius(r * 0.5f)
                )

                // 波形パスを構築（上縁 → 右端 → 下縁 → 左端 の閉じたパス）
                val amp = r * 2.8f * amplitudeScale  // 振幅（停止時0に）
                val freq = 0.03f            // 周波数
                val step = 4f               // x方向のサンプリング間隔（px）

                val path = androidx.compose.ui.graphics.Path()
                // 左端からスタート（中心線から上方向へ）
                path.moveTo(0f, cy)
                // 上縁（左→右）
                var xp = 0f
                while (xp <= progressX) {
                    val y = cy - amp * kotlin.math.abs(sin((xp * freq + phase1).toDouble()).toFloat())
                    path.lineTo(xp, y)
                    xp += step
                }
                path.lineTo(progressX, cy)
                path.close()

                // グラデーション塗りつぶし
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(color, color.copy(alpha = 0.3f)),
                        startY = cy - amp,
                        endY = cy
                    )
                )

                // 波の上縁ライン（くっきり表示）
                val linePath = androidx.compose.ui.graphics.Path()
                linePath.moveTo(0f, cy)
                xp = 0f
                while (xp <= progressX) {
                    val y = cy - amp * kotlin.math.abs(sin((xp * freq + phase1).toDouble()).toFloat())
                    linePath.lineTo(xp, y)
                    xp += step
                }
                drawPath(
                    path = linePath,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.7f)
                )

                // 再生位置マーカー（縦線 + 丸）
                drawLine(
                    color = color,
                    start = Offset(progressX, cy - amp - r),
                    end = Offset(progressX, cy + r),
                    strokeWidth = r * 0.8f
                )
                drawCircle(
                    color = color,
                    radius = r * 1.4f,
                    center = Offset(progressX, cy)
                )
            }
            // Style2のみ：未再生は薄いトラック（破線なし）
            // Style1のみ：上で処理済み
        }
    } // Column
}

// ===== 通常シークバー（カプセル＋固定整列ドット）=====
@Composable
fun NormalSeekBar(progress: Float, onProgressChange: (Float) -> Unit, thickness: Dp, color: Color, durationMs: Long = 0L) {
    var dragProgress by remember { mutableStateOf(-1f) }
    var isDraggingSeek by remember { mutableStateOf(false) }
    // ★ 時間インジケーターの消滅アニメーション中もdragProgressが-1fにリセットされた後の
    //   一瞬、表示位置・時刻の計算に使う値を保持しておくための変数。
    //   dragProgressをそのまま使うと、フェードアウトの途中で表示中の吹き出しが
    //   0:00の位置にワープしてから消えるような不自然な動きになってしまう。
    var lastKnownDragProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(dragProgress) {
        if (dragProgress >= 0f) lastKnownDragProgress = dragProgress
    }

    // ドラッグ中は即時追従、それ以外（シーク・スキップ等）はなめらかに補間
    val animatedProgress by animateFloatAsState(
        targetValue = if (isDraggingSeek && dragProgress >= 0f) dragProgress else progress,
        animationSpec = if (isDraggingSeek) snap() else tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "seekProgress"
    )

    // ★ 以前はこの領域全体がBox（子要素は重ねて描画される）だったため、
    //   ドラッグ中の時間インジケーターとシークバー本体(Canvas)が同じ位置に
    //   重なって表示されてしまっていた（Boxは指定が無い限り両方左上に配置される）。
    //   Columnに変更し、インジケーター→シークバーの順に縦積みすることで、
    //   インジケーターが必ずシークバーの「上」に表示され、重ならないようにする。
    Column(modifier = Modifier.fillMaxWidth()) {
        // ★ 以前は if(...) による単純な出し分けだったため、表示・非表示が
        //   アニメーション無しで瞬時に切り替わっていた（実際に指摘された不具合）。
        //   AnimatedVisibilityに変更し、フェード＋ポップイン/ポップアウトの
        //   アニメーションを付ける。
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    // ★ 以前は color(=シークバーと同じアクセントカラー) を背景に使っていたため、
                    //   アクセントカラーが白に近い場合、白文字(Color.White)が背景に埋もれて
                    //   見えなくなっていた（実際に発生した不具合）。
                    //   テーマ・アクセントカラーに関わらず常に読めるよう、
                    //   背景は黒の半透明・文字は白で固定する（吹き出し風の見た目にする）。
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
            val progressX = size.width * animatedProgress
            val r = thickness.toPx() / 2f

            // 再生済み：カプセル
            if (progressX > r * 2) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, cy - r),
                    size = Size(progressX, r * 2),
                    cornerRadius = CornerRadius(r)
                )
            }

            // 未再生：破線
            // ★ 波立ち(WaveSeekBar)ON時のStyle1と全く同じ形状・間隔にすることで、
            //   波立ちのON/OFFを切り替えても未再生部分の見た目が変わらないようにする。
            val dashW = r * 3.0f
            val dashH = r * 0.6f
            val dashGap = r * 2.5f
            val dashStartOffset = progressX % (dashW + dashGap)
            var dashX = progressX + (dashW + dashGap - dashStartOffset)
            while (dashX + dashW < size.width) {
                drawRoundRect(
                    color = color.copy(alpha = 0.35f),
                    topLeft = Offset(dashX, cy - dashH),
                    size = Size(dashW, dashH * 2),
                    cornerRadius = CornerRadius(dashH)
                )
                dashX += dashW + dashGap
            }
        }
    } // Column
}

// ===== 曲情報 =====
@Composable
fun TrackInfo(
    title: String, artist: String, album: String,
    titleFontSize: TextUnit, titleFontWeight: FontWeight,
    artistFontSize: TextUnit, artistFontWeight: FontWeight,
    albumFontSize: TextUnit, albumFontWeight: FontWeight,
    scrollTitle: Boolean = false,
    textColor: Color = Color.Unspecified,
    isBlurBg: Boolean = false,
) {
    // ぼかし背景時はテキストに影を付ける
    // ただし dark_mode + ライトモードは白オーバーレイ上なのでシャドウ不要
    val isDarkBlur = isBlurBg && !(textColor == Color.Black)
    val shadowStyle: androidx.compose.ui.text.TextStyle = if (isDarkBlur) {
        androidx.compose.ui.text.TextStyle(
            shadow = androidx.compose.ui.graphics.Shadow(
                color = Color.Black.copy(alpha = 0.6f),
                offset = Offset(0f, 2f),
                blurRadius = 6f
            )
        )
    } else {
        androidx.compose.ui.text.TextStyle.Default
    }

    val resolvedTitleColor = if (textColor == Color.Unspecified)
        MaterialTheme.colorScheme.onBackground else textColor
    // textColor から派生させることで dark_mode+ライト(黒)の場合も黒になる
    val resolvedSubColor = if (textColor != Color.Unspecified)
        textColor.copy(alpha = 0.75f)
    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    val resolvedAlbumColor = if (textColor != Color.Unspecified)
        textColor.copy(alpha = 0.55f)
    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (scrollTitle) {
            // ===== ループスクロール（末尾で一時停止→再スタート）=====
            val offsetX = remember(title) { Animatable(0f) }
            var containerWidthPx by remember { mutableStateOf(0) }
            var textWidthPx by remember(title) { mutableStateOf(0) }
            val gapDp = 60.dp
            val density = androidx.compose.ui.platform.LocalDensity.current
            val gapPx = with(density) { gapDp.toPx() }

            LaunchedEffect(title, textWidthPx, containerWidthPx) {
                offsetX.snapTo(0f)
                if (textWidthPx <= containerWidthPx || textWidthPx <= 0) return@LaunchedEffect
                val unitPx = textWidthPx + gapPx
                val durationMs = (unitPx * 14f).toInt().coerceIn(3000, 12000)
                kotlinx.coroutines.delay(1000)
                while (true) {
                    // 末尾までスクロール
                    offsetX.animateTo(
                        targetValue = -unitPx,
                        animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
                    )
                    // 末尾で1.5秒停止
                    kotlinx.coroutines.delay(1500)
                    // 先頭に瞬時リセット
                    offsetX.snapTo(0f)
                    // 先頭でも1秒停止してから再スタート
                    kotlinx.coroutines.delay(1000)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .onGloballyPositioned { containerWidthPx = it.size.width }
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth(align = Alignment.Start, unbounded = true)
                        .graphicsLayer { translationX = offsetX.value },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title.ifEmpty { "タイトル不明" },
                        fontSize = titleFontSize,
                        fontWeight = titleFontWeight,
                        color = resolvedTitleColor,
                        maxLines = 1,
                        softWrap = false,
                        style = shadowStyle,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            if (coords.size.width != textWidthPx) textWidthPx = coords.size.width
                        }
                    )
                    if (textWidthPx > containerWidthPx && containerWidthPx > 0) {
                        Spacer(modifier = Modifier.width(gapDp))
                        Text(
                            text = title.ifEmpty { "タイトル不明" },
                            fontSize = titleFontSize,
                            fontWeight = titleFontWeight,
                            color = resolvedTitleColor,
                            maxLines = 1,
                            softWrap = false,
                            style = shadowStyle,
                        )
                    }
                }
            }
        } else {
            Text(
                title.ifEmpty { "タイトル不明" },
                fontSize = titleFontSize,
                fontWeight = titleFontWeight,
                color = resolvedTitleColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = shadowStyle,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            artist.ifEmpty { "アーティスト不明" },
            fontSize = artistFontSize,
            fontWeight = artistFontWeight,
            color = resolvedSubColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = shadowStyle,
        )
        if (album.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                album,
                fontSize = albumFontSize,
                fontWeight = albumFontWeight,
                color = resolvedAlbumColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = shadowStyle,
            )
        }
    }
}

// ===== 空間オーディオバッジ =====
@Composable
fun SpatialAudioBadge(isBlurBg: Boolean = false) {
    val bgColor = if (isBlurBg)
        Color.White.copy(alpha = 0.18f)
    else
        MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isBlurBg)
        Color.White
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.RecordVoiceOver,
                contentDescription = "空間オーディオ",
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "空間オーディオ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

// ===== 長押しスケールアニメーション付きボタン =====
@Composable
private fun PressScaleButton(
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
        label = "pressScale"
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

// ===== コントロールボタン UI-A =====
@Composable
fun PlayerControlsA(
    isPlaying: Boolean,
    onPrev: () -> Unit, onNext: () -> Unit,
    onPlayPause: () -> Unit, onRewind: () -> Unit, onFastForward: () -> Unit,
    onLongPressPlay: () -> Unit,
    buttonColor: Color, vibrationMs: Int,
    iconColor: Color = Color.Unspecified,
    // 再生ボタンのアイコン色（背景と同色になる場合に個別指定するため分離）
    // デフォルトは Color.Unspecified → 内部で Color.White にフォールバック（従来動作）
    playIconColor: Color = Color.Unspecified,
    isBlurBg: Boolean = false,
) {
    val context = LocalContext.current
    val iconTint = if (iconColor != Color.Unspecified) iconColor
    else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ⏮ 前の曲
        PressScaleButton(
            modifier = Modifier.size(48.dp),
            onTap = { vibrateIfEnabled(context, vibrationMs); onPrev() }
        ) {
            Icon(Icons.Rounded.SkipPrevious, "前の曲", modifier = Modifier.size(34.dp), tint = iconTint)
        }

        // ↺ 10秒戻し（薄い丸背景）
        PressScaleButton(
            modifier = Modifier
                .size(52.dp)
                .background(buttonColor.copy(alpha = 0.15f), shape = RoundedCornerShape(50)),
            onTap = { vibrateIfEnabled(context, vibrationMs); onRewind() }
        ) {
            Icon(Icons.Rounded.Replay10, "10秒戻し", modifier = Modifier.size(28.dp), tint = iconTint)
        }

        // ⏸/▶ 再生・一時停止（大きめ・角丸正方形）長押しでアクションメニュー
        // playIconColor が指定されていればそれを使う（白ボタンに白アイコンを避けるため）
        // 未指定（Unspecified）の場合は従来通り白
        val resolvedPlayIconColor = if (playIconColor != Color.Unspecified) playIconColor else Color.White
        PressScaleButton(
            modifier = Modifier
                .size(80.dp)
                .background(buttonColor, shape = RoundedCornerShape(24.dp)),
            onTap = { vibrateIfEnabled(context, vibrationMs); onPlayPause() },
            onLongPress = { vibrateIfEnabled(context, vibrationMs); onLongPressPlay() }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "一時停止" else "再生",
                tint = resolvedPlayIconColor,
                modifier = Modifier.size(44.dp)
            )
        }

        // ↻ 10秒送り（薄い丸背景）
        PressScaleButton(
            modifier = Modifier
                .size(52.dp)
                .background(buttonColor.copy(alpha = 0.15f), shape = RoundedCornerShape(50)),
            onTap = { vibrateIfEnabled(context, vibrationMs); onFastForward() }
        ) {
            Icon(Icons.Rounded.Forward10, "10秒送り", modifier = Modifier.size(28.dp), tint = iconTint)
        }

        // ⏭ 次の曲
        PressScaleButton(
            modifier = Modifier.size(48.dp),
            onTap = { vibrateIfEnabled(context, vibrationMs); onNext() }
        ) {
            Icon(Icons.Rounded.SkipNext, "次の曲", modifier = Modifier.size(34.dp), tint = iconTint)
        }
    }
}

// ===== サブコントロール（歌詞・音量・リピート）=====
@Composable
fun SubControlsA(
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
    iconColor: Color = Color.Unspecified,
    isBlurBg: Boolean = false,
    textColor: Color = Color.Unspecified,
) {
    val iconTint = if (iconColor != Color.Unspecified) iconColor
    else MaterialTheme.colorScheme.onBackground
    val activeColor = accentColor

    // ★ 音量バー行の「下」に音声出力先を表示するため、全体をColumnで包む。
    //   横画面(HomeScreenALandscapeContent)でも同じSubControlsAを呼んでいるため、
    //   ここを直せば縦画面・横画面どちらにも自動で反映される。
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ===== 左ショートカット（設定で選んだ機能）=====
            HomeShortcutButton(
                type = shortcutLeft,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
                isFavorite = isFavorite,
                iconTint = iconTint,
                activeColor = activeColor,
                onShowLyrics = onShowLyrics,
                onRepeat = onRepeat,
                onShuffle = onShuffle,
                onToggleFavorite = onToggleFavorite,
                onShowHistory = onShowHistory,
                onShare = onShare
            )

            // ===== 音量スライダー（CustomVolumeBar）=====
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.VolumeDown, null,
                    modifier = Modifier.size(16.dp),
                    tint = iconTint.copy(alpha = 0.6f))
                Spacer(Modifier.width(4.dp))
                var isDragging by remember { mutableStateOf(false) }
                CustomVolumeBar(
                    progress    = mediaState.volume,
                    isActive    = isDragging,
                    accentColor = accentColor,
                    dotColor    = accentColor.copy(alpha = 0.45f),
                    trackColor  = accentColor.copy(alpha = 0.20f),
                    modifier    = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart  = { isDragging = true },
                                onDragEnd    = { isDragging = false },
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
                Icon(Icons.Rounded.VolumeUp, null,
                    modifier = Modifier.size(16.dp),
                    tint = iconTint.copy(alpha = 0.6f))
            }

            // ===== 右ショートカット（設定で選んだ機能）=====
            HomeShortcutButton(
                type = shortcutRight,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
                isFavorite = isFavorite,
                iconTint = iconTint,
                activeColor = activeColor,
                onShowLyrics = onShowLyrics,
                onRepeat = onRepeat,
                onShuffle = onShuffle,
                onToggleFavorite = onToggleFavorite,
                onShowHistory = onShowHistory,
                onShare = onShare
            )
        }

        // ===== 音の出力先（スピーカー / ヘッドホン / Bluetooth(接続名) / USB-DAC(接続名)）=====
        Spacer(Modifier.height(6.dp))
        AudioOutputIndicator(tint = iconTint.copy(alpha = 0.55f))
    }
}

// ===== ホーム画面ショートカットボタン =====
// 設定(homeShortcutLeft/Right)で選ばれた種類に応じてアイコン・挙動を出し分ける。
// 選択肢: "lyrics" / "favorite" / "history" / "share" / "repeat" / "shuffle"
// 未知の値やnullは "lyrics"（歌詞）にフォールバックする。
@Composable
private fun HomeShortcutButton(
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
                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "お気に入り",
                tint = if (isFavorite) activeColor else iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        "history" -> IconButton(onClick = onShowHistory, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = "履歴",
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        "share" -> IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "シェア",
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        "repeat" -> IconButton(onClick = onRepeat, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = when (repeatMode) {
                    QueueRepeatMode.ONE  -> Icons.Rounded.RepeatOne
                    else                 -> Icons.Rounded.Repeat
                },
                contentDescription = "リピート",
                tint = if (repeatMode != QueueRepeatMode.NONE) activeColor else iconTint.copy(0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
        "shuffle" -> IconButton(onClick = onShuffle, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "シャッフル",
                tint = if (isShuffleEnabled) activeColor else iconTint.copy(0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
        else -> IconButton(onClick = onShowLyrics, modifier = Modifier.size(48.dp)) { // "lyrics" がデフォルト
            Icon(
                imageVector = Icons.Rounded.Lyrics,
                contentDescription = "歌詞",
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ===== カスタム音量バー（資料.txtより）=====
@Composable
fun CustomVolumeBar(
    progress: Float,
    isActive: Boolean,
    accentColor: Color,
    dotColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedHeight by animateDpAsState(
        targetValue = if (isActive) 28.dp else 18.dp,
        animationSpec = tween(200),
        label = "volHeight"
    )
    Canvas(modifier = modifier.fillMaxWidth().height(animatedHeight)) {
        val p = 4.dp.toPx()
        val rad = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = trackColor, size = size, cornerRadius = rad)
        if (progress > 0) drawRoundRect(
            color = accentColor,
            topLeft = Offset(p, p),
            size = Size((size.width - p * 2) * progress, size.height - p * 2),
            cornerRadius = CornerRadius((size.height - p * 2) / 2)
        )
        val dotRadius = 2.dp.toPx()
        val spacing = 12.dp.toPx()
        var currentX = p + spacing
        while (currentX < size.width - p) {
            if (currentX > ((size.width - p * 2) * progress) + p + dotRadius) {
                drawCircle(color = dotColor, radius = dotRadius, center = Offset(currentX, size.height / 2))
            }
            currentX += spacing
        }
    }
}

// ===== 音量スライダー（CustomVolumeBar使用）=====
@Composable
fun VolumeSlider(volume: Float, onVolumeChange: (Float) -> Unit, color: Color) {
    var isDragging by remember { mutableStateOf(false) }
    val trackColor = color.copy(alpha = 0.20f)
    val dotColor   = color.copy(alpha = 0.45f)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VolumeDown, null, modifier = Modifier.size(18.dp),
            tint = color.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.width(8.dp))
        CustomVolumeBar(
            progress    = volume,
            isActive    = isDragging,
            accentColor = color,
            dotColor    = dotColor,
            trackColor  = trackColor,
            modifier    = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart  = { isDragging = true },
                        onDragEnd    = { isDragging = false },
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
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Rounded.VolumeUp, null, modifier = Modifier.size(18.dp),
            tint = color.copy(alpha = 0.7f))
    }
}

// ===== バイブレーション =====
@Suppress("DEPRECATION")
fun vibrateIfEnabled(context: android.content.Context, durationMs: Int) {
    if (durationMs <= 0) return
    val vibrator: android.os.Vibrator? = if (android.os.Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
    }
    vibrator ?: return
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(
            durationMs.toLong(), android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        vibrator.vibrate(durationMs.toLong())
    }
}
// =============================================================================
// ===== 動的アニメーション背景（Apple Music風 AGSL メッシュグラデーション）=====
// =============================================================================
// 【概要】
//   accentColor（アルバムアートから抽出した支配色）を軸に、
//   HSV色空間で色相を±40°ずらした3色を生成し、
//   AGSLの RuntimeShader で時間とともにゆっくり歪みながら混ぜ合わせる。
//
// 【AGSLシェーダーのアルゴリズム】
//   1. UV座標を sin/cos でゆっくり変形（ワープ）
//   2. 変形後のUVを使い、3つの色スポットとの距離で重み付け混合
//   3. 暗めのオーバーレイを重ねて可読性を確保
//
// 【API要件】
//   - RuntimeShader: API 33 (Android 13 / TIRAMISU) 以上
//   - この関数を呼ぶ前に Build.VERSION.SDK_INT >= TIRAMISU をチェックすること
//   - API 32以下では呼び出し元でブロックし、ぼかし背景にフォールバックする
// =============================================================================
// ===== 色の混ぜ方パターン =====
// hueShift    : 中心色から2・3色目を作るときの色相のずらし幅(度)。広いほど色の差がはっきりする
// baseSatBoost: 中心色自体の彩度を底上げする倍率。
//               ★ animatedColorSource="album_art"のとき、元になるblendedArtColorは
//               複数Swatchの加重平均のため彩度が失われがち（＝結果的に3色とも似た
//               薄い色になり、見た目が単色っぽくなる不具合の原因）。この倍率で
//               混色前にあらかじめ彩度を持ち上げておくことで、パターンによらず
//               「色が判別できる」状態を作る。
// sat2/val2, sat3/val3: 2・3色目それぞれの彩度・明度の追加倍率
private data class MeshColorPatternParams(
    val hueShift: Float,
    val baseSatBoost: Float,
    val sat2: Float, val val2: Float,
    val sat3: Float, val val3: Float
)

private fun resolveMeshColorPattern(pattern: String): MeshColorPatternParams = when (pattern) {
    // soft: 色相差を狭くし、彩度も抑えめ→落ち着いた印象
    "soft" -> MeshColorPatternParams(
        hueShift = 20f, baseSatBoost = 1.0f,
        sat2 = 0.95f, val2 = 0.90f, sat3 = 0.90f, val3 = 0.95f
    )
    // vivid: 色相差を広く・彩度を大きく底上げ→はっきり複数色に見える
    "vivid" -> MeshColorPatternParams(
        hueShift = 60f, baseSatBoost = 1.35f,
        sat2 = 1.30f, val2 = 0.95f, sat3 = 1.20f, val3 = 1.0f
    )
    // complementary: 補色に近い角度まで振る→コントラストが最も強い
    "complementary" -> MeshColorPatternParams(
        hueShift = 90f, baseSatBoost = 1.2f,
        sat2 = 1.2f, val2 = 0.9f, sat3 = 1.15f, val3 = 0.95f
    )
    // normal: 従来の見た目を踏襲しつつ、単色化対策として彩度だけ少し底上げ
    else -> MeshColorPatternParams(
        hueShift = 40f, baseSatBoost = 1.15f,
        sat2 = 1.1f, val2 = 0.85f, sat3 = 1.05f, val3 = 0.90f
    )
}

@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.TIRAMISU)
@Composable
fun AnimatedMeshBackground(
    color1: Color,
    // ★ 背景の色の濃さ(明るさ倍率)。設定画面のスライダーで0.6〜1.0の範囲で調整する。
    //   低いほど暗く落ち着いた背景になる。
    darkness: Float = 0.88f,
    // ★ 色の混ぜ方パターン。"soft"/"normal"/"vivid"/"complementary"
    colorPattern: String = "normal",
    // ★ ダークモードのときは明示的にさらに暗くする（ライトモードと同じ濃さのままだと
    //   ダークテーマ全体の中で背景だけが浮いて明るく見えてしまうため）。
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val params = resolveMeshColorPattern(colorPattern)

    // ===== 3色生成: accentColor を中心に HSV ±hueShift° =====
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color1.red   * 255).toInt(),
        (color1.green * 255).toInt(),
        (color1.blue  * 255).toInt(),
        hsv
    )
    // 単色化対策: 混色前に中心色自体の彩度を底上げしておく
    // （album_artモードで元色がくすんだ平均色になっていても、ここで彩度を持ち上げる
    //   ことで3色それぞれがちゃんと色として判別できるようにする）
    hsv[1] = (hsv[1] * params.baseSatBoost).coerceIn(0f, 1f)

    // 色相を回して2・3色目を作る。彩度・明度はパターンに応じて強調する
    val hsv2 = hsv.copyOf().also {
        it[0] = (it[0] + params.hueShift) % 360f
        it[1] = (it[1] * params.sat2).coerceIn(0f, 1f)
        it[2] = (it[2] * params.val2).coerceIn(0f, 1f)
    }
    val hsv3 = hsv.copyOf().also {
        it[0] = (it[0] - params.hueShift + 360f) % 360f
        it[1] = (it[1] * params.sat3).coerceIn(0f, 1f)
        it[2] = (it[2] * params.val3).coerceIn(0f, 1f)
    }

    val c1 = Color(android.graphics.Color.HSVToColor(hsv))
    val c2 = Color(android.graphics.Color.HSVToColor(hsv2))
    val c3 = Color(android.graphics.Color.HSVToColor(hsv3))

    // ===== 最終的な明るさ倍率 =====
    // 設定のdarknessスライダー値に加え、ダークモード時は追加でさらに暗くする。
    // (0.6〜1.0のdarknessに対し、ダークモードでは×0.75程度で明確に暗くなるようにする)
    val effectiveDarkness = (if (isDarkTheme) darkness * 0.75f else darkness).coerceIn(0.25f, 1f)

    // ===== 時間アニメーション（nanoTimeベース: リセットなし・完全連続）=====
    // infiniteTransition + RepeatMode.Restart は 0→2π で値が瞬間リセットされ
    // シェーダーの sin/cos がガクッと跳ぶ。
    // withFrameNanos で実経過時間（秒）を単調増加させることで完全に連続になる。
    var time by remember { mutableFloatStateOf(0f) }
    val startNanos = remember { System.nanoTime() }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                // 経過秒（単調増加・リセットなし）
                time = (frameNanos - startNanos) / 1_000_000_000f
            }
        }
    }

    // ===== AGSL シェーダーコード =====
    // uniform: r/g/b の各チャンネルを3色×3個（合計9個）渡す
    val shaderSrc = remember {
        """
        uniform float2 iResolution;
        uniform float  iTime;
        // 色1〜3（linear RGB）
        uniform float3 color1;
        uniform float3 color2;
        uniform float3 color3;
        // 背景全体の明るさ倍率（設定のスライダー・ダークモード補正を反映した最終値）
        uniform float  darkness;

        // ゆっくり動くスポット中心座標（-0.5〜0.5 の正規化空間）
        float2 spotCenter(float2 seed, float time, float speed) {
            return float2(
                0.5 + 0.35 * sin(time * speed + seed.x),
                0.5 + 0.35 * cos(time * speed * 0.7 + seed.y)
            );
        }

        // UV をゆっくり歪ませるワープ関数
        float2 warp(float2 uv, float time) {
            float wx = sin(uv.y * 2.8 + time * 0.4) * 0.06
                     + cos(uv.x * 3.1 + time * 0.3) * 0.04;
            float wy = cos(uv.x * 2.5 + time * 0.35) * 0.06
                     + sin(uv.y * 3.3 + time * 0.25) * 0.04;
            return uv + float2(wx, wy);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / iResolution;

            // ワープ適用
            float2 wuv = warp(uv, iTime);

            // 3つのスポット中心（それぞれ異なる速度・位相で動く）
            float2 p1 = spotCenter(float2(0.0, 1.2), iTime, 0.28);
            float2 p2 = spotCenter(float2(2.1, 0.5), iTime, 0.21);
            float2 p3 = spotCenter(float2(4.3, 3.1), iTime, 0.17);

            // 距離を逆数二乗で重み付け（近いほど強く）
            float d1 = 1.0 / (dot(wuv - p1, wuv - p1) * 6.0 + 0.3);
            float d2 = 1.0 / (dot(wuv - p2, wuv - p2) * 6.0 + 0.3);
            float d3 = 1.0 / (dot(wuv - p3, wuv - p3) * 6.0 + 0.3);

            float total = d1 + d2 + d3;
            float3 col  = (color1 * d1 + color2 * d2 + color3 * d3) / total;

            // 明るさ調整（テキスト可読性のため・設定のスライダー値+ダークモード補正）
            col *= darkness;

            return half4(col, 1.0);
        }
        """.trimIndent()
    }

    // c1/c2/c3（アルバムアート色）が変わったとき shader を再作成する
    val shader = remember(c1, c2, c3) { android.graphics.RuntimeShader(shaderSrc) }

    Canvas(modifier = modifier) {
        // uniform を毎フレーム更新
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("color1", c1.red, c1.green, c1.blue)
        shader.setFloatUniform("color2", c2.red, c2.green, c2.blue)
        shader.setFloatUniform("color3", c3.red, c3.green, c3.blue)
        shader.setFloatUniform("darkness", effectiveDarkness)

        // RuntimeShader を android.graphics.Paint に直接セットして nativeCanvas で描画
        val paint = android.graphics.Paint()
        paint.shader = shader

        drawContext.canvas.nativeCanvas.drawRect(
            0f, 0f, size.width, size.height, paint
        )
    }
}
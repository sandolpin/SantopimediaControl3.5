package com.sandolpin.santopimedia35.home.homescreena

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sandolpin.santopimedia35.MediaPlayerViewModel
import com.sandolpin.santopimedia35.QueueRepeatMode
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.MediaState
import com.sandolpin.santopimedia35.home.*
import com.sandolpin.santopimedia35.shareScreenshot

// UI-A 本体（背景の組み立て・縦/横レイアウト・SharedElement補助）

// ===== 背景クロスフェード用スナップショット =====
// Crossfade(targetState = ...)に渡す値。フェードアウト側のインスタンスには
// 「切り替わる前」の値が保持され続けるため、背景描画に必要な値だけをここに集約する。
private data class HomeScreenABgSnapshot(
    val albumArtUri: String?,
    val blendedArtColor: Color,
    val accentColor: Color,
    val rawAccentColor: Color,
    val animatedBgColor: Color,
    val meshColors: List<Color>?
)

// ===== テキストスライド用スナップショット =====
// AnimatedContentも同様に、フェードアウト側は「切り替わる前」の値を保持し続ける必要が
// あるため、曲名・アーティスト・アルバムのテキストだけをここに集約して渡す。
private data class TrackInfoSnapshot(
    val title: String,
    val artist: String,
    val album: String
)

// =============================================================================
// ===== SharedElementTransition 補助 =====
// -----------------------------------------------------------------------------
// HomeScreenA(縦画面のみ)とLyricsScreen(縦画面ミニプレーヤー)の間で、
// アルバムアート・タイトル・アーティストを連結したアニメーションにするための
// 共通ヘルパー。sharedTransitionScope / animatedVisibilityScope のどちらかが
// null（=連携未対応の呼び出し元。横画面・UI-B・UI-C・設定画面のプレビュー等）の
// 場合は何もしない通常のModifierを返すため、既存の呼び出し元には影響しない。
// =============================================================================
object SharedLyricsKeys {
    const val ALBUM_ART = "shared-album-art"
    const val TITLE = "shared-track-title"
    const val ARTIST = "shared-track-artist"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementIfAvailable(
    key: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return this
    return with(sharedTransitionScope) {
        this@sharedElementIfAvailable.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}

/**
 * UI-A PlayerScreen
 * レイアウト: アルバムアート(大) → 曲名 → シークバー → コントロール → 音量バー
 * コントロール・音量バーを下側に固定
 */
@OptIn(ExperimentalSharedTransitionApi::class)
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
    onShowHistory: () -> Unit = {},
    // ★ HomeScreenC⇔LyricsScreen間のsharedElementTransition用。
    //   呼び出し元(MainActivity.kt)がSharedTransitionLayout配下でAnimatedVisibilityの
    //   スコープを渡せる場合のみ非null。渡されない場合（横画面・呼び出し元未対応時）は
    //   通常通りアニメーション連結なしで表示される。
    sharedTransitionScope: SharedTransitionScope? = null,
    homeAnimatedVisibilityScope: AnimatedVisibilityScope? = null
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
    // ★ 動的アニメーション「アルバムアートから」: 抽出済みの [下地, アクセント1, アクセント2] を使う。
    //   背景が明るい(白っぽいジャケット等)ときは、白固定だと文字が読めなくなるため
    //   文字・アイコン・アクセントを濃い色に自動で切り替える（暗い背景のときは従来どおり白）。
    val meshColors = if (useAgsl) explicitMeshColorsOrNull(settings, mediaState) else null
    val animatedBgIsLight = meshColors != null && isMeshBackgroundLight(
        meshColors, settings.animatedBgDarkness, settings.animatedBgDarknessDark, isDark
    )
    val animatedFg = animatedForegroundColor(animatedBgIsLight)
    val accentColor = when {
        isAnimated && settings.animatedWhiteIcon -> animatedFg
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
        isAnimated -> animatedFg   // animated は ON/OFF 問わずテキスト・アイコンは白
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
            animatedBgColor = animatedBgColor,
            meshColors = meshColors
        )
        Crossfade(
            targetState = bgSnapshot,
            animationSpec = tween(durationMillis = 500),
            label = "playerBackgroundFade"
        ) { snap ->
            Box(modifier = Modifier.fillMaxSize()) {
                // ===== ぼかし背景 =====
                // ★ 通常の「ぼかし」スタイル・動的アニメーションのAPI<33フォールバックに加えて、
                //   動的アニメーションの色ルールで「アルバムアートのぼかし＋アルバムアートの色」
                //   (blur_album_art)を選んだ場合も、ぼかしたアルバムアートを下地として使う。
                val showBlurBase = isBlur || useAnimatedFallback
                if (showBlurBase) {
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
                        // ★ このオーバーレイは「ぼかし」スタイル本来の設定のため、
                        //   isBlur(=backgroundStyleが実際に"blur")のときだけ適用する。
                        //   blur_album_art（動的アニメーション側の色ルール）ではオーバーレイを
                        //   重ねず、上に乗る動的アニメーションの色の動きがそのまま見えるようにする。
                        if (isBlur) {
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
                }

                // ===== グラデーション・デフォルト背景（Boxに付けてシステムバー領域もカバー）=====
                // ===== 動的アニメーション背景（AGSL / API33+）=====
                if (useAgsl) {
                    AnimatedMeshBackground(
                        color1 = snap.animatedBgColor,  // 設定で選んだ色ソース（アクセント or アルバムアート）
                        darkness = settings.animatedBgDarkness,
                        darkModeDarkness = settings.animatedBgDarknessDark,
                        colorPattern = settings.animatedColorPattern,
                        isDarkTheme = isDark,
                        // 「アルバムアートから」のときは [下地, アクセント1, アクセント2] を直接渡す
                        explicitColors = snap.meshColors,
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

        // ===== 背景装飾（ドット/水玉/ランダム図形。どの背景スタイルにも重ねられる独立レイヤー）=====
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
            // ===== 縦画面レイアウト =====
            // ★ 画面の幅が大きい端末では、fillMaxWidth()+aspectRatio(1f)のアルバムアートの
            //   「高さ」も幅に比例して大きくなる。以前はスクロール無しの固定Columnだったため、
            //   幅が大きいほどアルバムアートが縦にも大きくなり、その分だけ下のコントロール類が
            //   画面下からはみ出して見切れてしまっていた不具合の対策として、一度は保険で
            //   verticalScrollを追加した。しかし「スクロールできる仕様自体をやめたい」という
            //   要望のため、SubcomposeLayoutで「アート以外に必要な高さ」を実測してから、
            //   残りの高さと画面幅のうち小さい方をアートの一辺サイズにする方式に変更した。
            //   マジックナンバーでの見積もりではなく実測のため、フォントサイズ設定や
            //   波形表示ON/OFFが何であっても、常に正確にスクロール無しで収まる。
            HomeScreenAPortraitAdaptive(
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
                },
                sharedTransitionScope = sharedTransitionScope,
                homeAnimatedVisibilityScope = homeAnimatedVisibilityScope
            )
        } // else（縦画面）ここまで
    }
}

// =============================================================================
// 縦画面レイアウト: SubcomposeLayoutでアート以外の高さを先に実測してから
// アートのサイズを逆算する（スクロール無しで必ず全要素が収まるようにするため）
// -----------------------------------------------------------------------------
// 【仕組み】
//   1. "below"スロットでアート以外のコンテンツ（波形〜サブコントロール）を
//      「幅は画面いっぱい・高さは無制限」の制約で先に測る（実際に描画はまだしない）。
//      これで、今の設定（フォントサイズ・波形ON/OFF等）における本当に必要な高さがわかる。
//   2. 画面の高さから、その「below」の高さと固定スペーサー分を引いた残りと、
//      画面幅を比較し、小さい方をアートの一辺サイズにする。
//      → 高さが十分ある端末では幅いっぱい（従来通り）、
//        高さが足りない端末（画面幅が広く高さが低い等）では自動的にアートが縮む。
//   3. 決まったサイズでアートを測って配置し、その下に"below"を配置する。
// =============================================================================
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeScreenAPortraitAdaptive(
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
    onLongPressPlay: () -> Unit,
    onShowLyrics: () -> Unit,
    onShowHistory: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    homeAnimatedVisibilityScope: AnimatedVisibilityScope?,
) {
    SubcomposeLayout(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
                // ★ bottomNavBarHeightはピル+システムナビゲーションバー分を含めて実測した値。
                //   これを使うことでコントロールがピルに隠れないようにする
                //   （背景は上のBoxで既にfillMaxSize()のままなのでピルの裏まで届く）
                bottom = bottomNavBarHeight
            )
    ) { constraints ->
        val fullWidth = constraints.maxWidth
        val fullHeight = constraints.maxHeight
        val topSpacerPx = 4.dp.roundToPx()
        val artGapPx = 12.dp.roundToPx()
        val minArtPx = 100.dp.roundToPx() // アートが小さくなりすぎないための下限

        // ① アート以外のコンテンツを高さ無制限で先に測る
        val belowPlaceable = subcompose("below") {
            HomeScreenAPortraitBelowArt(
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
                onLongPressPlay = onLongPressPlay,
                onShowLyrics = onShowLyrics,
                onShowHistory = onShowHistory,
                onToggleFavorite = onToggleFavorite,
                onShare = onShare,
                sharedTransitionScope = sharedTransitionScope,
                homeAnimatedVisibilityScope = homeAnimatedVisibilityScope,
            )
        }.first().measure(
            Constraints(
                minWidth = fullWidth, maxWidth = fullWidth,
                minHeight = 0, maxHeight = Constraints.Infinity
            )
        )

        // ② 残り高さと画面幅のうち小さい方をアートの一辺サイズにする
        val availableForArtPx = (fullHeight - belowPlaceable.height - topSpacerPx - artGapPx)
            .coerceAtLeast(minArtPx)
        val artSizePx = minOf(fullWidth, availableForArtPx)

        // ③ 決まったサイズでアートを測る
        // ★ sharedElementはComposable自身のサイズ変化をアニメーションで補間するため、
        //   ここで.size()を固定していても、LyricsScreen側のサイズへ滑らかに変形する。
        val artPlaceable = subcompose("art") {
            AlbumArtCard(
                uri = mediaState.albumArtUri,
                bitmap = mediaState.albumArtBitmap,
                shadowColor = accentColor,
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

// ===== 縦画面: アルバムアート以外のコンテンツ（波形〜サブコントロールまで）=====
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeScreenAPortraitBelowArt(
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
    onLongPressPlay: () -> Unit,
    onShowLyrics: () -> Unit,
    onShowHistory: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    homeAnimatedVisibilityScope: AnimatedVisibilityScope?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ===== 波形 =====
        if (settings.showWaveform) {
            WaveformAnimation(
                isPlaying = mediaState.isPlaying,
                color = accentColor,
                modifier = Modifier.height(14.dp).fillMaxWidth(0.22f)
            )
            Spacer(modifier = Modifier.height(14.dp))
        } else {
            // 波形を非表示にしても、波形があった場所(14dp+14dp)は空けたままにする
            Spacer(modifier = Modifier.height(28.dp))
        }

        // ===== シークバー =====
        CustomSeekBar(
            progress = mediaState.progress,
            onProgressChange = { viewModel.seekTo(it) },
            color = accentColor,
            spec = settings.toSeekBarSpec(),
            isPlaying = mediaState.isPlaying,
            durationMs = mediaState.durationMs
        )

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

        // ===== シークバーとタイトルの間の余白 =====
        Spacer(modifier = Modifier.height(26.dp))

        // ===== 曲名・アーティスト（曲切り替え時にスライドアニメーション）=====
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
            playIconColor = if (isAnimated && settings.animatedWhiteIcon)
                (if (accentColor.luminance() > 0.5f) Color.Black else Color.White)
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
            onToggleFavorite = onToggleFavorite,
            onShowHistory    = onShowHistory,
            onShare          = onShare,
            accentColor      = accentColor,
            iconColor        = iconColor,
            isBlurBg         = isBlur,
            textColor        = textColor,
        )

        Spacer(modifier = Modifier.height(28.dp))
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
            } else {
                // 波形があった場所(12dp+10dp)を空けたままにする
                Spacer(modifier = Modifier.height(22.dp))
            }

            // ===== シークバー =====
            CustomSeekBar(
                progress = mediaState.progress,
                onProgressChange = { viewModel.seekTo(it) },
                color = accentColor,
                spec = settings.toSeekBarSpec(),
                isPlaying = mediaState.isPlaying,
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